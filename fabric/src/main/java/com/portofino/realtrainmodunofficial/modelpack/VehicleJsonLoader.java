package com.portofino.realtrainmodunofficial.modelpack;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * legacy/legacylib inspired JSON loader with enhanced parsing capabilities
 * Handles complex vehicle configurations with proper scaling and validation
 */
public class VehicleJsonLoader {
    private static final String[] ENCODINGS = {"UTF-8", "SJIS"};
    
    /**
     * Parse vehicle configuration from JSON with legacy-style validation
     */
    public static VehicleConfig parseVehicleConfig(Path jsonPath) throws IOException {
        String jsonContent = readFileWithEncoding(jsonPath);
        JsonElement root = JsonParser.parseString(jsonContent);
        
        if (!root.isJsonObject()) {
            throw new IOException("Invalid JSON format: root must be object");
        }
        
        JsonObject obj = root.getAsJsonObject();
        return parseVehicleConfig(obj, jsonPath.getFileName().toString());
    }
    
    /**
     * Parse vehicle configuration from JSON object
     */
    private static VehicleConfig parseVehicleConfig(JsonObject obj, String fileName) {
        VehicleConfig config = new VehicleConfig();
        
        // Basic vehicle info
        config.id = getString(obj, "trainName", "train");
        config.displayName = getString(obj, "displayName", config.id);
        
        // Model configuration
        JsonObject modelObj = getObject(obj, "trainModel2");
        if (modelObj == null) modelObj = getObject(obj, "trainModel");
        
        if (modelObj != null) {
            config.modelFile = getString(modelObj, "modelFile");
            config.modelScale = parseFloat(modelObj, "scale", 1.0F);
            config.modelOffset = parseVec3(modelObj, "offset", 1.0/16.0);
            config.scriptPath = getString(modelObj, "rendererPath");
            
            // Parse textures with legacy-style array format
            config.textures = parseTextures(modelObj);
            
            // Parse seat positions with enhanced validation
            config.seatPositions = parseSeatPositions(modelObj);
            config.seatPositions.addAll(parseSeatPositions(obj)); // Also check root level
            
            // Parse bogie positions
            config.bogiePositions = parseBogiePositions(modelObj);
            config.bogiePositions.addAll(parseBogiePositions(obj));
            
            // Parse bogie models (legacy-style)
            config.bogieModels = parseBogieModels(obj);
        }
        
        // Additional legacy-style properties
        config.trainDistance = parseFloat(modelObj, "trainDistance", parseFloat(obj, "trainDistance", 4.5F));
        config.maxSpeed = parseFloat(obj, "maxSpeed", 20.0F);
        config.weight = parseFloat(obj, "weight", 1000.0F);
        
        return config;
    }
    
    /**
     * Read file with multiple encoding attempts (legacy-style)
     */
    private static String readFileWithEncoding(Path path) throws IOException {
        for (String encoding : ENCODINGS) {
            try {
                return Files.readString(path, java.nio.charset.Charset.forName(encoding));
            } catch (IOException e) {
                // Try next encoding
            }
        }
        throw new IOException("Failed to read file with any supported encoding: " + path);
    }
    
    /**
     * Parse seat positions with legacy-style naming conventions
     */
    private static List<Vec3> parseSeatPositions(JsonObject obj) {
        List<Vec3> seats = new ArrayList<>();
        
        // legacy supports multiple seat position field names
        String[] seatFields = {"seatPos", "seatPosF", "playerPos", "playerPosF"};
        
        for (String field : seatFields) {
            appendVec3Array(obj, field, seats);
        }
        
        return seats;
    }
    
    /**
     * Parse bogie positions
     */
    private static List<Vec3> parseBogiePositions(JsonObject obj) {
        List<Vec3> bogies = new ArrayList<>();
        appendVec3Array(obj, "bogiePos", bogies);
        return bogies;
    }
    
    /**
     * Parse bogie model configurations (legacy-style)
     */
    private static List<BogieModelConfig> parseBogieModels(JsonObject obj) {
        List<BogieModelConfig> bogieModels = new ArrayList<>();
        
        if (obj.has("bogieModel3") && obj.get("bogieModel3").isJsonArray()) {
            var array = obj.getAsJsonArray("bogieModel3");
            for (int i = 0; i < array.size(); i++) {
                if (!array.get(i).isJsonObject()) continue;
                
                JsonObject bogieObj = array.get(i).getAsJsonObject();
                BogieModelConfig bogieConfig = new BogieModelConfig();
                bogieConfig.modelFile = getString(bogieObj, "modelFile");
                bogieConfig.textures = parseTextures(bogieObj);
                bogieConfig.scale = parseFloat(bogieObj, "scale", 1.0F);
                bogieConfig.offset = parseVec3(bogieObj, "offset", 1.0/16.0);
                
                bogieModels.add(bogieConfig);
            }
        } else {
            // Single bogie model
            JsonObject bogieModel = getObject(obj, "bogieModel2");
            if (bogieModel == null) bogieModel = getObject(obj, "bogieModel");
            
            if (bogieModel != null) {
                BogieModelConfig bogieConfig = new BogieModelConfig();
                bogieConfig.modelFile = getString(bogieModel, "modelFile");
                bogieConfig.textures = parseTextures(bogieModel);
                bogieConfig.scale = parseFloat(bogieModel, "scale", 1.0F);
                bogieConfig.offset = parseVec3(bogieModel, "offset", 1.0/16.0);
                
                bogieModels.add(bogieConfig);
            }
        }
        
        return bogieModels;
    }
    
