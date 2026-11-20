package com.czminimap.client;

import com.czminimap.CzMinimap;
import com.czminimap.input.KeyBindings;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.event.GameShuttingDownEvent;

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
        ClientMapContext context = ClientMapContext.get();
        context.onClientTick(Minecraft.getInstance());
        context.minimapRenderer().tick(Minecraft.getInstance());
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        ClientMapContext.get().minimapRenderer().render(event.getGuiGraphics(), Minecraft.getInstance());
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientMapContext.get().onDisconnect();
    }

    @SubscribeEvent
    public static void onShuttingDown(GameShuttingDownEvent event) {
        ClientMapContext.get().onShutdown();
    }
}
