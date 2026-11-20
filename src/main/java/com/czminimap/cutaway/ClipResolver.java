package com.czminimap.cutaway;

/** Resolves horizontal and vertical cutaway planes without changing world coordinates. */
public final class ClipResolver {
    private ClipResolver() {
    }

    public static boolean isVisible(CutawaySettings settings, int x, int y, int z) {
        if (settings.horizontalEnabled() && y >= settings.maxYExclusive()) {
            return false;
        }
        if (!settings.verticalEnabled()) {
            return true;
        }

        int value = settings.axis() == VerticalAxis.X ? x : z;
        return settings.visibleSide() == ClipSide.NEGATIVE
                ? value < settings.coordinate()
                : value >= settings.coordinate();
    }
}
