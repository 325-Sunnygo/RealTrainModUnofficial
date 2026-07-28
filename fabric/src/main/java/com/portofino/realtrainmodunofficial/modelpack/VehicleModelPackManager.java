package com.portofino.realtrainmodunofficial.modelpack;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import com.portofino.realtrainmodunofficial.util.PackTextDecoder;
import com.portofino.realtrainmodunofficial.vehicle.VehicleDefinition;
import com.portofino.realtrainmodunofficial.vehicle.VehicleRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * legacy-inspired ModelPackManager with advanced JSON processing and caching
 * Handles vehicle packs, textures, models, and configuration files
 */
public class VehicleModelPackManager implements ResourceManagerReloadListener {
    public static final VehicleModelPackManager INSTANCE = new VehicleModelPackManager();
    
    // legacy-style file patterns
    private static final Pattern VEHICLE_JSON_PATTERN = Pattern.compile("^vehicle_(.+)_config\\.json$");
    private static final Pattern SCRIPT_INCLUDE_PATTERN = Pattern.compile("//include <(.+)>");
    
    // Resource caching
    private final Map<String, ResourceConfig> resourceConfigs = new ConcurrentHashMap<>();
    private final Map<String, String> scriptCache = new ConcurrentHashMap<>();
    private final Map<String, ResourceLocation> resourceLocationCache = new ConcurrentHashMap<>();
    private final Map<String, JsonElement> jsonCache = new ConcurrentHashMap<>();
    private ResourceManager resourceManager;

    // Model pack management
    private final Map<String, ModelPack> loadedPacks = new ConcurrentHashMap<>();
    private final Set<String> activePacks = new HashSet<>();
    
    private boolean initialized = false;
    
    private VehicleModelPackManager() {}
    
    /**
     * Initialize the model pack manager
     */
    public void initialize(ResourceManager resourceManager) {
        if (initialized) return;
        
        
        try {
            this.resourceManager = resourceManager;
            // Scan for vehicle configuration files
            scanVehicleConfigs(resourceManager);
            
            // Load model packs
            loadModelPacks(resourceManager);
            
            // Validate and register resources
            validateAndRegisterResources();
            
            initialized = true;
            
        } catch (Exception e) {
            RealTrainModUnofficial.LOGGER.error("Failed to initialize legacy Model Pack Manager", e);
        }
    }
    
    /**
     * Scan for vehicle configuration files (legacy-style)
     */
    private void scanVehicleConfigs(ResourceManager resourceManager) {
        try {
            Map<ResourceLocation, Resource> resources = resourceManager.listResources(
                "vehicle_packs",
                path -> path.getPath().endsWith("_config.json")
            );

            for (Map.Entry<ResourceLocation, Resource> entry : resources.entrySet()) {
                try {
                    loadVehicleConfig(entry.getKey(), entry.getValue());
                } catch (Exception e) {
                    RealTrainModUnofficial.LOGGER.warn("Failed to load vehicle config: {}", entry.getKey(), e);
                }
            }
        } catch (Exception e) {
            RealTrainModUnofficial.LOGGER.warn("Failed to scan vehicle configs", e);
        }
    }
    
    /**
     * Load vehicle configuration from resource
     */
    private void loadVehicleConfig(ResourceLocation location, Resource resource) throws IOException {
        try (InputStream is = resource.open()) {
            String jsonContent = PackTextDecoder.decodeJson(is.readAllBytes());
            JsonElement root = JsonParser.parseString(jsonContent);
            
            if (!root.isJsonObject()) {
                throw new IOException("Invalid JSON format in vehicle config");
            }
            
            JsonObject configObj = root.getAsJsonObject();
            String packId = extractPackId(location);
            
            ResourceConfig config = parseResourceConfig(configObj, packId);
            resourceConfigs.put(packId, config);
            
        }
    }
    
    /**
     * Extract pack ID from resource location
     */
    private String extractPackId(ResourceLocation location) {
        String path = location.getPath();
        Matcher matcher = VEHICLE_JSON_PATTERN.matcher(path);
        if (matcher.find()) {
            return matcher.group(1);
        }
        
        // Fallback: use filename without extension
        int lastSlash = path.lastIndexOf('/');
        int lastDot = path.lastIndexOf('.');
        return path.substring(lastSlash + 1, lastDot);
    }
    
