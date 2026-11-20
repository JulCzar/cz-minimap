package com.czminimap.client;

import com.czminimap.CzMinimap;
import com.czminimap.input.KeyBindings;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

@EventBusSubscriber(modid = CzMinimap.MOD_ID, value = Dist.CLIENT)
public final class ClientModEvents {
    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        KeyBindings.register(event);
    }
}
