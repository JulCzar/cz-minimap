package com.czminimap.rendering.minimap;

import com.czminimap.config.ClientConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MinimapLayoutTest {
    private static final MinimapLayout.ScreenArea SCREEN = new MinimapLayout.ScreenArea(800, 600);

    @Test
    void anchorsToAllScreenEdges() {
        assertEquals(new MinimapLayout(4, 4, 160, 90),
                MinimapLayout.resolve(SCREEN, ClientConfig.Position.TOP_LEFT, 160, 90, 0, 0));
        assertEquals(new MinimapLayout(320, 4, 160, 90),
                MinimapLayout.resolve(SCREEN, ClientConfig.Position.TOP_CENTER, 160, 90, 0, 0));
        assertEquals(new MinimapLayout(636, 4, 160, 90),
                MinimapLayout.resolve(SCREEN, ClientConfig.Position.TOP_RIGHT, 160, 90, 0, 0));
        assertEquals(new MinimapLayout(4, 506, 160, 90),
                MinimapLayout.resolve(SCREEN, ClientConfig.Position.BOTTOM_LEFT, 160, 90, 0, 0));
        assertEquals(new MinimapLayout(320, 506, 160, 90),
                MinimapLayout.resolve(SCREEN, ClientConfig.Position.BOTTOM_CENTER, 160, 90, 0, 0));
        assertEquals(new MinimapLayout(636, 506, 160, 90),
                MinimapLayout.resolve(SCREEN, ClientConfig.Position.BOTTOM_RIGHT, 160, 90, 0, 0));
    }

    @Test
    void customPositionIsUsedAndNegativeOriginsAreClamped() {
        assertEquals(new MinimapLayout(120, 240, 80, 80),
                MinimapLayout.resolve(SCREEN, ClientConfig.Position.CUSTOM, 80, 80, 120, 240));
        assertEquals(new MinimapLayout(0, 0, 80, 80),
                MinimapLayout.resolve(SCREEN, ClientConfig.Position.CUSTOM, 80, 80, -5, -9));
    }
}
