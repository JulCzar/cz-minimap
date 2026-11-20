package com.czminimap.layers;

import com.czminimap.core.DimensionKey;
import com.czminimap.cutaway.ClipResolver;
import com.czminimap.cutaway.CutawaySettings;

import java.util.List;
import java.util.Objects;

/** Immutable presentation filter derived from layer and cutaway controls. */
public record LayerVisibility(
        DimensionKey dimension,
        LayerMode mode,
        List<LayerRange> ranges,
        CutawaySettings cutaway
) {
    public LayerVisibility {
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(ranges, "ranges");
        Objects.requireNonNull(cutaway, "cutaway");
        ranges = List.copyOf(ranges);
        if (ranges.isEmpty()) {
            throw new IllegalArgumentException("At least one visible layer range is required");
        }
        for (LayerRange range : ranges) {
            Objects.requireNonNull(range, "range");
            if (range.minYInclusive() < dimension.minY()
                    || range.maxYExclusive() > dimension.maxYExclusive()) {
                throw new IllegalArgumentException("Layer range must stay within the dimension height limits");
            }
        }
    }

    public boolean isVisible(int x, int y, int z) {
        if (!dimension.containsY(y)) {
            return false;
        }
        boolean insideRange = false;
        for (LayerRange range : ranges) {
            if (range.contains(y)) {
                insideRange = true;
                break;
            }
        }
        if (!insideRange) {
            return false;
        }
        return mode != LayerMode.CUTAWAY || ClipResolver.isVisible(cutaway, x, y, z);
    }
}
