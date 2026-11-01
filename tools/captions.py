"""Authoring checks for explicit Ponder caption display units.

Runtime code splits only on literal newlines. This module checks that authors used
those boundaries between complete sentences or consistently marked continuations.
It deliberately does not try to choose split positions from punctuation or width.
"""
from __future__ import annotations

import argparse
import re

CONTINUATION = "…"
_TERMINATORS = frozenset(".!?。．！？؟۔।॥｡")
_REMOVABLE_POINTS = frozenset(".。．｡۔।॥")
_CLOSERS = frozenset("\"'”’»)]}）］｝」』】")
_CAPTION_PREFIX = "ae2ponder.ponder.shared."
_CAPTION_TEMPLATE_KEY = "ae2ponder.ponder.shared.caption_page"

_ABBREVIATIONS = re.compile(
    r"(?i)(?<![\w])(?:e\.g|i\.e|mr|mrs|ms|dr|prof|vs|etc|approx|no)\."
    r"(?!\w)"
)
_VERSION = re.compile(r"(?<!\d)(?:\d+\.)+\d+(?!\d)")
_DECIMAL = re.compile(r"(?<!\w)\d+\.\d+(?!\w)")
_RESOURCE_ID = re.compile(r"(?<![\w.])[a-z0-9_.-]+:[a-z0-9_/-]+(?:\.[a-z0-9_/-]+)*", re.IGNORECASE)
_FILENAME = re.compile(r"(?<!\w)(?:[\w-]+[\\/])*[\w-]+(?:\.[\w-]+)+")
_URL = re.compile(r"(?i)\b(?:https?://|ftp://)[^\s<>]+")
_DOMAIN = re.compile(r"(?i)\b(?:[a-z0-9-]+\.)+[a-z]{2,}\b")
_EN_DANGLING_ENDINGS = frozenset({
    "a", "an", "and", "are", "as", "at", "be", "because", "been", "being", "by",
    "can", "cannot", "could", "did", "do", "does", "for", "from", "had", "has", "have", "if",
    "in", "into", "is", "it", "may", "might", "must", "of", "on", "or", "should",
    "that", "the", "their", "then", "these", "this", "those", "to", "was", "were",
    "when", "which", "while", "who", "will", "with", "would",
})
_EN_ORDINARY_DANGLING_ENDINGS = _EN_DANGLING_ENDINGS - {
    "at", "by", "for", "from", "in", "into", "it", "of", "on", "that", "then", "these",
    "this", "those", "to", "with",
}
_EN_FINITE_VERBS = frozenset({
    "activate", "activates", "assign", "assigns", "attach", "attaches", "buffer", "buffers",
    "build", "builds", "combine", "combines", "complete", "completes", "configure", "configures", "consume", "consumes",
    "convert", "converts", "determine", "determines", "edit", "edits", "empty", "empties",
    "face", "faces", "fit", "fits", "go", "goes", "imprint", "imprints", "include", "includes",
    "lie", "lies", "list", "lists", "partition", "partitions", "print", "prints", "produce", "produces",
    "record", "records", "restore", "restores", "search", "searches", "sit", "sits", "transfer", "transfers",
    "replenish", "replenishes", "discharge", "discharges",
    "access", "accesses", "act", "acts", "apply", "applies", "assume", "assumes", "break", "breaks",
    "cannot", "carry", "carries", "change", "changes", "cycle", "cycles", "define", "defines",
    "delete", "deletes", "differ", "differs", "drop", "drops", "enable", "enables", "enter", "enters",
    "feed", "feeds", "give", "gives", "hold", "holds", "join", "joins", "keep", "keeps", "let", "lets",
    "limit", "limits", "link", "links", "monitor", "monitors", "move", "moves", "offer", "offers",
    "point", "points", "reject", "rejects", "replace", "replaces", "request", "requests", "respond", "responds",
    "return", "returns", "rotate", "rotates", "select", "selects", "send", "sends", "separate", "separates",
    "serve", "serves", "stay", "stays", "suit", "suits", "turn", "turns",
    "accept", "accepts", "add", "adds", "allow", "allows", "are", "be", "become", "becomes",
    "belong", "belongs", "can", "cause", "causes", "connect", "connects", "contain", "contains",
    "create", "creates", "does", "do", "draw", "draws", "flow", "flows", "get", "gets", "has",
    "have", "is", "make", "makes", "must", "need", "needs", "power", "powers", "provide",
    "provides", "receive", "receives", "require", "requires", "run", "runs", "set", "sets",
    "should", "start", "starts", "stop", "stops", "store", "stores", "supply", "supplies",
    "take", "takes", "use", "uses", "was", "were", "will", "work", "works", "would",
})
_JA_CONTINUATIVE_ENDINGS = (
    "ところで", "ものの", "けれども", "けれど", "それでも", "ため", "ので", "から", "なら",
    "たら", "れば", "つつも", "つつ", "し", "て", "と", "が",
)
_JA_DANGLING_ENDINGS = ("は", "を", "に", "へ", "の", "で", "より", "まで", "だけ")


