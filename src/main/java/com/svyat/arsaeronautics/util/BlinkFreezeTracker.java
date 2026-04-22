package com.svyat.arsaeronautics.util;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import java.util.*;

/**
 * Anchors entities to a SubLevel for a short period after Blink teleportation.
 * Each tick, repositions frozen entities at their stored offset from the SubLevel's
 * logicalPose position, so they move WITH the SubLevel during de-clipping instead
 * of being launched away by collision forces.
 */
public class BlinkFreezeTracker {

    private static final int FREEZE_TICKS = 20; // 1 second

    private static final List<FrozenEntity> frozen = new ArrayList<>();

    public static void freeze(Entity entity, ServerSubLevel ssl) {
        Vector3d sslPos = ssl.logicalPose().position();
        Vec3 offset = entity.position().subtract(sslPos.x(), sslPos.y(), sslPos.z());

        // Don't double-freeze, just reset timer + offset
        for (FrozenEntity f : frozen) {
            if (f.entity == entity) {
                f.remainingTicks = FREEZE_TICKS;
                f.ssl = ssl;
                f.offset = offset;
                return;
            }
        }
        frozen.add(new FrozenEntity(entity, ssl, offset, FREEZE_TICKS));
    }

    public static void freeze(Collection<? extends Entity> entities, ServerSubLevel ssl) {
        for (Entity entity : entities) {
            freeze(entity, ssl);
        }
    }

    public static void tick() {
        var it = frozen.iterator();
        while (it.hasNext()) {
            FrozenEntity f = it.next();

            if (f.entity.isRemoved()) {
                it.remove();
                continue;
            }

            f.remainingTicks--;
            if (f.remainingTicks <= 0) {
                it.remove();
                continue;
            }

            // If SubLevel is gone, just zero velocity as fallback
            if (f.ssl == null || f.ssl.getLevel() == null) {
                f.entity.setDeltaMovement(Vec3.ZERO);
                f.entity.fallDistance = 0;
                f.entity.hurtMarked = true;
                continue;
            }

            // Snap entity to its anchored position relative to SubLevel
            Vector3d sslPos = f.ssl.logicalPose().position();
            double targetX = sslPos.x() + f.offset.x;
            double targetY = sslPos.y() + f.offset.y;
            double targetZ = sslPos.z() + f.offset.z;
            f.entity.teleportTo(targetX, targetY, targetZ);
            f.entity.setDeltaMovement(Vec3.ZERO);
            f.entity.fallDistance = 0;
            f.entity.hurtMarked = true;
        }
    }

    public static void clear() {
        frozen.clear();
    }

    private static class FrozenEntity {
        final Entity entity;
        ServerSubLevel ssl;
        Vec3 offset;
        int remainingTicks;

        FrozenEntity(Entity entity, ServerSubLevel ssl, Vec3 offset, int remainingTicks) {
            this.entity = entity;
            this.ssl = ssl;
            this.offset = offset;
            this.remainingTicks = remainingTicks;
        }
    }
}
