package dev.ae2guide.validation;

import com.ibm.icu.text.ArabicShaping;
import com.ibm.icu.text.ArabicShapingException;
import dev.ae2guide.Platform;
import dev.ae2guide.GuideResources;
import dev.ae2guide.client.GuideBook;
import dev.ae2guide.client.CaptionTone;
import dev.ae2guide.client.GuideChapter;
import dev.ae2guide.client.GuideItemDescriptions;
import dev.ae2guide.client.GuideNavigation;
import dev.ae2guide.client.GuidePlugin;
import dev.ae2guide.client.GuidePonderScreen;
import dev.ae2guide.mixin.PonderUIAccessor;
import dev.ae2guide.validation.mixin.ScreenValidationAccessor;
import guideme.Guides;
import guideme.GuidesCommon;
import guideme.PageAnchor;
import guideme.color.ConstantColor;
import guideme.color.SymbolicColor;
import guideme.document.LytPoint;
import guideme.document.block.LytDocument;
import guideme.document.block.LytVisitor;
import guideme.document.flow.LytFlowContainer;
import guideme.document.flow.LytFlowContent;
import guideme.document.flow.LytFlowLink;
import guideme.document.flow.LytFlowSpan;
import guideme.internal.screen.DocumentScreen;
import guideme.internal.screen.IndepentScaleScreen;
import guideme.ui.GuideUiHost;
import net.createmod.ponder.foundation.PonderIndex;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.PonderTag;
import net.createmod.ponder.foundation.PonderTooltipHandler;
import net.createmod.ponder.foundation.ui.PonderButton;
import net.createmod.ponder.foundation.ui.PonderTagIndexScreen;
import net.createmod.ponder.foundation.ui.PonderTagScreen;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.createmod.catnip.gui.NavigatableSimiScreen;
import net.createmod.catnip.gui.widget.BoxWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Tests actual widgets and GuideME links in the isolated, hidden client. */
final class GuideUiValidation {
    private static final ResourceLocation GUIDE = GuideResources.parse("ae2:guide");
    private static final ResourceLocation PAGE = GuideResources.parse("ae2ponder:ponder.md");
    private static int layouts;
    private static Screen meGuide;
    private static RelatedLesson selectedRelatedLesson;
    private static boolean tooltipHoldCompleted;
    private static boolean focusedStarted;
    private static int focusedStage;
    private static int focusedTicks;
    private static Screen focusedOptionsScreen;
    private static PonderTag focusedStartTag;
    private static GuideBook.Lesson focusedBoundaryFrom;
    private static GuideBook.Lesson focusedBoundaryTo;
    private static boolean focusedOptionsChecked;
    private static boolean focusedInventoryChecked;
    private static boolean focusedNativeWChecked;
    private static int focusedNativeCategoryButtonClickCount;
    private static boolean focusedDifferentSameChapterLessonChecked;
    private static boolean focusedTagRouteChecked;
    private static boolean focusedCourseContextChecked;
    private static boolean focusedCrossChapterChecked;
    private static boolean focusedDirectIndexStableChecked;
    private static boolean focusedGuideItemFactoryChecked;
    private static boolean focusedOriginEquivalenceChecked;
    private static boolean focusedCrossLocaleEquivalenceChecked;
    private static String focusedOriginBaselineLocale;
    private static String focusedOriginComparedLocale;
    private static GuidePonderScreen focusedOriginBaselineScreen;
    private static boolean focusedCaptionPalettesChecked;
    private static boolean focusedToneScreenshotsCaptured;
    private static boolean focusedTooltipToneColorsChecked;
    private static boolean focusedCommandOriginChecked;
    private static List<String> focusedCaptionPaletteSequence = List.of();
    private static final Map<String, Integer> focusedGuideProseColorSpanCounts = new java.util.LinkedHashMap<>();
    private static final Map<String, Map<String, Object>> focusedAddonChapterChecks = new java.util.LinkedHashMap<>();
    private static final Map<String, String> focusedAddonCommandChecks = new java.util.LinkedHashMap<>();
    private static String focusedAddonIndexChapterId;
    private static int focusedAddonCommandIndex;
    private static boolean focusedAddonIndexRouteChecked;
    private static boolean focusedLegacyAddonCommandChecked;

    private GuideUiValidation() {}

    static int layouts() { return layouts; }

    static Map<String, Object> focusedResult() {
        return Map.ofEntries(
                Map.entry("passed", GuideValidation.optionalModCombinationsChecked() == 16
                        && focusedStage == 14 && focusedOptionsChecked && focusedInventoryChecked
                        && focusedNativeWChecked && focusedTagRouteChecked && focusedCourseContextChecked
                        && focusedCrossChapterChecked && focusedDirectIndexStableChecked
                        && focusedGuideItemFactoryChecked && focusedOriginEquivalenceChecked
                        && focusedCrossLocaleEquivalenceChecked
                        && focusedCaptionPalettesChecked && focusedToneScreenshotsCaptured
                        && focusedGuideProseColorSpanCounts.keySet().containsAll(List.of("en_us", "ar_sa"))
                        && focusedNativeCategoryButtonClickCount == 2
                        && focusedDifferentSameChapterLessonChecked
                        && focusedTooltipToneColorsChecked
                        && focusedAddonChapterChecks.size() == GuideChapter.active(GuideBook.lessons()).stream()
                                .filter(GuideChapter::isAddon).count()
                        && focusedAddonIndexRouteChecked
                        && focusedAddonCommandChecks.size() == GuideChapter.active(GuideBook.lessons()).stream()
                                .filter(GuideChapter::isAddon).count()
                        && focusedLegacyAddonCommandChecked
                        && focusedCommandOriginChecked),
                Map.entry("optionalModCombinationsChecked", GuideValidation.optionalModCombinationsChecked()),
                Map.entry("optionsGuideButtonAbsent", focusedOptionsChecked),
                Map.entry("inventoryGuideButtonAbsent", focusedInventoryChecked),
                Map.entry("nativePonderWContext", focusedNativeWChecked),
                Map.entry("nativeCategoryButtonsClicked", focusedNativeCategoryButtonClickCount == 2),
                Map.entry("nativeCategoryButtonClickCount", focusedNativeCategoryButtonClickCount),
                Map.entry("differentLessonFromNativeCategory", focusedDifferentSameChapterLessonChecked),
                Map.entry("tagItemUsesSameChapterLessonAndBack", focusedTagRouteChecked),
                Map.entry("guideCourseContext", focusedCourseContextChecked),
                Map.entry("crossChapterPreviousNextContext", focusedCrossChapterChecked),
                Map.entry("nonfirstDirectIndexSurvivesFirstTick", focusedDirectIndexStableChecked),
                Map.entry("guideItemWOpensScopedCourse", focusedGuideItemFactoryChecked),
                Map.entry("courseOriginEquivalence", focusedOriginEquivalenceChecked),
                Map.entry("crossLocaleCourseEquivalence", Map.of(
                        "passed", focusedCrossLocaleEquivalenceChecked,
                        "fromLocale", focusedOriginBaselineLocale == null ? "<not checked>" : focusedOriginBaselineLocale,
                        "toLocale", focusedOriginComparedLocale == null ? "<not checked>" : focusedOriginComparedLocale)),
                Map.entry("nativeCaptionPalettes", focusedCaptionPalettesChecked),
                Map.entry("nativeCaptionPaletteSequence", focusedCaptionPaletteSequence),
                Map.entry("nativeCaptionPaletteScreenshots", focusedToneScreenshotsCaptured),
                Map.entry("nativeCaptionPaletteScreenshotFiles", List.of("focused-caption-warning.png",
                        "focused-caption-normal.png", "focused-caption-hint.png")),
                Map.entry("tooltipToneColors", focusedTooltipToneColorsChecked),
                Map.entry("guideProseColorsCompiled", Map.of(
                        "passed", focusedGuideProseColorSpanCounts.keySet().containsAll(List.of("en_us", "ar_sa")),
                        "locales", List.copyOf(focusedGuideProseColorSpanCounts.keySet()),
                        "coloredSpans", Map.copyOf(focusedGuideProseColorSpanCounts))),
                Map.entry("ponderCommandOriginRestored", focusedCommandOriginChecked),
                Map.entry("availableLessons", GuideBook.lessons().size()),
                Map.entry("availableChapters", GuideChapter.active(GuideBook.lessons()).stream()
                        .map(GuideChapter::id).toList()),
                Map.entry("activeAddonChapterChecks", Map.copyOf(focusedAddonChapterChecks)),
                Map.entry("addonIndexRoute", focusedAddonIndexRouteChecked),
                Map.entry("addonChapterCommands", Map.copyOf(focusedAddonCommandChecks)),
                Map.entry("legacyAddonCommand", focusedLegacyAddonCommandChecked));
    }

