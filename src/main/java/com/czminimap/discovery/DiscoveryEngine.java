package com.czminimap.discovery;

import com.czminimap.cartography.CartographicMap;
import com.czminimap.core.DimensionKey;
import com.czminimap.core.MapManager;
import com.czminimap.core.WorldKey;
import com.czminimap.world.BlockPositionKey;
import com.czminimap.world.ChunkDataCollector;
import com.czminimap.world.VoxelSample;
import com.czminimap.world.WorldDataSnapshot;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Orchestrates progressive discovery on the client game thread. */
public final class DiscoveryEngine {
    private final MapManager mapManager;
    private final ExplorationTracker tracker = new ExplorationTracker();
    private DimensionKey dimension;
    private long tick;

    public DiscoveryEngine(MapManager mapManager) {
        this.mapManager = mapManager;
    }

    /** Clears spatial tracking when the world, dimension or loaded data changes. */
    public void reset() {
        tracker.reset();
        this.dimension = null;
    }

    public void tick(ClientLevel level, Player player, WorldKey world, DimensionKey dimension,
                     DiscoverySettings settings) {
        if (mapManager.selectWorld(world) || !dimension.equals(this.dimension)) {
            tracker.reset();
            this.dimension = dimension;
        }
        tick = Math.max(tick, mapManager.map().clock()) + 1;

        BlockPos position = player.blockPosition();
        tracker.update(position.getX(), position.getZ(), settings.surfaceRadius(), settings.undergroundRadius());

        int remaining = settings.maxSamplesPerTick();
        int airBudget = tracker.isSurfaceComplete() ? remaining : Math.max(1, remaining / 2);
        remaining -= discoverAir(level, player, world, airBudget);
        discoverSurface(level, world, remaining);
        tick++;
    }

    private int discoverAir(ClientLevel level, Player player, WorldKey world, int budget) {
        if (budget <= 0) {
            return 0;
        }
        BlockPos feet = player.blockPosition();
        tracker.seed(feet.getX(), feet.getY(), feet.getZ());

        List<VoxelSample> observations = new ArrayList<>();
        int processed = 0;
        while (processed < budget) {
            Long key = tracker.pollAir();
            if (key == null) {
                break;
            }
            int x = BlockPositionKey.x(key);
            int y = BlockPositionKey.y(key);
            int z = BlockPositionKey.z(key);
            if (!dimension.containsY(y)) {
                continue;
            }
            Optional<VoxelSample> sampled = ChunkDataCollector.sample(level, x, y, z);
            if (sampled.isEmpty()) {
                continue;
            }
            processed++;
            VoxelSample sample = sampled.get();
            observations.add(sample);
            if (!sample.air() || !isUnderground(level, x, y, z)) {
                continue;
            }
            for (int[] offset : ExplorationTracker.FACE_NEIGHBOURS) {
                tracker.enqueueAir(x + offset[0], y + offset[1], z + offset[2]);
            }
        }

        if (!observations.isEmpty()) {
            WorldDataSnapshot snapshot = new WorldDataSnapshot(world, dimension, tick, observations);
            DiscoveryResult result = DiscoveryPolicy.airPocket(snapshot,
                    sample -> isUnderground(level, sample.x(), sample.y(), sample.z()));
            apply(world, result);
        }
        return processed;
    }

    private void discoverSurface(ClientLevel level, WorldKey world, int budget) {
        int used = 0;
        while (used < budget) {
            int[] column = tracker.nextSurfaceColumn();
            if (column == null) {
                return;
            }
            List<VoxelSample> samples = ChunkDataCollector.sampleSurfaceColumn(level, dimension, column[0], column[1]);
            used += Math.max(1, samples.size());
            if (!samples.isEmpty()) {
                apply(world, DiscoveryPolicy.surfaceColumn(samples));
            }
        }
    }

    private boolean isUnderground(ClientLevel level, int x, int y, int z) {
        if (!dimension.containsY(y)) {
            return false;
        }
        int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        return y < surfaceY;
    }

    private void apply(WorldKey world, DiscoveryResult result) {
        CartographicMap map = mapManager.map();
        for (DiscoveryResult.VoxelUpdate update : result.updates()) {
            map.setVoxel(world, dimension, update.x(), update.y(), update.z(),
                    update.state(), update.appearance(), tick);
        }
    }
}
