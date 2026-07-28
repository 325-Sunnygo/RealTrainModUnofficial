package com.portofino.realtrainmodunofficial.formation;

import java.util.ArrayList;
import java.util.List;

public class TrainFormation {
    private final List<String> vehicleIds;
    private String name;
    
    public TrainFormation() {
        this.vehicleIds = new ArrayList<>();
        this.name = "";
    }
    
    public TrainFormation(List<String> vehicleIds, String name) {
        this.vehicleIds = new ArrayList<>(vehicleIds);
        this.name = name != null ? name : "";
    }
    
    public void addVehicle(String vehicleId) {
        if (vehicleIds.size() < 30) {
            vehicleIds.add(vehicleId);
        }
    }
    
    public void removeVehicle(int index) {
        if (index >= 0 && index < vehicleIds.size()) {
            vehicleIds.remove(index);
        }
    }
    
    public void setVehicle(int index, String vehicleId) {
        if (index >= 0 && index < vehicleIds.size()) {
            vehicleIds.set(index, vehicleId);
        }
    }
    
    public String getVehicle(int index) {
        if (index >= 0 && index < vehicleIds.size()) {
            return vehicleIds.get(index);
        }
        return null;
    }
    
    public List<String> getAllVehicles() {
        return new ArrayList<>(vehicleIds);
    }
    
    public int getCarCount() {
        return vehicleIds.size();
    }
    
    public boolean isEmpty() {
        return vehicleIds.isEmpty();
    }
    
    public boolean isFull() {
        return vehicleIds.size() >= 30;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name != null ? name : "";
    }
    
    public String getDisplayName() {
        return name.isEmpty() ? "Unnamed Formation" : name;
    }
    
    public TrainFormation copy() {
        return new TrainFormation(this.vehicleIds, this.name);
    }
}
