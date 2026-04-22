package com.svyat.arsaeronautics.mixin;

import com.hollingsworth.arsnouveau.api.spell.AbstractAugment;
import com.hollingsworth.arsnouveau.common.spell.augment.AugmentAmplify;
import com.hollingsworth.arsnouveau.common.spell.effect.EffectSnare;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashSet;
import java.util.Set;

@Mixin(value = EffectSnare.class, remap = false)
public class EffectSnareMixin {

    @Inject(method = "getCompatibleAugments", at = @At("RETURN"), cancellable = true)
    private void arsAeronautics$addAmplify(CallbackInfoReturnable<Set<AbstractAugment>> cir) {
        Set<AbstractAugment> augments = new HashSet<>(cir.getReturnValue());
        augments.add(AugmentAmplify.INSTANCE);
        cir.setReturnValue(augments);
    }
}
