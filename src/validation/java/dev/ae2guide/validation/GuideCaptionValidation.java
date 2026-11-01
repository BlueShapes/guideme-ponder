package dev.ae2guide.validation;

import dev.ae2guide.client.CaptionPages;
import dev.ae2guide.client.GuideTextLayout;
import dev.ae2guide.client.RightToLeftText;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Measures each complete authored caption unit in Minecraft's selected locale font. */
final class GuideCaptionValidation {
    static final List<Integer> CHECKED_WIDTHS = List.of(GuideTextLayout.CAPTION_WIDTH);

    private GuideCaptionValidation() {}

    static Report verify(Locale locale, Font font, Map<String, String> translations) {
        var overflows = new ArrayList<Overflow>();
        var measurements = new ArrayList<Measurement>();
        int checkedUnits = 0;
        for (var entry : translations.entrySet()) {
            if (!isCaptionKey(entry.getKey())) continue;
            var units = CaptionPages.split(entry.getValue());
            if (!String.join("\n", units).equals(entry.getValue())) {
                overflows.add(new Overflow(locale.toString(), entry.getKey(), -1, 0, 0,
                        "explicit caption-unit boundaries changed during parsing"));
            }
            if (units.isEmpty()) {
                overflows.add(new Overflow(locale.toString(), entry.getKey(), 0, 0, 0, "caption has no authored units"));
                continue;
            }
            for (int unitIndex = 0; unitIndex < units.size(); unitIndex++) {
                String unit = units.get(unitIndex);
                checkedUnits++;
                if (unit.isBlank()) {
                    overflows.add(new Overflow(locale.toString(), entry.getKey(), unitIndex, 0, 0,
                            "authored caption unit is empty"));
                    measurements.add(new Measurement(locale.toString(), entry.getKey(), unitIndex, List.of(0)));
                    continue;
                }
                var lineCounts = new ArrayList<Integer>(CHECKED_WIDTHS.size());
                for (int width : CHECKED_WIDTHS) {
                    var lines = wrap(locale, font, unit, width);
                    lineCounts.add(lines.size());
                    int renderedWidth = lines.stream().mapToInt(line -> renderedWidth(locale, font, line.getString()))
                            .max().orElse(0);
                    if (!withoutWhitespace(lines.stream().map(FormattedText::getString)
                            .collect(java.util.stream.Collectors.joining()))
                            .equals(withoutWhitespace(unit))) {
                        overflows.add(new Overflow(locale.toString(), entry.getKey(), unitIndex, width, lines.size(),
                                "font wrapping lost or reordered caption content"));
                    }
                    if (lines.size() > CaptionPages.MAX_LINES || renderedWidth > width) {
                        overflows.add(new Overflow(locale.toString(), entry.getKey(), unitIndex, width, lines.size(),
                                renderedWidth > width ? "rendered line exceeds the available text width" : "caption exceeds three rendered lines"));
                    }
                }
                measurements.add(new Measurement(locale.toString(), entry.getKey(), unitIndex, List.copyOf(lineCounts)));
            }
        }
        if (checkedUnits == 0) throw new IllegalStateException("No localized Ponder caption units found for " + locale);
        return new Report(checkedUnits, List.copyOf(overflows), List.copyOf(measurements));
    }

    static boolean isCaptionKey(String key) {
        return key.startsWith("ae2ponder.ponder.shared.")
                && !key.equals("ae2ponder.ponder.shared.caption_page");
    }

    static void verifyOverflowAndContinuationFixtures(Locale locale, Font font) {
        String longWord = "W".repeat(160) + ".";
        if (!CaptionPages.split(longWord).equals(List.of(longWord)))
            throw new IllegalStateException("A long unexpected caption was split at an unauthored boundary");
        if (wrap(locale, font, longWord, GuideTextLayout.CAPTION_WIDTH).size() <= CaptionPages.MAX_LINES)
            throw new IllegalStateException("The authoring font check did not detect a deliberately overlong unit");
        for (String fixture : List.of(
                "When power is insufficient…\n…connected machines stop.",
                "蓄電量が消費量を下回ると…\n…ネットワークの電力が不足し…\n…接続した装置が停止します。")) {
            for (String unit : CaptionPages.split(fixture)) for (int width : CHECKED_WIDTHS) {
                if (wrap(locale, font, unit, width).size() > CaptionPages.MAX_LINES)
                    throw new IllegalStateException("An authored continuation fixture exceeds three lines: " + locale + "/" + unit);
            }
        }
    }

    static String normalizeForSearch(String caption) {
        return caption.replace("…\n…", " ").replace('\n', ' ').replaceAll("\\s+", " ").trim();
    }

    private static List<FormattedText> wrap(Locale locale, Font font, String text, int width) {
        if (isRightToLeft(locale)) return RightToLeftText.wrap(font, text, width, Style.EMPTY);
        return font.getSplitter().splitLines(text, width, Style.EMPTY);
    }

    private static int renderedWidth(Locale locale, Font font, String text) {
        return isRightToLeft(locale) ? RightToLeftText.width(font, text) : font.width(text);
    }

    private static boolean isRightToLeft(Locale locale) {
        return switch (locale.getLanguage()) {
            case "ar", "fa", "he", "ur" -> true;
            default -> false;
        };
    }

    private static String withoutWhitespace(String text) {
        var result = new StringBuilder();
        text.codePoints().filter(codePoint -> !Character.isWhitespace(codePoint)).forEach(result::appendCodePoint);
        return result.toString();
    }

    record Report(int checkedUnits, List<Overflow> overflows, List<Measurement> measurements) {}
    record Measurement(String locale, String key, int unitIndex, List<Integer> renderedLinesAtCheckedWidths) {}
    record Overflow(String locale, String key, int unitIndex, int width, int lines, String reason) {}
}
