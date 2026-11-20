package com.czminimap.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ClientConfig {
    public enum Position {
        TOP_LEFT, TOP_CENTER, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT, CUSTOM
    }

    public static final ClientConfig CONFIG;
    public static final ModConfigSpec SPEC;

    public final ModConfigSpec.BooleanValue enabled;
    public final ModConfigSpec.EnumValue<Position> position;
    public final ModConfigSpec.IntValue width;
    public final ModConfigSpec.IntValue height;
    public final ModConfigSpec.BooleanValue circular;
    public final ModConfigSpec.BooleanValue northUp;
    public final ModConfigSpec.IntValue zoom;
    public final ModConfigSpec.IntValue opacity;
    public final ModConfigSpec.IntValue backgroundOpacity;
    public final ModConfigSpec.IntValue customX;
    public final ModConfigSpec.IntValue customY;
    public final ModConfigSpec.BooleanValue showCompass;
    public final ModConfigSpec.BooleanValue showCoordinates;

    public final ModConfigSpec.DoubleValue worldMapFov;
    public final ModConfigSpec.IntValue worldMapRenderRadius;
    public final ModConfigSpec.DoubleValue worldMapSensitivity;
    public final ModConfigSpec.IntValue worldMapMaxCachedChunks;
    public final ModConfigSpec.IntValue worldMapMeshBudget;
    public final ModConfigSpec.IntValue worldMapMeshMemoryMb;
    public final ModConfigSpec.IntValue worldMapBackgroundOpacity;

    private ClientConfig(ModConfigSpec.Builder builder) {
        builder.push("hud");
        enabled = builder.comment("Show the HUD minimap.").define("enabled", true);
        position = builder.comment("Minimap anchor position.")
                .defineEnum("position", Position.TOP_RIGHT, Position.values());
        width = builder.comment("Minimap width in pixels.").defineInRange("width", 160, 64, 512);
        height = builder.comment("Minimap height in pixels.").defineInRange("height", 160, 64, 512);
        circular = builder.comment("Use a circular mask instead of a square.")
                .define("circular", true);
        northUp = builder.comment("Keep north at the top instead of rotating with the player.")
                .define("north_up", false);
        zoom = builder.comment("Base minimap zoom level; key bindings add or subtract steps.")
                .defineInRange("zoom", 1, 0, 5);
        opacity = builder.comment("Map image opacity percentage.")
                .defineInRange("opacity", 100, 10, 100);
        backgroundOpacity = builder.comment("HUD panel opacity percentage.")
                .defineInRange("background_opacity", 70, 0, 100);
        customX = builder.comment("Horizontal offset in GUI pixels when position is CUSTOM.")
                .defineInRange("custom_x", 8, 0, 4096);
        customY = builder.comment("Vertical offset in GUI pixels when position is CUSTOM.")
                .defineInRange("custom_y", 8, 0, 4096);
        showCompass = builder.comment("Draw cardinal letters around the minimap.")
                .define("show_compass", true);
        showCoordinates = builder.comment("Draw the player's block coordinates below the minimap.")
                .define("show_coordinates", true);
        builder.pop();

        builder.push("worldmap");
        worldMapFov = builder.comment("3D map camera field of view in degrees.")
                .defineInRange("fov", 70.0, 30.0, 120.0);
        worldMapRenderRadius = builder.comment("Number of chunks around the camera target included in the 3D map.")
                .defineInRange("render_radius", 3, 1, 8);
        worldMapSensitivity = builder.comment("Mouse sensitivity multiplier for camera rotation, pan and zoom.")
                .defineInRange("sensitivity", 1.0, 0.2, 3.0);
        worldMapMaxCachedChunks = builder.comment("Maximum number of cached chunk meshes.")
                .defineInRange("max_cached_chunks", 512, 32, 4096);
        worldMapMeshBudget = builder.comment("Maximum chunk meshes rebuilt per client tick.")
                .defineInRange("mesh_budget", 8, 1, 64);
        worldMapMeshMemoryMb = builder.comment("Maximum CPU memory used by cached 3D meshes, in MiB.")
                .defineInRange("mesh_memory_mb", 128, 16, 1024);
        worldMapBackgroundOpacity = builder.comment("3D map background opacity percentage.")
                .defineInRange("background_opacity", 92, 0, 100);
        builder.pop();
    }

    static {
        var pair = new ModConfigSpec.Builder().configure(ClientConfig::new);
        CONFIG = pair.getLeft();
        SPEC = pair.getRight();
    }
}
