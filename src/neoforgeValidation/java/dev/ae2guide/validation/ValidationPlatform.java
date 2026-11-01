package dev.ae2guide.validation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.createmod.ponder.api.level.PonderLevel;
import net.createmod.ponder.enums.PonderKeybinds;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.lwjgl.glfw.GLFW;

import net.minecraft.client.KeyMapping;

/** Minecraft-version-specific setup used only by the opt-in validation harness. */
final class ValidationPlatform {
    private ValidationPlatform() {}

    static void hideWindow(Minecraft client) {
        GLFW.glfwHideWindow(client.getWindow().getWindow());
    }

    static void recoverInitialScreen(Minecraft client) {
        // NeoForge's startup screen flow opens the title screen directly.
    }

    static Screen createOptionsScreen(Minecraft client) {
        return new OptionsScreen(null, client.options);
    }

    static boolean isOptionsScreen(Screen screen) {
        return screen instanceof OptionsScreen;
    }

    static void createFreshLevel(Minecraft client) {
        var rules = new GameRules();
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
        rules.getRule(GameRules.RULE_SPAWN_CHUNK_RADIUS).set(0, null);
        client.createWorldOpenFlows().createFreshLevel("guide-validation-" + System.currentTimeMillis(),
                new LevelSettings("AE2 Guide Validation", GameType.CREATIVE, false,
                        Difficulty.PEACEFUL, true, rules, WorldDataConfiguration.DEFAULT),
                new WorldOptions(1L, false, false), registries -> registries.registryOrThrow(Registries.WORLD_PRESET)
                        .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(), client.screen);
    }

    static CompoundTag saveWithoutMetadata(BlockEntity blockEntity, RegistryAccess registryAccess) {
        return blockEntity.saveWithoutMetadata(registryAccess);
    }

    static void loadWorld(Minecraft client, String name) {
        client.createWorldOpenFlows().openWorld(name, client::stop);
    }

    static double averageTickMillis(Minecraft client) {
        return client.getSingleplayerServer().getAverageTickTimeNanos() / 1e6;
    }

    static ModelData getModelData(PonderLevel level, BlockPos position) {
        return level.getModelData(position);
    }

    static boolean isPonderKeyDown() { return PonderKeybinds.PONDER.isDown(); }

    static void setPonderKeyDown(boolean down) {
        try {
            var field = PonderKeybinds.class.getDeclaredField("mapping");
            field.setAccessible(true);
            var mapping = (KeyMapping) field.get(PonderKeybinds.PONDER);
            mapping.setDown(down);
            PonderKeyInput.setDown(mapping.getKey().getValue(), down);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot update Ponder's native key mapping", exception);
        }
    }
}
