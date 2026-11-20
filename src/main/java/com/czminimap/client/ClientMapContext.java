package com.czminimap.client;

import com.czminimap.cartography.CartographicMap;
import com.czminimap.config.MapConfig;
import com.czminimap.core.DimensionKey;
import com.czminimap.core.MapManager;
import com.czminimap.core.WorldKey;
import com.czminimap.discovery.DiscoveryEngine;
import com.czminimap.discovery.DiscoverySettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.player.Player;

/** Client-only composition root for the shared cartographic data and its producers. */
public final class ClientMapContext {
    private static final ClientMapContext INSTANCE = new ClientMapContext();

    private final MapManager mapManager = new MapManager();
    private final DiscoveryEngine discoveryEngine = new DiscoveryEngine(mapManager);

    private ClientMapContext() {
    }

    public static ClientMapContext get() {
        return INSTANCE;
    }

    public CartographicMap map() {
        return mapManager.map();
    }

    public MapManager mapManager() {
        return mapManager;
    }

    public void onClientTick(Minecraft minecraft) {
        ClientLevel level = minecraft.level;
        Player player = minecraft.player;
        if (level == null || player == null) {
            return;
        }
        WorldKey world = WorldIdentityResolver.resolveWorld(minecraft, MapConfig.CONFIG.worldIdentityMode.get());
        DimensionKey dimension = WorldIdentityResolver.resolveDimension(level);
        discoveryEngine.tick(level, player, world, dimension, DiscoverySettings.fromConfig());
    }
}
