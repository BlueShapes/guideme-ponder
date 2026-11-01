package dev.ae2guide.validation;

import net.createmod.ponder.foundation.element.TextWindowElement;
import net.createmod.ponder.foundation.PonderScene;
import net.minecraft.client.gui.Font;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/** Runtime assertions shared by the validation-only same-invocation Mixin. */
public final class TextWindowCoexistenceProbe {
    private static boolean splitLinesCalled;
    private static boolean lastArgumentsValid;
    private static int splitLinesCalls;
    private static int searchQueryCalls;
    private static int captionXModifierCalls;
    private static boolean renderedGuideTextWindow;
    private static boolean primaryMixinObserved;
    private static boolean rtlMixinObserved;
    private static String reflectionFailure;
    private static String lastWindowText;
    private static int lastWindowWidth;
    private static int lastWindowLines;
    private static final List<RenderedCaption> renderedThisFrame = new ArrayList<>();
    private static CaptionFrame latestCaptionFrame;

    private TextWindowCoexistenceProbe() {}

    public static void reset() {
        splitLinesCalled = false;
        lastArgumentsValid = false;
        splitLinesCalls = 0;
        searchQueryCalls = 0;
        captionXModifierCalls = 0;
        renderedGuideTextWindow = false;
        primaryMixinObserved = false;
        rtlMixinObserved = false;
        reflectionFailure = null;
        lastWindowText = null;
        lastWindowWidth = 0;
        lastWindowLines = 0;
        renderedThisFrame.clear();
        latestCaptionFrame = null;
    }

    public static int splitLinesCalls() {
        return splitLinesCalls;
    }

    public static void recordSearchQuery() { searchQueryCalls++; }

    public static void recordCaptionXModifier(String namespace) {
        if ("ae2ponder".equals(namespace)) captionXModifierCalls++;
    }

    public static int captionXModifierCalls() { return captionXModifierCalls; }

    public static void assertCaptionXModifierObserved() {
        if (captionXModifierCalls == 0)
            throw new IllegalStateException("Validation caption X modifier did not observe Ponder's computed position");
    }

    public static void assertSearchModifiers() {
        if (searchQueryCalls == 0)
            throw new IllegalStateException("Validation search modifier did not observe native query parsing");
    }

    public static void recordSplitLinesArguments(int argumentCount, Object text, Object width, Object style) {
        splitLinesCalled = true;
        splitLinesCalls++;
        lastArgumentsValid = argumentCount == 3 && text instanceof String
                && width instanceof Integer && (style == null || style instanceof Style);
        lastWindowText = text instanceof String value ? value : null;
        lastWindowWidth = width instanceof Integer value ? value : 0;
    }

    public static void recordRenderedWindow(Object target, boolean guideNamespace, boolean calledDuringRender,
                                            String renderedText, int width, float x) {
        if (!guideNamespace || !calledDuringRender) return;
        renderedGuideTextWindow = true;
        try {
            boolean rightToLeft = booleanField(target, "ae2ponder$rightToLeft");
            Font font = (Font) field(target, "ae2ponder$font");
            int lineCount = (int) field(target, "ae2ponder$lineCount");
            lastWindowLines = lineCount;
            primaryMixinObserved |= font != null && lineCount > 0;
            if (font != null && lineCount > 0 && renderedText != null)
                renderedThisFrame.add(new RenderedCaption(renderedText, width, lineCount,
                        System.identityHashCode(target), ((TextWindowElement) target).getFade(1), x));
            if (Language.getInstance().isDefaultRightToLeft() && rightToLeft && font != null && lineCount > 0) {
                Object wrappedLines = field(target, "ae2ponder$wrappedLines");
                rtlMixinObserved |= wrappedLines instanceof List<?> lines && !lines.isEmpty()
                        && lines.stream().allMatch(FormattedText.class::isInstance);
            }
        } catch (ReflectiveOperationException | ClassCastException exception) {
            reflectionFailure = exception.toString();
        }
    }

    public static void resetCaptionObservation() {
        renderedThisFrame.clear();
        latestCaptionFrame = null;
        lastWindowText = null;
        lastWindowWidth = 0;
        lastWindowLines = 0;
        captionXModifierCalls = 0;
    }

    public static RenderedCaption renderedCaption(String expectedText) {
        if (latestCaptionFrame == null) return null;
        return latestCaptionFrame.captions().stream()
                .filter(caption -> caption.text().equals(expectedText)).findFirst().orElse(null);
    }

    public static List<RenderedCaption> currentCaptionFrame() {
        return latestCaptionFrame == null ? List.of() : latestCaptionFrame.captions();
    }

    public static int currentCaptionFrameTime() {
        return latestCaptionFrame == null ? -1 : latestCaptionFrame.sceneTime();
    }

    public static void recordCaptionFrame(PonderScene scene) {
        if (scene == null) {
            latestCaptionFrame = null;
            renderedThisFrame.clear();
            return;
        }
        var frameCaptions = new ArrayList<RenderedCaption>();
        for (var visible : GuideCaptionTimelineValidation.visibleCaptions(scene)) {
            var rendered = renderedThisFrame.stream()
                    .filter(caption -> caption.identity() == visible.identity())
                    .findFirst().orElse(new RenderedCaption(visible.text(), 0, 0, visible.identity(),
                            visible.fade(), Float.NaN));
            frameCaptions.add(new RenderedCaption(visible.text(), rendered.width(), rendered.lineCount(),
                    visible.identity(), visible.fade(), rendered.x()));
        }
        latestCaptionFrame = new CaptionFrame(scene.getCurrentTime(), List.copyOf(frameCaptions));
        renderedThisFrame.clear();
    }

    public record RenderedCaption(String text, int width, int lineCount, int identity, float fade, float x) {}
    private record CaptionFrame(int sceneTime, List<RenderedCaption> captions) {}

    public static void assertPrimaryMixinAndProbe() {
        if (reflectionFailure != null)
            throw new IllegalStateException("Could not inspect the primary text-window correction: " + reflectionFailure);
        if (!splitLinesCalled || splitLinesCalls == 0 || !lastArgumentsValid)
            throw new IllegalStateException("Validation splitLines modifier did not observe the original invocation; calls="
                    + splitLinesCalls + ", lastArgumentsValid=" + lastArgumentsValid);
        if (!renderedGuideTextWindow || !primaryMixinObserved)
            throw new IllegalStateException("Primary text-window correction was not observed during Guide rendering");
    }

    public static void assertRtlMixinAndProbe() {
        assertPrimaryMixinAndProbe();
        if (!rtlMixinObserved)
            throw new IllegalStateException("Primary RTL text-window correction did not produce shaped wrapped lines");
    }

    private static boolean booleanField(Object target, String name) throws ReflectiveOperationException {
        return (boolean) field(target, name);
    }

    private static Object field(Object target, String name) throws ReflectiveOperationException {
        Field field = TextWindowElement.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
}
