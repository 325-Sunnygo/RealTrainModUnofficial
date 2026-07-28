package com.portofino.realtrainmodunofficial.seat;

import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.List;

public class SeatDefinition {
    private String id;
    private SeatType type;
    private Vec3 position;
    private Vector3f rotation;
    private List<Vec3> polygon;
    private double height = 1.0;
    private boolean isDriver = false;
    
    public enum SeatType {
        DRIVER,
        PASSENGER
    }
    
    public SeatDefinition() {}
    
    public SeatDefinition(String id, SeatType type, Vec3 position, List<Vec3> polygon) {
        this.id = id;
        this.type = type;
        this.position = position;
        this.polygon = polygon;
        this.rotation = new Vector3f(0, 0, 0);
    }
    
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public SeatType getType() { return type; }
    public void setType(SeatType type) { this.type = type; }
    
    public Vec3 getPosition() { return position; }
    public void setPosition(Vec3 position) { this.position = position; }
    
    public Vector3f getRotation() { return rotation; }
    public void setRotation(Vector3f rotation) { this.rotation = rotation; }
    
    public List<Vec3> getPolygon() { return polygon; }
    public void setPolygon(List<Vec3> polygon) { this.polygon = polygon; }
    
    public double getHeight() { return height; }
    public void setHeight(double height) { this.height = height; }
    
    public boolean isDriver() { return isDriver; }
    public void setDriver(boolean driver) { isDriver = driver; }
    
    /**
     * Check if a point is within the seat polygon area
     */
    public boolean isPointInPolygon(Vec3 point) {
        if (polygon == null || polygon.size() < 3) {
            return false;
        }
        
        // Simple point-in-polygon test for 2D (XZ plane)
        boolean inside = false;
        int j = polygon.size() - 1;
        
        for (int i = 0; i < polygon.size(); i++) {
            Vec3 pi = polygon.get(i);
            Vec3 pj = polygon.get(j);
            
            if (((pi.z > point.z) != (pj.z > point.z)) &&
                (point.x < (pj.x - pi.x) * (point.z - pi.z) / (pj.z - pi.z) + pi.x)) {
                inside = !inside;
            }
            j = i;
        }
        
        return inside;
    }
    
    /**
     * Check if a point is within the seat bounds (including height)
     */
    public boolean isPointInSeat(Vec3 point) {
        if (!isPointInPolygon(point)) {
            return false;
        }
        
        double minY = position.y;
        double maxY = position.y + height;
        
        return point.y >= minY && point.y <= maxY;
    }
}
