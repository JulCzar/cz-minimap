package com.czminimap.rendering.worldmap;

import com.czminimap.cartography.CartographicSnapshot;
import com.czminimap.core.DimensionKey;
import com.czminimap.core.WorldKey;
import com.czminimap.cutaway.CutawaySettings;
import com.czminimap.layers.CumulativeDirection;
import com.czminimap.layers.LayerMode;
import com.czminimap.layers.LayerResolver;
import com.czminimap.layers.LayerVisibility;
import com.czminimap.lod.LodLevel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/** LRU mesh cache with bounded asynchronous LOD construction. */
public final class WorldMapMeshCache implements AutoCloseable {
    private record ChunkKey(int x, int z) {
    }

    private record Entry(TerrainMesh mesh, LayerVisibility visibility) {
    }

    private record BuildKey(long generation, ChunkKey chunk, long revision, LodLevel lodLevel) {
    }

    private record Completed(BuildKey key, TerrainMesh mesh, LayerVisibility visibility) {
    }

    private final LinkedHashMap<ChunkKey, Entry> meshes = new LinkedHashMap<>(64, 0.75f, true);
    private final Set<BuildKey> pending = ConcurrentHashMap.newKeySet();
    private final ConcurrentLinkedQueue<Completed> completed = new ConcurrentLinkedQueue<>();
    private final Map<ChunkKey, BuildKey> latestDesired = new ConcurrentHashMap<>();
    private final AtomicLong generation = new AtomicLong();
    private final ThreadPoolExecutor executor;

    private int limit = 256;
    private long byteLimit = 128L * 1024L * 1024L;
    private long estimatedBytes;
    private WorldKey contextWorld;
    private DimensionKey contextDimension;
    private LayerVisibility contextVisibility;

    public WorldMapMeshCache() {
        int workers = Math.max(1, Math.min(2, Runtime.getRuntime().availableProcessors() - 1));
        executor = new ThreadPoolExecutor(workers, workers, 30L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(4096), runnable -> {
                    Thread thread = new Thread(runnable, "czminimap-lod-builder");
                    thread.setDaemon(true);
                    return thread;
                });
        executor.allowCoreThreadTimeOut(true);
    }

    public void setLimit(int newLimit) {
        limit = Math.max(16, newLimit);
        evict();
    }

    public void setByteLimit(long bytes) {
        byteLimit = Math.max(1024L * 1024L, bytes);
        evict();
    }

    /** Changes presentation context and cancels queued work that can no longer be displayed. */
    public void setContext(WorldKey world, DimensionKey dimension, LayerVisibility visibility) {
        if (Objects.equals(world, contextWorld)
                && Objects.equals(dimension, contextDimension)
                && Objects.equals(visibility, contextVisibility)) {
            return;
        }
        contextWorld = world;
        contextDimension = dimension;
        contextVisibility = visibility;
        clear();
    }

    public boolean needsRebuild(CartographicSnapshot snapshot, int chunkX, int chunkZ) {
        Entry cached = meshes.get(new ChunkKey(chunkX, chunkZ));
        return cached == null
                || cached.mesh().version() != snapshot.version()
                || cached.mesh().lodLevel() != LodLevel.LOD_0
                || cached.visibility().mode() != LayerMode.FULL;
    }

    public boolean needsRebuild(CartographicSnapshot snapshot, LayerVisibility visibility,
                                int chunkX, int chunkZ) {
        Entry cached = meshes.get(new ChunkKey(chunkX, chunkZ));
        return cached == null
                || cached.mesh().version() != snapshot.version()
                || cached.mesh().lodLevel() != LodLevel.LOD_0
                || !cached.visibility().equals(visibility);
    }

    public boolean needsRebuild(CartographicSnapshot snapshot, WorldKey world, DimensionKey dimension,
                                LayerVisibility visibility, LodLevel lodLevel, int chunkX, int chunkZ) {
        Entry cached = meshes.get(new ChunkKey(chunkX, chunkZ));
        return cached == null
                || cached.mesh().version() != snapshot.meshRevision(world, dimension, chunkX, chunkZ)
                || cached.mesh().lodLevel() != lodLevel
                || !cached.visibility().equals(visibility);
    }

