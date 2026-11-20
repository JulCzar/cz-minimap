package com.czminimap.rendering.worldmap;

import com.czminimap.cartography.CartographicSnapshot;
import com.czminimap.core.DimensionKey;
import com.czminimap.core.WorldKey;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Disposable mesh cache keyed by chunk and snapshot version. Never stores cartographic data. */
public final class WorldMapMeshCache {
    private record ChunkKey(int x, int z) {
    }

    private final LinkedHashMap<ChunkKey, TerrainMesh> meshes = new LinkedHashMap<>(64, 0.75f, true);
    private int limit = 256;

    public void setLimit(int newLimit) {
        limit = Math.max(16, newLimit);
        evict();
    }

    public boolean needsRebuild(CartographicSnapshot snapshot, int chunkX, int chunkZ) {
        TerrainMesh cached = meshes.get(new ChunkKey(chunkX, chunkZ));
        return cached == null || cached.version() != snapshot.version();
    }

    public TerrainMesh get(CartographicSnapshot snapshot, WorldKey world, DimensionKey dimension,
                           int chunkX, int chunkZ) {
        ChunkKey key = new ChunkKey(chunkX, chunkZ);
        TerrainMesh cached = meshes.get(key);
        if (cached != null && cached.version() == snapshot.version()) {
            return cached;
        }
        TerrainMesh built = TerrainMeshBuilder.build(snapshot, world, dimension,
                chunkX, chunkZ, snapshot.version());
        meshes.put(key, built);
        evict();
        return built;
    }

    public Collection<TerrainMesh> meshes() {
        return meshes.values();
    }

    public int size() {
        return meshes.size();
    }

    /** Drops meshes outside the current view radius so distant chunks do not occupy the cache. */
    public void retainRadius(int centerX, int centerZ, int radius) {
        Iterator<ChunkKey> iterator = meshes.keySet().iterator();
        while (iterator.hasNext()) {
            ChunkKey key = iterator.next();
            int dx = key.x() - centerX;
            int dz = key.z() - centerZ;
            if (dx * dx + dz * dz > (radius + 1) * (radius + 1)) {
                iterator.remove();
            }
        }
    }

    public void clear() {
        meshes.clear();
    }

    private void evict() {
        Iterator<Map.Entry<ChunkKey, TerrainMesh>> iterator = meshes.entrySet().iterator();
        while (meshes.size() > limit && iterator.hasNext()) {
            iterator.next();
            iterator.remove();
        }
    }
}
