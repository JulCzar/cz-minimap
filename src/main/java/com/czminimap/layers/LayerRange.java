package com.czminimap.layers;

/** A vertical world-space interval whose minimum is inclusive and maximum is exclusive. */
public record LayerRange(int minYInclusive, int maxYExclusive) {
    public LayerRange {
        if (minYInclusive >= maxYExclusive) {
            throw new IllegalArgumentException("minYInclusive must be lower than maxYExclusive");
        }
    }

    public boolean contains(int y) {
        return y >= minYInclusive && y < maxYExclusive;
    }

    public int height() {
        return maxYExclusive - minYInclusive;
    }
}