    public TerrainMesh get(CartographicSnapshot snapshot, WorldKey world, DimensionKey dimension,
                           int chunkX, int chunkZ) {
        return get(snapshot, world, dimension, fullVisibility(dimension), chunkX, chunkZ);
    }

    public TerrainMesh get(CartographicSnapshot snapshot, WorldKey world, DimensionKey dimension,
                           LayerVisibility visibility, int chunkX, int chunkZ) {
        ChunkKey key = new ChunkKey(chunkX, chunkZ);
        Entry cached = meshes.get(key);
        if (cached != null && cached.mesh().version() == snapshot.version()
                && cached.mesh().lodLevel() == LodLevel.LOD_0
                && cached.visibility().equals(visibility)) {
            return cached.mesh();
        }
        TerrainMesh built = TerrainMeshBuilder.build(snapshot, world, dimension,
                chunkX, chunkZ, snapshot.version(), visibility, LodLevel.LOD_0);
        put(key, new Entry(built, visibility));
        return built;
    }

    public TerrainMesh get(CartographicSnapshot snapshot, WorldKey world, DimensionKey dimension,
                           LayerVisibility visibility, LodLevel lodLevel, int chunkX, int chunkZ) {
        ChunkKey key = new ChunkKey(chunkX, chunkZ);
        Entry cached = meshes.get(key);
        long revision = snapshot.meshRevision(world, dimension, chunkX, chunkZ);
        if (cached != null && cached.mesh().version() == revision
                && cached.mesh().lodLevel() == lodLevel
                && cached.visibility().equals(visibility)) {
            return cached.mesh();
        }
        TerrainMesh built = TerrainMeshBuilder.build(snapshot, world, dimension,
                chunkX, chunkZ, revision, visibility, lodLevel);
        put(key, new Entry(built, visibility));
        return built;
    }

    /** Queues immutable-snapshot work without blocking the game or render thread. */
    public boolean request(CartographicSnapshot snapshot, WorldKey world, DimensionKey dimension,
                           LayerVisibility visibility, LodLevel lodLevel, int chunkX, int chunkZ) {
        if (!needsRebuild(snapshot, world, dimension, visibility, lodLevel, chunkX, chunkZ)) {
            return false;
        }
        ChunkKey chunk = new ChunkKey(chunkX, chunkZ);
        long revision = snapshot.meshRevision(world, dimension, chunkX, chunkZ);
        BuildKey key = new BuildKey(generation.get(), chunk, revision, lodLevel);
        latestDesired.put(chunk, key);
        if (pending.size() >= limit || !pending.add(key)) {
            return false;
        }
        try {
            executor.execute(() -> {
                try {
                    TerrainMesh mesh = TerrainMeshBuilder.build(snapshot, world, dimension,
                            chunkX, chunkZ, revision, visibility, lodLevel);
                    completed.add(new Completed(key, mesh, visibility));
                } finally {
                    pending.remove(key);
                }
            });
            return true;
        } catch (RejectedExecutionException exception) {
            pending.remove(key);
            return false;
        }
    }

    /** Publishes a bounded number of completed CPU meshes on the game thread. */
    public int drainCompleted(int budget) {
        int applied = 0;
        int processed = 0;
        int limit = Math.max(0, budget);
        while (processed < limit) {
            Completed result = completed.poll();
            if (result == null) {
                break;
            }
            processed++;
            if (result.key().generation() != generation.get()
                    || !result.key().equals(latestDesired.get(result.key().chunk()))) {
                continue;
            }
            put(result.key().chunk(), new Entry(result.mesh(), result.visibility()));
            applied++;
        }
        return applied;
    }