    /**
     * Parse texture overrides with legacy-style array format
     */
    private static Map<String, String> parseTextures(JsonObject modelObj) {
        Map<String, String> textures = new HashMap<>();
        
        if (modelObj == null || !modelObj.has("textures") || !modelObj.get("textures").isJsonArray()) {
            return textures;
        }
        
        var array = modelObj.getAsJsonArray("textures");
        for (var element : array) {
            if (!element.isJsonArray() || element.getAsJsonArray().size() < 2) continue;
            
            var pair = element.getAsJsonArray();
            String material = pair.get(0).getAsString();
            String texture = pair.get(1).getAsString();
            
            if (!material.isBlank() && !texture.isBlank()) {
                textures.put(material, encodeTextureDescriptor(pair));
            }
        }
        
        return textures;
    }

    private static String encodeTextureDescriptor(JsonArray pair) {
        String texture = pair.get(1).getAsString();
        if (pair.size() < 3) {
            return texture;
        }
        List<String> flags = new ArrayList<>();
        for (int i = 2; i < pair.size(); i++) {
            JsonElement option = pair.get(i);
            if (option == null || !option.isJsonPrimitive()) {
                continue;
            }
            String value = option.getAsString();
            if (!value.isBlank()) {
                flags.add(value.trim());
            }
        }
        if (flags.isEmpty()) {
            return texture;
        }
        return texture + "|ptmeta=" + String.join(",", flags);
    }
    
    /**
     * Append Vec3 array from JSON object to list
     */
    private static void appendVec3Array(JsonObject obj, String key, List<Vec3> out) {
        if (!obj.has(key) || !obj.get(key).isJsonArray()) return;
        
        var array = obj.getAsJsonArray(key);
        for (var element : array) {
            if (!element.isJsonArray() || element.getAsJsonArray().size() < 3) continue;
            
            var vecArray = element.getAsJsonArray();
            try {
                double x = vecArray.get(0).getAsDouble();
                double y = vecArray.get(1).getAsDouble();
                double z = vecArray.get(2).getAsDouble();
                
                // Apply legacy-style scaling (1/16 block units)
                out.add(new Vec3(x / 16.0, y / 16.0, z / 16.0));
            } catch (Exception e) {
                RealTrainModUnofficial.LOGGER.warn("Failed to parse Vec3 from {}: {}", key, e.getMessage());
            }
        }
    }
    
    /**
     * Parse Vec3 with scaling
     */
    private static Vec3 parseVec3(JsonObject obj, String key, double scale) {
        if (obj == null || !obj.has(key) || !obj.get(key).isJsonArray()) return Vec3.ZERO;
        
        var array = obj.getAsJsonArray(key);
        if (array.size() < 3) return Vec3.ZERO;
        
        try {
            return new Vec3(
                array.get(0).getAsDouble() * scale,
                array.get(1).getAsDouble() * scale,
                array.get(2).getAsDouble() * scale
            );
        } catch (Exception e) {
            RealTrainModUnofficial.LOGGER.warn("Failed to parse Vec3 from {}: {}", key, e.getMessage());
            return Vec3.ZERO;
        }
    }
    
    // Helper methods
    private static JsonObject getObject(JsonObject obj, String key) {
        return obj.has(key) && obj.get(key).isJsonObject() ? obj.getAsJsonObject(key) : null;
    }
    
    private static String getString(JsonObject obj, String key) {
        return getString(obj, key, "");
    }
    
    private static String getString(JsonObject obj, String key, String defaultValue) {
        return obj.has(key) && obj.get(key).isJsonPrimitive() ? obj.get(key).getAsString() : defaultValue;
    }
    
    private static float parseFloat(JsonObject obj, String key, float defaultValue) {
        if (obj == null || !obj.has(key)) return defaultValue;
        try {
            return obj.get(key).getAsFloat();
        } catch (Exception e) {
            return defaultValue;
        }
    }
    
    /**
     * Enhanced vehicle configuration class based on legacy's structure
     */
    public static class VehicleConfig {
        public String id;
        public String displayName;
        public String modelFile;
        public float modelScale = 1.0F;
        public Vec3 modelOffset = Vec3.ZERO;
        public Map<String, String> textures = new HashMap<>();
        public List<Vec3> seatPositions = new ArrayList<>();
        public List<Vec3> bogiePositions = new ArrayList<>();
        public List<BogieModelConfig> bogieModels = new ArrayList<>();
        public String scriptPath;
        public float trainDistance = 4.5F;
        public float maxSpeed = 20.0F;
        public float weight = 1000.0F;
        
        public void validate() throws IOException {
            if (modelFile == null || modelFile.isBlank()) {
                throw new IOException("Model file is required");
            }
            
            if (seatPositions.isEmpty() && bogiePositions.isEmpty()) {
                RealTrainModUnofficial.LOGGER.warn("Vehicle '{}' has no seats or bogies defined", id);
            }
        }
    }
    
    /**
     * Bogie model configuration
     */
    public static class BogieModelConfig {
        public String modelFile;
        public Map<String, String> textures = new HashMap<>();
        public float scale = 1.0F;
        public Vec3 offset = Vec3.ZERO;
    }
}
