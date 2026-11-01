package dev.ae2guide;

import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

/** Loader services used from the common guide implementation. */
public final class Platform {
    private Platform() {}

    public static boolean isModLoaded(String modId) { return ModList.get().isLoaded(modId); }

    public static Set<String> loadedModIds() {
        return ModList.get().getMods().stream().map(mod -> mod.getModId()).collect(Collectors.toUnmodifiableSet());
    }

    public static Path configDirectory() { return FMLPaths.CONFIGDIR.get(); }
}
