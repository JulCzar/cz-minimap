package com.czminimap.world;

import com.czminimap.cartography.VoxelAppearance;
import com.czminimap.core.DimensionKey;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Reads only chunks already present on the client. Never requests a chunk load. */
public final class ChunkDataCollector {
    private static final ResourceLocation FALLBACK_BIOME = ResourceLocation.parse("czminimap:unknown");
    private static final Set<String> RELEVANT_PROPERTIES = Set.of(
            "axis", "facing", "rotation", "waterlogged", "half", "shape");

    private ChunkDataCollector() {
    }

    public static Optional<VoxelSample> sample(ClientLevel level, int x, int y, int z) {
        ChunkAccess chunk = loadedChunk(level, x, z);
        if (chunk == null) {
            return Optional.empty();
        }
        BlockPos position = new BlockPos(x, y, z);
        BlockState state = chunk.getBlockState(position);
        return Optional.of(new VoxelSample(x, y, z, state.isAir(), appearance(level, position, state)));
    }

    /** Top block plus up to two blocks below, keeping only sky- or side-exposed blocks. */
    public static List<VoxelSample> sampleSurfaceColumn(ClientLevel level, DimensionKey dimension, int x, int z) {
        ChunkAccess chunk = loadedChunk(level, x, z);
        if (chunk == null) {
            return List.of();
        }
        int topY = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        if (!dimension.containsY(topY)) {
            return List.of();
        }

        List<VoxelSample> samples = new ArrayList<>(3);
        for (int offset = 0; offset < 3; offset++) {
            int y = topY - offset;
            if (!dimension.containsY(y)) {
                break;
            }
            Optional<VoxelSample> sample = sample(level, x, y, z);
            if (sample.isEmpty() || sample.get().air()) {
                break;
            }
            if (offset == 0 || sideExposed(level, x, y, z)) {
                samples.add(sample.get());
            }
        }
        return samples;
    }

    private static boolean sideExposed(ClientLevel level, int x, int y, int z) {
        int[][] neighbours = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] offset : neighbours) {
            if (isAir(level, x + offset[0], y, z + offset[1])) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAir(ClientLevel level, int x, int y, int z) {
        ChunkAccess chunk = loadedChunk(level, x, z);
        return chunk != null && chunk.getBlockState(new BlockPos(x, y, z)).isAir();
    }

    private static ChunkAccess loadedChunk(ClientLevel level, int x, int z) {
        int chunkX = SectionPos.blockToSectionCoord(x);
        int chunkZ = SectionPos.blockToSectionCoord(z);
        if (!level.hasChunk(chunkX, chunkZ)) {
            return null;
        }
        return level.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
    }

    private static VoxelAppearance appearance(ClientLevel level, BlockPos position, BlockState state) {
        ResourceLocation block = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        ResourceLocation biome = biome(level, position);
        return new VoxelAppearance(block, propertiesMask(state), biome, 0);
    }

    private static ResourceLocation biome(ClientLevel level, BlockPos position) {
        Holder<net.minecraft.world.level.biome.Biome> holder = level.getBiome(position);
        return holder.unwrapKey().map(key -> key.location()).orElse(FALLBACK_BIOME);
    }

    private static int propertiesMask(BlockState state) {
        int mask = 0;
        for (Map.Entry<Property<?>, Comparable<?>> entry : state.getValues().entrySet()) {
            if (!RELEVANT_PROPERTIES.contains(entry.getKey().getName())) {
                continue;
            }
            String value = entry.getKey().getName() + "=" + entry.getValue();
            mask = 31 * mask + value.hashCode();
        }
        return mask;
    }
}
