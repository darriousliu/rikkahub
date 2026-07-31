"""Deterministic inventory for Android-to-Compose resource migration."""

from __future__ import annotations

import hashlib
import json
import re
from collections import Counter
from copy import deepcopy
from pathlib import Path
from typing import Any

import yaml
from lxml import etree

from services.resource_compatibility_overlay import (
    ResourceCompatibilityOverlayError,
    ResourceCompatibilityOverlayService,
)


SCHEMA_VERSION = 1
RESOURCE_TYPES = ("string", "plural", "string-array")

_FORMAT_TOKEN = re.compile(
    r"%(?:"
    r"(?P<escaped>%)|"
    r"(?P<newline>n)|"
    r"(?:(?P<index>\d+)\$)?"
    r"(?P<flags>[-#+ 0,(<]*)"
    r"(?P<width>\d+)?"
    r"(?P<precision>\.\d+)?"
    r"(?:(?P<datetime>[tT])(?P<datetime_conversion>[A-Za-z])|"
    r"(?P<conversion>[bBhHsScCdoxXeEfgGaA]))"
    r")"
)


class ResourceInventoryError(RuntimeError):
    """Raised when resources or their migration map are invalid."""


def _sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def _canonical_json(value: Any) -> bytes:
    return json.dumps(
        value,
        ensure_ascii=False,
        sort_keys=True,
        separators=(",", ":"),
    ).encode("utf-8")


def analyze_format_args(value: str) -> dict[str, Any]:
    """Return exact Java Formatter tokens and a normalized argument signature."""
    exact: list[str] = []
    normalized: list[tuple[int, str]] = []
    escaped_percent_count = 0
    newline_token_count = 0
    implicit_index = 1
    previous_index: int | None = None

    for match in _FORMAT_TOKEN.finditer(value):
        if match.group("escaped"):
            escaped_percent_count += 1
            continue
        if match.group("newline"):
            newline_token_count += 1
            continue

        token = match.group(0)
        exact.append(token)
        flags = match.group("flags") or ""
        if "<" in flags:
            if previous_index is None:
                raise ResourceInventoryError(
                    f"Format token {token!r} reuses an argument before one is defined"
                )
            argument_index = previous_index
        elif match.group("index"):
            argument_index = int(match.group("index"))
        else:
            argument_index = implicit_index
            implicit_index += 1

        if match.group("datetime"):
            conversion = "t" + match.group("datetime_conversion").lower()
        else:
            conversion = match.group("conversion").lower()
        normalized.append((argument_index, conversion))
        previous_index = argument_index

    signature = [
        {"index": index, "conversion": conversion, "count": count}
        for (index, conversion), count in sorted(Counter(normalized).items())
    ]
    return {
        "format_args_exact": exact,
        "format_args_normalized": signature,
        "escaped_percent_count": escaped_percent_count,
        "newline_token_count": newline_token_count,
    }


def _logical_text(element: etree._Element) -> str:
    return "".join(element.itertext())


def _canonical_inner_xml(element: etree._Element) -> bytes:
    wrapper = etree.Element("value")
    wrapper.text = element.text
    for child in element:
        wrapper.append(deepcopy(child))
    return etree.tostring(wrapper, method="c14n", with_comments=False)


def _attributes(element: etree._Element, excluded: set[str]) -> dict[str, str]:
    return {
        key: value
        for key, value in sorted(element.attrib.items())
        if key not in excluded
    }


def _value_record(element: etree._Element) -> dict[str, Any]:
    value = _logical_text(element)
    return {
        "logical_value": value,
        "logical_value_sha256": _sha256(value.encode("utf-8")),
        "canonical_inner_xml_sha256": _sha256(_canonical_inner_xml(element)),
        **analyze_format_args(value),
    }


