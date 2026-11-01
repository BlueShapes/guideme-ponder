package dev.ae2guide.validation;

import com.google.gson.Gson;
import com.mojang.logging.LogUtils;
import dev.ae2guide.GuideResources;
import dev.ae2guide.Platform;
import dev.ae2guide.client.GuideBook;
import dev.ae2guide.client.GuideChapter;
import dev.ae2guide.client.GuidePlugin;
import dev.ae2guide.client.GuideScenes;
import net.createmod.ponder.foundation.PonderIndex;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.element.InputWindowElement;
import net.createmod.ponder.foundation.ui.PonderTagIndexScreen;
import net.createmod.ponder.foundation.ui.PonderTagScreen;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/** Opt-in integration harness, excluded from the released mod JAR. */
public final class GuideValidation {
    private static boolean opening;
    private static boolean validating;
    private static boolean waitingScreenLogged;
    private static boolean renderedSinceLastStep;
    private static boolean languageReloadWaitingLogged;
    private static CompletableFuture<Void> initialLanguageReload;
    private static int frame;
    private static final int SCENE_START = 280;
    private static final List<PonderScene> SCENES = new ArrayList<>();
    private static final List<List<Integer>> CALLOUT_TIMES = new ArrayList<>();
    private static final List<String> CALLOUT_SCREENSHOTS = List.of(
            "first_network", "charged_fluix", "processors", "p2p_channels", "quantum_bridge", "cell_io",
            "omni_cells", "omni_crafting", "bigger_singularity", "bigger_crafting", "things_disks", "mega_fluid_disks");
    private static int verifiedCallouts;
    private static final boolean CAPTION_ONLY = Boolean.getBoolean("ae2ponder.validateCaptions");
    private static final boolean FOCUSED_ONLY = Boolean.getBoolean("ae2ponder.validateFocused");
    private static final boolean NARRATION_ONLY = Boolean.getBoolean("ae2ponder.validateNarration");
    private static final boolean COVERAGE_ONLY = Boolean.getBoolean("ae2ponder.validateCoverage");
    private static long coverageLastTickNanos;
    private static boolean captionFontIdentityLogged;
    private static java.util.Map<String, Object> narrationFocusedResult;
    private static int optionalModCombinationsChecked;

    static int optionalModCombinationsChecked() { return optionalModCombinationsChecked; }

    private GuideValidation() {}

    static boolean initialize() {
        if (!Boolean.getBoolean("ae2ponder.validate")) return false;
        TextWindowCoexistenceProbe.reset();
        var client = Minecraft.getInstance();
        if (COVERAGE_ONLY) coverageLastTickNanos = System.nanoTime();
        ValidationPlatform.hideWindow(client);
        var languageManager = client.getLanguageManager();
        String initialLocale = FOCUSED_ONLY || NARRATION_ONLY ? "en_us" : "ja_jp";
        if (!initialLocale.equals(languageManager.getSelected())) {
            languageManager.setSelected(initialLocale);
            client.options.languageCode = initialLocale;
            initialLanguageReload = client.reloadResourcePacks();
        } else {
            client.options.languageCode = initialLocale;
        }
        try {
            Files.deleteIfExists(client.gameDirectory.toPath().resolve("validation-result.json"));
            Files.deleteIfExists(client.gameDirectory.toPath().resolve("validation-failure.txt"));
        }
        catch (Exception exception) { throw new IllegalStateException("Cannot clear old validation result", exception); }
        LogUtils.getLogger().info("AE2_GUIDE_VALIDATION_INITIALIZED: current screen={}, language={}",
                screenName(client), languageManager.getSelected());
        return true;
    }

