package com.czminimap.cartography;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** Compact, world-independent visual description of a voxel. */
public record VoxelAppearance(ResourceLocation block, int propertiesMask, ResourceLocation biome, int tint) {
    public VoxelAppearance {
        Objects.requireNonNull(block, "block");
        Objects.requireNonNull(biome, "biome");
    }
}
