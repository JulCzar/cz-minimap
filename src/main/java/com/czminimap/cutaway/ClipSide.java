package com.czminimap.cutaway;

/** Side of a vertical clipping plane that remains visible. */
public enum ClipSide {
    NEGATIVE,
    POSITIVE;

    public ClipSide toggle() {
        return this == NEGATIVE ? POSITIVE : NEGATIVE;
    }
}