    static void tick() {
        var client = Minecraft.getInstance();
        try {
            if (COVERAGE_ONLY && System.nanoTime() - coverageLastTickNanos > 180_000_000_000L)
                throw new IllegalStateException("Item coverage received no ready rendered tick for 180 seconds: screen="
                        + screenName(client) + ", overlay=" + (client.getOverlay() == null ? "none"
                        : client.getOverlay().getClass().getName()) + ", levelPresent=" + (client.level != null));
            if (!awaitInitialLanguageReload()) return;
        } catch (Throwable exception) {
            failValidation(client, exception);
            return;
        }
        if (!opening && client.getOverlay() == null && !(client.screen instanceof TitleScreen)) {
            if (!waitingScreenLogged) {
                waitingScreenLogged = true;
                LogUtils.getLogger().info("AE2_GUIDE_VALIDATION_WAITING: current screen={}", screenName(client));
            }
            ValidationPlatform.recoverInitialScreen(client);
            return;
        }
        if (!opening && client.screen instanceof TitleScreen && client.getOverlay() == null) {
            opening = true;
            ValidationPlatform.createFreshLevel(client);
        }
        if (!opening || client.level == null || client.getOverlay() != null) return;
        if (!renderedSinceLastStep) return;
        renderedSinceLastStep = false;
        if (!validating && (client.screen != null || client.player == null)) return;
        try {
            if (!validating) {
                validating = true;
                frame = 0;
                if (COVERAGE_ONLY) {
                    validateOptionalLessons();
                    LogUtils.getLogger().info("AE2_GUIDE_ITEM_COVERAGE_READY");
                } else if (CAPTION_ONLY) {
                    if (Platform.isModLoaded("create"))
                        throw new IllegalStateException("Create must be absent during caption validation");
                    LogUtils.getLogger().info("AE2_GUIDE_CAPTION_VALIDATION_READY: skipping navigation and catalogue checks");
                } else {
                    if (FOCUSED_ONLY || NARRATION_ONLY) validateOptionalLessons();
                    if (!FOCUSED_ONLY && !NARRATION_ONLY) validateScenes();
                    client.setScreen(ValidationPlatform.createOptionsScreen(client));
                }
            }
            frame++;
            if (COVERAGE_ONLY) {
                coverageLastTickNanos = System.nanoTime();
                if (GuideItemCoverageValidation.tick()) {
                    var result = GuideItemCoverageValidation.result();
                    Files.writeString(client.gameDirectory.toPath().resolve("validation-result.json"), new Gson().toJson(result));
                    if (!Boolean.TRUE.equals(result.get("passed")))
                        Files.writeString(client.gameDirectory.toPath().resolve("validation-failure.txt"), "Item coverage validation failed");
                    client.stop();
                }
                return;
            }
            if (CAPTION_ONLY) {
                if (GuideLanguageValidation.tickCaptions()) {
                    var result = client.gameDirectory.toPath().resolve("validation-result.json");
                    Files.writeString(result, new Gson().toJson(GuideLanguageValidation.captionResult()));
                    if (!(boolean) GuideLanguageValidation.captionResult().get("passed"))
                        Files.writeString(client.gameDirectory.toPath().resolve("validation-failure.txt"),
                                "Caption font/layout overflows were recorded in validation-result.json");
                    client.stop();
                }
                return;
            }
            if (NARRATION_ONLY && narrationFocusedResult != null) {
                if (GuideLanguageValidation.tickCaptions()) {
                    var captions = new java.util.LinkedHashMap<>(GuideLanguageValidation.captionResult());
                    var result = new java.util.LinkedHashMap<String, Object>();
                    result.put("mode", "narration");
                    result.put("passed", Boolean.TRUE.equals(narrationFocusedResult.get("passed"))
                            && Boolean.TRUE.equals(captions.get("passed")));
                    result.put("focused", narrationFocusedResult);
                    result.put("captions", captions);
                    Files.writeString(client.gameDirectory.toPath().resolve("validation-result.json"), new Gson().toJson(result));
                    if (!Boolean.TRUE.equals(result.get("passed")))
                        Files.writeString(client.gameDirectory.toPath().resolve("validation-failure.txt"),
                                "Focused navigation or full-locale narration validation failed");
                    client.stop();
                }
                return;
            }
            if (FOCUSED_ONLY || NARRATION_ONLY) {
                if (GuideUiValidation.tickFocused()) {
                    if (!GuideLanguageValidation.tickFocusedArabic()) return;
                    var focusedResult = new java.util.LinkedHashMap<>(GuideUiValidation.focusedResult());
                    var arabicResult = GuideLanguageValidation.focusedArabicResult();
                    focusedResult.put("arabicCaptionFont", arabicResult);
                    focusedResult.put("passed", Boolean.TRUE.equals(focusedResult.get("passed"))
                            && Boolean.TRUE.equals(arabicResult.get("passed")));
                    if (NARRATION_ONLY) {
                        if (!Boolean.TRUE.equals(focusedResult.get("passed")))
                            throw new IllegalStateException("Focused navigation or Arabic caption validation did not complete all checks");
                        narrationFocusedResult = focusedResult;
                        LogUtils.getLogger().info("AE2_GUIDE_NARRATION_FOCUSED_PASS: {} lessons; {} chapters",
                                GuideBook.lessons().size(), focusedResult.get("availableChapters"));
                        return;
                    }
                    Files.writeString(client.gameDirectory.toPath().resolve("validation-result.json"),
                            new Gson().toJson(focusedResult));
                    if (!Boolean.TRUE.equals(focusedResult.get("passed")))
                        throw new IllegalStateException("Focused navigation or Arabic caption validation did not complete all checks");
                    LogUtils.getLogger().info("AE2_GUIDE_FOCUSED_VALIDATION_PASS: {} lessons available; {} Arabic caption units checked",
                            GuideBook.lessons().size(), arabicResult.get("checkedUnits"));
                    client.stop();
                }
                return;
            }
            if (frame == 6) {
                GuideUiValidation.assertNoGuideButton(client.screen, "Options");
                screenshot("options-no-guide-button.png");
            }
            if (frame == 7) {
                if (!ValidationPlatform.isOptionsScreen(client.screen)) throw new IllegalStateException("Options did not open");
                client.player.connection.sendCommand("ponder-ae2");
            }
            if (frame == 9) {
                if (!(client.screen instanceof PonderTagIndexScreen index))
                    throw new IllegalStateException("Ponder command did not open the standard Ponder tag index; screen=" + screenName(client));
                screenshot("tags-index.png");
                GuideUiValidation.clickTagIndexEntry(index, GuideResources.of("ae2ponder", GuideChapter.START.id()));
            }
            if (frame == 11) {
                if (!(client.screen instanceof PonderTagScreen tagScreen)
                        || !tagScreen.getTag().getId().equals(GuideResources.of("ae2ponder", GuideChapter.START.id())))
                    throw new IllegalStateException("Ponder index entry did not open the AE2 start tag; screen=" + screenName(client));
                screenshot("tags-start.png");
                GuideUiValidation.selectFirstRelatedLesson(tagScreen);
            }
            if (frame == 12) GuideUiValidation.beginPonderTooltipHold();
            // Native progress grows by max(.25, progress) * .25, requiring at least 12 ticks.
            if (frame >= 12 && frame <= 34) GuideUiValidation.tickPonderTooltipHold();
            if (frame == 35) GuideUiValidation.requirePonderTooltipResult();
            if (frame == 45) GuideUiValidation.clickBack();
            if (frame == 47 && !(client.screen instanceof PonderTagScreen))
                throw new IllegalStateException("Native Ponder back did not restore the start tag");
            if (frame == 48) {
                if (!(client.screen instanceof PonderTagScreen tagScreen))
                    throw new IllegalStateException("Related-item route lost the start tag screen");
                GuideUiValidation.clickFirstRelatedItem(tagScreen);
            }
            if (frame == 50) {
                GuideUiValidation.requireSelectedRelatedLesson();
                var ui = (PonderUI) client.screen;
                GuideUiValidation.checkCourseOrder(ui);
                ui.seekToTime(Math.min(280, ui.getActiveScene().getTotalTime() - 1));
            }
            // Native controls are hidden during Ponder's opening zoom; wait for that transition.
            if (frame == 75) GuideUiValidation.clickArrow(true);
            if (frame == 76) GuideUiValidation.requireSelectedRelatedNextLesson();
            if (frame == 85) GuideUiValidation.clickArrow(false);
            if (frame == 86) GuideUiValidation.requireSelectedRelatedLesson();
            if (frame == 90) screenshot("first-lesson.png");
            if (frame == 93) GuideUiValidation.clickBack();
            if (frame == 95 && !(client.screen instanceof PonderTagScreen))
                throw new IllegalStateException("Native back did not restore the Ponder tag screen");
            if (frame == 96) GuideUiValidation.clickPonderBack();
            if (frame == 98 && !(client.screen instanceof PonderTagIndexScreen))
                throw new IllegalStateException("Ponder tag back did not restore the standard tag index");
            if (frame == 99) GuideUiValidation.clickPonderBack();
            if (frame == 101 && !ValidationPlatform.isOptionsScreen(client.screen))
                throw new IllegalStateException("Ponder index back did not restore Options");
            if (frame == 103) client.player.connection.sendCommand("ponder-ae2");
            if (frame == 105 && !(client.screen instanceof PonderTagIndexScreen))
                throw new IllegalStateException("/ponder-ae2 did not open the standard Ponder index");
            if (frame == 106) GuideUiValidation.clickPonderBack();
            if (frame == 108 && !ValidationPlatform.isOptionsScreen(client.screen))
                throw new IllegalStateException("Canonical command index back did not restore Options");
            if (frame == 109) client.player.connection.sendCommand("ae2ponder");
            if (frame == 111 && !(client.screen instanceof PonderTagIndexScreen))
                throw new IllegalStateException("/ae2ponder did not open the standard Ponder index");
            if (frame == 112) GuideUiValidation.clickPonderBack();
            if (frame == 114 && !ValidationPlatform.isOptionsScreen(client.screen))
                throw new IllegalStateException("Alias command index back did not restore Options");
            GuideUiValidation.tick(frame);
            if (frame >= SCENE_START && frame < SCENE_START + SCENES.size() * 12 && (frame - SCENE_START) % 12 == 0) {
                int index = (frame - SCENE_START) / 12;
                client.setScreen(new ValidationPonderScreen(SCENES.get(index), CALLOUT_TIMES.get(index).get(0)));
            }
            if (frame >= SCENE_START && frame < SCENE_START + SCENES.size() * 12 && client.screen instanceof PonderUI ui) {
                int phase = (frame - SCENE_START) % 12;
                if (phase == 4) {
                    var times = CALLOUT_TIMES.get((frame - SCENE_START) / 12);
                    int target = times.get(times.size() / 2);
                    if (target > ui.getActiveScene().getCurrentTime()) ui.seekToTime(target);
                }
                if (phase == 8) ui.seekToTime(ui.getActiveScene().getTotalTime());
                boolean captureCallouts = SCENES.size() <= 6 || CALLOUT_SCREENSHOTS.contains(ui.getActiveScene().getId().getPath());
                if (phase == 3 && (captureCallouts || ((frame - SCENE_START) / 12) % 6 == 0))
                    screenshot("lesson-" + ui.getActiveScene().getId().getPath() + ".png");
                if (phase == 6 && captureCallouts)
                    screenshot("callout-" + ui.getActiveScene().getId().getPath() + ".png");
            }
            if (frame >= SCENE_START + SCENES.size() * 12) {
                TextWindowCoexistenceProbe.assertPrimaryMixinAndProbe();
                if (Boolean.getBoolean("ae2ponder.validateLanguages") && !GuideLanguageValidation.tick()) return;
                var result = client.gameDirectory.toPath().resolve("validation-result.json");
                Files.writeString(result, new Gson().toJson(java.util.Map.ofEntries(
                        java.util.Map.entry("passed", true),
                        java.util.Map.entry("lessons", SCENES.size()),
                        java.util.Map.entry("rendered", SCENES.size()),
                        java.util.Map.entry("callouts", verifiedCallouts),
                        java.util.Map.entry("tagLayouts", GuideUiValidation.layouts()),
                        java.util.Map.entry("nativeNavigation", true),
                        java.util.Map.entry("guideMeLink", true),
                        java.util.Map.entry("languages", GuideLanguageValidation.checkedLanguages()),
                        java.util.Map.entry("checkedCaptions", GuideLanguageValidation.checkedCaptions()),
                        java.util.Map.entry("createInstalled", false),
                        java.util.Map.entry("loadedAddons", List.of("ae2omnicells", "bigger_ae2", "ae2things", "ae2_mega_things")
                                .stream().filter(Platform::isModLoaded).toList()))));
                LogUtils.getLogger().info("AE2_GUIDE_VALIDATION_PASS: {} storyboards compiled, replayed and rendered; {} item callouts verified",
                        SCENES.size(), verifiedCallouts);
                client.stop();
            }
        } catch (Throwable exception) {
            failValidation(client, exception);
        }
    }

