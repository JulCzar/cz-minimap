package com.czminimap.discovery;

import com.czminimap.cartography.DiscoveryState;
import com.czminimap.cartography.VoxelAppearance;
import com.czminimap.core.DimensionKey;
import com.czminimap.core.WorldKey;
import com.czminimap.world.VoxelSample;
import com.czminimap.world.WorldDataSnapshot;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DiscoveryPolicyTest {
    private static final VoxelAppearance STONE = new VoxelAppearance(
            ResourceLocation.parse("minecraft:stone"), 0, ResourceLocation.parse("minecraft:plains"), 0);

    @Test
    void surfaceColumnMarksTopAsExposedAndLowerBlocksAsSolid() {
        List<VoxelSample> column = List.of(
                sample(4, 70, -2),
                sample(4, 69, -2),
                sample(4, 68, -2));

        List<DiscoveryResult.VoxelUpdate> updates = DiscoveryPolicy.surfaceColumn(column).updates();

        assertEquals(3, updates.size());
        assertEquals(DiscoveryState.EXPOSED_SURFACE, updates.get(0).state());
        assertEquals(DiscoveryState.SOLID, updates.get(1).state());
        assertEquals(DiscoveryState.SOLID, updates.get(2).state());
        assertEquals(70, updates.get(0).y());
        assertEquals(STONE, updates.get(0).appearance());
    }

    @Test
    void airPocketSeparatesUndergroundAirFromKnownAir() {
        WorldKey world = new WorldKey("test_123456789abc", "Test");
        DimensionKey dimension = new DimensionKey(ResourceLocation.parse("minecraft:overworld"), -64, 320);
        WorldDataSnapshot observations = new WorldDataSnapshot(world, dimension, 3L, List.of(
                air(0, 20, 0), air(1, 70, 1), sample(0, 19, 0)));

        List<DiscoveryResult.VoxelUpdate> updates = DiscoveryPolicy.airPocket(
                observations, candidate -> candidate.y() < 50).updates();

        assertEquals(3, updates.size());
        assertEquals(DiscoveryState.UNDERGROUND_SPACE, updates.get(0).state());
        assertEquals(DiscoveryState.KNOWN_AIR, updates.get(1).state());
        assertEquals(DiscoveryState.SOLID, updates.get(2).state());
        assertNull(updates.get(0).appearance());
    }

    private static VoxelSample sample(int x, int y, int z) {
        return new VoxelSample(x, y, z, false, STONE);
    }

    private static VoxelSample air(int x, int y, int z) {
        return new VoxelSample(x, y, z, true, null);
    }
}