    static void assertNoGuideButton(Screen screen, String surface) {
        boolean found = screen.children().stream().filter(AbstractWidget.class::isInstance)
                .map(AbstractWidget.class::cast)
                .anyMatch(widget -> widget instanceof Button && widget.getX() == 8
                        && widget.getY() == screen.height - 28 && widget.getWidth() == 20
                        && widget.getHeight() == 20 && widget.getMessage().getString().isBlank());
        if (found) throw new IllegalStateException("Removed guide button remains on " + surface);
    }

    static boolean tickFocused() throws Exception {
        var client = Minecraft.getInstance();
        if (focusedStarted && focusedStage == 14) return true;
        if (!focusedStarted) {
            if (!ValidationPlatform.isOptionsScreen(client.screen))
                throw new IllegalStateException("Focused navigation did not start on Options");
            assertNoGuideButton(client.screen, "Options");
            focusedOptionsChecked = true;
            verifyOptionalLessonFilter();
            verifyGuideProseColorsCompiled("en_us");
            focusedOptionsScreen = client.screen;
            var toneLesson = GuideBook.lessons().stream().filter(lesson -> lesson.id().equals("certus_growth"))
                    .findFirst().orElseThrow(() -> new IllegalStateException("Missing focused caption tone fixture"));
            verifyGuideItemFactoryAndOriginEquivalence();
            client.setScreen(new GuidePonderScreen(toneLesson));
            focusedStarted = true;
            focusedStage = 0;
            focusedTicks = 0;
            return false;
        }
        if (++focusedTicks > 180)
            throw new IllegalStateException("Focused navigation timed out at stage " + focusedStage
                    + "; screen=" + client.screen);

        switch (focusedStage) {
            case 0 -> {
                if (!(client.screen instanceof GuidePonderScreen ui)) return false;
                var toneLesson = GuideBook.lessons().stream().filter(lesson -> lesson.id().equals("certus_growth"))
                        .findFirst().orElseThrow(() -> new IllegalStateException("Missing focused caption tone fixture"));
                ui.tick();
                assertGuideCourseContext(ui, toneLesson, "nonfirst lesson first tick");
                if (((PonderUIAccessor) (Object) ui).ae2ponder$getReferredToByTag() != null)
                    throw new IllegalStateException("Guide course context set Ponder's one-shot referred tag selector");
                focusedDirectIndexStableChecked = true;
                focusedCaptionPaletteSequence = GuideCaptionTimelineValidation
                        .verifyMixedTonePaletteSequence(ui, toneLesson, 4);
                focusedCaptionPalettesChecked = true;
                verifyTooltipToneColors();
                focusedTooltipToneColorsChecked = true;
                focusedStage = 15;
                focusedTicks = 0;
            }
            case 15 -> {
                if (!(client.screen instanceof GuidePonderScreen ui)) return false;
                GuideValidation.screenshot("focused-caption-hint.png");
                var lesson = GuideBook.lessons().stream().filter(value -> value.id().equals("certus_growth"))
                        .findFirst().orElseThrow();
                GuideCaptionTimelineValidation.seekAndVerifyMixedToneUnit(ui, lesson, 4, 0);
                focusedStage = 16;
                focusedTicks = 0;
            }
            case 16 -> {
                if (!(client.screen instanceof GuidePonderScreen ui)) return false;
                GuideValidation.screenshot("focused-caption-warning.png");
                var lesson = GuideBook.lessons().stream().filter(value -> value.id().equals("certus_growth"))
                        .findFirst().orElseThrow();
                GuideCaptionTimelineValidation.seekAndVerifyMixedToneUnit(ui, lesson, 4, 1);
                focusedStage = 17;
                focusedTicks = 0;
            }
            case 17 -> {
                if (!(client.screen instanceof GuidePonderScreen)) return false;
                GuideValidation.screenshot("focused-caption-normal.png");
                focusedToneScreenshotsCaptured = true;
                client.setScreen(new InventoryScreen(client.player));
                focusedStage = 1;
                focusedTicks = 0;
            }
            case 1 -> {
                if (!(client.screen instanceof InventoryScreen)
                        && !(client.screen instanceof CreativeModeInventoryScreen)) return false;
                assertNoGuideButton(client.screen, "Inventory");
                focusedInventoryChecked = true;
                client.setScreen(focusedOptionsScreen);
                client.player.connection.sendCommand("ponder-ae2");
                focusedStage = 2;
                focusedTicks = 0;
            }
            case 2 -> {
                if (!(client.screen instanceof PonderTagIndexScreen index)) return false;
                if (index.children().stream().noneMatch(PonderButton.class::isInstance))
                    throw new IllegalStateException("Native Ponder index buttons were removed");
                focusedStartTag = PonderIndex.getTagAccess().getRegisteredTag(
                        GuideResources.of("ae2ponder", GuideChapter.START.id()));
                if (focusedStartTag == null)
                    throw new IllegalStateException("Ponder index did not register the active start tag");
                clickTagIndexEntry(index, focusedStartTag.getId());
                focusedStage = 3;
                focusedTicks = 0;
            }
            case 3 -> {
                if (!(client.screen instanceof PonderTagScreen tagScreen)
                        || !tagScreen.getTag().getId().equals(focusedStartTag.getId())) return false;
                selectFirstRelatedLesson(tagScreen);
                beginPonderTooltipHold();
                focusedStage = 4;
                focusedTicks = 0;
            }
            case 4 -> {
                tickPonderTooltipHold();
                if (!(client.screen instanceof PonderUI ui)) return false;
                requirePonderTooltipResult();
                assertNativeComponentContext(ui, selectedRelatedLesson.itemId(), focusedStartTag);
                focusedNativeWChecked = true;
                focusedStage = 18;
                focusedTicks = 0;
            }
            case 18 -> {
                if (!(client.screen instanceof PonderUI ui) || !clickActiveCategory(ui, focusedStartTag))
                    return false;
                focusedStage = 5;
                focusedTicks = 0;
            }
            case 5 -> {
                if (!(client.screen instanceof PonderTagScreen tagScreen)
                        || !tagScreen.getTag().getId().equals(focusedStartTag.getId())) return false;
                String previousLessonId = selectedRelatedLesson.lesson().id();
                selectDifferentRelatedLesson(tagScreen, focusedStartTag.getId().getPath(), previousLessonId);
                if (selectedRelatedLesson.lesson().id().equals(previousLessonId)
                        || !selectedRelatedLesson.lesson().chapter().equals(focusedStartTag.getId().getPath()))
                    throw new IllegalStateException("Native category did not select a different lesson from the same chapter");
                focusedDifferentSameChapterLessonChecked = true;
                clickFirstRelatedItem(tagScreen);
                focusedStage = 6;
                focusedTicks = 0;
            }
            case 6 -> {
                if (!(client.screen instanceof GuidePonderScreen ui)) return false;
                assertGuideCourseContext(ui, selectedRelatedLesson.lesson(), "Ponder tag item");
                focusedCourseContextChecked = true;
                clickPonderBack();
                focusedStage = 7;
                focusedTicks = 0;
            }
            case 7 -> {
                if (!(client.screen instanceof PonderTagScreen tagScreen)
                        || !tagScreen.getTag().getId().equals(focusedStartTag.getId())) return false;
                if (selectedRelatedLesson == null)
                    throw new IllegalStateException("Focused navigation lost its selected related component");
                focusedTagRouteChecked = true;
                var boundary = firstChapterBoundary();
                focusedBoundaryFrom = boundary[0];
                focusedBoundaryTo = boundary[1];
                client.player.connection.sendCommand("ae2ponder lesson " + focusedBoundaryFrom.id());
                focusedStage = 8;
                focusedTicks = 0;
            }
            case 8 -> {
                if (!(client.screen instanceof GuidePonderScreen ui)
                        || !ui.getActiveScene().getId().getPath().equals(focusedBoundaryFrom.id())) return false;
                assertGuideCourseContext(ui, focusedBoundaryFrom, "lesson command");
                if (!clickReadyNativeArrow(ui, true)) return false;
                focusedStage = 9;
                focusedTicks = 0;
            }
            case 9 -> {
                if (!(client.screen instanceof GuidePonderScreen ui)
                        || !ui.getActiveScene().getId().getPath().equals(focusedBoundaryTo.id())) return false;
                assertGuideCourseContext(ui, focusedBoundaryTo, "next lesson across chapters");
                if (!clickReadyNativeArrow(ui, false)) return false;
                focusedStage = 10;
                focusedTicks = 0;
            }
            case 10 -> {
                if (!(client.screen instanceof GuidePonderScreen ui)
                        || !ui.getActiveScene().getId().getPath().equals(focusedBoundaryFrom.id())) return false;
                assertGuideCourseContext(ui, focusedBoundaryFrom, "previous lesson across chapters");
                focusedCrossChapterChecked = true;
                ui.tick();
                assertGuideCourseContext(ui, focusedBoundaryFrom, "post-context synchronization tick");
                if (((PonderUIAccessor) (Object) ui).ae2ponder$getReferredToByTag() != null)
                    throw new IllegalStateException("Chapter context left a pending native tag selection");
                assertRebuildPreservedSceneState(ui);
                focusedStage = 19;
                focusedTicks = 0;
            }
            case 19 -> {
                if (!(client.screen instanceof GuidePonderScreen ui)) return false;
                var chapterTag = PonderIndex.getTagAccess().getRegisteredTag(
                        GuideResources.of("ae2ponder", focusedBoundaryFrom.chapter()));
                if (chapterTag == null)
                    throw new IllegalStateException("Cross-chapter course lost its active native category: "
                            + focusedBoundaryFrom.chapter());
                if (!clickActiveCategory(ui, chapterTag)) return false;
                focusedStage = 11;
                focusedTicks = 0;
            }
            case 11 -> {
                if (!(client.screen instanceof PonderTagScreen tagScreen)
                        || !tagScreen.getTag().getId().equals(focusedStartTag.getId())) return false;
                clickPonderBack();
                focusedStage = 12;
                focusedTicks = 0;
            }
            case 12 -> {
                if (!(client.screen instanceof PonderTagIndexScreen)) return false;
                clickPonderBack();
                focusedStage = 13;
                focusedTicks = 0;
            }
            case 13 -> {
                if (!ValidationPlatform.isOptionsScreen(client.screen)) return false;
                if (client.screen != focusedOptionsScreen)
                    throw new IllegalStateException("Native Ponder back did not restore its command origin Options screen");
                focusedCommandOriginChecked = true;
                releasePonderKey();
                var addonChapters = activeAddonChapters();
                if (addonChapters.isEmpty()) {
                    focusedAddonIndexRouteChecked = true;
                    focusedLegacyAddonCommandChecked = GuideNavigation.firstLessonForChapter("addons") == null;
                    focusedStage = 14;
                    return true;
                }
                focusedAddonIndexChapterId = addonChapters.get(0).id();
                client.player.connection.sendCommand("ponder-ae2");
                focusedStage = 30;
                focusedTicks = 0;
            }
            case 30 -> {
                if (!(client.screen instanceof PonderTagIndexScreen index)) return false;
                clickTagIndexEntry(index, GuideResources.of("ae2ponder", focusedAddonIndexChapterId));
                focusedStage = 31;
                focusedTicks = 0;
            }
            case 31 -> {
                if (!(client.screen instanceof PonderTagScreen tagScreen)
                        || !tagScreen.getTag().getId().equals(GuideResources.of("ae2ponder", focusedAddonIndexChapterId)))
                    return false;
                focusedAddonIndexRouteChecked = true;
                clickPonderBack();
                focusedStage = 32;
                focusedTicks = 0;
            }
            case 32 -> {
                if (!(client.screen instanceof PonderTagIndexScreen)) return false;
                clickPonderBack();
                focusedStage = 33;
                focusedTicks = 0;
            }
            case 33 -> {
                if (!ValidationPlatform.isOptionsScreen(client.screen) || client.screen != focusedOptionsScreen) return false;
                focusedAddonCommandIndex = 0;
                client.player.connection.sendCommand("ponder-ae2 " + activeAddonChapters().get(0).id());
                focusedStage = 20;
                focusedTicks = 0;
            }
            case 20 -> {
                var addonChapter = activeAddonChapters().get(focusedAddonCommandIndex);
                var expected = GuideNavigation.firstLessonForChapter(addonChapter.id());
                if (!(client.screen instanceof GuidePonderScreen ui)
                        || expected == null || !ui.getActiveScene().getId().getPath().equals(expected.id())) return false;
                assertGuideCourseContext(ui, expected, "add-on chapter command " + addonChapter.id());
                focusedAddonCommandChecks.put(addonChapter.id(), expected.id());
                clickBack();
                focusedStage = 21;
                focusedTicks = 0;
            }
            case 21 -> {
                if (!ValidationPlatform.isOptionsScreen(client.screen) || client.screen != focusedOptionsScreen) return false;
                focusedAddonCommandIndex++;
                if (focusedAddonCommandIndex < activeAddonChapters().size()) {
                    client.player.connection.sendCommand("ponder-ae2 " + activeAddonChapters().get(focusedAddonCommandIndex).id());
                    focusedStage = 20;
                } else {
                    client.player.connection.sendCommand("ponder-ae2 addons");
                    focusedStage = 23;
                }
                focusedTicks = 0;
            }
            case 23 -> {
                var firstAddon = activeAddonChapters().get(0);
                var expected = GuideNavigation.firstLessonForChapter(firstAddon.id());
                if (!(client.screen instanceof GuidePonderScreen ui)
                        || expected == null || !ui.getActiveScene().getId().getPath().equals(expected.id())) return false;
                focusedLegacyAddonCommandChecked = true;
                assertGuideCourseContext(ui, expected, "legacy add-on chapter alias command");
                clickBack();
                focusedStage = 24;
                focusedTicks = 0;
            }
            case 24 -> {
                if (!ValidationPlatform.isOptionsScreen(client.screen) || client.screen != focusedOptionsScreen) return false;
                focusedStage = 14;
                return true;
            }
            default -> throw new IllegalStateException("Unexpected focused navigation stage: " + focusedStage);
        }
        return false;
    }

