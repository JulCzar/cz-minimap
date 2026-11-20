package com.czminimap.layers;

/** Direction in which cumulative layer visibility expands from the selected interval. */
public enum CumulativeDirection {
    UP,
    DOWN;

    public CumulativeDirection toggle() {
        return this == UP ? DOWN : UP;
    }
}
