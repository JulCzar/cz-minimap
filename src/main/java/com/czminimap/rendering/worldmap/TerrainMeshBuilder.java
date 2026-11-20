package com.czminimap.rendering.worldmap;

import com.czminimap.cartography.CartographicSnapshot;
import com.czminimap.cartography.DiscoveryState;
import com.czminimap.cartography.VoxelAppearance;
import com.czminimap.core.DimensionKey;
import com.czminimap.core.RegionKey;
import com.czminimap.core.WorldKey;
import com.czminimap.cutaway.CutawaySettings;
import com.czminimap.layers.CumulativeDirection;
import com.czminimap.layers.LayerMode;
import com.czminimap.layers.LayerResolver;
import com.czminimap.layers.LayerVisibility;
import com.czminimap.lod.LodLevel;
import net.minecraft.core.Direction;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Builds voxel-aligned chunk meshes from immutable cartographic data. */
public final class TerrainMeshBuilder {
    private static final int[][][] FACE_CORNERS = {
            {{0, 0, 0}, {0, 0, 1}, {1, 0, 1}, {1, 0, 0}},
            {{0, 1, 0}, {1, 1, 0}, {1, 1, 1}, {0, 1, 1}},
            {{0, 0, 0}, {1, 0, 0}, {1, 1, 0}, {0, 1, 0}},
            {{0, 0, 1}, {0, 1, 1}, {1, 1, 1}, {1, 0, 1}},
            {{0, 0, 0}, {0, 1, 0}, {0, 1, 1}, {0, 0, 1}},
            {{1, 0, 0}, {1, 0, 1}, {1, 1, 1}, {1, 1, 0}}
    };

    private TerrainMeshBuilder() {
    }

    public static TerrainMesh build(CartographicSnapshot snapshot, WorldKey world, DimensionKey dimension,
                                    int chunkX, int chunkZ, long version) {
        LayerVisibility visibility = LayerResolver.resolve(dimension, 16, LayerMode.FULL, 0,
                CumulativeDirection.DOWN, CutawaySettings.none());
        return build(snapshot, world, dimension, chunkX, chunkZ,
                version, visibility, LodLevel.LOD_0);
    }

    public static TerrainMesh build(CartographicSnapshot snapshot, WorldKey world, DimensionKey dimension,
                                    int chunkX, int chunkZ, long version, LayerVisibility visibility) {
        return build(snapshot, world, dimension, chunkX, chunkZ,
                version, visibility, LodLevel.LOD_0);
    }

    public static TerrainMesh build(CartographicSnapshot snapshot, WorldKey world, DimensionKey dimension,
                                    int chunkX, int chunkZ, long version, LayerVisibility visibility,
                                    LodLevel lodLevel) {
        Map<Integer, Slice> slices = collectSlices(snapshot, world, dimension,
                chunkX, chunkZ, visibility);
        FloatBuffer vertices = new FloatBuffer();
        int blockSize = lodLevel.blockSize();
        for (Map.Entry<Integer, Slice> entry : slices.entrySet()) {
            int y = entry.getKey();
            Slice slice = entry.getValue();
            for (int localZ = 0; localZ < 16; localZ += blockSize) {
                for (int localX = 0; localX < 16; localX += blockSize) {
                    emitCell(snapshot, world, dimension, visibility, vertices, slice,
                            chunkX * 16 + localX, y, chunkZ * 16 + localZ,
                            localX, localZ, blockSize);
                }
            }
        }
        return new TerrainMesh(chunkX, chunkZ, lodLevel, version,
                vertices.toArray(), vertices.size() / TerrainMesh.FLOATS_PER_VERTEX);
    }

    private static Map<Integer, Slice> collectSlices(CartographicSnapshot snapshot, WorldKey world,
                                                     DimensionKey dimension, int chunkX, int chunkZ,
                                                     LayerVisibility visibility) {
        Map<Integer, Slice> slices = new HashMap<>();
        RegionKey own = new RegionKey(world, dimension,
                RegionKey.fromChunk(chunkX), RegionKey.fromChunk(chunkZ));
        snapshot.forEachSolid(world, dimension, own, (x, y, z, state, appearance) -> {
            if (Math.floorDiv(x, 16) == chunkX && Math.floorDiv(z, 16) == chunkZ
                    && visibility.isVisible(x, y, z)) {
                slices.computeIfAbsent(y, ignored -> new Slice())
                        .set(Math.floorMod(x, 16), Math.floorMod(z, 16), appearance);
            }
        });
        return slices;
    }

