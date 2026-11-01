package dev.ae2guide.client;

import net.createmod.catnip.theme.Color;
import net.createmod.ponder.api.PonderPalette;

/** Authored intent of explanatory text; model outlines retain their own colors. */
public enum CaptionTone {
    NORMAL(PonderPalette.WHITE),
    WARNING(PonderPalette.RED),
    HINT(PonderPalette.GREEN);

    private final PonderPalette palette;

    CaptionTone(PonderPalette palette) { this.palette = palette; }

    public PonderPalette palette() { return palette; }

    /** Match native Ponder's bright body text as the RGB value used by Minecraft TextColor. */
    public int textColor() {
        return palette.getColorObject().mixWith(new Color(0xff_ffffdd), 0.5f).getRGB() & 0x00ffffff;
    }
}
