package dev.ae2guide.validation;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.common.NeoForge;

/** NeoForge event bridge for the shared validation harness. */
@EventBusSubscriber(modid = "ae2ponder", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class GuideValidationEvents {
    private GuideValidationEvents() {}

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            if (!GuideValidation.initialize()) return;
            NeoForge.EVENT_BUS.addListener(GuideValidationEvents::onClientTick);
            NeoForge.EVENT_BUS.addListener(GuideValidationEvents::onAfterFrame);
        });
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        GuideValidation.tick();
    }

    private static void onAfterFrame(RenderFrameEvent.Post event) {
        GuideValidation.rendered();
    }
}
