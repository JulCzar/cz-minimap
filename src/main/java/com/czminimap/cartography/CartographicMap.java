package com.czminimap.cartography;

import com.czminimap.core.DimensionKey;
import com.czminimap.core.RegionKey;
import com.czminimap.core.WorldKey;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Mutable cartographic source of truth. This class is the only public write path. */
public final class CartographicMap {
    private final Map<RegionKey, Region> regions = new HashMap<>();
    private final Set<RegionKey> dirtyRegions = new HashSet<>();
    private long version;
    private long clock;

    public long version() {
        return version;
    }

    /** Highest observation tick ever seen; keeps discovery timestamps monotonic across sessions. */
    public long clock() {
        return clock;
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
            clock = Math.max(clock, lastObservedTick);
            version++;
            dirtyRegions.add(key);
        }
    }

    /** Read-only point lookup for the discovery producer, which runs on the game thread. */
    public DiscoveryState stateAt(WorldKey world, DimensionKey dimension, int x, int y, int z) {
        Region region = regions.get(regionKey(world, dimension, x, z));
        return region == null ? DiscoveryState.UNKNOWN : region.stateAt(x, y, z);
    }

    /** Immutable copy for the storage worker. Never returns a view of live arrays. */
    public RegionData regionData(RegionKey key) {
        Objects.requireNonNull(key, "key");
        Region region = regions.get(key);
        return region == null ? RegionData.empty() : region.freezeData();
    }

    /** Merges persisted data. Loaded data is never marked dirty and never overwrites newer observations. */
    public void loadRegion(RegionKey key, RegionData data) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(data, "data");
        if (data.isEmpty()) {
            return;
        }
        for (RegionData.ChunkData chunk : data.chunks().values()) {
            for (RegionData.SectionData section : chunk.sections().values()) {
                clock = Math.max(clock, section.lastObservedTick());
            }
        }
        Region region = regions.computeIfAbsent(key, ignored -> new Region());
        if (region.load(data)) {
            version++;
        }
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

        boolean load(RegionData data) {
            int[] remap = new int[data.palette().size()];
            for (int i = 0; i < remap.length; i++) {
                remap[i] = palette.indexOf(data.palette().get(i));
            }

            boolean changed = false;
            for (Map.Entry<Long, RegionData.ChunkData> chunkEntry : data.chunks().entrySet()) {
                Chunk chunk = chunks.computeIfAbsent(chunkEntry.getKey(), ignored -> new Chunk());
                for (Map.Entry<Integer, RegionData.SectionData> sectionEntry : chunkEntry.getValue().sections().entrySet()) {
                    int sectionY = sectionEntry.getKey();
                    RegionData.SectionData incoming = sectionEntry.getValue();
                    if (chunk.sectionTick(sectionY) >= incoming.lastObservedTick()) {
                        continue;
                    }
                    chunk.sections.put(sectionY, Section.from(incoming, remap));
                    changed = true;
                }
            }
            return changed;
        }

        RegionData freezeData() {
            List<VoxelAppearance> frozenPalette = palette.freeze();
            Map<Long, RegionData.ChunkData> frozenChunks = new LinkedHashMap<>();
            chunks.forEach((key, chunk) -> frozenChunks.put(key, chunk.freezeData()));
            return new RegionData(frozenPalette, frozenChunks);
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

        long sectionTick(int sectionY) {
            Section section = sections.get(sectionY);
            return section == null ? Long.MIN_VALUE : section.lastObservedTick;
        }

        RegionData.ChunkData freezeData() {
            Map<Integer, RegionData.SectionData> frozen = new LinkedHashMap<>();
            sections.forEach((key, section) -> frozen.put(key,
                    new RegionData.SectionData(section.states, section.paletteIndices, section.lastObservedTick)));
            return new RegionData.ChunkData(frozen);
        }

        CartographicSnapshot.ChunkView freeze() {
            Map<Integer, CartographicSnapshot.SectionView> frozenSections = new LinkedHashMap<>();
            sections.forEach((key, section) -> frozenSections.put(key, section.freeze()));
            return new CartographicSnapshot.ChunkView(frozenSections);
        }
    }

    private static final class Section {
        private byte[] states = new byte[16 * 16 * 16];
        private int[] paletteIndices = new int[16 * 16 * 16];
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

        static Section from(RegionData.SectionData data, int[] remap) {
            Section section = new Section();
            section.states = data.states();
            int[] source = data.paletteIndices();
            for (int i = 0; i < source.length; i++) {
                int index = source[i];
                section.paletteIndices[i] = index < 0 || index >= remap.length ? -1 : remap[index];
            }
            section.lastObservedTick = data.lastObservedTick();
            return section;
        }

        DiscoveryState stateAt(int x, int y, int z) {
            byte encoded = states[CartographicSnapshot.localIndex(x, y, z)];
            return encoded < 0 || encoded >= DiscoveryState.values().length
                    ? DiscoveryState.UNKNOWN
                    : DiscoveryState.values()[encoded];
        }

        RegionData.SectionData freezeData() {
            return new RegionData.SectionData(states, paletteIndices, lastObservedTick);
        }

        CartographicSnapshot.SectionView freeze() {
            return new CartographicSnapshot.SectionView(states, paletteIndices, lastObservedTick);
        }
    }
}
