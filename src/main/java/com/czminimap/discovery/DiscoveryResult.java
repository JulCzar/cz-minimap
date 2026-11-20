package com.czminimap.discovery;

import com.czminimap.cartography.DiscoveryState;
import com.czminimap.cartography.VoxelAppearance;

import java.util.List;

/** Updates produced by a discovery policy, ready to be applied to the cartographic map. */
public record DiscoveryResult(List<VoxelUpdate> updates) {
    public DiscoveryResult {
        updates = List.copyOf(updates);
    }

    public record VoxelUpdate(int x, int y, int z, DiscoveryState state, VoxelAppearance appearance) {
    }
}