    private static boolean awaitInitialLanguageReload() {
        if (initialLanguageReload == null) return true;
        if (!initialLanguageReload.isDone()) {
            if (!languageReloadWaitingLogged) {
                languageReloadWaitingLogged = true;
                LogUtils.getLogger().info("AE2_GUIDE_VALIDATION_WAITING: Japanese resource reload is still running");
            }
            return false;
        }
        try {
            initialLanguageReload.join();
            initialLanguageReload = null;
            LogUtils.getLogger().info("AE2_GUIDE_VALIDATION_LANGUAGE_READY: {}",
                    Minecraft.getInstance().getLanguageManager().getSelected());
            return true;
        } catch (CompletionException exception) {
            throw new IllegalStateException("Could not load the initial guide locale", exception.getCause());
        }
    }

    private static void failValidation(Minecraft client, Throwable exception) {
        try { GuideUiValidation.releasePonderKey(); }
        catch (Throwable releaseFailure) { exception.addSuppressed(releaseFailure); }
        LogUtils.getLogger().error("AE2_GUIDE_VALIDATION_FAIL", exception);
        try { Files.writeString(client.gameDirectory.toPath().resolve("validation-failure.txt"), exception.toString()); }
        catch (Exception writeFailure) { LogUtils.getLogger().error("Cannot write validation failure", writeFailure); }
        if (COVERAGE_ONLY) {
            try {
                var result = new java.util.LinkedHashMap<>(GuideItemCoverageValidation.result());
                result.put("passed", false);
                result.put("failure", exception.toString());
                Files.writeString(client.gameDirectory.toPath().resolve("validation-result.json"), new Gson().toJson(result));
            } catch (Exception writeFailure) { LogUtils.getLogger().error("Cannot write partial item coverage result", writeFailure); }
        } else if (CAPTION_ONLY) {
            try {
                var result = GuideLanguageValidation.captionResult();
                result.put("passed", false);
                result.put("failure", exception.toString());
                Files.writeString(client.gameDirectory.toPath().resolve("validation-result.json"),
                        new Gson().toJson(result));
            } catch (Exception writeFailure) {
                LogUtils.getLogger().error("Cannot write partial caption validation result", writeFailure);
            }
        } else if (NARRATION_ONLY) {
            try {
                var result = new java.util.LinkedHashMap<String, Object>();
                result.put("mode", "narration");
                result.put("passed", false);
                result.put("focused", narrationFocusedResult == null ? GuideUiValidation.focusedResult() : narrationFocusedResult);
                result.put("captions", GuideLanguageValidation.captionResult());
                result.put("failure", exception.toString());
                Files.writeString(client.gameDirectory.toPath().resolve("validation-result.json"), new Gson().toJson(result));
            } catch (Exception writeFailure) { LogUtils.getLogger().error("Cannot write partial narration result", writeFailure); }
        } else if (FOCUSED_ONLY) {
            try {
                var result = new java.util.LinkedHashMap<>(GuideUiValidation.focusedResult());
                result.put("passed", false);
                result.put("failure", exception.toString());
                Files.writeString(client.gameDirectory.toPath().resolve("validation-result.json"),
                        new Gson().toJson(result));
            } catch (Exception writeFailure) {
                LogUtils.getLogger().error("Cannot write partial focused validation result", writeFailure);
            }
        }
        client.stop();
    }

