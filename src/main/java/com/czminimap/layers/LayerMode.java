package com.czminimap.layers;

/** Presentation modes for dimension-aware vertical terrain inspection. */
public enum LayerMode {
    FULL,
    SELECTED,
    CUMULATIVE,
    CUTAWAY;

    public LayerMode next() {
        LayerMode[] modes = values();
        return modes[(ordinal() + 1) % modes.length];
    }
}
