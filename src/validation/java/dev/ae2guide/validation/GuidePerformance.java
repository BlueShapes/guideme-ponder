package dev.ae2guide.validation;

import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import jdk.jfr.Configuration;
import jdk.jfr.Recording;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Opt-in normal-world profiling; only opens a world copied into this isolated run directory. */
public final class GuidePerformance {
    private static boolean opening;
    private static boolean waitingScreenLogged;
    private static long openingStarted;
    private static long started;
    private static long previousFrame;
    private static long renderStarted;
    private static Recording recording;
    private static final List<Double> FRAMES = new ArrayList<>();
    private static final List<Double> RENDERS = new ArrayList<>();
    private static final List<Double> TICKS = new ArrayList<>();

    private GuidePerformance() {}

    static boolean initialize() {
        if (!Boolean.getBoolean("ae2ponder.profile")) return false;
        var client = Minecraft.getInstance();
        ValidationPlatform.hideWindow(client);
        try { Files.deleteIfExists(client.gameDirectory.toPath().resolve("performance-result.json")); }
        catch (Exception exception) { throw new IllegalStateException("Cannot clear old performance result", exception); }
        LogUtils.getLogger().info("AE2_GUIDE_PROFILE_INITIALIZED: current screen={}", screenName(client));
        return true;
    }

    static void tick() {
        var client = Minecraft.getInstance();
        if (!opening && client.getOverlay() == null && !(client.screen instanceof TitleScreen)) {
            if (!waitingScreenLogged) {
                waitingScreenLogged = true;
                LogUtils.getLogger().info("AE2_GUIDE_PROFILE_WAITING: current screen={}", screenName(client));
            }
            ValidationPlatform.recoverInitialScreen(client);
            return;
        }
        if (!opening && client.screen instanceof TitleScreen && client.getOverlay() == null) {
            opening = true;
            openingStarted = System.nanoTime();
            client.options.pauseOnLostFocus = false;
            ValidationPlatform.loadWorld(client, "profile-world");
        }
        if (opening && started == 0 && System.nanoTime() - openingStarted > 150_000_000_000L) {
            LogUtils.getLogger().error("AE2_GUIDE_PROFILE_FAIL: copied world did not open within 150 seconds");
            client.stop();
            return;
        }
        if (client.level == null || client.player == null || client.screen != null || client.getOverlay() != null) return;
        try {
            if (started == 0) {
                started = System.nanoTime();
                recording = new Recording(Configuration.getConfiguration("profile"));
                recording.start();
                LogUtils.getLogger().info("AE2_GUIDE_PROFILE_START: render distance {}, simulation distance {}, resolution {}x{}",
                        client.options.renderDistance().get(), client.options.simulationDistance().get(),
                        client.getWindow().getWidth(), client.getWindow().getHeight());
            }
            double seconds = (System.nanoTime() - started) / 1e9;
            // Sweep the camera to exercise terrain rendering, without editing or moving through the world.
            client.player.setYRot((float) (seconds * 18));
            if (seconds >= 15) TICKS.add(ValidationPlatform.averageTickMillis(client));
            if (seconds >= 45) {
                recording.stop();
                recording.dump(client.gameDirectory.toPath().resolve("performance.jfr"));
                recording.close();
                var result = new LinkedHashMap<String, Object>();
                result.put("frames", statistics(FRAMES));
                result.put("render", statistics(RENDERS));
                result.put("serverTick", statistics(TICKS));
                result.put("renderDistance", client.options.renderDistance().get());
                result.put("simulationDistance", client.options.simulationDistance().get());
                result.put("resolution", client.getWindow().getWidth() + "x" + client.getWindow().getHeight());
                result.put("javaVersion", System.getProperty("java.version"));
                result.put("javaVendor", System.getProperty("java.vendor"));
                result.put("vsync", client.options.enableVsync().get());
                result.put("fpsLimit", client.options.framerateLimit().get());
                Files.writeString(client.gameDirectory.toPath().resolve("performance-result.json"),
                        new GsonBuilder().setPrettyPrinting().create().toJson(result));
                LogUtils.getLogger().info("AE2_GUIDE_PROFILE_PASS: {}", result);
                client.stop();
            }
        } catch (Exception exception) {
            LogUtils.getLogger().error("AE2_GUIDE_PROFILE_FAIL", exception);
            client.stop();
        }
    }

    static void beforeFrame() {
        long now = System.nanoTime();
        if (sampling() && previousFrame != 0) FRAMES.add((now - previousFrame) / 1e6);
        previousFrame = now;
        renderStarted = now;
    }

    static void afterFrame() {
        if (sampling()) RENDERS.add((System.nanoTime() - renderStarted) / 1e6);
    }

    private static boolean sampling() {
        return started != 0 && System.nanoTime() - started >= 15_000_000_000L;
    }

    private static Map<String, Object> statistics(List<Double> samples) {
        var sorted = samples.stream().sorted().toList();
        if (sorted.isEmpty()) return Map.of("count", 0);
        return Map.of("count", sorted.size(), "meanMs", sorted.stream().mapToDouble(Double::doubleValue).average().orElseThrow(),
                "medianMs", sorted.get(sorted.size() / 2), "p95Ms", sorted.get((int) ((sorted.size() - 1) * .95)),
                "maxMs", sorted.get(sorted.size() - 1));
    }

    private static String screenName(Minecraft client) {
        return client.screen == null ? "<none>" : client.screen.getClass().getSimpleName();
    }
}
