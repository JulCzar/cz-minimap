package com.czminimap.cartography;

import com.czminimap.core.DimensionKey;
import com.czminimap.core.RegionKey;
import com.czminimap.core.WorldKey;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Immutable, versioned view of the cartographic model. Caches are keyed by its version. */
public final class CartographicSnapshot {
    private final long version;
    private final Map<RegionKey, RegionView> regions;

    private CartographicSnapshot(long version, Map<RegionKey, RegionView> regions) {
        this.version = version;
        this.regions = Map.copyOf(regions);
    }

    static CartographicSnapshot of(long version, Map<RegionKey, RegionView> regions) {
        return new CartographicSnapshot(version, regions);
    }

    public long version() {
        return version;
    }

    public Set<RegionKey> regions() {
        return regions.keySet();
    }

    public DiscoveryState stateAt(WorldKey world, DimensionKey dimension, int x, int y, int z) {
        SectionView section = sectionAt(world, dimension, x, y, z);
        return section == null ? DiscoveryState.UNKNOWN : section.state(x, y, z);
    }

    public Optional<VoxelAppearance> appearanceAt(WorldKey world, DimensionKey dimension, int x, int y, int z) {
        RegionView region = regionAt(world, dimension, x, z);
        SectionView section = sectionAt(region, y, x, z);
        if (region == null || section == null) {
            return Optional.empty();
        }
        int paletteIndex = section.paletteIndex(x, y, z);
        return paletteIndex < 0 ? Optional.empty() : Optional.ofNullable(region.palette.get(paletteIndex));
    }

    public long lastObservedTickAt(WorldKey world, DimensionKey dimension, int x, int y, int z) {
        SectionView section = sectionAt(world, dimension, x, y, z);
        return section == null ? Long.MIN_VALUE : section.lastObservedTick;
    }

    private RegionView regionAt(WorldKey world, DimensionKey dimension, int x, int z) {
        return regions.get(new RegionKey(world, dimension, RegionKey.fromBlock(x), RegionKey.fromBlock(z)));
    }

    private SectionView sectionAt(WorldKey world, DimensionKey dimension, int x, int y, int z) {
        return sectionAt(regionAt(world, dimension, x, z), y, x, z);
    }

    private static SectionView sectionAt(RegionView region, int y, int x, int z) {
        if (region == null) {
            return null;
        }
        long chunkKey = chunkKey(Math.floorDiv(x, 16), Math.floorDiv(z, 16));
        ChunkView chunk = region.chunks.get(chunkKey);
        return chunk == null ? null : chunk.sections.get(Math.floorDiv(y, 16));
    }

    static long chunkKey(int chunkX, int chunkZ) {
        return (chunkX & 0xFFFFFFFFL) | ((chunkZ & 0xFFFFFFFFL) << 32);
    }

    static int localIndex(int x, int y, int z) {
        return (Math.floorMod(y, 16) << 8) | (Math.floorMod(z, 16) << 4) | Math.floorMod(x, 16);
    }

    static final class RegionView {
        final List<VoxelAppearance> palette;
        final Map<Long, ChunkView> chunks;

        RegionView(List<VoxelAppearance> palette, Map<Long, ChunkView> chunks) {
            this.palette = List.copyOf(palette);
            this.chunks = Map.copyOf(chunks);
        }
    }

    static final class ChunkView {
        final Map<Integer, SectionView> sections;

        ChunkView(Map<Integer, SectionView> sections) {
            this.sections = Map.copyOf(sections);
        }
    }

    static final class SectionView {
        private final byte[] states;
        private final int[] paletteIndices;
        final long lastObservedTick;

        SectionView(byte[] states, int[] paletteIndices, long lastObservedTick) {
            this.states = states.clone();
            this.paletteIndices = paletteIndices.clone();
            this.lastObservedTick = lastObservedTick;
        }

        DiscoveryState state(int x, int y, int z) {
            byte encoded = states[CartographicSnapshot.localIndex(x, y, z)];
            return encoded < 0 || encoded >= DiscoveryState.values().length
                    ? DiscoveryState.UNKNOWN
                    : DiscoveryState.values()[encoded];
        }

        int paletteIndex(int x, int y, int z) {
            return paletteIndices[CartographicSnapshot.localIndex(x, y, z)];
        }
    }
}