    static Screen focusedReturnScreen() { return focusedOptionsScreen; }

    private static List<GuideChapter> activeAddonChapters() {
        return GuideChapter.active(GuideBook.lessons()).stream().filter(GuideChapter::isAddon).toList();
    }

    private static void verifyOptionalLessonFilter() {
        var loadedMods = Platform.loadedModIds();
        GuideNavigationValidation.verifyLocalizedTargets(Minecraft.getInstance().getLanguageManager().getSelected());
        var expected = GuideBook.lessonsForMods(loadedMods).stream().map(GuideBook.Lesson::id).toList();
        var actual = GuideBook.lessons().stream().map(GuideBook.Lesson::id).toList();
        if (!actual.equals(expected))
            throw new IllegalStateException("Active lesson catalogue does not match the loaded optional-mod set");
        for (var lesson : GuideBook.allLessons()) {
            if (loadedMods.containsAll(lesson.requiresMods()) != actual.contains(lesson.id()))
                throw new IllegalStateException("Optional lesson leaked across its mod filter: " + lesson.id());
        }
        for (var chapter : GuideChapter.values()) {
            if (!chapter.isAddon()) continue;
            var chapterLessons = GuideBook.lessons().stream()
                    .filter(lesson -> lesson.chapter().equals(chapter.id())).toList();
            boolean addonLesson = !chapterLessons.isEmpty();
            var addonTagId = GuideResources.of("ae2ponder", chapter.id());
            var addonTag = PonderIndex.getTagAccess().getRegisteredTag(addonTagId);
            if (!addonLesson) {
                if (addonTag.getId().equals(addonTagId)
                        || PonderIndex.getTagAccess().getListedTags().stream().anyMatch(tag -> tag.getId().equals(addonTagId))
                        || !PonderIndex.getTagAccess().getItems(addonTagId).isEmpty())
                    throw new IllegalStateException("Unavailable optional chapter is registered: " + chapter.id());
                continue;
            }
            if (!addonTag.getId().equals(addonTagId))
                throw new IllegalStateException("Optional chapter is missing from the Ponder index: " + chapter.id());
            boolean tagListed = PonderIndex.getTagAccess().getListedTags().stream()
                    .anyMatch(tag -> tag.getId().equals(addonTagId));
            if (!tagListed)
                throw new IllegalStateException("Optional chapter is registered but missing from the Ponder index: " + chapter.id());

            var expectedItems = new HashSet<ResourceLocation>();
            expectedItems.add(GuideResources.parse(chapterLessons.get(0).icon()));
            chapterLessons.forEach(lesson -> {
                expectedItems.add(GuideResources.parse(lesson.icon()));
                lesson.components().stream().map(GuideResources::parse).forEach(expectedItems::add);
            });
            var actualItems = new HashSet<>(PonderIndex.getTagAccess().getItems(addonTag));
            if (!addonTag.getMainItem().isEmpty())
                actualItems.add(BuiltInRegistries.ITEM.getKey(addonTag.getMainItem().getItem()));
            if (!actualItems.equals(expectedItems))
                throw new IllegalStateException("Optional chapter exposes incorrect items: " + chapter.id());

            int checkedWItems = 0;
            for (var itemId : expectedItems) {
                var item = BuiltInRegistries.ITEM.getOptional(itemId).orElseThrow(() ->
                        new IllegalStateException("Optional chapter item is absent: " + chapter.id() + "/" + itemId));
                var itemTags = PonderIndex.getTagAccess().getTags(itemId);
                if (itemTags.stream().noneMatch(tag -> tag.getId().equals(addonTagId)))
                    throw new IllegalStateException("Optional chapter item has no matching standard category: " + itemId);
                var target = GuideNavigation.firstLessonForItem(itemId, chapter.id());
                if (target == null || !PonderIndex.getSceneAccess().doScenesExistForId(itemId))
                    throw new IllegalStateException("Optional chapter item has no W-accessible scene: " + itemId);
                var itemScenes = PonderIndex.getSceneAccess().compile(itemId);
                if (itemScenes.stream().noneMatch(scene -> scene.getId().equals(GuideResources.of("ae2ponder", target.id()))))
                    throw new IllegalStateException("Optional chapter scene is absent from the native W course: "
                            + itemId + "/" + target.id());
                // Shared components can open a core lesson first; W keeps every actual item category.
                var nativeTarget = GuideNavigation.firstLessonForItem(itemId, null);
                var ui = PonderUI.of(new ItemStack(item));
                if (nativeTarget == null || ui == null
                        || !ui.getActiveScene().getId().equals(GuideResources.of("ae2ponder", nativeTarget.id())))
                    throw new IllegalStateException("W opened a different initial item scene for " + itemId
                            + ": expected=" + (nativeTarget == null ? "<none>" : nativeTarget.id())
                            + ", actual=" + (ui == null ? "<none>" : ui.getActiveScene().getId()));
                var nativeTag = PonderIndex.getTagAccess().getRegisteredTag(GuideResources.of("ae2ponder", nativeTarget.chapter()));
                assertNativeComponentContext(ui, itemId, nativeTag);
                checkedWItems++;
            }
            focusedAddonChapterChecks.put(chapter.id(), Map.of("lessons", chapterLessons.size(),
                    "items", expectedItems.size(), "wItems", checkedWItems, "tagRegistered", true, "tagListed", true,
                    "tagItemsMatch", true));
        }
    }