    private static void emitCell(CartographicSnapshot snapshot, WorldKey world, DimensionKey dimension,
                                 LayerVisibility visibility, FloatBuffer vertices, Slice slice,
                                 int x, int y, int z, int localX, int localZ, int size) {
        if (!slice.allSolid(localX, localZ, size)) {
            if (size == 1) {
                if (slice.solid(localX, localZ)) {
                    emitPrism(snapshot, world, dimension, visibility, vertices,
                            x, y, z, 1, slice.appearance(localX, localZ));
                }
                return;
            }
            int half = size / 2;
            emitCell(snapshot, world, dimension, visibility, vertices, slice,
                    x, y, z, localX, localZ, half);
            emitCell(snapshot, world, dimension, visibility, vertices, slice,
                    x + half, y, z, localX + half, localZ, half);
            emitCell(snapshot, world, dimension, visibility, vertices, slice,
                    x, y, z + half, localX, localZ + half, half);
            emitCell(snapshot, world, dimension, visibility, vertices, slice,
                    x + half, y, z + half, localX + half, localZ + half, half);
            return;
        }
        emitPrism(snapshot, world, dimension, visibility, vertices,
                x, y, z, size, slice.dominantAppearance(localX, localZ, size));
    }

    private static void emitPrism(CartographicSnapshot snapshot, WorldKey world, DimensionKey dimension,
                                  LayerVisibility visibility, FloatBuffer vertices,
                                  int x, int y, int z, int size, VoxelAppearance appearance) {
        for (Direction face : Direction.values()) {
            if (!faceCovered(snapshot, world, dimension, visibility, x, y, z, size, face)) {
                emitFace(vertices, x, y, z, size, appearance, face);
            }
        }
    }

    private static boolean faceCovered(CartographicSnapshot snapshot, WorldKey world, DimensionKey dimension,
                                       LayerVisibility visibility, int x, int y, int z, int size,
                                       Direction face) {
        if (face == Direction.UP || face == Direction.DOWN) {
            int neighbourY = y + face.getStepY();
            for (int dz = 0; dz < size; dz++) {
                for (int dx = 0; dx < size; dx++) {
                    if (!solidVisible(snapshot, world, dimension, visibility,
                            x + dx, neighbourY, z + dz)) {
                        return false;
                    }
                }
            }
            return true;
        }
        for (int offset = 0; offset < size; offset++) {
            int neighbourX = x + (face == Direction.EAST ? size
                    : face == Direction.WEST ? -1 : offset);
            int neighbourZ = z + (face == Direction.SOUTH ? size
                    : face == Direction.NORTH ? -1 : offset);
            if (!solidVisible(snapshot, world, dimension, visibility, neighbourX, y, neighbourZ)) {
                return false;
            }
        }
        return true;
    }

    private static boolean solidVisible(CartographicSnapshot snapshot, WorldKey world, DimensionKey dimension,
                                        LayerVisibility visibility, int x, int y, int z) {
        if (!dimension.containsY(y) || !visibility.isVisible(x, y, z)) {
            return false;
        }
        DiscoveryState state = snapshot.stateAt(world, dimension, x, y, z);
        return state == DiscoveryState.SOLID || state == DiscoveryState.EXPOSED_SURFACE;
    }

    private static void emitFace(FloatBuffer vertices, int x, int y, int z, int size,
                                 VoxelAppearance appearance, Direction face) {
        int color = WorldMapMaterialResolver.color(appearance, face);
        float red = ((color >>> 16) & 0xFF) / 255f;
        float green = ((color >>> 8) & 0xFF) / 255f;
        float blue = (color & 0xFF) / 255f;
        float alpha = ((color >>> 24) & 0xFF) / 255f;
        for (int[] corner : FACE_CORNERS[face.ordinal()]) {
            vertices.add(x + corner[0] * size, y + corner[1], z + corner[2] * size,
                    red, green, blue, alpha);
        }
    }

    private static final class Slice {
        private final VoxelAppearance[] appearances = new VoxelAppearance[16 * 16];
        private final boolean[] solid = new boolean[16 * 16];

        void set(int x, int z, VoxelAppearance appearance) {
            int index = x + z * 16;
            solid[index] = true;
            appearances[index] = appearance;
        }

        boolean solid(int x, int z) {
            return solid[x + z * 16];
        }

        VoxelAppearance appearance(int x, int z) {
            return appearances[x + z * 16];
        }

        boolean allSolid(int x, int z, int size) {
            for (int dz = 0; dz < size; dz++) {
                for (int dx = 0; dx < size; dx++) {
                    if (!solid(x + dx, z + dz)) {
                        return false;
                    }
                }
            }
            return true;
        }

        VoxelAppearance dominantAppearance(int x, int z, int size) {
            Map<VoxelAppearance, Integer> counts = new LinkedHashMap<>();
            for (int dz = 0; dz < size; dz++) {
                for (int dx = 0; dx < size; dx++) {
                    counts.merge(appearance(x + dx, z + dz), 1, Integer::sum);
                }
            }
            VoxelAppearance dominant = null;
            int largest = -1;
            for (Map.Entry<VoxelAppearance, Integer> entry : counts.entrySet()) {
                if (entry.getValue() > largest) {
                    dominant = entry.getKey();
                    largest = entry.getValue();
                }
            }
            return dominant;
        }
    }

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