    /**
     * Parse resource configuration with legacy-style validation
     */
    private ResourceConfig parseResourceConfig(JsonObject configObj, String packId) {
        ResourceConfig config = new ResourceConfig();
        config.packId = packId;
        config.name = getString(configObj, "name", packId);
        config.version = getString(configObj, "version", "1.0.0");
        config.author = getString(configObj, "author", "Unknown");
        config.description = getString(configObj, "description", "");
        
        // Parse vehicle configurations
        if (configObj.has("vehicles") && configObj.get("vehicles").isJsonArray()) {
            config.vehicles = parseVehicleConfigs(configObj.getAsJsonArray("vehicles"));
        }
        
        // Parse model configurations
        if (configObj.has("models") && configObj.get("models").isJsonArray()) {
            config.models = parseModelConfigs(configObj.getAsJsonArray("models"));
        }
        
        // Parse texture configurations
        if (configObj.has("textures") && configObj.get("textures").isJsonArray()) {
            config.textures = parseTextureConfigs(configObj.getAsJsonArray("textures"));
        }
        
        // Parse dependencies
        if (configObj.has("dependencies") && configObj.get("dependencies").isJsonArray()) {
            config.dependencies = parseDependencies(configObj.getAsJsonArray("dependencies"));
        }
        
        // Parse script files
        if (configObj.has("scripts") && configObj.get("scripts").isJsonArray()) {
            config.scripts = parseScriptConfigs(configObj.getAsJsonArray("scripts"));
        }
        
        return config;
    }
    
    /**
     * Parse vehicle configurations array
     */
    private List<VehicleConfig> parseVehicleConfigs(com.google.gson.JsonArray vehiclesArray) {
        List<VehicleConfig> vehicles = new ArrayList<>();
        
        for (JsonElement element : vehiclesArray) {
            if (!element.isJsonObject()) continue;
            
            JsonObject vehicleObj = element.getAsJsonObject();
            VehicleConfig vehicle = new VehicleConfig();
            
            vehicle.id = getString(vehicleObj, "id", "");
            vehicle.name = getString(vehicleObj, "name", vehicle.id);
            vehicle.modelFile = getString(vehicleObj, "modelFile", "");
            vehicle.modelScale = parseFloat(vehicleObj, "scale", 1.0F);
            vehicle.modelOffset = parseVec3(vehicleObj, "offset", 1.0/16.0);
            
            // Parse legacy-style passenger seat positions
            vehicle.seatPositions = parselegacySeatPositions(vehicleObj);
            vehicle.playerPositions = parseLegacyPlayerPositions(vehicleObj);
            
            // Parse legacy-style bogie positions
            vehicle.bogiePositions = parselegacyBogiePositions(vehicleObj);
            
            // Parse bogie models
            vehicle.bogieModels = parselegacyBogieModels(vehicleObj);
            
            // Parse textures
            vehicle.textures = parselegacyTextures(vehicleObj);
            
            // Parse properties
            vehicle.trainDistance = parseFloat(vehicleObj, "trainDistance", 4.5F);
            vehicle.maxSpeed = parseFloat(vehicleObj, "maxSpeed", 20.0F);
            vehicle.weight = parseFloat(vehicleObj, "weight", 1000.0F);
            vehicle.power = parseFloat(vehicleObj, "power", 500.0F);
            
            // Parse legacy-specific properties
            vehicle.scriptPath = getString(vehicleObj, "rendererPath", getString(vehicleObj, "serverScriptPath", getString(vehicleObj, "soundScriptPath", getString(vehicleObj, "scriptPath", ""))));
            vehicle.doorType = getString(vehicleObj, "doorType", "manual");
            vehicle.lightType = getString(vehicleObj, "lightType", "standard");
            vehicle.soundType = getString(vehicleObj, "soundType", "default");
            
            vehicles.add(vehicle);
        }
        
        return vehicles;
    }
    
    /**
     * Parse legacy-style seat positions with multiple field support
     */
    private List<net.minecraft.world.phys.Vec3> parselegacySeatPositions(JsonObject obj) {
        List<net.minecraft.world.phys.Vec3> seats = new ArrayList<>();

        // legacy style: seatPos is often 1/16 units, seatPosF is in block units.
        appendVec3Array(obj, "seatPos", seats, 1.0 / 16.0);
        appendVec3Array(obj, "seatPosF", seats, 1.0);
        appendVec3Array(obj, "seatPositions", seats, 1.0);
        
        return seats;
    }

