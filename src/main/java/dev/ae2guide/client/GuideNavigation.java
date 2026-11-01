package dev.ae2guide.client;

import dev.ae2guide.GuideResources;
import net.createmod.catnip.gui.NavigatableSimiScreen;
import net.createmod.catnip.gui.ScreenOpener;
import net.createmod.ponder.foundation.ui.PonderTagIndexScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Locale;

/** Shared entry points for the standard Ponder index and the guide's full lesson course. */
public final class GuideNavigation {
    private GuideNavigation() {}

    /** Opens Ponder's standard tag index, preserving its navigation transition when possible. */
    public static void openIndex() {
        var screen = new PonderTagIndexScreen();
        if (Minecraft.getInstance().screen instanceof NavigatableSimiScreen)
            ScreenOpener.transitionTo(screen);
        else
            ScreenOpener.open(screen);
    }

    /** Returns the first available lesson for an item, preferring the requested chapter. */
    public static GuideBook.Lesson firstLessonForItem(ResourceLocation itemId, String preferredChapter) {
        List<GuideBook.Lesson> lessons = GuideBook.lessons();
        if (preferredChapter != null) {
            return lessons.stream()
                    .filter(lesson -> lesson.chapter().equals(preferredChapter))
                    .filter(lesson -> GuidePlugin.GUIDE_ITEM.equals(itemId) || hasItem(lesson, itemId))
                    .findFirst().orElse(null);
        }
        if (GuidePlugin.GUIDE_ITEM.equals(itemId)) return lessons.get(0);
        return lessons.stream().filter(lesson -> hasItem(lesson, itemId)).findFirst().orElse(null);
    }

    /** Returns the first available lesson matching a registry ID, registry path or localized item name. */
    public static GuideBook.Lesson firstLessonForItem(String target) {
        if (target == null || target.isBlank()) return null;
        String value = target.trim();
        ResourceLocation exactId = value.indexOf(':') >= 0 ? tryParse(value) : null;
        List<GuideBook.Lesson> lessons = GuideBook.lessons();
        if (matchesGuideItem(value, exactId)) return lessons.get(0);

        for (var lesson : lessons) {
            for (var itemId : itemIds(lesson)) {
                if (exactId != null && exactId.equals(itemId)) return lesson;
                if (value.indexOf(':') < 0 && normalizeRoot(value).equals(normalizeRoot(itemId.getPath())))
                    return lesson;
                var item = BuiltInRegistries.ITEM.getOptional(itemId).orElse(null);
                if (item != null && matchesText(value, new ItemStack(item).getHoverName().getString()))
                    return lesson;
            }
        }
        return null;
    }

    /** Returns the first available lesson of a chapter addressed by number, ID or localized/English name. */
    public static GuideBook.Lesson firstLessonForChapter(String target) {
        GuideChapter chapter = findChapter(target);
        if (chapter == null) return null;
        return GuideBook.lessons().stream().filter(lesson -> lesson.chapter().equals(chapter.id()))
                .findFirst().orElse(null);
    }

    private static GuideChapter findChapter(String target) {
        if (target == null || target.isBlank()) return null;
        String value = target.trim();
        List<GuideChapter> chapters = GuideChapter.active(GuideBook.lessons());
        if (isLegacyAddonAlias(value)) return chapters.stream().filter(GuideChapter::isAddon).findFirst().orElse(null);
        try {
            int number = Integer.parseInt(value);
            if (number > 0 && number <= chapters.size()) return chapters.get(number - 1);
        } catch (NumberFormatException ignored) {
            // Continue with chapter IDs and names.
        }

        for (var chapter : chapters) {
            if (normalizeRoot(value).equals(normalizeRoot(chapter.id()))
                    || matchesText(value, chapter.en())
                    || matchesText(value, chapter.label().getString())
                    || matchesText(value, Component.translatable("ae2ponder.ponder.tag." + chapter.id()).getString()))
                return chapter;
        }
        return null;
    }

    private static boolean isLegacyAddonAlias(String value) {
        return normalizeRoot(value).equals("addons")
                || matchesText(value, "Add-ons")
                || matchesText(value, Component.translatable("ae2ponder.chapter.addons").getString())
                || matchesText(value, stripChapterOrdinal(Component.translatable("ae2ponder.chapter.addons").getString()))
                || matchesText(value, Component.translatable("ae2ponder.ponder.tag.addons").getString());
    }

    private static String stripChapterOrdinal(String value) {
        return value.replaceFirst("^\\s*\\d+[.．、]?\\s*", "");
    }

    private static boolean hasItem(GuideBook.Lesson lesson, ResourceLocation itemId) {
        return itemIds(lesson).contains(itemId);
    }

    private static List<ResourceLocation> itemIds(GuideBook.Lesson lesson) {
        var itemIds = new java.util.ArrayList<ResourceLocation>(lesson.components().size() + 1);
        itemIds.add(GuideResources.parse(lesson.icon()));
        lesson.components().stream().map(GuideResources::parse).forEach(itemIds::add);
        return itemIds;
    }

    private static ResourceLocation tryParse(String value) {
        try {
            return GuideResources.parse(normalizeRoot(value));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static boolean matchesGuideItem(String target, ResourceLocation exactId) {
        ResourceLocation guideItemId = GuidePlugin.GUIDE_ITEM;
        if (guideItemId.equals(exactId)
                || target.indexOf(':') < 0 && normalizeRoot(target).equals(normalizeRoot(guideItemId.getPath())))
            return true;
        var guideItem = BuiltInRegistries.ITEM.getOptional(guideItemId).orElse(null);
        return guideItem != null && matchesText(target, new ItemStack(guideItem).getHoverName().getString());
    }

    private static boolean matchesText(String left, String right) {
        return normalizeRoot(left).equals(normalizeRoot(right))
                || normalizeSelected(left).equals(normalizeSelected(right));
    }

    private static String normalizeRoot(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeSelected(String value) {
        String selected = Minecraft.getInstance().getLanguageManager().getSelected();
        Locale locale = Locale.forLanguageTag(selected.replace('_', '-'));
        return value.trim().toLowerCase(locale);
    }
}
