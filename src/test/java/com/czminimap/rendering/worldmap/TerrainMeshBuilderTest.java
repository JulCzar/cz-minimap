package com.czminimap.rendering.worldmap;

import com.czminimap.cartography.CartographicMap;
import com.czminimap.cartography.CartographicSnapshot;
import com.czminimap.cartography.DiscoveryState;
import com.czminimap.cartography.VoxelAppearance;
import com.czminimap.core.DimensionKey;
import com.czminimap.core.WorldKey;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerrainMeshBuilderTest {
    private static final WorldKey WORLD = new WorldKey("test_world_123456789abc", "Test World");
    private static final DimensionKey OVERWORLD = new DimensionKey(
            ResourceLocation.parse("minecraft:overworld"), -64, 320);
    private static final VoxelAppearance STONE = new VoxelAppearance(
            ResourceLocation.parse("minecraft:stone"), 0, ResourceLocation.parse("minecraft:plains"), 0);

    @Test
    void singleSolidProducesSixFaces() {
        CartographicMap map = new CartographicMap();
        map.setVoxel(WORLD, OVERWORLD, 0, 64, 0, DiscoveryState.EXPOSED_SURFACE, STONE, 1L);
        CartographicSnapshot snapshot = map.snapshot();

        TerrainMesh mesh = TerrainMeshBuilder.build(snapshot, WORLD, OVERWORLD, 0, 0, snapshot.version());

        assertEquals(snapshot.version(), mesh.version());
        assertEquals(24, mesh.vertexCount());
        assertEquals(24 * TerrainMesh.FLOATS_PER_VERTEX, mesh.vertices().length);
    }

    @Test
    void sharedFaceBetweenSolidsIsRemoved() {
        CartographicMap map = new CartographicMap();
        map.setVoxel(WORLD, OVERWORLD, 0, 64, 0, DiscoveryState.SOLID, STONE, 1L);
        map.setVoxel(WORLD, OVERWORLD, 1, 64, 0, DiscoveryState.SOLID, STONE, 2L);
        CartographicSnapshot snapshot = map.snapshot();

        TerrainMesh mesh = TerrainMeshBuilder.build(snapshot, WORLD, OVERWORLD, 0, 0, snapshot.version());

        assertEquals(40, mesh.vertexCount());
    }

    @Test
    void knownAirDoesNotBecomeGeometryAndDoesNotCullFaces() {
        CartographicMap map = new CartographicMap();
        map.setVoxel(WORLD, OVERWORLD, 0, 64, 0, DiscoveryState.SOLID, STONE, 1L);
        map.setVoxel(WORLD, OVERWORLD, 0, 65, 0, DiscoveryState.KNOWN_AIR, null, 2L);
        map.setVoxel(WORLD, OVERWORLD, 1, 64, 0, DiscoveryState.UNDERGROUND_SPACE, null, 3L);
        CartographicSnapshot snapshot = map.snapshot();

        TerrainMesh mesh = TerrainMeshBuilder.build(snapshot, WORLD, OVERWORLD, 0, 0, snapshot.version());

        assertEquals(24, mesh.vertexCount());
    }

    @Test
    void cullsAcrossChunkBoundaries() {
        CartographicMap map = new CartographicMap();
        map.setVoxel(WORLD, OVERWORLD, 15, 64, 0, DiscoveryState.SOLID, STONE, 1L);
        map.setVoxel(WORLD, OVERWORLD, 16, 64, 0, DiscoveryState.SOLID, STONE, 2L);
        CartographicSnapshot snapshot = map.snapshot();

        TerrainMesh first = TerrainMeshBuilder.build(snapshot, WORLD, OVERWORLD, 0, 0, snapshot.version());
        TerrainMesh second = TerrainMeshBuilder.build(snapshot, WORLD, OVERWORLD, 1, 0, snapshot.version());

        assertEquals(20, first.vertexCount());
        assertEquals(20, second.vertexCount());
    }

    @Test
    void unknownRegionProducesEmptyMesh() {
        CartographicMap map = new CartographicMap();
        CartographicSnapshot snapshot = map.snapshot();

        TerrainMesh mesh = TerrainMeshBuilder.build(snapshot, WORLD, OVERWORLD, 0, 0, snapshot.version());

        assertTrue(mesh.isEmpty());
        assertEquals(0, mesh.vertexCount());
        assertEquals(0, mesh.vertices().length);
    }

    @Test
    void vertexDataCarriesRgbaAndPosition() {
        CartographicMap map = new CartographicMap();
        map.setVoxel(WORLD, OVERWORLD, 2, 70, 3, DiscoveryState.SOLID, STONE, 1L);
        CartographicSnapshot snapshot = map.snapshot();

        TerrainMesh mesh = TerrainMeshBuilder.build(snapshot, WORLD, OVERWORLD, 0, 0, snapshot.version());

        for (int i = 0; i < mesh.vertices().length; i += TerrainMesh.FLOATS_PER_VERTEX) {
            assertEquals(1.0f, mesh.vertices()[i + 6]);
            assertTrue(mesh.vertices()[i + 3] >= 0.0f && mesh.vertices()[i + 3] <= 1.0f);
            assertTrue(mesh.vertices()[i] >= 2.0f && mesh.vertices()[i] <= 3.0f);
            assertTrue(mesh.vertices()[i + 1] >= 70.0f && mesh.vertices()[i + 1] <= 71.0f);
            assertTrue(mesh.vertices()[i + 2] >= 3.0f && mesh.vertices()[i + 2] <= 4.0f);
        }
    }
}
