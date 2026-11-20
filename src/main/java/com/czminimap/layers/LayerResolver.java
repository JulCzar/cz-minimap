package com.czminimap.layers;

import com.czminimap.core.DimensionKey;
import com.czminimap.cutaway.CutawaySettings;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Builds dimension-aware layer intervals anchored at the dimension's real minimum Y. */
public final class LayerResolver {
    private LayerResolver() {
    }

    public static List<LayerRange> ranges(DimensionKey dimension, int intervalSize) {
        Objects.requireNonNull(dimension, "dimension");
        requireInterval(intervalSize);

        List<LayerRange> result = new ArrayList<>();
        long start = dimension.minY();
        while (start < dimension.maxYExclusive()) {
            long end = Math.min((long) dimension.maxYExclusive(), start + intervalSize);
            result.add(new LayerRange((int) start, (int) end));
            start = end;
        }
        return List.copyOf(result);
    }

    public static int clampIndex(DimensionKey dimension, int intervalSize, int index) {
        int lastIndex = ranges(dimension, intervalSize).size() - 1;
        return Math.max(0, Math.min(lastIndex, index));
    }

    public static int indexForY(DimensionKey dimension, int intervalSize, int y) {
        Objects.requireNonNull(dimension, "dimension");
        requireInterval(intervalSize);
        int count = ranges(dimension, intervalSize).size();
        if (y <= dimension.minY()) {
            return 0;
        }
        if (y >= dimension.maxYExclusive()) {
            return count - 1;
        }
        long offset = (long) y - dimension.minY();
        return Math.min(count - 1, (int) (offset / intervalSize));
    }

    public static LayerRange rangeAt(DimensionKey dimension, int intervalSize, int index) {
        List<LayerRange> ranges = ranges(dimension, intervalSize);
        return ranges.get(Math.max(0, Math.min(ranges.size() - 1, index)));
    }

    public static LayerVisibility resolve(DimensionKey dimension, int intervalSize, LayerMode mode,
                                          int selectedIndex, CumulativeDirection direction,
                                          CutawaySettings cutaway) {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(cutaway, "cutaway");

        List<LayerRange> all = ranges(dimension, intervalSize);
        int index = Math.max(0, Math.min(all.size() - 1, selectedIndex));
        List<LayerRange> visible = switch (mode) {
            case FULL, CUTAWAY -> all;
            case SELECTED -> List.of(all.get(index));
            case CUMULATIVE -> direction == CumulativeDirection.DOWN
                    ? List.copyOf(all.subList(0, index + 1))
                    : List.copyOf(all.subList(index, all.size()));
        };
        CutawaySettings normalized = mode == LayerMode.CUTAWAY ? cutaway : CutawaySettings.none();
        return new LayerVisibility(dimension, mode, visible, normalized);
    }

    private static void requireInterval(int intervalSize) {
        if (intervalSize <= 0) {
            throw new IllegalArgumentException("intervalSize must be positive");
        }
    }
}
