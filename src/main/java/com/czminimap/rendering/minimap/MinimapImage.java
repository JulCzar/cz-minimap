package com.czminimap.rendering.minimap;

import com.czminimap.cartography.CartographicSnapshot;
import com.czminimap.cartography.VoxelAppearance;
import com.czminimap.core.DimensionKey;
import com.czminimap.core.WorldKey;

/**
 * Immutable top-down ARGB buffer derived from a cartographic snapshot.
 * It contains no Minecraft world references and is rebuilt only when its inputs change.
 */
public record MinimapImage(int width, int height, double blocksPerPixel, int originX, int originZ,
                           int[] pixels, long snapshotVersion) {
    private static final int FRAME = 0xFFE8E8E8;

    public static MinimapImage build(CartographicSnapshot snapshot, WorldKey world, DimensionKey dimension,
                                     int centerX, int centerZ, int width, int height,
                                     double blocksPerPixel, boolean circular) {
        int size = width * height;
        int[] pixels = new int[size];
        int originX = (int) Math.floor(centerX - width / 2.0 * blocksPerPixel);
        int originZ = (int) Math.floor(centerZ - height / 2.0 * blocksPerPixel);

        double radius = Math.min(width, height) / 2.0 - 1.0;
        double radiusSquared = radius * radius;
        double ringSquared = Math.max(0.0, radius - 1.5);
        ringSquared *= ringSquared;

        for (int y = 0; y < height; y++) {
            int worldZ = originZ + (int) Math.floor(y * blocksPerPixel);
            for (int x = 0; x < width; x++) {
                int index = x + y * width;
                int worldX = originX + (int) Math.floor(x * blocksPerPixel);
                if (circular && outsideCircle(x, y, width, height, radiusSquared)) {
                    pixels[index] = 0;
                    continue;
                }
                if (circular && insideRing(x, y, width, height, radiusSquared, ringSquared)) {
                    pixels[index] = FRAME;
                    continue;
                }
                if (!circular && isBorder(x, y, width, height)) {
                    pixels[index] = FRAME;
                    continue;
                }
                CartographicSnapshot.SurfaceColumn column = snapshot.surfaceAt(world, dimension, worldX, worldZ);
                if (column == null) {
                    pixels[index] = MinimapColorResolver.unknown();
                    continue;
                }
                VoxelAppearance appearance = column.appearance();
                pixels[index] = appearance == null
                        ? MinimapColorResolver.unknown()
                        : MinimapColorResolver.color(appearance);
            }
        }
        return new MinimapImage(width, height, blocksPerPixel, originX, originZ, pixels, snapshot.version());
    }

    private static boolean outsideCircle(int x, int y, int width, int height, double radiusSquared) {
        double dx = x - (width - 1) / 2.0;
        double dy = y - (height - 1) / 2.0;
        return dx * dx + dy * dy > radiusSquared;
    }

    private static boolean insideRing(int x, int y, int width, int height,
                                      double radiusSquared, double ringSquared) {
        double dx = x - (width - 1) / 2.0;
        double dy = y - (height - 1) / 2.0;
        double distanceSquared = dx * dx + dy * dy;
        return distanceSquared <= radiusSquared && distanceSquared >= ringSquared;
    }

    private static boolean isBorder(int x, int y, int width, int height) {
        return x == 0 || y == 0 || x == width - 1 || y == height - 1;
    }
}
