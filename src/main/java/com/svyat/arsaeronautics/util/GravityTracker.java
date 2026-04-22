package com.svyat.arsaeronautics.util;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * Tracks active continuous-gravity effects applied by the Gravity spell
 * when Extend Time augments are used. Each tick, applies a downward impulse
 * at the SubLevel's center of mass.
 */
public class GravityTracker {

    private static final List<ActiveGravity> effects = new ArrayList<>();
    private static final Vec3 DOWN = new Vec3(0, -1, 0);

    public static void addEffect(ServerSubLevel target, Vec3 hitPos, double perTickPower, int durationTicks) {
        UUID id = target.getUniqueId();
        // Refresh if already tracked
        effects.removeIf(e -> e.targetId.equals(id));
        effects.add(new ActiveGravity(target, id, hitPos, perTickPower, durationTicks));
    }

    public static void tick() {
        var it = effects.iterator();
        while (it.hasNext()) {
            ActiveGravity e = it.next();
            if (e.remainingTicks <= 0) {
                it.remove();
                continue;
            }
            try {
                Vec3 impulse = DOWN.scale(e.power);
                ContraptionPushHelper.applyImpulse(e.target, e.hitPos, impulse);
            } catch (Exception ignored) {
                // SubLevel may have been removed / unloaded
                it.remove();
                continue;
            }
            e.remainingTicks--;
        }
    }

    public static void clear() {
        effects.clear();
    }

    private static class ActiveGravity {
        final ServerSubLevel target;
        final UUID targetId;
        final Vec3 hitPos;
        final double power;
        int remainingTicks;

        ActiveGravity(ServerSubLevel target, UUID targetId, Vec3 hitPos, double power, int durationTicks) {
            this.target = target;
            this.targetId = targetId;
            this.hitPos = hitPos;
            this.power = power;
            this.remainingTicks = durationTicks;
        }
    }
}
