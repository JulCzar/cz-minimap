package com.czminimap.gui;

import com.czminimap.client.ClientGameEvents;
import com.czminimap.config.ClientConfig;
import com.czminimap.cutaway.ClipSide;
import com.czminimap.layers.LayerMode;
import com.czminimap.layers.LayerRange;
import com.czminimap.rendering.worldmap.WorldMapRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Full-screen interactive 3D map. Camera interaction is kept separate from widgets. */
public final class WorldMapScreen extends Screen {
    private static final int PANEL_X = 8;
    private static final int PANEL_Y = 8;
    private static final int PANEL_WIDTH = 272;
    private static final int PANEL_HEIGHT = 194;

    private final WorldMapRenderer renderer;
    private int dragButton = -1;
    private double lastMouseX;
    private double lastMouseY;
    private Button modeButton;
    private Button directionButton;
    private Button previousLayerButton;
    private Button nextLayerButton;
    private Button horizontalCutButton;
    private Button verticalCutButton;
    private Button axisButton;
    private Button sideButton;
    private Button cutBackButton;
    private Button cutForwardButton;

    public WorldMapScreen(WorldMapRenderer renderer) {
        super(Component.translatable("gui.czminimap.world_map"));
        this.renderer = renderer;
    }

    @Override
    protected void init() {
        int left = PANEL_X + 4;
        int firstRow = PANEL_Y + 96;
        modeButton = addRenderableWidget(button(left, firstRow, 124,
                ignored -> renderer.cycleLayerMode()));
        directionButton = addRenderableWidget(button(left + 128, firstRow, 124,
                ignored -> renderer.toggleCumulativeDirection()));

        int secondRow = firstRow + 24;
        previousLayerButton = addRenderableWidget(button(left, secondRow, 40,
                ignored -> renderer.changeLayer(-1)));
        nextLayerButton = addRenderableWidget(button(left + 44, secondRow, 40,
                ignored -> renderer.changeLayer(1)));
        horizontalCutButton = addRenderableWidget(button(left + 88, secondRow, 80,
                ignored -> renderer.toggleHorizontalCutaway()));
        verticalCutButton = addRenderableWidget(button(left + 172, secondRow, 80,
                ignored -> renderer.toggleVerticalCutaway()));

        int thirdRow = secondRow + 24;
        axisButton = addRenderableWidget(button(left, thirdRow, 68,
                ignored -> renderer.toggleCutawayAxis()));
        sideButton = addRenderableWidget(button(left + 72, thirdRow, 76,
                ignored -> renderer.toggleCutawaySide()));
        cutBackButton = addRenderableWidget(button(left + 152, thirdRow, 48,
                ignored -> renderer.moveVerticalCut(-16)));
        cutForwardButton = addRenderableWidget(button(left + 204, thirdRow, 48,
                ignored -> renderer.moveVerticalCut(16)));
        refreshButtons();
    }

