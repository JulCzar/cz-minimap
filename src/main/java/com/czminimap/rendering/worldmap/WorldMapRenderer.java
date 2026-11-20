package com.czminimap.rendering.worldmap;

import com.czminimap.cartography.CartographicMap;
import com.czminimap.cartography.CartographicSnapshot;
import com.czminimap.client.ClientGameEvents;
import com.czminimap.client.ClientMapContext;
import com.czminimap.client.WorldIdentityResolver;
import com.czminimap.config.ClientConfig;
import com.czminimap.core.DimensionKey;
import com.czminimap.core.WorldKey;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Client-only renderer for the full-screen voxel map. All geometry comes from cartographic snapshots. */
public final class WorldMapRenderer {
    private static final RenderType TERRAIN = RenderType.create(
            "czminimap_terrain",
            DefaultVertexFormat.POSITION_COLOR,
            com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,
            262144,
            false,
            false,
            RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getPositionColorShader))
                    .setTransparencyState(RenderStateShard.NO_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .createCompositeState(false));

    private final OrbitalCamera camera = new OrbitalCamera();
    private final WorldMapMeshCache cache = new WorldMapMeshCache();

    private long lastVersion = Long.MIN_VALUE;
    private long lastResetRequests = -1L;
    private long lastReturnRequests = -1L;
    private int lastCenterChunkX = Integer.MIN_VALUE;
    private int lastCenterChunkZ = Integer.MIN_VALUE;
    private WorldKey lastWorld;
    private DimensionKey lastDimension;

    public OrbitalCamera camera() {
        return camera;
    }

    /** Called on the game thread. Refreshes camera requests and rebuilds a bounded number of chunk meshes. */
    public void tick(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        ClientConfig config = ClientConfig.CONFIG;
        camera.setDistanceLimits(8.0, Math.max(96.0, config.worldMapRenderRadius.get() * 16.0 * 4.0));
        cache.setLimit(config.worldMapMaxCachedChunks.get());

        var ui = ClientGameEvents.uiState();
        if (ui.getCameraResetRequests() != lastResetRequests) {
            lastResetRequests = ui.getCameraResetRequests();
            resetCamera(minecraft);
        }
        if (ui.getReturnToPlayerRequests() != lastReturnRequests) {
            lastReturnRequests = ui.getReturnToPlayerRequests();
            returnToPlayer(minecraft);
        }

        WorldKey world = ClientMapContext.get().mapManager().activeWorld();
        if (world == null) {
            return;
        }
        DimensionKey dimension = WorldIdentityResolver.resolveDimension(minecraft.level);
        CartographicMap map = ClientMapContext.get().map();
        int centerX = Math.floorDiv((int) Math.floor(camera.target().x), 16);
        int centerZ = Math.floorDiv((int) Math.floor(camera.target().z), 16);
        boolean changed = lastVersion != map.version()
                || lastCenterChunkX != centerX
                || lastCenterChunkZ != centerZ
                || !world.equals(lastWorld)
                || !dimension.equals(lastDimension);
        if (!changed) {
            return;
        }

        CartographicSnapshot snapshot = map.snapshot();
        int radius = config.worldMapRenderRadius.get();
        cache.retainRadius(centerX, centerZ, radius);
        List<int[]> chunks = chunksByDistance(centerX, centerZ, radius);
        int budget = config.worldMapMeshBudget.get();
        boolean allRebuilt = true;
        for (int[] chunk : chunks) {
            if (!cache.needsRebuild(snapshot, chunk[0], chunk[1])) {
                continue;
            }
            if (budget <= 0) {
                allRebuilt = false;
                continue;
            }
            cache.get(snapshot, world, dimension, chunk[0], chunk[1]);
            budget--;
        }
        if (!allRebuilt) {
            return;
        }
        lastVersion = map.version();
        lastCenterChunkX = centerX;
        lastCenterChunkZ = centerZ;
        lastWorld = world;
        lastDimension = dimension;
    }

    /** Called on the render thread. Only GPU uploads and draw calls happen here. */
    public void render(GuiGraphics graphics, Minecraft minecraft) {
        graphics.flush();
        int screenWidth = minecraft.getWindow().getWidth();
        int screenHeight = minecraft.getWindow().getHeight();
        float aspect = (float) screenWidth / Math.max(1, screenHeight);

        Matrix4f savedProjection = RenderSystem.getProjectionMatrix();
        graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), background());
        graphics.flush();

        float far = (float) Math.max(256.0, camera.distance() * 8.0);
        Matrix4f projection = camera.projectionMatrix(aspect,
                ClientConfig.CONFIG.worldMapFov.get().floatValue(), 0.1f, far);
        Matrix4f view = camera.viewMatrix();

        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        // Screens render with a GUI-specific Z translation on the model-view stack; drop it for 3D geometry.
        modelView.identity();
        modelView.mul(view);
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setProjectionMatrix(projection, VertexSorting.DISTANCE_TO_ORIGIN);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.clearDepth(1.0);

        PoseStack pose = new PoseStack();
        MultiBufferSource.BufferSource buffers = graphics.bufferSource();
        VertexConsumer consumer = buffers.getBuffer(TERRAIN);
        for (TerrainMesh mesh : cache.meshes()) {
            emit(consumer, pose, mesh.vertices());
        }
        if (minecraft.player != null) {
            var player = minecraft.player;
            float partial = minecraft.getTimer().getGameTimeDeltaPartialTick(true);
            double x = player.xo + (player.getX() - player.xo) * partial;
            double y = player.yo + (player.getY() - player.yo) * partial;
            double z = player.zo + (player.getZ() - player.zo) * partial;
            emitMarker(consumer, pose, x, y, z);
        }
        buffers.endBatch(TERRAIN);

        RenderSystem.depthMask(true);
        RenderSystem.disableDepthTest();
        modelView.popMatrix();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setProjectionMatrix(savedProjection, VertexSorting.ORTHOGRAPHIC_Z);
    }

    /** Invalidates presentation caches without touching GPU resources. */
    public void invalidate() {
        cache.clear();
        lastVersion = Long.MIN_VALUE;
        lastWorld = null;
        lastDimension = null;
    }

    public int cachedMeshCount() {
        return cache.size();
    }

    private void resetCamera(Minecraft minecraft) {
        if (minecraft.player == null) {
            return;
        }
        returnToPlayer(minecraft);
        camera.resetOrientation();
    }

    private void returnToPlayer(Minecraft minecraft) {
        if (minecraft.player == null) {
            return;
        }
        var position = minecraft.player.blockPosition();
        camera.focus(position.getX() + 0.5, position.getY() + 0.5, position.getZ() + 0.5, 96.0);
    }

    private static List<int[]> chunksByDistance(int centerX, int centerZ, int radius) {
        List<int[]> chunks = new ArrayList<>();
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                chunks.add(new int[]{centerX + dx, centerZ + dz});
            }
        }
        chunks.sort(Comparator.comparingInt((int[] c) -> {
            int dx = c[0] - centerX;
            int dz = c[1] - centerZ;
            return dx * dx + dz * dz;
        }));
        return chunks;
    }

    private static void emit(VertexConsumer consumer, PoseStack pose, float[] vertices) {
        PoseStack.Pose matrix = pose.last();
        for (int i = 0; i < vertices.length; i += TerrainMesh.FLOATS_PER_VERTEX) {
            consumer.addVertex(matrix,
                            vertices[i], vertices[i + 1], vertices[i + 2])
                    .setColor(vertices[i + 3], vertices[i + 4], vertices[i + 5], vertices[i + 6]);
        }
    }

    private static void emitMarker(VertexConsumer consumer, PoseStack pose, double x, double y, double z) {
        PoseStack.Pose matrix = pose.last();
        float radius = 0.6f;
        float top = (float) (y + 2.2f);
        float bottom = (float) (y - 0.4f);
        float[][] pyramid = {
                {(float) x, top, (float) z},
                {(float) (x - radius), bottom, (float) (z - radius)},
                {(float) (x + radius), bottom, (float) (z - radius)},
                {(float) (x + radius), bottom, (float) (z + radius)},
                {(float) (x - radius), bottom, (float) (z + radius)}
        };
        addQuad(consumer, matrix, pyramid[0], pyramid[1], pyramid[2], 0.2f, 1f, 1f);
        addQuad(consumer, matrix, pyramid[0], pyramid[2], pyramid[3], 0.2f, 1f, 1f);
        addQuad(consumer, matrix, pyramid[0], pyramid[3], pyramid[4], 0.2f, 1f, 1f);
        addQuad(consumer, matrix, pyramid[0], pyramid[4], pyramid[1], 0.2f, 1f, 1f);
    }

    private static void addQuad(VertexConsumer consumer, PoseStack.Pose matrix,
                                float[] a, float[] b, float[] c, float red, float green, float blue) {
        consumer.addVertex(matrix, a[0], a[1], a[2]).setColor(red, green, blue, 1f);
        consumer.addVertex(matrix, b[0], b[1], b[2]).setColor(red, green, blue, 1f);
        consumer.addVertex(matrix, c[0], c[1], c[2]).setColor(red, green, blue, 1f);
        consumer.addVertex(matrix, c[0], c[1], c[2]).setColor(red, green, blue, 1f);
    }

    private static int background() {
        int alpha = ClientConfig.CONFIG.worldMapBackgroundOpacity.get() * 255 / 100;
        return (alpha << 24) | 0x0B0D12;
    }
}
