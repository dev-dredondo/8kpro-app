#!/usr/bin/env python3
"""Require locale-stable numeric Formatter calls in Kotlin source."""
from __future__ import annotations

import argparse
from pathlib import Path
import sys
from typing import NamedTuple

from format_specs import _tokens as format_tokens

ROOT = Path(__file__).resolve().parents[2]
# Every Gradle module whose Kotlin ships to users, same rule as check_hardcoded_strings.SRC_ROOTS.
# A single hardcoded "app/src/main/java" silently scanned nothing once the code moved into :core.
# Roots that do not exist in this repo are skipped, so the same file works in every repo.
SOURCE_ROOTS = [
    ROOT / "app" / "src" / "main" / "java",
    ROOT / "core" / "src" / "main" / "java",
    ROOT / "player-core" / "src" / "main" / "java",
]
ALLOWLIST = ROOT / "tools/i18n/number_format_allowlist.txt"
LOCALIZED_CONVERSIONS = set("deEfFgGaA")
ALLOWED_CATEGORIES = {"DISPLAY", "DEVELOPER_DIAGNOSTIC"}


class Token(NamedTuple):
    kind: str
    text: str
    value: str
    start: int
    end: int


class FormatCall(NamedTuple):
    path: str
    literal: str
    occurrence: int
    line: int
    locale: str | None


def _decode_string(raw: str, triple: bool) -> str:
    if triple:
        return raw[3:-3]
    body = raw[1:-1]
    out: list[str] = []
    index = 0
    escapes = {"n": "\n", "r": "\r", "t": "\t", "b": "\b", "\"": "\"", "'": "'", "\\": "\\", "$": "$"}
    while index < len(body):
        if body[index] != "\\" or index + 1 >= len(body):
            out.append(body[index])
            index += 1
            continue
        escaped = body[index + 1]
        if escaped == "u" and index + 5 < len(body):
            try:
                out.append(chr(int(body[index + 2:index + 6], 16)))
                index += 6
                continue
            except ValueError:
                pass
        out.append(escapes.get(escaped, escaped))
        index += 2
    return "".join(out)


def _tokenize(source: str) -> list[Token]:
    tokens: list[Token] = []
    index = 0
    while index < len(source):
        char = source[index]
        if char.isspace():
            index += 1
            continue
        if source.startswith("//", index):
            newline = source.find("\n", index + 2)
            index = len(source) if newline < 0 else newline + 1
            continue
        if source.startswith("/*", index):
            depth = 1
            cursor = index + 2
            while cursor < len(source) and depth:
                if source.startswith("/*", cursor):
                    depth += 1
                    cursor += 2
                elif source.startswith("*/", cursor):
                    depth -= 1
                    cursor += 2
                else:
                    cursor += 1
            index = cursor
            continue
        if source.startswith('"""', index):
            end = source.find('"""', index + 3)
            end = len(source) if end < 0 else end + 3
            raw = source[index:end]
            tokens.append(Token("string", raw, _decode_string(raw, triple=True), index, end))
            index = end
            continue
        if char == '"':
            cursor = index + 1
            while cursor < len(source):
                if source[cursor] == "\\":
                    cursor += 2
                elif source[cursor] == '"':
                    cursor += 1
                    break
                else:
                    cursor += 1
            raw = source[index:cursor]
            tokens.append(Token("string", raw, _decode_string(raw, triple=False), index, cursor))
            index = cursor
            continue
        if char == "'":
            cursor = index + 1
            while cursor < len(source):
                if source[cursor] == "\\":
                    cursor += 2
                elif source[cursor] == "'":
                    cursor += 1
                    break
                else:
                    cursor += 1
            index = cursor
            continue
        if char.isalpha() or char == "_":
            cursor = index + 1
            while cursor < len(source) and (source[cursor].isalnum() or source[cursor] == "_"):
                cursor += 1
            raw = source[index:cursor]
            tokens.append(Token("identifier", raw, raw, index, cursor))
            index = cursor
            continue
        tokens.append(Token("symbol", char, char, index, index + 1))
        index += 1
    return tokens


def _call_arguments(tokens: list[Token], open_index: int) -> tuple[list[list[Token]], int] | None:
    arguments: list[list[Token]] = []
    current: list[Token] = []
    stack = [")"]
    pairs = {"(": ")", "[": "]", "{": "}"}
    index = open_index + 1
    while index < len(tokens):
        token = tokens[index]
        if token.text in pairs:
            stack.append(pairs[token.text])
            current.append(token)
        elif token.text == stack[-1]:
            stack.pop()
            if not stack:
                if current or arguments:
                    arguments.append(current)
                return arguments, index
            current.append(token)
        elif token.text == "," and len(stack) == 1:
            arguments.append(current)
            current = []
        else:
            current.append(token)
        index += 1
    return None


def _normalized(tokens: list[Token]) -> str:
    return "".join(token.text for token in tokens)