    private static Button button(int x, int y, int width, Button.OnPress action) {
        return Button.builder(Component.empty(), action).bounds(x, y, width, 20).build();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderer.render(graphics, minecraft);
        drawPanel(graphics);
        refreshButtons();
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (isOverControls(mouseX, mouseY)) {
            return true;
        }
        if (button == 0 || button == 1 || button == 2) {
            dragButton = button;
            lastMouseX = mouseX;
            lastMouseY = mouseY;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (dragButton != button) {
            return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
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
        if (isOverControls(mouseX, mouseY)) {
            return true;
        }
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
        int x = PANEL_X + 4;
        int y = PANEL_Y + 4;
        graphics.fill(PANEL_X, PANEL_Y, PANEL_X + PANEL_WIDTH, PANEL_Y + PANEL_HEIGHT, 0xC010141C);
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
        LayerRange selected = renderer.selectedLayer();
        graphics.drawString(minecraft.font,
                selected == null ? "Y  --" : "Selected  " + formatRange(selected),
                x, y, 0xFFD0D0D0);
        y += 12;
        List<LayerRange> visible = renderer.visibleLayers();
        graphics.drawString(minecraft.font,
                visible.isEmpty() ? "Visible  --" : "Visible  " + formatSpan(visible),
                x, y, 0xFFD0D0D0);
        y += 12;
        var cut = renderer.cutawaySettings();
        String vertical = cut.axis() + (cut.visibleSide() == ClipSide.NEGATIVE ? " < " : " >= ")
                + cut.coordinate();
        graphics.drawString(minecraft.font,
                "Cut  H " + (cut.horizontalEnabled() ? "Y < " + cut.maxYExclusive() : "off")
                        + "   V " + (cut.verticalEnabled() ? vertical : "off"),
                x, y, 0xFF9AA0AA);
        y = PANEL_Y + PANEL_HEIGHT - 14;
        graphics.drawString(minecraft.font, "LMB rotate  MMB/RMB pan  wheel zoom  ESC close",
                x, y, 0xFF9AA0AA);
    }

    private void refreshButtons() {
        if (modeButton == null) {
            return;
        }
        LayerMode mode = renderer.layerMode();
        var cut = renderer.cutawaySettings();
        modeButton.setMessage(Component.translatable("gui.czminimap.mode",
                Component.translatable("gui.czminimap.mode." + mode.name().toLowerCase())));
        directionButton.setMessage(Component.translatable("gui.czminimap.cumulative",
                Component.translatable("gui.czminimap.direction."
                        + renderer.cumulativeDirection().name().toLowerCase())));
        previousLayerButton.setMessage(Component.translatable("gui.czminimap.layer.previous"));
        nextLayerButton.setMessage(Component.translatable("gui.czminimap.layer.next"));
        horizontalCutButton.setMessage(Component.translatable("gui.czminimap.cut.horizontal",
                toggleText(cut.horizontalEnabled())));
        verticalCutButton.setMessage(Component.translatable("gui.czminimap.cut.vertical",
                toggleText(cut.verticalEnabled())));
        axisButton.setMessage(Component.translatable("gui.czminimap.cut.axis", cut.axis().name()));
        sideButton.setMessage(Component.translatable("gui.czminimap.cut.side",
                Component.translatable(cut.visibleSide() == ClipSide.NEGATIVE
                        ? "gui.czminimap.cut.side.negative"
                        : "gui.czminimap.cut.side.positive")));
        cutBackButton.setMessage(Component.literal("-16"));
        cutForwardButton.setMessage(Component.literal("+16"));

        boolean layerSelection = mode != LayerMode.FULL;
        previousLayerButton.active = layerSelection;
        nextLayerButton.active = layerSelection;
        directionButton.active = mode == LayerMode.CUMULATIVE;
        boolean cutawayMode = mode == LayerMode.CUTAWAY;
        horizontalCutButton.active = cutawayMode;
        verticalCutButton.active = cutawayMode;
        axisButton.active = cutawayMode && cut.verticalEnabled();
        sideButton.active = cutawayMode && cut.verticalEnabled();
        cutBackButton.active = cutawayMode && cut.verticalEnabled();
        cutForwardButton.active = cutawayMode && cut.verticalEnabled();
    }

    private static Component toggleText(boolean enabled) {
        return Component.translatable(enabled
                ? "gui.czminimap.enabled"
                : "gui.czminimap.disabled");
    }

    private static String formatRange(LayerRange range) {
        return "[" + range.minYInclusive() + ", " + range.maxYExclusive() + ")";
    }

    private static String formatSpan(List<LayerRange> ranges) {
        LayerRange first = ranges.getFirst();
        LayerRange last = ranges.getLast();
        return "[" + first.minYInclusive() + ", " + last.maxYExclusive() + ")"
                + (ranges.size() > 1 ? "  (" + ranges.size() + ")" : "");
    }

    private static boolean isOverControls(double mouseX, double mouseY) {
        return mouseX >= PANEL_X && mouseX < PANEL_X + PANEL_WIDTH
                && mouseY >= PANEL_Y && mouseY < PANEL_Y + PANEL_HEIGHT;
    }
}
