package com.czminimap.rendering.minimap;

import com.czminimap.cartography.CartographicMap;
import com.czminimap.cartography.CartographicSnapshot;
import com.czminimap.cartography.DiscoveryState;
import com.czminimap.cartography.VoxelAppearance;
import com.czminimap.core.DimensionKey;
import com.czminimap.core.WorldKey;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MinimapImageTest {
    private static final WorldKey WORLD = new WorldKey("test_world_123456789abc", "Test World");
    private static final DimensionKey OVERWORLD = new DimensionKey(
            ResourceLocation.parse("minecraft:overworld"), -64, 320);
    private static final VoxelAppearance GRASS = new VoxelAppearance(
            ResourceLocation.parse("minecraft:grass_block"), 0,
            ResourceLocation.parse("minecraft:plains"), 0);

    @Test
    void samplesDiscoveredSurfaceAndLeavesUnknownSpaceDistinct() {
        CartographicMap map = new CartographicMap();
        map.setVoxel(WORLD, OVERWORLD, 0, 64, 0, DiscoveryState.EXPOSED_SURFACE, GRASS, 1L);
        CartographicSnapshot snapshot = map.snapshot();

        MinimapImage image = MinimapImage.build(snapshot, WORLD, OVERWORLD, 0, 0, 8, 8, 1.0, false);

        assertEquals(snapshot.version(), image.snapshotVersion());
        assertEquals(-4, image.originX());
        assertEquals(-4, image.originZ());
        assertEquals(MinimapColorResolver.color(GRASS), image.pixels()[4 + 4 * 8]);
        assertEquals(MinimapColorResolver.unknown(), image.pixels()[1 + 1 * 8]);
    }

    @Test
    void circularMaskRemovesCornersWithoutRevealingData() {
        CartographicMap map = new CartographicMap();
        CartographicSnapshot snapshot = map.snapshot();
        MinimapImage image = MinimapImage.build(snapshot, WORLD, OVERWORLD, 0, 0, 16, 16, 1.0, true);

        assertEquals(0, image.pixels()[0]);
        assertEquals(0, image.pixels()[15 + 15 * 16]);
        assertEquals(MinimapColorResolver.unknown(), image.pixels()[8 + 8 * 16]);
    }

    @Test
    void squareMaskDrawsAFrameAndRespectsFractionalZoom() {
        CartographicMap map = new CartographicMap();
        map.setVoxel(WORLD, OVERWORLD, 4, 40, -2, DiscoveryState.SOLID, GRASS, 1L);
        CartographicSnapshot snapshot = map.snapshot();

        MinimapImage image = MinimapImage.build(snapshot, WORLD, OVERWORLD, 10, -6, 8, 8, 2.0, false);

        assertEquals(2, image.originX());
        assertEquals(-14, image.originZ());
        assertEquals(0xFFE8E8E8, image.pixels()[0]);
        assertEquals(MinimapColorResolver.color(GRASS), image.pixels()[1 + 6 * 8]);
    }
}
