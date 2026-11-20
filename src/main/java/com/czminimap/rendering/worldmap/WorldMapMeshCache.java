package com.czminimap.rendering.worldmap;

import com.czminimap.cartography.CartographicSnapshot;
import com.czminimap.core.DimensionKey;
import com.czminimap.core.WorldKey;
import com.czminimap.cutaway.CutawaySettings;
import com.czminimap.layers.CumulativeDirection;
import com.czminimap.layers.LayerMode;
import com.czminimap.layers.LayerResolver;
import com.czminimap.layers.LayerVisibility;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Disposable mesh cache keyed by chunk and snapshot version. Never stores cartographic data. */
public final class WorldMapMeshCache {
    private record ChunkKey(int x, int z) {
    }

    private record Entry(TerrainMesh mesh, LayerVisibility visibility) {
    }

    private final LinkedHashMap<ChunkKey, Entry> meshes = new LinkedHashMap<>(64, 0.75f, true);
    private int limit = 256;

    public void setLimit(int newLimit) {
        limit = Math.max(16, newLimit);
        evict();
    }

    public boolean needsRebuild(CartographicSnapshot snapshot, int chunkX, int chunkZ) {
        Entry cached = meshes.get(new ChunkKey(chunkX, chunkZ));
        return cached == null
                || cached.mesh().version() != snapshot.version()
                || cached.visibility().mode() != LayerMode.FULL;
    }

    public boolean needsRebuild(CartographicSnapshot snapshot, LayerVisibility visibility,
                                int chunkX, int chunkZ) {
        Entry cached = meshes.get(new ChunkKey(chunkX, chunkZ));
        return cached == null
                || cached.mesh().version() != snapshot.version()
                || !cached.visibility().equals(visibility);
    }

    public TerrainMesh get(CartographicSnapshot snapshot, WorldKey world, DimensionKey dimension,
                           int chunkX, int chunkZ) {
        return get(snapshot, world, dimension, fullVisibility(dimension), chunkX, chunkZ);
    }

    public TerrainMesh get(CartographicSnapshot snapshot, WorldKey world, DimensionKey dimension,
                           LayerVisibility visibility, int chunkX, int chunkZ) {
        ChunkKey key = new ChunkKey(chunkX, chunkZ);
        Entry cached = meshes.get(key);
        if (cached != null && cached.mesh().version() == snapshot.version()
                && cached.visibility().equals(visibility)) {
            return cached.mesh();
        }
        TerrainMesh built = TerrainMeshBuilder.build(snapshot, world, dimension,
                chunkX, chunkZ, snapshot.version(), visibility);
        meshes.put(key, new Entry(built, visibility));
        evict();
        return built;
    }

    public Collection<TerrainMesh> meshes() {
        ArrayList<TerrainMesh> result = new ArrayList<>(meshes.size());
        for (Entry entry : meshes.values()) {
            result.add(entry.mesh());
        }
        return List.copyOf(result);
    }

    /** Returns only meshes built for the requested cartographic and presentation state. */
    public Collection<TerrainMesh> meshes(long version, LayerVisibility visibility) {
        ArrayList<TerrainMesh> result = new ArrayList<>(meshes.size());
        for (Entry entry : meshes.values()) {
            if (entry.mesh().version() == version && entry.visibility().equals(visibility)) {
                result.add(entry.mesh());
            }
        }
        return List.copyOf(result);
    }

    public int size() {
        return meshes.size();
    }

    /** Largest square radius whose complete chunk set fits in the configured cache limit. */
    public static int fitRadiusToLimit(int requestedRadius, int cacheLimit) {
        int radius = Math.max(0, requestedRadius);
        int capacity = Math.max(1, cacheLimit);
        while (radius > 0) {
            long diameter = radius * 2L + 1L;
            if (diameter * diameter <= capacity) {
                break;
            }
            radius--;
        }
        return radius;
    }

    /** Drops meshes outside the current view radius so distant chunks do not occupy the cache. */
    public void retainRadius(int centerX, int centerZ, int radius) {
        Iterator<ChunkKey> iterator = meshes.keySet().iterator();
        while (iterator.hasNext()) {
            ChunkKey key = iterator.next();
            int dx = key.x() - centerX;
            int dz = key.z() - centerZ;
            if (Math.abs(dx) > radius || Math.abs(dz) > radius) {
                iterator.remove();
            }
        }
    }

    public void clear() {
        meshes.clear();
    }

    private void evict() {
        Iterator<Map.Entry<ChunkKey, Entry>> iterator = meshes.entrySet().iterator();
        while (meshes.size() > limit && iterator.hasNext()) {
            iterator.next();
            iterator.remove();
        }
    }

    private static LayerVisibility fullVisibility(DimensionKey dimension) {
        return LayerResolver.resolve(dimension, 16, LayerMode.FULL, 0,
                CumulativeDirection.DOWN, CutawaySettings.none());
    }
}
