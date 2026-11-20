package com.czminimap.world;

import com.czminimap.cartography.CartographicMap;
import com.czminimap.cartography.DiscoveryState;
import com.czminimap.cartography.VoxelAppearance;
import com.czminimap.core.DimensionKey;
import com.czminimap.core.WorldKey;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockUpdateSynchronizerTest {
    private static final WorldKey WORLD = new WorldKey("test_world_123456789abc", "Test World");
    private static final DimensionKey OVERWORLD = new DimensionKey(
            ResourceLocation.parse("minecraft:overworld"), -64, 320);
    private static final VoxelAppearance STONE = new VoxelAppearance(
            ResourceLocation.parse("minecraft:stone"), 0, ResourceLocation.parse("minecraft:plains"), 0);
    private static final VoxelAppearance DIRT = new VoxelAppearance(
            ResourceLocation.parse("minecraft:dirt"), 0, ResourceLocation.parse("minecraft:plains"), 0);

    @Test
    void neverTurnsUnknownClientDataIntoDiscoveredTerrain() {
        CartographicMap map = new CartographicMap();

        boolean changed = BlockUpdateSynchronizer.applyObservation(map, WORLD, OVERWORLD,
                new VoxelSample(3, 64, 4, false, STONE), 1L);

        assertFalse(changed);
        assertEquals(DiscoveryState.UNKNOWN, map.stateAt(WORLD, OVERWORLD, 3, 64, 4));
    }

    @Test
    void receivedRemovalUpdatesKnownVoxelAndSurfaceIndex() {
        CartographicMap map = new CartographicMap();
        map.setVoxel(WORLD, OVERWORLD, 3, 64, 4, DiscoveryState.EXPOSED_SURFACE, STONE, 1L);

        boolean changed = BlockUpdateSynchronizer.applyObservation(map, WORLD, OVERWORLD,
                new VoxelSample(3, 64, 4, true, null), 2L);

        assertTrue(changed);
        assertEquals(DiscoveryState.KNOWN_AIR, map.stateAt(WORLD, OVERWORLD, 3, 64, 4));
        assertNull(map.snapshot().surfaceAt(WORLD, OVERWORLD, 3, 4));
    }

    @Test
    void receivedPlacementUpdatesKnownAirAndAppearance() {
        CartographicMap map = new CartographicMap();
        map.setVoxel(WORLD, OVERWORLD, 3, 64, 4, DiscoveryState.KNOWN_AIR, null, 1L);

        boolean changed = BlockUpdateSynchronizer.applyObservation(map, WORLD, OVERWORLD,
                new VoxelSample(3, 64, 4, false, DIRT), 2L);

        assertTrue(changed);
        assertEquals(DiscoveryState.EXPOSED_SURFACE, map.stateAt(WORLD, OVERWORLD, 3, 64, 4));
        assertEquals(DIRT, map.snapshot().appearanceAt(WORLD, OVERWORLD, 3, 64, 4).orElseThrow());
    }
}
