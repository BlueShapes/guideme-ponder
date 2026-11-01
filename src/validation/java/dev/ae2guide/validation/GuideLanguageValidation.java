package dev.ae2guide.validation;

import com.mojang.logging.LogUtils;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import dev.ae2guide.GuideResources;
import dev.ae2guide.client.GuideBook;
import dev.ae2guide.client.GuidePonderScreen;
import dev.ae2guide.client.GuideTextLayout;
import dev.ae2guide.client.RightToLeftText;
import guideme.Guides;
import guideme.GuidesCommon;
import guideme.PageAnchor;
import guideme.internal.GuideMEClient;
import guideme.internal.search.GuideSearch;
import net.createmod.ponder.Ponder;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.concurrent.CompletableFuture;
import org.lwjgl.glfw.GLFW;

/** Verifies all supported locales after full reloads; captures page and Ponder UI for representative scripts. */
final class GuideLanguageValidation {
    private static final ResourceLocation GUIDE = GuideResources.parse("ae2:guide");
    private static final ResourceLocation PAGE = GuideResources.parse("ae2ponder:ponder.md");
    private static final List<String> SAMPLES = List.of("hi_in", "zh_hk", "pt_pt", "ar_sa", "ko_kr", "vi_vn", "tr_tr");
    private static int checkedLanguages;
    private static int checkedCaptions;
    private static String originalLanguage;
    private static List<String> languages = List.of();
    private static int languageIndex;
    private static String displayingSampleLocale;
    private static boolean restoringOriginalLanguage;
    private static int frame;
    private static CompletableFuture<Void> reload;
    private static String captionOriginalLanguage;
    private static int originalGuiScale;
    private static CompletableFuture<Void> captionReload;
    private static List<String> captionLocales = List.of();
    private static int captionLocaleIndex;
    private static boolean captionRestoringLanguage;
    private static int captionLanguagesChecked;
    private static int expectedCaptionKeys = -1;
    private static int captionUnitsChecked;
    private static final List<GuideCaptionValidation.Overflow> captionOverflows = new ArrayList<>();
    private static final List<GuideCaptionValidation.Measurement> captionMeasurements = new ArrayList<>();
    private static final List<Map<String, Object>> nativeCaptionSamples = new ArrayList<>();
    private static final List<Map<String, Object>> captionTimelineSamples = new ArrayList<>();
    private record CaptionSample(String lesson, int step) {}
    private static List<CaptionSample> captionSampleLessons = List.of();
    private static int captionSampleLessonIndex;
    private static int captionSampleUnitIndex;
    private static int captionSampleStepIndex;
    private static List<String> captionSampleUnits = List.of();
    private static String captionSampleLocale;
    private static String captionSampleText;
    private static String captionSampleLesson;
    private static String captionSampleStage;
    private static int captionSampleFrame;
    private static boolean captionFontIdentityLogged;
    private static boolean focusedArabicStarted;
    private static boolean focusedArabicRestored;
    private static String focusedArabicStage;
    private static String focusedArabicOriginalLanguage;
    private static int focusedArabicOriginalGuiScale;
    private static int focusedArabicTicks;
    private static CompletableFuture<Void> focusedArabicReload;
    private static GuideCaptionValidation.Report focusedArabicReport;
    private static ArabicNativeSample focusedArabicSample;
    private static Map<String, Object> focusedArabicNativeResult = Map.of();

    private GuideLanguageValidation() {}

    static int checkedLanguages() { return checkedLanguages; }
    static int checkedCaptions() { return checkedCaptions; }

