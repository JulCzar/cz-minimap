package com.czminimap.input;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

public final class KeyBindings {
    public static final String CATEGORY = "key.categories.czminimap";

    public static final KeyMapping OPEN_MAP = create("open_map", GLFW.GLFW_KEY_M);
    public static final KeyMapping TOGGLE_MINIMAP = create("toggle_minimap", GLFW.GLFW_KEY_B);
    public static final KeyMapping MINIMAP_ZOOM_IN = create("minimap_zoom_in", GLFW.GLFW_KEY_EQUAL);
    public static final KeyMapping MINIMAP_ZOOM_OUT = create("minimap_zoom_out", GLFW.GLFW_KEY_MINUS);
    public static final KeyMapping CAMERA_RESET = create("camera_reset", GLFW.GLFW_KEY_R);
    public static final KeyMapping RETURN_TO_PLAYER = create("return_to_player", GLFW.GLFW_KEY_P);
    public static final KeyMapping LAYER_NEXT = create("layer_next", GLFW.GLFW_KEY_RIGHT_BRACKET);
    public static final KeyMapping LAYER_PREVIOUS = create("layer_previous", GLFW.GLFW_KEY_LEFT_BRACKET);

    private static KeyMapping create(String name, int keyCode) {
        return new KeyMapping("key.czminimap." + name, KeyConflictContext.IN_GAME,
                InputConstants.Type.KEYSYM, keyCode, CATEGORY);
    }

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(OPEN_MAP);
        event.register(TOGGLE_MINIMAP);
        event.register(MINIMAP_ZOOM_IN);
        event.register(MINIMAP_ZOOM_OUT);
        event.register(CAMERA_RESET);
        event.register(RETURN_TO_PLAYER);
        event.register(LAYER_NEXT);
        event.register(LAYER_PREVIOUS);
    }
}
