package dev.ae2guide.validation;

import dev.ae2guide.GuideResources;
import dev.ae2guide.client.GuideBook;
import dev.ae2guide.client.GuideChapter;
import dev.ae2guide.client.GuideNavigation;
import dev.ae2guide.client.GuidePlugin;
import net.createmod.ponder.foundation.PonderIndex;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Locale;

/** Fast target-resolution checks shared with the command entry points. */
final class GuideNavigationValidation {
    private GuideNavigationValidation() {}

    static void verifyLocalizedTargets(String locale) {
        var lessons = GuideBook.lessons();
        var chapters = GuideChapter.active(lessons);
        Locale selectedLocale = Locale.forLanguageTag(locale.replace('_', '-'));
        for (int index = 0; index < chapters.size(); index++) {
            var chapter = chapters.get(index);
            var expected = lessons.stream().filter(lesson -> lesson.chapter().equals(chapter.id()))
                    .findFirst().orElseThrow(() -> new IllegalStateException("Active chapter has no lesson: " + chapter.id()));
            String localizedLabel = chapter.label().getString();
            String standardTagName = net.minecraft.network.chat.Component
                    .translatable("ae2ponder.ponder.tag." + chapter.id()).getString();
            String standardTagDescription = Component.translatable(
                    "ae2ponder.ponder.tag." + chapter.id() + ".description").getString();
            var registeredTag = PonderIndex.getTagAccess().getRegisteredTag(GuideResources.of("ae2ponder", chapter.id()));
            if (!registeredTag.getTitle().equals(standardTagName)
                    || !registeredTag.getDescription().equals(standardTagDescription))
                throw new IllegalStateException("Ponder tag did not use selected-locale title/description in " + locale
                        + ": " + chapter.id() + ", title=" + registeredTag.getTitle()
                        + ", description=" + registeredTag.getDescription());
            if (PonderIndex.getTagAccess().getListedTags().stream()
                    .noneMatch(tag -> tag.getId().equals(registeredTag.getId())))
                throw new IllegalStateException("Ponder tag is registered but absent from the standard index in "
                        + locale + ": " + chapter.id());
            for (String target : List.of(Integer.toString(index + 1), chapter.id(), chapter.id().toUpperCase(Locale.ROOT), chapter.en(),
                    chapter.en().toUpperCase(Locale.ROOT), localizedLabel,
                    localizedLabel.toUpperCase(selectedLocale), standardTagName,
                    standardTagName.toUpperCase(selectedLocale))) {
                var actual = GuideNavigation.firstLessonForChapter(target);
                if (actual == null || !actual.id().equals(expected.id()))
                    throw new IllegalStateException("Chapter target did not resolve in " + locale + ": " + target
                            + ", expected=" + expected.id() + ", actual=" + (actual == null ? "<none>" : actual.id()));
            }
        }

        var firstAddonChapter = chapters.stream().filter(GuideChapter::isAddon).findFirst().orElse(null);
        var firstAddonLesson = firstAddonChapter == null ? null : lessons.stream()
                .filter(lesson -> lesson.chapter().equals(firstAddonChapter.id())).findFirst().orElseThrow();
        for (String legacyTarget : List.of("addons", "Add-ons", Component.translatable("ae2ponder.chapter.addons").getString(),
                Component.translatable("ae2ponder.ponder.tag.addons").getString())) {
            var actual = GuideNavigation.firstLessonForChapter(legacyTarget);
            if (firstAddonLesson == null ? actual != null : actual == null || !actual.id().equals(firstAddonLesson.id()))
                throw new IllegalStateException("Legacy add-on chapter alias did not resolve to the first available add-on in "
                        + locale + ": " + legacyTarget);
        }

        var first = lessons.get(0);
        var itemId = GuideResources.parse(first.icon());
        var item = BuiltInRegistries.ITEM.getOptional(itemId).orElseThrow(() ->
                new IllegalStateException("First lesson item is missing: " + itemId));
        String displayName = new ItemStack(item).getHoverName().getString();
        for (String target : List.of(itemId.toString(), itemId.getPath(), itemId.getPath().toUpperCase(Locale.ROOT),
                displayName, displayName.toUpperCase(selectedLocale))) {
            var actual = GuideNavigation.firstLessonForItem(target);
            if (actual == null || !actual.id().equals(first.id()))
                throw new IllegalStateException("Item target did not resolve in " + locale + ": " + target
                        + ", expected=" + first.id() + ", actual=" + (actual == null ? "<none>" : actual.id()));
        }

        var guideItem = BuiltInRegistries.ITEM.getOptional(GuidePlugin.GUIDE_ITEM).orElseThrow(() ->
                new IllegalStateException("Guide item is missing: " + GuidePlugin.GUIDE_ITEM));
        String guideName = new ItemStack(guideItem).getHoverName().getString();
        for (String target : List.of(GuidePlugin.GUIDE_ITEM.toString(), GuidePlugin.GUIDE_ITEM.getPath(),
                GuidePlugin.GUIDE_ITEM.getPath().toUpperCase(Locale.ROOT), guideName,
                guideName.toUpperCase(selectedLocale), guideName.toUpperCase(Locale.ROOT))) {
            var actual = GuideNavigation.firstLessonForItem(target);
            if (actual == null || !actual.id().equals(lessons.get(0).id()))
                throw new IllegalStateException("Guide item target did not resolve to the course start in " + locale
                        + ": " + target + ", actual=" + (actual == null ? "<none>" : actual.id()));
        }

        for (var chapter : chapters) {
            var chapterLessons = lessons.stream().filter(lesson -> lesson.chapter().equals(chapter.id())).toList();
            for (var lesson : chapterLessons) {
                var components = java.util.stream.Stream.concat(java.util.stream.Stream.of(lesson.icon()), lesson.components().stream())
                        .map(GuideResources::parse).toList();
                for (var component : components) {
                    var actual = GuideNavigation.firstLessonForItem(component, chapter.id());
                    if (actual == null || !actual.chapter().equals(chapter.id()))
                        throw new IllegalStateException("Component did not resolve within its exact chapter in " + locale
                                + ": item=" + component + ", chapter=" + chapter.id());
                }
            }
            var foreignChapter = chapters.stream().filter(other -> !other.id().equals(chapter.id()))
                    .filter(other -> chapterLessons.stream().flatMap(lesson ->
                                    java.util.stream.Stream.concat(java.util.stream.Stream.of(lesson.icon()), lesson.components().stream()))
                            .map(GuideResources::parse)
                            .noneMatch(component -> GuideNavigation.firstLessonForItem(component, other.id()) != null))
                    .findFirst().orElse(null);
            if (foreignChapter != null) {
                var component = GuideResources.parse(chapterLessons.get(0).icon());
                if (GuideNavigation.firstLessonForItem(component, foreignChapter.id()) != null)
                    throw new IllegalStateException("Component target fell back outside its requested chapter: " + component
                            + ", requested=" + foreignChapter.id());
            }
        }
    }
}