def parse_values_file(path: Path) -> dict[str, Any]:
    """Parse strings, plurals and string arrays from one values XML file."""
    try:
        root = etree.parse(
            str(path),
            parser=etree.XMLParser(resolve_entities=False, no_network=True),
        ).getroot()
    except (OSError, etree.XMLSyntaxError) as error:
        raise ResourceInventoryError(f"Unable to parse {path}: {error}") from error

    if root.tag != "resources":
        raise ResourceInventoryError(f"Expected <resources> root in {path}")

    seen: set[tuple[str, str]] = set()
    resources: list[dict[str, Any]] = []
    key_sets: dict[str, list[str]] = {kind: [] for kind in RESOURCE_TYPES}

    for element in root:
        xml_type = element.tag
        if xml_type not in {"string", "plurals", "string-array"}:
            continue
        resource_type = "plural" if xml_type == "plurals" else xml_type
        name = element.get("name")
        if not name:
            raise ResourceInventoryError(f"Unnamed <{xml_type}> in {path}")
        identity = (resource_type, name)
        if identity in seen:
            raise ResourceInventoryError(
                f"Duplicate {resource_type} resource {name!r} in {path}"
            )
        seen.add(identity)
        key_sets[resource_type].append(name)

        common = {
            "type": resource_type,
            "key": name,
            "attributes": _attributes(element, {"name"}),
            "canonical_inner_xml_sha256": _sha256(_canonical_inner_xml(element)),
        }
        if resource_type == "string":
            resources.append({**common, **_value_record(element)})
            continue

        items: list[dict[str, Any]] = []
        quantities: set[str] = set()
        for index, item in enumerate(element.findall("item")):
            item_record = _value_record(item)
            if resource_type == "plural":
                quantity = item.get("quantity")
                if not quantity:
                    raise ResourceInventoryError(
                        f"Plural {name!r} has an item without quantity in {path}"
                    )
                if quantity in quantities:
                    raise ResourceInventoryError(
                        f"Plural {name!r} repeats quantity {quantity!r} in {path}"
                    )
                quantities.add(quantity)
                item_record = {
                    "quantity": quantity,
                    "attributes": _attributes(item, {"quantity"}),
                    **item_record,
                }
            else:
                item_record = {
                    "index": index,
                    "attributes": _attributes(item, set()),
                    **item_record,
                }
            items.append(item_record)
        if resource_type == "plural":
            items.sort(key=lambda item: item["quantity"])
        identity_key = "quantity" if resource_type == "plural" else "index"
        logical_value = [
            {
                identity_key: item[identity_key],
                "logical_value": item["logical_value"],
            }
            for item in items
        ]
        resources.append(
            {
                **common,
                "logical_value": logical_value,
                "logical_value_sha256": _sha256(_canonical_json(logical_value)),
                "items": items,
            }
        )

    for keys in key_sets.values():
        keys.sort()
    resources.sort(key=lambda item: (RESOURCE_TYPES.index(item["type"]), item["key"]))
    return {
        "file_size": path.stat().st_size,
        "file_sha256": _sha256(path.read_bytes()),
        "key_sets": key_sets,
        "key_set_sha256": _sha256(_canonical_json(key_sets)),
        "resources": resources,
        "resource_count": len(resources),
    }


def _value_projection(record: dict[str, Any]) -> dict[str, Any]:
    return {
        "logical_value": record["logical_value"],
        "logical_value_sha256": record["logical_value_sha256"],
        "canonical_inner_xml_sha256": record["canonical_inner_xml_sha256"],
        "format_args_exact": record["format_args_exact"],
        "format_args_normalized": record["format_args_normalized"],
        "escaped_percent_count": record["escaped_percent_count"],
        "newline_token_count": record["newline_token_count"],
    }


def _resource_projection(resource: dict[str, Any]) -> dict[str, Any]:
    projection = {
        "type": resource["type"],
        "key": resource["key"],
        "attributes": resource["attributes"],
        "logical_value": resource["logical_value"],
        "logical_value_sha256": resource["logical_value_sha256"],
        "canonical_inner_xml_sha256": resource["canonical_inner_xml_sha256"],
    }
    if resource["type"] == "string":
        projection.update(_value_projection(resource))
        return projection

    items = []
    for item in resource["items"]:
        item_projection = {
            key: item[key]
            for key in ("quantity", "index", "attributes")
            if key in item
        }
        item_projection.update(_value_projection(item))
        items.append(item_projection)
    projection["items"] = items
    return projection


def _locale_projection(locale: dict[str, Any]) -> dict[str, Any]:
    return {
        "key_sets": locale["key_sets"],
        "key_set_sha256": locale["key_set_sha256"],
        "resource_count": locale["resource_count"],
        "resources": [
            _resource_projection(resource) for resource in locale["resources"]
        ],
    }


