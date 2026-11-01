"""Regression checks for authored caption boundaries and the production Java splitter."""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

from captions import (search_text, split_units, strip_caption_terminal_point,
                      strip_caption_terminal_points, strip_prose_terminal_points,
                      terminal_point_errors, validate_caption, validate_catalogue)


class CaptionAuthoringChecks(unittest.TestCase):
    def assert_valid(self, text, locale):
        self.assertEqual([], validate_caption(text, locale), text)

    def assert_invalid(self, text, locale, fragment):
        errors = validate_caption(text, locale)
        self.assertTrue(any(fragment in error for error in errors), (text, errors))

    def test_two_complete_sentences_are_distinct_authored_units(self):
        caption = "The processing machine does not accept fluids.\nSupply power before starting."
        self.assertEqual(("The processing machine does not accept fluids.", "Supply power before starting."),
                         split_units(caption))
        self.assert_valid(caption, "en_us")

    def test_unmarked_fragment_is_rejected_even_when_followed_by_another_unit(self):
        self.assert_invalid("The machine does\nNot accept fluids.", "en_us", "incomplete English word")
        self.assert_invalid("The processing machine does.\nNot accept fluids.", "en_us", "incomplete English word")
        self.assert_valid("Remove it.", "en_us")
        self.assert_valid("The machine accepts fluids", "en_us")
        self.assert_valid("この装置は流体を受け付けません", "ja_jp")
        self.assert_valid("यह मशीन तरल पदार्थ स्वीकार नहीं करती", "hi_in")
        self.assert_valid("هذه الآلة لا تقبل السوائل", "ar_sa")
        self.assert_invalid("この装置は", "ja_jp", "incomplete Japanese particle")
        self.assert_invalid("電力が不足するので", "ja_jp", "incomplete Japanese particle")
        self.assert_invalid("The processing machine", "en_us", "subject phrase")
        self.assert_invalid("The processing machine cannot", "en_us", "incomplete English word")

    def test_unreviewed_joined_sentences_are_rejected(self):
        self.assert_invalid("The processing machine does not accept fluids. Supply power before starting.",
                            "en_us", "multiple sentences")

    def test_descriptive_predicates_do_not_require_imperative_wording(self):
        self.assert_valid("A Mysterious Cube lies at the meteorite’s center", "en_us")
        self.assert_valid("The component combines with its matching housing", "en_us")
        self.assert_valid("The IO port transfers contents between the DISK and network storage", "en_us")
        self.assert_valid("A charger replenishes the staff’s AE", "en_us")
        self.assert_valid("A melee hit discharges the staff above 300 AE", "en_us")
        self.assert_invalid("The matching Quantum Cell Component", "en_us", "subject phrase")

    def test_decimal_version_abbreviations_and_registry_id_are_not_sentence_boundaries(self):
        text = "Dr. Smith uses e.g. 1.5 units in Minecraft 1.21.1 with ae2:pattern_provider."
        self.assert_valid(text, "en_us")
        self.assert_valid('Use the title "Dr." before the name.', "en_us")
        self.assert_invalid("Use ae2:pattern_provider. Supply power.", "en_us", "multiple sentences")
        self.assert_valid("Spatial cell sizes are 2³, 16³, and 128³.", "en_us")

    def test_cjk_danda_and_arabic_sentence_punctuation(self):
        self.assert_valid("この装置は流体を受け付けません。", "ja_jp")
        self.assert_valid("यह मशीन तरल पदार्थ स्वीकार नहीं करती।", "hi_in")
        self.assert_valid("هذه الآلة لا تقبل السوائل؟", "ar_sa")
        self.assert_invalid("最初の文です。次の文です。", "ja_jp", "multiple sentences")

    def test_explicit_two_and_three_unit_continuations_are_paired(self):
        two = "応力源が足りず、応力が超過してしまうと…\n…それにつながった装置はすべて停止します。"
        three = "蓄電量が消費量を下回ると…\n…ネットワークの電力が不足し…\n…接続した装置が停止します。"
        self.assert_valid(two, "ja_jp")
        self.assert_valid(three, "ja_jp")
        self.assertEqual("応力源が足りず、応力が超過してしまうと それにつながった装置はすべて停止します。",
                         search_text(two))
        self.assertEqual("応力源が足りず、応力が超過してしまうとそれにつながった装置はすべて停止します。",
                         "".join(unit.strip("…") for unit in split_units(two)))

    def test_search_normalization_keeps_words_and_literal_ellipsis(self):
        self.assertEqual("one two three", search_text("one…\n…two\nthree"))
        self.assertEqual("Wait… for the next step.", search_text("Wait… for the next step."))

    def test_caption_page_format_template_is_not_treated_as_lesson_text(self):
        self.assertEqual([], validate_catalogue({"ae2ponder.ponder.shared.caption_page": "%s"}, "en_us"))

    def test_terminal_points_are_removed_without_touching_protected_tokens_or_other_marks(self):
        self.assertEqual("The machine works", strip_caption_terminal_point("The machine works.", "en_us"))
        self.assertEqual('“The machine works”', strip_caption_terminal_point('“The machine works.”', "en_us"))
        self.assertEqual("Use Dr. Smith's 2.5% mixture in Minecraft 1.20.1 with ae2:pattern_provider, config.yml, and https://example.org/path",
                         strip_caption_terminal_point("Use Dr. Smith's 2.5% mixture in Minecraft 1.20.1 with ae2:pattern_provider, config.yml, and https://example.org/path.", "en_us"))
        self.assertEqual("Use e.g. this item", strip_caption_terminal_point("Use e.g. this item.", "en_us"))
        self.assertEqual("Wait...", strip_caption_terminal_point("Wait...", "en_us"))
        self.assertEqual("この装置は流体を受け付けません", strip_caption_terminal_point("この装置は流体を受け付けません。", "ja_jp"))
        self.assertEqual("यह मशीन तरल पदार्थ स्वीकार नहीं करती", strip_caption_terminal_point("यह मशीन तरल पदार्थ स्वीकार नहीं करती।", "hi_in"))
        self.assertEqual("هذه الآلة لا تقبل السوائل", strip_caption_terminal_point("هذه الآلة لا تقبل السوائل۔", "ar_sa"))
        self.assertEqual("受け付けません？！", strip_caption_terminal_point("受け付けません？！", "ja_jp"))
        self.assertEqual("待って…", strip_caption_terminal_point("待って…", "ja_jp"))
        self.assertEqual("first\nsecond", strip_caption_terminal_points("first.\nsecond。", "ja_jp"))

    def test_prose_sentence_separation_uses_requested_whitespace(self):
        self.assertEqual("One Two", strip_prose_terminal_points("One. Two.", "en_us", " "))
        self.assertEqual("One\nTwo", strip_prose_terminal_points("One. Two.", "en_us", "\n"))
        self.assertEqual("One\n\nTwo", strip_prose_terminal_points("One. Two.", "en_us", "\n\n"))

    def test_project_prose_policy_excludes_borrowed_ui_and_protected_dots(self):
        strings = {"ae2ponder.ponder.shared.example_0": "Use Minecraft 1.20.1\nOpen config.yml",
                   "ponder.ui.example": "Borrowed UI.", "ae2ponder.chapter.network": "2. Networks",
                   "ae2ponder.description.example": "Use https://example.org/path"}
        self.assertEqual([], terminal_point_errors(strings, "en_us"))
        strings["ae2ponder.description.example"] += "."
        self.assertEqual(["ae2ponder.description.example"], terminal_point_errors(strings, "en_us"))

    def test_orphan_mismatch_blank_and_unmarked_continuation_are_rejected(self):
        self.assert_invalid("…The machines stop.", "en_us", "orphan or mismatched leading")
        self.assert_invalid("Power is insufficient…", "en_us", "orphan or mismatched trailing")
        self.assert_invalid("Power is insufficient…\nThe machines stop.", "en_us", "mismatched trailing")
        self.assert_invalid("Power is insufficient.\n\nThe machines stop.", "en_us", "is blank")
        self.assert_invalid("Power is insufficient...\n...The machines stop.", "en_us", "ASCII dots")

    def test_obvious_subject_only_and_dangling_auxiliary_parts_are_rejected(self):
        self.assert_invalid("The processing machine…\n…does not accept fluids.", "en_us", "subject phrase")
        self.assert_invalid("The processing machine does…\n…not accept fluids.", "en_us", "incomplete English word")
        self.assert_invalid("mach…\n…ine stops.", "en_us", "isolated English word")

    def test_long_sentence_and_long_word_are_never_split_by_the_linter_helper(self):
        long_word = "Supercalifragilisticexpialidocious describes one unusually long word."
        long_sentence = "The processing machine rejects fluids when its input configuration requires a different resource."
        self.assertEqual((long_word,), split_units(long_word))
        self.assertEqual((long_sentence,), split_units(long_sentence))
        self.assert_valid(long_word, "en_us")
        self.assert_valid(long_sentence, "en_us")

    def test_runtime_java_caption_helpers_preserve_units_and_bound_guide_layout(self):
        javac, java = shutil.which("javac"), shutil.which("java")
        if not javac or not java:
            self.skipTest("JDK is unavailable; Gradle compilation covers the Java helper")
        workspace = Path(__file__).resolve().parents[1]
        source = workspace / "src/main/java/dev/ae2guide/client/CaptionPages.java"
        layout_source = workspace / "src/main/java/dev/ae2guide/client/GuideTextLayout.java"
        harness = r'''import dev.ae2guide.client.CaptionPages;
import dev.ae2guide.client.GuideTextLayout;
import java.util.List;
public class CaptionPagesHarness {
    private static void expect(String text, String... expected) {
        List<String> actual = CaptionPages.split(text);
        if (!actual.equals(List.of(expected))) throw new AssertionError(actual + " != " + List.of(expected));
    }
    private static void expectX(String namespace, float nativeX, int width, float expected) {
        float actual = GuideTextLayout.captionX(namespace, nativeX, width);
        if (Float.compare(actual, expected) != 0)
            throw new AssertionError(namespace + ": " + actual + " != " + expected);
    }
    public static void main(String[] args) {
        expect("The machine does not accept fluids. Supply power before starting.",
               "The machine does not accept fluids. Supply power before starting.");
        expect("The processing machine does not accept fluids.\nSupply power before starting.",
               "The processing machine does not accept fluids.", "Supply power before starting.");
        expect("Power is insufficient…\n…all connected machines stop.",
               "Power is insufficient…", "…all connected machines stop.");
        expect("蓄電量が消費量を下回ると…\n…ネットワークの電力が不足し…\n…接続した装置が停止します。",
               "蓄電量が消費量を下回ると…", "…ネットワークの電力が不足し…", "…接続した装置が停止します。");
        expect("This very long sentence remains intact even if it exceeds the display width.",
               "This very long sentence remains intact even if it exceeds the display width.");
        expect("Dr. Smith uses e.g. 1.5 units in Minecraft 1.21.1 with ae2:pattern_provider.",
               "Dr. Smith uses e.g. 1.5 units in Minecraft 1.21.1 with ae2:pattern_provider.");
        expect("この装置は流体を受け付けません。", "この装置は流体を受け付けません。");
        expect("यह मशीन तरल पदार्थ स्वीकार नहीं करती।", "यह मशीन तरल पदार्थ स्वीकार नहीं करती।");
        expect("هذه الآلة لا تقبل السوائل؟", "هذه الآلة لا تقبل السوائل؟");
        expect("Supercalifragilisticexpialidocious is an unusually long word.",
               "Supercalifragilisticexpialidocious is an unusually long word.");
        expect("first\nsecond\n", "first", "second", "");
        expect("\nsecond", "", "second");
        expect("", "");
        if (GuideTextLayout.CAPTION_WIDTH != 180 || GuideTextLayout.CAPTION_Y != 138)
            throw new AssertionError("unexpected native caption dimensions/rail");
        expectX("ae2ponder", 185, 320, 132);
        expectX("ae2ponder", 218, 320, 132);
        expectX("ae2ponder", 185, 480, 185);
        expectX("ae2ponder", 218, 480, 218);
        expectX("ae2ponder", -50, 320, 18);
        expectX("ae2ponder", 500, 320, 132);
        expectX("ae2ponder", 500, 480, 292);
        expectX("othermod", 185.25f, 320, 185.25f);
    }
}'''
        with tempfile.TemporaryDirectory(prefix="caption-java-test-", dir=workspace / ".local") as folder:
            temp = Path(folder)
            harness_path = temp / "CaptionPagesHarness.java"
            harness_path.write_text(harness, encoding="utf-8")
            subprocess.run([javac, "-encoding", "UTF-8", "--release", "17", "-d", str(temp),
                            str(source), str(layout_source), str(harness_path)], check=True, text=True)
            subprocess.run([java, "-cp", str(temp), "CaptionPagesHarness"], check=True, text=True)


if __name__ == "__main__":
    unittest.main()
