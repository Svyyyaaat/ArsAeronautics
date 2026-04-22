package com.svyat.arsaeronautics.mixin;

import com.hollingsworth.arsnouveau.api.spell.SpellContext;
import com.hollingsworth.arsnouveau.api.spell.SpellResolver;
import com.hollingsworth.arsnouveau.api.spell.SpellStats;
import com.hollingsworth.arsnouveau.common.spell.effect.EffectGravity;
import com.svyat.arsaeronautics.util.ContraptionPushHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = EffectGravity.class, remap = false)
public class EffectGravityMixin {

    @Inject(method = "onResolveBlock", at = @At("HEAD"), cancellable = true)
    private void arsAeronautics$handleContraption(BlockHitResult rayTraceResult, Level world,
                                                   LivingEntity shooter, SpellStats spellStats,
                                                   SpellContext spellContext, SpellResolver resolver,
                                                   CallbackInfo ci) {
        if (world.isClientSide) return;
        if (spellStats.isSensitive()) return;

        Vec3 hitPos = rayTraceResult.getLocation();
        if (ContraptionPushHelper.isNearContraption(world, hitPos)) {
            ContraptionPushHelper.gravityContraptions(world, shooter, hitPos, spellStats);
            ci.cancel();
        }
    }
}