def _root_locale(tokens: list[Token]) -> bool:
    return _normalized(tokens) in {"Locale.ROOT", "java.util.Locale.ROOT"}


def _candidate_calls(source: str, path: str) -> list[tuple[str, int, str | None]]:
    tokens = _tokenize(source)
    calls: list[tuple[str, int, str | None]] = []
    index = 0
    while index < len(tokens):
        literal: Token | None = None
        locale: str | None = None
        open_index: int | None = None

        if (
            index + 3 < len(tokens)
            and tokens[index].kind == "string"
            and tokens[index + 1].text == "."
            and tokens[index + 2].text == "format"
            and tokens[index + 3].text == "("
        ):
            literal = tokens[index]
            open_index = index + 3
            parsed = _call_arguments(tokens, open_index)
            if parsed is not None:
                arguments, close_index = parsed
                if arguments and _root_locale(arguments[0]):
                    locale = _normalized(arguments[0])
                index = close_index
        elif (
            index + 3 < len(tokens)
            and tokens[index].text == "String"
            and tokens[index + 1].text == "."
            and tokens[index + 2].text == "format"
            and tokens[index + 3].text == "("
        ):
            open_index = index + 3
            parsed = _call_arguments(tokens, open_index)
            if parsed is not None:
                arguments, close_index = parsed
                if arguments and arguments[0] and arguments[0][0].kind == "string":
                    literal = arguments[0][0]
                elif len(arguments) > 1 and arguments[1] and arguments[1][0].kind == "string":
                    locale = _normalized(arguments[0])
                    literal = arguments[1][0]
                index = close_index

        if literal is not None:
            calls.append((literal.value, source.count("\n", 0, literal.start) + 1, locale))
        index += 1
    return calls


def find_format_calls(source: str, path: str) -> list[FormatCall]:
    occurrences: dict[str, int] = {}
    result: list[FormatCall] = []
    for literal, line, locale in _candidate_calls(source, path):
        conversions = {conversion for _, _, _, conversion in format_tokens(literal)[0]}
        if not conversions.intersection(LOCALIZED_CONVERSIONS):
            continue
        occurrence = occurrences.get(literal, 0) + 1
        occurrences[literal] = occurrence
        result.append(FormatCall(path, literal, occurrence, line, locale))
    return result


def load_allowlist(path: Path) -> tuple[dict[tuple[str, str, int], str], list[str]]:
    entries: dict[tuple[str, str, int], str] = {}
    errors: list[str] = []
    if not path.exists():
        return entries, errors
    for line_number, raw in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        fields = raw.split("\t")
        if len(fields) != 5:
            errors.append(f"{path}:{line_number}: expected five tab-separated fields")
            continue
        category, source_path, literal, occurrence_text, justification = fields
        if category not in ALLOWED_CATEGORIES:
            errors.append(f"{path}:{line_number}: unsupported category {category!r}")
            continue
        if not justification.strip():
            errors.append(f"{path}:{line_number}: justification is required")
            continue
        try:
            occurrence = int(occurrence_text)
        except ValueError:
            errors.append(f"{path}:{line_number}: occurrence must be an integer")
            continue
        entries[(source_path, literal, occurrence)] = category
    return entries, errors


def check(paths: list[Path], allowlist_path: Path = ALLOWLIST) -> tuple[list[str], set[tuple[str, str, int]]]:
    allowlist, errors = load_allowlist(allowlist_path)
    used: set[tuple[str, str, int]] = set()
    for source_path in paths:
        display_path = source_path.relative_to(ROOT).as_posix() if source_path.is_relative_to(ROOT) else source_path.as_posix()
        source = source_path.read_text(encoding="utf-8")
        for call in find_format_calls(source, display_path):
            key = (call.path, call.literal, call.occurrence)
            if key in allowlist:
                used.add(key)
                continue
            if call.locale not in {"Locale.ROOT", "java.util.Locale.ROOT"}:
                errors.append(
                    f"{call.path}:{call.line}: numeric format {call.literal!r} must pass Locale.ROOT "
                    "or have a reviewed display allowlist entry"
                )
    return errors, used


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("paths", nargs="*", type=Path)
    parser.add_argument("--allowlist", type=Path, default=ALLOWLIST)
    args = parser.parse_args(argv)
    paths = args.paths or sorted(
        f for root in SOURCE_ROOTS if root.is_dir() for f in root.rglob("*.kt")
    )
    errors, used = check(paths, args.allowlist)
    allowlist, _ = load_allowlist(args.allowlist)
    for key in sorted(set(allowlist) - used):
        errors.append(f"{args.allowlist}: stale number-format allowlist entry: {key}")
    if errors:
        print("Number locale check failed:")
        for error in errors:
            print(f"  {error}")
        return 1
    print(f"Number locale check OK ({len(paths)} Kotlin files, {len(used)} reviewed display calls)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
