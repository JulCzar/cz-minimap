package com.czminimap.rendering.minimap;

import com.czminimap.cartography.VoxelAppearance;
import net.minecraft.resources.ResourceLocation;

/** Pure ARGB colour mapping used by the HUD. Never reads the live world or the texture atlas. */
public final class MinimapColorResolver {
    private static final int UNKNOWN = 0xFF181818;

    private MinimapColorResolver() {
    }

    public static int unknown() {
        return UNKNOWN;
    }

    public static int color(VoxelAppearance appearance) {
        if (appearance == null) {
            return UNKNOWN;
        }
        int color = baseColor(appearance.block().getPath());
        return tint(color, appearance.biome().getPath());
    }

    private static int baseColor(String block) {
        if (block.contains("grass") || block.contains("moss") || block.contains("vine")) {
            return 0xFF4B8B3B;
        }
        if (block.contains("leaves") || block.contains("sapling")) {
            return 0xFF2F6B2A;
        }
        if (block.contains("dirt") || block.contains("mud") || block.contains("podzol")) {
            return 0xFF7A5636;
        }
        if (block.contains("sand") || block.contains("gravel") || block.contains("clay")) {
            return 0xFFC8B378;
        }
        if (block.contains("snow") || block.contains("ice")) {
            return 0xFFDDEBF2;
        }
        if (block.contains("water") || block.contains("kelp") || block.contains("coral")) {
            return 0xFF3A6FB0;
        }
        if (block.contains("stone") || block.contains("cobble") || block.contains("andesite")
                || block.contains("diorite") || block.contains("granite") || block.contains("basalt")) {
            return 0xFF7E7E7E;
        }
        if (block.contains("log") || block.contains("wood") || block.contains("planks")
                || block.contains("bark") || block.contains("door") || block.contains("fence")) {
            return 0xFF8A633B;
        }
        if (block.contains("netherrack") || block.contains("nether_brick") || block.contains("magma")) {
            return 0xFF8E3B34;
        }
        if (block.contains("end_stone") || block.contains("purpur")) {
            return 0xFFC7BE94;
        }
        if (block.contains("bedrock") || block.contains("obsidian")) {
            return 0xFF35353F;
        }
        return hashColor(appearanceHash(block));
    }

    private static int tint(int color, String biome) {
        int red = (color >>> 16) & 0xFF;
        int green = (color >>> 8) & 0xFF;
        int blue = color & 0xFF;
        if (biome.contains("desert") || biome.contains("badlands") || biome.contains("savanna")) {
            red = clamp(red + 18);
            green = clamp(green + 8);
            blue = clamp(blue - 12);
        } else if (biome.contains("snow") || biome.contains("ice") || biome.contains("frozen")) {
            red = clamp(red + 14);
            green = clamp(green + 18);
            blue = clamp(blue + 22);
        } else if (biome.contains("nether")) {
            red = clamp(red + 22);
            green = clamp(green - 12);
            blue = clamp(blue - 12);
        } else if (biome.contains("end")) {
            red = clamp(red + 10);
            green = clamp(green + 10);
        }
        return 0xFF000000 | (red << 16) | (green << 8) | blue;
    }

    private static int hashColor(int hash) {
        int hue = Math.floorMod(hash, 360);
        float saturation = 0.25f + (Math.floorMod(hash >>> 8, 30) / 100f);
        float brightness = 0.45f + (Math.floorMod(hash >>> 16, 25) / 100f);
        float sector = hue / 60f;
        int index = (int) sector;
        float fraction = sector - index;
        float lightness = brightness * (1f - saturation);
        float increasing = brightness * (1f - saturation * fraction);
        float decreasing = brightness * (1f - saturation * (1f - fraction));
        float[] channels = switch (index) {
            case 0 -> new float[]{brightness, increasing, lightness};
            case 1 -> new float[]{decreasing, brightness, lightness};
            case 2 -> new float[]{lightness, brightness, increasing};
            case 3 -> new float[]{lightness, decreasing, brightness};
            case 4 -> new float[]{increasing, lightness, brightness};
            default -> new float[]{brightness, lightness, decreasing};
        };
        int red = clamp(Math.round(channels[0] * 255f));
        int green = clamp(Math.round(channels[1] * 255f));
        int blue = clamp(Math.round(channels[2] * 255f));
        return 0xFF000000 | (red << 16) | (green << 8) | blue;
    }

    private static int appearanceHash(String value) {
        int hash = 17;
        for (int i = 0; i < value.length(); i++) {
            hash = 31 * hash + value.charAt(i);
        }
        return hash;
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }
}
