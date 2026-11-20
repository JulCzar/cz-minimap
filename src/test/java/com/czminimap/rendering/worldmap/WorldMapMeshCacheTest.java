package com.czminimap.rendering.worldmap;

import com.czminimap.cartography.CartographicMap;
import com.czminimap.cartography.CartographicSnapshot;
import com.czminimap.cartography.DiscoveryState;
import com.czminimap.cartography.VoxelAppearance;
import com.czminimap.core.DimensionKey;
import com.czminimap.core.WorldKey;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldMapMeshCacheTest {
    private static final WorldKey WORLD = new WorldKey("test_world_123456789abc", "Test World");
    private static final DimensionKey OVERWORLD = new DimensionKey(
            ResourceLocation.parse("minecraft:overworld"), -64, 320);
    private static final VoxelAppearance STONE = new VoxelAppearance(
            ResourceLocation.parse("minecraft:stone"), 0, ResourceLocation.parse("minecraft:plains"), 0);

    @Test
    void reusesMeshForSameSnapshotVersion() {
        WorldMapMeshCache cache = new WorldMapMeshCache();
        CartographicMap map = new CartographicMap();
        map.setVoxel(WORLD, OVERWORLD, 0, 64, 0, DiscoveryState.SOLID, STONE, 1L);
        CartographicSnapshot snapshot = map.snapshot();

        TerrainMesh first = cache.get(snapshot, WORLD, OVERWORLD, 0, 0);
        TerrainMesh second = cache.get(snapshot, WORLD, OVERWORLD, 0, 0);

        assertSame(first, second);
        assertFalse(cache.needsRebuild(snapshot, 0, 0));
        assertEquals(1, cache.size());
    }

    @Test
    void newSnapshotVersionInvalidatesMesh() {
        WorldMapMeshCache cache = new WorldMapMeshCache();
        CartographicMap map = new CartographicMap();
        map.setVoxel(WORLD, OVERWORLD, 0, 64, 0, DiscoveryState.SOLID, STONE, 1L);
        CartographicSnapshot firstSnapshot = map.snapshot();
        TerrainMesh first = cache.get(firstSnapshot, WORLD, OVERWORLD, 0, 0);

        map.setVoxel(WORLD, OVERWORLD, 1, 64, 0, DiscoveryState.SOLID, STONE, 2L);
        CartographicSnapshot secondSnapshot = map.snapshot();

        assertTrue(cache.needsRebuild(secondSnapshot, 0, 0));
        TerrainMesh second = cache.get(secondSnapshot, WORLD, OVERWORLD, 0, 0);
        assertNotSame(first, second);
        assertEquals(secondSnapshot.version(), second.version());
    }

    @Test
    void cacheLimitEvictsLeastRecentlyUsed() {
        WorldMapMeshCache cache = new WorldMapMeshCache();
        cache.setLimit(16);
        CartographicSnapshot snapshot = new CartographicMap().snapshot();

        for (int i = 0; i < 20; i++) {
            cache.get(snapshot, WORLD, OVERWORLD, i, 0);
        }
        assertEquals(16, cache.size());
        assertTrue(cache.needsRebuild(snapshot, 0, 0));
        assertFalse(cache.needsRebuild(snapshot, 19, 0));
    }

    @Test
    void retainRadiusDropsDistantChunks() {
        WorldMapMeshCache cache = new WorldMapMeshCache();
        CartographicSnapshot snapshot = new CartographicMap().snapshot();
        for (int i = 0; i < 6; i++) {
            cache.get(snapshot, WORLD, OVERWORLD, i, 0);
        }

        cache.retainRadius(0, 0, 1);

        assertEquals(3, cache.size());
        assertFalse(cache.needsRebuild(snapshot, 0, 0));
        assertFalse(cache.needsRebuild(snapshot, 2, 0));
        assertTrue(cache.needsRebuild(snapshot, 3, 0));
    }

    @Test
    void clearRemovesAllMeshes() {
        WorldMapMeshCache cache = new WorldMapMeshCache();
        CartographicSnapshot snapshot = new CartographicMap().snapshot();
        cache.get(snapshot, WORLD, OVERWORLD, 0, 0);

        cache.clear();

        assertEquals(0, cache.size());
        assertTrue(cache.needsRebuild(snapshot, 0, 0));
    }
}
