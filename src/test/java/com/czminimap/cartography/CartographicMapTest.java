package com.czminimap.cartography;

import com.czminimap.core.DimensionKey;
import com.czminimap.core.RegionKey;
import com.czminimap.core.WorldKey;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CartographicMapTest {
    private static final WorldKey WORLD = new WorldKey("test_world_123456789abc", "Test World");
    private static final DimensionKey OVERWORLD = new DimensionKey(
            ResourceLocation.parse("minecraft:overworld"), -64, 320);
    private static final VoxelAppearance STONE = new VoxelAppearance(
            ResourceLocation.parse("minecraft:stone"), 0, ResourceLocation.parse("minecraft:plains"), 0);

    @Test
    void unknownIsDistinctFromKnownAir() {
        CartographicMap map = new CartographicMap();
        CartographicSnapshot before = map.snapshot();
        assertEquals(DiscoveryState.UNKNOWN, before.stateAt(WORLD, OVERWORLD, 0, 0, 0));
        assertTrue(before.appearanceAt(WORLD, OVERWORLD, 0, 0, 0).isEmpty());

        map.setVoxel(WORLD, OVERWORLD, 0, 0, 0, DiscoveryState.KNOWN_AIR, null, 10L);
        CartographicSnapshot after = map.snapshot();
        assertEquals(DiscoveryState.KNOWN_AIR, after.stateAt(WORLD, OVERWORLD, 0, 0, 0));
        assertTrue(after.stateAt(WORLD, OVERWORLD, 0, 0, 0).isDiscovered());
        assertTrue(after.appearanceAt(WORLD, OVERWORLD, 0, 0, 0).isEmpty());
        assertEquals(DiscoveryState.UNKNOWN, after.stateAt(WORLD, OVERWORLD, 1, 0, 0));
        assertEquals(DiscoveryState.UNKNOWN, before.stateAt(WORLD, OVERWORLD, 0, 0, 0));
    }

    @Test
    void snapshotIsImmutableAndVersioned() {
        CartographicMap map = new CartographicMap();
        map.setVoxel(WORLD, OVERWORLD, 0, 0, 0, DiscoveryState.SOLID, STONE, 1L);
        CartographicSnapshot first = map.snapshot();

        map.setVoxel(WORLD, OVERWORLD, 1, 0, 0, DiscoveryState.EXPOSED_SURFACE, STONE, 2L);
        CartographicSnapshot second = map.snapshot();

        assertEquals(first.version() + 1, second.version());
        assertEquals(DiscoveryState.UNKNOWN, first.stateAt(WORLD, OVERWORLD, 1, 0, 0));
        assertEquals(DiscoveryState.EXPOSED_SURFACE, second.stateAt(WORLD, OVERWORLD, 1, 0, 0));
        assertThrows(UnsupportedOperationException.class, () -> second.regions().clear());
    }

    @Test
    void repeatedWriteDoesNotBumpVersionOrMarkDirty() {
        CartographicMap map = new CartographicMap();
        map.setVoxel(WORLD, OVERWORLD, 4, -4, -8, DiscoveryState.UNDERGROUND_SPACE, STONE, 7L);
        assertEquals(1L, map.version());
        assertTrue(map.drainDirtyRegions().size() == 1);
        assertFalse(map.isDirty());

        map.setVoxel(WORLD, OVERWORLD, 4, -4, -8, DiscoveryState.UNDERGROUND_SPACE, STONE, 9L);
        assertEquals(1L, map.version());
        assertFalse(map.isDirty());

        CartographicSnapshot snapshot = map.snapshot();
        assertEquals(DiscoveryState.UNDERGROUND_SPACE, snapshot.stateAt(WORLD, OVERWORLD, 4, -4, -8));
        assertEquals(Optional.of(STONE), snapshot.appearanceAt(WORLD, OVERWORLD, 4, -4, -8));
        assertEquals(9L, snapshot.lastObservedTickAt(WORLD, OVERWORLD, 4, -4, -8));
    }

    @Test
    void respectsDimensionHeightLimits() {
        CartographicMap map = new CartographicMap();
        assertThrows(IllegalArgumentException.class, () ->
                map.setVoxel(WORLD, OVERWORLD, 0, -65, 0, DiscoveryState.SOLID, STONE, 0L));
        assertThrows(IllegalArgumentException.class, () ->
                map.setVoxel(WORLD, OVERWORLD, 0, 320, 0, DiscoveryState.SOLID, STONE, 0L));

        DimensionKey end = new DimensionKey(ResourceLocation.parse("minecraft:the_end"), 0, 256);
        map.setVoxel(WORLD, end, 0, 0, 0, DiscoveryState.SOLID, STONE, 0L);
        assertEquals(DiscoveryState.SOLID, map.snapshot().stateAt(WORLD, end, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () ->
                map.setVoxel(WORLD, end, 0, 256, 0, DiscoveryState.SOLID, STONE, 0L));
    }

    @Test
    void pointLookupAvoidsFullSnapshots() {
        CartographicMap map = new CartographicMap();
        assertEquals(DiscoveryState.UNKNOWN, map.stateAt(WORLD, OVERWORLD, 0, 0, 0));

        map.setVoxel(WORLD, OVERWORLD, 17, -3, -18, DiscoveryState.EXPOSED_SURFACE, STONE, 4L);

        assertEquals(DiscoveryState.EXPOSED_SURFACE, map.stateAt(WORLD, OVERWORLD, 17, -3, -18));
        assertEquals(DiscoveryState.UNKNOWN, map.stateAt(WORLD, OVERWORLD, 18, -3, -18));
        assertEquals(DiscoveryState.UNKNOWN, map.stateAt(WORLD, OVERWORLD, 17, -2, -18));
    }

    @Test
    void writesUnknownWithoutChangingTheModel() {
        CartographicMap map = new CartographicMap();
        map.setVoxel(WORLD, OVERWORLD, 0, 0, 0, DiscoveryState.UNKNOWN, null, 1L);
        assertEquals(0L, map.version());
        assertFalse(map.isDirty());
        assertTrue(map.snapshot().regions().isEmpty());
    }

    @Test
    void surfaceIndexTracksHighestDiscoveredSurface() {
        CartographicMap map = new CartographicMap();
        CartographicSnapshot before = map.snapshot();
        assertNull(before.surfaceAt(WORLD, OVERWORLD, 3, 4));

        map.setVoxel(WORLD, OVERWORLD, 3, 60, 4, DiscoveryState.SOLID, STONE, 1L);
        map.setVoxel(WORLD, OVERWORLD, 3, 62, 4, DiscoveryState.EXPOSED_SURFACE, STONE, 2L);
        map.setVoxel(WORLD, OVERWORLD, 3, 61, 4, DiscoveryState.KNOWN_AIR, null, 3L);
        map.setVoxel(WORLD, OVERWORLD, 4, 10, 4, DiscoveryState.UNDERGROUND_SPACE, null, 4L);

        CartographicSnapshot after = map.snapshot();
        CartographicSnapshot.SurfaceColumn column = after.surfaceAt(WORLD, OVERWORLD, 3, 4);
        assertNotNull(column);
        assertEquals(62, column.y());
        assertEquals(DiscoveryState.EXPOSED_SURFACE, column.state());
        assertEquals(STONE, column.appearance());
        assertNull(after.surfaceAt(WORLD, OVERWORLD, 4, 4));
        assertNull(before.surfaceAt(WORLD, OVERWORLD, 3, 4));
    }

    @Test
    void surfaceIndexIsRebuiltFromPersistedData() {
        CartographicMap source = new CartographicMap();
        source.setVoxel(WORLD, OVERWORLD, 5, 70, 6, DiscoveryState.EXPOSED_SURFACE, STONE, 11L);
        RegionKey key = new RegionKey(WORLD, OVERWORLD, RegionKey.fromBlock(5), RegionKey.fromBlock(6));
        RegionData data = source.regionData(key);

        CartographicMap restored = new CartographicMap();
        restored.loadRegion(key, data);
        CartographicSnapshot.SurfaceColumn column = restored.snapshot().surfaceAt(WORLD, OVERWORLD, 5, 6);
        assertNotNull(column);
        assertEquals(70, column.y());
        assertEquals(STONE, column.appearance());

        byte[] states = new byte[RegionData.SectionData.VOXELS];
        int[] palette = new int[RegionData.SectionData.VOXELS];
        Arrays.fill(palette, -1);
        int index = ((70 & 15) << 8) | ((6 & 15) << 4) | (5 & 15);
        states[index] = (byte) DiscoveryState.EXPOSED_SURFACE.ordinal();
        palette[index] = 0;
        RegionData crafted = new RegionData(java.util.List.of(STONE), Map.of(
                CartographicSnapshot.chunkKey(0, 0),
                new RegionData.ChunkData(Map.of(4, new RegionData.SectionData(states, palette, 12L)))));
        RegionKey otherWorldKey = new RegionKey(new WorldKey("other_123456789abc", "Other"),
                OVERWORLD, 0, 0);
        CartographicMap isolated = new CartographicMap();
        isolated.loadRegion(otherWorldKey, crafted);
        assertNull(isolated.snapshot().surfaceAt(WORLD, OVERWORLD, 5, 6));
    }
}
