package com.czminimap.storage;

import com.czminimap.cartography.DiscoveryState;
import com.czminimap.cartography.RegionData;
import com.czminimap.cartography.VoxelAppearance;
import com.czminimap.core.DimensionKey;
import com.czminimap.core.RegionKey;
import com.czminimap.core.WorldKey;
import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4Factory;
import net.jpountz.lz4.LZ4FastDecompressor;
import net.minecraft.resources.ResourceLocation;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;

/**
 * Versioned binary format for one region. The header is plain data; the payload is LZ4-compressed and
 * protected by CRC32. Every field is validated on decode so a damaged file becomes a controlled failure.
 */
public final class RegionCodec {
    public static final int SCHEMA_VERSION = 1;

    private static final byte[] MAGIC = {'C', 'Z', 'M', '1'};
    private static final int MAX_PAYLOAD_BYTES = 64 * 1024 * 1024;
    private static final int MAX_PALETTE_ENTRIES = 1 << 16;
    private static final int MAX_CHUNKS = RegionKey.CHUNKS_PER_REGION * RegionKey.CHUNKS_PER_REGION;
    private static final int MAX_SECTIONS = 4096;
    private static final int SECTION_SHIFT = 4;
    private static final int SECTION_SIZE = 1 << SECTION_SHIFT;

    private static final LZ4Factory FACTORY = LZ4Factory.fastestInstance();
    private static final LZ4Compressor COMPRESSOR = FACTORY.fastCompressor();
    private static final LZ4FastDecompressor DECOMPRESSOR = FACTORY.fastDecompressor();

    private RegionCodec() {
    }

    /**
     * The display name is intentionally not persisted: world identity is the storage id only. Consumers
     * remap this key to the active {@link WorldKey} before inserting loaded voxels into the map.
     */
    public record DecodedRegion(RegionKey key, RegionData data) {
    }

    public static byte[] encode(RegionKey key, RegionData data) throws IOException {
        byte[] payload = writePayload(data);
        byte[] compressed = COMPRESSOR.compress(payload);
        CRC32 crc = new CRC32();
        crc.update(payload);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream(MAGIC.length + 64 + compressed.length);
        DataOutputStream out = new DataOutputStream(bytes);
        out.write(MAGIC);
        out.writeInt(SCHEMA_VERSION);
        out.writeUTF(key.world().storageId());
        out.writeUTF(key.dimension().id().toString());
        out.writeInt(key.dimension().minY());
        out.writeInt(key.dimension().maxYExclusive());
        out.writeInt(key.x());
        out.writeInt(key.z());
        out.writeInt(payload.length);
        out.writeInt(compressed.length);
        out.writeLong(crc.getValue());
        out.write(compressed);
        out.flush();
        return bytes.toByteArray();
    }

