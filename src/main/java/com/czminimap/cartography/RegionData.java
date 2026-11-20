package com.czminimap.cartography;

import java.util.List;
import java.util.Map;

/**
 * Immutable, storage-ready copy of one region. Arrays are cloned on construction and on access so a
 * background writer never observes mutable cartographic state.
 */
public record RegionData(List<VoxelAppearance> palette, Map<Long, ChunkData> chunks) {
    private static final RegionData EMPTY = new RegionData(List.of(), Map.of());

    public RegionData {
        palette = List.copyOf(palette);
        chunks = Map.copyOf(chunks);
    }

    public static RegionData empty() {
        return EMPTY;
    }

    public boolean isEmpty() {
        return chunks.isEmpty();
    }

    public record ChunkData(Map<Integer, SectionData> sections) {
        public ChunkData {
            sections = Map.copyOf(sections);
        }
    }

    public record SectionData(byte[] states, int[] paletteIndices, long lastObservedTick) {
        public static final int VOXELS = 16 * 16 * 16;

        public SectionData {
            if (states.length != VOXELS || paletteIndices.length != VOXELS) {
                throw new IllegalArgumentException("Section arrays must contain " + VOXELS + " entries");
            }
            states = states.clone();
            paletteIndices = paletteIndices.clone();
        }

        @Override
        public byte[] states() {
            return states.clone();
        }

        @Override
        public int[] paletteIndices() {
            return paletteIndices.clone();
        }
    }
}
