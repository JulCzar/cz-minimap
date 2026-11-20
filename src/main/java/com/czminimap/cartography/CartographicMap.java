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

        RegionKey key = new RegionKey(world, dimension, RegionKey.fromBlock(x), RegionKey.fromBlock(z));
        Region region = regions.computeIfAbsent(key, ignored -> new Region());
        if (region.setVoxel(x, y, z, state, appearance, lastObservedTick)) {
            version++;
            dirtyRegions.add(key);
        }
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

        CartographicSnapshot.SectionView freeze() {
            return new CartographicSnapshot.SectionView(states, paletteIndices, lastObservedTick);
        }
    }
}
