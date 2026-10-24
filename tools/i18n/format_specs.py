"""Finite Java/Android Formatter grammar used by the resource validator.

Kept separate from XML, locale catalogue, and coverage policy so placeholder behavior can be tested
and changed without touching the rest of the i18n validator.
"""
from __future__ import annotations

import re

_DATE_CONVERSION = r"[tT][HIklMNSLpzZsQYyBbhAaCceRTrYDFjmde]"
_ORDINARY_CONVERSION = r"[bBhHsScCdoxXeEfFgGaAn%]"
_FORMAT_RE = re.compile(
    rf"%(?:(?P<index>[1-9]\d*)\$)?(?P<flags>[-#+ 0,(]*)?(?P<width>\d+)?"
    rf"(?:\.(?P<precision>\d+))?(?:(?P<date>[tT])(?P<date_suffix>[HIklMNSLpzZsQYyBbhAaCceRTrYDFjmde])|"
    rf"(?P<conversion>[bBhHsScCdoxXeEfFgGaAn%]))"
)
_MAX_FORMAT_INTEGER = 2_147_483_647


def _bounded_integer(value: str | None) -> bool:
    return value is None or int(value) <= _MAX_FORMAT_INTEGER


def _match_is_valid(match: re.Match) -> bool:
    index = match.group("index")
    flags = match.group("flags") or ""
    width = match.group("width")
    precision = match.group("precision")
    conversion = match.group("conversion")
    date_suffix = match.group("date_suffix")
    if not all(_bounded_integer(value) for value in (index, width, precision)):
        return False
    if len(flags) != len(set(flags)) or ("-" in flags and "0" in flags) or ("+" in flags and " " in flags):
        return False
    if " " in flags and not index:
        return False
    if conversion in {"%", "n"}:
        return not index and not flags and width is None and precision is None and date_suffix is None
    if date_suffix is not None:
        return set(flags) <= {"-"} and ("-" not in flags or width is not None) and precision is None
    if conversion in {"s", "S", "b", "B", "h", "H"}:
        return set(flags) <= {"-"} and ("-" not in flags or width is not None)
    if conversion in {"c", "C"}:
        return set(flags) <= {"-"} and ("-" not in flags or width is not None) and precision is None
    allowed_flags = {
        "d": "-+ 0,(", "o": "-#0", "x": "-#0", "X": "-#0",
        "e": "-+ 0(#", "E": "-+ 0(#", "f": "-+ 0,(#",
        "g": "-+ 0,(", "G": "-+ 0,(", "a": "-+ 0#", "A": "-+ 0#",
    }
    if conversion not in allowed_flags or not set(flags) <= set(allowed_flags[conversion]):
        return False
    width_ok = ("-" not in flags and "0" not in flags) or width is not None
    return width_ok and (precision is None or conversion not in {"d", "o", "x", "X"})


def _tokens(text: str) -> tuple[list[tuple[int, int, int | None, str]], list[int]]:
    """Return valid tokens and positions of invalid percent sequences."""
    tokens: list[tuple[int, int, int | None, str]] = []
    invalid: list[int] = []
    pos = 0
    while pos < len(text):
        if text[pos] != "%":
            pos += 1
            continue
        match = _FORMAT_RE.match(text, pos)
        if match is None:
            invalid.append(pos)
            pos += 1
            continue
        if not _match_is_valid(match):
            invalid.append(pos)
            pos = match.end()
            continue
        conversion = (
            (match.group("date") or "") + (match.group("date_suffix") or "")
            if match.group("date_suffix") is not None else match.group("conversion")
        )
        index = int(match.group("index")) if match.group("index") else None
        tokens.append((pos, match.end(), index, conversion))
        pos = match.end()
    return tokens, invalid


def strip_valid_formats(text: str) -> str:
    spans = {start: end for start, end, _, _ in _tokens(text)[0]}
    out: list[str] = []
    pos = 0
    while pos < len(text):
        if pos in spans:
            pos = spans[pos]
        else:
            out.append(text[pos])
            pos += 1
    return "".join(out)


def invalid_format_snippets(text: str) -> list[str]:
    return [text[pos:pos + 16] for pos in _tokens(text)[1]]


def placeholders(text: str) -> list[int]:
    return [index for _, _, index, conversion in _tokens(text)[0]
            if index is not None and conversion not in {"%", "n"}]


def has_bare_placeholder(text: str) -> bool:
    return any(index is None and conversion not in {"%", "n"}
               for _, _, index, conversion in _tokens(text)[0])
