"""Deterministic Kotlin call-site migration for Compose string resources.

The migrator intentionally handles one narrow source shape: unqualified
``stringResource(...)`` calls.  It is not a general Kotlin parser, but its
scanner understands the lexical constructs that could otherwise create false
positives (comments, string/character literals and balanced delimiters).
"""

from __future__ import annotations

import difflib
import re
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable, Literal


MigrationMode = Literal["dry-run", "check", "apply"]

ANDROID_STRING_RESOURCE_IMPORT = (
    "androidx.compose.ui.res.stringResource as androidStringResource"
)
ANDROID_STRING_RESOURCE_UNALIASED_IMPORT = "androidx.compose.ui.res.stringResource"
APP_R_IMPORT = "me.rerere.rikkahub.R"
GENERATED_RESOURCES_IMPORT = "me.rerere.rikkahub.generated.resources.*"
PROJECT_STRING_RESOURCE_IMPORT = "me.rerere.rikkahub.ui.resources.stringResource"

_CALL_NAME = "stringResource"
_APP_STRING_REFERENCE = re.compile(r"(?<![\w.])R\s*\.\s*string\s*\.\s*[A-Za-z_]\w*")
_APP_STRING_PREFIX = re.compile(r"(?<![\w.])R(?=\s*\.\s*string\s*\.)")
_ANDROID_STRING_REFERENCE = re.compile(
    r"(?<![\w.])android\s*\.\s*R\s*\.\s*string\s*\.\s*[A-Za-z_]\w*"
)
_UNQUALIFIED_R_REFERENCE = re.compile(r"(?<![\w.])R\b")
_IMPORT_LINE = re.compile(
    r"^(?P<indent>[ \t]*)import[ \t]+(?P<name>[^\r\n;]+?)[ \t]*;?[ \t]*(?P<ending>\r?\n|$)",
    re.MULTILINE,
)


@dataclass(frozen=True)
class KotlinMigration:
    """Migration result for one Kotlin source string."""

    original: str
    source: str
    app_reference_count: int
    android_call_count: int

    @property
    def changed(self) -> bool:
        return self.source != self.original


@dataclass(frozen=True)
class KotlinFileMigration:
    """A changed Kotlin file and its deterministic unified diff."""

    path: Path
    original: str
    migrated: str
    app_reference_count: int
    android_call_count: int

    def unified_diff(self, display_path: str | None = None) -> str:
        name = display_path or self.path.as_posix()
        lines = difflib.unified_diff(
            self.original.splitlines(),
            self.migrated.splitlines(),
            fromfile=f"a/{name}",
            tofile=f"b/{name}",
            lineterm="",
        )
        value = "\n".join(lines)
        return f"{value}\n" if value else ""


@dataclass(frozen=True)
class KotlinMigrationReport:
    """Stable result for a directory migration run."""

    mode: MigrationMode
    files_scanned: int
    changed_files: tuple[KotlinFileMigration, ...]

    @property
    def files_changed(self) -> int:
        return len(self.changed_files)

    @property
    def app_reference_count(self) -> int:
        return sum(item.app_reference_count for item in self.changed_files)

    @property
    def android_call_count(self) -> int:
        return sum(item.android_call_count for item in self.changed_files)

    def unified_diff(self, relative_to: Path | None = None) -> str:
        values: list[str] = []
        for item in self.changed_files:
            if relative_to is None:
                display_path = item.path.as_posix()
            else:
                try:
                    display_path = item.path.relative_to(relative_to).as_posix()
                except ValueError:
                    display_path = item.path.as_posix()
            values.append(item.unified_diff(display_path))
        return "".join(values)


@dataclass(frozen=True)
class _Call:
    name_start: int
    open_paren: int
    close_paren: int


def _kotlin_code_mask(source: str) -> list[bool]:
    """Mark source characters that are outside comments and literals."""
    mask = [False] * len(source)
    index = 0
    state = "code"
    block_depth = 0

    while index < len(source):
        if state == "code":
            if source.startswith("//", index):
                index += 2
                state = "line-comment"
                continue
            if source.startswith("/*", index):
                index += 2
                block_depth = 1
                state = "block-comment"
                continue
            if source.startswith('\"\"\"', index):
                index += 3
                state = "triple-string"
                continue
            if source[index] == '"':
                index += 1
                state = "string"
                continue
            if source[index] == "'":
                index += 1
                state = "char"
                continue
            mask[index] = True
            index += 1
            continue

        if state == "line-comment":
            if source[index] in "\r\n":
                mask[index] = True
                state = "code"
            index += 1
            continue

        if state == "block-comment":
            if source.startswith("/*", index):
                block_depth += 1
                index += 2
            elif source.startswith("*/", index):
                block_depth -= 1
                index += 2
                if block_depth == 0:
                    state = "code"
            else:
                index += 1
            continue

        if state == "triple-string":
            if source.startswith('\"\"\"', index):
                index += 3
                state = "code"
            else:
                index += 1
            continue

        if state in {"string", "char"}:
            if source[index] == "\\":
                index = min(index + 2, len(source))
            elif (state == "string" and source[index] == '"') or (
                state == "char" and source[index] == "'"
            ):
                index += 1
                state = "code"
            else:
                index += 1

    return mask


