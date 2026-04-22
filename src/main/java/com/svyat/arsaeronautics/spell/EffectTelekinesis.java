package com.svyat.arsaeronautics.spell;

import com.hollingsworth.arsnouveau.api.spell.*;
import com.hollingsworth.arsnouveau.common.network.Networking;
import com.hollingsworth.arsnouveau.common.network.PacketUpdateFlight;
import com.hollingsworth.arsnouveau.common.spell.augment.AugmentAmplify;
import com.hollingsworth.arsnouveau.common.spell.augment.AugmentDampen;
import com.hollingsworth.arsnouveau.common.spell.augment.AugmentExtendTime;
import com.hollingsworth.arsnouveau.setup.registry.ModPotions;
import com.svyat.arsaeronautics.util.ContraptionPushHelper;
import com.svyat.arsaeronautics.util.TelekinesisTracker;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

public class EffectTelekinesis extends AbstractEffect {
    public static final EffectTelekinesis INSTANCE = new EffectTelekinesis();

    private EffectTelekinesis() {
        super(ResourceLocation.fromNamespaceAndPath("ars_aeronautics", "glyph_telekinesis"), "Telekinesis");
    }

    @Override
    public void onResolveBlock(BlockHitResult result, Level world, LivingEntity shooter,
                               SpellStats spellStats, SpellContext spellContext, SpellResolver resolver) {
        if (world.isClientSide || !(shooter instanceof ServerPlayer player)) return;

        Vec3 hitPos = result.getLocation();
        SubLevel hit = Sable.HELPER.getContaining(world, hitPos);
        if (!(hit instanceof ServerSubLevel ssl)) return;

        Vec3 center = ContraptionPushHelper.bboxCenter(ssl);
        double grabDistance = shooter.getEyePosition().distanceTo(center);
        int durationTicks = 200 + (int) (spellStats.getDurationMultiplier() * 100);
        double powerMult = Math.max(0.1, 1.0 + 0.3 * spellStats.getAmpMultiplier());

        TelekinesisTracker.startSubLevelSession(ssl, player, hitPos, grabDistance, durationTicks, powerMult);
    }

    @Override
    public void onResolveEntity(EntityHitResult rayTraceResult, Level world, LivingEntity shooter,
                                SpellStats spellStats, SpellContext spellContext, SpellResolver resolver) {
        if (world.isClientSide || !(shooter instanceof ServerPlayer player)) return;

        Entity target = rayTraceResult.getEntity();

        // Self-cast: grant creative flight
        if (target == shooter) {
            int durationTicks = 200 + (int) (spellStats.getDurationMultiplier() * 100);
            player.addEffect(new MobEffectInstance(ModPotions.FLIGHT_EFFECT, durationTicks));
            Networking.sendToPlayerClient(new PacketUpdateFlight(true, player.getAbilities().flying), player);
            return;
        }

        // Entity drag: grab a living entity and move it with your gaze
        if (target instanceof LivingEntity livingTarget) {
            double grabDistance = shooter.getEyePosition().distanceTo(target.position());
            int durationTicks = 200 + (int) (spellStats.getDurationMultiplier() * 100);
            double powerMult = Math.max(0.1, 1.0 + 0.3 * spellStats.getAmpMultiplier());

            TelekinesisTracker.startEntitySession(livingTarget, player, grabDistance, durationTicks, powerMult);
        }
    }

    @Override
    protected int getDefaultManaCost() {
        return 100;
    }

    @Override
    public SpellTier defaultTier() {
        return SpellTier.THREE;
    }

    @Override
    protected Set<AbstractAugment> getCompatibleAugments() {
        return augmentSetOf(AugmentAmplify.INSTANCE, AugmentDampen.INSTANCE, AugmentExtendTime.INSTANCE);
    }
}
