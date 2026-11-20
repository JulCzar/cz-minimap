package com.czminimap.rendering.worldmap;

/** Immutable CPU-side mesh of one chunk, derived from a cartographic snapshot version. */
public record TerrainMesh(int chunkX, int chunkZ, long version, float[] vertices, int vertexCount) {
    public static final int FLOATS_PER_VERTEX = 7;

    public boolean isEmpty() {
        return vertexCount == 0;
    }
}
