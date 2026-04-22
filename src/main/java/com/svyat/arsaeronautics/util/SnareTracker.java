package com.svyat.arsaeronautics.util;

import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.joml.Vector3d;

import java.util.*;

/**
 * Tracks snared SubLevels and zeros their velocity every tick,
 * effectively freezing them in place for the spell duration.
 */
public class SnareTracker {

    private static final List<ActiveSnare> effects = new ArrayList<>();

    public static void addEffect(ServerSubLevel target, int durationTicks) {
        UUID id = target.getUniqueId();
        effects.removeIf(e -> e.targetId.equals(id));
        effects.add(new ActiveSnare(target, id, durationTicks));
    }

    public static void tick() {
        var it = effects.iterator();
        while (it.hasNext()) {
            ActiveSnare e = it.next();
            if (e.remainingTicks <= 0) {
                it.remove();
                continue;
            }
            try {
                RigidBodyHandle handle = RigidBodyHandle.of(e.target);
                Vector3d negLin = handle.getLinearVelocity().negate(new Vector3d());
                Vector3d negAng = handle.getAngularVelocity().negate(new Vector3d());
                handle.addLinearAndAngularVelocity(negLin, negAng);
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

    private static class ActiveSnare {
        final ServerSubLevel target;
        final UUID targetId;
        int remainingTicks;

        ActiveSnare(ServerSubLevel target, UUID targetId, int durationTicks) {
            this.target = target;
            this.targetId = targetId;
            this.remainingTicks = durationTicks;
        }
    }
}
