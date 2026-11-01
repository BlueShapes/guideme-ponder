package dev.ae2guide.mixin;

import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.ponder.foundation.PonderTag;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Sets the initial lesson without animating through the lessons before it. */
@Mixin(value = PonderUI.class, remap = false)
public interface PonderUIAccessor {
    @Accessor("index") void ae2ponder$setIndex(int index);
    @Accessor("lazyIndex") LerpedFloat ae2ponder$getLazyIndex();
    @Accessor("stack") void ae2ponder$setSubject(ItemStack stack);
    @Accessor("tags") List<PonderTag> ae2ponder$getTags();
    @Accessor("referredToByTag") PonderTag ae2ponder$getReferredToByTag();
    @Accessor("referredToByTag") void ae2ponder$setReferredToByTag(PonderTag tag);
}
