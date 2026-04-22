package com.svyat.arsaeronautics.util;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Tracks delayed Blink teleports. During the delay, spawns portal particles
 * and plays sound. When the timer expires, executes the teleport.
 */
public class BlinkTracker {

    private static final List<PendingBlink> effects = new ArrayList<>();
    private static final int PARTICLE_INTERVAL = 5;  // ticks between particle bursts
    private static final int PARTICLES_PER_BURST = 3;
    private static final int SOUND_INTERVAL = 40;     // ticks between ambient sounds

    public static void addEffect(ServerSubLevel target, Vec3 destination, double entityPadding,
                                   int delayTicks, ServerLevel targetLevel) {
        UUID id = target.getUniqueId();
        effects.removeIf(e -> e.targetId.equals(id));
        effects.add(new PendingBlink(target, id, destination, entityPadding, delayTicks, targetLevel));

        // Play initial sound
        Vec3 center = ContraptionPushHelper.bboxCenter(target);
        if (target.getLevel() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, center.x, center.y, center.z,
                    SoundEvents.ENDERMAN_TELEPORT, SoundSource.BLOCKS, 0.6f, 0.8f);
        }
    }

    public static void tick() {
        var it = effects.iterator();
        while (it.hasNext()) {
            PendingBlink e = it.next();
            try {
                if (e.remainingTicks <= 0) {
                    if (e.targetLevel != null && e.target.getLevel() instanceof ServerLevel sourceLevel) {
                        // Cross-dimension teleport
                        ContraptionPushHelper.executeCrossDimBlink(
                                sourceLevel, e.target, e.targetLevel, e.destination, e.entityPadding);
                        // Sound at destination in target dimension
                        e.targetLevel.playSound(null, e.destination.x, e.destination.y, e.destination.z,
                                SoundEvents.ENDERMAN_TELEPORT, SoundSource.BLOCKS, 1.0f, 1.0f);
                    } else {
                        // Same-dimension teleport
                        ContraptionPushHelper.executeBlinkTeleport(
                                e.target.getLevel(), e.target, e.destination, e.entityPadding);
                        // Teleport sound at new location
                        Vec3 newCenter = ContraptionPushHelper.bboxCenter(e.target);
                        if (e.target.getLevel() instanceof ServerLevel serverLevel) {
                            serverLevel.playSound(null, newCenter.x, newCenter.y, newCenter.z,
                                    SoundEvents.ENDERMAN_TELEPORT, SoundSource.BLOCKS, 1.0f, 1.0f);
                        }
                    }
                    it.remove();
                    continue;
                }

                // Spawn particles periodically
                if (e.remainingTicks % PARTICLE_INTERVAL == 0) {
                    spawnParticles(e);
                }

                // Ambient sound periodically
                if (e.remainingTicks % SOUND_INTERVAL == 0) {
                    Vec3 center = ContraptionPushHelper.bboxCenter(e.target);
                    if (e.target.getLevel() instanceof ServerLevel serverLevel) {
                        serverLevel.playSound(null, center.x, center.y, center.z,
                                SoundEvents.PORTAL_AMBIENT, SoundSource.BLOCKS, 0.3f, 1.2f);
                    }
                }
            } catch (Exception ignored) {
                it.remove();
                continue;
            }
            e.remainingTicks--;
        }
    }

    private static void spawnParticles(PendingBlink e) {
        if (!(e.target.getLevel() instanceof ServerLevel serverLevel)) return;

        var bbox = e.target.boundingBox();
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        for (int i = 0; i < PARTICLES_PER_BURST; i++) {
            double x = rng.nextDouble(bbox.minX(), bbox.maxX());
            double y = rng.nextDouble(bbox.minY(), bbox.maxY());
            double z = rng.nextDouble(bbox.minZ(), bbox.maxZ());

            serverLevel.sendParticles(ParticleTypes.PORTAL,
                    x, y, z, 1, 0, 0, 0, 0.5);
        }
    }

    public static void clear() {
        effects.clear();
    }

    private static class PendingBlink {
        final ServerSubLevel target;
        final UUID targetId;
        final Vec3 destination;
        final double entityPadding;
        final ServerLevel targetLevel; // null = same dimension
        int remainingTicks;

        PendingBlink(ServerSubLevel target, UUID targetId, Vec3 destination,
                     double entityPadding, int delayTicks, ServerLevel targetLevel) {
            this.target = target;
            this.targetId = targetId;
            this.destination = destination;
            this.entityPadding = entityPadding;
            this.remainingTicks = delayTicks;
            this.targetLevel = targetLevel;
        }
    }
}