    /** Runs one Arabic font reload and real native-frame check after focused navigation. */
    static boolean tickFocusedArabic() throws Exception {
        var client = Minecraft.getInstance();
        if (focusedArabicStage == null) {
            if (client.getOverlay() != null)
                throw new IllegalStateException("Focused Arabic validation started while a client overlay was active");
            focusedArabicStarted = true;
            focusedArabicOriginalLanguage = client.getLanguageManager().getSelected();
            focusedArabicOriginalGuiScale = client.options.guiScale().get();
            focusedArabicStage = "reload_arabic";
            focusedArabicReload = startCaptionReload(client, "ar_sa");
            return false;
        }

        if (focusedArabicReload != null) {
            // Resource futures complete before the overlay fade; do not touch the new font until both finish.
            if (!focusedArabicReload.isDone() || client.getOverlay() != null) return false;
            focusedArabicReload.join();
            focusedArabicReload = null;
            if (focusedArabicStage.equals("reload_arabic")) {
                verifyFocusedArabicFont(client);
                focusedArabicSample = chooseArabicPunctuationSample(client.font,
                        read(GuideResources.of("ae2ponder", "lang/ar_sa.json")));
                GLFW.glfwSetWindowSize(client.getWindow().getWindow(), 1280, 960);
                client.resizeDisplay();
                focusedArabicStage = "resize_sample";
                focusedArabicTicks = 0;
                return false;
            }
            if (focusedArabicStage.equals("restore_locale")) {
                if (!client.getLanguageManager().getSelected().equals(focusedArabicOriginalLanguage))
                    throw new IllegalStateException("Focused Arabic validation did not restore locale: "
                            + focusedArabicOriginalLanguage);
                client.options.guiScale().set(focusedArabicOriginalGuiScale);
                client.resizeDisplay();
                client.setScreen(GuideUiValidation.focusedReturnScreen());
                focusedArabicRestored = true;
                focusedArabicStage = "done";
                return true;
            }
            throw new IllegalStateException("Unexpected focused Arabic reload stage: " + focusedArabicStage);
        }

        if (client.getOverlay() != null) return false;
        if (focusedArabicStage.equals("resize_sample")) {
            // At 960x640 Minecraft clamps scale 4 to 2; set it only after the larger framebuffer exists.
            if (++focusedArabicTicks == 1) {
                client.options.guiScale().set(4);
                client.resizeDisplay();
                return false;
            }
            if (focusedArabicTicks < 3) return false;
            requireGuiSize(client, 320, 240, "focused Arabic");
            GuideValidation.openCaptionSample(focusedArabicSample.lesson(), focusedArabicSample.step(),
                    focusedArabicSample.unitIndex());
            focusedArabicStage = "render_sample";
            focusedArabicTicks = 0;
            return false;
        }
        if (focusedArabicStage.equals("render_sample")) {
            if (++focusedArabicTicks < 25) return false;
            if (!(client.screen instanceof PonderUI))
                throw new IllegalStateException("Focused Arabic native sample lost its Ponder screen");
            var rendered = TextWindowCoexistenceProbe.renderedCaption(focusedArabicSample.text());
            int guiWidth = client.getWindow().getGuiScaledWidth();
            boolean complete = rendered != null && rendered.text().equals(focusedArabicSample.text())
                    && rendered.width() == GuideTextLayout.CAPTION_WIDTH
                    && rendered.lineCount() >= 2 && rendered.lineCount() <= 3
                    && rendered.x() >= GuideTextLayout.LEFT_MARGIN
                    && rendered.x() + rendered.width() <= guiWidth - GuideTextLayout.RIGHT_MARGIN
                    && focusedArabicSample.punctuation().codePoints().anyMatch(codePoint ->
                            rendered.text().indexOf(codePoint) >= 0);
            if (!complete)
                throw new IllegalStateException("Arabic native Ponder frame did not preserve punctuation in a wrapped "
                        + "caption: sample=" + focusedArabicSample + ", rendered=" + rendered + ", guiWidth=" + guiWidth);
            TextWindowCoexistenceProbe.assertRtlMixinAndProbe();
            focusedArabicNativeResult = Map.ofEntries(
                    Map.entry("locale", "ar_sa"), Map.entry("lesson", focusedArabicSample.lesson()),
                    Map.entry("step", focusedArabicSample.step()), Map.entry("unitIndex", focusedArabicSample.unitIndex()),
                    Map.entry("expectedText", focusedArabicSample.text()), Map.entry("renderedText", rendered.text()),
                    Map.entry("punctuation", focusedArabicSample.punctuation()),
                    Map.entry("width", rendered.width()), Map.entry("lineCount", rendered.lineCount()),
                    Map.entry("x", rendered.x()), Map.entry("guiWidth", guiWidth),
                    Map.entry("screenshot", "focused-arabic-native.png"), Map.entry("passed", true));
            screenshot("focused-arabic-native.png");
            client.setScreen(null);
            focusedArabicStage = "restore_locale";
            focusedArabicReload = startCaptionReload(client, focusedArabicOriginalLanguage);
            return false;
        }
        return focusedArabicStage.equals("done");
    }

    static Map<String, Object> focusedArabicResult() {
        boolean passed = focusedArabicStarted && focusedArabicRestored && focusedArabicReport != null
                && focusedArabicReport.checkedUnits() == 659 && focusedArabicReport.overflows().isEmpty()
                && Boolean.TRUE.equals(focusedArabicNativeResult.get("passed"));
        return Map.ofEntries(Map.entry("passed", passed), Map.entry("locale", "ar_sa"),
                Map.entry("checkedUnits", focusedArabicReport == null ? 0 : focusedArabicReport.checkedUnits()),
                Map.entry("checkedWidth", GuideTextLayout.CAPTION_WIDTH),
                Map.entry("overflowCount", focusedArabicReport == null ? -1 : focusedArabicReport.overflows().size()),
                Map.entry("nativePunctuationSample", focusedArabicNativeResult),
                Map.entry("originalLocaleRestored", focusedArabicRestored));
    }

    private static void verifyFocusedArabicFont(Minecraft client) throws Exception {
        if (!"ar_sa".equals(client.getLanguageManager().getSelected()))
            throw new IllegalStateException("Focused caption reload selected the wrong Arabic locale: "
                    + client.getLanguageManager().getSelected());
        var translations = read(GuideResources.of("ae2ponder", "lang/ar_sa.json"));
        for (var entry : translations.entrySet()) {
            if (GuideCaptionValidation.isCaptionKey(entry.getKey())
                    && !Language.getInstance().getOrDefault(entry.getKey()).equals(entry.getValue()))
                throw new IllegalStateException("Arabic caption fell back during the focused reload: " + entry.getKey());
        }
        focusedArabicReport = GuideCaptionValidation.verify(Locale.forLanguageTag("ar-SA"), client.font, translations);
        if (focusedArabicReport.checkedUnits() != 659 || !focusedArabicReport.overflows().isEmpty())
            throw new IllegalStateException("Arabic captions failed the live 180px font check: units="
                    + focusedArabicReport.checkedUnits() + ", overflows=" + focusedArabicReport.overflows().stream().limit(12).toList());
        GuideUiValidation.verifyGuideProseColorsCompiled("ar_sa");
        GuideUiValidation.verifyCrossLocaleCourseEquivalence("ar_sa");
    }

