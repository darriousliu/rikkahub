"""Deterministic compatibility overlays for Compose string resources.

An overlay is a deliberately small values XML file copied from an Android
source locale.  It is useful when Android and Compose choose different locale
fallbacks.  Overlay values are never translated: the complete ``<string>``
elements are copied from the declared source locale and verified against it.
"""

from __future__ import annotations

import difflib
from copy import deepcopy
from dataclasses import dataclass
from pathlib import Path
from typing import Literal

import yaml
from lxml import etree


OverlayMode = Literal["dry-run", "check", "apply"]


class ResourceCompatibilityOverlayError(RuntimeError):
    """Raised when an overlay mapping or generated file is invalid."""


@dataclass(frozen=True)
class CompatibilityOverlaySpec:
    """One validated compatibility-overlay declaration."""

    logical_id: str
    module: str
    source_qualifier: str
    target_qualifier: str
    source_file: Path
    target_locale_source_file: Path
    destination_file: Path
    keys: tuple[str, ...]


@dataclass(frozen=True)
class CompatibilityOverlayChange:
    """The deterministic output expected for one overlay."""

    spec: CompatibilityOverlaySpec
    original: bytes | None
    generated: bytes

    @property
    def changed(self) -> bool:
        return self.original != self.generated

    def unified_diff(self, project_root: Path) -> str:
        relative = self.spec.destination_file.relative_to(project_root).as_posix()
        before = b"" if self.original is None else self.original
        lines = difflib.unified_diff(
            before.decode("utf-8").splitlines(),
            self.generated.decode("utf-8").splitlines(),
            fromfile=f"a/{relative}" if self.original is not None else "/dev/null",
            tofile=f"b/{relative}",
            lineterm="",
        )
        value = "\n".join(lines)
        return f"{value}\n" if value else ""


@dataclass(frozen=True)
class CompatibilityOverlayReport:
    """Stable result of syncing all declared overlays."""

    mode: OverlayMode
    overlays_scanned: int
    changes: tuple[CompatibilityOverlayChange, ...]

    @property
    def files_changed(self) -> int:
        return len(self.changes)

    def unified_diff(self, project_root: Path) -> str:
        return "".join(change.unified_diff(project_root) for change in self.changes)


def _parse_resources(path: Path) -> etree._Element:
    try:
        root = etree.parse(
            str(path),
            parser=etree.XMLParser(resolve_entities=False, no_network=True),
        ).getroot()
    except (OSError, etree.XMLSyntaxError) as error:
        raise ResourceCompatibilityOverlayError(
            f"Unable to parse compatibility-overlay resource {path}: {error}"
        ) from error
    if root.tag != "resources":
        raise ResourceCompatibilityOverlayError(
            f"Expected <resources> root in compatibility-overlay resource {path}"
        )
    return root


def _named_value_elements(
    root: etree._Element,
    path: Path,
) -> dict[str, etree._Element]:
    values: dict[str, etree._Element] = {}
    for element in root:
        if not isinstance(element.tag, str):
            continue
        name = element.get("name")
        if name is None:
            continue
        if name in values:
            raise ResourceCompatibilityOverlayError(
                f"Duplicate resource {name!r} in {path}"
            )
        values[name] = element
    return values


def _canonical_element(element: etree._Element) -> bytes:
    copy = deepcopy(element)
    copy.tail = None
    return etree.tostring(copy, method="c14n", with_comments=False)


