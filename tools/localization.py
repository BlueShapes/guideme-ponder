"""Export translation sources and validate reviewed language bundles (stdlib only)."""
import argparse
import hashlib
import json
import re
from pathlib import Path

from captions import terminal_point_errors, validate_catalogue
from caption_styles import validate_styles

ROOT = Path(__file__).resolve().parents[1]
TRANSLATIONS = ROOT / "src/main/translations"
SUPPORTED = {"ja_jp", "en_us", "hi_in", "zh_hk", "zh_tw", "zh_cn", "ru_ru", "pt_br",
             "pt_pt", "es_es", "fr_fr", "de_de", "cs_cz", "pl_pl", "ms_my", "ro_ro",
             "ar_sa", "id_id", "ko_kr", "vi_vn", "tr_tr", "it_it"}
DESCRIPTION_ROLES = {"NORMAL", "WARNING", "HINT"}
MOD_ID = re.compile(r"[a-z][a-z0-9_]{1,63}")


def read(path):
    def unique(pairs):
        result = {}
        for key, value in pairs:
            if key in result:
                raise ValueError(f"Duplicate key {key} in {path}")
            result[key] = value
        return result
    return json.loads(path.read_text(encoding="utf-8"), object_pairs_hook=unique)


def sources():
    lessons = read(ROOT / "src/main/resources/assets/ae2ponder/guide/lessons.json")
    lessons += read(ROOT / "src/main/resources/assets/ae2ponder/guide/addons.json")
    descriptions = read(ROOT / "src/main/resources/assets/ae2ponder/guide/item_descriptions.json")
    validate_description_metadata(descriptions)
    ids, items = set(), set()
    for entry in descriptions:
        assert re.fullmatch(r"[a-z0-9_]+", entry["id"]) and entry["id"] not in ids, "Invalid/duplicate description ID"
        ids.add(entry["id"])
        assert entry["items"], "Description needs target items"
        for item in entry["items"]:
            assert re.fullmatch(r"[a-z0-9_.-]+:[a-z0-9_./-]+", item) and item not in items, "Invalid/duplicate description item"
            items.add(item)
    result = {}
    for locale, field in [("en_us", "en"), ("ja_jp", "ja")]:
        strings = read(ROOT / f"src/main/templates/lang/{locale}.json")
        for entry in descriptions:
            strings[f"ae2ponder.description.{entry['id']}"] = entry["text"][field]
        for lesson in lessons:
            strings[f"ae2ponder.ponder.{lesson['id']}.header"] = lesson["title"][field]
            strings[f"ae2ponder.lesson.{lesson['id']}.summary"] = lesson["summary"][field]
            for index, step in enumerate(lesson["steps"]):
                strings[f"ae2ponder.ponder.shared.{lesson['id']}_{index}"] = step["text"][field]
        result[locale] = strings
    assert result["en_us"].keys() == result["ja_jp"].keys(), "English/Japanese key mismatch"
    return result


def validate_description_metadata(descriptions):
    """Validate optional item-description availability and per-unit role metadata."""
    for entry in descriptions:
        description_id = entry["id"]
        required_mods = entry.get("requiresMods", [])
        assert isinstance(required_mods, list), f"Required mods must be a list: {description_id}"
        assert all(isinstance(mod, str) and MOD_ID.fullmatch(mod) for mod in required_mods), \
            f"Invalid required mod ID: {description_id}"
        assert len(required_mods) == len(set(required_mods)), f"Duplicate required mod ID: {description_id}"
        if "tone" in entry:
            assert isinstance(entry["tone"], str) and entry["tone"] in DESCRIPTION_ROLES, \
                f"Invalid description role: {description_id}"
        if "tones" in entry:
            tones = entry["tones"]
            assert isinstance(tones, list) and tones, f"Explicit roles must be a non-empty list: {description_id}"
            assert all(isinstance(tone, str) and tone in DESCRIPTION_ROLES for tone in tones), \
                f"Invalid description role: {description_id}"
        assert isinstance(entry.get("text"), dict), f"Missing item description text: {description_id}"
        for locale_field in ("en", "ja"):
            text = entry["text"].get(locale_field)
            assert isinstance(text, str) and text.strip(), f"Missing {locale_field} description: {description_id}"
            units = text.split("\n")
            assert all(unit.strip() for unit in units), f"Empty description unit: {locale_field}/{description_id}"
            if "tones" in entry:
                assert len(entry["tones"]) == len(units), \
                    f"Description roles must match {locale_field} units: {description_id}"