def split_units(text: str) -> tuple[str, ...]:
    """Return authored units while preserving empty and trailing units for linting."""
    return tuple(text.split("\n"))


def search_text(text: str) -> str:
    """Normalize explicit display-unit boundaries while retaining all authored words."""
    return text.replace(f"{CONTINUATION}\n{CONTINUATION}", " ").replace("\n", " ")


def _protected_periods(text: str) -> set[int]:
    protected: set[int] = set()
    for pattern in (_ABBREVIATIONS, _VERSION, _DECIMAL, _RESOURCE_ID, _FILENAME, _DOMAIN):
        for match in pattern.finditer(text):
            protected.update(index for index in range(match.start(), match.end()) if text[index] == ".")
    for match in _URL.finditer(text):
        end = match.end()
        while end > match.start() and text[end - 1] in ".,;:!?)]}»”’\"'":
            end -= 1
        protected.update(index for index in range(match.start(), end) if text[index] == ".")
    return protected


def _is_terminal_point(text: str, index: int, locale: str, protected: set[int]) -> bool:
    point = text[index]
    if point not in _REMOVABLE_POINTS or (point == "." and index in protected):
        return False
    if point == "." and ((index > 0 and text[index - 1] == ".")
                         or (index + 1 < len(text) and text[index + 1] == ".")):
        return False
    if point in "。．｡۔।॥":
        return True

    following = index + 1
    while following < len(text) and text[following] in _CLOSERS:
        following += 1
    had_space = False
    while following < len(text) and text[following].isspace():
        had_space = True
        following += 1
    if following >= len(text):
        return True
    if had_space:
        return True
    language = locale.split("_", 1)[0].lower()
    latin = {"en", "fr", "de", "es", "pt", "it", "cs", "pl", "tr", "vi", "id", "ms", "ro"}
    return language not in latin and text[following].isalpha()


def strip_caption_terminal_point(text: str, locale: str) -> str:
    """Remove one ordinary final sentence point, preserving ellipses and token dots."""
    protected = _protected_periods(text)
    end = len(text.rstrip())
    while end > 0 and text[end - 1] in _CLOSERS:
        end -= 1
    if end == 0 or not _is_terminal_point(text, end - 1, locale, protected):
        return text
    index = end - 1
    return text[:index] + text[index + 1:]


def strip_caption_terminal_points(text: str, locale: str) -> str:
    """Strip ordinary terminal points per authored LF unit without changing boundaries."""
    return "\n".join(strip_caption_terminal_point(unit, locale) for unit in split_units(text))


def strip_prose_terminal_points(text: str, locale: str, sentence_separator: str = " ") -> str:
    """Remove sentence-final points and keep following sentences separated by whitespace."""
    protected = _protected_periods(text)
    positions = [index for index, character in enumerate(text)
                 if _is_terminal_point(text, index, locale, protected)]
    edits = []
    for index in positions:
        close_end = index + 1
        while close_end < len(text) and text[close_end] in _CLOSERS:
            close_end += 1
        next_text = close_end
        while next_text < len(text) and text[next_text].isspace():
            next_text += 1
        if next_text < len(text):
            replacement = text[index + 1:close_end] + sentence_separator
            edits.append((index, next_text, replacement))
        else:
            edits.append((index, index + 1, ""))
    output = text
    for start, end, replacement in reversed(edits):
        output = output[:start] + replacement + output[end:]
    return output


