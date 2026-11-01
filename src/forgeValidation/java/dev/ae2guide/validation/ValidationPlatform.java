package dev.ae2guide.validation;

import net.createmod.ponder.api.level.PonderLevel;
import net.createmod.ponder.enums.PonderKeybinds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.OptionsScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.client.model.data.ModelData;
import org.lwjgl.glfw.GLFW;

/** Minecraft-version-specific setup used only by the opt-in validation harness. */
final class ValidationPlatform {
    private ValidationPlatform() {}

    static void hideWindow(Minecraft client) {
        client.options.onboardAccessibility = false;
        GLFW.glfwHideWindow(client.getWindow().getWindow());
    }

    static void recoverInitialScreen(Minecraft client) {
        if (client.screen instanceof AccessibilityOnboardingScreen) {
            client.setScreen(new TitleScreen());
        }
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
        // Minecraft 1.20.1 has no spawn-chunk-radius game rule.
        client.createWorldOpenFlows().createFreshLevel("guide-validation-" + System.currentTimeMillis(),
                new LevelSettings("AE2 Guide Validation", GameType.CREATIVE, false,
                        Difficulty.PEACEFUL, true, rules, WorldDataConfiguration.DEFAULT),
                new WorldOptions(1L, false, false), registries -> registries.registryOrThrow(Registries.WORLD_PRESET)
                        .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
    }

    static CompoundTag saveWithoutMetadata(BlockEntity blockEntity, RegistryAccess registryAccess) {
        return blockEntity.saveWithoutMetadata();
    }

    static void loadWorld(Minecraft client, String name) {
        client.createWorldOpenFlows().loadLevel(client.screen, name);
    }

    static double averageTickMillis(Minecraft client) {
        return client.getSingleplayerServer().getAverageTickTime();
    }

    static ModelData getModelData(PonderLevel level, BlockPos position) {
        // Match Forge Ponder's BakedModelBuffererImpl.bufferBlocks model-data provider.
        var blockEntity = level.getBlockEntity(position);
        return blockEntity == null ? ModelData.EMPTY : blockEntity.getModelData();
    }

    static boolean isPonderKeyDown() {
        return net.createmod.catnip.platform.CatnipClientServices.CLIENT_HOOKS
                .isKeyPressed(PonderKeybinds.PONDER.getKeybind());
    }

    static void setPonderKeyDown(boolean down) {
        PonderKeybinds.PONDER.getKeybind().setDown(down);
        PonderKeyInput.setDown(PonderKeybinds.PONDER.getKeybind().getKey().getValue(), down);
    }
}
