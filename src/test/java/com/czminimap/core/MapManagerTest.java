package com.czminimap.core;

import com.czminimap.cartography.CartographicMap;
import com.czminimap.cartography.DiscoveryState;
import com.czminimap.cartography.VoxelAppearance;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapManagerTest {
    private static final WorldKey FIRST = new WorldKey("first_123456789abc", "First");
    private static final WorldKey SECOND = new WorldKey("second_123456789abc", "Second");
    private static final DimensionKey OVERWORLD = new DimensionKey(
            ResourceLocation.parse("minecraft:overworld"), -64, 320);
    private static final VoxelAppearance STONE = new VoxelAppearance(
            ResourceLocation.parse("minecraft:stone"), 0, ResourceLocation.parse("minecraft:plains"), 0);

    @Test
    void keepsTheSameMapForTheSameWorldAndIsolatesDifferentWorlds() {
        MapManager manager = new MapManager();
        CartographicMap initial = manager.map();

        assertTrue(manager.selectWorld(FIRST));
        CartographicMap first = manager.map();
        first.setVoxel(FIRST, OVERWORLD, 0, 0, 0, DiscoveryState.SOLID, STONE, 1L);

        assertTrue(!manager.selectWorld(FIRST));
        assertSame(first, manager.map());

        assertTrue(manager.selectWorld(SECOND));
        assertNotSame(initial, manager.map());
        assertNotSame(first, manager.map());
        assertTrue(!manager.map().isDirty());
    }
}
