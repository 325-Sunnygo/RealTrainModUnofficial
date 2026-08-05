package com.portofino.realtrainmodunofficial.npc;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * NPC パックの索引と読み込み。
 * ★走査は {@code InstalledObjectPackLoader} に相乗りする (貨物と同じ理由)。
 */
public final class NpcRegistry {

    private static final Map<String, NpcDefinition> BY_ID = new LinkedHashMap<>();
    private static final List<NpcDefinition> LOADING = new ArrayList<>();

    public static synchronized void begin() {
        LOADING.clear();
    }

    public static synchronized void end() {
        BY_ID.clear();
        for (NpcDefinition def : LOADING) {
            BY_ID.put(def.getId(), def);
        }
        RealTrainModUnofficial.LOGGER.info("[RTMU] NPC パック: {} 件", BY_ID.size());
    }

    /** {@code ModelNPC_*.json} なら読んで true。 */
    public static synchronized boolean tryParse(JsonObject obj, String lowerFile, String packName) {
        if (!lowerFile.startsWith("modelnpc_")) {
            return false;
        }
        String name = str(obj, "name");
        if (name.isBlank()) {
            name = lowerFile.substring("modelnpc_".length()).replace(".json", "");
        }
        String modelFile = "";
        Map<String, String> tex = new LinkedHashMap<>();
        if (obj.has("model") && obj.get("model").isJsonObject()) {
            JsonObject model = obj.getAsJsonObject("model");
            modelFile = str(model, "modelFile");
            if (model.has("textures") && model.get("textures").isJsonArray()) {
                for (var element : model.getAsJsonArray("textures")) {
                    if (element.isJsonArray()) {
                        JsonArray pair = element.getAsJsonArray();
                        if (pair.size() >= 2) {
                            tex.put(pair.get(0).getAsString(), pair.get(1).getAsString());
                        }
                    }
                }
            }
        }
        LOADING.add(new NpcDefinition(name, packName, str(obj, "buttonTexture"), str(obj, "role"),
            str(obj, "texture"), modelFile, tex));
        return true;
    }

    private static String str(JsonObject obj, String key) {
        return obj.has(key) && obj.get(key).isJsonPrimitive() ? obj.get(key).getAsString() : "";
    }

    public static synchronized NpcDefinition getById(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        NpcDefinition def = BY_ID.get(id);
        if (def != null) {
            return def;
        }
        for (NpcDefinition candidate : BY_ID.values()) {
            if (candidate.getId().equalsIgnoreCase(id)) {
                return candidate;
            }
        }
        return null;
    }

    public static synchronized NpcDefinition getDefault() {
        return BY_ID.isEmpty() ? null : BY_ID.values().iterator().next();
    }

    public static synchronized List<NpcDefinition> getAll() {
        return new ArrayList<>(BY_ID.values());
    }

    private NpcRegistry() {
    }
}
