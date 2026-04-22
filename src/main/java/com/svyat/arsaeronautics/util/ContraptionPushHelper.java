package com.svyat.arsaeronautics.util;

import com.hollingsworth.arsnouveau.api.spell.SpellStats;
import com.hollingsworth.arsnouveau.common.block.tile.PortalTile;
import com.hollingsworth.arsnouveau.common.spell.augment.AugmentPierce;
import com.hollingsworth.arsnouveau.common.items.data.WarpScrollData;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelData;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelSerializer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class ContraptionPushHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger("ArsAeronautics");

    private static final double BASE_IMPULSE = 180.0;
    private static final double AMP_IMPULSE_BONUS = 120.0;
    private static final double LAUNCH_MULTIPLIER = 1.5;
    private static final double GRAVITY_MULTIPLIER = 2.0;
    private static final double GRAVITY_PER_TICK_DIVISOR = 40.0;
    static final int GRAVITY_TICKS_PER_EXTEND = 200; // 10 seconds
    private static final double BASE_TORQUE = 100.0;
    private static final double AMP_TORQUE_BONUS = 75.0;
    private static final double BASE_MAX_FALL_SPEED = 1.5;
    private static final double AMP_FALL_REDUCTION = 0.5;
    private static final double SEARCH_RADIUS = 2.0;

    /** Knockback — push along caster look direction, torque on direct hit. */
    public static boolean pushContraptions(Level world, LivingEntity shooter,
                                           Vec3 hitPos, SpellStats spellStats) {
        Vec3 dir = shooter.getLookAngle();
        double power = BASE_IMPULSE + AMP_IMPULSE_BONUS * spellStats.getAmpMultiplier();
        return applyForce(world, hitPos, dir, power, spellStats, true);
    }

    /** Pull — exact mirror of knockback: same power, torque on direct hit, reversed direction. */
    public static boolean pullContraptions(Level world, LivingEntity shooter,
                                           Vec3 hitPos, SpellStats spellStats) {
        Vec3 dir = shooter.getLookAngle().reverse();
        double power = BASE_IMPULSE + AMP_IMPULSE_BONUS * spellStats.getAmpMultiplier();
        return applyForce(world, hitPos, dir, power, spellStats, true);
    }

    /** Launch — straight up, applied at hit point (same as push/pull). */
    public static boolean launchContraptions(Level world, LivingEntity shooter,
                                             Vec3 hitPos, SpellStats spellStats) {
        Vec3 dir = new Vec3(0, 1, 0);
        double power = (BASE_IMPULSE + AMP_IMPULSE_BONUS * spellStats.getAmpMultiplier()) * LAUNCH_MULTIPLIER;
        return applyForce(world, hitPos, dir, power, spellStats, true);
    }

    /**
     * Gravity — straight down at center of mass, 10x knockback strength.
     * With Extend Time, also starts a continuous per-tick pull via GravityTracker.
     */
    public static boolean gravityContraptions(Level world, LivingEntity shooter,
                                              Vec3 hitPos, SpellStats spellStats) {
        Vec3 dir = new Vec3(0, -1, 0);
        double power = (BASE_IMPULSE + AMP_IMPULSE_BONUS * spellStats.getAmpMultiplier()) * GRAVITY_MULTIPLIER;

        boolean hitAny = applyForce(world, hitPos, dir, power, spellStats, true);

        double duration = spellStats.getDurationMultiplier();
        if (hitAny && duration > 0) {
            int ticks = (int) (duration * GRAVITY_TICKS_PER_EXTEND);
            double perTickPower = power / GRAVITY_PER_TICK_DIVISOR;

            double aoe = spellStats.getAoeMultiplier();
            int pierce = spellStats.getBuffCount(AugmentPierce.INSTANCE);

            for (ServerSubLevel target : findSubLevels(world, hitPos, aoe, pierce)) {
                GravityTracker.addEffect(target, hitPos, perTickPower, ticks);
            }
        }

        return hitAny;
    }

    /**
     * Rotate — spins SubLevels around an axis.
     * Default axis: Y (vertical spin). Sensitive: caster's look direction.
     * Amplify = more clockwise, Dampen = counter-clockwise.
     */
    public static boolean rotateContraptions(Level world, LivingEntity shooter,
                                             Vec3 hitPos, SpellStats spellStats) {
        double strength = BASE_TORQUE + AMP_TORQUE_BONUS * spellStats.getAmpMultiplier();

        // Axis: Y by default, look direction if Sensitive
        Vec3 axis = spellStats.isSensitive()
                ? shooter.getLookAngle()
                : new Vec3(0, 1, 0);

        Vec3 globalTorque = axis.normalize().scale(strength);

        SubLevel hit = Sable.HELPER.getContaining(world, hitPos);
        if (!(hit instanceof ServerSubLevel ssl)) return false;

        Vector3d localTorque = ssl.logicalPose().transformNormalInverse(
                new Vector3d(globalTorque.x, globalTorque.y, globalTorque.z), new Vector3d());
        RigidBodyHandle.of(ssl).applyTorqueImpulse(localTorque);
        return true;
    }

    /** Snare — freeze SubLevel in place for a duration. */
    public static boolean snareContraption(Level world, Vec3 hitPos, SpellStats spellStats) {
        SubLevel hit = Sable.HELPER.getContaining(world, hitPos);
        if (!(hit instanceof ServerSubLevel ssl)) return false;

        double durationMult = spellStats.getDurationMultiplier();
        int ticks = Math.max(20, (int) ((8 + 1 * durationMult) * 20));
        SnareTracker.addEffect(ssl, ticks);
        return true;
    }

    /** Slowfall — cap downward velocity each tick. Amplify = slower fall (lower cap). */
    public static boolean slowfallContraption(Level world, Vec3 hitPos, SpellStats spellStats) {
        SubLevel hit = Sable.HELPER.getContaining(world, hitPos);
        if (!(hit instanceof ServerSubLevel ssl)) return false;

        double durationMult = spellStats.getDurationMultiplier();
        int ticks = Math.max(20, (int) ((30 + 8 * durationMult) * 20));
        double maxFallSpeed = BASE_MAX_FALL_SPEED - AMP_FALL_REDUCTION * spellStats.getAmpMultiplier();

        LevitationTracker.addEffect(ssl, maxFallSpeed, ticks);
        return true;
    }

    private static final double BLINK_BASE_ENTITY_PADDING = 1.0;
    private static final int BLINK_DELAY_TICKS_PER_EXTEND = 100; // 5 seconds

    /**
     * Blink — teleport an entire SubLevel so its center ends up at the Warp Scroll destination.
     * With ExtendTime, the teleport is delayed (5s per glyph) with particles and sound.
     * AOE increases the entity capture padding by 1 block per level.
     * Supports cross-dimension teleport via serialize→remove→load workflow.
     */
    public static boolean blinkContraption(Level world, LivingEntity shooter, Vec3 hitPos,
                                           WarpScrollData scrollData, SpellStats spellStats) {
        if (!(world instanceof ServerLevel sourceLevel)) return false;

        SubLevel hit = Sable.HELPER.getContaining(world, hitPos);
        if (!(hit instanceof ServerSubLevel ssl)) return false;

        BlockPos destBlockPos = scrollData.pos().orElse(null);
        if (destBlockPos == null) return false;

        Vec3 destination = new Vec3(destBlockPos.getX() + 0.5, destBlockPos.getY(), destBlockPos.getZ() + 0.5);
        double entityPadding = BLINK_BASE_ENTITY_PADDING + spellStats.getAoeMultiplier();

        // Resolve target dimension — null means same dimension
        ServerLevel targetLevel = null;
        if (scrollData.crossDim()) {
            targetLevel = PortalTile.getServerLevel(scrollData.dimension(), sourceLevel);
            if (targetLevel == null) return false;
            if (targetLevel == sourceLevel) targetLevel = null; // same dim after all
        }

        double durationMult = spellStats.getDurationMultiplier();
        if (durationMult > 0) {
            int delayTicks = (int) (durationMult * BLINK_DELAY_TICKS_PER_EXTEND);
            BlinkTracker.addEffect(ssl, destination, entityPadding, delayTicks, targetLevel);
        } else if (targetLevel != null) {
            executeCrossDimBlink(sourceLevel, ssl, targetLevel, destination, entityPadding);
        } else {
            executeBlinkTeleport(world, ssl, destination, entityPadding);
        }

        return true;
    }

    /** Executes same-dimension SubLevel + entity teleport. Used directly and by BlinkTracker. */
    static boolean executeBlinkTeleport(Level world, ServerSubLevel ssl, Vec3 destination, double entityPadding) {
        // hitPos is in Sable's internal plot space (~20M coords), NOT world space.
        // Use the SubLevel's world-space bounding box center for the delta instead.
        Vec3 worldCenter = bboxCenter(ssl);
        Vec3 delta = destination.subtract(worldCenter);

        // Snapshot pose values into independent copies
        Vector3d currentPos = new Vector3d(ssl.logicalPose().position());
        Quaterniond currentOri = new Quaterniond(ssl.logicalPose().orientation());
        Vector3d newPos = new Vector3d(
                currentPos.x + delta.x,
                currentPos.y + delta.y,
                currentPos.z + delta.z
        );

        RigidBodyHandle handle = RigidBodyHandle.of(ssl);
        if (handle == null) return false;

        // Collect entities BEFORE teleporting the SubLevel
        List<Entity> entities = collectNearbyEntities(world, ssl, entityPadding);

        // Record riding relationships BEFORE teleport — Sable's entity kicking
        // dismounts passengers when the SubLevel moves via handle.teleport().
        // Seat entities are internal to the SubLevel (in plot space) and won't be in
        // our world-space entity collection, so record ANY vehicle relationship.
        Map<Entity, Entity> riderToVehicle = new IdentityHashMap<>();
        for (Entity entity : entities) {
            Entity vehicle = entity.getVehicle();
            if (vehicle != null) {
                riderToVehicle.put(entity, vehicle);
            }
        }

        // Teleport SubLevel — preserve orientation, use independent copies
        handle.teleport(newPos, currentOri);

        // Zero velocity
        Vector3d linVel = handle.getLinearVelocity(new Vector3d());
        Vector3d angVel = handle.getAngularVelocity(new Vector3d());
        handle.addLinearAndAngularVelocity(linVel.negate(new Vector3d()), angVel.negate(new Vector3d()));

        // Teleport all entities by the same delta
        for (Entity entity : entities) {
            Vec3 oldPos = entity.position();
            entity.teleportTo(oldPos.x + delta.x, oldPos.y + delta.y, oldPos.z + delta.z);
            entity.setDeltaMovement(Vec3.ZERO);
        }

        // Re-mount any passengers that were dismounted by Sable's entity kicking
        for (Map.Entry<Entity, Entity> entry : riderToVehicle.entrySet()) {
            Entity rider = entry.getKey();
            Entity vehicle = entry.getValue();
            if (rider.getVehicle() == null) {
                rider.startRiding(vehicle, true);
            }
        }

        // Anchor entities to SubLevel so they move with it during de-clipping
        BlinkFreezeTracker.freeze(entities, ssl);

        return true;
    }

    /**
     * Cross-dimension SubLevel teleport: serialize → load in target → remove from source.
     * Loads at the ORIGINAL pose first (preserving internal data consistency),
     * then teleports the new SubLevel to the destination via the physics handle.
     * Entities are teleported via changeDimension.
     */
    static boolean executeCrossDimBlink(ServerLevel sourceLevel, ServerSubLevel ssl,
                                        ServerLevel targetLevel, Vec3 destination, double entityPadding) {
        Vec3 worldCenter = bboxCenter(ssl);
        Vec3 delta = destination.subtract(worldCenter);

        // Collect entities and snapshot positions BEFORE any changes.
        // Position must be captured NOW because removeSubLevel/fullyLoad may allow
        // entity ticking, and with the SubLevel's collision removed, entities would fall.
        List<Entity> entities = collectNearbyEntities(sourceLevel, ssl, entityPadding);
        Map<Entity, Vec3> snapshotPositions = new HashMap<>();
        for (Entity e : entities) {
            snapshotPositions.put(e, e.position());
        }

        // Record riding relationships NOW — before removeSubLevel potentially dismounts
        // entities via Sable's entity kicking system. Seat entities are SubLevel-internal
        // (in plot space) and won't be in our entity set, so record ANY vehicle relationship.
        Map<Entity, Entity> riderToVehicle = new IdentityHashMap<>();
        for (Entity entity : entities) {
            Entity vehicle = entity.getVehicle();
            if (vehicle != null) {
                riderToVehicle.put(entity, vehicle);
            }
        }

        // Dismount all passengers before any SubLevel changes so removeSubLevel
        // and changeDimension don't interfere with riding state.
        for (Entity rider : riderToVehicle.keySet()) {
            rider.stopRiding();
        }

        // Serialize the SubLevel with its dependency chain
        List<UUID> deps = SubLevelHelper.getLoadingDependencyChain(ssl)
                .stream().map(SubLevel::getUniqueId).toList();
        SubLevelData data = SubLevelSerializer.toData(ssl, deps);
        if (data == null) return false;

        // Remap section indices if dimensions have different height ranges.
        // Nether minSection=0, Overworld minSection=-4. Section indices in the serialized
        // data are array offsets, so they must be adjusted for the target dimension.
        int sectionOffset = sourceLevel.getMinSection() - targetLevel.getMinSection();
        if (sectionOffset != 0) {
            remapSectionIndices(data.fullTag(), sectionOffset);
        }

        // Remove from source dimension FIRST to free the UUID.
        // Then flush holding chunk deletions so stale data doesn't persist across saves.
        var sourceContainer = SubLevelContainer.getContainer(sourceLevel);
        if (sourceContainer != null) {
            sourceContainer.removeSubLevel(ssl, SubLevelRemovalReason.REMOVED);
            sourceContainer.getHoldingChunkMap().saveAll();
        }

        // Clean up any ghost SubLevel in target dimension (stale holding data from previous teleport)
        var targetContainer = SubLevelContainer.getContainer(targetLevel);
        if (targetContainer != null) {
            SubLevel ghost = targetContainer.getSubLevel(data.uuid());
            if (ghost != null) {
                LOGGER.info("[ArsAero CrossDim] Removing stale SubLevel {} from target dimension", data.uuid());
                targetContainer.removeSubLevel(ghost, SubLevelRemovalReason.REMOVED);
                targetContainer.getHoldingChunkMap().saveAll();
            }
        }

        // Ensure plot coordinates are free in the target dimension to prevent
        // "Plot already exists" crashes from stale holding chunk data
        ensureFreePlotCoordinates(data.fullTag(), targetLevel);

        // Load in target dimension at ORIGINAL pose (preserves internal consistency)
        ServerSubLevel newSsl = SubLevelSerializer.fullyLoad(targetLevel, data);
        if (newSsl == null) {
            LOGGER.warn("[ArsAero CrossDim] fullyLoad returned null — SubLevel lost!");
            return false;
        }

        // Teleport the new SubLevel to the destination using the physics handle
        Vector3d currentPos = new Vector3d(newSsl.logicalPose().position());
        Quaterniond currentOri = new Quaterniond(newSsl.logicalPose().orientation());
        Vector3d newPos = new Vector3d(
                currentPos.x + delta.x,
                currentPos.y + delta.y,
                currentPos.z + delta.z
        );

        RigidBodyHandle handle = RigidBodyHandle.of(newSsl);
        if (handle != null) {
            handle.teleport(newPos, currentOri);
            // Zero velocity
            Vector3d linVel = handle.getLinearVelocity(new Vector3d());
            Vector3d angVel = handle.getAngularVelocity(new Vector3d());
            handle.addLinearAndAngularVelocity(linVel.negate(new Vector3d()), angVel.negate(new Vector3d()));
        }

        // Teleport all entities cross-dimension, tracking old→new entity mapping.
        // changeDimension may return a new Entity instance (same UUID) for non-players.
        Map<Entity, Entity> oldToNew = new IdentityHashMap<>();
        for (Entity entity : entities) {
            Vec3 oldPos = snapshotPositions.getOrDefault(entity, entity.position());
            Vec3 newEntityPos = new Vec3(
                    oldPos.x + delta.x,
                    oldPos.y + delta.y,
                    oldPos.z + delta.z
            );
            Entity newEntity = entity.changeDimension(new DimensionTransition(
                    targetLevel, newEntityPos, Vec3.ZERO,
                    entity.getYRot(), entity.getXRot(),
                    false, DimensionTransition.DO_NOTHING
            ));
            if (newEntity != null) {
                oldToNew.put(entity, newEntity);
            }
        }

        // Re-mount passengers on their vehicles in the target dimension.
        for (Map.Entry<Entity, Entity> entry : riderToVehicle.entrySet()) {
            Entity newRider = oldToNew.get(entry.getKey());
            Entity newVehicle = oldToNew.get(entry.getValue());
            if (newRider != null && newVehicle != null) {
                newRider.startRiding(newVehicle, true);
            }
        }

        // Anchor entities to SubLevel so they move with it during de-clipping
        BlinkFreezeTracker.freeze(oldToNew.values(), newSsl);

        return true;
    }

    private static final double MAX_BBOX_DIMENSION = 500.0;

    private static List<Entity> collectNearbyEntities(Level world, ServerSubLevel ssl, double inflate) {
        var bbox = ssl.boundingBox();
        double sizeX = bbox.maxX() - bbox.minX();
        double sizeY = bbox.maxY() - bbox.minY();
        double sizeZ = bbox.maxZ() - bbox.minZ();

        // Guard against corrupted bounding boxes (e.g. spanning world space to plot space)
        if (sizeX > MAX_BBOX_DIMENSION || sizeY > MAX_BBOX_DIMENSION || sizeZ > MAX_BBOX_DIMENSION) {
            LOGGER.warn("[ArsAero] SubLevel bbox abnormally large ({}, {}, {}), using pose position fallback",
                    sizeX, sizeY, sizeZ);
            Vec3 center = new Vec3(
                    ssl.logicalPose().position().x(),
                    ssl.logicalPose().position().y(),
                    ssl.logicalPose().position().z()
            );
            double r = 8.0 + inflate;
            AABB fallback = new AABB(
                    center.x - r, center.y - r, center.z - r,
                    center.x + r, center.y + r, center.z + r
            );
            return world.getEntities(null, fallback);
        }

        AABB searchArea = new AABB(
                bbox.minX() - inflate, bbox.minY() - inflate, bbox.minZ() - inflate,
                bbox.maxX() + inflate, bbox.maxY() + inflate, bbox.maxZ() + inflate
        );
        return world.getEntities(null, searchArea);
    }

    public static boolean isNearContraption(Level world, Vec3 pos) {
        return Sable.HELPER.getContaining(world, pos) != null;
    }

    // ---- internals ----

    private static boolean applyForce(Level world, Vec3 hitPos, Vec3 direction, double power,
                                      SpellStats spellStats, boolean torqueOnDirectHit) {
        if (power <= 0) return false;

        Vec3 impulse = direction.scale(power);

        double aoe = spellStats.getAoeMultiplier();
        int pierce = spellStats.getBuffCount(AugmentPierce.INSTANCE);

        SubLevel directHit = Sable.HELPER.getContaining(world, hitPos);
        UUID directHitId = directHit != null ? directHit.getUniqueId() : null;

        boolean hitAny = false;
        Set<UUID> pushed = new HashSet<>();

        for (ServerSubLevel target : findSubLevels(world, hitPos, aoe, pierce)) {
            if (pushed.add(target.getUniqueId())) {
                Vec3 applyAt;
                if (torqueOnDirectHit && target.getUniqueId().equals(directHitId)) {
                    applyAt = hitPos;
                } else {
                    applyAt = bboxCenter(target);
                }

                applyImpulse(target, applyAt, impulse);
                hitAny = true;
            }
        }

        return hitAny;
    }

    static void applyImpulse(ServerSubLevel target, Vec3 applyAt, Vec3 globalImpulse) {
        Vector3d localImpulse = target.logicalPose().transformNormalInverse(
                new Vector3d(globalImpulse.x, globalImpulse.y, globalImpulse.z), new Vector3d());
        Vec3 localImpulseVec = new Vec3(localImpulse.x, localImpulse.y, localImpulse.z);
        RigidBodyHandle.of(target).applyImpulseAtPoint(applyAt, localImpulseVec);
    }

    public static Vec3 bboxCenter(ServerSubLevel target) {
        var bbox = target.boundingBox();
        double sizeX = bbox.maxX() - bbox.minX();
        double sizeY = bbox.maxY() - bbox.minY();
        double sizeZ = bbox.maxZ() - bbox.minZ();

        // Guard against corrupted bounding boxes (spanning world space to plot space).
        // Fall back to logicalPose position which is always in valid world space.
        if (sizeX > MAX_BBOX_DIMENSION || sizeY > MAX_BBOX_DIMENSION || sizeZ > MAX_BBOX_DIMENSION) {
            var pos = target.logicalPose().position();
            return new Vec3(pos.x(), pos.y(), pos.z());
        }

        return new Vec3(
                (bbox.minX() + bbox.maxX()) / 2.0,
                (bbox.minY() + bbox.maxY()) / 2.0,
                (bbox.minZ() + bbox.maxZ()) / 2.0
        );
    }

    private static List<ServerSubLevel> findSubLevels(Level world, Vec3 pos, double aoe, int pierce) {
        double r = SEARCH_RADIUS + aoe + pierce;

        BoundingBox3d searchBox = new BoundingBox3d(
                pos.x - r, pos.y - r, pos.z - r,
                pos.x + r, pos.y + r, pos.z + r
        );

        List<ServerSubLevel> results = new ArrayList<>();

        SubLevel exact = Sable.HELPER.getContaining(world, pos);
        if (exact instanceof ServerSubLevel ssl) {
            results.add(ssl);
        }

        for (SubLevel candidate : Sable.HELPER.getAllIntersecting(world, searchBox)) {
            if (candidate instanceof ServerSubLevel ssl) {
                results.add(ssl);
            }
        }

        return results;
    }

    /**
     * Ensures the plot coordinates in the serialized SubLevel data are free in the target dimension.
     * If the stored coordinates are already occupied (e.g. by a ghost SubLevel from stale holding
     * chunk data), finds and assigns fresh free coordinates to prevent "Plot already exists" crashes.
     */
    private static void ensureFreePlotCoordinates(CompoundTag fullTag, ServerLevel targetLevel) {
        CompoundTag plotTag = fullTag.getCompound("plot");
        int plotX = plotTag.getInt("plot_x");
        int plotZ = plotTag.getInt("plot_z");

        var container = SubLevelContainer.getContainer(targetLevel);
        if (container == null) return;

        // Check if stored coordinates are already free
        if (container.getSubLevel(plotX, plotZ) == null) return;

        // Stored coordinates are occupied — find a free slot
        int gridSize = 1 << container.getLogSideLength();
        for (int z = 0; z < gridSize; z++) {
            for (int x = 0; x < gridSize; x++) {
                if (container.getSubLevel(x, z) == null) {
                    LOGGER.debug("[ArsAero CrossDim] Plot ({}, {}) occupied, reassigning to ({}, {})",
                            plotX, plotZ, x, z);
                    plotTag.putInt("plot_x", x);
                    plotTag.putInt("plot_z", z);
                    return;
                }
            }
        }
        LOGGER.warn("[ArsAero CrossDim] No free plot coordinates found in target dimension!");
    }

    /**
     * Remaps section indices in serialized SubLevel data to account for different
     * dimension height ranges. Section indices are array offsets into the chunk's
     * sections array — Nether has 16 sections (minSection=0), Overworld has 24
     * (minSection=-4). Without remapping, blocks end up at the wrong Y level.
     *
     * @param offset sourceLevel.getMinSection() - targetLevel.getMinSection()
     *               (positive when going nether→overworld, negative for overworld→nether)
     */
    private static void remapSectionIndices(CompoundTag fullTag, int offset) {
        CompoundTag plotTag = fullTag.getCompound("plot");
        CompoundTag chunks = plotTag.getCompound("chunks");

        for (String chunkKey : chunks.getAllKeys()) {
            CompoundTag chunkTag = chunks.getCompound(chunkKey);
            CompoundTag sectionsTag = chunkTag.getCompound("sections");

            // Build remapped sections
            CompoundTag remapped = new CompoundTag();
            for (String sectionKey : sectionsTag.getAllKeys()) {
                int oldIndex = Integer.parseInt(sectionKey);
                int newIndex = oldIndex + offset;
                if (newIndex >= 0) {
                    remapped.put(String.valueOf(newIndex), sectionsTag.get(sectionKey));
                }
            }

            chunkTag.put("sections", remapped);
        }
    }
}
