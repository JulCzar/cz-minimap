package com.czminimap.core;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** Dimension identity together with the real build-height limits of that dimension. */
public record DimensionKey(ResourceLocation id, int minY, int maxYExclusive) {
    public DimensionKey {
        Objects.requireNonNull(id, "id");
        if (minY >= maxYExclusive) {
            throw new IllegalArgumentException("minY must be lower than maxYExclusive");
        }
    }

    public boolean containsY(int y) {
        return y >= minY && y < maxYExclusive;
    }
}
