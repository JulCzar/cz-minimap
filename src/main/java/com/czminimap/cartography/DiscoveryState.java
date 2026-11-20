package com.czminimap.cartography;

/** Discovery state of one voxel. UNKNOWN is never equivalent to KNOWN_AIR. */
public enum DiscoveryState {
    UNKNOWN,
    KNOWN_AIR,
    SOLID,
    EXPOSED_SURFACE,
    UNDERGROUND_SPACE;

    public boolean isDiscovered() {
        return this != UNKNOWN;
    }
}