    private static void verifyGuideItemFactoryAndOriginEquivalence() {
        var lessons = GuideBook.lessons();
        if (lessons.size() < 2) throw new IllegalStateException("Focused origin checks require at least two lessons");
        var first = lessons.get(0);
        var guideItem = BuiltInRegistries.ITEM.getOptional(GuidePlugin.GUIDE_ITEM)
                .orElseThrow(() -> new IllegalStateException("Missing Ponder guide item: " + GuidePlugin.GUIDE_ITEM));
        var guideItemUi = PonderUI.of(new ItemStack(guideItem));
        if (!(guideItemUi instanceof GuidePonderScreen guideFromItem))
            throw new IllegalStateException("Native Ponder factory did not route only ae2:guide to the full guide course");
        assertGuideCourseContext(guideFromItem, first, "native W on ae2:guide");
        guideFromItem.tick();
        assertGuideCourseContext(guideFromItem, first, "native W on ae2:guide first tick");
        focusedGuideItemFactoryChecked = true;

        focusedOriginBaselineLocale = Minecraft.getInstance().getLanguageManager().getSelected();
        focusedOriginBaselineScreen = new GuidePonderScreen(first);
        var differentSceneLesson = lessons.stream().filter(lesson -> !lesson.id().equals(first.id()))
                .findFirst().orElseThrow();
        var differentScene = new GuidePonderScreen(differentSceneLesson);
        if (!focusedOriginBaselineScreen.isEquivalentTo(new GuidePonderScreen(first)))
            throw new IllegalStateException("Equivalent guide course screens were not reusable");
        if (focusedOriginBaselineScreen.isEquivalentTo(differentScene)
                || differentScene.isEquivalentTo(focusedOriginBaselineScreen))
            throw new IllegalStateException("Guide screens for different scenes were treated as equivalent");

        ItemStack ordinaryStack = null;
        for (var lesson : lessons) {
            var ids = new ArrayList<String>();
            ids.add(lesson.icon());
            ids.addAll(lesson.components());
            for (String rawId : ids) {
                var itemId = GuideResources.parse(rawId);
                if (itemId.equals(GuidePlugin.GUIDE_ITEM)) continue;
                var item = BuiltInRegistries.ITEM.getOptional(itemId).orElse(null);
                if (item == null || !PonderIndex.getSceneAccess().doScenesExistForId(itemId)) continue;
                ordinaryStack = new ItemStack(item);
                break;
            }
            if (ordinaryStack != null) break;
        }
        if (ordinaryStack == null)
            throw new IllegalStateException("No ordinary native Ponder component was available for origin checks");
        var ordinaryOne = PonderUI.of(ordinaryStack.copy());
        var ordinaryTwo = PonderUI.of(ordinaryStack.copy());
        if (!ordinaryOne.isEquivalentTo(ordinaryTwo) || !ordinaryTwo.isEquivalentTo(ordinaryOne))
            throw new IllegalStateException("Origin isolation changed equivalence between ordinary native Ponder screens");

        var foreignInitialScene = PonderUI.of(ordinaryStack.copy());
        ((PonderUIAccessor) (Object) foreignInitialScene).ae2ponder$setSubject(guideFromItem.getSubject().copy());
        try {
            var sceneIdField = PonderScene.class.getDeclaredField("sceneId");
            sceneIdField.setAccessible(true);
            var nativeScene = foreignInitialScene.getActiveScene();
            var originalSceneId = (ResourceLocation) sceneIdField.get(nativeScene);
            sceneIdField.set(nativeScene, GuideResources.of("foreignguide", originalSceneId.getPath()));
            try {
                if (!foreignInitialScene.getActiveScene().getId().getNamespace().equals("foreignguide"))
                    throw new IllegalStateException("Foreign-scene fixture did not change the native first scene namespace");
                if (focusedOriginBaselineScreen.isEquivalentTo(foreignInitialScene)
                        || foreignInitialScene.isEquivalentTo(focusedOriginBaselineScreen))
                    throw new IllegalStateException("A foreign initial native scene reused a guide course before tag selection");
            } finally {
                sceneIdField.set(nativeScene, originalSceneId);
            }
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not run the foreign initial-scene origin probe", exception);
        }
        focusedOriginEquivalenceChecked = true;
    }