    private static ArabicNativeSample chooseArabicPunctuationSample(Font font, Map<String, String> translations) {
        String prefix = "ae2ponder.ponder.shared.";
        var available = GuideBook.lessons().stream().map(GuideBook.Lesson::id)
                .collect(java.util.stream.Collectors.toSet());
        var keys = translations.keySet().stream().filter(GuideCaptionValidation::isCaptionKey).sorted().toList();
        for (String preferred : List.of(prefix + "charged_fluix_4", prefix + "first_network_0", prefix + "processing_patterns_4")) {
            if (keys.contains(preferred)) {
                var sample = findArabicPunctuationSample(font, translations.get(preferred), preferred, prefix, available);
                if (sample != null) return sample;
            }
        }
        for (String key : keys) {
            var sample = findArabicPunctuationSample(font, translations.get(key), key, prefix, available);
            if (sample != null) return sample;
        }
        throw new IllegalStateException("Arabic captions contain no available punctuation unit that wraps at 180px");
    }

    private static ArabicNativeSample findArabicPunctuationSample(Font font, String caption, String key, String prefix,
                                                                   java.util.Set<String> availableLessons) {
        String suffix = key.substring(prefix.length());
        int stepSeparator = suffix.lastIndexOf('_');
        if (stepSeparator < 1) return null;
        String lesson = suffix.substring(0, stepSeparator);
        if (!availableLessons.contains(lesson)) return null;
        final int step;
        try { step = Integer.parseInt(suffix.substring(stepSeparator + 1)); }
        catch (NumberFormatException ignored) { return null; }
        List<String> units = dev.ae2guide.client.CaptionPages.split(caption);
        for (int unitIndex = 0; unitIndex < units.size(); unitIndex++) {
            String unit = units.get(unitIndex);
            int punctuation = unit.codePoints().filter(GuideLanguageValidation::isArabicPunctuation).findFirst().orElse(-1);
            if (punctuation < 0) continue;
            var wrapped = RightToLeftText.wrap(font, unit, GuideTextLayout.CAPTION_WIDTH, Style.EMPTY);
            if (wrapped.size() < 2 || wrapped.size() > dev.ae2guide.client.CaptionPages.MAX_LINES) continue;
            String visualText = wrapped.stream().map(net.minecraft.network.chat.FormattedText::getString)
                    .collect(java.util.stream.Collectors.joining());
            if (visualText.indexOf(punctuation) < 0) continue;
            return new ArabicNativeSample(lesson, step, unitIndex, unit,
                    new String(Character.toChars(punctuation)));
        }
        return null;
    }

    private static boolean isArabicPunctuation(int codePoint) {
        return "،؛؟…:.,!?".codePoints().anyMatch(candidate -> candidate == codePoint);
    }

    private static CompletableFuture<Void> startCaptionReload(Minecraft client, String locale) {
        if (client.getOverlay() != null)
            throw new IllegalStateException("Cannot begin a focused caption reload while an overlay is active");
        client.setScreen(createCaptionReloadScreen());
        client.getLanguageManager().setSelected(locale);
        client.options.languageCode = locale;
        return client.reloadResourcePacks();
    }

    private record ArabicNativeSample(String lesson, int step, int unitIndex, String text, String punctuation) {}

    /** Dedicated locale/font and native text-window pass that skips navigation and catalogue UI checks. */
    static boolean tickCaptions() throws Exception {
        var client = Minecraft.getInstance();
        if (captionOriginalLanguage == null) {
            captionOriginalLanguage = client.getLanguageManager().getSelected();
            originalGuiScale = client.options.guiScale().get();
            // Keep the GUI at 320x240 and 480x360 as the framebuffer changes below.
            client.options.guiScale().set(4);
            client.resizeDisplay();
            captionLocales = loadLanguages();
            captionReload(captionLocales.get(0));
            return false;
        }
        if (captionReload != null) {
            // The reload future completes before LoadingOverlay's fade ends. Keep the
            // old Ponder UI out of that final overlay render, which can outlive its font.
            if (!captionReload.isDone() || client.getOverlay() != null) return false;
            captionReload.join();
            captionReload = null;
            if (captionRestoringLanguage) {
                if (!client.getLanguageManager().getSelected().equals(captionOriginalLanguage))
                    throw new IllegalStateException("Caption validation did not restore the original locale: "
                            + captionOriginalLanguage);
                return finishCaptionValidation();
            }
            String locale = captionLocales.get(captionLocaleIndex);
            verifyCaptionLocale(locale);
            captionLanguagesChecked++;
            var sampleLessons = captionSampleLessons(locale);
            if (!sampleLessons.isEmpty()) {
                captionSampleLocale = locale;
                captionSampleLessons = sampleLessons;
                captionSampleLessonIndex = 0;
                captionSampleUnitIndex = 0;
                beginCaptionSample(client);
                return false;
            }
            advanceCaptionLocale();
            return false;
        }
        if (captionSampleStage != null) return tickCaptionSample(client);
        return false;
    }

    static Map<String, Object> captionResult() {
        var result = new LinkedHashMap<String, Object>();
        boolean firstNetworkTimeline = hasCaptionTimeline("en_us", "first_network", 0);
        boolean processingTimeline = hasCaptionTimeline("en_us", "processing_patterns", 4);
        result.put("mode", "captions");
        result.put("passed", captionOverflows.isEmpty() && captionLanguagesChecked == 22
                && !nativeCaptionSamples.isEmpty() && firstNetworkTimeline && processingTimeline);
        result.put("languages", captionLanguagesChecked);
        result.put("currentLocale", captionLocaleIndex < captionLocales.size()
                ? captionLocales.get(captionLocaleIndex) : null);
        result.put("captionKeysPerLanguage", expectedCaptionKeys);
        result.put("checkedCaptionUnits", captionUnitsChecked);
        result.put("checkedWidths", GuideCaptionValidation.CHECKED_WIDTHS);
        result.put("captionTextWindowWidth", GuideTextLayout.CAPTION_WIDTH);
        result.put("localizedSearch", captionLanguagesChecked == 22);
        result.put("continuationFontFixtures", captionLanguagesChecked * 2);
        result.put("nativeSamples", List.copyOf(nativeCaptionSamples));
        result.put("nativeTimelineSamples", List.copyOf(captionTimelineSamples));
        result.put("firstNetworkTimeline", firstNetworkTimeline);
        result.put("processingPatternsTimeline", processingTimeline);
        result.put("captionUnitLineCounts", List.copyOf(captionMeasurements));
        result.put("captionOverflows", List.copyOf(captionOverflows));
        return result;
    }

