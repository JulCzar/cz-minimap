package com.czminimap.gui;

import com.czminimap.client.ClientGameEvents;
import com.czminimap.config.ClientConfig;
import com.czminimap.rendering.worldmap.WorldMapRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Full-screen interactive 3D map. Camera interaction is kept separate from widgets. */
public final class WorldMapScreen extends Screen {
    private final WorldMapRenderer renderer;
    private int dragButton = -1;
    private double lastMouseX;
    private double lastMouseY;

    public WorldMapScreen(WorldMapRenderer renderer) {
        super(Component.translatable("gui.czminimap.world_map"));
        this.renderer = renderer;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderer.render(graphics, minecraft);
        drawPanel(graphics);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 || button == 1 || button == 2) {
            dragButton = button;
            lastMouseX = mouseX;
            lastMouseY = mouseY;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (dragButton != button) {
            return false;
        }
        double sensitivity = ClientConfig.CONFIG.worldMapSensitivity.get();
        if (button == 0) {
            renderer.camera().rotate(mouseX - lastMouseX, mouseY - lastMouseY, sensitivity);
        } else {
            renderer.camera().pan(mouseX - lastMouseX, mouseY - lastMouseY, sensitivity);
        }
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragButton == button) {
            dragButton = -1;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        renderer.camera().zoom(vertical, ClientConfig.CONFIG.worldMapSensitivity.get());
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        ClientGameEvents.uiState().setMapOpen(false);
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void drawPanel(GuiGraphics graphics) {
        int x = 8;
        int y = 8;
        graphics.fill(x - 4, y - 4, x + 224, y + 92, 0xC010141C);
        graphics.drawString(minecraft.font, Component.translatable("gui.czminimap.world_map"),
                x, y, 0xFFFFFFFF);
        y += 14;

        var player = minecraft.player;
        if (player != null) {
            var pos = player.blockPosition();
            graphics.drawString(minecraft.font,
                    "Player  X " + pos.getX() + "  Y " + pos.getY() + "  Z " + pos.getZ(),
                    x, y, 0xFFD0D0D0);
        }
        y += 12;

        var target = renderer.camera().target();
        graphics.drawString(minecraft.font,
                String.format("Target  %.0f  %.0f  %.0f", target.x, target.y, target.z),
                x, y, 0xFFD0D0D0);
        y += 12;
        graphics.drawString(minecraft.font,
                String.format("Distance  %.1f   Meshes  %d",
                        renderer.camera().distance(), renderer.cachedMeshCount()),
                x, y, 0xFFD0D0D0);
        y += 12;
        graphics.drawString(minecraft.font, "LMB rotate  MMB/RMB pan  wheel zoom  ESC close",
                x, y, 0xFF9AA0AA);
    }
}
