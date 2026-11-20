package com.czminimap.lod;

/** Voxel-aligned horizontal aggregation levels used by the 3D map. */
public enum LodLevel {
    LOD_0(0, 1),
    LOD_1(1, 2),
    LOD_2(2, 4),
    LOD_3(3, 8);

    private final int index;
    private final int blockSize;

    LodLevel(int index, int blockSize) {
        this.index = index;
        this.blockSize = blockSize;
    }

    public int index() {
        return index;
    }

    public int blockSize() {
        return blockSize;
    }

    public static LodLevel fromIndex(int index) {
        return values()[Math.clamp(index, 0, values().length - 1)];
    }
}