    private static void validateScenes() {
        if (Platform.isModLoaded("create")) throw new IllegalStateException("Create must be absent during validation");
        validateOptionalLessons();
        GuideDescriptionValidation.verifyHooks();
        String selection = System.getProperty("ae2ponder.validationLessons", "");
        var selectedIds = selection.isBlank() ? List.<String>of() : List.of(selection.split(","));
        var lessons = GuideBook.lessons().stream().filter(lesson -> selectedIds.isEmpty() || selectedIds.contains(lesson.id())).toList();
        if (lessons.isEmpty() || (!selectedIds.isEmpty() && lessons.size() != selectedIds.size()))
            throw new IllegalArgumentException("Unknown validation lesson selection: " + selection);
        for (var lesson : lessons) {
            requireItem(lesson.icon());
            lesson.components().forEach(GuideValidation::requireItem);
            for (var component : lesson.components()) {
                if (!PonderIndex.getSceneAccess().doScenesExistForId(GuideResources.parse(component)))
                    throw new IllegalStateException("Item has no tooltip Ponder entry: " + component);
            }
            lesson.steps().stream().filter(step -> step.flow() != null).forEach(step -> requireItem(step.flow().item()));
            lesson.steps().stream().flatMap(step -> step.callouts().stream()).forEach(callout -> requireItem(callout.item()));
            var scene = PonderIndex.getSceneAccess().compile(List.of(GuidePlugin.entry(lesson.id()))).get(0);
            if (scene.getBounds().maxY() != 4 || scene.getBounds().maxX() != 8) throw new IllegalStateException("Incorrect stage bounds");
            scene.seekToTime(scene.getTotalTime());
            checkBlocks(lesson, scene);
            scene.begin();
            if (lesson.id().equals("cable_colors")) checkAnchorIsolation(lesson, scene);
            CALLOUT_TIMES.add(checkCallouts(lesson, scene));
            scene.seekToTime(scene.getTotalTime());
            checkBlocks(lesson, scene);
            SCENES.add(scene);
            LogUtils.getLogger().info("AE2_GUIDE_SCENE_PASS {} ({} nodes, {} steps)", lesson.id(), lesson.nodes().size(), lesson.steps().size());
        }
    }

