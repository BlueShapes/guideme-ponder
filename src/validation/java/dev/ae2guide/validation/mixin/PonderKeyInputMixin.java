package dev.ae2guide.validation.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import dev.ae2guide.validation.PonderKeyInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Simulates input without sending keyboard events to the user's desktop. Not distributed. */
@Mixin(value = InputConstants.class, remap = false)
public abstract class PonderKeyInputMixin {
    @Inject(method = "isKeyDown", at = @At("HEAD"), cancellable = true, remap = false)
    private static void ae2guidevalidation$heldKey(long window, int key, CallbackInfoReturnable<Boolean> ci) {
        if (PonderKeyInput.isHeld(window, key)) ci.setReturnValue(true);
    }
}
