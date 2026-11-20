package com.czminimap.rendering.minimap;

import com.czminimap.config.ClientConfig;

/** Pure screen-space placement of the HUD panel in GUI-scaled pixels. */
public record MinimapLayout(int x, int y, int width, int height) {
    public record ScreenArea(int width, int height) {
    }

    public static MinimapLayout resolve(ScreenArea screen, ClientConfig.Position position,
                                        int width, int height, int customX, int customY) {
        int margin = 4;
        int x = switch (position) {
            case TOP_LEFT, BOTTOM_LEFT -> margin;
            case TOP_CENTER, BOTTOM_CENTER -> (screen.width() - width) / 2;
            case TOP_RIGHT, BOTTOM_RIGHT -> screen.width() - width - margin;
            case CUSTOM -> customX;
        };
        int y = switch (position) {
            case TOP_LEFT, TOP_CENTER, TOP_RIGHT -> margin;
            case BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT -> screen.height() - height - margin;
            case CUSTOM -> customY;
        };
        return new MinimapLayout(Math.max(0, x), Math.max(0, y), width, height);
    }
}
