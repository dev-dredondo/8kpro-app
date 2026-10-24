#!/usr/bin/env python3
"""Require bounded Compose text to declare its overflow behavior."""
from __future__ import annotations

import argparse
from pathlib import Path
import sys
from typing import NamedTuple

ROOT = Path(__file__).resolve().parents[2]
# Every module whose Kotlin ships to users, for the same reason check_hardcoded_strings.py scans
# more than one root: code moves between modules and must not leave the gate by doing so.
SOURCE_ROOTS = [ROOT / "app/src/main/java", ROOT / "core/src/main/java"]
ALLOWLIST = ROOT / "tools/i18n/text_overflow_allowlist.txt"


class Token(NamedTuple):
    kind: str
    text: str
    start: int
    end: int


class TextCall(NamedTuple):
    path: str
    identity: str
    occurrence: int
    line: int
    has_max_lines: bool
    has_overflow: bool
    clips: bool


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
            tokens.append(Token("string", source[index:end], index, end))
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
            tokens.append(Token("string", source[index:cursor], index, cursor))
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
            tokens.append(Token("identifier", source[index:cursor], index, cursor))
            index = cursor
            continue
        tokens.append(Token("symbol", char, index, index + 1))
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


def _named_argument(arguments: list[list[Token]], name: str) -> list[Token] | None:
    for argument in arguments:
        if len(argument) >= 3 and argument[0].text == name and argument[1].text == "=":
            return argument[2:]
    return None


def find_text_calls(source: str, path: str) -> list[TextCall]:
    tokens = _tokenize(source)
    occurrences: dict[str, int] = {}
    result: list[TextCall] = []
    index = 0
    while index + 1 < len(tokens):
        token = tokens[index]
        if token.text not in {"Text", "BasicText"} or tokens[index + 1].text != "(":
            index += 1
            continue
        if index > 0 and tokens[index - 1].text == "fun":
            index += 1
            continue
        parsed = _call_arguments(tokens, index + 1)
        if parsed is None:
            index += 1
            continue
        arguments, close_index = parsed
        first = _normalized(arguments[0]) if arguments else ""
        identity = f"{token.text}({first})"
        occurrence = occurrences.get(identity, 0) + 1
        occurrences[identity] = occurrence
        overflow = _named_argument(arguments, "overflow")
        result.append(
            TextCall(
                path=path,
                identity=identity,
                occurrence=occurrence,
                line=source.count("\n", 0, token.start) + 1,
                has_max_lines=_named_argument(arguments, "maxLines") is not None,
                has_overflow=overflow is not None,
                clips=overflow is not None and "TextOverflow.Clip" in _normalized(overflow),
            )
        )
        index = close_index + 1
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
        if len(fields) != 4:
            errors.append(f"{path}:{line_number}: expected four tab-separated fields")
            continue
        source_path, identity, occurrence_text, justification = fields
        if not justification.strip():
            errors.append(f"{path}:{line_number}: justification is required")
            continue
        try:
            occurrence = int(occurrence_text)
        except ValueError:
            errors.append(f"{path}:{line_number}: occurrence must be an integer")
            continue
        entries[(source_path, identity, occurrence)] = justification
    return entries, errors


def check(paths: list[Path], allowlist_path: Path = ALLOWLIST) -> tuple[list[str], set[tuple[str, str, int]]]:
    allowlist, errors = load_allowlist(allowlist_path)
    used: set[tuple[str, str, int]] = set()
    for source_path in paths:
        display_path = source_path.relative_to(ROOT).as_posix() if source_path.is_relative_to(ROOT) else source_path.as_posix()
        source = source_path.read_text(encoding="utf-8")
        for call in find_text_calls(source, display_path):
            if call.has_max_lines and not call.has_overflow:
                errors.append(f"{call.path}:{call.line}: {call.identity} sets maxLines without overflow")
            if call.clips:
                key = (call.path, call.identity, call.occurrence)
                if key in allowlist:
                    used.add(key)
                else:
                    errors.append(f"{call.path}:{call.line}: {call.identity} uses unapproved TextOverflow.Clip")
    return errors, used


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("paths", nargs="*", type=Path)
    parser.add_argument("--allowlist", type=Path, default=ALLOWLIST)
    args = parser.parse_args(argv)
    paths = args.paths or sorted(
        (f for root in SOURCE_ROOTS if root.is_dir() for f in root.rglob("*.kt")),
        key=lambda f: f.relative_to(ROOT).as_posix(),
    )
    errors, used = check(paths, args.allowlist)
    allowlist, _ = load_allowlist(args.allowlist)
    for key in sorted(set(allowlist) - used):
        errors.append(f"{args.allowlist}: stale text-overflow exception: {key}")
    if errors:
        print("Text overflow check failed:")
        for error in errors:
            print(f"  {error}")
        return 1
    print(f"Text overflow check OK ({len(paths)} Kotlin files, {len(used)} Clip exceptions)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
