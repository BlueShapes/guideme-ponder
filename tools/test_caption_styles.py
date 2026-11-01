import copy
import unittest

from caption_styles import caption_hash, validate_styles

KEY = "ae2ponder.ponder.shared.example_0"


class CaptionStylesTest(unittest.TestCase):
    def style(self, locale, strings, roles):
        return {"_caption_hash": caption_hash(locale, strings), KEY: roles}

    def test_locale_specific_units_are_not_english_indices(self):
        en = {KEY: "A fact\nA warning\nOptional advice"}
        ja = {KEY: "事実です\n注意が必要です…\n…条件を確認します\n任意の助言です"}
        self.assertEqual([], validate_styles(self.style("en_us", en, ["NORMAL", "WARNING", "HINT"]), en, "en_us"))
        ja_styles = self.style("ja_jp", ja, ["NORMAL", "WARNING", "WARNING", "HINT"])
        self.assertEqual([], validate_styles(ja_styles, ja, "ja_jp"))
        ja_styles[KEY] = ["NORMAL", "WARNING", "HINT"]
        self.assertTrue(any("authored display units" in error for error in validate_styles(ja_styles, ja, "ja_jp")))

    def test_same_count_text_edit_requires_review(self):
        strings = {KEY: "A fact"}
        styles = self.style("en_us", strings, ["NORMAL"])
        self.assertTrue(any("stale" in error for error in validate_styles(styles, {KEY: "A risk"}, "en_us")))

    def test_missing_extra_or_unknown_roles_fail(self):
        strings = {KEY: "A fact"}
        styles = self.style("en_us", strings, ["NORMAL"])
        for mutate in (lambda data: data.pop(KEY),
                       lambda data: data.update({KEY + "x": ["NORMAL"]}),
                       lambda data: data.update({KEY: ["BLUE"]}),
                       lambda data: data.update({KEY: "NORMAL"}),
                       lambda data: data.update({KEY: [None]})):
            bad = copy.deepcopy(styles)
            mutate(bad)
            self.assertTrue(validate_styles(bad, strings, "en_us"))

    def test_ui_changes_do_not_invalidate_caption_review(self):
        strings = {KEY: "A fact", "ae2ponder.guide.heading": "Heading"}
        styles = self.style("en_us", strings, ["NORMAL"])
        strings["ae2ponder.guide.heading"] = "New heading"
        self.assertEqual([], validate_styles(styles, strings, "en_us"))


if __name__ == "__main__":
    unittest.main()