    private static boolean hasCaptionTimeline(String locale, String lesson, int step) {
        return captionTimelineSamples.stream().anyMatch(sample -> locale.equals(sample.get("locale"))
                && lesson.equals(sample.get("lesson")) && Integer.valueOf(step).equals(sample.get("step"))
                && Boolean.TRUE.equals(sample.get("rewindVerified"))
                && Boolean.TRUE.equals(sample.get("replayVerified"))
                && Boolean.TRUE.equals(sample.get("finalWindowCleanup")));
    }

    private static void verifyCaptionLocale(String locale) throws Exception {
        var client = Minecraft.getInstance();
        if (client.getLanguageManager().getLanguage(locale) == null
                || !locale.equals(client.getLanguageManager().getSelected()))
            throw new IllegalStateException("Caption font reload selected the wrong locale: " + locale);
        var own = read(GuideResources.of("ae2ponder", "lang/" + locale + ".json"));
        // Validate this locale's chapter aliases and the actual Ponder tag labels while the
        // same all-addons client is already reloading each language for font measurements.
        GuideNavigationValidation.verifyLocalizedTargets(locale);
        var keys = own.keySet().stream().filter(GuideCaptionValidation::isCaptionKey).toList();
        if (expectedCaptionKeys < 0) expectedCaptionKeys = keys.size();
        if (keys.size() != expectedCaptionKeys)
            throw new IllegalStateException("Caption key count changed in " + locale + ": expected="
                    + expectedCaptionKeys + ", actual=" + keys.size());
        for (String key : keys) {
            String value = own.get(key);
            if (value == null || value.isBlank())
                throw new IllegalStateException("Empty localized caption: " + locale + "/" + key);
            if (!Language.getInstance().getOrDefault(key).equals(value))
                throw new IllegalStateException("Caption fell back to another locale: " + locale + "/" + key);
        }
        var report = GuideCaptionValidation.verify(Locale.forLanguageTag(locale.replace('_', '-')), client.font, own);
        captionUnitsChecked += report.checkedUnits();
        captionOverflows.addAll(report.overflows());
        captionMeasurements.addAll(report.measurements());
        GuideCaptionValidation.verifyOverflowAndContinuationFixtures(
                Locale.forLanguageTag(locale.replace('_', '-')), client.font);
        var guide = Guides.getById(GUIDE);
        if (guide == null || !locale.equals(guide.getParsedPage(PAGE).getLanguage()))
            throw new IllegalStateException("GuideME caption search did not reload the selected locale: " + locale);
        verifyGuideSearch(locale, own);
    }

    private static List<CaptionSample> captionSampleLessons(String locale) {
        var available = GuideBook.lessons().stream().map(GuideBook.Lesson::id).collect(java.util.stream.Collectors.toSet());
        var candidates = switch (locale) {
            case "en_us" -> List.of(new CaptionSample("first_network", 0),
                    new CaptionSample("processing_patterns", 4), new CaptionSample("omni_materials", 2),
                    new CaptionSample("things_disks", 3));
            case "ja_jp" -> List.of(new CaptionSample("processing_patterns", 4));
            case "hi_in" -> List.of(new CaptionSample("first_network", 0));
            case "ar_sa" -> List.of(new CaptionSample("processing_patterns", 4));
            default -> List.<CaptionSample>of();
        };
        return candidates.stream().filter(sample -> available.contains(sample.lesson())).toList();
    }

    private static void beginCaptionSample(Minecraft client) {
        var sample = captionSampleLessons.get(captionSampleLessonIndex);
        captionSampleLesson = sample.lesson();
        captionSampleStepIndex = sample.step();
        String key = "ae2ponder.ponder.shared." + captionSampleLesson + "_" + captionSampleStepIndex;
        captionSampleUnits = dev.ae2guide.client.CaptionPages.split(Component.translatable(key).getString());
        captionSampleText = captionSampleUnits.get(captionSampleUnitIndex);
        if (client.getOverlay() != null) {
            captionSampleStage = "wait_overlay_before_resize";
            captionSampleFrame = 0;
            return;
        }
        prepareCaptionSample(client);
    }

    private static void prepareCaptionSample(Minecraft client) {
        client.setScreen(null);
        GLFW.glfwSetWindowSize(client.getWindow().getWindow(), 1280, 960);
        captionSampleStage = "resize_minimum";
        captionSampleFrame = 0;
        TextWindowCoexistenceProbe.resetCaptionObservation();
    }