    private List<net.minecraft.world.phys.Vec3> parseLegacyPlayerPositions(JsonObject obj) {
        List<net.minecraft.world.phys.Vec3> players = new ArrayList<>();
        appendVec3Array(obj, "playerPos", players, 1.0);
        appendVec3Array(obj, "playerPosF", players, 1.0);
        return players;
    }
    
    /**
     * Parse legacy-style bogie positions
     */
    private List<net.minecraft.world.phys.Vec3> parselegacyBogiePositions(JsonObject obj) {
        List<net.minecraft.world.phys.Vec3> bogies = new ArrayList<>();

        // These fields are expected to be in block units in legacy packs.
        appendVec3Array(obj, "bogiePos", bogies, 1.0);
        appendVec3Array(obj, "bogiePositions", bogies, 1.0);
        appendVec3Array(obj, "truckPos", bogies, 1.0);
        appendVec3Array(obj, "truckPositions", bogies, 1.0);
        
        return bogies;
    }
    
    /**
     * Parse legacy-style bogie models
     */
    private List<BogieModelConfig> parselegacyBogieModels(JsonObject obj) {
        List<BogieModelConfig> bogieModels = new ArrayList<>();
        
        // Check for legacy-style bogieModel3 array
        if (obj.has("bogieModel3") && obj.get("bogieModel3").isJsonArray()) {
            com.google.gson.JsonArray array = obj.getAsJsonArray("bogieModel3");
            for (JsonElement element : array) {
                if (!element.isJsonObject()) continue;
                
                JsonObject bogieObj = element.getAsJsonObject();
                BogieModelConfig bogieConfig = new BogieModelConfig();
                bogieConfig.modelFile = getString(bogieObj, "modelFile", "");
                bogieConfig.textures = parselegacyTextures(bogieObj);
                bogieConfig.scale = parseFloat(bogieObj, "scale", 1.0F);
                bogieConfig.offset = parseVec3(bogieObj, "offset", 1.0/16.0);
                
                bogieModels.add(bogieConfig);
            }
        } else {
            // Single bogie model (bogieModel2 or bogieModel)
            JsonObject bogieModel = getObject(obj, "bogieModel2");
            if (bogieModel == null) bogieModel = getObject(obj, "bogieModel");
            
            if (bogieModel != null) {
                BogieModelConfig bogieConfig = new BogieModelConfig();
                bogieConfig.modelFile = getString(bogieModel, "modelFile", "");
                bogieConfig.textures = parselegacyTextures(bogieModel);
                bogieConfig.scale = parseFloat(bogieModel, "scale", 1.0F);
                bogieConfig.offset = parseVec3(bogieModel, "offset", 1.0/16.0);
                
                bogieModels.add(bogieConfig);
            }
        }
        
        return bogieModels;
    }
    
    /**
     * Parse legacy-style textures with array format
     */
    private Map<String, String> parselegacyTextures(JsonObject modelObj) {
        Map<String, String> textures = new HashMap<>();
        
        if (modelObj == null || !modelObj.has("textures") || !modelObj.get("textures").isJsonArray()) {
            return textures;
        }
        
        com.google.gson.JsonArray array = modelObj.getAsJsonArray("textures");
        for (JsonElement element : array) {
            if (!element.isJsonArray() || element.getAsJsonArray().size() < 2) continue;
            
            com.google.gson.JsonArray pair = element.getAsJsonArray();
            String material = pair.get(0).getAsString();
            String texture = pair.get(1).getAsString();
            
            if (!material.isBlank() && !texture.isBlank()) {
                textures.put(material, encodeTextureDescriptor(pair));
            }
        }
        
        return textures;
    }

