package dev.ae2guide.validation;

import dev.ae2guide.GuideResources;
import dev.ae2guide.client.GuideItemDescriptions;
import dev.ae2guide.client.RightToLeftText;
import net.createmod.ponder.foundation.PonderIndex;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Exercise the runtime event hook, localized expansion and text-only item routing. */
final class GuideDescriptionValidation {
    private GuideDescriptionValidation() {}

    static void verifyHooks() {
        var client = Minecraft.getInstance();
        String hint = hint();
        for (var entry : GuideItemDescriptions.descriptions()) for (String id : entry.items()) {
            var location = GuideResources.parse(id);
            if (!BuiltInRegistries.ITEM.containsKey(location))
                continue; // Some description IDs only exist in the other supported Minecraft version.
            var tooltip = Screen.getTooltipFromItem(client, stack(id));
            if (tooltip.stream().filter(line -> line.getString().equals(hint)).count() != 1)
                throw new IllegalStateException("Tooltip event did not add one description hint: " + id);
            if ((entry.id().equals("certus_decoration") || entry.id().equals("facade"))
                    && PonderIndex.getSceneAccess().doScenesExistForId(location))
                throw new IllegalStateException("Text-only item unexpectedly opens a scene: " + id);
        }
        if (!PonderIndex.getSceneAccess().doScenesExistForId(GuideResources.parse("ae2:cable_anchor")))
            throw new IllegalStateException("Cable anchor lost its placement scene");
        var untouched = new ArrayList<Component>(List.of(Component.literal("Existing tooltip")));
        var before = List.copyOf(untouched);
        GuideItemDescriptions.append(stack("ae2:fluix_crystal"), untouched, true);
        if (!untouched.equals(before)) throw new IllegalStateException("Unrelated tooltip was modified");
    }

    static void verifyLocalized(Map<String, String> translations) {
        var client = Minecraft.getInstance();
        int limit = Math.min(240, client.getWindow().getGuiScaledWidth() - 32);
        for (var entry : GuideItemDescriptions.descriptions()) {
            String item = entry.items().stream().filter(id -> BuiltInRegistries.ITEM.containsKey(GuideResources.parse(id)))
                    .findFirst().orElse(null);
            if (item == null) continue;
            String expected = translations.get(entry.translationKey());
            if (expected == null) throw new IllegalStateException("Missing localized description: " + entry.id());
            var name = Component.literal("Name");
            var existing = Component.literal("Existing tooltip");
            var tooltip = new ArrayList<Component>(List.of(name, existing));
            GuideItemDescriptions.append(stack(item), tooltip, true);
            if (tooltip.get(0) != name || tooltip.get(tooltip.size() - 1) != existing)
                throw new IllegalStateException("Description discarded an existing tooltip");
            var lines = tooltip.subList(1, tooltip.size() - 1);
            String joined = lines.stream().map(Component::getString).reduce("", String::concat).replaceAll("\\s", "");
            if (!joined.equals(RightToLeftText.shapeArabic(expected).replaceAll("\\s", "")))
                throw new IllegalStateException("Expanded description lost translated text: " + entry.id());
            if (lines.stream().anyMatch(line -> client.font.width(line.getVisualOrderText()) > limit))
                throw new IllegalStateException("Description exceeds its tooltip width: " + entry.id());
            var collapsed = new ArrayList<Component>(List.of(name, existing));
            GuideItemDescriptions.append(stack(item), collapsed, false);
            if (collapsed.size() != 3 || !collapsed.get(1).getString().equals(hint()))
                throw new IllegalStateException("Localized description hint failed: " + entry.id());
        }
    }

    private static String hint() {
        return Component.translatable("ae2ponder.tooltip.hold_for_description",
                Component.translatable("key.keyboard.left.shift")).getString();
    }

    private static ItemStack stack(String id) {
        return new ItemStack(BuiltInRegistries.ITEM.get(GuideResources.parse(id)));
    }

    static final class TooltipScreen extends Screen {
        TooltipScreen() { super(Component.empty()); }

        @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            var tooltip = new ArrayList<Component>();
            var item = stack("ae2:cable_anchor");
            tooltip.add(item.getHoverName());
            GuideItemDescriptions.append(item, tooltip, true);
            graphics.renderComponentTooltip(font, tooltip, width / 3, height / 3);
        }
    }
}