    private static boolean tickCaptionSample(Minecraft client) throws Exception {
        if (client.getOverlay() != null) return false;
        if (captionSampleStage.equals("wait_overlay_before_resize")) {
            prepareCaptionSample(client);
            return false;
        }
        captionSampleFrame++;
        if (captionSampleStage.equals("resize_minimum") && captionSampleFrame >= 2) {
            requireGuiSize(client, 320, 240, "minimum");
            GuideValidation.openCaptionSample(captionSampleLesson, captionSampleStepIndex, captionSampleUnitIndex);
            captionSampleStage = "render_minimum";
            captionSampleFrame = 0;
        } else if (captionSampleStage.equals("render_minimum") && captionSampleFrame >= 25) {
            if (captionSampleUnitIndex == 0 && captionSampleUnits.size() > 1) {
                if (!(client.screen instanceof PonderUI ui))
                    throw new IllegalStateException("Native caption timeline lost its Ponder UI");
                var lesson = GuideBook.lessons().stream().filter(value -> value.id().equals(captionSampleLesson))
                        .findFirst().orElseThrow();
                captionTimelineSamples.add(GuideCaptionTimelineValidation.verify(ui, captionSampleLocale,
                        lesson, captionSampleStepIndex, captionSampleUnits, captionSampleUnitIndex));
                captionSampleStage = "timeline_minimum";
                captionSampleFrame = 0;
                return false;
            }
            captureNativeCaption("minimum");
            screenshot(captionScreenshotName("minimum"));
            client.setScreen(null);
            GLFW.glfwSetWindowSize(client.getWindow().getWindow(), 1920, 1440);
            TextWindowCoexistenceProbe.resetCaptionObservation();
            captionSampleStage = "resize_normal";
            captionSampleFrame = 0;
        } else if (captionSampleStage.equals("timeline_minimum") && captionSampleFrame >= 1) {
            captureNativeCaption("minimum");
            screenshot(captionScreenshotName("minimum"));
            client.setScreen(null);
            GLFW.glfwSetWindowSize(client.getWindow().getWindow(), 1920, 1440);
            TextWindowCoexistenceProbe.resetCaptionObservation();
            captionSampleStage = "resize_normal";
            captionSampleFrame = 0;
        } else if (captionSampleStage.equals("resize_normal") && captionSampleFrame >= 2) {
            requireGuiSize(client, 480, 360, "normal");
            GuideValidation.openCaptionSample(captionSampleLesson, captionSampleStepIndex, captionSampleUnitIndex);
            captionSampleStage = "render_normal";
            captionSampleFrame = 0;
        } else if (captionSampleStage.equals("render_normal") && captionSampleFrame >= 25) {
            captureNativeCaption("normal");
            screenshot(captionScreenshotName("normal"));
            client.setScreen(null);
            captionSampleUnitIndex++;
            if (captionSampleUnitIndex < captionSampleUnits.size()) {
                beginCaptionSample(client);
                return false;
            }
            captionSampleUnitIndex = 0;
            captionSampleLessonIndex++;
            if (captionSampleLessonIndex < captionSampleLessons.size()) {
                beginCaptionSample(client);
                return false;
            }
            captionSampleStage = null;
            captionSampleLocale = null;
            advanceCaptionLocale();
        }
        return false;
    }

    private static String captionScreenshotName(String size) {
        return "caption-" + size + "-" + captionSampleLocale + "-" + captionSampleLesson
                + "-" + captionSampleStepIndex + "-" + captionSampleUnitIndex + ".png";
    }

    private static void captureNativeCaption(String guiSize) {
        var client = Minecraft.getInstance();
        if (client.getOverlay() != null)
            throw new IllegalStateException("Caption validation tried to capture while a client overlay was active");
        var visibleUnits = TextWindowCoexistenceProbe.currentCaptionFrame().stream()
                .filter(caption -> captionSampleUnits.contains(caption.text())).toList();
        var rendered = TextWindowCoexistenceProbe.renderedCaption(captionSampleText);
        boolean complete = rendered != null && visibleUnits.size() == 1
                && visibleUnits.get(0).text().equals(captionSampleText);
        int actualWidth = complete ? rendered.width() : 0;
        int actualLines = complete ? rendered.lineCount() : 0;
        float actualX = complete && Float.isFinite(rendered.x()) ? rendered.x() : -1.0f;
        int guiWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        boolean horizontalBounds = complete && actualX >= GuideTextLayout.LEFT_MARGIN
                && actualX + actualWidth <= guiWidth - GuideTextLayout.RIGHT_MARGIN;
        int xModifierCalls = TextWindowCoexistenceProbe.captionXModifierCalls();
        boolean xModifierObserved = xModifierCalls > 0;
        if (!complete || actualWidth != GuideTextLayout.CAPTION_WIDTH || actualLines < 1 || actualLines > 3
                || !horizontalBounds || !xModifierObserved) {
            String reason = !complete ? "native text window did not render the whole authored unit"
                    : actualWidth != GuideTextLayout.CAPTION_WIDTH ? "native text window width differs from the required layout"
                    : actualLines < 1 || actualLines > 3 ? "native text window exceeds three rendered lines"
                    : !horizontalBounds ? "native text window violates the horizontal screen margins"
                    : "chainable caption X modifier was not observed";
            captionOverflows.add(new GuideCaptionValidation.Overflow(captionSampleLocale,
                    "ae2ponder.ponder.shared." + captionSampleLesson + "_" + captionSampleStepIndex,
                    captionSampleUnitIndex, GuideTextLayout.CAPTION_WIDTH, actualLines, reason));
        }
        var result = new LinkedHashMap<String, Object>();
        result.put("locale", captionSampleLocale);
        result.put("lesson", captionSampleLesson);
        result.put("guiSize", guiSize);
        result.put("captionKey", "ae2ponder.ponder.shared." + captionSampleLesson + "_" + captionSampleStepIndex);
        result.put("unitIndex", captionSampleUnitIndex);
        result.put("expectedText", captionSampleText);
        result.put("renderedText", complete ? rendered.text() : "");
        result.put("guiWidth", guiWidth);
        result.put("guiHeight", Minecraft.getInstance().getWindow().getGuiScaledHeight());
        result.put("completeUnit", complete);
        result.put("textWindowWidth", actualWidth);
        result.put("textWindowX", actualX);
        result.put("horizontalBounds", horizontalBounds);
        result.put("captionXModifierCalls", xModifierCalls);
        result.put("captionXModifierObserved", xModifierObserved);
        result.put("renderedLines", actualLines);
        result.put("layoutPassed", complete && actualWidth == GuideTextLayout.CAPTION_WIDTH
                && actualLines >= 1 && actualLines <= 3 && horizontalBounds && xModifierObserved);
        result.put("visibleCaptionCount", visibleUnits.size());
        result.put("sceneTick", TextWindowCoexistenceProbe.currentCaptionFrameTime());
        nativeCaptionSamples.add(Map.copyOf(result));
        TextWindowCoexistenceProbe.assertCaptionXModifierObserved();
    }