    static void verifyCrossLocaleCourseEquivalence(String actualLocale) {
        if (focusedOriginBaselineScreen == null || focusedOriginBaselineLocale == null)
            throw new IllegalStateException("No baseline guide screen was saved for cross-locale equivalence");
        if (!Minecraft.getInstance().getLanguageManager().getSelected().equals(actualLocale))
            throw new IllegalStateException("Cross-locale equivalence check ran before the requested locale reload completed");
        if (focusedOriginBaselineLocale.equals(actualLocale))
            throw new IllegalStateException("Cross-locale fixture did not change the selected locale: " + actualLocale);
        var sameSceneOtherLocale = new GuidePonderScreen(GuideBook.lessons().get(0));
        if (focusedOriginBaselineScreen.isEquivalentTo(sameSceneOtherLocale)
                || sameSceneOtherLocale.isEquivalentTo(focusedOriginBaselineScreen))
            throw new IllegalStateException("Equivalent scene screens compiled for different reloaded locales were reused");
        focusedOriginComparedLocale = actualLocale;
        focusedCrossLocaleEquivalenceChecked = true;
    }

    private static GuideBook.Lesson[] firstChapterBoundary() {
        var lessons = GuideBook.lessons();
        for (int index = 0; index + 1 < lessons.size(); index++) {
            var current = lessons.get(index);
            var next = lessons.get(index + 1);
            if (!current.chapter().equals(next.chapter())) return new GuideBook.Lesson[]{current, next};
        }
        throw new IllegalStateException("Focused navigation requires at least two active chapters");
    }

    private static void assertNativeComponentContext(PonderUI ui, ResourceLocation itemId, PonderTag expectedTag) {
        if (!itemId.equals(BuiltInRegistries.ITEM.getKey(ui.getSubject().getItem())))
            throw new IllegalStateException("Native Ponder W changed its actual component context: "
                    + BuiltInRegistries.ITEM.getKey(ui.getSubject().getItem()) + " vs " + itemId);
        Set<ResourceLocation> expectedTags = PonderIndex.getTagAccess().getTags(itemId).stream()
                .map(tag -> tag.getId()).collect(java.util.stream.Collectors.toSet());
        Set<ResourceLocation> actualTags = ((PonderUIAccessor) (Object) ui).ae2ponder$getTags().stream()
                .map(tag -> tag.getId()).collect(java.util.stream.Collectors.toSet());
        if (!actualTags.equals(expectedTags) || !actualTags.contains(expectedTag.getId()))
            throw new IllegalStateException("Native Ponder W category tags do not match the actual component: item="
                    + itemId + ", expected=" + expectedTags + ", actual=" + actualTags);
        if (ui.getActiveScene().getTags().stream().noneMatch(tag -> tag.getId().equals(expectedTag.getId())))
            throw new IllegalStateException("Native Ponder W did not select a same-chapter lesson: "
                    + ui.getActiveScene().getId() + ", chapter=" + expectedTag.getId());
    }

    private static void assertGuideCourseContext(GuidePonderScreen ui, GuideBook.Lesson expected, String source) {
        if (!ui.getActiveScene().getId().getPath().equals(expected.id()))
            throw new IllegalStateException(source + " opened the wrong guide scene: " + ui.getActiveScene().getId()
                    + ", expected=" + expected.id());
        ResourceLocation expectedIcon = GuideResources.parse(expected.icon());
        ResourceLocation actualIcon = BuiltInRegistries.ITEM.getKey(ui.getSubject().getItem());
        if (!expectedIcon.equals(actualIcon))
            throw new IllegalStateException(source + " did not update the active lesson icon: " + actualIcon
                    + ", expected=" + expectedIcon);
        var tags = ((PonderUIAccessor) (Object) ui).ae2ponder$getTags();
        ResourceLocation expectedTag = GuideResources.of("ae2ponder", expected.chapter());
        if (tags.size() != 1 || !tags.get(0).getId().equals(expectedTag))
            throw new IllegalStateException(source + " did not keep the exact active chapter tag: "
                    + tags.stream().map(tag -> tag.getId().toString()).toList() + ", expected=" + expectedTag);
    }

    private static void assertRebuildPreservedSceneState(PonderUI ui) {
        var scene = ui.getActiveScene();
        int targetTime = Math.max(0, Math.min(53, scene.getTotalTime() - 1));
        ui.seekToTime(targetTime);
        int timeBeforeRebuild = scene.getCurrentTime();
        ((ScreenValidationAccessor) (Object) ui).ae2guidevalidation$rebuildWidgets();
        if (Minecraft.getInstance().screen != ui || ui.getActiveScene() != scene
                || ui.getActiveScene().getCurrentTime() != timeBeforeRebuild)
            throw new IllegalStateException("Updating the active chapter widgets reset Ponder scene state");
    }

    private static void verifyTooltipToneColors() {
        var entry = GuideItemDescriptions.descriptions().get(0);
        var itemId = GuideResources.parse(entry.items().get(0));
        var stack = new ItemStack(BuiltInRegistries.ITEM.get(itemId));
        var collapsed = new ArrayList<Component>();
        GuideItemDescriptions.append(stack, collapsed, false);
        if (collapsed.isEmpty() || collapsed.get(0).getStyle().getColor() == null
                || collapsed.get(0).getStyle().getColor().getValue() != CaptionTone.HINT.textColor())
            throw new IllegalStateException("Collapsed item-description hint did not use the HINT text color");

        var expanded = new ArrayList<Component>();
        GuideItemDescriptions.append(stack, expanded, true);
        if (expanded.isEmpty() || expanded.stream().anyMatch(line -> line.getStyle().getColor() == null
                || line.getStyle().getColor().getValue() != CaptionTone.NORMAL.textColor()))
            throw new IllegalStateException("Expanded item-description text did not use the NORMAL text color");
    }

    static void tick(int frame) throws Exception {
        frame -= 100;
        var client = Minecraft.getInstance();
        if (frame == 22) checkTagPages();
        if (frame == 23) GLFW.glfwSetWindowSize(client.getWindow().getWindow(), 640, 480);
        if (frame == 26) client.setScreen(new PonderTagScreen(GuideResources.parse("ae2ponder:advanced")));
        if (frame == 28) {
            requireInside(client.screen.children().stream().filter(AbstractWidget.class::isInstance)
                    .map(AbstractWidget.class::cast).toList(), client.screen.width, client.screen.height);
            screenshot("tags-small-first.png");
        }
        if (frame == 29) pageButton((PonderTagScreen) client.screen, ">").onPress();
        if (frame == 32) screenshot("tags-small-next.png");
        if (frame == 33) GLFW.glfwSetWindowSize(client.getWindow().getWindow(), 960, 640);
        if (frame == 36) client.setScreen(new PonderTagScreen(GuideResources.parse("ae2ponder:advanced")));
        if (frame == 38) screenshot("tags-advanced.png");
        if (frame == 40) {
            var guide = Guides.getById(GUIDE);
            boolean pageExists = guide != null && guide.pageExists(PAGE);
            String pageLanguage = pageExists ? guide.getParsedPage(PAGE).getLanguage() : "<missing>";
            if (!pageExists || !"ja_jp".equals(pageLanguage))
                throw new IllegalStateException("Japanese Ponder page missing from ME Guide: guideExists=" + (guide != null)
                        + ", pageExists=" + pageExists + ", actualLanguage=" + pageLanguage);
            GuidesCommon.openGuide(client.player, GUIDE, PageAnchor.page(PAGE));
            // The hidden window starts with its pointer at the sidebar's auto-open hotspot.
            // Update Minecraft's own mouse state without moving the user's desktop pointer.
            var window = client.getWindow();
            var onMove = net.minecraft.client.MouseHandler.class.getDeclaredMethod("onMove", long.class, double.class, double.class);
            onMove.setAccessible(true);
            onMove.invoke(client.mouseHandler, window.getWindow(), window.getScreenWidth() * 0.8, window.getScreenHeight() * 0.5);
        }
        if (frame == 44) {
            if (!(client.screen instanceof GuideUiHost)) throw new IllegalStateException("ME Guide did not open");
            meGuide = client.screen;
            checkCompiledGuideContents(meGuide);
            screenshot("me-guide-ponder.png");
        }
        if (frame == 45) clickFirstGuideLink();
        if (frame == 47) {
            requireLesson(GuideBook.lessons().get(0).id());
            checkCourseOrder((PonderUI) client.screen);
        }
        if (frame == 70) clickArrow(true);
        if (frame == 72) requireLesson(GuideBook.lessons().get(1).id());
        if (frame == 77) clickArrow(false);
        if (frame == 78) requireLesson(GuideBook.lessons().get(0).id());
        if (frame == 82) clickBack();
        if (frame == 84 && client.screen != meGuide)
            throw new IllegalStateException("Native back button did not restore ME Guide");
        if (frame == 85) client.player.connection.sendCommand("ae2ponder lesson first_network");
        if (frame == 87) requireLesson("first_network");
        if (frame == 110) clickArrow(false);
        if (frame == 111) {
            int index = GuideBook.lessons().stream().map(GuideBook.Lesson::id).toList().indexOf("first_network");
            requireLesson(GuideBook.lessons().get(index - 1).id());
        }
        if (frame == 116) clickArrow(true);
        if (frame == 117) requireLesson("first_network");
        if (frame == 118) {
            var ui = (PonderUI) client.screen;
            ui.seekToTime(45);
        }
        if (frame == 121) screenshot("native-course-navigation.png");
        if (frame == 124) clickBack();
        if (frame == 126) {
            if (client.screen != meGuide) throw new IllegalStateException("Selected lesson did not return to ME Guide");
            client.player.connection.sendCommand("ae2ponder lesson " + GuideBook.lessons().get(GuideBook.lessons().size() - 1).id());
        }
        if (frame == 128) requireLesson(GuideBook.lessons().get(GuideBook.lessons().size() - 1).id());
        if (frame == 151) {
            requireLesson(GuideBook.lessons().get(GuideBook.lessons().size() - 1).id());
            clickArrow(true);
        }
        if (frame == 152) {
            requireLesson(GuideBook.lessons().get(GuideBook.lessons().size() - 1).id());
        }
        if (frame == 153) clickBack();
        if (frame == 155 && client.screen != meGuide)
            throw new IllegalStateException("Last lesson lost the GuideME return screen");
    }

