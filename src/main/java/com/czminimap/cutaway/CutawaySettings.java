package com.czminimap.cutaway;

import java.util.Objects;

/** Immutable world-space clipping settings. Disabled planes leave terrain unchanged. */
public record CutawaySettings(
        boolean horizontalEnabled,
        int maxYExclusive,
        boolean verticalEnabled,
        VerticalAxis axis,
        int coordinate,
        ClipSide visibleSide
) {
    public CutawaySettings {
        Objects.requireNonNull(axis, "axis");
        Objects.requireNonNull(visibleSide, "visibleSide");
    }

    public static CutawaySettings none() {
        return new CutawaySettings(false, 0, false, VerticalAxis.X, 0, ClipSide.NEGATIVE);
    }

    public CutawaySettings withHorizontalEnabled(boolean enabled) {
        return new CutawaySettings(enabled, maxYExclusive, verticalEnabled, axis, coordinate, visibleSide);
    }

    public CutawaySettings withMaxYExclusive(int maximum) {
        return new CutawaySettings(horizontalEnabled, maximum, verticalEnabled, axis, coordinate, visibleSide);
    }

    public CutawaySettings withVerticalEnabled(boolean enabled) {
        return new CutawaySettings(horizontalEnabled, maxYExclusive, enabled, axis, coordinate, visibleSide);
    }

    public CutawaySettings withAxis(VerticalAxis newAxis) {
        return new CutawaySettings(horizontalEnabled, maxYExclusive, verticalEnabled,
                Objects.requireNonNull(newAxis, "newAxis"), coordinate, visibleSide);
    }

    public CutawaySettings withCoordinate(int newCoordinate) {
        return new CutawaySettings(horizontalEnabled, maxYExclusive, verticalEnabled,
                axis, newCoordinate, visibleSide);
    }

    public CutawaySettings withVisibleSide(ClipSide newVisibleSide) {
        return new CutawaySettings(horizontalEnabled, maxYExclusive, verticalEnabled,
                axis, coordinate, Objects.requireNonNull(newVisibleSide, "newVisibleSide"));
    }
}
