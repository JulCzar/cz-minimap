package com.czminimap.rendering.worldmap;

import com.czminimap.cartography.CartographicMap;
import com.czminimap.cartography.CartographicSnapshot;
import com.czminimap.client.ClientGameEvents;
import com.czminimap.client.ClientMapContext;
import com.czminimap.client.WorldIdentityResolver;
import com.czminimap.config.ClientConfig;
import com.czminimap.config.MapConfig;
import com.czminimap.core.DimensionKey;
import com.czminimap.core.WorldKey;
import com.czminimap.cutaway.ClipSide;
import com.czminimap.cutaway.CutawaySettings;
import com.czminimap.cutaway.VerticalAxis;
import com.czminimap.layers.CumulativeDirection;
import com.czminimap.layers.LayerMode;
import com.czminimap.layers.LayerRange;
import com.czminimap.layers.LayerResolver;
import com.czminimap.layers.LayerVisibility;
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
import java.util.Objects;

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
    private int lastRenderRadius = Integer.MIN_VALUE;
    private int lastLayerStep;
    private int selectedLayerIndex;
    private int layerInterval = -1;
    private long renderVersion = Long.MIN_VALUE;
    private WorldKey lastWorld;
    private DimensionKey lastDimension;
    private DimensionKey layerDimension;
    private LayerMode layerMode;
    private CumulativeDirection cumulativeDirection;
    private CutawaySettings cutaway = CutawaySettings.none();
    private LayerVisibility layerVisibility;
    private LayerVisibility lastBuiltVisibility;

    public OrbitalCamera camera() {
        return camera;
    }

    /** Called on the game thread. Refreshes camera requests and rebuilds a bounded number of chunk meshes. */
    public void tick(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        ClientConfig config = ClientConfig.CONFIG;
        int cacheLimit = config.worldMapMaxCachedChunks.get();
        int radius = WorldMapMeshCache.fitRadiusToLimit(config.worldMapRenderRadius.get(), cacheLimit);
        camera.setDistanceLimits(8.0, Math.max(96.0, radius * 16.0 * 4.0));
        cache.setLimit(cacheLimit);

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
        updateLayerState(minecraft, dimension);
        CartographicMap map = ClientMapContext.get().map();
        int centerX = Math.floorDiv((int) Math.floor(camera.target().x), 16);
        int centerZ = Math.floorDiv((int) Math.floor(camera.target().z), 16);
        boolean changed = lastVersion != map.version()
                || lastCenterChunkX != centerX
                || lastCenterChunkZ != centerZ
                || lastRenderRadius != radius
                || !world.equals(lastWorld)
                || !dimension.equals(lastDimension)
                || !Objects.equals(layerVisibility, lastBuiltVisibility);
        if (!changed) {
            return;
        }

        CartographicSnapshot snapshot = map.snapshot();
        renderVersion = snapshot.version();
        cache.retainRadius(centerX, centerZ, radius);
        List<int[]> chunks = chunksByDistance(centerX, centerZ, radius);
        int budget = config.worldMapMeshBudget.get();
        boolean allRebuilt = true;
        for (int[] chunk : chunks) {
            if (!cache.needsRebuild(snapshot, layerVisibility, chunk[0], chunk[1])) {
                continue;
            }
            if (budget <= 0) {
                allRebuilt = false;
                continue;
            }
            cache.get(snapshot, world, dimension, layerVisibility, chunk[0], chunk[1]);
            budget--;
        }
        if (!allRebuilt) {
            return;
        }
        lastVersion = map.version();
        lastCenterChunkX = centerX;
        lastCenterChunkZ = centerZ;
        lastRenderRadius = radius;
        lastWorld = world;
        lastDimension = dimension;
        lastBuiltVisibility = layerVisibility;
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
        if (layerVisibility != null) {
            for (TerrainMesh mesh : cache.meshes(renderVersion, layerVisibility)) {
                emit(consumer, pose, mesh.vertices());
            }
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
        renderVersion = Long.MIN_VALUE;
        lastWorld = null;
        lastDimension = null;
        lastRenderRadius = Integer.MIN_VALUE;
        lastBuiltVisibility = null;
        layerVisibility = null;
        layerDimension = null;
        layerInterval = -1;
    }

    public int cachedMeshCount() {
        return layerVisibility == null ? 0 : cache.meshes(renderVersion, layerVisibility).size();
    }

    public LayerMode layerMode() {
        return layerMode == null ? MapConfig.CONFIG.defaultLayerMode.get() : layerMode;
    }

    public CumulativeDirection cumulativeDirection() {
        return cumulativeDirection == null ? MapConfig.CONFIG.cumulativeDirection.get() : cumulativeDirection;
    }

    public LayerRange selectedLayer() {
        if (layerDimension == null || layerInterval <= 0) {
            return null;
        }
        return LayerResolver.rangeAt(layerDimension, layerInterval, selectedLayerIndex);
    }

    public List<LayerRange> visibleLayers() {
        return layerVisibility == null ? List.of() : layerVisibility.ranges();
    }

    public CutawaySettings cutawaySettings() {
        return cutaway;
    }

    public void cycleLayerMode() {
        layerMode = layerMode().next();
    }

    public void changeLayer(int delta) {
        if (layerDimension == null || layerInterval <= 0 || delta == 0) {
            return;
        }
        selectedLayerIndex = LayerResolver.clampIndex(
                layerDimension, layerInterval, selectedLayerIndex + delta);
        syncHorizontalCut();
    }

    public void toggleCumulativeDirection() {
        cumulativeDirection = cumulativeDirection().toggle();
    }

    public void toggleHorizontalCutaway() {
        cutaway = cutaway.withHorizontalEnabled(!cutaway.horizontalEnabled());
        syncHorizontalCut();
    }

    public void toggleVerticalCutaway() {
        cutaway = cutaway.withVerticalEnabled(!cutaway.verticalEnabled());
    }

    public void toggleCutawayAxis() {
        VerticalAxis axis = cutaway.axis().toggle();
        int coordinate = (int) Math.floor(axis == VerticalAxis.X ? camera.target().x : camera.target().z);
        cutaway = cutaway.withAxis(axis).withCoordinate(coordinate);
    }

    public void toggleCutawaySide() {
        cutaway = cutaway.withVisibleSide(cutaway.visibleSide().toggle());
    }

    public void moveVerticalCut(int blocks) {
        long moved = (long) cutaway.coordinate() + blocks;
        int coordinate = (int) Math.clamp(moved, -30_000_000L, 30_000_000L);
        cutaway = cutaway.withCoordinate(coordinate);
    }

    private void updateLayerState(Minecraft minecraft, DimensionKey dimension) {
        int configuredInterval = MapConfig.CONFIG.layerInterval.get();
        if (layerMode == null) {
            layerMode = MapConfig.CONFIG.defaultLayerMode.get();
        }
        if (cumulativeDirection == null) {
            cumulativeDirection = MapConfig.CONFIG.cumulativeDirection.get();
        }

        if (!dimension.equals(layerDimension) || configuredInterval != layerInterval) {
            int referenceY = minecraft.player == null
                    ? dimension.minY()
                    : minecraft.player.blockPosition().getY();
            if (dimension.equals(layerDimension) && layerInterval > 0) {
                LayerRange previous = LayerResolver.rangeAt(layerDimension, layerInterval, selectedLayerIndex);
                referenceY = previous.minYInclusive();
            }
            layerDimension = dimension;
            layerInterval = configuredInterval;
            selectedLayerIndex = LayerResolver.indexForY(dimension, configuredInterval, referenceY);
            LayerRange selected = selectedLayer();
            int verticalCoordinate = (int) Math.floor(camera.target().x);
            cutaway = new CutawaySettings(true, selected.maxYExclusive(), false,
                    VerticalAxis.X, verticalCoordinate, ClipSide.NEGATIVE);
        }

        int requestedStep = ClientGameEvents.uiState().getLayerStep();
        int delta = requestedStep - lastLayerStep;
        lastLayerStep = requestedStep;
        changeLayer(delta);
        layerVisibility = LayerResolver.resolve(dimension, configuredInterval, layerMode,
                selectedLayerIndex, cumulativeDirection, cutaway);
    }

    private void syncHorizontalCut() {
        LayerRange selected = selectedLayer();
        if (selected != null) {
            cutaway = cutaway.withMaxYExclusive(selected.maxYExclusive());
        }
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