    private static void requireGuiSize(Minecraft client, int expectedWidth, int expectedHeight, String label) {
        int width = client.getWindow().getGuiScaledWidth();
        int height = client.getWindow().getGuiScaledHeight();
        if (width != expectedWidth || height != expectedHeight)
            throw new IllegalStateException("Caption validation expected the " + label + " GUI at "
                    + expectedWidth + "x" + expectedHeight + " but Minecraft reports " + width + "x" + height);
    }

    private static void advanceCaptionLocale() {
        captionLocaleIndex++;
        if (captionLocaleIndex < captionLocales.size()) {
            captionReload(captionLocales.get(captionLocaleIndex));
        } else {
            captionRestoringLanguage = true;
            captionReload(captionOriginalLanguage);
        }
    }

    private static void captionReload(String locale) {
        captionReload = startCaptionReload(Minecraft.getInstance(), locale);
    }

    private static Screen createCaptionReloadScreen() {
        return new Screen(Component.empty()) {
            @Override
            public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}
        };
    }

    private static boolean finishCaptionValidation() {
        Minecraft.getInstance().options.guiScale().set(originalGuiScale);
        Minecraft.getInstance().resizeDisplay();
        if (captionLanguagesChecked != 22)
            throw new IllegalStateException("Caption validation did not reload all 22 supported languages");
        LogUtils.getLogger().info("AE2_GUIDE_CAPTION_VALIDATION: {} locales, {} units, {} overflows, {} native samples",
                captionLanguagesChecked, captionUnitsChecked, captionOverflows.size(), nativeCaptionSamples.size());
        return true;
    }

    static boolean tick() throws Exception {
        var client = Minecraft.getInstance();
        if (originalLanguage == null) {
            originalLanguage = client.getLanguageManager().getSelected();
            languages = loadLanguages();
            beginReload(languages.get(languageIndex));
            return false;
        }
        if (reload != null) {
            if (!reload.isDone()) return false;
            reload.join();
            reload = null;
            if (restoringOriginalLanguage) {
                if (!client.getLanguageManager().getSelected().equals(originalLanguage))
                    throw new IllegalStateException("Resource reload did not restore the original language: " + originalLanguage);
                return true;
            }

            String locale = languages.get(languageIndex);
            verifyCurrentLanguage(locale);
            if (SAMPLES.contains(locale)) {
                if (Language.getInstance().isDefaultRightToLeft()) verifyRightToLeftWrapping();
                GuidesCommon.openGuide(client.player, GUIDE, PageAnchor.page(PAGE));
                displayingSampleLocale = locale;
                frame = 0;
            } else {
                advanceLanguage();
                return false;
            }
        }
        if (displayingSampleLocale == null) return false;
        frame++;
        String locale = displayingSampleLocale;
        if (frame == 4) {
            screenshot("guide-" + locale + ".png");
            GuideUiValidation.checkCompiledGuideContents(client.screen);
            if (Language.getInstance().isDefaultRightToLeft()) verifyArabicSearchText();
        }
        if (frame == 7) {
            GuidePonderScreen.open(GuideBook.lessons().stream().filter(lesson -> lesson.id().equals("first_network")).findFirst().orElseThrow());
        }
        if (frame == 9) {
            GuideUiValidation.requireLesson("first_network");
            ((PonderUI) client.screen).seekToTime(45);
        }
        if (frame == 30) {
            screenshot("ponder-" + locale + ".png");
            if (locale.equals("ar_sa")) TextWindowCoexistenceProbe.assertRtlMixinAndProbe();
        }
        if (frame == 31) client.setScreen(new GuideDescriptionValidation.TooltipScreen());
        if (frame == 34) screenshot("description-" + locale + ".png");
        if (frame == 35) {
            displayingSampleLocale = null;
            advanceLanguage();
            return false;
        }
        return false;
    }

    private static void beginReload(String locale) {
        var client = Minecraft.getInstance();
        client.getLanguageManager().setSelected(locale);
        client.options.languageCode = locale;
        reload = client.reloadResourcePacks();
    }

    private static List<String> loadLanguages() throws Exception {
        var languages = read(GuideResources.parse("ae2ponder:guide/languages.json"));
        if (languages.size() != 22) throw new IllegalStateException("Missing supported languages in the release");
        var locales = languages.keySet().stream().sorted().toList();
        if (!locales.containsAll(SAMPLES))
            throw new IllegalStateException("Missing sampled screenshot locales: " + SAMPLES.stream()
                    .filter(locale -> !locales.contains(locale)).toList());
        return locales;
    }

    private static void advanceLanguage() {
        languageIndex++;
        if (languageIndex < languages.size()) {
            beginReload(languages.get(languageIndex));
        } else {
            restoringOriginalLanguage = true;
            beginReload(originalLanguage);
        }
    }

    private static void verifyCurrentLanguage(String locale) throws Exception {
        var client = Minecraft.getInstance();
        if (client.getLanguageManager().getLanguage(locale) == null)
            throw new IllegalStateException("Unknown Minecraft locale: " + locale);
        if (!locale.equals(client.getLanguageManager().getSelected()))
            throw new IllegalStateException("Resource reload finished in the wrong locale: expected=" + locale
                    + ", selected=" + client.getLanguageManager().getSelected());

        var guide = Guides.getById(GUIDE);
        var parsedPage = guide == null ? null : guide.getParsedPage(PAGE);
        String pageLanguage = parsedPage == null ? "<missing guide page>" : parsedPage.getLanguage();
        if (!locale.equals(pageLanguage))
            throw new IllegalStateException("GuideME did not reload its translated page: expected=" + locale
                    + ", actual=" + pageLanguage);

        var own = read(GuideResources.of("ae2ponder", "lang/" + locale + ".json"));
        var captionReport = GuideCaptionValidation.verify(Locale.forLanguageTag(locale.replace('_', '-')), client.font, own);
        checkedCaptions += captionReport.checkedUnits();
        if (!captionReport.overflows().isEmpty())
            throw new IllegalStateException("Localized caption units overflow the supported Ponder window in " + locale
                    + ": " + captionReport.overflows().stream().limit(12).toList());
        var nativeText = read(GuideResources.of("ponder", "lang/" + locale + ".json"));
        var guideMe = read(GuideResources.of("guideme", "lang/" + locale + ".json"));
        GuideDescriptionValidation.verifyLocalized(own);
        for (var translations : List.of(own, nativeText, guideMe)) {
            for (var entry : translations.entrySet()) {
                if (!Language.getInstance().getOrDefault(entry.getKey()).equals(entry.getValue()))
                    throw new IllegalStateException("Runtime translation fell back: " + locale + "/" + entry.getKey());
            }
        }

        String expectedNext = nativeText.get("ponder.ui.next");
        String ponderNext = Ponder.lang().translate("ui.next").string();
        String componentNext = Component.translatable("ponder.ui.next").getString();
        if (expectedNext == null || !ponderNext.equals(expectedNext))
            throw new IllegalStateException("Ponder controls did not use the selected language: " + locale
                    + ", key=ponder.ui.next, merged locale key present=" + nativeText.containsKey("ponder.ui.next")
                    + ", expected=\"" + expectedNext + "\", merged locale stack=\"" + nativeText.get("ponder.ui.next")
                    + "\", Ponder.lang=\"" + ponderNext
                    + "\", Component.translatable=\"" + componentNext + "\"");
        GuideNavigationValidation.verifyLocalizedTargets(locale);
        GuideUiValidation.checkTagPages();
        verifyGuideSearch(locale, own);
        String prefix = locale.equals("en_us") ? "" : "_" + locale + "/";
        readPage(GuideResources.of("ae2ponder", "ae2guide/" + prefix + "ponder.md"));
        checkedLanguages++;
    }

    private static void verifyGuideSearch(String locale, Map<String, String> own) throws Exception {
        var guide = Guides.getById(GUIDE);
        var title = own.get("ae2ponder.ponder.meteorite.header");
        var summary = own.get("ae2ponder.lesson.meteorite.summary");
        var rawSubtitle = own.get("ae2ponder.ponder.shared.meteorite_0");
        var subtitle = rawSubtitle == null ? null : GuideCaptionValidation.normalizeForSearch(rawSubtitle);
        if (guide == null || title == null || summary == null || subtitle == null
                || title.isBlank() || summary.isBlank() || subtitle.isBlank())
            throw new IllegalStateException("GuideME localized search source is incomplete: " + locale);
        if (title.contains(subtitle) || summary.contains(subtitle))
            throw new IllegalStateException("Subtitle search sample is not unique to subtitle content: " + locale);
        String indexedLanguage = guide.getParsedPage(PAGE).getLanguage();
        if (!locale.equals(indexedLanguage))
            throw new IllegalStateException("GuideME search page is not using the selected locale: expected=" + locale
                    + ", actual=" + indexedLanguage);

        GuideSearch search = GuideMEClient.instance().getSearch();
        search.index(guide);
        var pendingField = GuideSearch.class.getDeclaredField("pendingTasks");
        pendingField.setAccessible(true);
        for (int pass = 0; pass < 500; pass++) {
            search.processWork();
            if (((Collection<?>) pendingField.get(search)).isEmpty()) break;
        }
        if (!((Collection<?>) pendingField.get(search)).isEmpty())
            throw new IllegalStateException("GuideME search indexing did not finish: " + locale);
        requireSearchResult(search, guide, title, locale, "title");
        requireSearchResult(search, guide, summary, locale, "summary");
        verifyIndexedText(guide, subtitle, locale, "ae2ponder.ponder.shared.meteorite_0 full subtitle");
        var subtitleUnits = dev.ae2guide.client.CaptionPages.split(rawSubtitle);
        for (int i = 0; i < subtitleUnits.size(); i++) {
            String unit = GuideCaptionValidation.normalizeForSearch(subtitleUnits.get(i));
            if (unit.isBlank())
                throw new IllegalStateException("GuideME localized subtitle contains an empty authored unit: locale="
                        + locale + ", source=ae2ponder.ponder.shared.meteorite_0, unit=" + (i + 1)
                        + "/" + subtitleUnits.size());
            requireSearchResult(search, guide, unit, locale,
                    "ae2ponder.ponder.shared.meteorite_0 subtitle unit " + (i + 1) + "/" + subtitleUnits.size());
        }
        TextWindowCoexistenceProbe.assertSearchModifiers();
    }

    private static void requireSearchResult(GuideSearch search, guideme.Guide guide,
                                           String query, String locale, String source) {
        final boolean found;
        try {
            found = search.searchGuide(query, guide).stream().anyMatch(result -> PAGE.equals(result.pageId()));
        } catch (RuntimeException exception) {
            throw new IllegalStateException("GuideME standard search threw for localized " + source
                    + " text: locale=" + locale + ", source=" + source + ", query=" + query, exception);
        }
        if (!found)
            throw new IllegalStateException("GuideME standard search missed localized " + source
                    + " text: locale=" + locale + ", source=" + source + ", query=" + query);
    }

    private static void verifyIndexedText(guideme.Guide guide, String expected, String locale, String source) {
        final String searchable;
        try {
            var parsed = guide.getParsedPage(PAGE);
            var indexText = GuideSearch.class.getDeclaredMethod("getSearchableText",
                    guideme.Guide.class, guideme.compiler.ParsedGuidePage.class);
            indexText.setAccessible(true);
            searchable = (String) indexText.invoke(null, guide, parsed);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not inspect GuideME indexed text: locale=" + locale
                    + ", source=" + source + ", expected=" + expected, exception);
        }
        if (!searchable.contains(expected))
            throw new IllegalStateException("GuideME indexed text lost localized content: locale=" + locale
                    + ", source=" + source + ", expected=" + expected);
    }

    private static Map<String, String> read(ResourceLocation id) throws Exception {
        var resources = Minecraft.getInstance().getResourceManager();
        var stack = resources.getResourceStack(id);
        if (stack.isEmpty()) throw new IllegalStateException("Missing validation resource: " + id);
        var translations = new HashMap<String, String>();
        var gson = new Gson();
        var type = new TypeToken<Map<String, String>>() {}.getType();
        // Vanilla loads the resource stack from lower to higher priority, so later packs override earlier values.
        for (var resource : stack) {
            try (var reader = new InputStreamReader(resource.open(), StandardCharsets.UTF_8)) {
                var entries = gson.<Map<String, String>>fromJson(reader, type);
                if (entries != null) translations.putAll(entries);
            }
        }
        return translations;
    }

    private static void verifyRightToLeftWrapping() throws Exception {
        var font = Minecraft.getInstance().font;
        var strings = read(GuideResources.parse("ae2ponder:lang/ar_sa.json"));
        for (var entry : strings.entrySet()) {
            if (!GuideCaptionValidation.isCaptionKey(entry.getKey())) continue;
            var units = dev.ae2guide.client.CaptionPages.split(entry.getValue());
            for (int unit = 0; unit < units.size(); unit++) for (int limit : GuideCaptionValidation.CHECKED_WIDTHS) {
                var lines = RightToLeftText.wrap(font, units.get(unit), limit, Style.EMPTY);
                if (lines.size() > dev.ae2guide.client.CaptionPages.MAX_LINES
                        || lines.stream().anyMatch(line -> RightToLeftText.width(font, line.getString()) > limit))
                    throw new IllegalStateException("Shaped caption unit exceeds its window: " + entry.getKey()
                            + ", unit=" + unit + ", width=" + limit);
                String joined = lines.stream().map(line -> line.getString().replaceAll("\\s", ""))
                        .collect(java.util.stream.Collectors.joining());
                if (!joined.equals(units.get(unit).replaceAll("\\s", "")))
                    throw new IllegalStateException("Shaped caption unit lost text: " + entry.getKey() + ", unit=" + unit);
            }
        }
    }

    private static void verifyArabicSearchText() throws Exception {
        var guide = Guides.getById(GUIDE);
        var parsed = guide.getParsedPage(PAGE);
        var indexText = guideme.internal.search.GuideSearch.class.getDeclaredMethod("getSearchableText",
                guideme.Guide.class, guideme.compiler.ParsedGuidePage.class);
        indexText.setAccessible(true);
        String searchable = (String) indexText.invoke(null, guide, parsed);
        for (var lesson : GuideBook.lessons()) {
            String title = Language.getInstance().getOrDefault("ae2ponder.ponder." + lesson.id() + ".header");
            if (!searchable.contains(title))
                throw new IllegalStateException("GuideME search lost logical Arabic text: " + lesson.id());
        }
        String subtitle = GuideCaptionValidation.normalizeForSearch(
                Language.getInstance().getOrDefault("ae2ponder.ponder.shared.meteorite_0"));
        if (!searchable.contains(subtitle))
            throw new IllegalStateException("GuideME search lost logical Arabic subtitle search terms");
    }

    private static void readPage(ResourceLocation id) throws Exception {
        var resources = Minecraft.getInstance().getResourceManager();
        try (var input = resources.getResourceOrThrow(id).open()) {
            String page = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            for (var lesson : GuideBook.allLessons()) {
                if (!page.contains("command=\"/ae2ponder lesson " + lesson.id() + "\""))
                    throw new IllegalStateException("Localized ME Guide missed a lesson link: " + id + "/" + lesson.id());
            }
        }
    }

    private static void screenshot(String name) {
        var client = Minecraft.getInstance();
        Screenshot.grab(client.gameDirectory, name, client.getMainRenderTarget(), message -> {});
    }
}
