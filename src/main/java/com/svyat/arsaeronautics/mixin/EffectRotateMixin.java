package com.svyat.arsaeronautics.mixin;

import com.hollingsworth.arsnouveau.api.spell.*;
import com.hollingsworth.arsnouveau.common.spell.augment.AugmentExtract;
import com.hollingsworth.arsnouveau.common.spell.effect.EffectRotate;
import com.svyat.arsaeronautics.util.ContraptionPushHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashSet;
import java.util.Set;

@Mixin(value = EffectRotate.class, remap = false)
public class EffectRotateMixin {

    /** Add Extract to compatible augments so the spell book accepts it. */
    @Inject(method = "getCompatibleAugments", at = @At("RETURN"), cancellable = true)
    private void arsAeronautics$addExtract(CallbackInfoReturnable<Set<AbstractAugment>> cir) {
        Set<AbstractAugment> augments = new HashSet<>(cir.getReturnValue());
        augments.add(AugmentExtract.INSTANCE);
        cir.setReturnValue(augments);
    }

    /** When Extract is present and we hit a SubLevel, spin it instead of vanilla rotate. */
    @Inject(method = "onResolveBlock", at = @At("HEAD"), cancellable = true)
    private void arsAeronautics$handleContraption(BlockHitResult rayTraceResult, Level world,
                                                   LivingEntity shooter, SpellStats spellStats,
                                                   SpellContext spellContext, SpellResolver resolver,
                                                   CallbackInfo ci) {
        if (world.isClientSide) return;
        if (!spellStats.hasBuff(AugmentExtract.INSTANCE)) return;

        Vec3 hitPos = rayTraceResult.getLocation();
        if (ContraptionPushHelper.isNearContraption(world, hitPos)) {
            ContraptionPushHelper.rotateContraptions(world, shooter, hitPos, spellStats);
            ci.cancel();
        }
    }
}
