package com.czminimap.rendering.minimap;

import com.czminimap.cartography.VoxelAppearance;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinimapColorResolverTest {
    private static VoxelAppearance appearance(String block, String biome) {
        return new VoxelAppearance(ResourceLocation.parse("minecraft:" + block), 0,
                ResourceLocation.parse("minecraft:" + biome), 0);
    }

    @Test
    void unknownAndMissingAppearancesShareOneDistinctColour() {
        assertEquals(MinimapColorResolver.unknown(), MinimapColorResolver.color(null));
        assertEquals(0xFF181818, MinimapColorResolver.unknown());
    }

    @Test
    void recognisableMaterialsKeepDistinctColours() {
        int grass = MinimapColorResolver.color(appearance("grass_block", "plains"));
        int sand = MinimapColorResolver.color(appearance("sand", "desert"));
        int stone = MinimapColorResolver.color(appearance("stone", "plains"));
        int water = MinimapColorResolver.color(appearance("water", "ocean"));
        assertNotEquals(grass, sand);
        assertNotEquals(stone, water);
        assertTrue(((grass >>> 8) & 0xFF) > ((grass >>> 16) & 0xFF));
    }

    @Test
    void biomeTintChangesTheResultWithoutErasingMaterialDifferences() {
        int plainsGrass = MinimapColorResolver.color(appearance("grass_block", "plains"));
        int desertGrass = MinimapColorResolver.color(appearance("grass_block", "desert"));
        assertNotEquals(plainsGrass, desertGrass);
        assertEquals(0xFF000000, plainsGrass & 0xFF000000);
        assertEquals(0xFF000000, desertGrass & 0xFF000000);
    }

    @Test
    void fallbackIsDeterministic() {
        int first = MinimapColorResolver.color(appearance("quartz_block", "plains"));
        int second = MinimapColorResolver.color(appearance("quartz_block", "plains"));
        int other = MinimapColorResolver.color(appearance("smooth_basalt", "plains"));
        assertEquals(first, second);
        assertNotEquals(first, other);
    }
}
