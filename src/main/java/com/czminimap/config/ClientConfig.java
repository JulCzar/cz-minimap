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
        zoom = builder.comment("Minimap zoom level.").defineInRange("zoom", 1, 0, 5);
        opacity = builder.comment("Minimap opacity percentage.")
                .defineInRange("opacity", 100, 10, 100);
        builder.pop();
    }

    static {
        var pair = new ModConfigSpec.Builder().configure(ClientConfig::new);
        CONFIG = pair.getLeft();
        SPEC = pair.getRight();
    }
}
