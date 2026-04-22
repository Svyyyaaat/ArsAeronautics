package com.svyat.arsaeronautics.util;

import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.physics.constraint.ConstraintJointAxis;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import dev.ryanhcode.sable.api.physics.constraint.free.FreeConstraintConfiguration;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.*;

/**
 * Manages active Telekinesis sessions: SubLevel grabs (constraint-based,
 * updated per physics tick) and entity drags (velocity-based, per server tick).
 * One session per player. Left-click (swing) to cancel.
 */
public class TelekinesisTracker {

    private static final Map<UUID, SubLevelSession> subLevelSessions = new HashMap<>();
    private static final Map<UUID, EntitySession> entitySessions = new HashMap<>();

    // 10x iron Handle values
    private static final double BASE_STIFFNESS = 2400.0;
    private static final double BASE_DAMPING = 300.0;
    private static final double BASE_MAX_FORCE = 1200.0;
    private static final double ANGULAR_DAMPING = 45.0;
    private static final double MAX_RANGE = 30.0;

    // Entity drag: spring factor (fraction of distance covered per tick)
    private static final double ENTITY_SPRING_FACTOR = 0.3;

    // ---- Public API ----

    public static void startSubLevelSession(ServerSubLevel target, ServerPlayer player, Vec3 hitPos,
                                            double grabDistance, int durationTicks, double powerMult) {
        UUID playerId = player.getUUID();
        clearPlayer(playerId);
        subLevelSessions.put(playerId, new SubLevelSession(target, player, hitPos, grabDistance, durationTicks, powerMult));
    }

    public static void startEntitySession(LivingEntity target, ServerPlayer player,
                                          double grabDistance, int durationTicks, double powerMult) {
        UUID playerId = player.getUUID();
        clearPlayer(playerId);
        entitySessions.put(playerId, new EntitySession(target, player, grabDistance, durationTicks, powerMult));
    }

    public static boolean isTracking(UUID playerUUID) {
        return subLevelSessions.containsKey(playerUUID) || entitySessions.containsKey(playerUUID);
    }

    // ---- Physics tick (SubLevel constraints only) ----

    public static void physicsTick(SubLevelPhysicsSystem system, double deltaTime) {
        for (SubLevelSession session : subLevelSessions.values()) {
            if (!session.isValid()) continue;
            if (session.target.getLevel() != system.getLevel()) continue;
            session.updateConstraint(system);
        }
    }

    // ---- Server tick (duration, cancel, entity drag) ----

    public static void tick() {
        tickSubLevelSessions();
        tickEntitySessions();
    }

    public static void clear() {
        for (SubLevelSession s : subLevelSessions.values()) s.cleanup();
        subLevelSessions.clear();
        for (EntitySession s : entitySessions.values()) s.cleanup();
        entitySessions.clear();
    }

    // ---- Internals ----

    private static void clearPlayer(UUID playerId) {
        SubLevelSession sl = subLevelSessions.remove(playerId);
        if (sl != null) sl.cleanup();
        EntitySession es = entitySessions.remove(playerId);
        if (es != null) es.cleanup();
    }

    private static void tickSubLevelSessions() {
        var it = subLevelSessions.entrySet().iterator();
        while (it.hasNext()) {
            SubLevelSession session = it.next().getValue();

            if (!session.isValid() || shouldCancel(session.player, session)) {
                session.cleanup();
                it.remove();
                continue;
            }

            Vec3 center = ContraptionPushHelper.bboxCenter(session.target);
            if (session.player.getEyePosition().distanceTo(center) > MAX_RANGE) {
                session.cleanup();
                it.remove();
                continue;
            }

            session.remainingTicks--;
        }
    }

