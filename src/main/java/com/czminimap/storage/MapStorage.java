package com.czminimap.storage;

import com.czminimap.CzMinimap;
import com.czminimap.cartography.RegionData;
import com.czminimap.core.RegionKey;
import com.czminimap.core.WorldKey;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Client-side region storage. All disk access happens on a single bounded worker; callers only hand over
 * immutable {@link RegionData} and poll completed load batches.
 */
public final class MapStorage implements AutoCloseable {
    private static final int SAVE_QUEUE_CAPACITY = 256;
    private static final int STOP_TIMEOUT_SECONDS = 15;
    private static final String DIMENSIONS = "dimensions";
    private static final String REGIONS = "regions";
    private static final Pattern REGION_FILE = Pattern.compile("r\\.-?\\d+\\.-?\\d+\\.bin(\\.(tmp|bak))?");

    public record LoadedRegion(RegionKey key, RegionData data) {
    }

    public record LoadBatch(long token, List<LoadedRegion> regions) {
    }

    private record SaveJob(RegionKey key, RegionData data) {
    }

    private final Path root;
    private final ExecutorService worker;
    private final Semaphore permits = new Semaphore(SAVE_QUEUE_CAPACITY);
    private final Set<RegionKey> queued = ConcurrentHashMap.newKeySet();
    private final ConcurrentLinkedQueue<LoadBatch> loaded = new ConcurrentLinkedQueue<>();
    private final AtomicLong tokens = new AtomicLong();
    private volatile boolean closed;

