package com.czminimap.layers;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LayerRangeTest {
    @Test
    void usesInclusiveMinimumAndExclusiveMaximumAtNegativeY() {
        LayerRange range = new LayerRange(-64, -48);

        assertTrue(range.contains(-64));
        assertTrue(range.contains(-49));
        assertFalse(range.contains(-48));
        assertFalse(range.contains(-65));
        assertEquals(16, range.height());
    }

    @Test
    void rejectsEmptyAndReversedIntervals() {
        assertThrows(IllegalArgumentException.class, () -> new LayerRange(4, 4));
        assertThrows(IllegalArgumentException.class, () -> new LayerRange(5, 4));
    }
}
