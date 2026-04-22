package com.svyat.arsaeronautics.mixin;

import com.hollingsworth.arsnouveau.api.item.inv.InteractType;
import com.hollingsworth.arsnouveau.api.item.inv.SlotReference;
import com.hollingsworth.arsnouveau.api.spell.*;
import com.hollingsworth.arsnouveau.api.spell.wrapped_caster.TileCaster;
import com.hollingsworth.arsnouveau.common.items.data.WarpScrollData;
import com.hollingsworth.arsnouveau.common.spell.augment.AugmentAOE;
import com.hollingsworth.arsnouveau.common.spell.augment.AugmentExtendTime;
import com.hollingsworth.arsnouveau.common.spell.effect.EffectBlink;
import com.hollingsworth.arsnouveau.setup.registry.DataComponentRegistry;
import com.svyat.arsaeronautics.util.ContraptionPushHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
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

@Mixin(value = EffectBlink.class, remap = false)
public class EffectBlinkMixin {

    @Inject(method = "getCompatibleAugments", at = @At("RETURN"), cancellable = true)
    private void arsAeronautics$addAugments(CallbackInfoReturnable<Set<AbstractAugment>> cir) {
        Set<AbstractAugment> augments = new HashSet<>(cir.getReturnValue());
        augments.add(AugmentAOE.INSTANCE);
        augments.add(AugmentExtendTime.INSTANCE);
        cir.setReturnValue(augments);
    }

    @Inject(method = "onResolveBlock", at = @At("HEAD"), cancellable = true)
    private void arsAeronautics$handleContraption(BlockHitResult rayTraceResult, Level world,
                                                   LivingEntity shooter, SpellStats spellStats,
                                                   SpellContext spellContext, SpellResolver resolver,
                                                   CallbackInfo ci) {
        if (world.isClientSide) return;

        Vec3 hitPos = rayTraceResult.getLocation();
        if (!ContraptionPushHelper.isNearContraption(world, hitPos)) return;

        // Try player offhand first
        WarpScrollData scrollData = null;
        ItemStack offhand = shooter.getOffhandItem();
        if (offhand != null) {
            scrollData = offhand.get(DataComponentRegistry.WARP_SCROLL.get());
        }

        // If not found and caster is a turret, search adjacent inventories
        if ((scrollData == null || !scrollData.isValid())
                && spellContext.getCaster() instanceof TileCaster tileCaster) {
            SlotReference ref = tileCaster.getInvManager().findItem(
                    stack -> stack.has(DataComponentRegistry.WARP_SCROLL.get()),
                    InteractType.EXTRACT
            );
            if (!ref.isEmpty()) {
                ItemStack scrollStack = ref.getHandler().getStackInSlot(ref.getSlot());
                scrollData = scrollStack.get(DataComponentRegistry.WARP_SCROLL.get());
            }
        }

        if (scrollData == null || !scrollData.isValid()) return;

        if (ContraptionPushHelper.blinkContraption(world, shooter, hitPos, scrollData, spellStats)) {
            ci.cancel();
        }
    }
}
