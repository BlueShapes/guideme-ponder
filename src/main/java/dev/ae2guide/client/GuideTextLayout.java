package dev.ae2guide.client;

/** Layout constants and namespace-scoped placement for native Ponder caption windows. */
public final class GuideTextLayout {
    public static final int CAPTION_Y = 138;
    public static final int CAPTION_WIDTH = 180;
    public static final int LEFT_MARGIN = 18;
    public static final int RIGHT_MARGIN = 8;

    private GuideTextLayout() {}

    /** Keeps this guide's native caption window inside the screen while preserving other Ponder layouts. */
    public static float captionX(String namespace, float nativeX, int uiWidth) {
        if (!"ae2ponder".equals(namespace)) return nativeX;
        return Math.max(LEFT_MARGIN, Math.min(nativeX, uiWidth - CAPTION_WIDTH - RIGHT_MARGIN));
    }
}
