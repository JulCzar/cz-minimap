package com.czminimap.world;

import com.czminimap.cartography.VoxelAppearance;

/** One immutable block observation taken from an already loaded client chunk. */
public record VoxelSample(int x, int y, int z, boolean air, VoxelAppearance appearance) {
}
