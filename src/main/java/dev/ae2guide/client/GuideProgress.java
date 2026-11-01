package dev.ae2guide.client;

import com.google.gson.Gson;
import com.mojang.logging.LogUtils;
import dev.ae2guide.Platform;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;

final class GuideProgress {
    private static final java.nio.file.Path FILE = Platform.configDirectory().resolve("ae2ponder-progress.json");
    private static final Set<String> COMPLETED = load();
    private GuideProgress() {}

    private static Set<String> load() {
        if (!Files.isRegularFile(FILE)) return new HashSet<>();
        try (var reader = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
            var ids = new Gson().fromJson(reader, String[].class);
            return ids == null ? new HashSet<>() : new HashSet<>(java.util.Arrays.asList(ids));
        } catch (Exception exception) {
            LogUtils.getLogger().warn("Could not read AE2 guide progress: {}", FILE, exception);
            return new HashSet<>();
        }
    }

    static boolean completed(String id) { return COMPLETED.contains(id); }

    static void complete(String id) {
        if (!COMPLETED.add(id)) return;
        try {
            Files.createDirectories(FILE.getParent());
            var pending = FILE.resolveSibling("ae2ponder-progress.json.tmp");
            Files.writeString(pending, new Gson().toJson(COMPLETED.stream().sorted().toList()), StandardCharsets.UTF_8);
            Files.move(pending, FILE, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception exception) {
            LogUtils.getLogger().warn("Could not save AE2 guide progress: {}", FILE, exception);
        }
    }
}