    private static void validateOptionalLessons() {
        var supportedAddons = List.of("ae2omnicells", "bigger_ae2", "ae2things", "ae2_mega_things");
        var allLessons = GuideBook.allLessons();
        for (var lesson : allLessons) {
            if (supportedAddons.contains(lesson.chapter()) && lesson.requiresMods().isEmpty())
                throw new IllegalStateException("Add-on lesson has no optional mod requirement: " + lesson.id());
            if (!supportedAddons.containsAll(lesson.requiresMods()))
                throw new IllegalStateException("Lesson requires an untested add-on: " + lesson.id());
            if (!lesson.requiresMods().isEmpty() && !lesson.chapter().equals(lesson.requiresMods().get(0)))
                throw new IllegalStateException("Add-on lesson is not in its own mod chapter: " + lesson.id());
        }

        for (int mask = 0; mask < 1 << supportedAddons.size(); mask++) {
            var loaded = new java.util.HashSet<String>();
            for (int bit = 0; bit < supportedAddons.size(); bit++)
                if ((mask & (1 << bit)) != 0) loaded.add(supportedAddons.get(bit));
            var expected = allLessons.stream().filter(lesson -> loaded.containsAll(lesson.requiresMods()))
                    .map(GuideBook.Lesson::id).toList();
            var actual = GuideBook.lessonsForMods(loaded).stream().map(GuideBook.Lesson::id).toList();
            if (!actual.equals(expected)) throw new IllegalStateException("Optional lesson filter failed for add-on mask " + mask);
            var activeChapterIds = GuideChapter.active(GuideBook.lessonsForMods(loaded)).stream()
                    .map(GuideChapter::id).toList();
            var expectedAddonChapters = supportedAddons.stream().filter(loaded::contains).toList();
            var actualAddonChapters = activeChapterIds.stream().filter(supportedAddons::contains).toList();
            if (!actualAddonChapters.equals(expectedAddonChapters))
                throw new IllegalStateException("Optional chapter filter failed for add-on mask " + mask
                        + ": expected=" + expectedAddonChapters + ", actual=" + actualAddonChapters);
        }

        var loadedAddons = supportedAddons.stream().filter(Platform::isModLoaded).collect(java.util.stream.Collectors.toSet());
        boolean expectAddons = Boolean.getBoolean("ae2ponder.expectAddons");
        if (expectAddons && !loadedAddons.equals(Set.copyOf(supportedAddons)))
            throw new IllegalStateException("All four optional add-ons were expected, found " + loadedAddons);
        if (!expectAddons && !loadedAddons.isEmpty())
            throw new IllegalStateException("The no-add-on test configuration unexpectedly loaded " + loadedAddons);

        var expectedAvailable = GuideBook.lessonsForMods(loadedAddons);
        if (!GuideBook.lessons().stream().map(GuideBook.Lesson::id).toList()
                .equals(expectedAvailable.stream().map(GuideBook.Lesson::id).toList()))
            throw new IllegalStateException("Runtime lesson availability disagrees with loaded add-ons");
        for (var addon : supportedAddons) {
            var chapter = java.util.Arrays.stream(GuideChapter.values()).filter(value -> value.id().equals(addon))
                    .findFirst().orElseThrow(() -> new IllegalStateException("Missing add-on chapter: " + addon));
            if (!chapter.isAddon())
                throw new IllegalStateException("Supported add-on ID is not marked as an add-on chapter: " + addon);
            var addonLessons = expectedAvailable.stream().filter(lesson -> lesson.chapter().equals(addon)).toList();
            var addonTagId = GuideResources.of("ae2ponder", addon);
            var addonTag = PonderIndex.getTagAccess().getRegisteredTag(addonTagId);
            if (addonLessons.isEmpty()) {
                // Ponder returns its not_registered sentinel for an unknown tag, rather than null.
                if (addonTag.getId().equals(addonTagId)
                        || PonderIndex.getTagAccess().getListedTags().stream().anyMatch(tag -> tag.getId().equals(addonTagId))
                        || !PonderIndex.getTagAccess().getItems(addonTagId).isEmpty())
                    throw new IllegalStateException("An unavailable add-on chapter is registered: " + addon);
            } else {
                if (!addonTag.getId().equals(addonTagId))
                    throw new IllegalStateException("The installed add-on chapter was not registered: " + addon);
                var expectedItems = new java.util.HashSet<ResourceLocation>();
                expectedItems.add(GuideResources.parse(addonLessons.get(0).icon()));
                addonLessons.forEach(lesson -> {
                    expectedItems.add(GuideResources.parse(lesson.icon()));
                    lesson.components().stream().map(GuideResources::parse).forEach(expectedItems::add);
                });
                var actualItems = new java.util.HashSet<>(PonderIndex.getTagAccess().getItems(addonTag));
                if (!addonTag.getMainItem().isEmpty())
                    actualItems.add(BuiltInRegistries.ITEM.getKey(addonTag.getMainItem().getItem()));
                if (!actualItems.equals(expectedItems))
                    throw new IllegalStateException("The add-on chapter exposes missing or unavailable related items: " + addon);
            }
        }

        for (var lesson : expectedAvailable) {
            requireItem(lesson.icon());
            lesson.components().forEach(GuideValidation::requireItem);
            GuidePlugin.entry(lesson.id());
        }
        for (var lesson : allLessons) {
            if (expectedAvailable.contains(lesson)) continue;
            try {
                GuidePlugin.entry(lesson.id());
                throw new IllegalStateException("Unavailable add-on lesson was registered: " + lesson.id());
            } catch (IllegalStateException expected) {
                if (!expected.getMessage().startsWith("Ponder lesson has not been registered:")) throw expected;
            }
        }

        var compiledCourse = PonderIndex.getSceneAccess().compile(GuidePlugin.course()).stream()
                .map(scene -> scene.getId().getPath()).toList();
        if (!compiledCourse.equals(expectedAvailable.stream().map(GuideBook.Lesson::id).toList()))
            throw new IllegalStateException("Native Ponder course includes hidden lessons or has the wrong order");
        optionalModCombinationsChecked = 1 << supportedAddons.size();
        LogUtils.getLogger().info("AE2_GUIDE_OPTIONAL_PASS: {} loaded; {} available lessons; 16 mod combinations checked",
                loadedAddons, expectedAvailable.size());
    }

