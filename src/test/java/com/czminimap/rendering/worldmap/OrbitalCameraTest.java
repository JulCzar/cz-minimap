package com.czminimap.rendering.worldmap;

import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrbitalCameraTest {
    @Test
    void focusMovesTargetAndClampsDistance() {
        OrbitalCamera camera = new OrbitalCamera();
        camera.setDistanceLimits(10.0, 100.0);

        camera.focus(100.0, 64.0, -40.0, 500.0);

        assertEquals(100.0, camera.target().x);
        assertEquals(64.0, camera.target().y);
        assertEquals(-40.0, camera.target().z);
        assertEquals(100.0, camera.distance());
    }

    @Test
    void rotateChangesAnglesAndClampsPitch() {
        OrbitalCamera camera = new OrbitalCamera();
        double initialYaw = camera.yaw();

        camera.rotate(50.0, 20.0, 1.0);
        assertNotEquals(initialYaw, camera.yaw());
        assertTrue(camera.pitch() < Math.PI / 2);
        assertTrue(camera.pitch() > -Math.PI / 2);

        camera.resetOrientation();
        assertEquals(initialYaw, camera.yaw());
    }

    @Test
    void zoomRespectsDistanceLimits() {
        OrbitalCamera camera = new OrbitalCamera();
        camera.setDistanceLimits(8.0, 64.0);

        camera.zoom(-100.0, 1.0);
        assertEquals(8.0, camera.distance());

        camera.zoom(100.0, 1.0);
        assertEquals(64.0, camera.distance());
    }

    @Test
    void eyeOrbitsTargetAtConfiguredDistance() {
        OrbitalCamera camera = new OrbitalCamera();
        camera.focus(10.0, 20.0, 30.0, 48.0);

        var eye = camera.eye();
        double dx = eye.x - 10.0;
        double dy = eye.y - 20.0;
        double dz = eye.z - 30.0;

        assertEquals(48.0, Math.sqrt(dx * dx + dy * dy + dz * dz), 1e-6);
    }

    @Test
    void viewMatrixPlacesEyeAtOrigin() {
        OrbitalCamera camera = new OrbitalCamera();
        camera.focus(12.0, 64.0, -8.0, 40.0);
        camera.rotate(30.0, 15.0, 1.0);

        var eye = camera.eye();
        Matrix4f view = camera.viewMatrix();
        Vector4f result = new Vector4f((float) eye.x, (float) eye.y, (float) eye.z, 1.0f).mul(view);

        assertEquals(0.0f, result.x, 1e-3f);
        assertEquals(0.0f, result.y, 1e-3f);
        assertEquals(0.0f, result.z, 1e-3f);
    }

    @Test
    void projectionMatrixUsesRequestedFov() {
        OrbitalCamera camera = new OrbitalCamera();
        Matrix4f projection = camera.projectionMatrix(1.0f, 70.0f, 0.1f, 100.0f);

        assertTrue(projection.m00() > 0.0f);
        assertTrue(projection.m11() > 0.0f);
        assertTrue(projection.m22() < 0.0f);
    }
}