def _is_code_match(match: re.Match[str], mask: list[bool]) -> bool:
    return all(
        mask[index]
        for index in range(match.start(), match.end())
        if not match.string[index].isspace()
    )


def _next_code_character(source: str, mask: list[bool], start: int) -> int | None:
    for index in range(start, len(source)):
        if mask[index] and not source[index].isspace():
            return index
    return None


def _previous_identifier(source: str, mask: list[bool], start: int) -> str | None:
    index = start - 1
    while index >= 0 and (not mask[index] or source[index].isspace()):
        index -= 1
    if index < 0 or not (source[index].isalnum() or source[index] == "_"):
        return None
    end = index + 1
    while index >= 0 and mask[index] and (source[index].isalnum() or source[index] == "_"):
        index -= 1
    return source[index + 1 : end]


def _matching_parenthesis(source: str, mask: list[bool], opening: int) -> int | None:
    depth = 0
    for index in range(opening, len(source)):
        if not mask[index]:
            continue
        if source[index] == "(":
            depth += 1
        elif source[index] == ")":
            depth -= 1
            if depth == 0:
                return index
    return None


def _find_calls(source: str, mask: list[bool]) -> list[_Call]:
    calls: list[_Call] = []
    for match in re.finditer(r"\bstringResource\b", source):
        if not _is_code_match(match, mask):
            continue
        if match.start() > 0 and source[match.start() - 1] == ".":
            # The migration has a deliberately narrow import contract. Fully
            # qualified calls are left for an explicit/manual migration.
            continue
        if _previous_identifier(source, mask, match.start()) == "fun":
            continue
        opening = _next_code_character(source, mask, match.end())
        if opening is None or source[opening] != "(":
            continue
        closing = _matching_parenthesis(source, mask, opening)
        if closing is not None:
            calls.append(_Call(match.start(), opening, closing))
    return calls


def _first_argument_end(source: str, mask: list[bool], call: _Call) -> int:
    round_depth = square_depth = curly_depth = 0
    for index in range(call.open_paren + 1, call.close_paren):
        if not mask[index]:
            continue
        character = source[index]
        if character == "(":
            round_depth += 1
        elif character == ")":
            round_depth -= 1
        elif character == "[":
            square_depth += 1
        elif character == "]":
            square_depth -= 1
        elif character == "{":
            curly_depth += 1
        elif character == "}":
            curly_depth -= 1
        elif character == "," and not (round_depth or square_depth or curly_depth):
            return index
    return call.close_paren


def _matches_in_range(
    pattern: re.Pattern[str],
    source: str,
    mask: list[bool],
    start: int,
    end: int,
) -> list[re.Match[str]]:
    return [
        match
        for match in pattern.finditer(source, start, end)
        if _is_code_match(match, mask)
    ]


def _replace_ranges(source: str, replacements: Iterable[tuple[int, int, str]]) -> str:
    value = source
    for start, end, replacement in sorted(set(replacements), reverse=True):
        value = f"{value[:start]}{replacement}{value[end:]}"
    return value


def _has_unqualified_r_reference(source: str) -> bool:
    mask = _kotlin_code_mask(source)
    return any(
        _is_code_match(match, mask)
        for match in _UNQUALIFIED_R_REFERENCE.finditer(source)
    )


def _import_matches(source: str) -> list[re.Match[str]]:
    return list(_IMPORT_LINE.finditer(source))


def _remove_import(source: str, import_name: str) -> str:
    replacements = [
        (match.start(), match.end(), "")
        for match in _import_matches(source)
        if match.group("name").strip() == import_name
    ]
    return _replace_ranges(source, replacements)


def _replace_import(source: str, old_name: str, new_name: str) -> str:
    replacements = [
        (
            match.start("name"),
            match.end("name"),
            new_name,
        )
        for match in _import_matches(source)
        if match.group("name").strip() == old_name
    ]
    return _replace_ranges(source, replacements)