    public MapStorage(Path root) {
        this.root = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
        this.worker = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "czminimap-storage");
            thread.setDaemon(true);
            return thread;
        });
    }

    /** Returns {@code false} when the bounded queue is full; the caller keeps the region dirty and retries. */
    public boolean enqueueSave(RegionKey key, RegionData data) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(data, "data");
        if (closed || data.isEmpty()) {
            return false;
        }
        if (!queued.add(key)) {
            return true;
        }
        if (!permits.tryAcquire()) {
            queued.remove(key);
            return false;
        }
        SaveJob job = new SaveJob(key, data);
        try {
            worker.execute(() -> {
                try {
                    writeRegion(job);
                } catch (Exception exception) {
                    CzMinimap.LOGGER.error("Failed to save map region {}", regionFileName(job.key()), exception);
                } finally {
                    queued.remove(job.key());
                    permits.release();
                }
            });
            return true;
        } catch (RuntimeException exception) {
            queued.remove(key);
            permits.release();
            throw exception;
        }
    }

    /**
     * Scans every saved dimension of one world. Results are published as one batch with the returned token,
     * so the caller can pause discovery until the batch has been applied on the game thread.
     */
    public long requestLoad(WorldKey world) {
        Objects.requireNonNull(world, "world");
        long token = tokens.incrementAndGet();
        worker.execute(() -> {
            List<LoadedRegion> regions = new ArrayList<>();
            Path worldRoot = worldRoot(world);
            if (Files.isDirectory(worldRoot)) {
                try (Stream<Path> paths = Files.walk(worldRoot)) {
                    List<Path> files = paths.filter(Files::isRegularFile)
                            .filter(path -> REGION_FILE.matcher(path.getFileName().toString()).matches())
                            .sorted(Comparator.comparing(Path::toString))
                            .toList();
                    for (Path file : files) {
                        RegionCodec.DecodedRegion decoded = loadFile(file, world);
                        if (decoded != null) {
                            regions.add(new LoadedRegion(decoded.key(), decoded.data()));
                        }
                    }
                } catch (IOException exception) {
                    CzMinimap.LOGGER.error("Failed to scan map data for {}", world.storageId(), exception);
                }
            }
            loaded.add(new LoadBatch(token, List.copyOf(regions)));
        });
        return token;
    }

    public LoadBatch pollLoaded() {
        return loaded.poll();
    }

    /** Waits for all already-enqueued writes. Called on disconnect, never from a render callback. */
    public void flush() {
        if (closed) {
            return;
        }
        try {
            worker.submit(() -> {
            }).get(STOP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (Exception exception) {
            CzMinimap.LOGGER.error("Timed out while flushing map storage", exception);
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        flush();
        closed = true;
        worker.shutdown();
        try {
            if (!worker.awaitTermination(STOP_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                worker.shutdownNow();
            }
        } catch (InterruptedException exception) {
            worker.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void writeRegion(SaveJob job) throws IOException {
        Path target = regionPath(job.key());
        Files.createDirectories(target.getParent());
        byte[] bytes = RegionCodec.encode(job.key(), job.data());

        String fileName = regionFileName(job.key());
        Path directory = target.getParent();
        Path temporary = directory.resolve(fileName + ".tmp");
        Path backup = directory.resolve(fileName + ".bak");

        Files.write(temporary, bytes);
        if (Files.exists(target)) {
            Files.copy(target, backup, StandardCopyOption.REPLACE_EXISTING);
        }
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private RegionCodec.DecodedRegion loadFile(Path file, WorldKey world) {
        String fileName = file.getFileName().toString();
        String mainName = mainFileName(fileName);
        Path main = file.resolveSibling(mainName);
        if (!fileName.equals(mainName) && Files.exists(main)) {
            return null;
        }

        byte[] bytes;
        try {
            bytes = Files.readAllBytes(file);
        } catch (IOException exception) {
            CzMinimap.LOGGER.warn("Cannot read map region {}", file, exception);
            return null;
        }

        try {
            RegionCodec.DecodedRegion decoded = RegionCodec.decode(bytes);
            if (!world.storageId().equals(decoded.key().world().storageId())) {
                throw new IOException("region belongs to a different world");
            }
            if (!fileName.equals(mainName)) {
                promote(file, main);
            }
            return decoded;
        } catch (Exception exception) {
            CzMinimap.LOGGER.warn("Discarding invalid map region {}", file, exception);
            quarantine(file);
            return fileName.equals(mainName) ? recover(main) : null;
        }
    }

    private RegionCodec.DecodedRegion recover(Path main) {
        String fileName = main.getFileName().toString();
        for (String suffix : List.of(".tmp", ".bak")) {
            Path candidate = main.resolveSibling(fileName + suffix);
            if (!Files.isRegularFile(candidate)) {
                continue;
            }
            try {
                RegionCodec.DecodedRegion decoded = RegionCodec.decode(Files.readAllBytes(candidate));
                promote(candidate, main);
                CzMinimap.LOGGER.warn("Recovered map region {} from {}", fileName, candidate.getFileName());
                return decoded;
            } catch (Exception exception) {
                CzMinimap.LOGGER.warn("Cannot recover map region from {}", candidate, exception);
                quarantine(candidate);
            }
        }
        return null;
    }

    private static void promote(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void quarantine(Path file) {
        Path target = file.resolveSibling(file.getFileName() + ".corrupt");
        try {
            Files.move(file, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            CzMinimap.LOGGER.warn("Cannot quarantine map region {}", file, exception);
        }
    }

    private static String mainFileName(String fileName) {
        return fileName.endsWith(".tmp") || fileName.endsWith(".bak")
                ? fileName.substring(0, fileName.length() - 4)
                : fileName;
    }

    private Path worldRoot(WorldKey world) {
        return root.resolve(safeSegment(world.storageId()));
    }

    private Path regionPath(RegionKey key) {
        return worldRoot(key.world())
                .resolve(DIMENSIONS)
                .resolve(safeSegment(key.dimension().id().getNamespace()))
                .resolve(safeSegment(key.dimension().id().getPath()))
                .resolve(REGIONS)
                .resolve(regionFileName(key));
    }

    private static String regionFileName(RegionKey key) {
        return "r." + key.x() + "." + key.z() + ".bin";
    }

    private static String safeSegment(String value) {
        String cleaned = value.replaceAll("[^a-z0-9_.-]", "_");
        if (cleaned.isEmpty()) {
            return "_";
        }
        return cleaned.length() > 64 ? cleaned.substring(0, 64) : cleaned;
    }
}
