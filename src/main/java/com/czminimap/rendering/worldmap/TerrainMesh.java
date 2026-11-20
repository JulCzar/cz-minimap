package com.czminimap.rendering.worldmap;

import com.czminimap.lod.LodLevel;

/** Immutable CPU-side mesh of one chunk, derived from a snapshot revision and LOD. */
public record TerrainMesh(int chunkX, int chunkZ, LodLevel lodLevel, long version,
                          float[] vertices, int vertexCount) {
    public static final int FLOATS_PER_VERTEX = 7;

    public boolean isEmpty() {
        return vertexCount == 0;
    }
}
