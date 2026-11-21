package net.neoforged.neoforge.client.event;

import net.minecraft.client.Camera;
import net.neoforged.bus.api.Event;

public class CalculateDetachedCameraDistanceEvent extends Event {
    private final Camera camera;
    private float distance;

    public CalculateDetachedCameraDistanceEvent(Camera camera, float distance) {
        this.camera = camera;
        this.distance = distance;
    }

    public Camera getCamera() {
        return camera;
    }

    public float getDistance() {
        return distance;
    }

    public void setDistance(float distance) {
        this.distance = distance;
    }
}
