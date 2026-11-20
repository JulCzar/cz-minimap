package com.czminimap.client;

import com.czminimap.CzMinimap;
import com.czminimap.cartography.CartographicMap;
import com.czminimap.cartography.RegionData;
import com.czminimap.config.MapConfig;
import com.czminimap.core.DimensionKey;
import com.czminimap.core.MapManager;
import com.czminimap.core.RegionKey;
import com.czminimap.core.WorldKey;
import com.czminimap.discovery.DiscoveryEngine;
import com.czminimap.discovery.DiscoverySettings;
import com.czminimap.rendering.minimap.MinimapRenderer;
import com.czminimap.rendering.worldmap.WorldMapRenderer;
import com.czminimap.storage.MapStorage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.player.Player;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Client-only composition root for the shared cartographic data, its producers and its persistence. */
public final class ClientMapContext {
    private static final ClientMapContext INSTANCE = new ClientMapContext();

    private final MapManager mapManager = new MapManager();
    private final DiscoveryEngine discoveryEngine = new DiscoveryEngine(mapManager);
    private final MinimapRenderer minimapRenderer = new MinimapRenderer();
    private final WorldMapRenderer worldMapRenderer = new WorldMapRenderer();
    private final Set<RegionKey> pendingDirty = new HashSet<>();

    private MapStorage storage;
    private boolean storageFailed;
    private boolean loadPending;
    private long pendingLoadToken;
    private long lastFlushMillis;

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

    public MinimapRenderer minimapRenderer() {
        return minimapRenderer;
    }

    public WorldMapRenderer worldMapRenderer() {
        return worldMapRenderer;
    }

    public void onClientTick(Minecraft minecraft) {
        ClientLevel level = minecraft.level;
        Player player = minecraft.player;
        if (level == null || player == null) {
            return;
        }

        MapStorage mapStorage = storage();
        WorldKey world = WorldIdentityResolver.resolveWorld(minecraft, MapConfig.CONFIG.worldIdentityMode.get());
        DimensionKey dimension = WorldIdentityResolver.resolveDimension(level);

        if (!world.equals(mapManager.activeWorld())) {
            if (mapStorage != null) {
                flushPending(mapStorage);
                mapStorage.flush();
            }
            mapManager.selectWorld(world);
            discoveryEngine.reset();
            minimapRenderer.invalidate();
            worldMapRenderer.invalidate();
            pendingDirty.clear();
            lastFlushMillis = System.currentTimeMillis();
            if (mapStorage != null) {
                pendingLoadToken = mapStorage.requestLoad(world);
                loadPending = true;
            }
        }

        applyLoaded(mapStorage, world, dimension);
        if (loadPending) {
            return;
        }

        discoveryEngine.tick(level, player, world, dimension, DiscoverySettings.fromConfig());
        pendingDirty.addAll(mapManager.map().drainDirtyRegions());

        long now = System.currentTimeMillis();
        long interval = MapConfig.CONFIG.flushIntervalSeconds.get() * 1000L;
        if (mapStorage != null && now - lastFlushMillis >= interval) {
            flushPending(mapStorage);
            lastFlushMillis = now;
        }
    }

    private void applyLoaded(MapStorage mapStorage, WorldKey world, DimensionKey dimension) {
        if (mapStorage == null) {
            return;
        }
        MapStorage.LoadBatch batch;
        while ((batch = mapStorage.pollLoaded()) != null) {
            if (batch.token() != pendingLoadToken) {
                continue;
            }
            for (MapStorage.LoadedRegion loaded : batch.regions()) {
                RegionKey savedKey = loaded.key();
                // Neither the display name nor runtime height limits are persisted: use the active ones
                // whenever the dimension id still matches.
                DimensionKey savedDimension = savedKey.dimension();
                DimensionKey target = savedDimension.id().equals(dimension.id()) ? dimension : savedDimension;
                RegionKey key = new RegionKey(world, target, savedKey.x(), savedKey.z());
                mapManager.map().loadRegion(key, loaded.data());
            }
            loadPending = false;
        }
    }

    private void flushPending(MapStorage mapStorage) {
        for (RegionKey key : List.copyOf(pendingDirty)) {
            RegionData data = mapManager.map().regionData(key);
            if (data.isEmpty() || mapStorage.enqueueSave(key, data)) {
                pendingDirty.remove(key);
            }
        }
    }

    private MapStorage storage() {
        if (storage != null || storageFailed) {
            return storage;
        }
        try {
            Path root = Minecraft.getInstance().gameDirectory.toPath().resolve("czminimap");
            storage = new MapStorage(root);
        } catch (RuntimeException exception) {
            CzMinimap.LOGGER.error("Map storage is unavailable; discovery will remain in memory", exception);
            storageFailed = true;
        }
        return storage;
    }

    public void onDisconnect() {
        if (storage == null) {
            return;
        }
        flushPending(storage);
        storage.flush();
        mapManager.reset();
        discoveryEngine.reset();
        minimapRenderer.invalidate();
        worldMapRenderer.invalidate();
        pendingDirty.clear();
        loadPending = false;
        pendingLoadToken = 0L;
    }

    public void onShutdown() {
        minimapRenderer.close();
        worldMapRenderer.invalidate();
        if (storage == null) {
            return;
        }
        flushPending(storage);
        storage.close();
    }
}
