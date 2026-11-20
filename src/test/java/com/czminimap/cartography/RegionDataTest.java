package com.czminimap.cartography;

import com.czminimap.core.DimensionKey;
import com.czminimap.core.RegionKey;
import com.czminimap.core.WorldKey;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionDataTest {
    private static final WorldKey WORLD = new WorldKey("test_world_123456789abc", "Test World");
    private static final DimensionKey OVERWORLD = new DimensionKey(
            ResourceLocation.parse("minecraft:overworld"), -64, 320);
    private static final RegionKey REGION = new RegionKey(WORLD, OVERWORLD, 0, 0);
    private static final VoxelAppearance STONE = new VoxelAppearance(
            ResourceLocation.parse("minecraft:stone"), 1, ResourceLocation.parse("minecraft:plains"), 0x556633);
    private static final VoxelAppearance DIRT = new VoxelAppearance(
            ResourceLocation.parse("minecraft:dirt"), 0, ResourceLocation.parse("minecraft:forest"), 0x336622);

    @Test
    void regionDataRoundTripRestoresStatesAndPalette() {
        CartographicMap source = new CartographicMap();
        source.setVoxel(WORLD, OVERWORLD, 0, 0, 0, DiscoveryState.EXPOSED_SURFACE, STONE, 40L);
        source.setVoxel(WORLD, OVERWORLD, 1, 0, 0, DiscoveryState.SOLID, DIRT, 41L);
        source.setVoxel(WORLD, OVERWORLD, 2, 0, 0, DiscoveryState.KNOWN_AIR, null, 42L);

        RegionData data = source.regionData(REGION);
        assertFalse(data.isEmpty());

        CartographicMap restored = new CartographicMap();
        restored.loadRegion(REGION, data);

        CartographicSnapshot snapshot = restored.snapshot();
        assertEquals(DiscoveryState.EXPOSED_SURFACE, snapshot.stateAt(WORLD, OVERWORLD, 0, 0, 0));
        assertEquals(DiscoveryState.SOLID, snapshot.stateAt(WORLD, OVERWORLD, 1, 0, 0));
        assertEquals(DiscoveryState.KNOWN_AIR, snapshot.stateAt(WORLD, OVERWORLD, 2, 0, 0));
        assertEquals(Optional.of(STONE), snapshot.appearanceAt(WORLD, OVERWORLD, 0, 0, 0));
        assertEquals(Optional.of(DIRT), snapshot.appearanceAt(WORLD, OVERWORLD, 1, 0, 0));
        assertTrue(snapshot.appearanceAt(WORLD, OVERWORLD, 2, 0, 0).isEmpty());
        assertEquals(42L, restored.clock());
        assertFalse(restored.isDirty());
    }

    @Test
    void loadDoesNotOverwriteNewerObservations() {
        CartographicMap map = new CartographicMap();
        map.setVoxel(WORLD, OVERWORLD, 0, 0, 0, DiscoveryState.EXPOSED_SURFACE, STONE, 10L);
        RegionData older = map.regionData(REGION);

        map.setVoxel(WORLD, OVERWORLD, 0, 0, 0, DiscoveryState.KNOWN_AIR, null, 11L);
        map.loadRegion(REGION, older);

        assertEquals(DiscoveryState.KNOWN_AIR, map.snapshot().stateAt(WORLD, OVERWORLD, 0, 0, 0));
        assertEquals(11L, map.clock());
    }

    @Test
    void regionDataIsDetachedFromLiveVoxels() {
        CartographicMap map = new CartographicMap();
        map.setVoxel(WORLD, OVERWORLD, 0, 0, 0, DiscoveryState.SOLID, STONE, 1L);

        RegionData data = map.regionData(REGION);
        RegionData.ChunkData chunk = data.chunks().values().iterator().next();
        RegionData.SectionData section = chunk.sections().values().iterator().next();
        byte[] states = section.states();
        states[0] = 0;
        section.paletteIndices()[0] = -1;

        assertNotEquals(0, map.snapshot().stateAt(WORLD, OVERWORLD, 0, 0, 0).ordinal());
        assertEquals(1, data.chunks().size());
    }

    @Test
    void emptyRegionHasNoChunks() {
        CartographicMap map = new CartographicMap();
        assertTrue(map.regionData(REGION).isEmpty());
        map.loadRegion(REGION, RegionData.empty());
        assertTrue(map.snapshot().regions().isEmpty());
    }
}
