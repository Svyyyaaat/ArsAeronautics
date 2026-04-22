package com.svyat.arsaeronautics.mixin;

import com.hollingsworth.arsnouveau.api.spell.AbstractEffect;
import com.hollingsworth.arsnouveau.api.spell.SpellContext;
import com.hollingsworth.arsnouveau.api.spell.SpellResolver;
import com.hollingsworth.arsnouveau.api.spell.SpellStats;
import com.hollingsworth.arsnouveau.common.spell.effect.EffectSlowfall;
import com.hollingsworth.arsnouveau.common.spell.effect.EffectSnare;
import com.svyat.arsaeronautics.util.ContraptionPushHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Targets AbstractEffect.onResolveBlock because EffectSnare and EffectSlowfall
 * do NOT override onResolveBlock themselves — they only have onResolveEntity.
 * We intercept the inherited method and use instanceof checks.
 */
@Mixin(value = AbstractEffect.class, remap = false)
public class AbstractEffectBlockMixin {

    @Inject(method = "onResolveBlock", at = @At("HEAD"), cancellable = true)
    private void arsAeronautics$handleSnareSlowfall(BlockHitResult rayTraceResult, Level world,
                                                     LivingEntity shooter, SpellStats spellStats,
                                                     SpellContext spellContext, SpellResolver resolver,
                                                     CallbackInfo ci) {
        if (world.isClientSide) return;

        Object self = this;

        if (!(self instanceof EffectSnare) && !(self instanceof EffectSlowfall)) return;

        Vec3 hitPos = rayTraceResult.getLocation();
        if (!ContraptionPushHelper.isNearContraption(world, hitPos)) return;

        if (self instanceof EffectSnare) {
            ContraptionPushHelper.snareContraption(world, hitPos, spellStats);
            ci.cancel();
        } else {
            ContraptionPushHelper.slowfallContraption(world, hitPos, spellStats);
            ci.cancel();
        }
    }
}