    private static List<Integer> checkCallouts(GuideBook.Lesson lesson, PonderScene scene) {
        try {
            var itemField = InputWindowElement.class.getDeclaredField("item");
            itemField.setAccessible(true);
            var times = new ArrayList<Integer>();
            if (scene.getKeyframeCount() != lesson.steps().size()) throw new IllegalStateException("Missing step keyframes");
            for (int stepIndex = 0; stepIndex < lesson.steps().size(); stepIndex++) {
                var step = lesson.steps().get(stepIndex);
                if (step.callouts().isEmpty()) continue;
                int slotDuration = GuideScenes.captionDuration(lesson, stepIndex) / step.callouts().size();
                for (int slot = 0; slot < step.callouts().size(); slot++) {
                    int time = scene.getKeyframeTime(stepIndex) + 12 + slot * slotDuration + 15;
                    scene.seekToTime(time);
                    var visible = scene.getElements().stream().filter(InputWindowElement.class::isInstance)
                            .map(InputWindowElement.class::cast).filter(InputWindowElement::isVisible).toList();
                    if (visible.size() != 1) throw new IllegalStateException("Missing or overlapping callouts: " + lesson.id() + "/" + stepIndex);
                    var bubble = visible.get(0);
                    var item = (ItemStack) itemField.get(bubble);
                    if (bubble.getFade(1) < .99f || !BuiltInRegistries.ITEM.getKey(item.getItem()).toString().equals(step.callouts().get(slot).item()))
                        throw new IllegalStateException("Wrong or invisible callout item: " + lesson.id() + "/" + stepIndex + "/" + slot);
                    times.add(time);
                    verifiedCallouts++;
                }
            }
            if (times.isEmpty()) throw new IllegalStateException("No item callouts in " + lesson.id());
            return List.copyOf(times);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot inspect Ponder item callouts", exception);
        }
    }