    static void checkTagPages() {
        var client = Minecraft.getInstance();
        for (var size : List.of(new int[]{320, 240}, new int[]{427, 260}, new int[]{640, 360})) {
            for (var chapter : GuideChapter.active(GuideBook.lessons())) {
                var tag = PonderIndex.getTagAccess().getRegisteredTag(GuideResources.of("ae2ponder", chapter.id()));
                var screen = new PonderTagScreen(tag);
                client.setScreen(screen);
                screen.init(client, size[0], size[1]);
                String chapterId = tag.getId().getPath();
                Set<ResourceLocation> expected = PonderIndex.getTagAccess().getItems(tag).stream()
                        .filter(itemId -> GuideNavigation.firstLessonForItem(itemId, chapterId) != null)
                        .collect(java.util.stream.Collectors.toCollection(HashSet::new));
                if (!tag.getMainItem().isEmpty()) {
                    var mainId = BuiltInRegistries.ITEM.getKey(tag.getMainItem().getItem());
                    if (GuideNavigation.firstLessonForItem(mainId, chapterId) != null) expected.add(mainId);
                    else {
                        var firstLesson = GuideNavigation.firstLessonForChapter(chapterId);
                        if (firstLesson != null) expected.add(GuideResources.parse(firstLesson.icon()));
                    }
                }
                Set<ResourceLocation> seen = new HashSet<>();
                int pages = 0;
                do {
                    requireInside(screen.children().stream().filter(AbstractWidget.class::isInstance)
                            .map(AbstractWidget.class::cast).toList(), size[0], size[1]);
                    for (var child : screen.children()) {
                        if (child instanceof PonderButton button && button.getItem() != null && !button.getItem().isEmpty()) {
                            var itemId = BuiltInRegistries.ITEM.getKey(button.getItem().getItem());
                            if (!PonderIndex.getTagAccess().getTags(itemId).stream()
                                    .anyMatch(registeredTag -> registeredTag.getId().equals(tag.getId())))
                                throw new IllegalStateException("Guide tag showed an unrelated component: " + chapterId + "/" + itemId);
                            if (!seen.add(itemId))
                                throw new IllegalStateException("Duplicate item across tag pages: " + chapter.id());
                        }
                    }
                    if (!pageButton(screen, ">").active) break;
                    pageButton(screen, ">").onPress();
                    if (++pages > expected.size()) throw new IllegalStateException("Tag pagination does not terminate");
                } while (true);
                if (!seen.equals(expected)) throw new IllegalStateException("Missing related items: " + chapter.id());
                // Resize while on the last page; the page index must be clamped to its new range.
                screen.init(client, 640, 480);
                requireInside(screen.children().stream().filter(AbstractWidget.class::isInstance)
                        .map(AbstractWidget.class::cast).toList(), 640, 480);
                if (expected.size() > 0 && screen.children().stream().noneMatch(child -> child instanceof PonderButton))
                    throw new IllegalStateException("Resize stranded an empty tag page");
                layouts++;
            }
        }
    }

    private static void requireInside(List<AbstractWidget> widgets, int width, int height) {
        for (var widget : widgets) {
            if (widget.getX() < 0 || widget.getY() < 0 || widget.getX() + widget.getWidth() > width
                    || widget.getY() + widget.getHeight() > height)
                throw new IllegalStateException("Widget outside " + width + "x" + height + ": " + widget.getMessage().getString());
        }
    }

