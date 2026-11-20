package com.czminimap.rendering.worldmap;

import com.czminimap.cartography.CartographicSnapshot;
import com.czminimap.cartography.VoxelAppearance;
import com.czminimap.core.DimensionKey;
import com.czminimap.core.RegionKey;
import com.czminimap.core.WorldKey;
import com.czminimap.cutaway.CutawaySettings;
import com.czminimap.layers.CumulativeDirection;
import com.czminimap.layers.LayerMode;
import com.czminimap.layers.LayerResolver;
import com.czminimap.layers.LayerVisibility;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.Direction;

import java.util.Arrays;

/** Builds a chunk mesh from immutable cartographic data. Removes faces between known solid neighbours. */
public final class TerrainMeshBuilder {
    private static final int[][][] FACE_CORNERS = {
            {{0, 0, 0}, {0, 0, 1}, {1, 0, 1}, {1, 0, 0}}, // DOWN
            {{0, 1, 0}, {1, 1, 0}, {1, 1, 1}, {0, 1, 1}}, // UP
            {{0, 0, 0}, {1, 0, 0}, {1, 1, 0}, {0, 1, 0}}, // NORTH
            {{0, 0, 1}, {0, 1, 1}, {1, 1, 1}, {1, 0, 1}}, // SOUTH
            {{0, 0, 0}, {0, 1, 0}, {0, 1, 1}, {0, 0, 1}}, // WEST
            {{1, 0, 0}, {1, 0, 1}, {1, 1, 1}, {1, 1, 0}}  // EAST
    };

    private TerrainMeshBuilder() {
    }

    public static TerrainMesh build(CartographicSnapshot snapshot, WorldKey world, DimensionKey dimension,
                                    int chunkX, int chunkZ, long version) {
        LayerVisibility visibility = LayerResolver.resolve(dimension, 16, LayerMode.FULL, 0,
                CumulativeDirection.DOWN, CutawaySettings.none());
        return build(snapshot, world, dimension, chunkX, chunkZ, version, visibility);
    }

    public static TerrainMesh build(CartographicSnapshot snapshot, WorldKey world, DimensionKey dimension,
                                    int chunkX, int chunkZ, long version, LayerVisibility visibility) {
        LongSet solid = new LongOpenHashSet();
        collectSolid(snapshot, world, dimension, chunkX, chunkZ, visibility, solid);

        FloatBuffer vertices = new FloatBuffer();
        RegionKey own = new RegionKey(world, dimension,
                RegionKey.fromChunk(chunkX), RegionKey.fromChunk(chunkZ));
        snapshot.forEachSolid(world, dimension, own, (x, y, z, state, appearance) -> {
            if ((Math.floorDiv(x, 16) != chunkX) || (Math.floorDiv(z, 16) != chunkZ)) {
                return;
            }
            if (!visibility.isVisible(x, y, z)) {
                return;
            }
            for (Direction face : Direction.values()) {
                if (solid.contains(blockKey(x + face.getStepX(), y + face.getStepY(), z + face.getStepZ()))) {
                    continue;
                }
                emitFace(vertices, x, y, z, appearance, face);
            }
        });
        return new TerrainMesh(chunkX, chunkZ, version,
                vertices.toArray(), vertices.size() / TerrainMesh.FLOATS_PER_VERTEX);
    }

    private static void collectSolid(CartographicSnapshot snapshot, WorldKey world, DimensionKey dimension,
                                     int chunkX, int chunkZ, LayerVisibility visibility, LongSet solid) {
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                int targetX = chunkX + dx;
                int targetZ = chunkZ + dz;
                RegionKey region = new RegionKey(world, dimension,
                        RegionKey.fromChunk(targetX), RegionKey.fromChunk(targetZ));
                snapshot.forEachSolid(world, dimension, region, (x, y, z, state, appearance) -> {
                    if (Math.floorDiv(x, 16) == targetX && Math.floorDiv(z, 16) == targetZ
                            && visibility.isVisible(x, y, z)) {
                        solid.add(blockKey(x, y, z));
                    }
                });
            }
        }
    }

    private static void emitFace(FloatBuffer vertices, int x, int y, int z,
                                 VoxelAppearance appearance, Direction face) {
        int color = WorldMapMaterialResolver.color(appearance, face);
        float red = ((color >>> 16) & 0xFF) / 255f;
        float green = ((color >>> 8) & 0xFF) / 255f;
        float blue = (color & 0xFF) / 255f;
        float alpha = ((color >>> 24) & 0xFF) / 255f;
        for (int[] corner : FACE_CORNERS[face.ordinal()]) {
            vertices.add(x + corner[0], y + corner[1], z + corner[2], red, green, blue, alpha);
        }
    }

    private static long blockKey(int x, int y, int z) {
        return ((long) x & 0xFFFFL) | (((long) z & 0xFFFFL) << 16) | (((long) y) << 32);
    }

    /** Minimal growable float array; avoids boxing and per-face allocations. */
    private static final class FloatBuffer {
        private float[] data = new float[1024];
        private int size;

        void add(float... values) {
            if (size + values.length > data.length) {
                data = Arrays.copyOf(data, Math.max(data.length * 2, size + values.length));
            }
            System.arraycopy(values, 0, data, size, values.length);
            size += values.length;
        }

        int size() {
            return size;
        }

        float[] toArray() {
            return Arrays.copyOf(data, size);
        }
    }
}
