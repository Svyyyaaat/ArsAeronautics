package com.svyat.arsaeronautics.util;

import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.joml.Vector3d;

import java.util.*;

/**
 * Tracks SubLevels under a Slowfall effect. Each tick, caps downward velocity
 * to a maximum fall speed, making objects float down gently regardless of mass.
 * With Amplify the max fall speed decreases (stronger effect = slower fall).
 */
public class LevitationTracker {

    private static final List<ActiveLevitation> effects = new ArrayList<>();

    public static void addEffect(ServerSubLevel target, double maxFallSpeed, int durationTicks) {
        UUID id = target.getUniqueId();
        effects.removeIf(e -> e.targetId.equals(id));
        effects.add(new ActiveLevitation(target, id, maxFallSpeed, durationTicks));
    }

    public static void tick() {
        var it = effects.iterator();
        while (it.hasNext()) {
            ActiveLevitation e = it.next();
            if (e.remainingTicks <= 0) {
                it.remove();
                continue;
            }
            try {
                RigidBodyHandle handle = RigidBodyHandle.of(e.target);
                Vector3d vel = handle.getLinearVelocity(new Vector3d());

                // Only intervene if falling faster than the allowed max
                if (vel.y < -e.maxFallSpeed) {
                    double correction = -e.maxFallSpeed - vel.y; // positive — slows the fall
                    handle.addLinearAndAngularVelocity(new Vector3d(0, correction, 0), new Vector3d());
                }
            } catch (Exception ignored) {
                it.remove();
                continue;
            }
            e.remainingTicks--;
        }
    }

    public static void clear() {
        effects.clear();
    }

    private static class ActiveLevitation {
        final ServerSubLevel target;
        final UUID targetId;
        final double maxFallSpeed;
        int remainingTicks;

        ActiveLevitation(ServerSubLevel target, UUID targetId, double maxFallSpeed, int durationTicks) {
            this.target = target;
            this.targetId = targetId;
            this.maxFallSpeed = maxFallSpeed;
            this.remainingTicks = durationTicks;
        }
    }
}
