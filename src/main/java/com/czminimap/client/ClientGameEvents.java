package com.czminimap.client;

import com.czminimap.CzMinimap;
import com.czminimap.input.KeyBindings;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = CzMinimap.MOD_ID, value = Dist.CLIENT)
public final class ClientGameEvents {
    private static final ClientUiState UI_STATE = new ClientUiState();

    public static ClientUiState uiState() {
        return UI_STATE;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        while (KeyBindings.OPEN_MAP.consumeClick()) {
            UI_STATE.toggleMap();
        }
        while (KeyBindings.TOGGLE_MINIMAP.consumeClick()) {
            UI_STATE.toggleMinimap();
        }
        while (KeyBindings.MINIMAP_ZOOM_IN.consumeClick()) {
            UI_STATE.changeMinimapZoom(1);
        }
        while (KeyBindings.MINIMAP_ZOOM_OUT.consumeClick()) {
            UI_STATE.changeMinimapZoom(-1);
        }
        while (KeyBindings.CAMERA_RESET.consumeClick()) {
            UI_STATE.requestCameraReset();
        }
        while (KeyBindings.RETURN_TO_PLAYER.consumeClick()) {
            UI_STATE.requestReturnToPlayer();
        }
        while (KeyBindings.LAYER_NEXT.consumeClick()) {
            UI_STATE.changeLayer(1);
        }
        while (KeyBindings.LAYER_PREVIOUS.consumeClick()) {
            UI_STATE.changeLayer(-1);
        }
    }
}
