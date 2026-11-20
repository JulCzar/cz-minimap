package com.czminimap;

import com.czminimap.config.ClientConfig;
import com.czminimap.config.MapConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

@Mod(CzMinimap.MOD_ID)
public final class CzMinimap {
    public static final String MOD_ID = "czminimap";

    public CzMinimap(ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC, "czminimap-hud-client.toml");
        container.registerConfig(ModConfig.Type.CLIENT, MapConfig.SPEC, "czminimap-map-client.toml");
    }
}
