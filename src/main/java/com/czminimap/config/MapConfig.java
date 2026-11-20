package com.czminimap.config;

import com.czminimap.core.WorldIdentity;
import com.czminimap.layers.CumulativeDirection;
import com.czminimap.layers.LayerMode;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class MapConfig {
    public static final MapConfig CONFIG;
    public static final ModConfigSpec SPEC;

    public final ModConfigSpec.IntValue surfaceRadius;
    public final ModConfigSpec.IntValue undergroundRadius;
    public final ModConfigSpec.IntValue maxSamplesPerTick;
    public final ModConfigSpec.IntValue layerInterval;
    public final ModConfigSpec.EnumValue<LayerMode> defaultLayerMode;
    public final ModConfigSpec.EnumValue<CumulativeDirection> cumulativeDirection;
    public final ModConfigSpec.IntValue lodQuality;
    public final ModConfigSpec.IntValue renderDistance;
    public final ModConfigSpec.IntValue cameraSensitivity;
    public final ModConfigSpec.EnumValue<WorldIdentity.Mode> worldIdentityMode;
    public final ModConfigSpec.IntValue flushIntervalSeconds;

    private MapConfig(ModConfigSpec.Builder builder) {
        builder.push("discovery");
        surfaceRadius = builder.comment("Surface discovery radius in blocks.")
                .defineInRange("surface_radius", 96, 16, 256);
        undergroundRadius = builder.comment("Underground proximity discovery radius in blocks.")
                .defineInRange("underground_radius", 32, 8, 128);
        maxSamplesPerTick = builder.comment("Maximum discovery samples evaluated per game tick.")
                .defineInRange("max_samples_per_tick", 512, 32, 8192);
        builder.pop();

        builder.push("layers");
        layerInterval = builder.comment("Vertical interval size in blocks.")
                .defineInRange("interval_size", 16, 4, 64);
        defaultLayerMode = builder.comment("Visualization mode selected when the 3D map is first opened.")
                .defineEnum("default_mode", LayerMode.FULL, LayerMode.values());
        cumulativeDirection = builder.comment("Direction used by cumulative layer visualization.")
                .defineEnum("cumulative_direction", CumulativeDirection.DOWN, CumulativeDirection.values());
        builder.pop();

        builder.push("rendering");
        lodQuality = builder.comment("Highest LOD detail level allowed (0 = full detail, 3 = coarse).")
                .defineInRange("lod_quality", 0, 0, 3);
        renderDistance = builder.comment("Maximum number of map regions rendered from the focal point.")
                .defineInRange("max_render_distance", 8, 1, 32);
        cameraSensitivity = builder.comment("Orbital camera sensitivity percentage.")
                .defineInRange("camera_sensitivity", 100, 10, 500);
        builder.pop();

        builder.push("storage");
        worldIdentityMode = builder.comment(
                        "How map directories are named. Changing this creates a new directory; existing data is not moved.")
                .defineEnum("world_identity_mode", WorldIdentity.Mode.NAME_AND_HASH, WorldIdentity.Mode.values());
        flushIntervalSeconds = builder.comment("Interval between background map flushes, in seconds.")
                .defineInRange("flush_interval_seconds", 30, 5, 600);
        builder.pop();
    }

    static {
        var pair = new ModConfigSpec.Builder().configure(MapConfig::new);
        CONFIG = pair.getLeft();
        SPEC = pair.getRight();
    }
}
