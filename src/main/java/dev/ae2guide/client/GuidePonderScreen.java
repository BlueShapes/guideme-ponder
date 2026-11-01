package dev.ae2guide.client;

import dev.ae2guide.mixin.PonderUIAccessor;
import dev.ae2guide.GuideConstants;
import dev.ae2guide.GuideResources;
import net.createmod.catnip.gui.NavigatableSimiScreen;
import net.createmod.catnip.gui.ScreenOpener;
import net.createmod.ponder.foundation.PonderIndex;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

import java.util.Objects;

/** All lessons use Ponder's own arrows, next-up prompt and history navigation. */
public final class GuidePonderScreen extends PonderUI {
    private final String compiledLocale = Minecraft.getInstance().getLanguageManager().getSelected();
    private boolean widgetsInitialized;

    public GuidePonderScreen(GuideBook.Lesson lesson) {
        super(PonderIndex.getSceneAccess().compile(GuidePlugin.course()));
        int index = GuideBook.lessons().indexOf(lesson);
        if (index < 0) throw new IllegalArgumentException("Unknown guide lesson: " + lesson.id());
        var access = (PonderUIAccessor) (Object) this;
        access.ae2ponder$setIndex(index);
        access.ae2ponder$getLazyIndex().startWithValue(index);
        syncActiveLessonContext(false);
    }

    @Override
    protected void init() {
        syncActiveLessonContext(false);
        super.init();
        widgetsInitialized = true;
    }

    @Override
    protected boolean scroll(boolean forward) {
        boolean changed = super.scroll(forward);
        if (changed) syncActiveLessonContext(true);
        return changed;
    }

    @Override
    public void tick() {
        var previous = getActiveScene().getId();
        super.tick();
        if (!previous.equals(getActiveScene().getId())) syncActiveLessonContext(true);
    }

    private void syncActiveLessonContext(boolean rebuildWhenChapterChanges) {
        var sceneId = getActiveScene().getId();
        if (!sceneId.getNamespace().equals(GuideConstants.MOD_ID)) return;
        var lesson = GuideBook.lessons().stream().filter(value -> value.id().equals(sceneId.getPath()))
                .findFirst().orElseThrow(() -> new IllegalStateException("Unknown active guide scene: " + sceneId));
        var itemId = GuideResources.parse(lesson.icon());
        ItemLike icon = BuiltInRegistries.ITEM.getOptional(itemId).orElse(null);
        if (icon == null) icon = BuiltInRegistries.BLOCK.getOptional(itemId).orElse(null);
        if (icon == null) throw new IllegalStateException("Missing active lesson icon: " + itemId);

        var tagId = GuideResources.of(GuideConstants.MOD_ID, lesson.chapter());
        var tag = PonderIndex.getTagAccess().getRegisteredTag(tagId);
        if (tag == null) throw new IllegalStateException("Missing active lesson chapter tag: " + tagId);

        var access = (PonderUIAccessor) (Object) this;
        access.ae2ponder$setSubject(new ItemStack(icon));
        var tags = access.ae2ponder$getTags();
        boolean chapterChanged = tags.size() != 1 || !Objects.equals(tags.get(0).getId(), tagId);
        if (chapterChanged) {
            tags.clear();
            tags.add(tag);
        }
        if (chapterChanged && widgetsInitialized && rebuildWhenChapterChanges) rebuildWidgets();
    }

    @Override
    public boolean isEquivalentTo(NavigatableSimiScreen other) {
        return other instanceof GuidePonderScreen guide
                && compiledLocale.equals(guide.compiledLocale)
                && getActiveScene().getId().equals(guide.getActiveScene().getId());
    }

    public static void open(GuideBook.Lesson lesson) {
        var screen = new GuidePonderScreen(lesson);
        // Keep Catnip's animated transition when entering from another Ponder navigation screen.
        if (Minecraft.getInstance().screen instanceof NavigatableSimiScreen)
            ScreenOpener.transitionTo(screen);
        else
            ScreenOpener.open(screen);
    }
}
