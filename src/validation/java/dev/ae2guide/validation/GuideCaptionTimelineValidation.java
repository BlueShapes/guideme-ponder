package dev.ae2guide.validation;

import dev.ae2guide.client.CaptionPages;
import dev.ae2guide.client.CaptionTone;
import dev.ae2guide.client.GuideBook;
import dev.ae2guide.client.GuideCaptionTones;
import dev.ae2guide.client.GuideScenes;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.element.TextWindowElement;
import net.createmod.ponder.foundation.ui.PonderUI;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;

/** Verifies caption visibility against Ponder's live, seekable scene timeline. */
final class GuideCaptionTimelineValidation {
    private static final float RENDER_FADE_THRESHOLD = 0.0625f;
    private static final float FULL_OPACITY = 0.999f;
    private static final Field TEXT_GETTER = textGetterField();

    private GuideCaptionTimelineValidation() {}

    static List<String> verifyMixedTonePaletteSequence(PonderUI ui, GuideBook.Lesson lesson, int stepIndex) {
        var units = CaptionPages.split(Component.translatable(
                "ae2ponder.ponder.shared." + lesson.id() + "_" + stepIndex).getString());
        var tones = GuideCaptionTones.forCaption(lesson.id(), stepIndex, units);
        var expected = List.of(CaptionTone.WARNING, CaptionTone.NORMAL, CaptionTone.HINT);
        if (!tones.equals(expected))
            throw new IllegalStateException("Mixed caption-tone fixture changed: " + lesson.id() + " step=" + stepIndex
                    + " expected=" + expected + ", actual=" + tones);

        var budgets = GuideScenes.captionUnitDurations(lesson, stepIndex);
        int time = ui.getActiveScene().getKeyframeTime(stepIndex) + 12;
        var rendered = new ArrayList<String>(units.size());
        for (int unit = 0; unit < units.size(); unit++) {
            int midpoint = time + budgets.get(unit) / 2;
            ui.seekToTime(midpoint);
            var visible = visibleCaptions(ui.getActiveScene()).stream()
                    .filter(caption -> units.contains(caption.text())).toList();
            if (visible.size() != 1 || !visible.get(0).text().equals(units.get(unit)))
                throw new IllegalStateException("Expected one mixed-tone caption at its midpoint: " + lesson.id()
                        + " step=" + stepIndex + " unit=" + unit + " visible=" + visible);
            var actualPalette = visible.get(0).element().getPalette();
            if (actualPalette != tones.get(unit).palette())
                throw new IllegalStateException("Native text window palette does not match its authored role: "
                        + lesson.id() + " step=" + stepIndex + " unit=" + unit + " tone=" + tones.get(unit)
                        + " expected=" + tones.get(unit).palette() + ", actual=" + actualPalette);
            rendered.add(actualPalette.name());
            time += budgets.get(unit) + CaptionPages.PAGE_GAP_TICKS;
        }
        return List.copyOf(rendered);
    }

    static void seekAndVerifyMixedToneUnit(PonderUI ui, GuideBook.Lesson lesson, int stepIndex, int unitIndex) {
        var units = CaptionPages.split(Component.translatable(
                "ae2ponder.ponder.shared." + lesson.id() + "_" + stepIndex).getString());
        var tones = GuideCaptionTones.forCaption(lesson.id(), stepIndex, units);
        var budgets = GuideScenes.captionUnitDurations(lesson, stepIndex);
        if (unitIndex < 0 || unitIndex >= units.size() || units.size() != budgets.size())
            throw new IllegalArgumentException("Invalid mixed-tone screenshot unit: " + lesson.id() + "/" + stepIndex
                    + "/" + unitIndex);
        int time = ui.getActiveScene().getKeyframeTime(stepIndex) + 12;
        for (int index = 0; index < unitIndex; index++) time += budgets.get(index) + CaptionPages.PAGE_GAP_TICKS;
        ui.seekToTime(time + budgets.get(unitIndex) / 2);
        var visible = visibleCaptions(ui.getActiveScene()).stream()
                .filter(caption -> caption.text().equals(units.get(unitIndex))).toList();
        if (visible.size() != 1 || visible.get(0).element().getPalette() != tones.get(unitIndex).palette())
            throw new IllegalStateException("Mixed-tone screenshot target is not the expected native caption: "
                    + lesson.id() + " step=" + stepIndex + " unit=" + unitIndex + " tone=" + tones.get(unitIndex)
                    + " visible=" + visible);
    }

