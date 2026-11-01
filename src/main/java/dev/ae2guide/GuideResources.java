package dev.ae2guide;

import net.minecraft.resources.ResourceLocation;

/** Resource location helpers shared by the supported Minecraft versions. */
public final class GuideResources {
    private GuideResources() {}

    public static ResourceLocation parse(String value) {
        var location = ResourceLocation.tryParse(value);
        if (location == null) throw new IllegalArgumentException("Invalid resource location: " + value);
        return location;
    }

    public static ResourceLocation of(String namespace, String path) {
        return parse(namespace + ":" + path);
    }
}
