package com.portofino.realtrainmodunofficial.cargo;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 貨物パック ({@code ModelContainer_*.json} / {@code ModelFirearm_*.json}) の読み込み。
 *
 * <p>★走査はしない。{@code InstalledObjectPackLoader} が mod の jar・同梱パック・
 * 外部パックの json を<b>すべて</b>舐めているので、そこから該当ファイルだけ渡してもらう。
 * 走査を二重に書くとローダーごとの差 (ModList/FabricLoader) がもう一組増える。
 */
public final class CargoPackLoader {

    private static final List<CargoDefinition> LOADED = new ArrayList<>();

    /** 読み込みの開始。{@code InstalledObjectPackLoader.load()} の頭で呼ぶ。 */
    public static synchronized void begin() {
        LOADED.clear();
    }

    /** 読み込みの終了。索引へ流し込む。 */
    public static synchronized void end() {
        CargoRegistry.setDefinitions(LOADED);
        long container = LOADED.stream().filter(d -> d.getKind() == CargoDefinition.Kind.CONTAINER).count();
        long firearm = LOADED.size() - container;
        RealTrainModUnofficial.LOGGER.info("[RTMU] 貨物パック: コンテナ {} / 火砲 {}", container, firearm);
    }

    /**
     * このファイルが貨物パックなら読んで true。違うなら false。
     *
     * @param lowerFile 小文字にしたファイル名 (例 {@code modelcontainer_c20ft.json})
     */
    public static synchronized boolean tryParse(JsonObject obj, String lowerFile, String packName) {
        if (lowerFile.startsWith("modelcontainer_")) {
            LOADED.add(parseContainer(obj, lowerFile, packName));
            return true;
        }
        if (lowerFile.startsWith("modelfirearm_")) {
            LOADED.add(parseFirearm(obj, lowerFile, packName));
            return true;
        }
        return false;
    }

    private static CargoDefinition parseContainer(JsonObject obj, String lowerFile, String packName) {
        String name = firstNonBlank(getString(obj, "containerName"), stripName(lowerFile));
        Map<String, String> tex = new LinkedHashMap<>();
        String model = resolveModel(obj, "containerModel", "containerTexture", tex);
        return new CargoDefinition(
            name, name, packName, CargoDefinition.Kind.CONTAINER,
            model, tex, getString(obj, "buttonTexture"), getBoolean(obj, "doCulling", true),
            getFloat(obj, "containerWidth", 2.5F),
            getFloat(obj, "containerHeight", 2.5F),
            getFloat(obj, "containerLength", 6.0F),
            Vec3.ZERO, Vec3.ZERO, null, null, "");
    }

    private static CargoDefinition parseFirearm(JsonObject obj, String lowerFile, String packName) {
        String name = firstNonBlank(getString(obj, "firearmName"), stripName(lowerFile));
        Map<String, String> tex = new LinkedHashMap<>();
        String model = resolveModel(obj, "firearmModel", "firearmTexture", tex);
        CargoDefinition def = new CargoDefinition(
            name, name, packName, CargoDefinition.Kind.FIREARM,
            model, tex, getString(obj, "buttonTexture"), getBoolean(obj, "doCulling", true),
            0.0F, 0.0F, 0.0F,
            getVec(obj, "muzzlePos"), getVec(obj, "playerPos"),
            getFloatArray(obj, "yaw"), getFloatArray(obj, "pitch"),
            getString(obj, "bulletType"));
        def.setFirearmParts(getPartsPos(obj, "modelPartsN"), getPartsPos(obj, "modelPartsY"),
            getPartsPos(obj, "modelPartsX"), getPartsPos(obj, "modelPartsBarrel"),
            (int) getFloat(obj, "magazineSize", 1.0F));
        return def;
    }

    /**
     * モデルとテクスチャを取る。本家は<b>2 つの書式</b>がある:
     * <ul>
     *   <li>旧: {@code containerModel} / {@code containerTexture} が直に入る</li>
     *   <li>新: {@code model: { modelFile, textures: [[名前, パス, ""], ...] }}</li>
     * </ul>
     * ★新形式を見落とすと C20FT_NC / Phalanx / SeaRAM / 18MRL が読めない。
     */
    private static String resolveModel(JsonObject obj, String legacyModelKey, String legacyTextureKey,
                                       Map<String, String> textureOut) {
        JsonObject model = obj.has("model") && obj.get("model").isJsonObject()
            ? obj.getAsJsonObject("model") : null;
        if (model != null) {
            String file = getString(model, "modelFile");
            if (model.has("textures") && model.get("textures").isJsonArray()) {
                for (var element : model.getAsJsonArray("textures")) {
                    if (element.isJsonArray()) {
                        JsonArray pair = element.getAsJsonArray();
                        if (pair.size() >= 2) {
                            textureOut.put(pair.get(0).getAsString(), pair.get(1).getAsString());
                        }
                    }
                }
            }
            if (!file.isBlank()) {
                return file;
            }
        }
        String texture = getString(obj, legacyTextureKey);
        if (!texture.isBlank()) {
            textureOut.put("default", texture);
        }
        return getString(obj, legacyModelKey);
    }

    private static String stripName(String lowerFile) {
        String s = lowerFile;
        int under = s.indexOf('_');
        if (under >= 0) {
            s = s.substring(under + 1);
        }
        if (s.endsWith(".json")) {
            s = s.substring(0, s.length() - 5);
        }
        return s;
    }

    private static String getString(JsonObject obj, String key) {
        return obj.has(key) && obj.get(key).isJsonPrimitive() ? obj.get(key).getAsString() : "";
    }

    private static float getFloat(JsonObject obj, String key, float fallback) {
        try {
            return obj.has(key) && obj.get(key).isJsonPrimitive() ? obj.get(key).getAsFloat() : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    private static boolean getBoolean(JsonObject obj, String key, boolean fallback) {
        try {
            return obj.has(key) && obj.get(key).isJsonPrimitive() ? obj.get(key).getAsBoolean() : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    private static float[] getFloatArray(JsonObject obj, String key) {
        if (!obj.has(key) || !obj.get(key).isJsonArray()) {
            return null;
        }
        JsonArray array = obj.getAsJsonArray(key);
        float[] out = new float[array.size()];
        for (int i = 0; i < out.length; ++i) {
            try {
                out[i] = array.get(i).getAsFloat();
            } catch (Exception e) {
                out[i] = 0.0F;
            }
        }
        return out;
    }

    /** {@code modelPartsX: {objects:[...], pos:[x,y,z]}} の pos を取る。 */
    private static Vec3 getPartsPos(JsonObject obj, String key) {
        if (!obj.has(key) || !obj.get(key).isJsonObject()) {
            return Vec3.ZERO;
        }
        return getVec(obj.getAsJsonObject(key), "pos");
    }

    private static Vec3 getVec(JsonObject obj, String key) {
        float[] a = getFloatArray(obj, key);
        return (a == null || a.length < 3) ? Vec3.ZERO : new Vec3(a[0], a[1], a[2]);
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return "";
    }

    private CargoPackLoader() {
    }
}
