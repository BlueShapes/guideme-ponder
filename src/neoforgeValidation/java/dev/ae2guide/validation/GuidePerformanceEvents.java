package dev.ae2guide.validation;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.common.NeoForge;

/** NeoForge event bridge for the shared performance harness. */
@EventBusSubscriber(modid = "ae2ponder", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class GuidePerformanceEvents {
    private GuidePerformanceEvents() {}

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            if (!GuidePerformance.initialize()) return;
            NeoForge.EVENT_BUS.addListener(GuidePerformanceEvents::onClientTick);
            NeoForge.EVENT_BUS.addListener(GuidePerformanceEvents::onBeforeFrame);
            NeoForge.EVENT_BUS.addListener(GuidePerformanceEvents::onAfterFrame);
        });
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        GuidePerformance.tick();
    }

    private static void onBeforeFrame(RenderFrameEvent.Pre event) {
        GuidePerformance.beforeFrame();
    }

    private static void onAfterFrame(RenderFrameEvent.Post event) {
        GuidePerformance.afterFrame();
    }
}
