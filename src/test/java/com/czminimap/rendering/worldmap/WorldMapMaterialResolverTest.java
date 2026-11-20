package com.czminimap.rendering.worldmap;

import com.czminimap.cartography.VoxelAppearance;
import com.czminimap.rendering.minimap.MinimapColorResolver;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldMapMaterialResolverTest {
    private static final VoxelAppearance GRASS = new VoxelAppearance(
            ResourceLocation.parse("minecraft:grass_block"), 0,
            ResourceLocation.parse("minecraft:plains"), 0);

    @Test
    void topFaceUsesUnshadedMinimapColour() {
        int base = MinimapColorResolver.color(GRASS);
        assertEquals(base, WorldMapMaterialResolver.color(GRASS, Direction.UP));
    }

    @Test
    void facesAreShadedDeterministicallyByDirection() {
        int top = WorldMapMaterialResolver.color(GRASS, Direction.UP);
        int bottom = WorldMapMaterialResolver.color(GRASS, Direction.DOWN);
        int north = WorldMapMaterialResolver.color(GRASS, Direction.NORTH);
        int east = WorldMapMaterialResolver.color(GRASS, Direction.EAST);

        assertEquals(top, WorldMapMaterialResolver.color(GRASS, Direction.UP));
        assertTrue(channel(bottom, 16) < channel(top, 16));
        assertTrue(channel(north, 16) < channel(east, 16));
        assertEquals(0xFF000000, top & 0xFF000000);
    }

    @Test
    void unknownAppearanceKeepsUnknownColour() {
        assertEquals(MinimapColorResolver.unknown(), WorldMapMaterialResolver.color(null, Direction.UP));
    }

    private static int channel(int color, int shift) {
        return (color >>> shift) & 0xFF;
    }
}
