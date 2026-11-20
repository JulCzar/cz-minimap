package com.czminimap.discovery;

import com.czminimap.cartography.DiscoveryState;
import com.czminimap.world.VoxelSample;
import com.czminimap.world.WorldDataSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Pure translation of block observations into cartographic updates. */
public final class DiscoveryPolicy {
    private DiscoveryPolicy() {
    }

    /**
     * Converts one sampled surface column. Index zero is the sky-exposed top;
     * remaining entries are side-exposed blocks below it.
     */
    public static DiscoveryResult surfaceColumn(List<VoxelSample> column) {
        List<DiscoveryResult.VoxelUpdate> updates = new ArrayList<>(column.size());
        for (int index = 0; index < column.size(); index++) {
            VoxelSample sample = column.get(index);
            DiscoveryState state = index == 0 ? DiscoveryState.EXPOSED_SURFACE : DiscoveryState.SOLID;
            updates.add(update(sample, state));
        }
        return new DiscoveryResult(updates);
    }

    /**
     * Converts air reached from the player and the solid blocks adjacent to it.
     * Air is classified as underground only when {@code underground} accepts it.
     */
    public static DiscoveryResult airPocket(WorldDataSnapshot observations, Predicate<VoxelSample> underground) {
        List<DiscoveryResult.VoxelUpdate> updates = new ArrayList<>(observations.samples().size());
        for (VoxelSample sample : observations.samples()) {
            if (sample.air()) {
                DiscoveryState state = underground.test(sample)
                        ? DiscoveryState.UNDERGROUND_SPACE
                        : DiscoveryState.KNOWN_AIR;
                updates.add(new DiscoveryResult.VoxelUpdate(
                        sample.x(), sample.y(), sample.z(), state, null));
            } else {
                updates.add(update(sample, DiscoveryState.SOLID));
            }
        }
        return new DiscoveryResult(updates);
    }

    private static DiscoveryResult.VoxelUpdate update(VoxelSample sample, DiscoveryState state) {
        return new DiscoveryResult.VoxelUpdate(sample.x(), sample.y(), sample.z(), state, sample.appearance());
    }
}
