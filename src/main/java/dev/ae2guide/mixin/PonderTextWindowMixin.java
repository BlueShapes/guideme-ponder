package dev.ae2guide.mixin;

import dev.ae2guide.client.GuideTextLayout;
import dev.ae2guide.client.RightToLeftText;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.element.TextWindowElement;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/** Keep AE2 Ponder captions in a bounded native window and match Arabic layout to shaped glyphs. */
@Mixin(value = TextWindowElement.class, remap = false)
public abstract class PonderTextWindowMixin {
    @Unique private boolean ae2ponder$rightToLeft;
    @Unique private boolean ae2ponder$guideCaption;
    @Unique private Font ae2ponder$font;
    @Unique private List<FormattedText> ae2ponder$wrappedLines;
    @Unique private int ae2ponder$lineCount;

    @Inject(method = "render", at = @At(value = "HEAD", remap = false), remap = false)
    private void ae2ponder$selectLayout(PonderScene scene, PonderUI screen, GuiGraphics graphics,
                                        float partialTicks, float fade, CallbackInfo ci) {
        ae2ponder$guideCaption = scene.getNamespace().equals("ae2ponder");
        ae2ponder$rightToLeft = ae2ponder$guideCaption && Language.getInstance().isDefaultRightToLeft();
        ae2ponder$font = screen.getFontRenderer();
        ae2ponder$wrappedLines = null;
    }

    @ModifyVariable(method = "render", remap = false, index = 11,
            at = @At(value = "STORE", ordinal = 0, remap = false),
            slice = @Slice(from = @At(value = "INVOKE", target = "Ljava/lang/Math;min(FF)F",
                            ordinal = 0, shift = At.Shift.AFTER, remap = false),
                    to = @At(value = "INVOKE", target = "Ljava/lang/Math;min(FF)F", ordinal = 1, remap = false)),
            require = 1, allow = 1)
    private float ae2ponder$captionX(float original, PonderScene scene, PonderUI screen,
                                     GuiGraphics graphics, float partialTicks, float fade) {
        return ae2ponder$guideCaption
                ? GuideTextLayout.captionX(scene.getNamespace(), original, screen.width)
                : original;
    }

    @ModifyArg(method = "render", remap = false, index = 1, at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/StringSplitter;splitLines(Ljava/lang/String;ILnet/minecraft/network/chat/Style;)Ljava/util/List;", remap = true))
    private int ae2ponder$prepareWrap(String text, int width, Style style) {
        if (ae2ponder$rightToLeft)
            ae2ponder$wrappedLines = RightToLeftText.wrap(ae2ponder$font, text, width, style);
        return width;
    }

    // Ponder 1.0.87 uses the same lines/boxHeight local slots on both supported loaders.
    @ModifyVariable(method = "render", remap = false, at = @At(value = "STORE", remap = false), index = 13)
    private List<FormattedText> ae2ponder$wrap(List<FormattedText> original) {
        var lines = ae2ponder$rightToLeft ? ae2ponder$wrappedLines : original;
        ae2ponder$lineCount = lines.size();
        return lines;
    }

    @ModifyArg(method = "render", remap = false, at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/gui/Font;width(Lnet/minecraft/network/chat/FormattedText;)I", remap = true), index = 0)
    private FormattedText ae2ponder$measure(FormattedText line) {
        return ae2ponder$rightToLeft ? FormattedText.of(ae2ponder$font.bidirectionalShaping(line.getString())) : line;
    }

    @ModifyVariable(method = "render", remap = false, at = @At(value = "STORE", remap = false), index = 15)
    private int ae2ponder$height(int original) {
        return ae2ponder$rightToLeft ? ae2ponder$lineCount * ae2ponder$font.lineHeight : original;
    }
}
