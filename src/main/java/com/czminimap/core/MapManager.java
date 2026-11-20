package com.czminimap.core;

import com.czminimap.cartography.CartographicMap;

import java.util.Objects;

/** Owns the single client-side cartographic source of truth for the active world. */
public final class MapManager {
    private CartographicMap map = new CartographicMap();
    private WorldKey activeWorld;

    public CartographicMap map() {
        return map;
    }

    public WorldKey activeWorld() {
        return activeWorld;
    }

    /**
     * Selects the active world. Returns {@code true} when the world changed and the
     * in-memory map had to be replaced.
     */
    public boolean selectWorld(WorldKey world) {
        Objects.requireNonNull(world, "world");
        if (world.equals(activeWorld)) {
            return false;
        }
        activeWorld = world;
        map = new CartographicMap();
        return true;
    }
}
