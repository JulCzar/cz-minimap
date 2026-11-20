package com.czminimap.discovery;

import com.czminimap.config.MapConfig;

/** Immutable discovery limits for one client session. */
public record DiscoverySettings(int surfaceRadius, int undergroundRadius, int maxSamplesPerTick) {
    public DiscoverySettings {
        if (surfaceRadius < 1 || undergroundRadius < 1 || maxSamplesPerTick < 1) {
            throw new IllegalArgumentException("Discovery limits must be positive");
        }
    }

    public static DiscoverySettings fromConfig() {
        MapConfig config = MapConfig.CONFIG;
        return new DiscoverySettings(
                config.surfaceRadius.get(),
                config.undergroundRadius.get(),
                config.maxSamplesPerTick.get());
    }
}