    public TerrainMesh getIfReady(CartographicSnapshot snapshot, WorldKey world, DimensionKey dimension,
                                  LayerVisibility visibility, LodLevel lodLevel, int chunkX, int chunkZ) {
        ChunkKey key = new ChunkKey(chunkX, chunkZ);
        Entry cached = meshes.get(key);
        if (cached == null
                || cached.mesh().version() != snapshot.meshRevision(world, dimension, chunkX, chunkZ)
                || cached.mesh().lodLevel() != lodLevel
                || !cached.visibility().equals(visibility)) {
            return null;
        }
        return cached.mesh();
    }

    /** Keeps the previous valid level visible while a different LOD is built. */
    public TerrainMesh getFallback(CartographicSnapshot snapshot, WorldKey world, DimensionKey dimension,
                                   LayerVisibility visibility, int chunkX, int chunkZ) {
        Entry cached = meshes.get(new ChunkKey(chunkX, chunkZ));
        if (cached == null
                || cached.mesh().version() != snapshot.meshRevision(world, dimension, chunkX, chunkZ)
                || !cached.visibility().equals(visibility)) {
            return null;
        }
        return cached.mesh();
    }

    public Collection<TerrainMesh> meshes() {
        ArrayList<TerrainMesh> result = new ArrayList<>(meshes.size());
        for (Entry entry : meshes.values()) {
            result.add(entry.mesh());
        }
        return List.copyOf(result);
    }

    public Collection<TerrainMesh> meshes(long version, LayerVisibility visibility) {
        ArrayList<TerrainMesh> result = new ArrayList<>(meshes.size());
        for (Entry entry : meshes.values()) {
            if (entry.mesh().version() == version && entry.visibility().equals(visibility)) {
                result.add(entry.mesh());
            }
        }
        return List.copyOf(result);
    }

    public int size() {
        return meshes.size();
    }

    public int pendingCount() {
        return pending.size();
    }

    public long estimatedBytes() {
        return estimatedBytes;
    }

    public static int fitRadiusToLimit(int requestedRadius, int cacheLimit) {
        int radius = Math.max(0, requestedRadius);
        int capacity = Math.max(1, cacheLimit);
        while (radius > 0) {
            long diameter = radius * 2L + 1L;
            if (diameter * diameter <= capacity) {
                break;
            }
            radius--;
        }
        return radius;
    }

    public void retainRadius(int centerX, int centerZ, int radius) {
        Iterator<Map.Entry<ChunkKey, Entry>> iterator = meshes.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<ChunkKey, Entry> entry = iterator.next();
            ChunkKey key = entry.getKey();
            if (Math.abs(key.x() - centerX) > radius || Math.abs(key.z() - centerZ) > radius) {
                estimatedBytes -= bytes(entry.getValue().mesh());
                iterator.remove();
                latestDesired.remove(key);
            }
        }
    }

    public void clear() {
        generation.incrementAndGet();
        meshes.clear();
        estimatedBytes = 0L;
        latestDesired.clear();
        completed.clear();
        executor.getQueue().clear();
        pending.clear();
    }

    @Override
    public void close() {
        clear();
        executor.shutdownNow();
        pending.clear();
    }

    private void put(ChunkKey key, Entry entry) {
        Entry previous = meshes.put(key, entry);
        if (previous != null) {
            estimatedBytes -= bytes(previous.mesh());
        }
        estimatedBytes += bytes(entry.mesh());
        evict();
    }

    private void evict() {
        Iterator<Map.Entry<ChunkKey, Entry>> iterator = meshes.entrySet().iterator();
        while ((meshes.size() > limit || (estimatedBytes > byteLimit && meshes.size() > 1))
                && iterator.hasNext()) {
            Map.Entry<ChunkKey, Entry> entry = iterator.next();
            estimatedBytes -= bytes(entry.getValue().mesh());
            latestDesired.remove(entry.getKey());
            iterator.remove();
        }
    }

    private static long bytes(TerrainMesh mesh) {
        return (long) mesh.vertices().length * Float.BYTES;
    }

    private static LayerVisibility fullVisibility(DimensionKey dimension) {
        return LayerResolver.resolve(dimension, 16, LayerMode.FULL, 0,
                CumulativeDirection.DOWN, CutawaySettings.none());
    }
}
