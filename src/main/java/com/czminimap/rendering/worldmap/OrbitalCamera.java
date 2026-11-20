package com.czminimap.rendering.worldmap;

import org.joml.Matrix4f;
import org.joml.Vector3d;

/** Camera state and matrix maths for the orbital 3D map. No Minecraft rendering state is touched here. */
public final class OrbitalCamera {
    private static final double MIN_PITCH = -Math.PI / 2 + 0.05;
    private static final double MAX_PITCH = Math.PI / 2 - 0.05;
    private static final double DEFAULT_YAW = Math.toRadians(-35.0);
    private static final double DEFAULT_PITCH = Math.toRadians(45.0);
    private static final double ROTATE_SCALE = 0.008;
    private static final double PAN_SCALE = 0.0025;
    private static final double ZOOM_STEP = 1.15;

    private final Vector3d target = new Vector3d();
    private double distance = 96.0;
    private double yaw = DEFAULT_YAW;
    private double pitch = DEFAULT_PITCH;
    private double minDistance = 8.0;
    private double maxDistance = 512.0;

    public Vector3d target() {
        return target;
    }

    public double distance() {
        return distance;
    }

    public double yaw() {
        return yaw;
    }

    public double pitch() {
        return pitch;
    }

    public void setDistanceLimits(double min, double max) {
        minDistance = Math.max(1.0, min);
        maxDistance = Math.max(minDistance, max);
        distance = clamp(distance, minDistance, maxDistance);
    }

    public void focus(double x, double y, double z, double newDistance) {
        target.set(x, y, z);
        distance = clamp(newDistance, minDistance, maxDistance);
    }

    public void resetOrientation() {
        yaw = DEFAULT_YAW;
        pitch = DEFAULT_PITCH;
    }

    public void rotate(double deltaX, double deltaY, double sensitivity) {
        yaw -= deltaX * ROTATE_SCALE * sensitivity;
        pitch = clamp(pitch + deltaY * ROTATE_SCALE * sensitivity, MIN_PITCH, MAX_PITCH);
    }

    public void pan(double deltaX, double deltaY, double sensitivity) {
        double scale = distance * PAN_SCALE * sensitivity;
        double sinYaw = Math.sin(yaw);
        double cosYaw = Math.cos(yaw);
        double sinPitch = Math.sin(pitch);
        double cosPitch = Math.cos(pitch);
        target.x += (-cosYaw * deltaX - sinYaw * sinPitch * deltaY) * scale;
        target.y += cosPitch * deltaY * scale;
        target.z += (sinYaw * deltaX - cosYaw * sinPitch * deltaY) * scale;
    }

    public void zoom(double notches, double sensitivity) {
        distance = clamp(distance * Math.pow(ZOOM_STEP, notches * sensitivity), minDistance, maxDistance);
    }

    public Vector3d eye() {
        double cosPitch = Math.cos(pitch);
        return new Vector3d(
                target.x + distance * cosPitch * Math.sin(yaw),
                target.y + distance * Math.sin(pitch),
                target.z + distance * cosPitch * Math.cos(yaw));
    }

    public Matrix4f viewMatrix() {
        Vector3d eye = eye();
        return new Matrix4f().lookAt(
                (float) eye.x, (float) eye.y, (float) eye.z,
                (float) target.x, (float) target.y, (float) target.z,
                0.0f, 1.0f, 0.0f);
    }

    public Matrix4f projectionMatrix(float aspect, float fovDegrees, float near, float far) {
        return new Matrix4f().perspective((float) Math.toRadians(fovDegrees), aspect, near, far);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
