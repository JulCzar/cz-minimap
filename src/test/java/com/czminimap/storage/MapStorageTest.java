package com.czminimap.storage;

import com.czminimap.cartography.CartographicMap;
import com.czminimap.cartography.DiscoveryState;
import com.czminimap.cartography.RegionData;
import com.czminimap.cartography.VoxelAppearance;
import com.czminimap.core.DimensionKey;
import com.czminimap.core.RegionKey;
import com.czminimap.core.WorldKey;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapStorageTest {
    private static final WorldKey WORLD = new WorldKey("test_world_123456789abc", "Test World");
    private static final WorldKey OTHER_WORLD = new WorldKey("other_world_abcdef123456", "Other World");
    private static final DimensionKey OVERWORLD = new DimensionKey(
            ResourceLocation.parse("minecraft:overworld"), -64, 320);
    private static final RegionKey REGION = new RegionKey(WORLD, OVERWORLD, 0, 0);
    private static final RegionKey OTHER_REGION = new RegionKey(OTHER_WORLD, OVERWORLD, 0, 0);
    private static final VoxelAppearance STONE = new VoxelAppearance(
            ResourceLocation.parse("minecraft:stone"), 0, ResourceLocation.parse("minecraft:plains"), 0);

    @TempDir
    Path temp;

    @Test
    void saveAndLoadRoundTrip() throws Exception {
        Path root = temp.resolve("czminimap");
        try (MapStorage storage = new MapStorage(root)) {
            CartographicMap map = new CartographicMap();
            map.setVoxel(WORLD, OVERWORLD, 8, 12, 4, DiscoveryState.EXPOSED_SURFACE, STONE, 91L);
            assertTrue(storage.enqueueSave(REGION, map.regionData(REGION)));
            storage.flush();
        }

        try (MapStorage storage = new MapStorage(root)) {
            long token = storage.requestLoad(WORLD);
            MapStorage.LoadBatch batch = awaitBatch(storage, token);
            assertEquals(1, batch.regions().size());

            CartographicMap restored = new CartographicMap();
            for (MapStorage.LoadedRegion loaded : batch.regions()) {
                RegionKey saved = loaded.key();
                restored.loadRegion(new RegionKey(WORLD, saved.dimension(), saved.x(), saved.z()), loaded.data());
            }
            assertEquals(DiscoveryState.EXPOSED_SURFACE,
                    restored.snapshot().stateAt(WORLD, OVERWORLD, 8, 12, 4));
        }
    }

    @Test
    void worldsDoNotShareRegions() throws Exception {
        Path root = temp.resolve("czminimap");
        try (MapStorage storage = new MapStorage(root)) {
            CartographicMap map = new CartographicMap();
            map.setVoxel(WORLD, OVERWORLD, 0, 0, 0, DiscoveryState.SOLID, STONE, 1L);
            map.setVoxel(OTHER_WORLD, OVERWORLD, 0, 0, 0, DiscoveryState.KNOWN_AIR, null, 2L);
            assertTrue(storage.enqueueSave(REGION, map.regionData(REGION)));
            assertTrue(storage.enqueueSave(OTHER_REGION, map.regionData(OTHER_REGION)));
            storage.flush();
        }

        try (MapStorage storage = new MapStorage(root)) {
            long token = storage.requestLoad(WORLD);
            List<MapStorage.LoadedRegion> regions = awaitBatch(storage, token).regions();
            assertEquals(1, regions.size());
            assertEquals(WORLD.storageId(), regions.getFirst().key().world().storageId());
            var section = regions.getFirst().data().chunks().values().iterator().next()
                    .sections().values().iterator().next();
            assertEquals(DiscoveryState.SOLID.ordinal(), section.states()[0]);
        }
    }

    @Test
    void corruptFileIsQuarantinedWithoutCrashing() throws Exception {
        Path root = temp.resolve("czminimap");
        try (MapStorage storage = new MapStorage(root)) {
            CartographicMap map = new CartographicMap();
            map.setVoxel(WORLD, OVERWORLD, 0, 0, 0, DiscoveryState.SOLID, STONE, 1L);
            assertTrue(storage.enqueueSave(REGION, map.regionData(REGION)));
            storage.flush();
        }

        Path file = findFile(root, "r.0.0.bin");
        Files.write(file, new byte[]{1, 2, 3, 4, 5});

        try (MapStorage storage = new MapStorage(root)) {
            long token = storage.requestLoad(WORLD);
            assertTrue(awaitBatch(storage, token).regions().isEmpty());
        }
        assertTrue(Files.exists(file.resolveSibling("r.0.0.bin.corrupt")));
    }

    @Test
    void recoversInterruptedWriteFromTemporaryFile() throws Exception {
        Path root = temp.resolve("czminimap");
        try (MapStorage storage = new MapStorage(root)) {
            CartographicMap map = new CartographicMap();
            map.setVoxel(WORLD, OVERWORLD, 0, 0, 0, DiscoveryState.SOLID, STONE, 1L);
            assertTrue(storage.enqueueSave(REGION, map.regionData(REGION)));
            storage.flush();
        }

        Path file = findFile(root, "r.0.0.bin");
        Path temporary = file.resolveSibling("r.0.0.bin.tmp");
        Files.copy(file, temporary, StandardCopyOption.REPLACE_EXISTING);
        Files.delete(file);

        try (MapStorage storage = new MapStorage(root)) {
            long token = storage.requestLoad(WORLD);
            assertEquals(1, awaitBatch(storage, token).regions().size());
        }
        assertTrue(Files.exists(file));
        assertFalse(Files.exists(temporary));
    }

    @Test
    void rejectsNonExistentStorageRootGracefully() {
        MapStorage storage = new MapStorage(temp.resolve("missing"));
        long token = storage.requestLoad(WORLD);
        assertTrue(awaitBatchUnchecked(storage, token).regions().isEmpty());
        storage.close();
        assertThrows(NullPointerException.class, () -> new MapStorage(null));
    }

    private static MapStorage.LoadBatch awaitBatch(MapStorage storage, long token) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            MapStorage.LoadBatch batch = storage.pollLoaded();
            if (batch != null && batch.token() == token) {
                return batch;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("Load batch was not published");
    }

    private static MapStorage.LoadBatch awaitBatchUnchecked(MapStorage storage, long token) {
        try {
            return awaitBatch(storage, token);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }

    private static Path findFile(Path root, String name) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(path -> path.getFileName().toString().equals(name))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Missing file " + name));
        }
    }
}
