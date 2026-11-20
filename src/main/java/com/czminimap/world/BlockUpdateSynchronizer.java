package com.czminimap.world;

import com.czminimap.cartography.CartographicMap;
import com.czminimap.cartography.DiscoveryState;
import com.czminimap.core.DimensionKey;
import com.czminimap.core.WorldKey;
import net.minecraft.client.multiplayer.ClientLevel;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Incrementally reconciles already-discovered voxels with loaded client chunk state. */
public final class BlockUpdateSynchronizer {
    private static final int[][] FACE_NEIGHBOURS = {
            {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}
    };
    private static final int POSITIONS_PER_CHUNK_PASS = 64;

    private record ChunkCursorKey(int x, int z) {
    }

    private record KnownChunk(long revision, List<Long> positions) {
    }

    private final ArrayDeque<Long> pendingPositions = new ArrayDeque<>();
    private final Map<ChunkCursorKey, Integer> chunkOffsets = new HashMap<>();
    private final Map<ChunkCursorKey, KnownChunk> knownChunks = new HashMap<>();
    private List<int[]> chunks = List.of();
    private DimensionKey dimension;
    private int centerChunkX = Integer.MIN_VALUE;
    private int centerChunkZ = Integer.MIN_VALUE;
    private int radius = -1;
    private int chunkCursor;

    public void reset() {
        pendingPositions.clear();
        chunkOffsets.clear();
        knownChunks.clear();
        chunks = List.of();
        dimension = null;
        centerChunkX = Integer.MIN_VALUE;
        centerChunkZ = Integer.MIN_VALUE;
        radius = -1;
        chunkCursor = 0;
    }

    public int tick(ClientLevel level, WorldKey world, DimensionKey dimension,
                    CartographicMap map, int scanRadiusChunks, int sampleBudget,
                    int playerBlockX, int playerBlockZ) {
        int chunkX = Math.floorDiv(playerBlockX, 16);
        int chunkZ = Math.floorDiv(playerBlockZ, 16);
        int requestedRadius = Math.max(1, scanRadiusChunks);
        if (!dimension.equals(this.dimension)
                || chunkX != centerChunkX || chunkZ != centerChunkZ || requestedRadius != radius) {
            rebase(dimension, chunkX, chunkZ, requestedRadius);
        }

        int checked = 0;
        int updates = 0;
        while (checked < Math.max(0, sampleBudget)) {
            if (pendingPositions.isEmpty() && !fillNextChunk(map, world, dimension)) {
                break;
            }
            Long packed = pendingPositions.pollFirst();
            if (packed == null) {
                break;
            }
            int x = BlockPositionKey.x(packed);
            int y = BlockPositionKey.y(packed);
            int z = BlockPositionKey.z(packed);
            if (!dimension.containsY(y)) {
                continue;
            }
            var sampled = ChunkDataCollector.sample(level, x, y, z);
            if (sampled.isEmpty()) {
                continue;
            }
            checked++;
            long observationTick = Math.max(map.clock(), level.getGameTime()) + updates + 1L;
            if (applyObservation(map, world, dimension, sampled.get(), observationTick)) {
                updates++;
            }
        }
        return updates;
    }

    public static boolean applyObservation(CartographicMap map, WorldKey world, DimensionKey dimension,
                                           VoxelSample sample, long observationTick) {
        DiscoveryState current = map.stateAt(world, dimension, sample.x(), sample.y(), sample.z());
        if (current == DiscoveryState.UNKNOWN) {
            return false;
        }
        if (sample.air()) {
            DiscoveryState air = current == DiscoveryState.UNDERGROUND_SPACE
                    ? DiscoveryState.UNDERGROUND_SPACE
                    : DiscoveryState.KNOWN_AIR;
            return map.setVoxel(world, dimension, sample.x(), sample.y(), sample.z(),
                    air, null, observationTick);
        }

        DiscoveryState solid = current == DiscoveryState.EXPOSED_SURFACE
                || current == DiscoveryState.KNOWN_AIR
                || current == DiscoveryState.UNDERGROUND_SPACE
                || touchesKnownAir(map, world, dimension, sample.x(), sample.y(), sample.z())
                ? DiscoveryState.EXPOSED_SURFACE
                : DiscoveryState.SOLID;
        return map.setVoxel(world, dimension, sample.x(), sample.y(), sample.z(),
                solid, sample.appearance(), observationTick);
    }

    private static boolean touchesKnownAir(CartographicMap map, WorldKey world, DimensionKey dimension,
                                           int x, int y, int z) {
        for (int[] offset : FACE_NEIGHBOURS) {
            int neighbourY = y + offset[1];
            if (!dimension.containsY(neighbourY)) {
                continue;
            }
            DiscoveryState state = map.stateAt(world, dimension,
                    x + offset[0], neighbourY, z + offset[2]);
            if (state == DiscoveryState.KNOWN_AIR || state == DiscoveryState.UNDERGROUND_SPACE) {
                return true;
            }
        }
        return false;
    }

    private void rebase(DimensionKey dimension, int centerChunkX, int centerChunkZ, int radius) {
        this.dimension = dimension;
        this.centerChunkX = centerChunkX;
        this.centerChunkZ = centerChunkZ;
        this.radius = radius;
        this.chunkCursor = 0;
        pendingPositions.clear();
        chunkOffsets.clear();
        knownChunks.clear();

        ArrayList<int[]> ordered = new ArrayList<>((radius * 2 + 1) * (radius * 2 + 1));
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                ordered.add(new int[]{centerChunkX + dx, centerChunkZ + dz});
            }
        }
        ordered.sort(Comparator.comparingInt(chunk -> {
            int dx = chunk[0] - centerChunkX;
            int dz = chunk[1] - centerChunkZ;
            return dx * dx + dz * dz;
        }));
        chunks = List.copyOf(ordered);
    }

    private boolean fillNextChunk(CartographicMap map, WorldKey world, DimensionKey dimension) {
        int attempts = chunks.size();
        while (attempts-- > 0) {
            int[] chunk = chunks.get(chunkCursor++);
            if (chunkCursor >= chunks.size()) {
                chunkCursor = 0;
            }
            ChunkCursorKey key = new ChunkCursorKey(chunk[0], chunk[1]);
            long revision = map.chunkRevision(world, dimension, chunk[0], chunk[1]);
            KnownChunk cached = knownChunks.get(key);
            if (cached == null || cached.revision() != revision) {
                cached = new KnownChunk(revision,
                        map.knownPositionsInChunk(world, dimension, chunk[0], chunk[1]));
                knownChunks.put(key, cached);
            }
            List<Long> known = cached.positions();
            if (!known.isEmpty()) {
                int offset = Math.floorMod(chunkOffsets.getOrDefault(key, 0), known.size());
                int count = Math.min(POSITIONS_PER_CHUNK_PASS, known.size());
                for (int i = 0; i < count; i++) {
                    pendingPositions.addLast(known.get((offset + i) % known.size()));
                }
                chunkOffsets.put(key, (offset + count) % known.size());
                return true;
            }
        }
        return false;
    }
}