    private static void checkAnchorIsolation(GuideBook.Lesson lesson, PonderScene scene) {
        // Rewind and replay: restarting the storyboard must remove the later anchor.
        try {
            for (int step : List.of(2, 3, 2, 3)) {
                if (step == 2) scene.begin();
                scene.seekToTime(scene.getKeyframeTime(step) + 30);
                var front = scene.getWorld().getBlockEntity(lesson.node("c2").position());
                var neighbor = scene.getWorld().getBlockEntity(lesson.node("red1").position());
                boolean connected = step == 2;
                var getPart = front.getClass().getMethod("getPart", Direction.class);
                Object attachment = getPart.invoke(front, Direction.NORTH);
                boolean anchor = attachment != null && attachment.getClass().getName().equals("appeng.parts.misc.CableAnchorPart");
                var cable = getPart.invoke(front, (Object) null);
                var adjacent = getPart.invoke(neighbor, (Object) null);
                var isConnected = cable.getClass().getMethod("isConnected", Direction.class);
                var bus = front.getClass().getMethod("getCableBus").invoke(front);
                var render = bus.getClass().getMethod("getRenderState").invoke(bus);
                var connections = (java.util.Map<?, ?>) render.getClass().getMethod("getConnectionTypes").invoke(render);
                var attachments = (java.util.Map<?, ?>) render.getClass().getMethod("getAttachments").invoke(render);
                if ((boolean) isConnected.invoke(cable, Direction.NORTH) != connected
                        || (boolean) isConnected.invoke(adjacent, Direction.SOUTH) != connected
                        || anchor == connected
                        || connections.containsKey(Direction.NORTH) != connected
                        || attachments.containsKey(Direction.NORTH) == connected)
                    throw new IllegalStateException("Anchor connection/attachment model is incorrect at step " + step);
            }
            scene.begin();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot inspect AE2 cable attachment model", exception);
        }
    }