class ResourceInventoryService:
    """Build and verify a deterministic migration baseline."""

    def __init__(
        self,
        project_root: Path,
        locale_codes: list[str],
        module_resource_roots: dict[str, str],
        migration_map_path: Path,
    ) -> None:
        self.project_root = project_root.resolve()
        self.locale_codes = sorted(locale_codes, key=lambda code: (code != "values", code))
        self.module_resource_roots = module_resource_roots
        self.migration_map_path = migration_map_path.resolve()
        with self.migration_map_path.open("r", encoding="utf-8") as file:
            self.migration_map = yaml.safe_load(file)
        if self.migration_map.get("schema_version") != SCHEMA_VERSION:
            raise ResourceInventoryError("Unsupported resource migration map schema")

    @classmethod
    def from_config(cls, config: Any, migration_map_path: Path) -> "ResourceInventoryService":
        return cls(
            project_root=config.project_root,
            locale_codes=config.get_language_codes(),
            module_resource_roots={module.name: module.res_path for module in config.modules},
            migration_map_path=migration_map_path,
        )

    def _relative(self, path: Path) -> str:
        try:
            return path.resolve().relative_to(self.project_root).as_posix()
        except ValueError as error:
            raise ResourceInventoryError(f"Path escapes project root: {path}") from error

    def _project_path(self, relative_path: str) -> Path:
        path = (self.project_root / relative_path).resolve()
        self._relative(path)
        return path

    def _string_modules(self) -> list[dict[str, Any]]:
        modules = self.migration_map.get("string_modules", [])
        if not isinstance(modules, list):
            raise ResourceInventoryError("string_modules must be a list")
        names = [item.get("name") for item in modules]
        if len(names) != len(set(names)):
            raise ResourceInventoryError("String module names must be unique")
        for item in modules:
            disposition = item.get("disposition")
            policy = item.get("android_source_policy", "none")
            if disposition not in {"mirror", "move"}:
                raise ResourceInventoryError(
                    f"Unsupported string disposition for {item.get('name')}: {disposition}"
                )
            if disposition == "mirror" and policy != "full-mirror":
                raise ResourceInventoryError(
                    f"Mirrored string module {item.get('name')} must use full-mirror"
                )
        return sorted(modules, key=lambda item: item["name"])

    def snapshot(self) -> dict[str, Any]:
        modules: list[dict[str, Any]] = []
        type_counts = {kind: 0 for kind in RESOURCE_TYPES}
        for module in self._string_modules():
            name = module["name"]
            source_root = module["source_root"]
            if self.module_resource_roots.get(name) != source_root:
                raise ResourceInventoryError(
                    f"Config res_path for {name!r} does not match migration map: "
                    f"{self.module_resource_roots.get(name)!r} != {source_root!r}"
                )
            root = self._project_path(source_root)
            discovered = {
                file.parent.name for file in root.glob("values*/strings.xml") if file.is_file()
            }
            if discovered != set(self.locale_codes):
                raise ResourceInventoryError(
                    f"Locale set mismatch for {name}: expected {self.locale_codes}, "
                    f"found {sorted(discovered)}"
                )

            locales: list[dict[str, Any]] = []
            for qualifier in self.locale_codes:
                file = root / qualifier / "strings.xml"
                record = parse_values_file(file)
                for resource_type, keys in record["key_sets"].items():
                    type_counts[resource_type] += len(keys)
                locales.append(
                    {
                        "qualifier": qualifier,
                        "source_path": self._relative(file),
                        **record,
                    }
                )
            allowlist = sorted(module.get("android_key_allowlist", []))
            for key in allowlist:
                for locale in locales:
                    if key not in locale["key_sets"]["string"]:
                        raise ResourceInventoryError(
                            f"Android string allowlist key {key!r} is missing from "
                            f"{name}/{locale['qualifier']}"
                        )
            modules.append(
                {
                    "name": name,
                    "disposition": module["disposition"],
                    "android_source_policy": module.get("android_source_policy", "none"),
                    "source_root": source_root,
                    "destination_root": module["destination_root"],
                    "android_key_allowlist": allowlist,
                    "locales": locales,
                }
            )

        binaries = self._binary_resources()
        return {
            "schema_version": SCHEMA_VERSION,
            "migration_map_sha256": _sha256(_canonical_json(self.migration_map)),
            "locale_codes": self.locale_codes,
            "modules": modules,
            "binary_resources": binaries,
            "summary": {
                "locale_file_count": sum(len(module["locales"]) for module in modules),
                "resource_counts": type_counts,
                "binary_resource_count": len(binaries),
                "binary_total_size": sum(item["size"] for item in binaries),
            },
        }

    def _binary_resources(self) -> list[dict[str, Any]]:
        records: list[dict[str, Any]] = []
        seen_ids: set[str] = set()
        seen_paths: set[str] = set()

        for item in self.migration_map.get("binary_resources", []):
            logical_id = item["logical_id"]
            source_path = item["source_path"]
            if logical_id in seen_ids or source_path in seen_paths:
                raise ResourceInventoryError(f"Duplicate binary mapping: {logical_id}")
            seen_ids.add(logical_id)
            seen_paths.add(source_path)
            path = self._project_path(source_path)
            if not path.is_file():
                raise ResourceInventoryError(f"Missing binary resource: {source_path}")
            records.append(
                {
                    "logical_id": logical_id,
                    "source_path": source_path,
                    "destination_path": item.get("destination_path"),
                    "disposition": item["disposition"],
                    "size": path.stat().st_size,
                    "sha256": _sha256(path.read_bytes()),
                }
            )

        for source_path in self._android_only_paths():
            path = self._project_path(source_path)
            if source_path in seen_paths:
                raise ResourceInventoryError(
                    f"Resource is both migratable and Android-only: {source_path}"
                )
            seen_paths.add(source_path)
            logical_id = f"android-only:{source_path}"
            records.append(
                {
                    "logical_id": logical_id,
                    "source_path": source_path,
                    "destination_path": None,
                    "disposition": "android-only",
                    "size": path.stat().st_size,
                    "sha256": _sha256(path.read_bytes()),
                }
            )

        records.sort(key=lambda item: item["logical_id"])
        return records

    def _android_only_paths(self) -> list[str]:
        paths: set[str] = set()
        for pattern in sorted(self.migration_map.get("android_only_paths", [])):
            matches = sorted(
                path for path in self.project_root.glob(pattern) if path.is_file()
            )
            if not matches:
                raise ResourceInventoryError(f"Android-only path matches nothing: {pattern}")
            paths.update(self._relative(path) for path in matches)
        return sorted(paths)

    @staticmethod
    def write_snapshot(snapshot: dict[str, Any], output_path: Path) -> None:
        output_path.parent.mkdir(parents=True, exist_ok=True)
        output_path.write_text(
            json.dumps(snapshot, ensure_ascii=False, sort_keys=True, indent=2) + "\n",
            encoding="utf-8",
        )

    def verify(self, baseline_path: Path) -> dict[str, Any]:
        try:
            baseline = json.loads(baseline_path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError) as error:
            raise ResourceInventoryError(f"Unable to read baseline: {error}") from error
        if baseline.get("schema_version") != SCHEMA_VERSION:
            raise ResourceInventoryError("Unsupported resource baseline schema")
        current_map_hash = _sha256(_canonical_json(self.migration_map))
        if baseline.get("migration_map_sha256") != current_map_hash:
            raise ResourceInventoryError(
                "Migration map differs from baseline; regenerate and review the baseline"
            )
        if baseline.get("locale_codes") != self.locale_codes:
            raise ResourceInventoryError("Configured locale set differs from baseline")

        self._verify_strings(baseline)
        self._verify_binaries(baseline)
        self._verify_compatibility_overlays()
        return baseline["summary"]

    def _verify_compatibility_overlays(self) -> None:
        try:
            ResourceCompatibilityOverlayService(
                self.project_root,
                self.migration_map_path,
            ).verify()
        except ResourceCompatibilityOverlayError as error:
            raise ResourceInventoryError(str(error)) from error

    def _verify_strings(self, baseline: dict[str, Any]) -> None:
        map_modules = {item["name"]: item for item in self._string_modules()}
        baseline_modules = {item["name"]: item for item in baseline["modules"]}
        if map_modules.keys() != baseline_modules.keys():
            raise ResourceInventoryError("String module set differs from baseline")

        for name, expected_module in baseline_modules.items():
            mapping = map_modules[name]
            if expected_module["disposition"] != mapping["disposition"]:
                raise ResourceInventoryError(f"Disposition changed for string module {name}")
            expected_locales = {
                item["qualifier"]: item for item in expected_module["locales"]
            }
            source_root = self._project_path(mapping["source_root"])
            destination_root = self._project_path(mapping["destination_root"])
            destination_started = any(
                (destination_root / qualifier / "strings.xml").is_file()
                for qualifier in self.locale_codes
            )

            for qualifier, expected in expected_locales.items():
                source = source_root / qualifier / "strings.xml"
                destination = destination_root / qualifier / "strings.xml"
                disposition = mapping["disposition"]
                if disposition == "mirror" and not source.is_file():
                    raise ResourceInventoryError(
                        f"Mirrored Android strings are missing: {self._relative(source)}"
                    )
                if disposition == "mirror" and destination_started and not destination.is_file():
                    raise ResourceInventoryError(
                        f"Mirrored Compose strings are missing: {self._relative(destination)}"
                    )
                if disposition == "move" and not (source.is_file() or destination.is_file()):
                    raise ResourceInventoryError(
                        f"Moved strings are missing from both roots: {name}/{qualifier}"
                    )

                if source.is_file():
                    actual = parse_values_file(source)
                    if _locale_projection(actual) != _locale_projection(expected):
                        raise ResourceInventoryError(
                            f"Android string values differ from baseline: {self._relative(source)}"
                        )
                    if (
                        actual["file_size"] != expected["file_size"]
                        or actual["file_sha256"] != expected["file_sha256"]
                    ):
                        raise ResourceInventoryError(
                            f"Android strings file bytes differ from baseline: {self._relative(source)}"
                        )
                if destination.is_file():
                    actual = parse_values_file(destination)
                    if _locale_projection(actual) != _locale_projection(expected):
                        raise ResourceInventoryError(
                            "Compose string values differ from the exact baseline: "
                            f"{self._relative(destination)}"
                        )
                    if (
                        actual["file_size"] != expected["file_size"]
                        or actual["file_sha256"] != expected["file_sha256"]
                    ):
                        raise ResourceInventoryError(
                            "Compose strings file bytes differ from the exact baseline: "
                            f"{self._relative(destination)}"
                        )

    def _verify_binaries(self, baseline: dict[str, Any]) -> None:
        expected = {item["logical_id"]: item for item in baseline["binary_resources"]}
        expected_android_paths = {
            item["source_path"]
            for item in expected.values()
            if item["disposition"] == "android-only"
        }
        if set(self._android_only_paths()) != expected_android_paths:
            raise ResourceInventoryError("Android-only resource path set differs from baseline")
        configured_ids = {
            item["logical_id"] for item in self.migration_map.get("binary_resources", [])
        }
        configured_ids.update(
            logical_id
            for logical_id, item in expected.items()
            if item["disposition"] == "android-only"
        )
        if configured_ids != expected.keys():
            raise ResourceInventoryError("Binary logical ID set differs from baseline")

        mirror_destination_started = any(
            item["disposition"] == "mirror"
            and item.get("destination_path")
            and self._project_path(item["destination_path"]).is_file()
            for item in expected.values()
        )
        for logical_id, item in expected.items():
            source = self._project_path(item["source_path"])
            destination = (
                self._project_path(item["destination_path"])
                if item.get("destination_path")
                else None
            )
            disposition = item["disposition"]
            if disposition == "android-only" and not source.is_file():
                raise ResourceInventoryError(f"Android-only resource is missing: {logical_id}")
            if disposition == "mirror":
                if not source.is_file():
                    raise ResourceInventoryError(f"Mirrored source is missing: {logical_id}")
                if mirror_destination_started and not destination.is_file():
                    raise ResourceInventoryError(f"Mirrored destination is missing: {logical_id}")
            if disposition == "move" and not (
                source.is_file() or (destination is not None and destination.is_file())
            ):
                raise ResourceInventoryError(
                    f"Moved resource is missing from both locations: {logical_id}"
                )

            for path in (source, destination):
                if path is None or not path.is_file():
                    continue
                if path.stat().st_size != item["size"] or _sha256(path.read_bytes()) != item["sha256"]:
                    raise ResourceInventoryError(
                        f"Binary resource differs from baseline: {self._relative(path)}"
                    )
