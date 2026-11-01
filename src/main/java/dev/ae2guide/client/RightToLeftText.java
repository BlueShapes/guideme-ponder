package dev.ae2guide.client;

import com.ibm.icu.text.ArabicShaping;
import com.ibm.icu.text.ArabicShapingException;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;

import java.util.List;

/** Wrap logical text against the width of the shaped glyphs that Font actually draws. */
public final class RightToLeftText {
    private RightToLeftText() {}

    /** Shape display nodes before GuideME measures individual glyphs; keep their logical order. */
    public static String shapeArabic(String text) {
        if (text.codePoints().noneMatch(code -> Character.UnicodeScript.of(code) == Character.UnicodeScript.ARABIC))
            return text;
        try {
            return new ArabicShaping(ArabicShaping.LETTERS_SHAPE).shape(text);
        } catch (ArabicShapingException exception) {
            return text; // Match Font's fallback for text ICU cannot shape.
        }
    }

    public static int width(Font font, String text) {
        return font.width(font.bidirectionalShaping(text));
    }

    public static List<FormattedText> wrap(Font font, String text, int width, Style style) {
        int logicalWidth = Math.max(1, width);
        while (true) {
            var lines = font.getSplitter().splitLines(text, logicalWidth, style);
            int renderedWidth = lines.stream().mapToInt(line -> width(font, line.getString())).max().orElse(0);
            if (renderedWidth <= width || logicalWidth == 1) return lines;
            logicalWidth = Math.max(1, logicalWidth - Math.max(1, renderedWidth - width));
        }
    }
}
