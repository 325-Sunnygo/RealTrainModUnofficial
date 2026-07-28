package org.webctc.tecon;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.MinecraftServer;
import org.webctc.WebCTCSavedData;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 本家 org.webctc.cache.tecon.TeConData の移植 (列車運行盤の保存)。
 * TeCon = {uuid, name, parts[]}。parts は図形 JSON (フロントと共有する形状) を
 * そのまま保持し、サーバーは TeConLever / Route だけ解釈する。
 */
public final class TeConData {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    /** uuid → TeCon (JsonObject: uuid/name/parts)。 */
    public static final Map<String, JsonObject> teConList = new LinkedHashMap<>();

    private static MinecraftServer server;

    private TeConData() {
    }

    public static void onServerStarted(MinecraftServer mcServer) {
        server = mcServer;
        load();
    }

    public static void onServerStopping() {
        server = null;
        teConList.clear();
        TeConRuntimeManager.clear();
    }

    private static void load() {
        teConList.clear();
        try {
            WebCTCSavedData data = WebCTCSavedData.get(server.overworld());
            JsonArray array = JsonParser.parseString(data.get("tecons")).getAsJsonArray();
            for (var el : array) {
                if (el.isJsonObject()) {
                    JsonObject teCon = el.getAsJsonObject();
                    if (teCon.has("uuid")) {
                        teConList.put(teCon.get("uuid").getAsString(), teCon);
                    }
                }
            }
        } catch (Exception e) {
            org.webctc.WebCTCCore.LOGGER.warn("Failed to load TeCon data", e);
        }
    }

    public static void markDirty() {
        if (server == null) {
            return;
        }
        JsonArray array = new JsonArray();
        teConList.values().forEach(array::add);
        WebCTCSavedData.get(server.overworld()).set("tecons", GSON.toJson(array));
    }

    public static JsonObject create() {
        JsonObject teCon = new JsonObject();
        teCon.addProperty("uuid", UUID.randomUUID().toString());
        teCon.addProperty("name", "Default Name");
        teCon.add("parts", new JsonArray());
        teConList.put(teCon.get("uuid").getAsString(), teCon);
        return teCon;
    }

    /** 本家 updateBy — name と parts のみ更新。 */
    public static void updateBy(JsonObject teCon, JsonObject updated) {
        if (updated.has("name")) {
            teCon.addProperty("name", updated.get("name").getAsString());
        }
        if (updated.has("parts") && updated.get("parts").isJsonArray()) {
            teCon.add("parts", updated.get("parts"));
        }
    }

    public static boolean delete(String uuid) {
        return teConList.remove(uuid) != null;
    }

    public static String listJson() {
        JsonArray array = new JsonArray();
        teConList.values().forEach(array::add);
        return GSON.toJson(array);
    }

    public static String toJson(JsonObject teCon) {
        return GSON.toJson(teCon);
    }
}
