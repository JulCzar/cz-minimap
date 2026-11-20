package com.czminimap.rendering.worldmap;

import com.czminimap.cartography.CartographicMap;
import com.czminimap.cartography.CartographicSnapshot;
import com.czminimap.cartography.DiscoveryState;
import com.czminimap.cartography.VoxelAppearance;
import com.czminimap.core.DimensionKey;
import com.czminimap.core.WorldKey;
import com.czminimap.cutaway.CutawaySettings;
import com.czminimap.layers.CumulativeDirection;
import com.czminimap.layers.LayerMode;
import com.czminimap.layers.LayerResolver;
import com.czminimap.layers.LayerVisibility;
import com.czminimap.lod.LodLevel;
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

        assertEquals(2, cache.size());
        assertFalse(cache.needsRebuild(snapshot, 0, 0));
        assertFalse(cache.needsRebuild(snapshot, 1, 0));
        assertTrue(cache.needsRebuild(snapshot, 2, 0));
    }

    @Test
    void completeSquareRadiusNeverExceedsCacheLimit() {
        assertEquals(2, WorldMapMeshCache.fitRadiusToLimit(8, 32));
        assertEquals(3, WorldMapMeshCache.fitRadiusToLimit(8, 64));
        assertEquals(8, WorldMapMeshCache.fitRadiusToLimit(8, 512));
        assertEquals(0, WorldMapMeshCache.fitRadiusToLimit(3, 1));
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

    @Test
    void reusesMeshForEqualPresentationState() {
        WorldMapMeshCache cache = new WorldMapMeshCache();
        CartographicMap map = mapWithStone();
        CartographicSnapshot snapshot = map.snapshot();
        LayerVisibility firstView = view(LayerMode.SELECTED, 8);
        LayerVisibility equalView = view(LayerMode.SELECTED, 8);

        TerrainMesh first = cache.get(snapshot, WORLD, OVERWORLD, firstView, 0, 0);
        TerrainMesh second = cache.get(snapshot, WORLD, OVERWORLD, equalView, 0, 0);

        assertSame(first, second);
        assertFalse(cache.needsRebuild(snapshot, equalView, 0, 0));
    }

    @Test
    void presentationChangeRebuildsWithoutChangingCartographicVersion() {
        WorldMapMeshCache cache = new WorldMapMeshCache();
        CartographicMap map = mapWithStone();
        CartographicSnapshot snapshot = map.snapshot();
        long mapVersion = map.version();
        LayerVisibility full = view(LayerMode.FULL, 8);
        LayerVisibility selected = view(LayerMode.SELECTED, 8);

        TerrainMesh first = cache.get(snapshot, WORLD, OVERWORLD, full, 0, 0);
        assertTrue(cache.needsRebuild(snapshot, selected, 0, 0));
        TerrainMesh second = cache.get(snapshot, WORLD, OVERWORLD, selected, 0, 0);

        assertNotSame(first, second);
        assertEquals(mapVersion, map.version());
        assertFalse(cache.needsRebuild(snapshot, selected, 0, 0));
    }

    @Test
    void meshCollectionExcludesEntriesFromOtherViews() {
        WorldMapMeshCache cache = new WorldMapMeshCache();
        CartographicSnapshot snapshot = mapWithStone().snapshot();
        LayerVisibility full = view(LayerMode.FULL, 8);
        LayerVisibility selected = view(LayerMode.SELECTED, 8);

        cache.get(snapshot, WORLD, OVERWORLD, full, 0, 0);
        cache.get(snapshot, WORLD, OVERWORLD, selected, 1, 0);

        assertEquals(1, cache.meshes(snapshot.version(), full).size());
        assertEquals(1, cache.meshes(snapshot.version(), selected).size());
    }

    @Test
    void distantCartographicChangeDoesNotInvalidateLocalLodMesh() {
        WorldMapMeshCache cache = new WorldMapMeshCache();
        CartographicMap map = mapWithStone();
        LayerVisibility full = view(LayerMode.FULL, 8);
        CartographicSnapshot firstSnapshot = map.snapshot();
        TerrainMesh first = cache.get(firstSnapshot, WORLD, OVERWORLD,
                full, LodLevel.LOD_2, 0, 0);

        map.setVoxel(WORLD, OVERWORLD, 160, 64, 0, DiscoveryState.SOLID, STONE, 2L);
        CartographicSnapshot secondSnapshot = map.snapshot();

        assertFalse(cache.needsRebuild(secondSnapshot, WORLD, OVERWORLD,
                full, LodLevel.LOD_2, 0, 0));
        assertSame(first, cache.get(secondSnapshot, WORLD, OVERWORLD,
                full, LodLevel.LOD_2, 0, 0));
    }

    @Test
    void neighbourChangeInvalidatesBoundaryCullingMesh() {
        WorldMapMeshCache cache = new WorldMapMeshCache();
        CartographicMap map = mapWithStone();
        LayerVisibility full = view(LayerMode.FULL, 8);
        CartographicSnapshot firstSnapshot = map.snapshot();
        cache.get(firstSnapshot, WORLD, OVERWORLD, full, LodLevel.LOD_1, 0, 0);

        map.setVoxel(WORLD, OVERWORLD, 16, 64, 0, DiscoveryState.SOLID, STONE, 2L);
        CartographicSnapshot secondSnapshot = map.snapshot();

        assertTrue(cache.needsRebuild(secondSnapshot, WORLD, OVERWORLD,
                full, LodLevel.LOD_1, 0, 0));
    }

    @Test
    void validPreviousLevelRemainsAvailableDuringLodTransition() {
        WorldMapMeshCache cache = new WorldMapMeshCache();
        CartographicSnapshot snapshot = mapWithStone().snapshot();
        LayerVisibility full = view(LayerMode.FULL, 8);
        TerrainMesh detailed = cache.get(snapshot, WORLD, OVERWORLD,
                full, LodLevel.LOD_0, 0, 0);

        assertTrue(cache.needsRebuild(snapshot, WORLD, OVERWORLD,
                full, LodLevel.LOD_3, 0, 0));
        assertSame(detailed, cache.getFallback(snapshot, WORLD, OVERWORLD, full, 0, 0));
    }

    @Test
    void asynchronousRequestPublishesMeshBuiltFromSnapshot() throws InterruptedException {
        WorldMapMeshCache cache = new WorldMapMeshCache();
        try {
            CartographicSnapshot snapshot = mapWithStone().snapshot();
            LayerVisibility full = view(LayerMode.FULL, 8);
            assertTrue(cache.request(snapshot, WORLD, OVERWORLD,
                    full, LodLevel.LOD_2, 0, 0));

            long deadline = System.nanoTime() + 2_000_000_000L;
            TerrainMesh mesh = null;
            while (mesh == null && System.nanoTime() < deadline) {
                cache.drainCompleted(8);
                mesh = cache.getIfReady(snapshot, WORLD, OVERWORLD,
                        full, LodLevel.LOD_2, 0, 0);
                if (mesh == null) {
                    Thread.sleep(5L);
                }
            }

            assertTrue(mesh != null);
            assertEquals(LodLevel.LOD_2, mesh.lodLevel());
        } finally {
            cache.close();
        }
    }

    private static CartographicMap mapWithStone() {
        CartographicMap map = new CartographicMap();
        map.setVoxel(WORLD, OVERWORLD, 0, 64, 0, DiscoveryState.SOLID, STONE, 1L);
        return map;
    }

    private static LayerVisibility view(LayerMode mode, int selectedIndex) {
        return LayerResolver.resolve(OVERWORLD, 16, mode, selectedIndex,
                CumulativeDirection.DOWN, CutawaySettings.none());
    }
}