def _has_multiple_sentences(text: str, locale: str) -> bool:
    protected = _protected_periods(text)
    language = locale.split("_", 1)[0].lower()
    for index, character in enumerate(text):
        if character not in _TERMINATORS or (character == "." and index in protected):
            continue
        if character == "." and index + 1 < len(text) and text[index + 1] == ".":
            continue
        following = index + 1
        while following < len(text) and text[following] in _CLOSERS:
            following += 1
        had_space = False
        while following < len(text) and text[following].isspace():
            had_space = True
            following += 1
        if following >= len(text):
            continue
        if had_space or character not in ".!?":
            return True
        # Latin sentences conventionally have whitespace after their terminator.
        if language not in {"en", "fr", "de", "es", "pt", "it", "cs", "pl", "tr", "vi", "id", "ms", "ro"}:
            if text[following].isalpha():
                return True
    return False


def _english_subject_only(text: str) -> bool:
    words = re.findall(r"[A-Za-z]+(?:['’-][A-Za-z]+)*", text)
    if len(words) < 2 or words[0].lower() not in {"the", "a", "an", "this", "that", "these", "those"}:
        return False
    return not any(word.lower() in _EN_FINITE_VERBS for word in words[1:])


def _continuation_fragment_errors(content: str, locale: str) -> list[str]:
    language = locale.split("_", 1)[0].lower()
    errors: list[str] = []
    words = re.findall(r"[A-Za-z]+(?:['’-][A-Za-z]+)*", content)
    if language == "en" and words:
        if words[-1].lower() in _EN_DANGLING_ENDINGS:
            errors.append(f"continuation ends with incomplete English word '{words[-1]}'")
        if _english_subject_only(content):
            errors.append("continuation is only an English subject phrase")
        if len(words) == 1 and words[0].lower() not in _EN_FINITE_VERBS:
            errors.append("continuation is an isolated English word, not a readable phrase")
    if language == "ja":
        if content.endswith(_JA_DANGLING_ENDINGS) and not content.endswith(_JA_CONTINUATIVE_ENDINGS):
            errors.append("continuation ends with an incomplete Japanese particle")
    return errors


def _english_unit_has_dangling_auxiliary(content: str) -> bool:
    end = len(content.rstrip())
    while end > 0 and content[end - 1] in _CLOSERS:
        end -= 1
    if end > 0 and content[end - 1] == "?":
        return False
    terminal = content[:end].rstrip()
    while terminal and terminal[-1] in _REMOVABLE_POINTS | frozenset("!?"):
        terminal = terminal[:-1].rstrip()
    if not terminal or not terminal[-1].isalpha():
        return False
    words = re.findall(r"[A-Za-z]+(?:['’-][A-Za-z]+)*", terminal)
    return bool(words) and words[-1].lower() in _EN_ORDINARY_DANGLING_ENDINGS