def _generate_overlay(
    spec: CompatibilityOverlaySpec,
) -> tuple[bytes, dict[str, etree._Element]]:
    source_root = _parse_resources(spec.source_file)
    target_root = _parse_resources(spec.target_locale_source_file)
    source_values = _named_value_elements(source_root, spec.source_file)
    target_values = _named_value_elements(target_root, spec.target_locale_source_file)

    selected: dict[str, etree._Element] = {}
    for key in spec.keys:
        source = source_values.get(key)
        if source is None or source.tag != "string":
            raise ResourceCompatibilityOverlayError(
                f"Compatibility-overlay source string {key!r} is missing from "
                f"{spec.source_file}"
            )
        if key in target_values:
            raise ResourceCompatibilityOverlayError(
                f"Compatibility-overlay key {key!r} is already present in target "
                f"locale source {spec.target_locale_source_file}"
            )
        selected[key] = source

    serialized_elements: list[str] = []
    for key in spec.keys:
        element = deepcopy(selected[key])
        element.tail = None
        serialized_elements.append(
            etree.tostring(
                element,
                encoding="unicode",
                with_tail=False,
                pretty_print=False,
            )
        )
    body = "\n".join(f"  {element}" for element in serialized_elements)
    generated = (
        f'<?xml version="1.0" encoding="utf-8"?>\n'
        f"<resources>\n{body}\n</resources>\n"
    ).encode("utf-8")
    return generated, selected


