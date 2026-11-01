package dev.ae2guide.mixin;

import dev.ae2guide.client.GuidePonderScreen;
import dev.ae2guide.client.GuideBook;
import dev.ae2guide.client.GuidePlugin;
import net.createmod.catnip.gui.NavigatableSimiScreen;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Prevents native item courses and the guide's full course from backtracking into each other. */
@Mixin(value = PonderUI.class, remap = false)
public abstract class PonderNavigationOriginMixin {
    @Inject(method = "of(Lnet/minecraft/world/item/ItemStack;)Lnet/createmod/ponder/foundation/ui/PonderUI;",
            at = @At("HEAD"), cancellable = true)
    private static void ae2ponder$openGuideCourseForGuideItem(ItemStack subject,
                                                               CallbackInfoReturnable<PonderUI> callback) {
        if (subject == null || subject.isEmpty()
                || !GuidePlugin.GUIDE_ITEM.equals(BuiltInRegistries.ITEM.getKey(subject.getItem()))) return;
        var lessons = GuideBook.lessons();
        if (!lessons.isEmpty()) callback.setReturnValue(new GuidePonderScreen(lessons.get(0)));
    }

    @Inject(method = "of(Lnet/minecraft/world/item/ItemStack;)Lnet/createmod/ponder/foundation/ui/PonderUI;",
            at = @At("RETURN"))
    private static void ae2ponder$retainGuideItemData(ItemStack subject,
                                                      CallbackInfoReturnable<PonderUI> callback) {
        var screen = callback.getReturnValue();
        if (screen == null || screen instanceof GuidePonderScreen || !ae2ponder$isGuideSubject(subject)) return;
        // Native Ponder creates its subject from a registry ID, losing energy, NBT and other components.
        ((PonderUIAccessor) (Object) screen).ae2ponder$setSubject(subject.copy());
    }

    @Unique
    private static boolean ae2ponder$isGuideSubject(ItemStack subject) {
        if (subject == null || subject.isEmpty()) return false;
        String id = BuiltInRegistries.ITEM.getKey(subject.getItem()).toString();
        return GuideBook.lessons().stream().anyMatch(lesson -> lesson.icon().equals(id) || lesson.components().contains(id));
    }

    @Inject(method = "isEquivalentTo", at = @At("HEAD"), cancellable = true)
    private void ae2ponder$keepDistinctCourseOrigins(NavigatableSimiScreen other,
                                                      CallbackInfoReturnable<Boolean> callback) {
        boolean thisIsGuideCourse = (Object) this instanceof GuidePonderScreen;
        boolean otherIsGuideCourse = other instanceof GuidePonderScreen;
        if (thisIsGuideCourse != otherIsGuideCourse) callback.setReturnValue(false);
        else if (!thisIsGuideCourse && other instanceof PonderUI otherPonder) {
            var subject = ((PonderUI) (Object) this).getSubject();
            if (ae2ponder$isGuideSubject(subject) && !ItemStack.matches(subject, otherPonder.getSubject()))
                callback.setReturnValue(false);
        }
    }
}