    private static void tickEntitySessions() {
        var it = entitySessions.entrySet().iterator();
        while (it.hasNext()) {
            EntitySession session = it.next().getValue();

            if (!session.isValid() || shouldCancel(session.player, session)) {
                session.cleanup();
                it.remove();
                continue;
            }

            if (session.player.getEyePosition().distanceTo(session.target.position()) > MAX_RANGE) {
                session.cleanup();
                it.remove();
                continue;
            }

            // Move entity toward target position
            Vec3 eye = session.player.getEyePosition();
            Vec3 look = session.player.getLookAngle();
            Vec3 goalPos = eye.add(look.scale(session.grabDistance));
            Vec3 delta = goalPos.subtract(session.target.position());

            double factor = ENTITY_SPRING_FACTOR * session.powerMultiplier;
            session.target.setDeltaMovement(delta.scale(factor));
            session.target.hurtMarked = true;
            session.target.fallDistance = 0;

            session.remainingTicks--;
        }
    }

    /** Checks left-click cancel (rising edge) and duration expiry. */
    private static boolean shouldCancel(ServerPlayer player, SessionBase session) {
        boolean swinging = player.swinging;
        if (swinging && !session.wasSwinging) {
            return true;
        }
        session.wasSwinging = swinging;
        return session.remainingTicks <= 0;
    }

    // ---- Session classes ----

    private static abstract class SessionBase {
        final ServerPlayer player;
        final double grabDistance;
        final double powerMultiplier;
        int remainingTicks;
        boolean wasSwinging;

        SessionBase(ServerPlayer player, double grabDistance, int durationTicks, double powerMult) {
            this.player = player;
            this.grabDistance = grabDistance;
            this.remainingTicks = durationTicks;
            this.powerMultiplier = powerMult;
            this.wasSwinging = player.swinging;
        }

        abstract boolean isValid();
        void cleanup() {}
    }

    private static class SubLevelSession extends SessionBase {
        final ServerSubLevel target;
        final Vector3d plotAnchor;
        PhysicsConstraintHandle constraint;

        SubLevelSession(ServerSubLevel target, ServerPlayer player, Vec3 hitPos,
                        double grabDistance, int durationTicks, double powerMult) {
            super(player, grabDistance, durationTicks, powerMult);
            this.target = target;
            this.plotAnchor = new Vector3d(hitPos.x, hitPos.y, hitPos.z);
        }

        @Override
        boolean isValid() {
            return player != null && player.isAlive() && !player.isRemoved()
                    && target != null && target.getLevel() != null;
        }

        void updateConstraint(SubLevelPhysicsSystem physicsSystem) {
            if (constraint != null) {
                try { constraint.remove(); } catch (Exception ignored) {}
                constraint = null;
            }
            if (!isValid()) return;

            try {
                Vec3 eye = player.getEyePosition();
                Vec3 look = player.getLookAngle();
                Vec3 targetPos = eye.add(look.scale(grabDistance));
                Vector3d goal = new Vector3d(targetPos.x, targetPos.y, targetPos.z);

                PhysicsPipeline pipeline = physicsSystem.getPipeline();
                FreeConstraintConfiguration config = new FreeConstraintConfiguration(
                        goal, plotAnchor, new Quaterniond()
                );
                constraint = pipeline.addConstraint(null, target, config);

                double stiffness = BASE_STIFFNESS * powerMultiplier;
                double damping = BASE_DAMPING * powerMultiplier;
                double maxForce = BASE_MAX_FORCE * powerMultiplier;

                for (ConstraintJointAxis axis : ConstraintJointAxis.LINEAR) {
                    constraint.setMotor(axis, 0.0, stiffness, damping, true, maxForce);
                }
                for (ConstraintJointAxis axis : ConstraintJointAxis.ANGULAR) {
                    constraint.setMotor(axis, 0.0, 0.0, ANGULAR_DAMPING, true, maxForce);
                }
            } catch (Exception e) {
                constraint = null;
            }
        }

        @Override
        void cleanup() {
            if (constraint != null) {
                try { constraint.remove(); } catch (Exception ignored) {}
                constraint = null;
            }
        }
    }

    private static class EntitySession extends SessionBase {
        final LivingEntity target;

        EntitySession(LivingEntity target, ServerPlayer player,
                      double grabDistance, int durationTicks, double powerMult) {
            super(player, grabDistance, durationTicks, powerMult);
            this.target = target;
        }

        @Override
        boolean isValid() {
            return player != null && player.isAlive() && !player.isRemoved()
                    && target != null && target.isAlive() && !target.isRemoved();
        }
    }
}