def source_hash(source):
    # Identical to gradle/localization.gradle; a source edit invalidates every bundle.
    text = "".join(locale + "\0" + "".join(key + "\0" + strings[key] + "\0"
                  for key in sorted(strings)) for locale, strings in sorted(source.items()))
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def placeholders(text):
    tokens = re.findall(r"%(?:(\d+)\$)?([sd])", text)
    result = []
    implicit = 0
    for position, kind in tokens:
        if not position:
            implicit += 1
        result.append((int(position) if position else implicit, kind))
    return sorted(result)


def check():
    source = sources()
    descriptions = read(ROOT / "src/main/resources/assets/ae2ponder/guide/item_descriptions.json")
    validate_description_metadata(descriptions)
    digest = source_hash(source)
    expected = source["en_us"]
    languages = read(TRANSLATIONS / "languages.json")
    assert languages.keys() == SUPPORTED, "All 22 supported languages are required"
    reviews = read(TRANSLATIONS / "source-review.json")
    assert reviews.keys() == source.keys(), "English/Japanese source review records are required"
    for locale in source:
        assert reviews[locale] == digest, f"Stale source review: {locale}"
    for locale in languages:
        if locale in source:
            strings = source[locale]
        else:
            bundle = read(TRANSLATIONS / f"{locale}.json")
            assert bundle.pop("_source_hash", None) == digest, f"Stale translation: {locale}; review all source edits"
            strings = bundle
        assert strings.keys() == expected.keys(), f"Missing/extra keys: {locale}: {strings.keys() ^ expected.keys()}"
        for entry in descriptions:
            text = strings[f"ae2ponder.description.{entry['id']}"]
            units = text.split("\n")
            assert all(unit.strip() for unit in units), f"Empty description unit: {locale}/{entry['id']}"
            if "tones" in entry:
                assert len(entry["tones"]) == len(units), \
                    f"Description roles must match localized units: {locale}/{entry['id']}"
        caption_errors = validate_catalogue(strings, locale)
        assert not caption_errors, "Invalid caption units: " + "; ".join(
            f"{key}: {error}" for key, error in caption_errors[:12]
        )
        points = terminal_point_errors(strings, locale)
        assert not points, f"Ordinary sentence-final points in {locale}: {points[:12]}"
        styles = read(ROOT / "src/main/caption_styles" / f"{locale}.json")
        style_errors = validate_styles(styles, strings, locale)
        assert not style_errors, f"Invalid caption roles: {locale}: " + "; ".join(style_errors[:12])
        for key, text in strings.items():
            assert isinstance(text, str) and text.strip(), f"Empty translation: {locale}/{key}"
            assert placeholders(text) == placeholders(expected[key]), f"Wrong placeholders: {locale}/{key}"
    from platform_text import check as check_platform_text
    check_platform_text(ROOT, source)
    print(f"PASS: {len(languages)} languages, {len(expected)} strings each; source freshness and placeholders")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=["source", "check"])
    args = parser.parse_args()
    if args.action == "source":
        source = sources()
        target = ROOT / ".local/localization/source.json"
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(json.dumps({"_source_hash": source_hash(source), **source["en_us"]},
                                   ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(f"Exported {len(source['en_us'])} source strings to {target}")
    else:
        check()
