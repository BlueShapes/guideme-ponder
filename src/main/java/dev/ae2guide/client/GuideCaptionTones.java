package dev.ae2guide.client;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Locale-specific roles, reviewed against the exact authored caption units at build time. */
public final class GuideCaptionTones {
    private static final String RESOURCE = "/assets/ae2ponder/guide/caption_styles.json";
    private GuideCaptionTones() {}

    private static final class Holder {
        private static final Map<String, Map<String, List<CaptionTone>>> STYLES = load();
    }

    public static List<CaptionTone> forCaption(String lessonId, int stepIndex, List<String> localizedUnits) {
        String selected = Minecraft.getInstance().getLanguageManager().getSelected();
        return forLocale(selected, "ae2ponder.ponder.shared." + lessonId + "_" + stepIndex,
                localizedUnits.size());
    }

    /** Unsupported locales use the same English fallback as the explanatory text. */
    public static List<CaptionTone> forLocale(String locale, String key, int unitCount) {
        var styles = Holder.STYLES.getOrDefault(locale, Holder.STYLES.get("en_us"));
        var tones = styles.get(key);
        if (tones == null || tones.size() != unitCount)
            throw new IllegalStateException("Caption roles do not match authored units: " + locale + "/" + key);
        return tones;
    }

    private static Map<String, Map<String, List<CaptionTone>>> load() {
        try {
            var stream = Objects.requireNonNull(GuideCaptionTones.class.getResourceAsStream(RESOURCE),
                    "Missing caption roles: " + RESOURCE);
            Map<String, Map<String, List<CaptionTone>>> parsed;
            try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                parsed = new Gson().fromJson(reader,
                        new TypeToken<Map<String, Map<String, List<CaptionTone>>>>() {}.getType());
            }
            var result = new HashMap<String, Map<String, List<CaptionTone>>>();
            Objects.requireNonNull(parsed, "Caption roles").forEach((locale, captions) -> {
                var entries = new HashMap<String, List<CaptionTone>>();
                captions.forEach((key, tones) -> entries.put(key, List.copyOf(tones)));
                result.put(locale, Map.copyOf(entries));
            });
            if (!result.containsKey("en_us")) throw new IllegalStateException("English caption roles are required");
            return Map.copyOf(result);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to load AE2 caption roles", exception);
        }
    }
}
