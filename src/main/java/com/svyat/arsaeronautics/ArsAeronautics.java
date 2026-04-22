package com.svyat.arsaeronautics;

import com.hollingsworth.arsnouveau.api.registry.GlyphRegistry;
import com.svyat.arsaeronautics.spell.EffectTelekinesis;
import com.svyat.arsaeronautics.util.TelekinesisTracker;
import dev.ryanhcode.sable.platform.SableEventPlatform;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(ArsAeronautics.MODID)
public class ArsAeronautics {
    public static final String MODID = "ars_aeronautics";

    public ArsAeronautics(IEventBus modEventBus) {
        GlyphRegistry.registerSpell(EffectTelekinesis.INSTANCE);
        SableEventPlatform.INSTANCE.onPhysicsTick(TelekinesisTracker::physicsTick);
    }

    public static ResourceLocation prefix(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
