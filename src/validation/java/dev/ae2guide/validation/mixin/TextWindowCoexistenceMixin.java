package dev.ae2guide.validation.mixin;

import dev.ae2guide.validation.TextWindowCoexistenceProbe;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.element.TextWindowElement;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.network.chat.Style;

/** No-op second modifier used to verify that same-invocation mixins coexist in the validation client. */
@Mixin(value = TextWindowElement.class, remap = false)
public abstract class TextWindowCoexistenceMixin {
    @Unique private int ae2guidevalidation$callsAtRenderStart;
    @Unique private String ae2guidevalidation$renderedText;
    @Unique private int ae2guidevalidation$renderedWidth;
    @Unique private float ae2guidevalidation$captionX = Float.NaN;
    @Unique private int ae2guidevalidation$screenWidth;

    @Inject(method = "render", at = @At("HEAD"), remap = false)
    private void ae2guidevalidation$beginRender(PonderScene scene, PonderUI screen, GuiGraphics graphics,
                                                float partialTicks, float fade, CallbackInfo ci) {
        ae2guidevalidation$callsAtRenderStart = TextWindowCoexistenceProbe.splitLinesCalls();
        ae2guidevalidation$renderedText = null;
        ae2guidevalidation$renderedWidth = 0;
        ae2guidevalidation$captionX = Float.NaN;
        ae2guidevalidation$screenWidth = screen.width;
    }

    @ModifyVariable(method = "render", remap = false, index = 11,
            at = @At(value = "STORE", ordinal = 0, remap = false),
            slice = @Slice(from = @At(value = "INVOKE", target = "Ljava/lang/Math;min(FF)F",
                            ordinal = 0, shift = At.Shift.AFTER, remap = false),
                    to = @At(value = "INVOKE", target = "Ljava/lang/Math;min(FF)F", ordinal = 1, remap = false)),
            require = 1, allow = 1)
    private float ae2guidevalidation$observeCaptionX(float x, PonderScene scene, PonderUI screen,
                                                      GuiGraphics graphics, float partialTicks, float fade) {
        TextWindowCoexistenceProbe.recordCaptionXModifier(scene.getNamespace());
        return x;
    }

    @ModifyArg(method = "render", remap = false, index = 0,
            at = @At(value = "INVOKE", target = "Ljava/lang/Math;min(FF)F", ordinal = 1, remap = false),
            require = 1, allow = 1)
    private float ae2guidevalidation$observeFinalCaptionX(float availableWidth) {
        ae2guidevalidation$captionX = ae2guidevalidation$screenWidth - availableWidth;
        return availableWidth;
    }

    @ModifyArg(method = "render", remap = false, index = 1, at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/StringSplitter;splitLines(Ljava/lang/String;ILnet/minecraft/network/chat/Style;)Ljava/util/List;",
            remap = false))
    private int ae2guidevalidation$observeSplitLines(String text, int width, Style style) {
        TextWindowCoexistenceProbe.recordSplitLinesArguments(3, text, width, style);
        ae2guidevalidation$renderedText = text;
        ae2guidevalidation$renderedWidth = width;
        return width;
    }

    @Inject(method = "render", at = @At("TAIL"), remap = false)
    private void ae2guidevalidation$finishRender(PonderScene scene, PonderUI screen, GuiGraphics graphics,
                                                  float partialTicks, float fade, CallbackInfo ci) {
        TextWindowCoexistenceProbe.recordRenderedWindow(this, scene.getNamespace().equals("ae2ponder"),
                ae2guidevalidation$callsAtRenderStart < TextWindowCoexistenceProbe.splitLinesCalls(),
                ae2guidevalidation$renderedText, ae2guidevalidation$renderedWidth,
                ae2guidevalidation$captionX);
    }
}
