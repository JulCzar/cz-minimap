package com.czminimap.rendering.minimap;

import com.czminimap.cartography.CartographicMap;
import com.czminimap.cartography.CartographicSnapshot;
import com.czminimap.client.ClientGameEvents;
import com.czminimap.client.ClientMapContext;
import com.czminimap.client.WorldIdentityResolver;
import com.czminimap.config.ClientConfig;
import com.czminimap.core.DimensionKey;
import com.czminimap.core.WorldKey;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import org.joml.Quaternionf;

/** Client-only HUD renderer. All world-derived pixels come from a versioned cartographic snapshot. */
public final class MinimapRenderer {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath("czminimap", "hud_minimap");
    private static final long MIN_REFRESH_INTERVAL_MS = 100L;

    private DynamicTexture texture;
    private int textureWidth;
    private int textureHeight;
    private MinimapImage current;
    private MinimapImage pending;

    private long lastAreaRevision = -1L;
    private long lastBuildMillis;
    private int lastCenterX = Integer.MIN_VALUE;
    private int lastCenterZ = Integer.MIN_VALUE;
    private int lastZoom = Integer.MIN_VALUE;
    private int lastWidth = Integer.MIN_VALUE;
    private int lastHeight = Integer.MIN_VALUE;
    private boolean lastCircular;
    private boolean lastNorthUp;
    private WorldKey lastWorld;
    private DimensionKey lastDimension;

    /** Called on the game thread. Builds pixel data only when an input actually changed. */
    public void tick(Minecraft minecraft) {
        if (!isActive(minecraft)) {
            lastAreaRevision = -1L;
            pending = null;
            return;
        }

        WorldKey world = ClientMapContext.get().mapManager().activeWorld();
        if (world == null) {
            return;
        }
        DimensionKey dimension = WorldIdentityResolver.resolveDimension(minecraft.level);
        int zoom = Math.max(0, Math.min(8,
                ClientConfig.CONFIG.zoom.get() + ClientGameEvents.uiState().getMinimapZoomSteps()));
        int width = ClientConfig.CONFIG.width.get();
        int height = ClientConfig.CONFIG.height.get();
        boolean circular = ClientConfig.CONFIG.circular.get();
        boolean northUp = ClientConfig.CONFIG.northUp.get();
        int centerX = minecraft.player.blockPosition().getX();
        int centerZ = minecraft.player.blockPosition().getZ();
        double scale = blocksPerPixel(zoom);
        int horizontalRadius = (int) Math.ceil(width * scale * 0.5) + 1;
        int verticalRadius = (int) Math.ceil(height * scale * 0.5) + 1;

        CartographicMap map = ClientMapContext.get().map();
        long areaRevision = map.areaRevision(world, dimension,
                centerX - horizontalRadius, centerZ - verticalRadius,
                centerX + horizontalRadius, centerZ + verticalRadius);
        boolean versionChanged = lastAreaRevision != areaRevision;
        boolean inputsChanged = !world.equals(lastWorld)
                || !dimension.equals(lastDimension)
                || centerX != lastCenterX
                || centerZ != lastCenterZ
                || zoom != lastZoom
                || width != lastWidth
                || height != lastHeight
                || circular != lastCircular
                || northUp != lastNorthUp;
        if (!versionChanged && !inputsChanged) {
            return;
        }

        long now = System.currentTimeMillis();
        if (lastBuildMillis > 0L && now - lastBuildMillis < MIN_REFRESH_INTERVAL_MS) {
            return;
        }

        CartographicSnapshot snapshot = map.snapshot();
        pending = MinimapImage.build(snapshot, world, dimension, centerX, centerZ, width, height,
                scale, circular);
        lastAreaRevision = areaRevision;
        lastBuildMillis = now;
        lastCenterX = centerX;
        lastCenterZ = centerZ;
        lastZoom = zoom;
        lastWidth = width;
        lastHeight = height;
        lastCircular = circular;
        lastNorthUp = northUp;
        lastWorld = world;
        lastDimension = dimension;
    }

    /** Called on the render thread. Only texture upload and drawing happen here. */
    public void render(GuiGraphics graphics, Minecraft minecraft) {
        if (!isActive(minecraft)) {
            return;
        }
        if (pending != null) {
            applyPending(minecraft);
        }
        if (current == null || texture == null) {
            return;
        }
        draw(graphics, minecraft);
    }

    /** Drops cached pixel data without touching GPU resources. */
    public void invalidate() {
        current = null;
        pending = null;
        lastAreaRevision = -1L;
        lastWorld = null;
        lastDimension = null;
    }

    public void close() {
        if (texture != null) {
            Minecraft.getInstance().getTextureManager().release(TEXTURE);
            texture.close();
            texture = null;
        }
        invalidate();
    }

    private void applyPending(Minecraft minecraft) {
        MinimapImage image = pending;
        ensureTexture(minecraft.getTextureManager(), image.width(), image.height());
        NativeImage pixels = texture.getPixels();
        int[] argb = image.pixels();
        for (int y = 0; y < image.height(); y++) {
            for (int x = 0; x < image.width(); x++) {
                pixels.setPixelRGBA(x, y, toAbgr(argb[x + y * image.width()]));
            }
        }
        texture.upload();
        current = image;
        pending = null;
    }

