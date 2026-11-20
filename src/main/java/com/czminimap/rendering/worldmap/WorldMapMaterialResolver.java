package com.czminimap.rendering.worldmap;

import com.czminimap.cartography.VoxelAppearance;
import com.czminimap.rendering.minimap.MinimapColorResolver;
import net.minecraft.core.Direction;

/** Resolves a stored block appearance into a shaded face colour for the 3D map. */
public final class WorldMapMaterialResolver {
    private WorldMapMaterialResolver() {
    }

    /** Deterministic ARGB colour with a fixed light direction; no live world or atlas access. */
    public static int color(VoxelAppearance appearance, Direction face) {
        int base = MinimapColorResolver.color(appearance);
        float shade = switch (face) {
            case UP -> 1.0f;
            case DOWN -> 0.45f;
            case NORTH, SOUTH -> 0.72f;
            case WEST, EAST -> 0.86f;
        };
        int red = (int) (((base >>> 16) & 0xFF) * shade);
        int green = (int) (((base >>> 8) & 0xFF) * shade);
        int blue = (int) ((base & 0xFF) * shade);
        return 0xFF000000 | (red << 16) | (green << 8) | blue;
    }
}
