package com.czminimap.cutaway;

/** World-space horizontal axis whose constant coordinate defines a vertical clipping plane. */
public enum VerticalAxis {
    X,
    Z;

    public VerticalAxis toggle() {
        return this == X ? Z : X;
    }
}
