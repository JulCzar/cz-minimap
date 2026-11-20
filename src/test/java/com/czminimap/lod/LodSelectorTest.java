package com.czminimap.lod;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LodSelectorTest {
    @Test
    void exposesPowerOfTwoAggregationLevels() {
        assertEquals(1, LodLevel.LOD_0.blockSize());
        assertEquals(2, LodLevel.LOD_1.blockSize());
        assertEquals(4, LodLevel.LOD_2.blockSize());
        assertEquals(8, LodLevel.LOD_3.blockSize());
    }

    @Test
    void selectsAllLevelsFromProjectedBlockSize() {
        assertEquals(LodLevel.LOD_0, LodSelector.select(40.0, 1080, 70.0, 3));
        assertEquals(LodLevel.LOD_1, LodSelector.select(250.0, 1080, 70.0, 3));
        assertEquals(LodLevel.LOD_2, LodSelector.select(500.0, 1080, 70.0, 3));
        assertEquals(LodLevel.LOD_3, LodSelector.select(1000.0, 1080, 70.0, 3));
    }

    @Test
    void qualitySettingCapsCoarseness() {
        assertEquals(LodLevel.LOD_1, LodSelector.select(2000.0, 1080, 70.0, 1));
        assertEquals(LodLevel.LOD_0, LodSelector.select(2000.0, 1080, 70.0, 0));
    }
}