class ResourceCompatibilityOverlayService:
    """Generate and verify compatibility overlays declared in the migration map."""

    VALID_MODES = {"dry-run", "check", "apply"}

    def __init__(self, project_root: Path, migration_map_path: Path) -> None:
        self.project_root = Path(project_root).resolve()
        self.migration_map_path = Path(migration_map_path).resolve()
        try:
            loaded = yaml.safe_load(self.migration_map_path.read_text(encoding="utf-8"))
        except (OSError, yaml.YAMLError) as error:
            raise ResourceCompatibilityOverlayError(
                f"Unable to read resource migration map: {error}"
            ) from error
        if not isinstance(loaded, dict) or loaded.get("schema_version") != 1:
            raise ResourceCompatibilityOverlayError(
                "Unsupported resource migration map schema"
            )
        self.migration_map = loaded

    def _project_path(self, relative_path: str) -> Path:
        path = (self.project_root / relative_path).resolve()
        try:
            path.relative_to(self.project_root)
        except ValueError as error:
            raise ResourceCompatibilityOverlayError(
                f"Compatibility-overlay path escapes project root: {relative_path}"
            ) from error
        return path

    def specs(self) -> tuple[CompatibilityOverlaySpec, ...]:
        mappings = self.migration_map.get("compatibility_overlays", [])
        if not isinstance(mappings, list):
            raise ResourceCompatibilityOverlayError(
                "compatibility_overlays must be a list"
            )
        modules = {
            item.get("name"): item
            for item in self.migration_map.get("string_modules", [])
            if isinstance(item, dict)
        }
        specs: list[CompatibilityOverlaySpec] = []
        seen_ids: set[str] = set()
        seen_destinations: set[Path] = set()

        for mapping in mappings:
            if not isinstance(mapping, dict):
                raise ResourceCompatibilityOverlayError(
                    "Each compatibility overlay must be a mapping"
                )
            logical_id = mapping.get("logical_id")
            module_name = mapping.get("module")
            source_qualifier = mapping.get("source_qualifier")
            target_qualifier = mapping.get("target_qualifier")
            destination_path = mapping.get("destination_file")
            keys = mapping.get("keys")
            if not all(
                isinstance(value, str) and value
                for value in (
                    logical_id,
                    module_name,
                    source_qualifier,
                    target_qualifier,
                    destination_path,
                )
            ):
                raise ResourceCompatibilityOverlayError(
                    "Compatibility overlay requires non-empty logical_id, module, "
                    "source_qualifier, target_qualifier and destination_file"
                )
            if logical_id in seen_ids:
                raise ResourceCompatibilityOverlayError(
                    f"Duplicate compatibility-overlay logical ID: {logical_id}"
                )
            seen_ids.add(logical_id)
            if not isinstance(keys, list) or not keys or not all(
                isinstance(key, str) and key for key in keys
            ):
                raise ResourceCompatibilityOverlayError(
                    f"Compatibility overlay {logical_id} must declare non-empty keys"
                )
            if len(keys) != len(set(keys)):
                raise ResourceCompatibilityOverlayError(
                    f"Compatibility overlay {logical_id} repeats a key"
                )
            module = modules.get(module_name)
            if module is None:
                raise ResourceCompatibilityOverlayError(
                    f"Compatibility overlay {logical_id} references unknown string "
                    f"module {module_name!r}"
                )
            if not source_qualifier.startswith("values") or not target_qualifier.startswith(
                "values"
            ):
                raise ResourceCompatibilityOverlayError(
                    f"Compatibility overlay {logical_id} has invalid values qualifiers"
                )
            source_root = self._project_path(module["source_root"])
            destination_root = self._project_path(module["destination_root"])
            destination = self._project_path(destination_path)
            expected_parent = destination_root / target_qualifier
            if destination.parent != expected_parent or destination.name == "strings.xml":
                raise ResourceCompatibilityOverlayError(
                    f"Compatibility overlay {logical_id} destination must be a separate "
                    f"XML file under {expected_parent}"
                )
            if destination.suffix != ".xml":
                raise ResourceCompatibilityOverlayError(
                    f"Compatibility overlay {logical_id} destination must be XML"
                )
            if destination in seen_destinations:
                raise ResourceCompatibilityOverlayError(
                    f"Duplicate compatibility-overlay destination: {destination_path}"
                )
            seen_destinations.add(destination)
            specs.append(
                CompatibilityOverlaySpec(
                    logical_id=logical_id,
                    module=module_name,
                    source_qualifier=source_qualifier,
                    target_qualifier=target_qualifier,
                    source_file=source_root / source_qualifier / "strings.xml",
                    target_locale_source_file=(
                        source_root / target_qualifier / "strings.xml"
                    ),
                    destination_file=destination,
                    keys=tuple(keys),
                )
            )

        return tuple(sorted(specs, key=lambda spec: spec.logical_id))

    def sync(self, *, mode: OverlayMode = "dry-run") -> CompatibilityOverlayReport:
        if mode not in self.VALID_MODES:
            raise ValueError(f"Unsupported compatibility-overlay mode: {mode}")
        specs = self.specs()
        changes: list[CompatibilityOverlayChange] = []
        for spec in specs:
            generated, _ = _generate_overlay(spec)
            original = (
                spec.destination_file.read_bytes()
                if spec.destination_file.is_file()
                else None
            )
            change = CompatibilityOverlayChange(spec, original, generated)
            if not change.changed:
                continue
            changes.append(change)
            if mode == "apply":
                spec.destination_file.parent.mkdir(parents=True, exist_ok=True)
                spec.destination_file.write_bytes(generated)
        return CompatibilityOverlayReport(mode, len(specs), tuple(changes))

    def verify(self) -> None:
        """Require every overlay to exist with only exact source string elements."""
        for spec in self.specs():
            generated, source_elements = _generate_overlay(spec)
            if not spec.destination_file.is_file():
                raise ResourceCompatibilityOverlayError(
                    f"Compatibility overlay is missing: {spec.destination_file}"
                )
            overlay_root = _parse_resources(spec.destination_file)
            overlay_elements = _named_value_elements(overlay_root, spec.destination_file)
            actual_keys = set(overlay_elements)
            expected_keys = set(spec.keys)
            if actual_keys != expected_keys:
                missing = sorted(expected_keys - actual_keys)
                extra = sorted(actual_keys - expected_keys)
                raise ResourceCompatibilityOverlayError(
                    f"Compatibility overlay {spec.logical_id} has an unexpected key set; "
                    f"missing={missing}, extra={extra}"
                )
            for key in spec.keys:
                actual = overlay_elements[key]
                if actual.tag != "string" or _canonical_element(actual) != _canonical_element(
                    source_elements[key]
                ):
                    raise ResourceCompatibilityOverlayError(
                        f"Compatibility overlay string {key!r} differs from its source "
                        f"element in {spec.source_file}"
                    )
            if spec.destination_file.read_bytes() != generated:
                raise ResourceCompatibilityOverlayError(
                    f"Compatibility overlay serialization is not deterministic: "
                    f"{spec.destination_file}"
                )
