package dev.ae2guide.validation;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Forge event bridge for the shared validation harness. */
@Mod.EventBusSubscriber(modid = "ae2ponder", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class GuideValidationEvents {
    private GuideValidationEvents() {}

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            if (!GuideValidation.initialize()) return;
            MinecraftForge.EVENT_BUS.addListener(GuideValidationEvents::onClientTick);
            MinecraftForge.EVENT_BUS.addListener(GuideValidationEvents::onRenderTick);
        });
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) GuideValidation.tick();
    }

    private static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.END) GuideValidation.rendered();
    }
}