    static List<VisibleCaption> visibleCaptions(PonderScene scene) {
        var visible = new ArrayList<VisibleCaption>();
        for (var element : scene.getElements()) {
            if (!(element instanceof TextWindowElement window) || !window.isVisible()) continue;
            float fade = window.getFade(1);
            // TextWindowElement.render returns below this threshold, so these windows produce no pixels.
            if (fade < RENDER_FADE_THRESHOLD) continue;
            visible.add(new VisibleCaption(window, text(window), fade));
        }
        return List.copyOf(visible);
    }

    static Map<String, Object> verify(PonderUI ui, String locale, GuideBook.Lesson lesson,
                                      int stepIndex, List<String> units, int restoreUnitIndex) {
        if (units.size() < 2) throw new IllegalArgumentException("A timeline check needs at least two caption units");
        if (restoreUnitIndex < 0 || restoreUnitIndex >= units.size())
            throw new IllegalArgumentException("Invalid caption unit to restore: " + restoreUnitIndex);

        PonderScene scene = ui.getActiveScene();
        List<Integer> budgets = GuideScenes.captionUnitDurations(lesson, stepIndex);
        if (budgets.size() != units.size())
            throw new IllegalStateException("Caption timing budget does not match its authored units: " + lesson.id());
        if (budgets.stream().anyMatch(ticks -> ticks < CaptionPages.MIN_READ_TICKS_PER_PAGE))
            throw new IllegalStateException("Caption unit has less than the required read budget: " + lesson.id());

        int stepStart = scene.getKeyframeTime(stepIndex) + 12;
        var unitStarts = new ArrayList<Integer>(units.size());
        int nextStart = stepStart;
        for (int budget : budgets) {
            unitStarts.add(nextStart);
            nextStart += budget + CaptionPages.PAGE_GAP_TICKS;
        }
        int stepEnd = stepIndex + 1 < scene.getKeyframeCount()
                ? scene.getKeyframeTime(stepIndex + 1) : scene.getTotalTime();
        if (stepEnd <= stepStart) throw new IllegalStateException("Caption step has an empty Ponder timeline");

        var firstVisible = new int[units.size()];
        var lastVisible = new int[units.size()];
        var windows = new TextWindowElement[units.size()];
        var visibleTicks = new int[units.size()];
        var longestOpaqueRun = new int[units.size()];
        var currentOpaqueRun = new int[units.size()];
        var midpointChecked = new boolean[units.size()];
        java.util.Arrays.fill(firstVisible, -1);
        java.util.Arrays.fill(lastVisible, -1);

        // PonderUI.seekToTime performs its own replay when this target precedes the current scene time.
        ui.seekToTime(stepStart);
        requireTime(scene, stepStart);
        for (int time = stepStart; time <= stepEnd; time++) {
            ui.seekToTime(time);
            requireTime(scene, time);
            List<VisibleCaption> visible = visibleCaptions(scene);
            var authored = visible.stream().filter(caption -> units.contains(caption.text())).toList();
            if (authored.size() > 1)
                throw new IllegalStateException("Multiple caption units render together: " + lesson.id()
                        + " step=" + stepIndex + " tick=" + time + " text="
                        + authored.stream().map(VisibleCaption::text).toList());

            int midpointUnit = midpointUnitAt(time, unitStarts, budgets);
            if (authored.isEmpty()) {
                if (midpointUnit >= 0)
                    throw new IllegalStateException("Caption is absent at its visible midpoint: " + lesson.id()
                            + " step=" + stepIndex + " unit=" + midpointUnit + " tick=" + time);
                for (int unit = 0; unit < units.size(); unit++) currentOpaqueRun[unit] = 0;
            } else {
                VisibleCaption caption = authored.get(0);
                int unit = scheduledUnitAt(time, unitStarts);
                if (unit < 0 || !caption.text().equals(units.get(unit)))
                    throw new IllegalStateException("Ponder rendered caption units out of order: " + lesson.id()
                            + " step=" + stepIndex + " tick=" + time + " expectedUnit=" + unit
                            + " actualText=" + caption.text());
                if (windows[unit] == null) windows[unit] = caption.element();
                else if (windows[unit] != caption.element())
                    throw new IllegalStateException("Caption unit changed native text-window element mid-display: "
                            + lesson.id() + " step=" + stepIndex + " unit=" + unit + " tick=" + time);
                for (int earlier = 0; earlier < unit; earlier++)
                    if (windows[earlier] == caption.element())
                        throw new IllegalStateException("Caption units reused one native text-window element: "
                                + lesson.id() + " step=" + stepIndex + " units=" + earlier + "/" + unit);
                if (firstVisible[unit] < 0) firstVisible[unit] = time;
                lastVisible[unit] = time;
                visibleTicks[unit]++;
                if (caption.fade() >= FULL_OPACITY) {
                    currentOpaqueRun[unit]++;
                    longestOpaqueRun[unit] = Math.max(longestOpaqueRun[unit], currentOpaqueRun[unit]);
                } else {
                    currentOpaqueRun[unit] = 0;
                }
                for (int other = 0; other < units.size(); other++)
                    if (other != unit) currentOpaqueRun[other] = 0;

                if (midpointUnit == unit) {
                    if (caption.fade() < FULL_OPACITY)
                        throw new IllegalStateException("Caption midpoint is still fading: " + lesson.id()
                                + " step=" + stepIndex + " unit=" + unit + " fade=" + caption.fade());
                    midpointChecked[unit] = true;
                }
            }

            // Ponder's TextInstruction adds five fade-in and five fade-out ticks to showText(duration).
            // The 12-tick authored gap therefore contains two completely hidden isVisible() ticks.
            for (int unit = 0; unit < units.size() - 1; unit++) {
                int firstEmptyTick = unitStarts.get(unit) + budgets.get(unit) + 10;
                int secondEmptyTick = firstEmptyTick + 1;
                if (time == firstEmptyTick || time == secondEmptyTick) {
                    if (!authored.isEmpty())
                        throw new IllegalStateException("Caption window remained visible in its gap: " + lesson.id()
                                + " step=" + stepIndex + " unit=" + unit + " tick=" + time);
                }
            }
        }

        var gapTicks = new ArrayList<Integer>(Math.max(0, units.size() - 1));
        for (int unit = 0; unit < units.size(); unit++) {
            if (firstVisible[unit] < 0 || visibleTicks[unit] == 0)
                throw new IllegalStateException("Ponder did not display an authored caption unit: " + lesson.id()
                        + " step=" + stepIndex + " unit=" + unit);
            if (!midpointChecked[unit])
                throw new IllegalStateException("Ponder did not show a fully visible caption midpoint: " + lesson.id()
                        + " step=" + stepIndex + " unit=" + unit);
            if (longestOpaqueRun[unit] < CaptionPages.MIN_READ_TICKS_PER_PAGE)
                throw new IllegalStateException("Ponder full-opacity reading time is below the minimum: " + lesson.id()
                        + " step=" + stepIndex + " unit=" + unit + " ticks=" + longestOpaqueRun[unit]);
            if (unit + 1 < units.size()) {
                int emptyTicks = firstVisible[unit + 1] - lastVisible[unit] - 1;
                if (emptyTicks < 1)
                    throw new IllegalStateException("Caption units overlap without an empty rendered frame: " + lesson.id()
                            + " step=" + stepIndex + " afterUnit=" + unit + " gap=" + emptyTicks);
                gapTicks.add(emptyTicks);
            }
        }

        int firstMidpoint = midpoint(unitStarts.get(0), budgets.get(0));
        int lastUnit = units.size() - 1;
        int lastMidpoint = midpoint(unitStarts.get(lastUnit), budgets.get(lastUnit));
        ui.seekToTime(firstMidpoint);
        requireOnlyCaption(scene, units, 0, firstMidpoint, lesson.id(), stepIndex, "rewind");
        ui.seekToTime(lastMidpoint);
        requireOnlyCaption(scene, units, lastUnit, lastMidpoint, lesson.id(), stepIndex, "replay");

        int cleanupTick = unitStarts.get(lastUnit) + budgets.get(lastUnit) + 11;
        ui.seekToTime(cleanupTick);
        if (!visibleCaptions(scene).stream().noneMatch(caption -> units.contains(caption.text())))
            throw new IllegalStateException("The final caption window did not clean up: " + lesson.id()
                    + " step=" + stepIndex + " tick=" + cleanupTick);
        int restoreTime = midpoint(unitStarts.get(restoreUnitIndex), budgets.get(restoreUnitIndex));
        ui.seekToTime(restoreTime);
        requireOnlyCaption(scene, units, restoreUnitIndex, restoreTime, lesson.id(), stepIndex, "restore");

        var result = new LinkedHashMap<String, Object>();
        result.put("locale", locale);
        result.put("lesson", lesson.id());
        result.put("step", stepIndex);
        result.put("unitCount", units.size());
        result.put("readBudgetTicks", List.copyOf(budgets));
        result.put("fullOpacityTicks", toList(longestOpaqueRun));
        result.put("visibleTicks", toList(visibleTicks));
        result.put("windowIdentities", java.util.Arrays.stream(windows)
                .map(window -> System.identityHashCode(window)).toList());
        result.put("emptyGapTicks", List.copyOf(gapTicks));
        result.put("midpoints", midpointChecked.length);
        result.put("rewindVerified", true);
        result.put("replayVerified", true);
        result.put("finalWindowCleanup", true);
        return Map.copyOf(result);
    }

