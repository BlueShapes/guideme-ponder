package dev.ae2guide.client;

import dev.ae2guide.GuideConstants;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

@EventBusSubscriber(modid = GuideConstants.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class GuideClientEvents {
    private GuideClientEvents() {}

    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        GuideClient.setup();
        event.enqueueWork(() -> {
            NeoForge.EVENT_BUS.addListener(GuideClientEvents::tick);
            NeoForge.EVENT_BUS.addListener(GuideClientEvents::registerCommands);
            NeoForge.EVENT_BUS.addListener(GuideClientEvents::itemTooltip);
        });
    }

    private static void tick(ClientTickEvent.Post event) { GuideClient.tick(); }

    private static void registerCommands(RegisterClientCommandsEvent event) {
        GuideCommands.register(event.getDispatcher());
    }

    private static void itemTooltip(ItemTooltipEvent event) {
        GuideItemDescriptions.append(event.getItemStack(), event.getToolTip(), Screen.hasShiftDown());
    }
}
