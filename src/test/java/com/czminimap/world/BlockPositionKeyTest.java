package com.czminimap.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BlockPositionKeyTest {
    @Test
    void roundTripsNegativeAndPositiveCoordinates() {
        int[][] positions = {{0, 0, 0}, {-5, -70, 300}, {1_000_000, 2000, -1_000_000}};

        for (int[] position : positions) {
            long key = BlockPositionKey.encode(position[0], position[1], position[2]);
            assertEquals(position[0], BlockPositionKey.x(key));
            assertEquals(position[1], BlockPositionKey.y(key));
            assertEquals(position[2], BlockPositionKey.z(key));
        }
    }
}