def validate_caption(text: str, locale: str) -> list[str]:
    """Return authoring errors for one localized caption with explicit newline units."""
    units = split_units(text)
    errors: list[str] = []
    if not text:
        return ["caption is empty"]

    starts: list[bool] = []
    ends: list[bool] = []
    contents: list[str] = []
    for index, unit in enumerate(units):
        if not unit.strip():
            errors.append(f"unit {index + 1} is blank")
        if unit != unit.strip():
            errors.append(f"unit {index + 1} has leading or trailing whitespace")
        start = unit.startswith(CONTINUATION)
        end = unit.endswith(CONTINUATION)
        if unit.startswith("...") or unit.endswith("..."):
            errors.append(f"unit {index + 1} uses ASCII dots; continuation markers must be '{CONTINUATION}'")
        starts.append(start)
        ends.append(end)
        content = unit[1:] if start else unit
        content = content[:-1] if end else content
        contents.append(content)

    for index, (start, end) in enumerate(zip(starts, ends)):
        if start and (index == 0 or not ends[index - 1]):
            errors.append(f"unit {index + 1} has an orphan or mismatched leading continuation marker")
        if end and (index == len(units) - 1 or not starts[index + 1]):
            errors.append(f"unit {index + 1} has an orphan or mismatched trailing continuation marker")

    for index, (unit, content) in enumerate(zip(units, contents)):
        if not content.strip():
            errors.append(f"unit {index + 1} contains only a continuation marker")
            continue
        if _has_multiple_sentences(content, locale):
            errors.append(f"unit {index + 1} contains multiple sentences; put each complete sentence in its own unit")

        if ends[index]:
            errors.extend(f"unit {index + 1}: {error}" for error in _continuation_fragment_errors(content, locale))
            continue
        language = locale.split("_", 1)[0].lower()
        if language == "en" and _english_unit_has_dangling_auxiliary(content):
            errors.append(f"unit {index + 1} ends with an incomplete English word")
        if language == "en" and _english_subject_only(content):
            errors.append(f"unit {index + 1} is only an English subject phrase")
        if language == "ja" and content.endswith(_JA_DANGLING_ENDINGS + _JA_CONTINUATIVE_ENDINGS):
            errors.append(f"unit {index + 1} ends with an incomplete Japanese particle")
        if starts[index]:
            errors.extend(f"unit {index + 1}: {error}" for error in _continuation_fragment_errors(content, locale))
    return errors


def validate_catalogue(strings: dict[str, str], locale: str) -> list[tuple[str, str]]:
    """Validate all lesson caption keys in a localized source or language bundle."""
    errors: list[tuple[str, str]] = []
    for key, text in strings.items():
        if key.startswith(_CAPTION_PREFIX) and key != _CAPTION_TEMPLATE_KEY:
            errors.extend((key, error) for error in validate_caption(text, locale))
    return errors


def terminal_point_errors(strings: dict[str, str], locale: str) -> list[str]:
    """Apply our prose policy without changing borrowed UI strings or technical dots."""
    errors = []
    for key, text in strings.items():
        caption = key.startswith(_CAPTION_PREFIX) and key != _CAPTION_TEMPLATE_KEY
        prose = (key.startswith("ae2ponder.description.")
                 or key.startswith("ae2ponder.lesson.") and key.endswith(".summary")
                 or key.startswith("ae2ponder.ponder.tag.") and key.endswith(".description")
                 or key in {"ae2ponder.guide.intro", "ae2ponder.guide.controls", "ae2ponder.guide.description"})
        if caption and strip_caption_terminal_points(text, locale) != text:
            errors.append(key)
        elif prose and strip_prose_terminal_points(text, locale) != text:
            errors.append(key)
    return errors


def check_all_locales() -> None:
    """Lint authored and translated lesson captions without checking freshness hashes."""
    # Lazy import keeps localization.py free to import this module's validation functions.
    import localization

    source = localization.sources()
    languages = localization.read(localization.TRANSLATIONS / "languages.json")
    checked_strings = 0
    checked_units = 0
    errors: list[str] = []
    for locale in languages:
        if locale in source:
            strings = source[locale]
        else:
            bundle = localization.read(localization.TRANSLATIONS / f"{locale}.json")
            bundle.pop("_source_hash", None)
            strings = bundle
        caption_errors = validate_catalogue(strings, locale)
        errors.extend(f"{locale}/{key}: ordinary sentence-final point" for key in terminal_point_errors(strings, locale))
        caption_strings = (text for key, text in strings.items()
                           if key.startswith(_CAPTION_PREFIX) and key != _CAPTION_TEMPLATE_KEY)
        for text in caption_strings:
            checked_strings += 1
            checked_units += len(split_units(text))
        errors.extend(f"{locale}/{key}: {error}" for key, error in caption_errors)
    if errors:
        raise SystemExit("Caption authoring errors:\n" + "\n".join(errors[:80]))
    print(f"PASS: {checked_strings} localized caption strings and {checked_units} authored units "
          f"across {len(languages)} languages")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=["check-all-locales"])
    args = parser.parse_args()
    if args.action == "check-all-locales":
        check_all_locales()
