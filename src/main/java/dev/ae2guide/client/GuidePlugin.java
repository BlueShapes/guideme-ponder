package dev.ae2guide.client;

import dev.ae2guide.GuideConstants;
import dev.ae2guide.GuideResources;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.createmod.ponder.api.registration.SharedTextRegistrationHelper;
import net.createmod.ponder.api.registration.StoryBoardEntry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GuidePlugin implements PonderPlugin {
    public static final ResourceLocation GUIDE_ITEM = GuideResources.of("ae2", "guide");
    private static final Map<String, StoryBoardEntry> ENTRIES = new LinkedHashMap<>();
    private static final List<StoryBoardEntry> COURSE = new ArrayList<>();

    public static List<StoryBoardEntry> course() { return List.copyOf(COURSE); }

    public static StoryBoardEntry entry(String lessonId) {
        var entry = ENTRIES.get(lessonId);
        if (entry == null) throw new IllegalStateException("Ponder lesson has not been registered: " + lessonId);
        return entry;
    }

    @Override public String getModId() { return GuideConstants.MOD_ID; }

    @Override public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        ENTRIES.clear();
        COURSE.clear();
        for (var lesson : GuideBook.lessons()) {
            var tag = GuideResources.of(getModId(), lesson.chapter());
            var entry = helper.addStoryBoard(GuideResources.parse(lesson.icon()), "stage",
                    (scene, util) -> GuideScenes.build(lesson, scene, util), tag);
            ENTRIES.put(lesson.id(), entry);
            // A single guide item supplies Ponder's native previous/next lesson navigation.
            COURSE.add(helper.addStoryBoard(GUIDE_ITEM, "stage",
                    (scene, util) -> GuideScenes.build(lesson, scene, util), tag));
            for (var component : lesson.components()) {
                if (!component.equals(lesson.icon())) helper.addStoryBoard(GuideResources.parse(component), "stage",
                        (scene, util) -> GuideScenes.build(lesson, scene, util), tag);
            }
        }
    }

    @Override public void registerTags(PonderTagRegistrationHelper<ResourceLocation> helper) {
        for (var chapter : GuideChapter.active(GuideBook.lessons())) {
            String icon = chapter.isAddon()
                    ? GuideBook.lessons().stream().filter(lesson -> lesson.chapter().equals(chapter.id()))
                            .map(GuideBook.Lesson::icon).findFirst().orElseThrow()
                    : chapter.icon();
            helper.registerTag(chapter.id()).title(chapter.en()).description(chapter.description())
                    .item(BuiltInRegistries.ITEM.get(GuideResources.parse(icon))).addToIndex().register();
        }
        for (var lesson : GuideBook.lessons()) {
            helper.addTagToComponent(GuideResources.parse(lesson.icon()),
                    GuideResources.of(getModId(), lesson.chapter()));
            for (var component : lesson.components()) helper.addTagToComponent(GuideResources.parse(component),
                    GuideResources.of(getModId(), lesson.chapter()));
        }
    }

    @Override public void registerSharedText(SharedTextRegistrationHelper helper) {
        for (var lesson : GuideBook.lessons()) for (int i = 0; i < lesson.steps().size(); i++)
            helper.registerSharedText(lesson.id() + "_" + i, lesson.steps().get(i).text().en());
    }
}