def _add_import(source: str, import_name: str) -> str:
    matches = _import_matches(source)
    existing = {match.group("name").strip() for match in matches}
    if import_name in existing:
        return source

    newline = "\r\n" if "\r\n" in source else "\n"
    new_line = f"import {import_name}{newline}"
    if not matches:
        package = re.search(r"^package\b[^\r\n]*(?:\r?\n|$)", source, re.MULTILINE)
        if package is None:
            return f"{new_line}{newline}{source}"
        insert_at = package.end()
        return f"{source[:insert_at]}{newline}{new_line}{source[insert_at:]}"

    # Insert relative to the existing imports without reordering unrelated
    # lines. This keeps application diffs small while remaining deterministic.
    for match in matches:
        if match.group("name").strip() > import_name:
            return f"{source[:match.start()]}{new_line}{source[match.start():]}"
    insert_at = matches[-1].end()
    return f"{source[:insert_at]}{new_line}{source[insert_at:]}"


def _update_imports(
    source: str,
    *,
    migrated_app_references: bool,
    migrated_android_calls: bool,
) -> str:
    if migrated_android_calls:
        source = _replace_import(
            source,
            ANDROID_STRING_RESOURCE_UNALIASED_IMPORT,
            ANDROID_STRING_RESOURCE_IMPORT,
        )
        source = _add_import(source, ANDROID_STRING_RESOURCE_IMPORT)
    elif migrated_app_references:
        source = _remove_import(source, ANDROID_STRING_RESOURCE_UNALIASED_IMPORT)

    if migrated_app_references:
        source = _add_import(source, GENERATED_RESOURCES_IMPORT)
        source = _add_import(source, PROJECT_STRING_RESOURCE_IMPORT)

    if migrated_app_references and not _has_unqualified_r_reference(source):
        source = _remove_import(source, APP_R_IMPORT)
    return source


def migrate_kotlin_source(source: str) -> KotlinMigration:
    """Migrate eligible calls in one Kotlin source string."""
    mask = _kotlin_code_mask(source)
    calls = _find_calls(source, mask)
    replacements: list[tuple[int, int, str]] = []
    app_references: set[tuple[int, int]] = set()
    android_calls: set[int] = set()

    for call in calls:
        first_argument_end = _first_argument_end(source, mask, call)
        android_references = _matches_in_range(
            _ANDROID_STRING_REFERENCE,
            source,
            mask,
            call.open_paren + 1,
            first_argument_end,
        )
        if android_references:
            android_calls.add(call.name_start)
            replacements.append(
                (
                    call.name_start,
                    call.name_start + len(_CALL_NAME),
                    "androidStringResource",
                )
            )

        for match in _matches_in_range(
            _APP_STRING_REFERENCE,
            source,
            mask,
            call.open_paren + 1,
            call.close_paren,
        ):
            prefix = _APP_STRING_PREFIX.search(source, match.start(), match.end())
            if prefix is None or not _is_code_match(prefix, mask):
                continue
            app_references.add((prefix.start(), prefix.end()))
            replacements.append((prefix.start(), prefix.end(), "Res"))

    migrated = _replace_ranges(source, replacements)
    migrated = _update_imports(
        migrated,
        migrated_app_references=bool(app_references),
        migrated_android_calls=bool(android_calls),
    )
    return KotlinMigration(
        source=migrated,
        original=source,
        app_reference_count=len(app_references),
        android_call_count=len(android_calls),
    )


class KotlinResourceCallMigrator:
    """Run the deterministic call-site migration over Kotlin files."""

    VALID_MODES = {"dry-run", "check", "apply"}

    def migrate_paths(
        self,
        paths: Iterable[Path],
        *,
        mode: MigrationMode = "dry-run",
    ) -> KotlinMigrationReport:
        if mode not in self.VALID_MODES:
            raise ValueError(f"Unsupported migration mode: {mode}")

        normalized_paths = tuple(
            sorted({Path(path) for path in paths}, key=lambda path: path.as_posix())
        )
        changed: list[KotlinFileMigration] = []
        for path in normalized_paths:
            original = path.read_bytes().decode("utf-8")
            result = migrate_kotlin_source(original)
            if not result.changed:
                continue
            item = KotlinFileMigration(
                path=path,
                original=original,
                migrated=result.source,
                app_reference_count=result.app_reference_count,
                android_call_count=result.android_call_count,
            )
            changed.append(item)
            if mode == "apply":
                path.write_bytes(result.source.encode("utf-8"))

        return KotlinMigrationReport(
            mode=mode,
            files_scanned=len(normalized_paths),
            changed_files=tuple(changed),
        )

    def migrate_tree(
        self,
        source_root: Path,
        *,
        mode: MigrationMode = "dry-run",
    ) -> KotlinMigrationReport:
        source_root = Path(source_root)
        if not source_root.is_dir():
            raise ValueError(f"Kotlin source root does not exist: {source_root}")
        return self.migrate_paths(source_root.rglob("*.kt"), mode=mode)
