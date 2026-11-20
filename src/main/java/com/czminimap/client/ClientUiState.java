package com.czminimap.client;

public final class ClientUiState {
    private boolean minimapVisible = true;
    private boolean mapOpen;
    private int minimapZoomSteps;
    private int layerStep;
    private long cameraResetRequests;
    private long returnToPlayerRequests;

    public boolean isMinimapVisible() {
        return minimapVisible;
    }

    public void toggleMinimap() {
        minimapVisible = !minimapVisible;
    }

    public boolean isMapOpen() {
        return mapOpen;
    }

    public void toggleMap() {
        mapOpen = !mapOpen;
    }

    public int getMinimapZoomSteps() {
        return minimapZoomSteps;
    }

    public void changeMinimapZoom(int delta) {
        minimapZoomSteps = Math.clamp(minimapZoomSteps + delta, -3, 3);
    }

    public int getLayerStep() {
        return layerStep;
    }

    public void changeLayer(int delta) {
        layerStep += delta;
    }

    public long getCameraResetRequests() {
        return cameraResetRequests;
    }

    public void requestCameraReset() {
        cameraResetRequests++;
    }

    public long getReturnToPlayerRequests() {
        return returnToPlayerRequests;
    }

    public void requestReturnToPlayer() {
        returnToPlayerRequests++;
    }
}
