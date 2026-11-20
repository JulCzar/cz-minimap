package com.czminimap.core;

import java.util.Objects;

/** Sparse storage unit: 32 x 32 chunks of one dimension of one world. */
public record RegionKey(WorldKey world, DimensionKey dimension, int x, int z) {
    public static final int CHUNKS_PER_REGION = 32;
    public static final int BLOCKS_PER_REGION = CHUNKS_PER_REGION * 16;

    public RegionKey {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(dimension, "dimension");
    }

    public static int fromBlock(int blockCoordinate) {
        return Math.floorDiv(blockCoordinate, BLOCKS_PER_REGION);
    }

    public static int fromChunk(int chunkCoordinate) {
        return Math.floorDiv(chunkCoordinate, CHUNKS_PER_REGION);
    }
}
