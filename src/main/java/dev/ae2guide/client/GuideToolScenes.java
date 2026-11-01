package dev.ae2guide.client;

import net.createmod.ponder.api.ParticleEmitter;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.EntityElement;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.phys.Vec3;

/** Small, replay-local visual helpers for the charged staff lesson. */
final class GuideToolScenes {
    private ElementLink<EntityElement> target;

    void apply(GuideBook.Lesson lesson, String effect, SceneBuilder scene) {
        switch (effect) {
            case "none" -> { }
            case "staff_target" -> showTarget(lesson, scene);
            case "staff_charge" -> showCharge(lesson, scene);
            case "staff_hit" -> showHit(lesson, scene, true);
            case "staff_empty_hit" -> showHit(lesson, scene, false);
            default -> throw new IllegalArgumentException("Unsupported guide step effect: " + effect);
        }
    }

    private void showTarget(GuideBook.Lesson lesson, SceneBuilder scene) {
        var position = lesson.node("target").position();
        target = scene.world().createEntity(level -> {
            Husk husk = EntityType.HUSK.create(level);
            if (husk == null) throw new IllegalStateException("Unable to create charged staff target");
            husk.moveTo(position.getX() + 0.5, position.getY() + 1, position.getZ() + 0.5, 0, 0);
            husk.setNoAi(true);
            husk.setNoGravity(true);
            husk.setSilent(true);
            husk.setPersistenceRequired();
            return husk;
        });
    }

    private static void showCharge(GuideBook.Lesson lesson, SceneBuilder scene) {
        var power = lesson.node("power").position();
        var charger = lesson.node("charger").position();
        var spark = scene.effects().simpleParticleEmitter(ParticleTypes.ELECTRIC_SPARK, new Vec3(0, 0.08, 0));
        emitAtBlock(scene, power, 0.5f, 5, spark);
        emitAtBlock(scene, charger, 0.65f, 10, spark);
    }

    private void showHit(GuideBook.Lesson lesson, SceneBuilder scene, boolean charged) {
        if (target == null) throw new IllegalStateException("Charged staff hit effect has no target");
        scene.world().modifyEntity(target, entity -> {
            if (entity instanceof LivingEntity living) {
                // The ordinary hurt animation sets the renderer's red flash without changing health.
                living.animateHurt(0);
                living.setDeltaMovement(Vec3.ZERO);
            }
        });

        var location = entityPoint(lesson.node("target").position(), 1.3);
        if (charged) {
            var spark = scene.effects().simpleParticleEmitter(ParticleTypes.ELECTRIC_SPARK, new Vec3(0, 0.1, 0));
            scene.effects().emitParticles(location, spark, 0.55f, 12);
        } else {
            var hit = scene.effects().simpleParticleEmitter(ParticleTypes.DAMAGE_INDICATOR, new Vec3(0, 0.1, 0));
            scene.effects().emitParticles(location, hit, 0.35f, 5);
        }
    }

    private static void emitAtBlock(SceneBuilder scene, net.minecraft.core.BlockPos position,
                                    float radius, int count, ParticleEmitter emitter) {
        scene.effects().emitParticles(entityPoint(position, 0.7), emitter, radius, count);
    }

    private static Vec3 entityPoint(net.minecraft.core.BlockPos position, double height) {
        return Vec3.atCenterOf(position).add(0, height, 0);
    }
}
