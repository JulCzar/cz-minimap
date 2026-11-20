package com.czminimap.layers;

import com.czminimap.core.DimensionKey;
import com.czminimap.cutaway.ClipSide;
import com.czminimap.cutaway.CutawaySettings;
import com.czminimap.cutaway.VerticalAxis;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LayerResolverTest {
    private static final DimensionKey OVERWORLD = new DimensionKey(
            ResourceLocation.parse("minecraft:overworld"), -64, 320);

    @Test
    void anchorsIntervalsAtDimensionMinimum() {
        List<LayerRange> ranges = LayerResolver.ranges(OVERWORLD, 16);

        assertEquals(24, ranges.size());
        assertEquals(new LayerRange(-64, -48), ranges.getFirst());
        assertEquals(new LayerRange(304, 320), ranges.getLast());
        assertEquals(0, LayerResolver.indexForY(OVERWORLD, 16, -64));
        assertEquals(0, LayerResolver.indexForY(OVERWORLD, 16, -49));
        assertEquals(1, LayerResolver.indexForY(OVERWORLD, 16, -48));
        assertEquals(23, LayerResolver.indexForY(OVERWORLD, 16, 319));
    }

    @Test
    void truncatesLastRangeAtDimensionMaximum() {
        DimensionKey custom = new DimensionKey(ResourceLocation.parse("test:partial"), -10, 27);

        assertEquals(List.of(
                        new LayerRange(-10, 6),
                        new LayerRange(6, 22),
                        new LayerRange(22, 27)),
                LayerResolver.ranges(custom, 16));
        assertEquals(2, LayerResolver.clampIndex(custom, 16, 99));
        assertEquals(0, LayerResolver.clampIndex(custom, 16, -99));
        assertEquals(new LayerRange(22, 27), LayerResolver.rangeAt(custom, 16, 99));
    }

    @Test
    void rejectsNonPositiveIntervalSize() {
        assertThrows(IllegalArgumentException.class, () -> LayerResolver.ranges(OVERWORLD, 0));
        assertThrows(IllegalArgumentException.class, () -> LayerResolver.ranges(OVERWORLD, -1));
    }

    @Test
    void selectedAndCumulativeModesExposeTheExpectedIntervals() {
        CutawaySettings ignored = CutawaySettings.none().withHorizontalEnabled(true);

        LayerVisibility selected = LayerResolver.resolve(OVERWORLD, 16, LayerMode.SELECTED,
                2, CumulativeDirection.DOWN, ignored);
        assertEquals(List.of(new LayerRange(-32, -16)), selected.ranges());
        assertEquals(CutawaySettings.none(), selected.cutaway());

        LayerVisibility down = LayerResolver.resolve(OVERWORLD, 16, LayerMode.CUMULATIVE,
                2, CumulativeDirection.DOWN, ignored);
        assertEquals(List.of(
                new LayerRange(-64, -48),
                new LayerRange(-48, -32),
                new LayerRange(-32, -16)), down.ranges());

        LayerVisibility up = LayerResolver.resolve(OVERWORLD, 16, LayerMode.CUMULATIVE,
                22, CumulativeDirection.UP, ignored);
        assertEquals(List.of(new LayerRange(288, 304), new LayerRange(304, 320)), up.ranges());
    }

    @Test
    void fullModeIncludesEveryIntervalAndPreservesWorldY() {
        LayerVisibility visibility = LayerResolver.resolve(OVERWORLD, 16, LayerMode.FULL,
                0, CumulativeDirection.DOWN, CutawaySettings.none());

        assertEquals(24, visibility.ranges().size());
        assertTrue(visibility.isVisible(0, -64, 0));
        assertTrue(visibility.isVisible(0, 319, 0));
        assertFalse(visibility.isVisible(0, 320, 0));
    }

    @Test
    void cutawayCombinesFullDimensionRangesWithWorldSpacePlanes() {
        CutawaySettings cutaway = new CutawaySettings(
                true, 48, true, VerticalAxis.Z, 10, ClipSide.NEGATIVE);
        LayerVisibility visibility = LayerResolver.resolve(OVERWORLD, 16, LayerMode.CUTAWAY,
                7, CumulativeDirection.DOWN, cutaway);

        assertEquals(24, visibility.ranges().size());
        assertTrue(visibility.isVisible(100, 47, 9));
        assertFalse(visibility.isVisible(100, 48, 9));
        assertFalse(visibility.isVisible(100, 47, 10));
    }
}
