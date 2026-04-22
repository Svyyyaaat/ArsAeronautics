package com.svyat.arsaeronautics;

import com.svyat.arsaeronautics.util.BlinkFreezeTracker;
import com.svyat.arsaeronautics.util.BlinkTracker;
import com.svyat.arsaeronautics.util.GravityTracker;
import com.svyat.arsaeronautics.util.LevitationTracker;
import com.svyat.arsaeronautics.util.SnareTracker;
import com.svyat.arsaeronautics.util.TelekinesisTracker;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = ArsAeronautics.MODID)
public class ServerEvents {

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        GravityTracker.tick();
        SnareTracker.tick();
        LevitationTracker.tick();
        BlinkTracker.tick();
        BlinkFreezeTracker.tick();
        TelekinesisTracker.tick();
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        GravityTracker.clear();
        SnareTracker.clear();
        LevitationTracker.clear();
        BlinkTracker.clear();
        BlinkFreezeTracker.clear();
        TelekinesisTracker.clear();
    }
}
