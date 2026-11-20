package com.czminimap.storage;

import com.czminimap.cartography.DiscoveryState;
import com.czminimap.cartography.RegionData;
import com.czminimap.cartography.VoxelAppearance;
import com.czminimap.core.DimensionKey;
import com.czminimap.core.RegionKey;
import com.czminimap.core.WorldKey;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RegionCodecTest {
    private static final WorldKey WORLD = new WorldKey("test_world_123456789abc", "Test World");
    private static final DimensionKey OVERWORLD = new DimensionKey(
            ResourceLocation.parse("minecraft:overworld"), -64, 320);
    private static final RegionKey REGION = new RegionKey(WORLD, OVERWORLD, -1, 3);
    private static final VoxelAppearance STONE = new VoxelAppearance(
            ResourceLocation.parse("minecraft:stone"), 2, ResourceLocation.parse("minecraft:plains"), 12345);

    private static RegionData sampleData() {
        CartographicMapSource source = new CartographicMapSource();
        return source.data();
    }

    @Test
    void encodeDecodeRoundTrip() throws IOException {
        RegionData data = sampleData();
        byte[] encoded = RegionCodec.encode(REGION, data);
        RegionCodec.DecodedRegion decoded = RegionCodec.decode(encoded);

        assertEquals(REGION.world().storageId(), decoded.key().world().storageId());
        assertEquals(REGION.dimension(), decoded.key().dimension());
        assertEquals(REGION.x(), decoded.key().x());
        assertEquals(REGION.z(), decoded.key().z());
        assertEquals(data.palette(), decoded.data().palette());

        RegionData.SectionData original = data.chunks().values().iterator().next()
                .sections().values().iterator().next();
        RegionData.SectionData copy = decoded.data().chunks().values().iterator().next()
                .sections().values().iterator().next();
        assertArrayEquals(original.states(), copy.states());
        assertArrayEquals(original.paletteIndices(), copy.paletteIndices());
        assertEquals(original.lastObservedTick(), copy.lastObservedTick());
    }

    @Test
    void rejectsBadMagicAndFutureVersion() throws IOException {
        byte[] encoded = RegionCodec.encode(REGION, sampleData());
        byte[] badMagic = encoded.clone();
        badMagic[0] = 'X';
        assertThrows(IOException.class, () -> RegionCodec.decode(badMagic));

        byte[] futureVersion = encoded.clone();
        futureVersion[7] = 2;
        assertThrows(IOException.class, () -> RegionCodec.decode(futureVersion));
    }

    @Test
    void detectsPayloadCorruptionAndTruncation() throws IOException {
        byte[] encoded = RegionCodec.encode(REGION, sampleData());
        byte[] corrupted = encoded.clone();
        corrupted[corrupted.length - 1] ^= 0x40;
        assertThrows(IOException.class, () -> RegionCodec.decode(corrupted));

        assertThrows(IOException.class, () -> RegionCodec.decode(Arrays.copyOf(encoded, encoded.length - 1)));
    }

    /** Builds a region through the public model so the payload matches real production data. */
    private static final class CartographicMapSource {
        private final com.czminimap.cartography.CartographicMap map = new com.czminimap.cartography.CartographicMap();

        private CartographicMapSource() {
            map.setVoxel(WORLD, OVERWORLD, -1, 5, 1537, DiscoveryState.EXPOSED_SURFACE, STONE, 77L);
            map.setVoxel(WORLD, OVERWORLD, 0, 5, 1537, DiscoveryState.KNOWN_AIR, null, 78L);
        }

        private RegionData data() {
            return map.regionData(REGION);
        }
    }
}