    public static DecodedRegion decode(byte[] file) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(file));

        byte[] magic = new byte[MAGIC.length];
        in.readFully(magic);
        if (!Arrays.equals(magic, MAGIC)) {
            throw new IOException("not a CZM region file");
        }

        int version = in.readInt();
        if (version != SCHEMA_VERSION) {
            throw new IOException("unsupported schema version " + version);
        }

        String worldId = in.readUTF();
        String dimensionId = in.readUTF();
        int minY = in.readInt();
        int maxYExclusive = in.readInt();
        int regionX = in.readInt();
        int regionZ = in.readInt();
        int payloadSize = in.readInt();
        int compressedSize = in.readInt();
        long checksum = in.readLong();

        if (payloadSize <= 0 || payloadSize > MAX_PAYLOAD_BYTES
                || compressedSize <= 0 || compressedSize > MAX_PAYLOAD_BYTES) {
            throw new IOException("invalid payload sizes");
        }
        if (Math.abs(regionX) > 1_000_000 || Math.abs(regionZ) > 1_000_000) {
            throw new IOException("region coordinates out of range");
        }
        if (in.available() != compressedSize) {
            throw new IOException("file length does not match header");
        }

        byte[] compressed = new byte[compressedSize];
        in.readFully(compressed);

        byte[] payload;
        try {
            payload = DECOMPRESSOR.decompress(compressed, payloadSize);
        } catch (RuntimeException exception) {
            throw new IOException("LZ4 decompression failed", exception);
        }

        CRC32 crc = new CRC32();
        crc.update(payload);
        if (crc.getValue() != checksum) {
            throw new IOException("region checksum mismatch");
        }

        DimensionKey dimension = new DimensionKey(ResourceLocation.parse(dimensionId), minY, maxYExclusive);
        RegionData data = readPayload(payload, dimension, regionX, regionZ);
        // The display name is not part of persisted identity; the active WorldKey is supplied by the caller.
        RegionKey key = new RegionKey(new WorldKey(worldId, worldId), dimension, regionX, regionZ);
        return new DecodedRegion(key, data);
    }

    private static byte[] writePayload(RegionData data) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);

        if (data.palette().size() > MAX_PALETTE_ENTRIES || data.chunks().size() > MAX_CHUNKS) {
            throw new IOException("region exceeds format limits");
        }

        out.writeInt(data.palette().size());
        for (VoxelAppearance appearance : data.palette()) {
            out.writeUTF(appearance.block().toString());
            out.writeInt(appearance.propertiesMask());
            out.writeUTF(appearance.biome().toString());
            out.writeInt(appearance.tint());
        }

        List<Map.Entry<Long, RegionData.ChunkData>> chunks = new ArrayList<>(data.chunks().entrySet());
        chunks.sort(Map.Entry.comparingByKey());
        out.writeInt(chunks.size());
        for (Map.Entry<Long, RegionData.ChunkData> chunkEntry : chunks) {
            out.writeLong(chunkEntry.getKey());
            List<Map.Entry<Integer, RegionData.SectionData>> sections =
                    new ArrayList<>(chunkEntry.getValue().sections().entrySet());
            sections.sort(Map.Entry.comparingByKey());
            out.writeInt(sections.size());
            for (Map.Entry<Integer, RegionData.SectionData> sectionEntry : sections) {
                RegionData.SectionData section = sectionEntry.getValue();
                out.writeInt(sectionEntry.getKey());
                out.writeLong(section.lastObservedTick());
                out.write(section.states());
                for (int index : section.paletteIndices()) {
                    out.writeInt(index);
                }
            }
        }

        out.flush();
        return bytes.toByteArray();
    }

    private static RegionData readPayload(byte[] payload, DimensionKey dimension, int regionX, int regionZ)
            throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload));

        int paletteSize = in.readInt();
        if (paletteSize < 0 || paletteSize > MAX_PALETTE_ENTRIES) {
            throw new IOException("invalid palette size");
        }
        List<VoxelAppearance> palette = new ArrayList<>(paletteSize);
        for (int i = 0; i < paletteSize; i++) {
            ResourceLocation block = ResourceLocation.parse(in.readUTF());
            int propertiesMask = in.readInt();
            ResourceLocation biome = ResourceLocation.parse(in.readUTF());
            int tint = in.readInt();
            palette.add(new VoxelAppearance(block, propertiesMask, biome, tint));
        }

        int chunkCount = in.readInt();
        if (chunkCount < 0 || chunkCount > MAX_CHUNKS) {
            throw new IOException("invalid chunk count");
        }
        Map<Long, RegionData.ChunkData> chunks = new LinkedHashMap<>();
        int minSection = Math.floorDiv(dimension.minY(), SECTION_SIZE);
        int maxSection = Math.floorDiv(dimension.maxYExclusive() - 1, SECTION_SIZE);

        for (int i = 0; i < chunkCount; i++) {
            long chunkKey = in.readLong();
            validateChunkKey(chunkKey, regionX, regionZ);
            int sectionCount = in.readInt();
            if (sectionCount < 0 || sectionCount > MAX_SECTIONS) {
                throw new IOException("invalid section count");
            }
            Map<Integer, RegionData.SectionData> sections = new LinkedHashMap<>();
            for (int j = 0; j < sectionCount; j++) {
                int sectionY = in.readInt();
                if (sectionY < minSection || sectionY > maxSection) {
                    throw new IOException("section outside dimension bounds");
                }
                long tick = in.readLong();
                byte[] states = new byte[RegionData.SectionData.VOXELS];
                in.readFully(states);
                int[] indices = new int[RegionData.SectionData.VOXELS];
                for (int k = 0; k < indices.length; k++) {
                    indices[k] = in.readInt();
                }
                validateSection(states, indices, paletteSize);
                sections.put(sectionY, new RegionData.SectionData(states, indices, tick));
            }
            chunks.put(chunkKey, new RegionData.ChunkData(sections));
        }

        if (in.available() != 0) {
            throw new IOException("trailing bytes in region payload");
        }
        return new RegionData(palette, chunks);
    }

    private static void validateChunkKey(long key, int regionX, int regionZ) throws IOException {
        int chunkX = (int) (key & 0xFFFFFFFFL) - regionX * RegionKey.CHUNKS_PER_REGION;
        int chunkZ = (int) (key >>> 32) - regionZ * RegionKey.CHUNKS_PER_REGION;
        if (chunkX < 0 || chunkX >= RegionKey.CHUNKS_PER_REGION
                || chunkZ < 0 || chunkZ >= RegionKey.CHUNKS_PER_REGION) {
            throw new IOException("chunk key outside region");
        }
    }

    private static void validateSection(byte[] states, int[] indices, int paletteSize) throws IOException {
        int stateCount = DiscoveryState.values().length;
        for (int i = 0; i < states.length; i++) {
            byte state = states[i];
            if (state < 0 || state >= stateCount) {
                throw new IOException("invalid discovery state in section");
            }
            int paletteIndex = indices[i];
            if (paletteIndex < -1 || paletteIndex >= paletteSize) {
                throw new IOException("invalid palette index in section");
            }
        }
    }
}
