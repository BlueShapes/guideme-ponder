"""Check reviewed, locale-specific roles for every authored caption unit."""
import hashlib
from pathlib import Path

PREFIX = "ae2ponder.ponder.shared."
ROLES = {"NORMAL", "WARNING", "HINT"}
ROOT = Path(__file__).resolve().parents[1]


def caption_strings(strings):
    return {key: text for key, text in strings.items()
            if key.startswith(PREFIX) and key != PREFIX + "caption_page"}


def caption_hash(locale, strings):
    captions = caption_strings(strings)
    text = locale + "\0" + "".join(key + "\0" + captions[key] + "\0" for key in sorted(captions))
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def validate_styles(styles, strings, locale):
    """Do not align roles with English indices: each locale supplies its own complete arrays."""
    errors = []
    expected = caption_strings(strings)
    keys = set(styles) - {"_caption_hash"}
    if keys != set(expected):
        errors.append(f"caption keys differ: missing={sorted(set(expected) - keys)}, extra={sorted(keys - set(expected))}")
    if styles.get("_caption_hash") != caption_hash(locale, strings):
        errors.append("stale caption role review; review every changed unit before refreshing the hash")
    for key in sorted(keys & set(expected)):
        roles = styles[key]
        if not isinstance(roles, list) or len(roles) != len(expected[key].split("\n")):
            errors.append(f"{key}: roles must match this locale's authored display units")
        if not isinstance(roles, list) or any(not isinstance(role, str) or role not in ROLES for role in roles):
            errors.append(f"{key}: roles must be NORMAL, WARNING or HINT")
    return errors


def check():
    # Lazy import keeps this module usable by localization.check and its unit tests.
    from localization import TRANSLATIONS, read, sources
    languages = read(TRANSLATIONS / "languages.json")
    folder = ROOT / "src/main/caption_styles"
    assert {path.stem for path in folder.glob("*.json")} == set(languages), "Caption roles require all 22 locales"
    originals = sources()
    units = 0
    for locale in languages:
        strings = originals[locale] if locale in originals else read(TRANSLATIONS / f"{locale}.json")
        styles = read(folder / f"{locale}.json")
        errors = validate_styles(styles, strings, locale)
        assert not errors, f"Invalid {locale} caption roles: " + "; ".join(errors[:12])
        units += sum(len(roles) for key, roles in styles.items() if key != "_caption_hash")
    print(f"PASS: reviewed caption roles for {len(languages)} languages, {units} authored units")


if __name__ == "__main__":
    check()