    private String encodeTextureDescriptor(com.google.gson.JsonArray pair) {
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
    private void appendVec3Array(JsonObject obj, String key, List<net.minecraft.world.phys.Vec3> out, double scale) {
        if (!obj.has(key) || !obj.get(key).isJsonArray()) return;
        
        com.google.gson.JsonArray array = obj.getAsJsonArray(key);
        for (JsonElement element : array) {
            if (!element.isJsonArray() || element.getAsJsonArray().size() < 3) continue;
            
            com.google.gson.JsonArray vecArray = element.getAsJsonArray();
            try {
                double x = vecArray.get(0).getAsDouble();
                double y = vecArray.get(1).getAsDouble();
                double z = vecArray.get(2).getAsDouble();
                
                out.add(new net.minecraft.world.phys.Vec3(x * scale, y * scale, z * scale));
            } catch (Exception e) {
                RealTrainModUnofficial.LOGGER.warn("Failed to parse Vec3 from {}: {}", key, e.getMessage());
            }
        }
    }
    
    /**
     * Load model packs from resources
     */
    private void loadModelPacks(ResourceManager resourceManager) {
        for (String packId : resourceConfigs.keySet()) {
            ResourceConfig config = resourceConfigs.get(packId);
            
            try {
                ModelPack pack = new ModelPack(packId, config);
                loadedPacks.put(packId, pack);
                activePacks.add(packId);
                
            } catch (Exception e) {
                RealTrainModUnofficial.LOGGER.warn("Failed to load model pack: {}", packId, e);
            }
        }
    }
    
    /**
     * Validate and register all resources
     */
    private void validateAndRegisterResources() {
        for (ModelPack pack : loadedPacks.values()) {
            try {
                pack.validate();
                pack.registerVehicles();
            } catch (Exception e) {
                RealTrainModUnofficial.LOGGER.warn("Failed to validate/register pack: {}", pack.getPackId(), e);
                activePacks.remove(pack.getPackId());
            }
        }
    }
    
    /**
     * Get resource location with caching
     */
    public ResourceLocation getResource(String path) {
        return resourceLocationCache.computeIfAbsent(path, p -> {
            String domain = "minecraft";
            final String resourcePath = path;
            if (path.contains(":")) {
                String[] parts = path.split(":");
                domain = parts[0];
                return ResourceLocation.tryBuild(domain, parts[1]);
            }
            return ResourceLocation.tryBuild(domain, resourcePath);
        });
    }
    
    /**
     * Get script content with include processing
     */
    public String getScript(String fileName) throws IOException {
        return scriptCache.computeIfAbsent(fileName, this::loadScriptWithIncludes);
    }
    
    /**
     * Load script with include processing (legacy-style)
     */
    private String loadScriptWithIncludes(String fileName) {
        try {
            String rawScript = loadScriptFile(fileName);
            return processScriptIncludes(rawScript);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load script: " + fileName, e);
        }
    }
    
    /**
     * Process script includes recursively
     */
    private String processScriptIncludes(String rawScript) {
        Matcher matcher = SCRIPT_INCLUDE_PATTERN.matcher(rawScript);
        
        while (matcher.find()) {
            String includePath = matcher.group(1);
            try {
                String includedScript = getScript(includePath);
                rawScript = matcher.replaceFirst(Matcher.quoteReplacement(includedScript));
                matcher.reset(rawScript); // Reset matcher for new content
            } catch (Exception e) {
                RealTrainModUnofficial.LOGGER.warn("Failed to include script: {}", includePath, e);
                rawScript = matcher.replaceFirst(""); // Remove failed include
            }
        }

        return rawScript;
    }

    /**
     * Load script file
     */
    private String loadScriptFile(String fileName) throws IOException {
        if (resourceManager == null) {
            throw new IOException("legacy model pack resource manager is not initialized");
        }

        String normalized = fileName.replace('\\', '/');
        String fileNameOnly = normalized.contains("/") ? normalized.substring(normalized.lastIndexOf('/') + 1) : normalized;

        try {
            Map<ResourceLocation, Resource> scriptResources = resourceManager.listResources(
                "vehicle_packs",
                path -> {
                    String candidate = path.getPath();
                    return candidate.equals(normalized) || candidate.endsWith("/" + fileNameOnly) || candidate.endsWith("/" + normalized);
                }
            );
            for (Resource resource : scriptResources.values()) {
                try (InputStream is = resource.open()) {
                    return PackTextDecoder.readText(is);
                }
            }
        } catch (IOException ignored) {
            // If listResources fails for the current path, use direct resource lookup as fallback
        }

        throw new IOException("Script not found: " + fileName);
    }

    @Override
    public void onResourceManagerReload(ResourceManager resourceManager) {
        // Clear caches
        resourceConfigs.clear();
        scriptCache.clear();
        resourceLocationCache.clear();
        jsonCache.clear();
        loadedPacks.clear();
        activePacks.clear();

        // Reinitialization guard must be reset, otherwise initialize() returns early.
        initialized = false;

        // Reinitialize
        initialize(resourceManager);
    }
    
    // Helper methods
    private static JsonObject getObject(JsonObject obj, String key) {
        return obj.has(key) && obj.get(key).isJsonObject() ? obj.getAsJsonObject(key) : null;
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
    
    private static net.minecraft.world.phys.Vec3 parseVec3(JsonObject obj, String key, double scale) {
        if (obj == null || !obj.has(key) || !obj.get(key).isJsonArray()) return net.minecraft.world.phys.Vec3.ZERO;
        
        com.google.gson.JsonArray array = obj.getAsJsonArray(key);
        if (array.size() < 3) return net.minecraft.world.phys.Vec3.ZERO;
        
        try {
            return new net.minecraft.world.phys.Vec3(
                array.get(0).getAsDouble() * scale,
                array.get(1).getAsDouble() * scale,
                array.get(2).getAsDouble() * scale
            );
        } catch (Exception e) {
            RealTrainModUnofficial.LOGGER.warn("Failed to parse Vec3 from {}: {}", key, e.getMessage());
            return net.minecraft.world.phys.Vec3.ZERO;
        }
    }
    
    private static List<String> parseDependencies(com.google.gson.JsonArray array) {
        List<String> dependencies = new ArrayList<>();
        for (JsonElement element : array) {
            if (element.isJsonPrimitive()) {
                dependencies.add(element.getAsString());
            }
        }
        return dependencies;
    }
    
    private static List<String> parseScriptConfigs(com.google.gson.JsonArray array) {
        List<String> scripts = new ArrayList<>();
        for (JsonElement element : array) {
            if (element.isJsonPrimitive()) {
                scripts.add(element.getAsString());
            }
        }
        return scripts;
    }
    
    private static List<ModelConfig> parseModelConfigs(com.google.gson.JsonArray array) {
        List<ModelConfig> models = new ArrayList<>();
        // Implementation for model configs
        return models;
    }
    
    private static List<TextureConfig> parseTextureConfigs(com.google.gson.JsonArray array) {
        List<TextureConfig> textures = new ArrayList<>();
        // Implementation for texture configs
        return textures;
    }
    
    // Configuration classes
    public static class ResourceConfig {
        public String packId;
        public String name;
        public String version;
        public String author;
        public String description;
        public List<VehicleConfig> vehicles = new ArrayList<>();
        public List<ModelConfig> models = new ArrayList<>();
        public List<TextureConfig> textures = new ArrayList<>();
        public List<String> dependencies = new ArrayList<>();
        public List<String> scripts = new ArrayList<>();
    }
    
    public static class ModelConfig {
        public String id;
        public String modelFile;
        public String textureFile;
        public float scale = 1.0F;
        public net.minecraft.world.phys.Vec3 offset = net.minecraft.world.phys.Vec3.ZERO;
    }
    
    public static class TextureConfig {
        public String id;
        public String textureFile;
        public Map<String, String> overrides = new HashMap<>();
    }
    
    public static class ModelPack {
        private final String packId;
        private final ResourceConfig config;
        
        public ModelPack(String packId, ResourceConfig config) {
            this.packId = packId;
            this.config = config;
        }
        
        public void validate() throws IOException {
            if (config.vehicles.isEmpty()) {
                throw new IOException("Model pack has no vehicles: " + packId);
            }
            
            for (VehicleConfig vehicle : config.vehicles) {
                vehicle.validate();
            }
        }
        
        public void registerVehicles() {
            for (VehicleConfig vehicle : config.vehicles) {
                try {
                    // Convert legacy VehicleConfig to RealTrainModUnofficial VehicleDefinition
                    VehicleDefinition def = convertToVehicleDefinition(vehicle);
                    // Add to registry using existing method
                    synchronized (VehicleRegistry.class) {
                        List<VehicleDefinition> currentDefs = new ArrayList<>(VehicleRegistry.getAll());
                        currentDefs.add(def);
                        VehicleRegistry.setDefinitions(currentDefs);
                    }
                } catch (Exception e) {
                    RealTrainModUnofficial.LOGGER.warn("Failed to register vehicle: {}", vehicle.id, e);
                }
            }
        }
        
        private VehicleDefinition convertToVehicleDefinition(VehicleConfig vehicle) {
            // Convert legacy config to RealTrainModUnofficial format
            List<VehicleDefinition.BogieDefinition> bogieDefs = new ArrayList<>();
            
            // Add bogie models
            for (int i = 0; i < vehicle.bogieModels.size(); i++) {
                BogieModelConfig bogieModel = vehicle.bogieModels.get(i);
                net.minecraft.world.phys.Vec3 position = i < vehicle.bogiePositions.size() ? vehicle.bogiePositions.get(i) : net.minecraft.world.phys.Vec3.ZERO;
                bogieDefs.add(new VehicleDefinition.BogieDefinition(
                    bogieModel.modelFile, bogieModel.textures, position
                ));
            }
            
            String scriptPath = vehicle.scriptPath;
            if ((scriptPath == null || scriptPath.isBlank()) && !config.scripts.isEmpty()) {
                scriptPath = config.scripts.get(0);
            }

            int frontDriverSeatIndex = findExtremeSeatIndexByZ(vehicle.seatPositions, true);
            int rearDriverSeatIndex = findExtremeSeatIndexByZ(vehicle.seatPositions, false);

            // Create VehicleDefinition
            return new VehicleDefinition(
                vehicle.id,
                vehicle.name,
                "Legacy_Pack", // Default pack name
                vehicle.modelFile,
                "",
                vehicle.textures,
                vehicle.modelOffset,
                vehicle.modelScale,
                bogieDefs,
                vehicle.seatPositions,
                vehicle.playerPositions,
                !vehicle.playerPositions.isEmpty() ? vehicle.playerPositions.get(0) : (vehicle.seatPositions.isEmpty() ? null : vehicle.seatPositions.get(0)),
                scriptPath,
                "",
                "Train",
                vehicle.doorType,
                vehicle.trainDistance,
                0, // driverSeatIndex (legacyでは通常0番目が運転席)
                frontDriverSeatIndex,
                rearDriverSeatIndex,
                java.util.List.of(),
                java.util.List.of(),
                vehicle.maxSpeed > 0.0F ? java.util.List.of(vehicle.maxSpeed) : java.util.List.of(),
                0.00243F,
                false,
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of(),
                "",
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of(),
                "",
                java.util.List.of(),
                false,
                false,
                false,
                false
            );
        }

        private int findExtremeSeatIndexByZ(List<net.minecraft.world.phys.Vec3> seats, boolean front) {
            if (seats == null || seats.isEmpty()) {
                return 0;
            }

            int bestIndex = 0;
            double bestZ = seats.get(0).z;
            for (int i = 1; i < seats.size(); i++) {
                double z = seats.get(i).z;
                if (front ? z > bestZ : z < bestZ) {
                    bestZ = z;
                    bestIndex = i;
                }
            }
            return bestIndex;
        }
        
        public String getPackId() { return packId; }
        public ResourceConfig getConfig() { return config; }
    }
    
    public static class VehicleConfig {
        public String id;
        public String name;
        public String modelFile;
        public float modelScale = 1.0F;
        public net.minecraft.world.phys.Vec3 modelOffset = net.minecraft.world.phys.Vec3.ZERO;
        public Map<String, String> textures = new HashMap<>();
        public List<net.minecraft.world.phys.Vec3> seatPositions = new ArrayList<>();
        public List<net.minecraft.world.phys.Vec3> playerPositions = new ArrayList<>();
        public List<net.minecraft.world.phys.Vec3> bogiePositions = new ArrayList<>();
        public List<BogieModelConfig> bogieModels = new ArrayList<>();
        public String scriptPath = "";
        public float trainDistance = 4.5F;
        public float maxSpeed = 20.0F;
        public float weight = 1000.0F;
        public float power = 500.0F;
        public String doorType = "manual";
        public String lightType = "standard";
        public String soundType = "default";
        
        public void validate() throws IOException {
            if (modelFile == null || modelFile.isBlank()) {
                throw new IOException("Model file is required for vehicle: " + id);
            }
            
            if (seatPositions.isEmpty() && playerPositions.isEmpty() && bogiePositions.isEmpty()) {
                RealTrainModUnofficial.LOGGER.warn("Vehicle '{}' has no seats, driver positions, or bogies defined", id);
            }
        }
    }
    
    public static class BogieModelConfig {
        public String modelFile;
        public Map<String, String> textures = new HashMap<>();
        public float scale = 1.0F;
        public net.minecraft.world.phys.Vec3 offset = net.minecraft.world.phys.Vec3.ZERO;
    }
}
