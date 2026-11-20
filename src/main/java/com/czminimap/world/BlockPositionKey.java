package com.czminimap.world;

/** Packs a block position into a long for bounded discovery queues. */
public final class BlockPositionKey {
    private static final long X_MASK = 0x3FFFFFFL;
    private static final long Y_MASK = 0xFFFL;
    private static final long Z_MASK = 0x3FFFFFFL;

    private BlockPositionKey() {
    }

    public static long encode(int x, int y, int z) {
        return ((x & X_MASK) << 38) | ((y & Y_MASK) << 26) | (z & Z_MASK);
    }

    public static int x(long key) {
        return signExtend((key >> 38) & X_MASK, 26);
    }

    public static int y(long key) {
        return signExtend((key >> 26) & Y_MASK, 12);
    }

    public static int z(long key) {
        return signExtend(key & Z_MASK, 26);
    }

    private static int signExtend(long value, int bits) {
        long signBit = 1L << (bits - 1);
        return (int) ((value ^ signBit) - signBit);
    }
}
