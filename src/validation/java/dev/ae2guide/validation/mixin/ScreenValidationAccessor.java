package dev.ae2guide.validation.mixin;

import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Exposes Screen's protected widget rebuild only in the hidden validation client. */
@Mixin(Screen.class)
public interface ScreenValidationAccessor {
    @Invoker("rebuildWidgets") void ae2guidevalidation$rebuildWidgets();
}
