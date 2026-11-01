"""Validate authored loader-specific text without duplicating whole language bundles."""
import hashlib
from pathlib import Path

from captions import terminal_point_errors, validate_catalogue

ROLES = {"NORMAL", "WARNING", "HINT"}


def text_hash(text):
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def review_hash(locale, key, text, roles):
    return text_hash("\0".join([locale, key, text, *roles]) + "\0")


def validate_overrides(document, strings, source_hash, descriptions, placeholders):
    """Return only reviewed text/role changes; leave shared originals untouched."""
    assert set(document) == {"_source_hash", "locales"}, "Invalid platform override fields"
    assert document["_source_hash"] == source_hash, "Stale platform source review"
    assert isinstance(document["locales"], dict) and document["locales"], "Empty platform overrides"
    by_key = {"ae2ponder.description." + entry["id"]: entry for entry in descriptions}
    count = 0
    for locale, entries in document["locales"].items():
        assert locale in strings, f"Unsupported platform locale: {locale}"
        assert isinstance(entries, dict) and entries, f"Empty platform locale: {locale}"
        for key, entry in entries.items():
            assert key in strings[locale], f"Unknown platform text key: {locale}/{key}"
            assert set(entry) == {"baseTextSha256", "text", "roles", "_review_hash"}, f"Invalid override fields: {locale}/{key}"
            assert entry["baseTextSha256"] == text_hash(strings[locale][key]), f"Stale platform base text: {locale}/{key}"
            text, roles = entry["text"], entry["roles"]
            assert isinstance(text, str) and text.strip() and all(unit.strip() for unit in text.split("\n")), f"Empty platform text: {locale}/{key}"
            assert isinstance(roles, list) and len(roles) == len(text.split("\n")), f"Platform roles must match units: {locale}/{key}"
            assert all(role in ROLES for role in roles), f"Invalid platform role: {locale}/{key}"
            assert entry["_review_hash"] == review_hash(locale, key, text, roles), f"Stale platform text/role review: {locale}/{key}"
            assert placeholders(text) == placeholders(strings[locale][key]), f"Wrong platform placeholders: {locale}/{key}"
            assert not terminal_point_errors({key: text}, locale), f"Platform sentence-final points: {locale}/{key}"
            if key.startswith("ae2ponder.ponder.shared.") and not key.endswith(".caption_page"):
                assert not validate_catalogue({key: text}, locale), f"Invalid platform caption units: {locale}/{key}"
            else:
                assert key in by_key, f"Only explanatory captions/descriptions may have platform overrides: {key}"
                description = by_key[key]
                expected = description.get("tones", [description.get("tone", "NORMAL")] * len(roles))
                assert roles == expected, f"Platform description roles must match shared metadata: {locale}/{key}"
            count += 1
    return count


def check(root=None, originals=None):
    # Lazy import keeps localization.check and isolated test fixtures independent.
    import localization
    root = Path(root) if root is not None else localization.ROOT
    paths = {platform: root / f"src/{platform}/translations/text_overrides.json" for platform in ("neoforge", "forge")}
    if not any(path.is_file() for path in paths.values()):
        return {}
    originals = originals if originals is not None else localization.sources()
    languages = localization.read(root / "src/main/translations/languages.json")
    strings = {locale: originals[locale] if locale in originals else localization.read(root / f"src/main/translations/{locale}.json") for locale in languages}
    descriptions = localization.read(root / "src/main/resources/assets/ae2ponder/guide/item_descriptions.json")
    counts = {}
    for platform, path in paths.items():
        if path.is_file():
            counts[platform] = validate_overrides(localization.read(path), strings, localization.source_hash(originals), descriptions, localization.placeholders)
    return counts


if __name__ == "__main__":
    print(f"PASS: reviewed platform text overrides {check()}")
