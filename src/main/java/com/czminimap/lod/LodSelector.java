package com.czminimap.lod;

/** Chooses detail from camera distance and projected block size. */
public final class LodSelector {
    private LodSelector() {
    }

    public static LodLevel select(double distance, int viewportHeight, double fovDegrees,
                                  int maximumLevel) {
        double safeDistance = Math.max(1.0, distance);
        double halfFov = Math.toRadians(Math.clamp(fovDegrees, 1.0, 179.0) * 0.5);
        double pixelsPerBlock = Math.max(1, viewportHeight)
                / (2.0 * Math.tan(halfFov) * safeDistance);

        int projectedLevel;
        if (pixelsPerBlock >= 4.0) {
            projectedLevel = 0;
        } else if (pixelsPerBlock >= 2.0) {
            projectedLevel = 1;
        } else if (pixelsPerBlock >= 1.0) {
            projectedLevel = 2;
        } else {
            projectedLevel = 3;
        }
        return LodLevel.fromIndex(Math.min(projectedLevel, Math.clamp(maximumLevel, 0, 3)));
    }
}
