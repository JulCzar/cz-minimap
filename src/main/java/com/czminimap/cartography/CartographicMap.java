package com.czminimap.cartography;

import com.czminimap.core.DimensionKey;
import com.czminimap.core.RegionKey;
import com.czminimap.core.WorldKey;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Mutable cartographic source of truth. This class is the only public write path. */
public final class CartographicMap {
    private final Map<RegionKey, Region> regions = new HashMap<>();
    private final Set<RegionKey> dirtyRegions = new HashSet<>();
    private long version;

    public long version() {
        return version;
    }

    public boolean isDirty() {
        return !dirtyRegions.isEmpty();
    }

    public Set<RegionKey> drainDirtyRegions() {
        Set<RegionKey> drained = Set.copyOf(dirtyRegions);
        dirtyRegions.clear();
        return drained;
    }

    public void setVoxel(WorldKey world, DimensionKey dimension, int x, int y, int z,
                         DiscoveryState state, VoxelAppearance appearance, long lastObservedTick) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(state, "state");
        if (!dimension.containsY(y)) {
            throw new IllegalArgumentException("Y " + y + " is outside " + dimension);
        }
        if (state == DiscoveryState.UNKNOWN) {
            return;
        }

        RegionKey key = regionKey(world, dimension, x, z);
        Region region = regions.computeIfAbsent(key, ignored -> new Region());
        if (region.setVoxel(x, y, z, state, appearance, lastObservedTick)) {
            version++;
            dirtyRegions.add(key);
        }
    }

    /** Read-only point lookup for the discovery producer, which runs on the game thread. */
    public DiscoveryState stateAt(WorldKey world, DimensionKey dimension, int x, int y, int z) {
        Region region = regions.get(regionKey(world, dimension, x, z));
        return region == null ? DiscoveryState.UNKNOWN : region.stateAt(x, y, z);
    }

    private static RegionKey regionKey(WorldKey world, DimensionKey dimension, int x, int z) {
        return new RegionKey(world, dimension, RegionKey.fromBlock(x), RegionKey.fromBlock(z));
    }

    public CartographicSnapshot snapshot() {
        Map<RegionKey, CartographicSnapshot.RegionView> view = new LinkedHashMap<>();
        regions.forEach((key, region) -> view.put(key, region.freeze()));
        return CartographicSnapshot.of(version, view);
    }

    private static final class Region {
        private final VoxelPalette palette = new VoxelPalette();
        private final Map<Long, Chunk> chunks = new HashMap<>();

        boolean setVoxel(int x, int y, int z, DiscoveryState state, VoxelAppearance appearance, long tick) {
            int chunkX = Math.floorDiv(x, 16);
            int chunkZ = Math.floorDiv(z, 16);
            Chunk chunk = chunks.computeIfAbsent(CartographicSnapshot.chunkKey(chunkX, chunkZ), ignored -> new Chunk());
            return chunk.setVoxel(x, y, z, state, palette.indexOf(appearance), tick);
        }

        DiscoveryState stateAt(int x, int y, int z) {
            Chunk chunk = chunks.get(CartographicSnapshot.chunkKey(Math.floorDiv(x, 16), Math.floorDiv(z, 16)));
            return chunk == null ? DiscoveryState.UNKNOWN : chunk.stateAt(x, y, z);
        }

        CartographicSnapshot.RegionView freeze() {
            Map<Long, CartographicSnapshot.ChunkView> frozenChunks = new LinkedHashMap<>();
            chunks.forEach((key, chunk) -> frozenChunks.put(key, chunk.freeze()));
            return new CartographicSnapshot.RegionView(palette.freeze(), frozenChunks);
        }
    }

    private static final class Chunk {
        private final Map<Integer, Section> sections = new HashMap<>();

        boolean setVoxel(int x, int y, int z, DiscoveryState state, int paletteIndex, long tick) {
            Section section = sections.computeIfAbsent(Math.floorDiv(y, 16), ignored -> new Section());
            return section.setVoxel(x, y, z, state, paletteIndex, tick);
        }

        DiscoveryState stateAt(int x, int y, int z) {
            Section section = sections.get(Math.floorDiv(y, 16));
            return section == null ? DiscoveryState.UNKNOWN : section.stateAt(x, y, z);
        }

        CartographicSnapshot.ChunkView freeze() {
            Map<Integer, CartographicSnapshot.SectionView> frozenSections = new LinkedHashMap<>();
            sections.forEach((key, section) -> frozenSections.put(key, section.freeze()));
            return new CartographicSnapshot.ChunkView(frozenSections);
        }
    }

    private static final class Section {
        private final byte[] states = new byte[16 * 16 * 16];
        private final int[] paletteIndices = new int[16 * 16 * 16];
        private long lastObservedTick = Long.MIN_VALUE;

        private Section() {
            Arrays.fill(paletteIndices, -1);
        }

        boolean setVoxel(int x, int y, int z, DiscoveryState state, int paletteIndex, long tick) {
            int index = CartographicSnapshot.localIndex(x, y, z);
            byte encoded = (byte) state.ordinal();
            boolean changed = states[index] != encoded || paletteIndices[index] != paletteIndex;
            states[index] = encoded;
            paletteIndices[index] = paletteIndex;
            lastObservedTick = Math.max(lastObservedTick, tick);
            return changed;
        }

        DiscoveryState stateAt(int x, int y, int z) {
            byte encoded = states[CartographicSnapshot.localIndex(x, y, z)];
            return encoded < 0 || encoded >= DiscoveryState.values().length
                    ? DiscoveryState.UNKNOWN
                    : DiscoveryState.values()[encoded];
        }

        CartographicSnapshot.SectionView freeze() {
            return new CartographicSnapshot.SectionView(states, paletteIndices, lastObservedTick);
        }
    }
}
