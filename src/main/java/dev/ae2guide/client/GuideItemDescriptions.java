package dev.ae2guide.client;

import com.google.gson.Gson;
import dev.ae2guide.GuideResources;
import dev.ae2guide.Platform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Data-driven descriptions appended to selected item tooltips. */
public final class GuideItemDescriptions {
    private static final String RESOURCE = "/assets/ae2ponder/guide/item_descriptions.json";
    private static final String DESCRIPTION_KEY = "ae2ponder.description.";
    private static final int MAX_WIDTH = 240;

    private GuideItemDescriptions() {}

    private static final class Holder {
        private static final Index INDEX = load();
    }

    private record Index(List<Entry> descriptions, Map<ResourceLocation, Entry> byItem) {}

    public record Entry(String id, List<String> items, GuideBook.Localized text, CaptionTone tone,
                        List<String> requiresMods, List<CaptionTone> tones) {
        public Entry {
            if (id == null || !id.matches("[a-z0-9_]+"))
                throw new IllegalArgumentException("Invalid item description ID: " + id);
            items = List.copyOf(Objects.requireNonNull(items, "Item description items"));
            if (items.isEmpty()) throw new IllegalArgumentException("Item description has no items: " + id);
            items.forEach(GuideResources::parse);
            Objects.requireNonNull(text, "Item description text");
            tone = tone == null ? CaptionTone.NORMAL : tone;
            requiresMods = requiresMods == null ? List.of() : List.copyOf(requiresMods);
            if (requiresMods.stream().anyMatch(mod -> mod == null || !mod.matches("[a-z][a-z0-9_]{1,63}")))
                throw new IllegalArgumentException("Invalid required mod ID in item description: " + id);
            if (new HashSet<>(requiresMods).size() != requiresMods.size())
                throw new IllegalArgumentException("Duplicate required mod ID in item description: " + id);
            if (tones != null) {
                tones = List.copyOf(tones);
                if (tones.isEmpty() || tones.stream().anyMatch(Objects::isNull))
                    throw new IllegalArgumentException("Invalid item description roles: " + id);
            }
        }

        public String translationKey() { return DESCRIPTION_KEY + id; }
        public boolean hasExplicitTones() { return tones != null; }
    }

    public static List<Entry> descriptions() { return Holder.INDEX.descriptions(); }

    /** Return the available description for this exact item without changing its data components. */
    public static Entry descriptionFor(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return Holder.INDEX.byItem().get(itemId);
    }

    /** Insert a guide hint or translated description after the name, retaining existing tooltip text. */
    public static void append(ItemStack stack, List<Component> tooltip, boolean expanded) {
        if (stack.isEmpty()) return;
        Entry entry = descriptionFor(stack);
        if (entry == null) return;

        List<Component> additions = expanded ? expandedDescription(entry) : collapsedHint();
        tooltip.addAll(Math.min(1, tooltip.size()), additions);
    }

    private static List<Component> collapsedHint() {
        return List.of(Component.translatable("ae2ponder.tooltip.hold_for_description",
                Component.translatable("key.keyboard.left.shift"))
                .withStyle(style -> style.withColor(CaptionTone.HINT.textColor())));
    }

    private static List<Component> expandedDescription(Entry entry) {
        String text = Component.translatable(entry.translationKey()).getString();
        String[] units = text.split("\\n", -1);
        if (Arrays.stream(units).anyMatch(String::isBlank))
            throw new IllegalStateException("Empty item description unit: " + entry.id());
        if (entry.hasExplicitTones() && entry.tones().size() != units.length)
            throw new IllegalStateException("Item description roles must match localized units: " + entry.id());
        Font font = Minecraft.getInstance().font;
        int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int width = Math.max(1, Math.min(MAX_WIDTH, screenWidth - 32));
        var result = new ArrayList<Component>();
        for (int index = 0; index < units.length; index++) {
            var tone = entry.hasExplicitTones() ? entry.tones().get(index) : entry.tone();
            var shaped = RightToLeftText.shapeArabic(units[index]);
            RightToLeftText.wrap(font, shaped, width, Style.EMPTY).forEach(line -> result.add(
                    Component.literal(line.getString()).withStyle(style -> style.withColor(tone.textColor()))));
        }
        return List.copyOf(result);
    }

    private static Index load() {
        try {
            var stream = Objects.requireNonNull(GuideItemDescriptions.class.getResourceAsStream(RESOURCE),
                    "Missing item descriptions: " + RESOURCE);
            Entry[] entries;
            try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                entries = new Gson().fromJson(reader, Entry[].class);
            }
            if (entries == null) throw new IllegalStateException("Item descriptions are empty");

            var descriptions = List.copyOf(Arrays.asList(entries));
            var ids = new HashSet<String>();
            var items = new HashSet<ResourceLocation>();
            var byItem = new HashMap<ResourceLocation, Entry>();
            for (var entry : descriptions) {
                if (!ids.add(entry.id())) throw new IllegalStateException("Duplicate item description ID: " + entry.id());
                for (var item : entry.items()) {
                    var itemId = GuideResources.parse(item);
                    if (!items.add(itemId)) throw new IllegalStateException("Duplicate item in descriptions: " + itemId);
                    byItem.put(itemId, entry);
                }
            }
            var availableDescriptions = descriptions.stream()
                    .filter(entry -> entry.requiresMods().stream().allMatch(Platform::isModLoaded))
                    .toList();
            var availableIds = new HashSet<String>();
            availableDescriptions.forEach(entry -> availableIds.add(entry.id()));
            byItem.entrySet().removeIf(entry -> !availableIds.contains(entry.getValue().id()));
            return new Index(availableDescriptions, Map.copyOf(byItem));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to load AE2 item descriptions", exception);
        }
    }
}
