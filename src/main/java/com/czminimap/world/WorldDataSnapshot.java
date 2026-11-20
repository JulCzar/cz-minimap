package com.czminimap.world;

import com.czminimap.core.DimensionKey;
import com.czminimap.core.WorldKey;

import java.util.List;

/** Bounded, immutable set of block observations. Absence of a sample means "still unknown". */
public record WorldDataSnapshot(WorldKey world, DimensionKey dimension, long tick, List<VoxelSample> samples) {
    public WorldDataSnapshot {
        samples = List.copyOf(samples);
    }
}