    private static void checkBlocks(GuideBook.Lesson lesson, PonderScene scene) {
        for (var node : lesson.nodes()) {
            var expected = BuiltInRegistries.BLOCK.get(GuideResources.parse(node.block()));
            if (!scene.getWorld().getBlockState(node.position()).is(expected))
                throw new IllegalStateException("Missing stage model: " + lesson.id() + "/" + node.id());
            if (!node.nbt().equals("{}") && scene.getWorld().getBlockEntity(node.position()) == null)
                throw new IllegalStateException("Missing stage block entity: " + lesson.id() + "/" + node.id());
            if (node.block().equals("ae2:cable_bus")) {
                var blockEntity = scene.getWorld().getBlockEntity(node.position());
                var expectedNbt = GuideScenes.parseNbt(node.nbt());
                lesson.steps().forEach(step -> {
                    if (step.nbt().containsKey(node.id())) expectedNbt.merge(GuideScenes.parseNbt(step.nbt().get(node.id())));
                });
                var actual = ValidationPlatform.saveWithoutMetadata(blockEntity, scene.getWorld().registryAccess());
                for (var slot : List.of("cable", "up", "down", "north", "south", "east", "west")) {
                    String id = expectedNbt.getCompound(slot).getString("id");
                    if (!id.isEmpty() && !actual.getCompound(slot).getString("id").equals(id))
                        throw new IllegalStateException("Missing AE2 part: " + lesson.id() + "/" + node.id() + "/" + slot + ": " + id);
                }
                var state = scene.getWorld().getBlockState(node.position());
                var model = Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
                var data = model.getModelData(scene.getWorld(), node.position(), state,
                        ValidationPlatform.getModelData(scene.getWorld(), node.position()));
                int quads = 0;
                for (var layer : RenderType.chunkBufferLayers()) {
                    quads += model.getQuads(state, null, RandomSource.create(1L), data, layer).size();
                    for (var direction : Direction.values()) quads += model.getQuads(state, direction, RandomSource.create(1L), data, layer).size();
                }
                if (quads == 0) throw new IllegalStateException("Invisible AE2 cable model: " + lesson.id() + "/" + node.id());
            }
        }
    }

    private static void requireItem(String id) {
        if (!BuiltInRegistries.ITEM.containsKey(GuideResources.parse(id))) throw new IllegalStateException("Unknown guide item: " + id);
    }

    static void screenshot(String name) {
        var client = Minecraft.getInstance();
        Screenshot.grab(client.gameDirectory, name, client.getMainRenderTarget(), message -> LogUtils.getLogger().info("{}", message.getString()));
    }

    static void openCaptionSample(String lessonId, int stepIndex, int unitIndex) {
        var client = Minecraft.getInstance();
        if (client.getOverlay() != null)
            throw new IllegalStateException("Caption validation tried to open Ponder while a client overlay was active");
        var lesson = GuideBook.lessons().stream().filter(value -> value.id().equals(lessonId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Caption sample lesson is unavailable: " + lessonId));
        var scene = PonderIndex.getSceneAccess().compile(List.of(GuidePlugin.entry(lessonId))).get(0);
        var durations = GuideScenes.captionUnitDurations(lesson, stepIndex);
        int captionStart = scene.getKeyframeTime(stepIndex) + 12 + 20;
        for (int unit = 0; unit < unitIndex; unit++)
            captionStart += durations.get(unit) + dev.ae2guide.client.CaptionPages.PAGE_GAP_TICKS;
        if (captionStart >= scene.getTotalTime())
            throw new IllegalStateException("Caption sample has no renderable first display unit: " + lessonId);
        var expected = GuideCaptionValidation.normalizeForSearch(
                net.minecraft.network.chat.Component.translatable("ae2ponder.ponder.shared." + lessonId + "_" + stepIndex).getString());
        if (expected.isBlank())
            throw new IllegalStateException("Caption sample text is unavailable: " + lessonId);
        clientResetCaptionProbe();
        var screen = new ValidationPonderScreen(scene, captionStart);
        client.setScreen(screen);
        if (!captionFontIdentityLogged) {
            var clientFont = client.font;
            var ponderFont = screen.getFontRenderer();
            LogUtils.getLogger().info("AE2_GUIDE_CAPTION_FONT_IDENTITY: locale={}, clientFont={}, ponderFont={}, overlay={}",
                    client.getLanguageManager().getSelected(), System.identityHashCode(clientFont),
                    System.identityHashCode(ponderFont), client.getOverlay());
            if (clientFont != ponderFont)
                throw new IllegalStateException("Ponder UI did not capture the active Minecraft font for caption validation");
            captionFontIdentityLogged = true;
        }
    }

    private static void clientResetCaptionProbe() {
        TextWindowCoexistenceProbe.resetCaptionObservation();
    }

    private static String screenName(Minecraft client) {
        return client.screen == null ? "<none>" : client.screen.getClass().getSimpleName();
    }

    static void rendered() {
        var client = Minecraft.getInstance();
        if (client.getOverlay() != null) {
            renderedSinceLastStep = false;
            return;
        }
        var screen = client.screen;
        TextWindowCoexistenceProbe.recordCaptionFrame(screen instanceof PonderUI ui ? ui.getActiveScene() : null);
        renderedSinceLastStep = true;
    }

    private static final class ValidationPonderScreen extends PonderUI {
        private final int initialTime;
        private ValidationPonderScreen(PonderScene scene, int initialTime) {
            super(List.of(scene));
            this.initialTime = initialTime;
        }
        @Override protected void init() {
            super.init();
            seekToTime(initialTime);
        }
    }
}
