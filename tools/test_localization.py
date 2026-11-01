"""Check the maintenance guards with isolated fixtures, without editing translations."""
import contextlib
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import localization
import caption_styles


class LocalizationChecks(unittest.TestCase):
    def setUp(self):
        workspace = Path(__file__).resolve().parents[1]
        fixtures = workspace / ".local/localization/test-fixtures"
        fixtures.mkdir(parents=True, exist_ok=True)
        self.temp = tempfile.TemporaryDirectory(dir=fixtures)
        self.folder = Path(self.temp.name)
        assert self.folder.resolve().is_relative_to(workspace.resolve())
        self.addCleanup(self.temp.cleanup)
        self.source = {
            "en_us": {"title": "First ME network", "status": "%1$s / %2$s"},
            "ja_jp": {"title": "最初のMEネットワーク", "status": "%1$s / %2$s"},
        }
        self.update_reviews()
        self.write("languages.json", dict.fromkeys(sorted(localization.SUPPORTED), "Language"))
        for locale in localization.SUPPORTED - self.source.keys():
            self.write(locale + ".json", {"_source_hash": localization.source_hash(self.source),
                                        "title": "Translated title", "status": "%2$s / %1$s"})
        (self.folder / "src/main/caption_styles").mkdir(parents=True, exist_ok=True)
        descriptions = self.folder / "src/main/resources/assets/ae2ponder/guide"
        descriptions.mkdir(parents=True, exist_ok=True)
        (descriptions / "item_descriptions.json").write_text("[]", encoding="utf-8")

    def write(self, name, value):
        (self.folder / name).write_text(json.dumps(value, ensure_ascii=False), encoding="utf-8")

    def update_reviews(self):
        self.write("source-review.json", dict.fromkeys(self.source, localization.source_hash(self.source)))

    def write_style_fixtures(self):
        for locale in localization.SUPPORTED:
            if locale in self.source:
                strings = self.source[locale]
            else:
                strings = localization.read(self.folder / (locale + ".json"))
                strings.pop("_source_hash", None)
            roles = {key: ["NORMAL"] * len(text.split("\n"))
                     for key, text in strings.items()
                     if key.startswith(caption_styles.PREFIX) and key != caption_styles.PREFIX + "caption_page"}
            roles["_caption_hash"] = caption_styles.caption_hash(locale, strings)
            (self.folder / "src/main/caption_styles" / (locale + ".json")).write_text(
                json.dumps(roles, ensure_ascii=False), encoding="utf-8")

    def check(self, refresh_styles=True):
        if refresh_styles:
            self.write_style_fixtures()
        with patch.object(localization, "TRANSLATIONS", self.folder), \
             patch.object(localization, "ROOT", self.folder):
            with patch.object(localization, "sources", return_value=self.source), \
                 contextlib.redirect_stdout(io.StringIO()):
                localization.check()

    def change(self, **updates):
        path = self.folder / "hi_in.json"
        bundle = localization.read(path)
        bundle.update(updates)
        self.write(path.name, bundle)

    def test_complete_languages_allow_reordered_placeholders(self):
        self.check()

    def test_every_localized_caption_is_checked_for_sentence_units(self):
        english_key = "ae2ponder.ponder.shared.caption_fixture"
        japanese_key = "ae2ponder.ponder.shared.caption_fixture"
        self.source["en_us"][english_key] = "The machine rejects fluids\nSupply power before starting"
        self.source["ja_jp"][japanese_key] = "機械は流体を受け付けません\n起動前に電力を供給してください"
        self.update_reviews()
        digest = localization.source_hash(self.source)
        for locale in localization.SUPPORTED - self.source.keys():
            bundle = localization.read(self.folder / (locale + ".json"))
            bundle[english_key] = "La máquina rechaza fluidos\nSuministra energía antes de iniciarla"
            bundle["_source_hash"] = digest
            self.write(locale + ".json", bundle)

        self.check()

        bundle = localization.read(self.folder / "hi_in.json")
        bundle[english_key] = "यह मशीन तरल पदार्थ स्वीकार नहीं करती। बिजली दें।"
        self.write("hi_in.json", bundle)
        with self.assertRaisesRegex(AssertionError, "multiple sentences"):
            self.check()

    def test_caption_roles_are_checked_against_locale_units_and_review_hash(self):
        key = "ae2ponder.ponder.shared.caption_fixture"
        self.source["en_us"][key] = "The machine rejects fluids\nSupply power before starting"
        self.source["ja_jp"][key] = "機械は流体を受け付けません\n起動前に電力を供給してください"
        self.update_reviews()
        digest = localization.source_hash(self.source)
        for locale in localization.SUPPORTED - self.source.keys():
            bundle = localization.read(self.folder / (locale + ".json"))
            bundle[key] = "La máquina rechaza fluidos\nSuministra energía antes de iniciarla"
            bundle["_source_hash"] = digest
            self.write(locale + ".json", bundle)
        self.write_style_fixtures()

        styles = localization.read(self.folder / "src/main/caption_styles/hi_in.json")
        styles[key] = ["NORMAL"]
        self.write("src/main/caption_styles/hi_in.json", styles)
        with self.assertRaisesRegex(AssertionError, "Invalid caption roles: hi_in"):
            self.check(refresh_styles=False)

        self.write_style_fixtures()
        styles = localization.read(self.folder / "src/main/caption_styles/hi_in.json")
        styles["_caption_hash"] = "stale"
        self.write("src/main/caption_styles/hi_in.json", styles)
        with self.assertRaisesRegex(AssertionError, "stale caption role review"):
            self.check(refresh_styles=False)

    def test_source_edit_requires_review_in_other_languages(self):
        self.source["en_us"]["title"] = "New lesson content"
        self.update_reviews()
        with self.assertRaisesRegex(AssertionError, "Stale translation"):
            self.check()

    def test_source_edit_requires_english_and_japanese_review(self):
        self.source["en_us"]["title"] = "New lesson content"
        with self.assertRaisesRegex(AssertionError, "Stale source review"):
            self.check()

    def test_missing_translation_cannot_fall_back(self):
        path = self.folder / "hi_in.json"
        bundle = localization.read(path)
        del bundle["title"]
        self.write(path.name, bundle)
        with self.assertRaisesRegex(AssertionError, "Missing/extra keys"):
            self.check()

    def test_wrong_placeholder_is_rejected(self):
        self.change(status="%1$s / %1$s")
        with self.assertRaisesRegex(AssertionError, "Wrong placeholders"):
            self.check()

    def test_mixed_placeholders_cannot_repeat_first_argument(self):
        self.source["en_us"]["status"] = "%s / %s"
        self.source["ja_jp"]["status"] = "%s / %s"
        self.update_reviews()
        for locale in localization.SUPPORTED - self.source.keys():
            self.write(locale + ".json", {"_source_hash": localization.source_hash(self.source),
                                        "title": "Translated title", "status": "%1$s / %s"})
        with self.assertRaisesRegex(AssertionError, "Wrong placeholders"):
            self.check()

    def test_empty_translation_is_rejected(self):
        self.change(title="   ")
        with self.assertRaisesRegex(AssertionError, "Empty translation"):
            self.check()

    def test_removed_language_is_rejected(self):
        self.write("languages.json", {"en_us": "English", "ja_jp": "日本語"})
        with self.assertRaisesRegex(AssertionError, "All 22"):
            self.check()

    def test_previous_language_scope_is_rejected(self):
        additions = {"fr_fr", "ar_sa", "id_id", "ko_kr", "vi_vn", "tr_tr", "it_it"}
        self.write("languages.json", dict.fromkeys(sorted(localization.SUPPORTED - additions), "Language"))
        with self.assertRaisesRegex(AssertionError, "All 22"):
            self.check()

    def test_duplicate_json_key_is_rejected(self):
        path = self.folder / "duplicate.json"
        path.write_text('{"title": "One", "title": "Two"}', encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "Duplicate key"):
            localization.read(path)

    def test_optional_subtitle_changes_invalidate_translation_reviews(self):
        guide = self.folder / "src/main/resources/assets/ae2ponder/guide"
        templates = self.folder / "src/main/templates/lang"
        guide.mkdir(parents=True, exist_ok=True)
        templates.mkdir(parents=True)
        for locale in ("en_us", "ja_jp"):
            (templates / (locale + ".json")).write_text('{}', encoding="utf-8")
        (guide / "lessons.json").write_text('[]', encoding="utf-8")
        (guide / "item_descriptions.json").write_text('[]', encoding="utf-8")
        lesson = {"id": "optional_cell", "title": {"en": "Cell", "ja": "セル"},
                  "summary": {"en": "Store items", "ja": "アイテムを保存"},
                  "steps": [{"text": {"en": "Finite capacity", "ja": "容量は有限"}}]}
        path = guide / "addons.json"
        path.write_text(json.dumps([lesson]), encoding="utf-8")
        with patch.object(localization, "ROOT", self.folder):
            original = localization.sources()
            self.assertEqual(original["ja_jp"]["ae2ponder.ponder.shared.optional_cell_0"], "容量は有限")
            lesson["steps"][0]["text"]["en"] = "Finite capacity; unlimited types"
            path.write_text(json.dumps([lesson]), encoding="utf-8")
            self.assertNotEqual(localization.source_hash(original),
                                localization.source_hash(localization.sources()))

    def test_item_description_changes_require_translation_review(self):
        guide = self.folder / "src/main/resources/assets/ae2ponder/guide"
        templates = self.folder / "src/main/templates/lang"
        guide.mkdir(parents=True, exist_ok=True)
        templates.mkdir(parents=True)
        for locale in ("en_us", "ja_jp"):
            (templates / (locale + ".json")).write_text('{}', encoding="utf-8")
        for name in ("lessons.json", "addons.json"):
            (guide / name).write_text('[]', encoding="utf-8")
        entry = {"id": "anchor", "items": ["ae2:cable_anchor"],
                 "text": {"en": "Blocks a connection", "ja": "接続を遮断します。"}}
        path = guide / "item_descriptions.json"
        path.write_text(json.dumps([entry]), encoding="utf-8")
        with patch.object(localization, "ROOT", self.folder):
            original = localization.sources()
            self.assertEqual(original["ja_jp"]["ae2ponder.description.anchor"], "接続を遮断します。")
            entry["text"]["en"] = "Blocks the connection on its face"
            path.write_text(json.dumps([entry]), encoding="utf-8")
            self.assertNotEqual(localization.source_hash(original),
                                localization.source_hash(localization.sources()))
            entry["items"].append("ae2:cable_anchor")
            path.write_text(json.dumps([entry]), encoding="utf-8")
            with self.assertRaisesRegex(AssertionError, "duplicate description item"):
                localization.sources()

    def test_item_description_roles_match_authored_line_units(self):
        valid = [{"id": "split", "items": ["ae2:anchor"], "tones": ["NORMAL", "WARNING"],
                  "text": {"en": "First unit\nSecond unit", "ja": "一つ目\n二つ目"}}]
        localization.validate_description_metadata(valid)
        valid[0]["text"]["ja"] = "一つ目"
        with self.assertRaisesRegex(AssertionError, "roles must match ja units"):
            localization.validate_description_metadata(valid)

    def test_item_description_rejects_invalid_required_mod_id(self):
        invalid = [{"id": "optional", "items": ["ae2:anchor"], "requiresMods": ["Bad-Mod"],
                    "text": {"en": "Description", "ja": "説明"}}]
        with self.assertRaisesRegex(AssertionError, "Invalid required mod ID"):
            localization.validate_description_metadata(invalid)


if __name__ == "__main__":
    unittest.main()