    private static int midpointUnitAt(int time, List<Integer> starts, List<Integer> budgets) {
        for (int unit = 0; unit < starts.size(); unit++)
            if (time == midpoint(starts.get(unit), budgets.get(unit))) return unit;
        return -1;
    }

    private static int scheduledUnitAt(int time, List<Integer> starts) {
        for (int unit = starts.size() - 1; unit >= 0; unit--)
            if (time >= starts.get(unit)) return unit;
        return -1;
    }

    private static int midpoint(int start, int duration) {
        return start + duration / 2;
    }

    private static void requireOnlyCaption(PonderScene scene, List<String> units, int expectedUnit, int time,
                                           String lesson, int step, String phase) {
        var visible = visibleCaptions(scene).stream().filter(caption -> units.contains(caption.text())).toList();
        if (visible.size() != 1 || !visible.get(0).text().equals(units.get(expectedUnit))
                || visible.get(0).fade() < FULL_OPACITY)
            throw new IllegalStateException("Ponder " + phase + " did not restore exactly one expected caption: "
                    + lesson + " step=" + step + " unit=" + expectedUnit + " tick=" + time + " visible=" + visible);
    }

    private static List<Integer> toList(int[] values) {
        var list = new ArrayList<Integer>(values.length);
        for (int value : values) list.add(value);
        return List.copyOf(list);
    }

    private static void requireTime(PonderScene scene, int expected) {
        if (scene.getCurrentTime() != expected)
            throw new IllegalStateException("Ponder seek stopped at the wrong timeline tick: expected=" + expected
                    + ", actual=" + scene.getCurrentTime());
    }

    private static String text(TextWindowElement window) {
        try {
            Object getter = TEXT_GETTER.get(window);
            if (!(getter instanceof Supplier<?> supplier))
                throw new IllegalStateException("Text window has no localized text supplier");
            Object value = supplier.get();
            return value instanceof String text ? text : "";
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Cannot inspect the native text-window content", exception);
        }
    }

    private static Field textGetterField() {
        try {
            Field field = TextWindowElement.class.getDeclaredField("textGetter");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    record VisibleCaption(TextWindowElement element, String text, float fade) {
        int identity() { return System.identityHashCode(element); }
    }
}
