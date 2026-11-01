package dev.ae2guide.client;

import java.util.Arrays;
import java.util.List;

/** Splits authored caption units without inferring boundaries from their text or rendered width. */
public final class CaptionPages {
    public static final int MAX_LINES = 3;
    public static final int MIN_READ_TICKS_PER_PAGE = 80;
    public static final int PAGE_GAP_TICKS = 12;

    private CaptionPages() {}

    /**
     * Returns the display units explicitly authored with literal newlines.
     * Every code unit, including leading, trailing, and repeated newlines, is preserved.
     */
    public static List<String> split(String text) {
        return List.copyOf(Arrays.asList(text.split("\\n", -1)));
    }

    public static int requiredDuration(int pageCount) {
        if (pageCount < 1) throw new IllegalArgumentException("A caption needs at least one page");
        return pageCount * MIN_READ_TICKS_PER_PAGE + (pageCount - 1) * PAGE_GAP_TICKS;
    }
}
