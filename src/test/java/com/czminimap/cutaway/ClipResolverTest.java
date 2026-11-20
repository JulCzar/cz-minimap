package com.czminimap.cutaway;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClipResolverTest {
    @Test
    void horizontalPlaneKeepsOnlyBlocksBelowExclusiveMaximum() {
        CutawaySettings settings = CutawaySettings.none()
                .withHorizontalEnabled(true)
                .withMaxYExclusive(-32);

        assertTrue(ClipResolver.isVisible(settings, 0, -33, 0));
        assertFalse(ClipResolver.isVisible(settings, 0, -32, 0));
    }

    @Test
    void verticalPlaneSupportsBothAxesAndSides() {
        CutawaySettings negativeX = CutawaySettings.none()
                .withVerticalEnabled(true)
                .withCoordinate(10);
        assertTrue(ClipResolver.isVisible(negativeX, 9, 0, 200));
        assertFalse(ClipResolver.isVisible(negativeX, 10, 0, 200));

        CutawaySettings positiveZ = negativeX
                .withAxis(VerticalAxis.Z)
                .withVisibleSide(ClipSide.POSITIVE)
                .withCoordinate(-5);
        assertFalse(ClipResolver.isVisible(positiveZ, 200, 0, -6));
        assertTrue(ClipResolver.isVisible(positiveZ, 200, 0, -5));
    }

    @Test
    void enabledPlanesAreCombined() {
        CutawaySettings settings = new CutawaySettings(
                true, 80, true, VerticalAxis.X, 0, ClipSide.POSITIVE);

        assertTrue(ClipResolver.isVisible(settings, 0, 79, 0));
        assertFalse(ClipResolver.isVisible(settings, -1, 79, 0));
        assertFalse(ClipResolver.isVisible(settings, 0, 80, 0));
    }
}