    private void ensureTexture(TextureManager manager, int width, int height) {
        if (texture != null && (textureWidth != width || textureHeight != height)) {
            manager.release(TEXTURE);
            texture.close();
            texture = null;
        }
        if (texture == null) {
            texture = new DynamicTexture(width, height, false);
            textureWidth = width;
            textureHeight = height;
            manager.register(TEXTURE, texture);
        }
    }

    private void draw(GuiGraphics graphics, Minecraft minecraft) {
        MinimapLayout layout = MinimapLayout.resolve(
                new MinimapLayout.ScreenArea(
                        minecraft.getWindow().getGuiScaledWidth(),
                        minecraft.getWindow().getGuiScaledHeight()),
                ClientConfig.CONFIG.position.get(),
                current.width(), current.height(),
                ClientConfig.CONFIG.customX.get(), ClientConfig.CONFIG.customY.get());

        float opacity = ClientConfig.CONFIG.opacity.get() / 100f;
        float backgroundAlpha = ClientConfig.CONFIG.backgroundOpacity.get() / 100f;
        int background = ((int) (backgroundAlpha * 255) << 24) | 0x101018;
        graphics.fill(layout.x() - 2, layout.y() - 2,
                layout.x() + layout.width() + 2, layout.y() + layout.height() + 2, background);
        graphics.setColor(1f, 1f, 1f, opacity);

        int centerX = layout.x() + layout.width() / 2;
        int centerY = layout.y() + layout.height() / 2;
        float yaw = minecraft.player.getYRot();
        boolean northUp = ClientConfig.CONFIG.northUp.get();
        float mapAngle = northUp ? 0f : (float) (Math.PI - Math.toRadians(yaw));

        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(centerX, centerY, 0);
        pose.rotateAround(new Quaternionf().rotationZ(mapAngle), 0, 0, 0);
        pose.translate(-centerX, -centerY, 0);
        graphics.blit(TEXTURE, layout.x(), layout.y(), 0,
                0f, 0f, layout.width(), layout.height(), layout.width(), layout.height());
        if (ClientConfig.CONFIG.showCompass.get()) {
            drawCompass(graphics, minecraft, centerX, centerY);
        }
        pose.popPose();
        graphics.setColor(1f, 1f, 1f, 1f);

        drawPlayer(graphics, centerX, centerY, northUp, yaw);
        if (ClientConfig.CONFIG.showCoordinates.get()) {
            var position = minecraft.player.blockPosition();
            graphics.drawString(minecraft.font, "X " + position.getX() + "  Z " + position.getZ(),
                    layout.x(), layout.y() + layout.height() + 4, 0xFFFFFFFF);
        }
    }

    private void drawCompass(GuiGraphics graphics, Minecraft minecraft, int centerX, int centerY) {
        int radius = Math.min(currentWidth(), currentHeight()) / 2 + 2;
        graphics.drawString(minecraft.font, "N", centerX - 3, centerY - radius - 10, 0xFFFFFFFF);
        graphics.drawString(minecraft.font, "S", centerX - 3, centerY + radius + 2, 0xFFFFFFFF);
        graphics.drawString(minecraft.font, "E", centerX + radius + 2, centerY - 4, 0xFFFFFFFF);
        graphics.drawString(minecraft.font, "W", centerX - radius - 10, centerY - 4, 0xFFFFFFFF);
    }

    private static void drawPlayer(GuiGraphics graphics, int centerX, int centerY,
                                   boolean northUp, float yaw) {
        float angle = northUp ? (float) (Math.PI + Math.toRadians(yaw)) : 0f;
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(centerX, centerY, 0);
        pose.rotateAround(new Quaternionf().rotationZ(angle), 0, 0, 0);
        graphics.fill(-2, -7, 2, -1, 0xFFFFFFFF);
        graphics.fill(-4, -1, 4, 3, 0xFFFFFFFF);
        graphics.fill(-1, 3, 1, 7, 0xFFB0B0B0);
        pose.popPose();
    }

    private static boolean isActive(Minecraft minecraft) {
        return minecraft.level != null
                && minecraft.player != null
                && minecraft.screen == null
                && ClientConfig.CONFIG.enabled.get()
                && ClientGameEvents.uiState().isMinimapVisible()
                && !ClientGameEvents.uiState().isMapOpen();
    }

    private static double blocksPerPixel(int zoom) {
        return 8.0 / (1 << Math.min(zoom, 6));
    }

    private static int toAbgr(int argb) {
        int alpha = (argb >>> 24) & 0xFF;
        int red = (argb >>> 16) & 0xFF;
        int green = (argb >>> 8) & 0xFF;
        int blue = argb & 0xFF;
        return (alpha << 24) | (blue << 16) | (green << 8) | red;
    }

    private int currentWidth() {
        return current == null ? 0 : current.width();
    }

    private int currentHeight() {
        return current == null ? 0 : current.height();
    }
}
