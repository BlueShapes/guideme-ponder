package dev.ae2guide.validation;

import net.minecraft.client.Minecraft;

/** Supplies the held key only to the hidden validation window's native input poll. */
public final class PonderKeyInput {
    private static int heldKey = -1;

    private PonderKeyInput() {}

    public static void setDown(int key, boolean down) { heldKey = down ? key : -1; }

    public static boolean isHeld(long window, int key) {
        return heldKey >= 0 && key == heldKey && window == Minecraft.getInstance().getWindow().getWindow();
    }
}
