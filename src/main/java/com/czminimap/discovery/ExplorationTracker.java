package com.czminimap.discovery;

import com.czminimap.world.BlockPositionKey;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/** Bounded, deterministic work queues for surface columns and underground air. */
public final class ExplorationTracker {
    /** Six face neighbours; underground expansion never uses diagonals. */
    public static final int[][] FACE_NEIGHBOURS = {
            {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}
    };

    private static final int REBASE_DISTANCE = 8;
    private static final int MAX_QUEUED_AIR = 16_384;

    private int centerX;
    private int centerZ;
    private int surfaceRadius;
    private int surfaceIndex;
    private int undergroundRadius;
    private int frontierX;
    private int frontierZ;
    private boolean seeded;
    private final ArrayDeque<Long> airFrontier = new ArrayDeque<>();
    private final Set<Long> queuedAir = new HashSet<>();

    /** Aligns the queues with the player, rebasing the sweep after meaningful movement. */
    public void update(int x, int z, int surfaceRadius, int undergroundRadius) {
        boolean moved = Math.abs(x - centerX) > REBASE_DISTANCE || Math.abs(z - centerZ) > REBASE_DISTANCE;
        boolean resized = surfaceRadius != this.surfaceRadius || undergroundRadius != this.undergroundRadius;
        if (surfaceIndex == 0 && !seeded && airFrontier.isEmpty()) {
            setCenter(x, z, surfaceRadius, undergroundRadius);
            return;
        }
        if (moved || resized) {
            reset();
            setCenter(x, z, surfaceRadius, undergroundRadius);
        }
    }

    public void reset() {
        surfaceIndex = 0;
        seeded = false;
        airFrontier.clear();
        queuedAir.clear();
    }

    /** Seeds the underground frontier around the player's own block once per sweep. */
    public void seed(int x, int y, int z) {
        if (seeded) {
            return;
        }
        seeded = true;
        enqueueAir(x, y, z);
        for (int[] offset : FACE_NEIGHBOURS) {
            enqueueAir(x + offset[0], y + offset[1], z + offset[2]);
        }
    }

    public boolean isSurfaceComplete() {
        int side = surfaceRadius * 2 + 1;
        return surfaceIndex >= side * side;
    }

    /** Next column offset from the player, or {@code null} when the sweep is complete. */
    public int[] nextSurfaceColumn() {
        int side = surfaceRadius * 2 + 1;
        if (surfaceIndex >= side * side) {
            return null;
        }
        int[] offset = spiralOffset(surfaceIndex++);
        return new int[]{centerX + offset[0], centerZ + offset[1]};
    }

    public void enqueueAir(int x, int y, int z) {
        if (Math.abs(x - frontierX) > undergroundRadius || Math.abs(z - frontierZ) > undergroundRadius) {
            return;
        }
        if (queuedAir.size() >= MAX_QUEUED_AIR) {
            return;
        }
        long key = BlockPositionKey.encode(x, y, z);
        if (queuedAir.add(key)) {
            airFrontier.addLast(key);
        }
    }

    public Long pollAir() {
        Long key = airFrontier.pollFirst();
        return key == null ? null : key;
    }

    private void setCenter(int x, int z, int surfaceRadius, int undergroundRadius) {
        centerX = x;
        centerZ = z;
        frontierX = x;
        frontierZ = z;
        this.surfaceRadius = surfaceRadius;
        this.undergroundRadius = undergroundRadius;
    }

    /**
     * Maps a spiral index to an offset. Index zero is the origin; ring {@code r}
     * contains the {@code 8r} positions at Chebyshev distance {@code r}.
     */
    private static int[] spiralOffset(int index) {
        if (index == 0) {
            return new int[]{0, 0};
        }
        int ring = 1;
        int consumed = 1;
        while (index >= consumed + 8 * ring) {
            consumed += 8 * ring;
            ring++;
        }
        int offset = index - consumed;
        int sideLength = 2 * ring;
        int side = offset / sideLength;
        int step = offset % sideLength;
        return switch (side) {
            case 0 -> new int[]{-ring + 1 + step, -ring};
            case 1 -> new int[]{ring, -ring + 1 + step};
            case 2 -> new int[]{ring - 1 - step, ring};
            default -> new int[]{-ring, ring - 1 - step};
        };
    }
}