    private static Button pageButton(PonderTagScreen screen, String label) {
        return screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> button.getMessage().getString().equals(label)).findFirst().orElseThrow();
    }

    static void requireLesson(String id) {
        var screen = Minecraft.getInstance().screen;
        if (!(screen instanceof GuidePonderScreen ui) || !ui.getActiveScene().getId().getPath().equals(id))
            throw new IllegalStateException("Wrong initial or navigated Ponder lesson: " + id + "; screen=" + screen.getClass().getSimpleName()
                    + (screen instanceof PonderUI ui ? "; actual=" + ui.getActiveScene().getId() : ""));
        if (screen.children().stream().anyMatch(Button.class::isInstance))
            throw new IllegalStateException("Custom lesson navigation button remains");
    }

    static void clickTagIndexEntry(PonderTagIndexScreen screen, ResourceLocation tagId) {
        for (int page = 0; page < 64; page++) {
            var target = screen.children().stream().filter(PonderButton.class::isInstance).map(PonderButton.class::cast)
                    .filter(button -> button.getTag() != null && button.getTag().getId().equals(tagId))
                    .findFirst().orElse(null);
            if (target != null) {
                clickWidget(screen, target, "Ponder tag " + tagId);
                return;
            }

            var next = pageButton(screen, "pageNext");
            if (next == null || !next.active)
                throw new IllegalStateException("Ponder index has no visible widget for tag " + tagId);
            clickWidget(screen, next, "Ponder index next page");
        }
        throw new IllegalStateException("Ponder index pagination did not reach tag " + tagId);
    }

    static void selectFirstRelatedLesson(PonderTagScreen screen) {
        selectedRelatedLesson = screen.children().stream().filter(PonderButton.class::isInstance)
                .map(PonderButton.class::cast).filter(button -> button.getItem() != null && !button.getItem().isEmpty())
                .map(button -> relatedLesson(button, screen.getTag().getId().getPath()))
                .filter(java.util.Objects::nonNull)
                .filter(GuideUiValidation::opensMatchingNativeScene)
                .findFirst().orElseThrow(() -> new IllegalStateException(
                        "Start tag has no related item opening its first registered guide scene"));
    }

    private static void selectDifferentRelatedLesson(PonderTagScreen screen, String chapter, String excludedLessonId) {
        selectedRelatedLesson = screen.children().stream().filter(PonderButton.class::isInstance)
                .map(PonderButton.class::cast).filter(button -> button.getItem() != null && !button.getItem().isEmpty())
                .map(button -> relatedLesson(button, chapter))
                .filter(java.util.Objects::nonNull)
                .filter(related -> !related.lesson().id().equals(excludedLessonId))
                .filter(GuideUiValidation::opensMatchingNativeScene)
                .findFirst().orElseThrow(() -> new IllegalStateException("Native category has no second same-chapter lesson"));
    }

    private static RelatedLesson relatedLesson(PonderButton button, String preferredChapter) {
        var itemId = BuiltInRegistries.ITEM.getKey(button.getItem().getItem());
        if (!PonderIndex.getSceneAccess().doScenesExistForId(itemId)) return null;
        var lesson = GuideNavigation.firstLessonForItem(itemId, preferredChapter);
        return lesson == null ? null : new RelatedLesson(button, button.getItem().copy(), itemId, lesson);
    }

    private static boolean opensMatchingNativeScene(RelatedLesson related) {
        var nativeUi = PonderUI.of(related.stack().copy());
        return nativeUi.getActiveScene().getId().getPath().equals(related.lesson().id());
    }

    static void beginPonderTooltipHold() {
        if (selectedRelatedLesson == null)
            throw new IllegalStateException("No related item was selected for the Ponder tooltip hold");
        if (!PonderTooltipHandler.enable)
            throw new IllegalStateException("PonderTooltipHandler is disabled");
        tooltipHoldCompleted = false;
        ValidationPlatform.setPonderKeyDown(true);
        if (!ValidationPlatform.isPonderKeyDown())
            throw new IllegalStateException("Native Ponder key poll did not observe the simulated held key");
    }

    static void tickPonderTooltipHold() {
        if (tooltipHoldCompleted || selectedRelatedLesson == null) return;
        var client = Minecraft.getInstance();
        boolean keepKeyDown = false;
        try {
            if (client.screen instanceof PonderUI) {
                tooltipHoldCompleted = true;
                return;
            }
            if (!(client.screen instanceof PonderTagScreen))
                throw new IllegalStateException("Ponder tooltip hold left the tag screen unexpectedly: " + client.screen);
            PonderTooltipHandler.tick();
            PonderTooltipHandler.addToTooltip(new ArrayList<>(), selectedRelatedLesson.stack().copy());
            if (client.screen instanceof PonderUI) tooltipHoldCompleted = true;
            else keepKeyDown = true;
        } finally {
            if (!keepKeyDown) releasePonderKey();
        }
    }

    static void requirePonderTooltipResult() {
        releasePonderKey();
        var screen = Minecraft.getInstance().screen;
        if (!tooltipHoldCompleted || !(screen instanceof PonderUI ui))
            throw new IllegalStateException("Holding Ponder's keybind did not open its native UI; screen=" + screen);
        var subject = ui.getSubject();
        var actualItem = subject.isEmpty() ? "<empty>" : BuiltInRegistries.ITEM.getKey(subject.getItem()).toString();
        if (subject.isEmpty() || !BuiltInRegistries.ITEM.getKey(subject.getItem()).equals(selectedRelatedLesson.itemId())
                || !ui.getActiveScene().getId().getPath().equals(selectedRelatedLesson.lesson().id()))
            throw new IllegalStateException("Ponder tooltip opened the wrong item scene: item=" + actualItem
                    + ", expected item=" + selectedRelatedLesson.itemId() + ", scene=" + ui.getActiveScene().getId()
                    + ", expected scene=" + selectedRelatedLesson.lesson().id());
    }

    static void releasePonderKey() {
        ValidationPlatform.setPonderKeyDown(false);
    }

    static void clickFirstRelatedItem(PonderTagScreen screen) {
        if (selectedRelatedLesson == null) throw new IllegalStateException("No selected related lesson");
        var target = screen.children().stream().filter(PonderButton.class::isInstance).map(PonderButton.class::cast)
                .filter(button -> button.getItem() != null && !button.getItem().isEmpty())
                .filter(button -> BuiltInRegistries.ITEM.getKey(button.getItem().getItem()).equals(selectedRelatedLesson.itemId()))
                .findFirst().orElseThrow(() -> new IllegalStateException(
                        "Selected guide item is not present as a Ponder tag widget: " + selectedRelatedLesson.itemId()));
        clickWidget(screen, target, "Related guide item " + selectedRelatedLesson.itemId());
    }

    static void requireSelectedRelatedLesson() {
        if (selectedRelatedLesson == null) throw new IllegalStateException("No related lesson was selected");
        requireLesson(selectedRelatedLesson.lesson().id());
    }

    static void requireSelectedRelatedNextLesson() {
        if (selectedRelatedLesson == null) throw new IllegalStateException("No related lesson was selected");
        int index = GuideBook.lessons().indexOf(selectedRelatedLesson.lesson());
        if (index < 0 || index + 1 >= GuideBook.lessons().size())
            throw new IllegalStateException("Selected related lesson has no next course entry");
        requireLesson(GuideBook.lessons().get(index + 1).id());
    }

    static void clickPonderBack() throws ReflectiveOperationException {
        var screen = Minecraft.getInstance().screen;
        if (!(screen instanceof NavigatableSimiScreen))
            throw new IllegalStateException("Expected a native Ponder navigation screen: " + screen);
        var field = NavigatableSimiScreen.class.getDeclaredField("backTrack");
        field.setAccessible(true);
        var back = (BoxWidget) field.get(screen);
        if (back == null) throw new IllegalStateException("Native Ponder back widget was not initialized");
        clickWidget(screen, back, "Ponder back");
    }

    private static boolean clickActiveCategory(PonderUI ui, PonderTag expectedTag) throws ReflectiveOperationException {
        List<PonderTag> tags = ((PonderUIAccessor) (Object) ui).ae2ponder$getTags();
        int index = -1;
        for (int candidate = 0; candidate < tags.size(); candidate++) {
            if (tags.get(candidate).getId().equals(expectedTag.getId())) {
                index = candidate;
                break;
            }
        }
        if (index < 0)
            throw new IllegalStateException("PonderUI has no native category button for " + expectedTag.getId()
                    + "; context tags=" + tags.stream().map(tag -> tag.getId().toString()).toList());
        var field = PonderUI.class.getDeclaredField("tagButtons");
        field.setAccessible(true);
        @SuppressWarnings("unchecked") List<PonderButton> buttons = (List<PonderButton>) field.get(ui);
        if (buttons.size() != tags.size())
            throw new IllegalStateException("Native Ponder category buttons are not aligned with the screen context tags: "
                    + buttons.size() + " buttons for " + tags.size() + " tags");
        var button = buttons.get(index);
        // Native Ponder rejects clicks while its rendered fade is below 0.1.
        if (!button.isVisible()) return false;
        if (!ui.children().contains(button) || Minecraft.getInstance().screen != ui)
            throw new IllegalStateException("Native category button does not belong to the current screen");
        clickWidget(ui, button, "Native Ponder category " + expectedTag.getId());
        focusedNativeCategoryButtonClickCount++;
        return true;
    }

    private static boolean clickReadyNativeArrow(PonderUI ui, boolean forward) throws ReflectiveOperationException {
        var field = PonderUI.class.getDeclaredField(forward ? "right" : "left");
        field.setAccessible(true);
        var button = (PonderButton) field.get(ui);
        if (!button.isVisible()) return false;
        if (!ui.children().contains(button) || Minecraft.getInstance().screen != ui)
            throw new IllegalStateException("Native scene arrow does not belong to the current screen");
        clickWidget(ui, button, forward ? "Native next scene" : "Native previous scene");
        return true;
    }

    private static PonderButton pageButton(PonderTagIndexScreen screen, String fieldName) {
        try {
            var field = PonderTagIndexScreen.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            return (PonderButton) field.get(screen);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot inspect native Ponder index pagination", exception);
        }
    }

    private static void clickWidget(Screen screen, net.minecraft.client.gui.components.AbstractWidget widget, String description) {
        double x = widget.getX() + widget.getWidth() / 2.0;
        double y = widget.getY() + widget.getHeight() / 2.0;
        if (!screen.mouseClicked(x, y, 0))
            throw new IllegalStateException(description + " did not handle its native click");
    }

    private record RelatedLesson(PonderButton button, ItemStack stack, ResourceLocation itemId, GuideBook.Lesson lesson) {}

    static void checkCourseOrder(PonderUI ui) throws ReflectiveOperationException {
        var field = PonderUI.class.getDeclaredField("scenes");
        field.setAccessible(true);
        @SuppressWarnings("unchecked") var scenes = (List<PonderScene>) field.get(ui);
        if (!scenes.stream().map(scene -> scene.getId().getPath()).toList()
                .equals(GuideBook.lessons().stream().map(GuideBook.Lesson::id).toList()))
            throw new IllegalStateException("Native course omitted or reordered lessons");
    }

    static void clickArrow(boolean forward) {
        var screen = Minecraft.getInstance().screen;
        screen.mouseClicked(screen.width / 2 + (forward ? 28 : -28), screen.height - 41, 0);
    }

    static void clickBack() {
        var screen = Minecraft.getInstance().screen;
        screen.mouseClicked(41, screen.height - 41, 0);
    }

    static void checkCompiledGuideContents(Screen screen) throws ReflectiveOperationException {
        if (!(screen instanceof DocumentScreen)) throw new IllegalStateException("Expected a compiled ME Guide page");
        var getter = DocumentScreen.class.getDeclaredMethod("getDocumentWithLayout");
        getter.setAccessible(true);
        var document = (LytDocument) getter.invoke(screen);
        checkCompiledGuideContents(document);
    }

    static void verifyGuideProseColorsCompiled(String expectedLocale) throws ReflectiveOperationException {
        var client = Minecraft.getInstance();
        String selectedLocale = client.getLanguageManager().getSelected();
        if (!expectedLocale.equals(selectedLocale))
            throw new IllegalStateException("GuideME color-span check ran in the wrong locale: expected="
                    + expectedLocale + ", selected=" + selectedLocale);
        var guide = Guides.getById(GUIDE);
        if (guide == null || !guide.pageExists(PAGE))
            throw new IllegalStateException("Focused GuideME page is unavailable: " + PAGE);
        var parsedPage = guide.getParsedPage(PAGE);
        if (parsedPage == null || !expectedLocale.equals(parsedPage.getLanguage()))
            throw new IllegalStateException("GuideME page was not parsed for the selected locale: expected="
                    + expectedLocale + ", actual=" + (parsedPage == null ? "<missing>" : parsedPage.getLanguage()));
        var compiledPage = guide.getPage(PAGE);
        if (compiledPage == null || compiledPage.document() == null)
            throw new IllegalStateException("GuideME page did not compile: " + PAGE);
        var document = compiledPage.document();
        checkCompiledGuideContents(document);

        int[] coloredSpans = {0};
        document.visit(new LytVisitor() {
            @Override public Result beforeFlowContent(LytFlowContent content) {
                if (!(content instanceof LytFlowSpan span)) return Result.CONTINUE;
                var color = span.getStyle().color();
                // Links are also spans; their native cyan is intentionally preserved.
                if (span instanceof LytFlowLink) {
                    if (color != SymbolicColor.LINK)
                        throw new IllegalStateException("GuideME link lost its native color: " + color);
                    return Result.CONTINUE;
                }
                if (!(color instanceof ConstantColor constant)) return Result.CONTINUE;
                if (constant.lightModeColor() != 0xfff6f6e5
                        || constant.darkModeColor() != 0xfff6f6e5)
                    throw new IllegalStateException("GuideME explanatory span compiled with an unexpected color: " + color);
                coloredSpans[0]++;
                return Result.CONTINUE;
            }
        });
        if (coloredSpans[0] < 3)
            throw new IllegalStateException("GuideME page compiled only " + coloredSpans[0]
                    + " native explanatory color spans in " + expectedLocale);
        focusedGuideProseColorSpanCounts.put(expectedLocale, coloredSpans[0]);
    }

    private static void checkCompiledGuideContents(LytDocument document) {
        int[] links = {0};
        var text = new StringBuilder();
        document.visit(new LytVisitor() {
            @Override public Result beforeFlowContent(LytFlowContent content) {
                if (content instanceof LytFlowLink) links[0]++;
                if (content instanceof guideme.document.flow.LytFlowText flowText)
                    text.append(flowText.getText()).append(' ');
                return Result.CONTINUE;
            }
        });
        int expectedLinks = GuideBook.lessons().size() + 1; // One start link and one per available lesson.
        if (links[0] != expectedLinks)
            throw new IllegalStateException("Compiled ME Guide has " + links[0] + " links; expected " + expectedLinks);

        var availableIds = GuideBook.lessons().stream().map(GuideBook.Lesson::id).collect(java.util.stream.Collectors.toSet());
        String displayText = logicalGuideText(text.toString());
        for (var chapter : GuideChapter.values()) {
            if (!chapter.isAddon()) continue;
            boolean available = availableIds.stream().map(id -> GuideBook.lessons().stream()
                    .filter(lesson -> lesson.id().equals(id)).findFirst().orElseThrow())
                    .anyMatch(lesson -> lesson.chapter().equals(chapter.id()));
            String title = logicalGuideText(Component.translatable("ae2ponder.chapter." + chapter.id()).getString());
            String description = logicalGuideText(Component.translatable(
                    "ae2ponder.ponder.tag." + chapter.id() + ".description").getString());
            if (available) {
                if (!displayText.contains(title) || !displayText.contains(description))
                    throw new IllegalStateException("Compiled ME Guide lost available add-on chapter content: " + chapter.id());
            } else if (displayText.contains(title) || displayText.contains(description)) {
                throw new IllegalStateException("Unavailable add-on chapter leaked into compiled ME Guide: " + chapter.id());
            }
        }
        for (var lesson : GuideBook.allLessons()) {
            for (String key : List.of("ae2ponder.ponder." + lesson.id() + ".header",
                    "ae2ponder.lesson." + lesson.id() + ".summary")) {
                String localizedText = logicalGuideText(Component.translatable(key).getString());
                if (availableIds.contains(lesson.id())) {
                    if (!displayText.contains(localizedText))
                        throw new IllegalStateException("Compiled ME Guide lost localized text: " + key);
                } else if (!localizedText.isBlank() && displayText.contains(localizedText)) {
                    throw new IllegalStateException("Unavailable add-on text leaked into compiled ME Guide: " + lesson.id());
                }
            }
        }
    }

    private static String logicalGuideText(String text) {
        try {
            // Undo display forms before comparison: NFKC alone adds spaces/tatweel to shaped vowel marks.
            String logical = new ArabicShaping(ArabicShaping.LETTERS_UNSHAPE).shape(text);
            return java.text.Normalizer.normalize(logical, java.text.Normalizer.Form.NFC)
                    // Lam-alef shaping moves fathatan after the ligature; both spellings are equivalent.
                    .replace("\u064b\u0627", "\u0627\u064b");
        } catch (ArabicShapingException exception) {
            throw new IllegalStateException("Cannot decode compiled ME Guide text", exception);
        }
    }

    private static void clickFirstGuideLink() throws ReflectiveOperationException {
        var screen = Minecraft.getInstance().screen;
        var getter = DocumentScreen.class.getDeclaredMethod("getDocumentWithLayout");
        getter.setAccessible(true);
        var document = (LytDocument) getter.invoke(screen);
        LytFlowLink[] first = {null};
        document.visit(new LytVisitor() {
            @Override public Result beforeFlowContent(LytFlowContent content) {
                if (first[0] == null && content instanceof LytFlowLink link) first[0] = link;
                return Result.CONTINUE;
            }
        });
        if (first[0] == null) throw new IllegalStateException("ME Guide has no Ponder link");
        var container = (LytFlowContainer) first[0].getParent();
        var rect = first[0].getChildren().stream().flatMap(child -> container.enumerateContentBounds(child))
                .filter(bounds -> !bounds.isEmpty()).findFirst().orElseThrow();
        var point = ((GuideUiHost) screen).getScreenPoint(new LytPoint(rect.x() + rect.width() / 2f, rect.y() + rect.height() / 2f));
        double scale = ((IndepentScaleScreen) screen).getEffectiveScale();
        var child = screen.getChildAt(point.x() * scale, point.y() * scale);
        if (child.isPresent() && child.get().getClass().getSimpleName().equals("GuideNavBar"))
            throw new IllegalStateException("ME Guide sidebar intercepted Ponder link at " + point);
        if (!screen.mouseClicked(point.x() * scale, point.y() * scale, 0))
            throw new IllegalStateException("ME Guide Ponder link did not handle its click");
    }

    private static void screenshot(String name) {
        var client = Minecraft.getInstance();
        Screenshot.grab(client.gameDirectory, name, client.getMainRenderTarget(), message -> {});
    }
}
