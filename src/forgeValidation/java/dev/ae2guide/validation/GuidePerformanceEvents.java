package dev.ae2guide.validation;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Forge event bridge for the shared performance harness. */
@Mod.EventBusSubscriber(modid = "ae2ponder", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class GuidePerformanceEvents {
    private GuidePerformanceEvents() {}

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            if (!GuidePerformance.initialize()) return;
            MinecraftForge.EVENT_BUS.addListener(GuidePerformanceEvents::onClientTick);
            MinecraftForge.EVENT_BUS.addListener(GuidePerformanceEvents::onRenderTick);
        });
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) GuidePerformance.tick();
    }

    private static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) GuidePerformance.beforeFrame();
        else GuidePerformance.afterFrame();
    }
}
