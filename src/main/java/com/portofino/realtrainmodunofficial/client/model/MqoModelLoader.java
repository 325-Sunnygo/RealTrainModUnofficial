package com.portofino.realtrainmodunofficial.client.model;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.portofino.realtrainmodunofficial.client.render.VertexWriter;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.portofino.realtrainmodunofficial.Config;
import net.minecraft.client.renderer.GameRenderer;
import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import com.portofino.realtrainmodunofficial.blockentity.InstalledObjectBlockEntity;
import com.portofino.realtrainmodunofficial.blockentity.LargeRailCoreBlockEntity;
import com.portofino.realtrainmodunofficial.entity.TrainEntity;
import com.portofino.realtrainmodunofficial.rail.RailDefinition;
import com.portofino.realtrainmodunofficial.rail.RailPackLoader;
import com.portofino.realtrainmodunofficial.modelpack.VehicleModelPackManager;
import com.portofino.realtrainmodunofficial.script.TrainScriptSystem;
import com.portofino.realtrainmodunofficial.util.PackTextDecoder;
import com.portofino.realtrainmodunofficial.vehicle.VehicleDefinition;
import com.portofino.realtrainmodunofficial.vehicle.VehicleRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.io.BufferedInputStream;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.w3c.dom.Node;

/**
 * Metasequoia (.mqo) loader aligned with legacy model library {@code MqoModel}: 0.01 vertex scale, triangulation and quad handling.
 */
public final class MqoModelLoader {
    /** メッシュ捕獲モード。焼き込み中はVBO経路を使わず頂点をVertexConsumerへ流す。 */
    public static boolean captureMode;

    private static final float RTM_DEFAULT_SMOOTHING_ANGLE = 60.0F;

    /** 描画フレーム番号。pass1の対策をpass0とペアの時だけ適用する判定に使う。 */
    private static long renderFrame;

    /** 毎フレーム 1 回進める ({@code DeferredTranslucentRenderer} の AFTER_LEVEL から)。 */
    public static void advanceRenderFrame() {
        renderFrame++;
    }
    private static final String TEXTURE_META_SEPARATOR = "|ptmeta=";
    /** MQO マテリアルの col(r g b a)。4番目がアルファ(不透明度)。RTM はガラス等をこの a<1 で半透明にする。 */
    private static final Object MODEL_CACHE_LOCK = new Object();
    private static final LinkedHashMap<String, CachedModel> MODEL_CACHE = new LinkedHashMap<>(64, 0.75F, true);
    private static final Set<String> FAILED_MODEL_KEYS = ConcurrentHashMap.newKeySet();
    private static final Map<String, String> SOUND_SCRIPT_SOURCE_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, TextureInfo> TEXTURE_INFO_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, ScriptTextureData> SCRIPT_TEXTURE_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, ResourceSearchResult> RESOURCE_SEARCH_CACHE = new ConcurrentHashMap<>();
    private static final Set<String> SHADER_MOD_IDS = Set.of("iris", "oculus");
    private static volatile List<Path> sharedPackCandidates;
    private static ResourceLocation fallbackWhite;
    private static long modelCacheBytes;
    private static int bakedFilterLogCount = 0;
    private static volatile long shaderPipelineCacheUntilMillis;
    private static volatile boolean shaderPipelineCacheValue;
    private static final ResourceSearchResult MISSING_RESOURCE = new ResourceSearchResult(null, null, "__missing__");

    private static void logModelLoadDetail(String phase, String pattern, Object... args) {
    }

    private static Object[] prependArg(String first, Object[] rest) {
        Object[] merged = new Object[rest.length + 1];
        merged[0] = first;
        System.arraycopy(rest, 0, merged, 1, rest.length);
        return merged;
    }

    private MqoModelLoader() {
    }

    public static MqoModel loadModelForRail(RailDefinition def) {
        if (def == null) return null;
        String key = "r|" + def.getPackName() + "|" + def.getModelFile() + "|" + def.getTextureOverrides().hashCode();
        if (FAILED_MODEL_KEYS.contains(key)) {
            return null;
        }
        MqoModel cached = getCachedModel(key);
        if (cached != null) {
            return cached;
        }
        Path packPath = RailPackLoader.resolvePackPath(def.getPackName());
        MqoModel model = packPath == null ? null
            : loadInternal(packPath, def.getModelFile(), def.getTextureOverrides(), false);
        if (model == null) {
            // レールモデルが解決できないと描画されず道床(砂利)だけ生成され「レールが無い」
            // 状態になる (ユーザー報告)。標準レール ModelRail_1067mm.mqo を mod jar から
            // フォールバック読み込みし、必ず鉄レールが出るようにする。
            model = loadFallbackRailModel();
        }
        if (model != null) {
            if (packPath != null) loadScriptForModel(model, packPath, def.getScriptPath());
            cacheModel(key, model);
        } else {
            FAILED_MODEL_KEYS.add(key);
        }
        return model;
    }

    private static MqoModel fallbackRailModel;
    private static boolean fallbackRailAttempted;

    /** 標準 1067mm レールを mod jar から読み込むフォールバック。失敗しても null を返すだけ。 */
    private static MqoModel loadFallbackRailModel() {
        if (fallbackRailAttempted) return fallbackRailModel;
        fallbackRailAttempted = true;
        try {
            Path modJar = com.portofino.realtrainmodunofficial.BundledPackStore.getModJarPath();
            if (modJar != null) {
                fallbackRailModel = loadInternal(modJar, "ModelRail_1067mm.mqo",
                    java.util.Map.of("default", "textures/rail/largeRail.png"), false);
            }
        } catch (Throwable t) {
            RealTrainModUnofficial.LOGGER.warn("Failed to load fallback rail model", t);
        }
        return fallbackRailModel;
    }

    public static MqoModel loadModelForVehicle(VehicleDefinition def) {
        if (def == null) {
            RealTrainModUnofficial.LOGGER.warn("loadModelForVehicle: def is null");
            return null;
        }
        Path packPath = RailPackLoader.resolvePackPath(def.getPackName());
        if (packPath == null) {
            RealTrainModUnofficial.LOGGER.warn("loadModelForVehicle: packPath is null for pack {}", def.getPackName());
            return null;
        }
        String scriptPath = resolveVehicleRenderScriptPath(packPath, def);
        String soundScriptPath = def.getSoundScriptPath() != null ? def.getSoundScriptPath() : "";
        // legacy script は init() で trainName/modelName ごとの差分を固定するため、車両ID単位で分離する
        String key = "v|" + def.getId() + "|" + def.getPackName() + "|" + def.getModelFile() + "|" + def.getTextureOverrides().hashCode() + "|" + scriptPath.hashCode() + "|" + soundScriptPath.hashCode() + "|smooth";
        if (FAILED_MODEL_KEYS.contains(key)) {
            return null;
        }
        MqoModel cached = getCachedModel(key);
        if (cached != null) {
            return cached;
        }
        MqoModel model = loadInternal(packPath, def.getModelFile(), def.getTextureOverrides(), true, def.getModelScale());
        if (model != null) {
            attachScriptModelGraph(model, def.getModelFile());
            loadScriptForModel(model, packPath, scriptPath, def.getId());
            cacheModel(key, model);
        } else {
            RealTrainModUnofficial.LOGGER.warn("loadModelForVehicle: model is null");
            FAILED_MODEL_KEYS.add(key);
        }
        return model;
    }

    /** レガシースクリプトをロードしない版。二重処理を避ける。 */
    public static MqoModel loadModelForVehicleNoScript(VehicleDefinition def) {
        if (def == null) {
            return null;
        }
        Path packPath = RailPackLoader.resolvePackPath(def.getPackName());
        if (packPath == null) {
            return null;
        }
        String key = "vns|" + def.getId() + "|" + def.getPackName() + "|" + def.getModelFile() + "|" + def.getTextureOverrides().hashCode();
        if (FAILED_MODEL_KEYS.contains(key)) {
            return null;
        }
        MqoModel cached = getCachedModel(key);
        if (cached != null) {
            return cached;
        }
        MqoModel model = loadInternal(packPath, def.getModelFile(), def.getTextureOverrides(), true);
        if (model != null) {
            cacheModel(key, model);
        } else {
            FAILED_MODEL_KEYS.add(key);
        }
        return model;
    }

    private static String resolveVehicleRenderScriptPath(Path packPath, VehicleDefinition def) {
        if (def == null) {
            return "";
        }
        String explicit = normalizeScriptPath(def.getScriptPath());
        if (!explicit.isBlank()) {
            return explicit;
        }
        String inferred = inferVehicleRenderScriptPath(packPath, def.getId(), def.getModelFile());
        if (!inferred.isBlank()) {
            return inferred;
        }
        return "";
    }

    private static String inferVehicleRenderScriptPath(Path packPath, String vehicleId, String modelFile) {
        if (packPath == null || !Files.exists(packPath)) {
            return "";
        }
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        for (String token : List.of(vehicleId, modelFile)) {
            String family = inferVehicleScriptFamily(token);
            if (family.isBlank()) {
                continue;
            }
            candidates.add("assets/minecraft/scripts/render_" + family + ".js");
            candidates.add("scripts/render_" + family + ".js");
            candidates.add("assets/minecraft/scripts/" + family + ".js");
            candidates.add("scripts/" + family + ".js");
        }
        for (String candidate : candidates) {
            try {
                if (Files.isDirectory(packPath)) {
                    Path file = resolveFilePathInPack(packPath, candidate);
                    if (file != null && Files.exists(file)) {
                        return candidate;
                    }
                } else {
                    try (ZipFile zip = new ZipFile(packPath.toFile())) {
                        if (findEntry(zip, candidate) != null) {
                            return candidate;
                        }
                    }
                }
            } catch (IOException ignored) {
            }
        }
        return "";
    }

    private static String inferVehicleScriptFamily(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String base = raw.replace('\\', '/');
        int slash = base.lastIndexOf('/');
        if (slash >= 0) {
            base = base.substring(slash + 1);
        }
        int dot = base.lastIndexOf('.');
        if (dot > 0) {
            base = base.substring(0, dot);
        }
        base = base.toLowerCase(Locale.ROOT);
        if (base.startsWith("modeltrain_")) {
            base = base.substring("modeltrain_".length());
        }
        base = base.replaceFirst("_(?:mc|mcp\\d+|p\\d+)$", "");
        return base;
    }

    public static ScriptEngine loadServerScriptForVehicle(VehicleDefinition def) {
        if (def == null || !def.hasServerScript()) {
            return null;
        }
        Path packPath = RailPackLoader.resolvePackPath(def.getPackName());
        if (packPath == null) {
            return null;
        }
        String scriptPath = def.getServerScriptPath();
        String key = "server|" + def.getId() + "|" + def.getPackName() + "|" + scriptPath;
        String scriptSource = SOUND_SCRIPT_SOURCE_CACHE.computeIfAbsent(key, ignored -> {
            String loaded = loadStandaloneScriptSource(packPath, scriptPath);
            return loaded == null ? "" : loaded;
        });
        if (scriptSource == null || scriptSource.isBlank()) {
            return null;
        }
        return TrainScriptSystem.loadStandaloneScript(scriptPath, scriptSource, def.getId());
    }

    public static ScriptEngine loadSoundScriptForVehicle(VehicleDefinition def) {
        if (def == null || !def.hasSoundScript()) {
            return null;
        }
        Path packPath = RailPackLoader.resolvePackPath(def.getPackName());
        if (packPath == null) {
            return null;
        }
        String scriptPath = def.getSoundScriptPath();
        String key = "sound|" + def.getId() + "|" + def.getPackName() + "|" + scriptPath;
        String scriptSource = SOUND_SCRIPT_SOURCE_CACHE.computeIfAbsent(key, ignored -> {
            String loaded = loadStandaloneScriptSource(packPath, scriptPath);
            return loaded == null ? "" : loaded;
        });
        if (scriptSource == null || scriptSource.isBlank()) {
            return null;
        }
        return TrainScriptSystem.loadStandaloneScript(scriptPath, scriptSource, def.getId());
    }

    public static MqoModel loadModelForVehiclePart(VehicleDefinition def, String modelFile, Map<String, String> textureOverrides) {
        return loadModelForVehiclePart(def, modelFile, textureOverrides, "");
    }

    public static MqoModel loadModelForVehiclePart(VehicleDefinition def, String modelFile, Map<String, String> textureOverrides, String scriptPath) {
        if (def == null || modelFile == null || modelFile.isBlank()) return null;
        Map<String, String> tex = textureOverrides == null ? Map.of() : textureOverrides;
        String script = scriptPath == null ? "" : scriptPath;
        String key = "vp|" + def.getPackName() + "|" + modelFile + "|" + tex.hashCode() + "|smooth|" + script.hashCode();
        MqoModel cached = getCachedModel(key);
        if (cached != null) {
            return cached;
        }
        Path packPath = RailPackLoader.resolvePackPath(def.getPackName());
        MqoModel model = loadInternal(packPath, modelFile, tex, true);
        if (model != null) {
            if (!script.isBlank()) {
                loadScriptForModel(model, packPath, script, def.getId());
            }
            cacheModel(key, model);
        }
        return model;
    }

    /**
     * 本家の Java 実装モデル ({@code ModelBogie.class} 等) を、MQO と同じバッチ表現へ組み立てる。
     * <p>頂点は {@link ClassModelGeometry} がバニラの {@code ModelPart} に焼かせた物をそのまま使う。
     * 材質は 1 つ (テクスチャ 1 枚) だけなのでバッチも 1 つ。
     */
    /**
     * 本家の {@code .ngto} (ボクセル) モデルをバッチ表現へ組み立てる。
     * <p>テクスチャはブロックアトラス 1 枚。ガラス等は別バッチ (半透明) に分ける。
     */
    private static MqoModel buildNgtoModel(ResourceSearchResult resource, String modelFile, float voxelScale) {
        try {
            List<NgtoModelGeometry.Part> parts = NgtoModelGeometry.buildParts(readBytes(resource), modelFile, voxelScale);
            if (parts.isEmpty()) {
                RealTrainModUnofficial.LOGGER.warn("NGTO produced no geometry: {}", modelFile);
                return null;
            }
            ResourceLocation atlas = net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS;
            List<Batch> batches = new ArrayList<>();
            for (NgtoModelGeometry.Part part : parts) {
                //.ngtz はパーツ名でスクリプトから部分描画される。グループ名に使う。
                addNgtoBatch(batches, part.opaque(), atlas, part.name(), false);
                addNgtoBatch(batches, part.translucent(), atlas, part.name(), true);
            }
            if (batches.isEmpty()) {
                return null;
            }
            MqoModel built = new MqoModel(batches, List.of(atlas));
            built.voxelModel = true;
            return built;
        } catch (Exception e) {
            RealTrainModUnofficial.LOGGER.warn("Failed to load NGTO {}", modelFile, e);
            return null;
        }
    }

    private static void addNgtoBatch(List<Batch> out, float[] data, ResourceLocation texture,
                                     String groupName, boolean translucent) {
        if (data == null || data.length == 0) {
            return;
        }
        float minU = Float.POSITIVE_INFINITY;
        float maxU = Float.NEGATIVE_INFINITY;
        float minV = Float.POSITIVE_INFINITY;
        float maxV = Float.NEGATIVE_INFINITY;
        for (int i = 0; i + NgtoModelGeometry.STRIDE <= data.length; i += NgtoModelGeometry.STRIDE) {
            minU = Math.min(minU, data[i + 6]);
            maxU = Math.max(maxU, data[i + 6]);
            minV = Math.min(minV, data[i + 7]);
            maxV = Math.max(maxV, data[i + 7]);
        }
        out.add(new Batch(out.size(), groupName, texture, new ResourceLocation[0], data,
            data.length / NgtoModelGeometry.STRIDE, 0, translucent, minU, maxU, minV, maxV));
    }

    private static byte[] readBytes(ResourceSearchResult resource) throws IOException {
        if (resource.filePath() != null) {
            return Files.readAllBytes(resource.filePath());
        }
        try (ZipFile zip = new ZipFile(resource.packPath().toFile())) {
            ZipEntry entry = zip.getEntry(resource.zipEntryName());
            if (entry == null) {
                throw new IOException("Missing zip entry: " + resource.zipEntryName());
            }
            try (InputStream in = zip.getInputStream(entry)) {
                return in.readAllBytes();
            }
        }
    }

    private static MqoModel buildClassModel(Path packPath, String modelFile, Map<String, String> textureOverrides) {
        float[] data = ClassModelGeometry.build(modelFile);
        if (data == null || data.length == 0) {
            RealTrainModUnofficial.LOGGER.warn("Built-in class model produced no geometry: {}", modelFile);
            return null;
        }
        String texturePath = null;
        if (textureOverrides != null) {
            texturePath = firstNonBlankValue(textureOverrides.get("default"), textureOverrides.get(""));
            if (texturePath == null) {
                for (String value : textureOverrides.values()) {
                    if (value != null && !value.isBlank()) {
                        texturePath = value;
                        break;
                    }
                }
            }
        }
        if (texturePath == null || texturePath.isBlank()) {
            texturePath = ClassModelGeometry.defaultTexture(modelFile);
        }
        ResourceLocation texture = resolveClassModelTexture(packPath, texturePath);
        float minU = Float.POSITIVE_INFINITY;
        float maxU = Float.NEGATIVE_INFINITY;
        float minV = Float.POSITIVE_INFINITY;
        float maxV = Float.NEGATIVE_INFINITY;
        for (int i = 0; i + ClassModelGeometry.STRIDE <= data.length; i += ClassModelGeometry.STRIDE) {
            minU = Math.min(minU, data[i + 6]);
            maxU = Math.max(maxU, data[i + 6]);
            minV = Math.min(minV, data[i + 7]);
            maxV = Math.max(maxV, data[i + 7]);
        }
        Batch batch = new Batch(0, ClassModelGeometry.groupName(modelFile), texture, new ResourceLocation[0],
            data, data.length / ClassModelGeometry.STRIDE, 0, false, minU, maxU, minV, maxV);
        return new MqoModel(List.of(batch), List.of(texture));
    }

    private static String firstNonBlankValue(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return b != null && !b.isBlank() ? b : null;
    }

    /** {@code minecraft:textures/entity/minecart.png} のような名前空間付きも受ける。 */
    private static ResourceLocation resolveClassModelTexture(Path packPath, String texturePath) {
        if (texturePath == null || texturePath.isBlank()) {
            return fallbackTexture();
        }
        if (texturePath.indexOf(':') >= 0) {
            ResourceLocation direct = ResourceLocation.tryParse(texturePath);
            if (direct != null) {
                return direct;
            }
        }
        ResourceLocation resolved = resolvePackTexture(packPath, texturePath);
        if (resolved != null && !resolved.equals(fallbackTexture())) {
            return resolved;
        }
        //パックに無いならバニラのリソースとして引く。minecart は
        //"textures/entity/minecart.png" = バニラのテクスチャで、パック内には存在しない。
        //ここを通さないと 4x4 の白テクスチャになる (真っ白な面として出る)。
        ResourceLocation vanilla = ResourceLocation.tryParse("minecraft:" + texturePath);
        if (vanilla != null && Minecraft.getInstance() != null
                && Minecraft.getInstance().getResourceManager().getResource(vanilla).isPresent()) {
            return vanilla;
        }
        return resolved != null ? resolved : fallbackTexture();
    }

    public static ResourceLocation resolvePackTexture(String packName, String texturePath) {
        if (packName == null || packName.isBlank() || texturePath == null || texturePath.isBlank()) {
            return fallbackTexture();
        }
        Path packPath = RailPackLoader.resolvePackPath(packName);
        if (packPath == null) {
            return fallbackTexture();
        }
        return resolvePackTexture(packPath, texturePath);
    }

    /** パス直指定版。パック名を持たない経路 ({@link #loadInternal}) から使う。 */
    private static ResourceLocation resolvePackTexture(Path packPath, String texturePath) {
        if (packPath == null || texturePath == null || texturePath.isBlank()) {
            return fallbackTexture();
        }
        //RTMU 追加: アニメーション GIF を方向幕/種別幕などに直接使えるようにする。
        //拡張子 .gif は ImageIO でフレーム分解し、毎 tick 貼り替わる DynamicTexture を返す。
        if (texturePath.toLowerCase(Locale.ROOT).endsWith(".gif")) {
            final String gifRel = texturePath;
            ResourceLocation gif = GifTextures.resolve(packPath + "|gif|" + gifRel,
                    () -> openTexture(packPath, gifRel));
            if (gif != null) {
                return gif;
            }
            //デコード失敗時のみ下の静止経路へフォールバック
        }
        TextureBinding binding = TextureBinding.parse(texturePath);
        String cacheKey = packPath + "|" + binding.cacheKey();
        TextureInfo info = TEXTURE_INFO_CACHE.computeIfAbsent(cacheKey, key -> registerTextureFromZip(binding, new TextureOpener() {
            @Override
            public InputStream open(String rel) throws Exception {
                return openTexture(packPath, rel);
            }

            @Override
            public String getPackKey() {
                return packPath.toString();
            }
        }));
        return info.location;
    }

    public static MqoModel loadModelFromPack(String packName, String modelFile, Map<String, String> textureOverrides,
                                             String scriptPath, boolean smoothing) {
        if (packName == null || modelFile == null || modelFile.isBlank()) {
            return null;
        }
        Map<String, String> tex = textureOverrides == null ? Map.of() : textureOverrides;
        String key = "p|" + packName + "|" + modelFile + "|" + tex.hashCode() + "|" + smoothing + "|" + (scriptPath == null ? 0 : scriptPath.hashCode());
        if (FAILED_MODEL_KEYS.contains(key)) {
            return null;
        }
        MqoModel cached = getCachedModel(key);
        if (cached != null) {
            return cached;
        }
        Path packPath = RailPackLoader.resolvePackPath(packName);
        if (packPath == null) {
            FAILED_MODEL_KEYS.add(key);
            return null;
        }
        MqoModel model = loadInternal(packPath, modelFile, tex, smoothing);
        if (model != null) {
            loadScriptForModel(model, packPath, scriptPath);
            cacheModel(key, model);
        } else {
            FAILED_MODEL_KEYS.add(key);
        }
        return model;
    }

    /**
     * スクリプトへ渡すモデルへ、本家 {@code ModelObject.model} 相当のグラフを載せる。
     * <p>解決は {@code VehicleScriptRenderers.buildModelObject} と同じ全パック横断の索引。
     * 失敗しても描画自体は続くので、警告だけ出して黙って進む。
     */
    private static void attachScriptModelGraph(MqoModel model, String modelFile) {
        if (model == null || modelFile == null || modelFile.isBlank()) {
            return;
        }
        ScriptModel sm = model.getScriptModel();
        if (sm == null || sm.model != null) {
            return;
        }
        try {
            byte[] bytes = jp.ngt.ngtlib.io.NGTFileLoader.findAsset("models/" + modelFile);
            if (bytes == null) {
                bytes = jp.ngt.ngtlib.io.NGTFileLoader.findAsset(modelFile);
            }
            if (bytes != null) {
                sm.model = jp.ngt.ngtlib.renderer.model.ModelLoader.parse(bytes, modelFile);
            }
        } catch (Exception e) {
            RealTrainModUnofficial.LOGGER.warn("[RTMU] モデルグラフを作れませんでした ({}): {}", modelFile, e.toString());
        }
    }

    private static ResourceLocation firstNonNull(ResourceLocation a, ResourceLocation b) {
        return a != null ? a : b;
    }

    private static MqoModel loadInternal(Path packPath, String modelFile, Map<String, String> textureOverrides, boolean smoothing) {
        return loadInternal(packPath, modelFile, textureOverrides, smoothing, 1.0F);
    }

    /**
     * @param voxelScale ボクセルモデル (.ngto/.ngtz) の縮尺。本家 {@code NGTOParts.render} は
     *                   {@code glScalef(scale)} を<b>パーツの形状の内側だけ</b>に掛ける。
     *                   描画の最上位で掛けると、スクリプトが台車/ドアを置くための
     *                   {@code glTranslatef} まで縮んで<b>全部が車両中央に寄る</b>。
     *                   なのでここで形状へ焼き込み、描画側では掛けない。
     */
    private static MqoModel loadInternal(Path packPath, String modelFile, Map<String, String> textureOverrides,
                                         boolean smoothing, float voxelScale) {
        //本家 Java 実装モデル (ModelBogie.class / ModelTrain_kiha600.class / ModelTrain_Minecart.class)。
        //ファイルとして存在しないので、パックを探す前にここで組み立てる。
        //★車体・台車・プレビューで入口が違う (loadModelForVehicle / loadModelForVehiclePart /
        //  loadModelFromPack) ので、<b>共通の底</b>であるここで受ける。入口ごとに足すと
        //  必ず取りこぼす (実際、台車だけ出て車体が出ない状態になった)。
        if (ClassModelGeometry.isSupported(modelFile)) {
            return buildClassModel(packPath, modelFile, textureOverrides);
        }
        if (packPath == null || !Files.exists(packPath)) return null;
        logModelLoadDetail("begin", "packPath={}, modelFile={}, smoothing={}, textureOverrides={}", packPath, modelFile, smoothing, textureOverrides);
        try {
            if (Files.isDirectory(packPath)) {
                ResourceSearchResult modelResource = findResource(modelFile, packPath);
                if (modelResource == null) {
                    RealTrainModUnofficial.LOGGER.warn("MQO not found in pack {}: {}", packPath.getFileName(), modelFile);
                    return null;
                }
                Path modelPackPath = modelResource.packPath();
                if (modelPackPath == null) {
                    RealTrainModUnofficial.LOGGER.warn("Resolved MQO had no source pack for {} from {}", modelFile, com.portofino.realtrainmodunofficial.util.LogPaths.safe(packPath));
                    return null;
                }
                logModelLoadDetail("resolved", "modelFile={} resolvedPack={} filePath={} zipEntry={}", modelFile, modelPackPath, modelResource.filePath(), modelResource.zipEntryName());
                String lowerModelFile = modelFile.toLowerCase(Locale.ROOT);
                TextureOpener opener = new TextureOpener() {
                    @Override
                    public InputStream open(String rel) throws Exception {
                        return openTexture(modelPackPath, rel);
                    }
                    @Override
                    public String getPackKey() {
                        return modelPackPath.toString();
                    }
                };
                if (lowerModelFile.endsWith(".ngto") || lowerModelFile.endsWith(".ngtz")) {
                    //本家の .ngto/.ngtz はボクセル。ブロックの焼き済みモデルから面を組む。
                    return buildNgtoModel(modelResource, modelFile, voxelScale);
                }
                if (lowerModelFile.endsWith(".obj")) {
                    return bakeObj(readText(modelResource), opener, textureOverrides, smoothing);
                }
                String text = lowerModelFile.endsWith(".mqoz")
                    ? readCompressedMqo(modelResource)
                    : readText(modelResource);
                return bake(text, opener, textureOverrides, smoothing);
            }
            try (ZipFile zf = new ZipFile(packPath.toFile())) {
                ResourceSearchResult modelResource = findResource(modelFile, packPath);
                if (modelResource == null) {
                    RealTrainModUnofficial.LOGGER.warn("MQO not found in pack {}: {}", packPath.getFileName(), modelFile);
                    return null;
                }
                Path modelPackPath = modelResource.packPath();
                if (modelPackPath == null) {
                    RealTrainModUnofficial.LOGGER.warn("Resolved MQO had no source pack for {} from {}", modelFile, com.portofino.realtrainmodunofficial.util.LogPaths.safe(packPath));
                    return null;
                }
                logModelLoadDetail("resolved", "modelFile={} resolvedPack={} filePath={} zipEntry={}", modelFile, modelPackPath, modelResource.filePath(), modelResource.zipEntryName());
                String lowerModelFile = modelFile.toLowerCase(Locale.ROOT);
                TextureOpener opener = new TextureOpener() {
                    @Override
                    public InputStream open(String rel) throws Exception {
                        return openTexture(modelPackPath, rel);
                    }
                    @Override
                    public String getPackKey() {
                        return modelPackPath.toString();
                    }
                };
                if (lowerModelFile.endsWith(".ngto") || lowerModelFile.endsWith(".ngtz")) {
                    //本家の .ngto/.ngtz はボクセル。ブロックの焼き済みモデルから面を組む。
                    return buildNgtoModel(modelResource, modelFile, voxelScale);
                }
                if (lowerModelFile.endsWith(".obj")) {
                    return bakeObj(readText(modelResource), opener, textureOverrides, smoothing);
                }
                String text = lowerModelFile.endsWith(".mqoz")
                    ? readCompressedMqo(modelResource)
                    : readText(modelResource);
                return bake(text, opener, textureOverrides, smoothing);
            }
        } catch (Exception e) {
            RealTrainModUnofficial.LOGGER.warn("Failed to load MQO {} from {}", modelFile, com.portofino.realtrainmodunofficial.util.LogPaths.safe(packPath), e);
            return null;
        }
    }

    /** 別パック解決のログを 1 組合せ 1 回だけ出すための記録。 */
    private static final java.util.Set<String> CROSS_PACK_LOGGED =
        java.util.concurrent.ConcurrentHashMap.newKeySet();

    private static InputStream openTexture(Path packPath, String relative) throws IOException {
        if (packPath == null) {
            return null;
        }
        if (Files.isDirectory(packPath)) {
            Path file = resolveFilePathInPack(packPath, relative);
            if (file != null) {
                return Files.newInputStream(file);
            }
        } else {
            ZipFile zip = new ZipFile(packPath.toFile());
            ZipEntry entry = findEntry(zip, relative);
            if (entry != null) {
                InputStream raw = zip.getInputStream(entry);
                return new java.io.FilterInputStream(raw) {
                    @Override
                    public void close() throws IOException {
                        super.close();
                        zip.close();
                    }
                };
            }
            zip.close();
        }
        ResourceSearchResult fallback = findResource(relative, packPath);
        if (fallback == null || packPath.equals(fallback.packPath())) {
            return null;
        }
        //別パックから引いた時だけ 1 回記録する。分割パック (定義とテクスチャが別 zip) で
        //「どのパックの絵が実際に使われたか」を後から確認できるようにする。
        //同名ファイルを多数持つパックの取り違えは、これを見ないと切り分けられない。
        if (CROSS_PACK_LOGGED.add(packPath + "|" + relative)) {
            RealTrainModUnofficial.LOGGER.info("[RTMU] テクスチャ/資産を別パックから解決: {} → {} ({})",
                relative,
                com.portofino.realtrainmodunofficial.util.LogPaths.safe(fallback.packPath()),
                fallback.zipEntryName() != null ? fallback.zipEntryName() : fallback.filePath());
        }
        return openResource(fallback);
    }

    private static String readCompressedMqo(Path path) throws java.io.IOException {
        try (ZipFile zf = new ZipFile(path.toFile())) {
            for (ZipEntry entry : java.util.Collections.list(zf.entries())) {
                if (!entry.isDirectory() && entry.getName().toLowerCase(Locale.ROOT).endsWith(".mqo")) {
                    try (InputStream in = zf.getInputStream(entry)) {
                        return PackTextDecoder.readText(in);
                    }
                }
            }
        }
        throw new java.io.IOException("No .mqo entry found inside compressed MQO: " + path);
    }

    private static String readCompressedMqo(InputStream input) throws java.io.IOException {
        try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(new BufferedInputStream(input))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.isDirectory() && entry.getName().toLowerCase(Locale.ROOT).endsWith(".mqo")) {
                    return PackTextDecoder.readText(zis);
                }
            }
        }
        throw new java.io.IOException("No .mqo entry found inside compressed MQO stream");
    }

    private static MqoModel getCachedModel(String key) {
        synchronized (MODEL_CACHE_LOCK) {
            CachedModel cached = MODEL_CACHE.get(key);
            if (cached == null) {
                return null;
            }
            cached.touch(System.nanoTime());
            return cached.model();
        }
    }

    private static void cacheModel(String key, MqoModel model) {
        if (key == null || model == null) {
            return;
        }
        synchronized (MODEL_CACHE_LOCK) {
            CachedModel previous = MODEL_CACHE.remove(key);
            if (previous != null) {
                modelCacheBytes -= previous.estimatedBytes();
                //古いモデルを差し替える時は GPU VBO を解放 (リーク防止)。ただし同じ
                //オブジェクトを再登録する場合は、今から使う VBO を閉じないようスキップ。
                if (previous.model != model) {
                    previous.model.closeGpuResources();
                }
            }
            CachedModel cached = new CachedModel(model, model.estimateMemoryBytes(), System.nanoTime());
            MODEL_CACHE.put(key, cached);
            modelCacheBytes += cached.estimatedBytes();
            evictModelCacheLocked();
        }
    }

    private static void evictModelCacheLocked() {
        long limitBytes = Math.max(1024L, Config.MODEL_CACHE_LIMIT_MIB.get()) * 1024L * 1024L;
        long protectNanos = Math.max(300L, Config.MODEL_CACHE_PROTECT_SECONDS.get()) * 1_000_000_000L;
        if (modelCacheBytes <= limitBytes) {
            return;
        }
        long now = System.nanoTime();
        Iterator<Map.Entry<String, CachedModel>> iterator = MODEL_CACHE.entrySet().iterator();
        while (modelCacheBytes > limitBytes && iterator.hasNext()) {
            Map.Entry<String, CachedModel> entry = iterator.next();
            CachedModel cached = entry.getValue();
            if (protectNanos > 0L && now - cached.lastAccessNanos() < protectNanos) {
                continue;
            }
            modelCacheBytes -= cached.estimatedBytes();
            iterator.remove();
            //追い出したモデルの GPU VBO を解放する。これを怠ると VertexBuffer が native の
            //まま残り、巡回中に GPU メモリがリークして徐々に重くなっていた (今回の主因)。
            cached.model.closeGpuResources();
        }
    }

    private static Path resolveFilePathInPack(Path root, String relative) throws java.io.IOException {
        if (relative == null) return null;
        String norm = relative.replace('\\', '/');
        for (String candidatePath : candidateResourcePaths(norm)) {
            Path candidate = root.resolve(candidatePath);
            if (Files.exists(candidate) && Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        //★zip 版 (findEntry) と同じく<b>ファイル名だけの一致は取らない</b>。
        //assets/<domain>/ を基準にした完全パス (サフィックス) 一致のみ。
        String suffix = ("/" + norm).toLowerCase(Locale.ROOT);
        try (var stream = Files.walk(root)) {
            for (Path file : (Iterable<Path>) stream::iterator) {
                if (!Files.isRegularFile(file)) continue;
                String rel = root.relativize(file).toString().replace('\\', '/').toLowerCase(Locale.ROOT);
                if (rel.equals(norm.toLowerCase(Locale.ROOT)) || ("/" + rel).endsWith(suffix)) {
                    return file;
                }
            }
        }
        return null;
    }

    private static String normalizeScriptPath(String scriptPath) {
        if (scriptPath == null || scriptPath.isBlank()) {
            return "";
        }
        return scriptPath.replace('\\', '/').replaceFirst("^/+", "");
    }

    private static ZipEntry findEntry(ZipFile zf, String relative) {
        if (relative == null) return null;
        String norm = relative.replace('\\', '/');
        for (String candidatePath : candidateResourcePaths(norm)) {
            ZipEntry direct = zf.getEntry(candidatePath);
            if (direct != null && !direct.isDirectory()) {
                return direct;
            }
        }
        //★<b>ファイル名だけの一致は取らない</b>。本家 ModelPackManager.getResource は
        //assets/<domain>/<path> の完全一致でしか引かない。
        //以前は「そのパック内に同名が 1 個しか無ければ採用」としていたが、パックが複数ある
        //環境では<b>正しいパックを見る前に別パックの同名ファイルで確定</b>してしまう。
        //実例: 相模線の `205_main_mi/SG/205m.png` が、鶴見線パックに 1 個だけある
        //`205_main_mi/TR/205m.png` に化けていた (中間車だけ鶴見線の色になる)。
        //サフィックス一致は assets/<domain>/ を基準にした完全パス解決なので残す。
        String suffixLower = ("/" + norm).toLowerCase(Locale.ROOT);
        ZipEntry suffixMatch = null;
        java.util.Enumeration<? extends ZipEntry> en = zf.entries();
        while (en.hasMoreElements()) {
            ZipEntry ze = en.nextElement();
            if (ze.isDirectory()) continue;
            String name = ze.getName().replace('\\', '/');
            if (name.equalsIgnoreCase(norm)) {
                return ze;
            }
            if (suffixMatch == null && name.toLowerCase(Locale.ROOT).endsWith(suffixLower)) {
                suffixMatch = ze;
            }
        }
        return suffixMatch;
    }

    private static List<String> candidateResourcePaths(String norm) {
        List<String> candidates = new ArrayList<>();
        candidates.add(norm);
        candidates.add("assets/minecraft/" + norm);
        if (!norm.startsWith("textures/")) {
            candidates.add("assets/minecraft/textures/" + norm);
        }
        if (!norm.startsWith("models/") && looksLikeModelPath(norm)) {
            candidates.add("assets/minecraft/models/" + norm);
        }
        if (!norm.startsWith("scripts/") && looksLikeScriptPath(norm)) {
            candidates.add("assets/minecraft/scripts/" + norm);
        }
        return candidates;
    }

    private static boolean looksLikeModelPath(String norm) {
        String lower = norm.toLowerCase(Locale.ROOT);
        return lower.endsWith(".mqo") || lower.endsWith(".mqoz") || lower.endsWith(".obj") || lower.endsWith(".ngto");
    }

    private static boolean looksLikeScriptPath(String norm) {
        String lower = norm.toLowerCase(Locale.ROOT);
        return lower.endsWith(".js");
    }

    private record ResourceSearchResult(Path packPath, Path filePath, String zipEntryName) {
    }

    private static ResourceSearchResult findResource(String relative, Path preferredPackPath) throws IOException {
        if (relative == null || relative.isBlank()) {
            return null;
        }
        String normalized = normalize(relative).replaceFirst("^/+", "");
        String preferredKey = preferredPackPath == null ? "" : preferredPackPath.toAbsolutePath().normalize().toString();
        String cacheKey = preferredKey + "|" + normalized;
        ResourceSearchResult cached = RESOURCE_SEARCH_CACHE.get(cacheKey);
        if (cached != null) {
            logModelLoadDetail("resource-cache", "relative={} preferredPack={} hit={} resolvedPack={} filePath={} zipEntry={}",
                normalized, preferredPackPath, cached != MISSING_RESOURCE, cached.packPath(), cached.filePath(), cached.zipEntryName());
            return cached == MISSING_RESOURCE ? null : cached;
        }
        LinkedHashSet<Path> candidates = new LinkedHashSet<>();
        if (preferredPackPath != null) {
            candidates.add(preferredPackPath.toAbsolutePath().normalize());
        }
        candidates.addAll(getSharedPackCandidates());
        //★本家と同じく<b>完全パスだけ</b>で解決する。
        //本家 ModelPackManager.getResource は ResourceLocationCustom(domain, path) を作るだけで
        //探索しない (MC のリソースパック機構が assets/&lt;domain&gt;/&lt;path&gt; を完全一致で引く)。
        //RTMU にはファイル名だけで探すフォールバックがあったが、JRCT パックのように
        //全系列で `.../button/1.png` と同名のボタン画像を持つパックでは<b>別系列の絵</b>を
        //掴む (同名 5 個)。車両とボタンが一致しない原因だったので撤去した。
        for (Path candidate : candidates) {
            logModelLoadDetail("resource-scan", "relative={} candidatePack={}", normalized, candidate);
            ResourceSearchResult found = findResourceInPack(candidate, normalized);
            if (found != null) {
                logModelLoadDetail("resource-hit", "relative={} candidatePack={} filePath={} zipEntry={}",
                    normalized, candidate, found.filePath(), found.zipEntryName());
                RESOURCE_SEARCH_CACHE.put(cacheKey, found);
                return found;
            }
        }

        logModelLoadDetail("resource-miss", "relative={} preferredPack={} searchedPacks={}", normalized, preferredPackPath, candidates);
        RESOURCE_SEARCH_CACHE.put(cacheKey, MISSING_RESOURCE);
        return null;
    }

    private static ResourceSearchResult findResourceInPack(Path packPath, String relative) throws IOException {
        if (packPath == null || relative == null || relative.isBlank() || !Files.exists(packPath)) {
            return null;
        }
        if (Files.isDirectory(packPath)) {
            Path file = resolveFilePathInPack(packPath, relative);
            return file != null ? new ResourceSearchResult(packPath, file, null) : null;
        }
        try (ZipFile zip = new ZipFile(packPath.toFile())) {
            ZipEntry entry = findEntry(zip, relative);
            return entry != null ? new ResourceSearchResult(packPath, null, entry.getName()) : null;
        } catch (IOException e) {
            return null;
        }
    }

    private static List<Path> getSharedPackCandidates() {
        List<Path> cached = sharedPackCandidates;
        if (cached != null) {
            return cached;
        }
        synchronized (MqoModelLoader.class) {
            if (sharedPackCandidates != null) {
                return sharedPackCandidates;
            }
            LinkedHashSet<Path> candidates = new LinkedHashSet<>();
            try {
                Path gameDir = Minecraft.getInstance().gameDirectory.toPath();
                addPackCandidates(candidates, gameDir);
                addPackCandidates(candidates, gameDir.resolve("mods"));
                addPackCandidates(candidates, gameDir.resolve("mods").resolve("modelpacks"));
                addPackCandidates(candidates, gameDir.resolve("content"));
                addPackCandidates(candidates, gameDir.resolve("vehicle_packs"));
                addPackCandidates(candidates, com.portofino.realtrainmodunofficial.DefaultAssetsFolder.get());
                Path configDir = gameDir.resolve("config").resolve("realtrainmodunofficial");
                addPackCandidates(candidates, configDir);
                addPackCandidates(candidates, configDir.resolve("packs"));
                addPackCandidates(candidates, configDir.resolve("vehicle_packs"));
                addPackCandidates(candidates, configDir.resolve("rail_packs"));
                addPackCandidates(candidates, configDir.resolve("bundled_pack_cache"));
                try {
                    Path modJar = com.portofino.realtrainmodunofficial.BundledPackStore.getModJarPath();
                    if (modJar != null) {
                        candidates.add(modJar);
                    }
                } catch (Exception ignored) {}
            } catch (Exception e) {
                RealTrainModUnofficial.LOGGER.warn("Failed to build shared pack search list", e);
            }
            sharedPackCandidates = List.copyOf(candidates);
            return sharedPackCandidates;
        }
    }

    private static void addPackCandidates(LinkedHashSet<Path> candidates, Path dir) {
        if (dir == null || !Files.isDirectory(dir)) {
            return;
        }
        try (var stream = Files.list(dir)) {
            stream.forEach(path -> {
                try {
                    if (Files.isDirectory(path) || isSupportedArchive(path)) {
                        candidates.add(path.toAbsolutePath().normalize());
                    }
                } catch (Exception ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }

    private static boolean isSupportedArchive(Path path) {
        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return fileName.endsWith(".zip") || fileName.endsWith(".jar");
    }

    private static String readText(ResourceSearchResult resource) throws IOException {
        if (resource.filePath() != null) {
            return PackTextDecoder.readText(resource.filePath());
        }
        try (ZipFile zip = new ZipFile(resource.packPath().toFile())) {
            ZipEntry entry = zip.getEntry(resource.zipEntryName());
            if (entry == null) {
                throw new IOException("Missing zip entry: " + resource.zipEntryName());
            }
            try (InputStream in = zip.getInputStream(entry)) {
                return PackTextDecoder.readText(in);
            }
        }
    }

    private static String readCompressedMqo(ResourceSearchResult resource) throws IOException {
        if (resource.filePath() != null) {
            return readCompressedMqo(resource.filePath());
        }
        try (ZipFile zip = new ZipFile(resource.packPath().toFile())) {
            ZipEntry entry = zip.getEntry(resource.zipEntryName());
            if (entry == null) {
                throw new IOException("Missing zip entry: " + resource.zipEntryName());
            }
            try (InputStream in = zip.getInputStream(entry)) {
                return readCompressedMqo(in);
            }
        }
    }

    private static InputStream openResource(ResourceSearchResult resource) throws IOException {
        if (resource == null) {
            return null;
        }
        if (resource.filePath() != null) {
            return Files.newInputStream(resource.filePath());
        }
        ZipFile zip = new ZipFile(resource.packPath().toFile());
        ZipEntry entry = zip.getEntry(resource.zipEntryName());
        if (entry == null) {
            zip.close();
            return null;
        }
        InputStream raw = zip.getInputStream(entry);
        return new java.io.FilterInputStream(raw) {
            @Override
            public void close() throws IOException {
                super.close();
                zip.close();
            }
        };
    }

    private static MqoModel bake(String mqoText, TextureOpener opener, Map<String, String> textureOverrides, boolean smoothing) throws Exception {
        List<String> materialOrder = new ArrayList<>();
        List<String> materialTexPaths = new ArrayList<>();
        List<Float> materialAlphas = new ArrayList<>();
        List<float[]> materialColors = new ArrayList<>();
        List<Vec3> currentVerts = new ArrayList<>();
        // key = groupName + "|" + matKey so each object×material pair is a separate batch
        Map<String, BatchBuilder> byGroup = new LinkedHashMap<>();

        //★MQO 書式の解釈は jp.ngt.ngtlib.renderer.model.MqoReader に一本化してある。
        //ここは読み取った内容を描画バッチへ組むだけ (座標 0.01 倍・頂点順はそのまま)。
        //以前はこのメソッドが独自に同じ書式を舐めており、スクリプト側の ModelLoader と
        //解釈が二重管理になっていた。
        final int[] mirrorType = {-1};
        final float[] facetAngle = {RTM_DEFAULT_SMOOTHING_ANGLE};
        final String[] groupName = {"default"};
        final Exception[] failure = new Exception[1];

        jp.ngt.ngtlib.renderer.model.MqoReader.read(mqoText, new jp.ngt.ngtlib.renderer.model.MqoReader.Handler() {
            @Override
            public void material(int index, String name, String texPath, float r, float g, float b, float a) {
                materialOrder.add(name);
                materialTexPaths.add(texPath);
                materialAlphas.add(a);
                materialColors.add(new float[]{r, g, b});
            }

            @Override
            public void objectStart(String name) {
                mirrorType[0] = -1;
                facetAngle[0] = RTM_DEFAULT_SMOOTHING_ANGLE;
                groupName[0] = name == null || name.isEmpty() ? "default" : name;
            }

            @Override
            public void facet(float angle) {
                facetAngle[0] = angle;
            }

            @Override
            public void mirrorAxis(int axis) {
                //mirror_axis はビットマスク(1=X,2=Y,4=Z)。ビット判定で取りこぼしを防ぐ
                mirrorType[0] = (axis & 1) != 0 ? 0 : (axis & 2) != 0 ? 1 : (axis & 4) != 0 ? 2 : -1;
            }

            @Override
            public void verticesStart() {
                currentVerts.clear();
            }

            @Override
            public void vertex(float x, float y, float z) {
                currentVerts.add(new Vec3(x * 0.01F, y * 0.01F, z * 0.01F));
            }

            @Override
            public void face(int count, int materialId, int[] vertexIndices, float[] uvs) {
                if (failure[0] != null) {
                    return;
                }
                try {
                    addFace(count, materialId, vertexIndices, uvs, currentVerts, materialOrder, materialTexPaths,
                        materialAlphas, materialColors, textureOverrides, opener, mirrorType[0], groupName[0],
                        facetAngle[0], byGroup);
                } catch (Exception e) {
                    failure[0] = e;
                }
            }
        });
        if (failure[0] != null) {
            throw failure[0];
        }

        List<Batch> out = new ArrayList<>();
        applyObjectWideSmoothing(byGroup.values(), smoothing);
        for (BatchBuilder bb : byGroup.values()) {
            if (!bb.positions.isEmpty()) out.add(bb.bake());
        }
        List<ResourceLocation> materialTextures = new ArrayList<>(materialOrder.size());
        StringBuilder mapping = new StringBuilder();
        for (int i = 0; i < materialOrder.size(); i++) {
            materialTextures.add(resolveTexture((byte) i, materialOrder, materialTexPaths, textureOverrides, opener).location);
            //どの材質にどのテクスチャが載ったかを 1 モデル 1 回だけ残す。
            //「材質名は JSON にあるのに別の材質の絵が貼られる」類は、これが無いと追えない。
            mapping.append(i == 0 ? "" : ", ")
                   .append(materialOrder.get(i)).append("->").append(resolveTexturePath(
                       (byte) i, materialOrder, materialTexPaths, textureOverrides));
        }
        RealTrainModUnofficial.LOGGER.info("[RTMU] 材質→テクスチャ [{}]: {}", opener.getPackKey(), mapping);
        return new MqoModel(out, materialTextures);
    }

    private static MqoModel bakeObj(String objText, TextureOpener opener, Map<String, String> textureOverrides, boolean smoothing) throws Exception {
        List<Vec3> vertices = new ArrayList<>();
        List<float[]> texCoords = new ArrayList<>();
        List<Vector3f> normals = new ArrayList<>();
        Map<String, String> materialTextures = new HashMap<>();
        Map<String, BatchBuilder> byGroup = new LinkedHashMap<>();
        String currentGroup = "default";
        String currentMaterial = "default";

        for (String raw : objText.split("\\R")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("mtllib ")) {
                materialTextures.putAll(loadObjMaterialLibrary(line.substring(7).trim(), opener));
                continue;
            }
            if (line.startsWith("o ") || line.startsWith("g ")) {
                String name = line.substring(2).trim();
                currentGroup = name.isBlank() ? "default" : name;
                continue;
            }
            if (line.startsWith("usemtl ")) {
                String name = line.substring(7).trim();
                currentMaterial = name.isBlank() ? "default" : name;
                continue;
            }
            if (line.startsWith("v ")) {
                String[] parts = line.substring(2).trim().split("\\s+");
                if (parts.length >= 3) {
                    vertices.add(new Vec3(
                        Double.parseDouble(parts[0]),
                        Double.parseDouble(parts[1]),
                        Double.parseDouble(parts[2])
                    ));
                }
                continue;
            }
            if (line.startsWith("vt ")) {
                String[] parts = line.substring(3).trim().split("\\s+");
                if (parts.length >= 2) {
                    texCoords.add(new float[]{
                        Float.parseFloat(parts[0]),
                        1.0F - Float.parseFloat(parts[1])
                    });
                }
                continue;
            }
            if (line.startsWith("vn ")) {
                String[] parts = line.substring(3).trim().split("\\s+");
                if (parts.length >= 3) {
                    Vector3f normal = new Vector3f(
                        Float.parseFloat(parts[0]),
                        Float.parseFloat(parts[1]),
                        Float.parseFloat(parts[2])
                    );
                    if (normal.lengthSquared() > 1.0E-8F) {
                        normal.normalize();
                    }
                    normals.add(normal);
                }
                continue;
            }
            if (!line.startsWith("f ")) {
                continue;
            }

            ObjFaceVertex[] faceVertices = parseObjFace(line.substring(2).trim(), vertices, texCoords, normals);
            if (faceVertices.length < 3) {
                continue;
            }

            TextureInfo textureInfo = resolveObjTexture(currentMaterial, materialTextures, textureOverrides, opener);
            float[] uvBounds = flattenUvs(faceVertices);
            float avgY = 0f;
            for (ObjFaceVertex fv : faceVertices) avgY += (float) fv.position().y;
            avgY /= faceVertices.length;
            boolean translucent = shouldTreatFaceAsTranslucent(textureInfo, currentGroup, uvBounds, faceVertices.length, avgY);
            int materialId = currentMaterial.hashCode() & 0x7FFFFFFF;
            String batchKey = currentGroup + "|" + currentMaterial + "|" + translucent;
            final String batchGroupName = currentGroup;
            BatchBuilder bb = byGroup.computeIfAbsent(batchKey,
                k -> new BatchBuilder(byGroup.size(), batchGroupName, textureInfo.location, textureInfo.emissiveTextures, materialId, translucent, 60.0F));

            if (faceVertices.length == 4) {
                emitObjQuad(faceVertices[0], faceVertices[1], faceVertices[2], faceVertices[3], bb);
            } else {
                for (int i = 1; i < faceVertices.length - 1; i++) {
                    emitObjTri(faceVertices[0], faceVertices[i], faceVertices[i + 1], bb);
                }
            }
        }

        List<Batch> out = new ArrayList<>();
        LinkedHashSet<ResourceLocation> uniqueTextures = new LinkedHashSet<>();
        applyObjectWideSmoothing(byGroup.values(), smoothing);
        for (BatchBuilder bb : byGroup.values()) {
            if (!bb.positions.isEmpty()) {
                Batch batch = bb.bake();
                out.add(batch);
                uniqueTextures.add(batch.texture);
            }
        }
        return new MqoModel(out, new ArrayList<>(uniqueTextures));
    }

    private static ObjFaceVertex[] parseObjFace(String faceSpec, List<Vec3> vertices, List<float[]> texCoords, List<Vector3f> normals) {
        String[] parts = faceSpec.split("\\s+");
        List<ObjFaceVertex> out = new ArrayList<>(parts.length);
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            String[] indices = part.split("/", -1);
            int vertexIndex = resolveObjIndex(indices.length > 0 ? indices[0] : "", vertices.size());
            if (vertexIndex < 0 || vertexIndex >= vertices.size()) {
                continue;
            }
            float u = 0.0F;
            float v = 0.0F;
            if (indices.length > 1 && !indices[1].isBlank()) {
                int texIndex = resolveObjIndex(indices[1], texCoords.size());
                if (texIndex >= 0 && texIndex < texCoords.size()) {
                    float[] uv = texCoords.get(texIndex);
                    u = uv[0];
                    v = uv[1];
                }
            }
            Vector3f normal = null;
            if (indices.length > 2 && !indices[2].isBlank()) {
                int normalIndex = resolveObjIndex(indices[2], normals.size());
                if (normalIndex >= 0 && normalIndex < normals.size()) {
                    normal = new Vector3f(normals.get(normalIndex));
                }
            }
            out.add(new ObjFaceVertex(vertices.get(vertexIndex), u, v, normal));
        }
        return out.toArray(ObjFaceVertex[]::new);
    }

    private static int resolveObjIndex(String token, int size) {
        if (token == null || token.isBlank()) {
            return -1;
        }
        int index = Integer.parseInt(token.trim());
        return index > 0 ? index - 1 : size + index;
    }

    private static float[] flattenUvs(ObjFaceVertex[] vertices) {
        float[] out = new float[vertices.length * 2];
        for (int i = 0; i < vertices.length; i++) {
            out[i * 2] = vertices[i].u();
            out[i * 2 + 1] = vertices[i].v();
        }
        return out;
    }

    private static void emitObjQuad(ObjFaceVertex v0, ObjFaceVertex v1, ObjFaceVertex v2, ObjFaceVertex v3, BatchBuilder bb) {
        Vector3f normal = chooseFaceNormal(v0, v1, v2, v3);
        if (!bb.markFace(
            new Vec3[]{v0.position(), v1.position(), v2.position(), v3.position()},
            new float[]{v0.u(), v0.v(), v1.u(), v1.v(), v2.u(), v2.v(), v3.u(), v3.v()})) {
            return;
        }
        putObjVertex(bb, v0, normal);
        putObjVertex(bb, v1, normal);
        putObjVertex(bb, v2, normal);
        putObjVertex(bb, v3, normal);
    }

    private static void emitObjTri(ObjFaceVertex v0, ObjFaceVertex v1, ObjFaceVertex v2, BatchBuilder bb) {
        Vector3f normal = chooseFaceNormal(v0, v1, v2, null);
        if (!bb.markFace(
            new Vec3[]{v0.position(), v1.position(), v2.position(), v2.position()},
            new float[]{v0.u(), v0.v(), v1.u(), v1.v(), v2.u(), v2.v(), v2.u(), v2.v()})) {
            return;
        }
        putObjVertex(bb, v0, normal);
        putObjVertex(bb, v1, normal);
        putObjVertex(bb, v2, normal);
        putObjVertex(bb, v2, normal);
    }

    private static void putObjVertex(BatchBuilder bb, ObjFaceVertex vertex, Vector3f fallbackNormal) {
        Vector3f normal = vertex.normal() != null ? new Vector3f(vertex.normal()) : new Vector3f(fallbackNormal);
        if (!FaceNormals.normalize(normal)) {
            normal.set(fallbackNormal);
            FaceNormals.normalize(normal);
        }
        bb.put(vertex.position(), normal, vertex.u(), vertex.v());
    }

    /** 面積 0 (3 点が一直線/同一点) か。大きさでは判定しない。 */
    private static boolean isUsableNormal(Vector3f n) {
        float max = Math.max(Math.abs(n.x), Math.max(Math.abs(n.y), Math.abs(n.z)));
        return max > 0.0F && Float.isFinite(max);
    }

    private static Vector3f chooseFaceNormal(ObjFaceVertex v0, ObjFaceVertex v1, ObjFaceVertex v2, ObjFaceVertex v3) {
        Vector3f supplied = averageSuppliedNormals(v0, v1, v2, v3);
        if (supplied != null) {
            return supplied;
        }
        Vector3f e1 = new Vector3f((float) (v1.position().x - v0.position().x), (float) (v1.position().y - v0.position().y), (float) (v1.position().z - v0.position().z));
        Vector3f e2 = new Vector3f((float) (v2.position().x - v0.position().x), (float) (v2.position().y - v0.position().y), (float) (v2.position().z - v0.position().z));
        Vector3f normal = e1.cross(e2);
        if (!isUsableNormal(normal) && v3 != null) {
            e2.set((float) (v3.position().x - v0.position().x), (float) (v3.position().y - v0.position().y), (float) (v3.position().z - v0.position().z));
            normal = e1.cross(e2);
        }
        FaceNormals.normalize(normal);
        return normal;
    }

    private static Vector3f averageSuppliedNormals(ObjFaceVertex... vertices) {
        Vector3f sum = new Vector3f();
        int count = 0;
        for (ObjFaceVertex vertex : vertices) {
            if (vertex != null && vertex.normal() != null) {
                sum.add(vertex.normal());
                count++;
            }
        }
        if (count == 0 || sum.lengthSquared() <= 1.0E-8F) {
            return null;
        }
        sum.normalize();
        return sum;
    }

    private static TextureInfo resolveObjTexture(String materialName, Map<String, String> materialTextures,
                                                 Map<String, String> textureOverrides, TextureOpener opener) throws Exception {
        String path = null;
        if (materialName != null && textureOverrides.containsKey(materialName)) {
            path = textureOverrides.get(materialName);
        }
        if ((path == null || path.isBlank()) && textureOverrides.containsKey("default")) {
            path = textureOverrides.get("default");
        }
        // OBJ も MQO 同様、JSON overrides を mtl 由来のパスより優先する。
        if ((path == null || path.isBlank()) && !textureOverrides.isEmpty()) {
            path = textureOverrides.values().iterator().next();
        }
        if ((path == null || path.isBlank()) && materialName != null) {
            path = materialTextures.get(materialName);
        }
        if ((path == null || path.isBlank()) && !materialTextures.isEmpty()) {
            path = materialTextures.values().iterator().next();
        }
        if (path == null || path.isBlank()) {
            path = "textures/misc/white.png";
        }
        TextureBinding binding = TextureBinding.parse(path);
        String cacheKey = opener.getPackKey() + "|" + binding.cacheKey();
        logModelLoadDetail("texture-resolve-obj", "materialName={} resolvedPath={} cacheKey={}", materialName, path, cacheKey);
        return TEXTURE_INFO_CACHE.computeIfAbsent(cacheKey, k -> registerTextureFromZip(binding, opener));
    }

    private static Map<String, String> loadObjMaterialLibrary(String materialFile, TextureOpener opener) {
        Map<String, String> materials = new HashMap<>();
        if (materialFile == null || materialFile.isBlank()) {
            return materials;
        }
        try (InputStream input = opener.open(materialFile)) {
            if (input == null) {
                return materials;
            }
            String current = null;
            for (String raw : PackTextDecoder.readText(input).split("\\R")) {
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                if (line.startsWith("newmtl ")) {
                    current = line.substring(7).trim();
                    continue;
                }
                if (current == null) {
                    continue;
                }
                if (line.startsWith("map_Kd ")) {
                    materials.put(current, line.substring(7).trim());
                } else if (line.startsWith("map_d ")) {
                    materials.putIfAbsent(current, line.substring(6).trim() + TEXTURE_META_SEPARATOR + "alphablend");
                }
            }
        } catch (Exception ignored) {
        }
        return materials;
    }

    private record ObjFaceVertex(Vec3 position, float u, float v, Vector3f normal) {
    }


    /**
     * 面 1 枚をバッチへ足す。書式の解釈は {@link jp.ngt.ngtlib.renderer.model.MqoReader} 側で
     * 済んでおり、ここへは解析済みの値だけが渡る。
     */
    private static void addFace(
        int vertexCount,
        int materialId,
        int[] vidx,
        float[] uvs,
        List<Vec3> verts,
        List<String> materialOrder,
        List<String> materialTexPaths,
        List<Float> materialAlphas,
        List<float[]> materialColors,
        Map<String, String> textureOverrides,
        TextureOpener opener,
        int mirrorType,
        String groupName,
        float facetAngle,
        Map<String, BatchBuilder> byGroup
    ) throws Exception {
        if (vertexCount < 3 || vidx == null) return;
        byte matId = (byte) materialId;
        TextureInfo textureInfo = resolveTexture(matId, materialOrder, materialTexPaths, textureOverrides, opener);
        float matAlpha = (matId & 0xFF) < materialAlphas.size() ? materialAlphas.get(matId & 0xFF) : 1.0F;
        int matKey = matId & 0xFF;
        if (uvs != null && uvs.length < vertexCount * 2) uvs = null;
        float avgY = 0f;
        float faceMinY = Float.MAX_VALUE, faceMaxY = -Float.MAX_VALUE;
        {
            int cnt = Math.min(vertexCount, vidx.length);
            for (int i = 0; i < cnt; i++) {
                try {
                    float vy = (float) verts.get(vidx[i]).y;
                    avgY += vy;
                    if (vy < faceMinY) faceMinY = vy;
                    if (vy > faceMaxY) faceMaxY = vy;
                } catch (Exception ignored) {}
            }
            if (cnt > 0) avgY /= cnt;
        }
        if (shouldSkipLegacyShadowPlaneFace(groupName, verts, vidx, vertexCount, faceMinY, faceMaxY)) {
            return;
        }
        // マテリアル col の a<1 = ガラス等の半透明。グループ名に依らず半透明描画し、その不透明度を適用する。
        boolean translucent = matAlpha < 0.99F
            || shouldTreatFaceAsTranslucent(textureInfo, groupName, uvs, vertexCount, avgY);
        String batchKey = groupName + "|" + matKey + "|" + translucent;
        int batchOrder = byGroup.size();
        float baseAlpha = matAlpha;
        float[] matColor = matKey < materialColors.size() ? materialColors.get(matKey) : null;
        final float colR = matColor != null ? matColor[0] : 1.0F;
        final float colG = matColor != null ? matColor[1] : 1.0F;
        final float colB = matColor != null ? matColor[2] : 1.0F;
        BatchBuilder bb = byGroup.computeIfAbsent(batchKey, k -> {
            BatchBuilder b = new BatchBuilder(batchOrder, groupName, textureInfo.location, textureInfo.emissiveTextures, matKey, translucent, facetAngle);
            b.baseAlpha = baseAlpha;
            b.baseColorR = colR;
            b.baseColorG = colG;
            b.baseColorB = colB;
            b.glassTranslucent = textureInfo.hasGlassBand;
            b.texHasTranslucentPixels = textureInfo.hasAnyTranslucentPixel;
            b.pass1Mask = textureInfo.pass1Mask;
            b.opaqueTexture = textureInfo.opaqueLocation;
            b.windowTexture = textureInfo.windowLocation;
            return b;
        });

        if (vertexCount == 4) {
            addQuad(verts, vidx, uvs, matId, bb, mirrorType);
        } else {
            addPolygonFan(verts, vidx, uvs, vertexCount, bb, mirrorType);
        }
    }

    private static boolean shouldSkipLegacyShadowPlaneFace(String groupName, List<Vec3> verts, int[] vidx,
                                                           int vertexCount, float faceMinY, float faceMaxY) {
        if (groupName == null || verts == null || vidx == null) {
            return false;
        }
        String lower = groupName.trim().toLowerCase(Locale.ROOT);
        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE;
        float minZ = Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        int cnt = Math.min(vertexCount, vidx.length);
        for (int i = 0; i < cnt; i++) {
            try {
                Vec3 v = verts.get(vidx[i]);
                float x = (float) v.x;
                float z = (float) v.z;
                if (x < minX) minX = x;
                if (x > maxX) maxX = x;
                if (z < minZ) minZ = z;
                if (z > maxZ) maxZ = z;
            } catch (Exception ignored) {
            }
        }
        if (cnt < 3 || minX == Float.MAX_VALUE || minZ == Float.MAX_VALUE) {
            return false;
        }
        float dx = maxX - minX;
        float dy = faceMaxY - faceMinY;
        float dz = maxZ - minZ;
        // MQO vertices are stored after the legacy 0.01 scale conversion. 旧RTM用パックの
        // 車体下「影板」は元MQO上で y=-98 / z=±900 付近なので、ここでは -0.98 / ±9.0
        // として判定する。
        boolean underBody = faceMinY < -0.10F;
        //影板判定の高さ閾値。内装床を巻き込まない範囲に限定
        boolean broadHorizontalPlate = faceMinY < -0.55F && dy < 0.035F && dx > 0.45F && dz > 1.20F;
        boolean veryLowFlatPlate = faceMinY < -0.75F && dy < 0.05F && (dz > 0.45F || dx > 0.80F);
        boolean lowUnderbodyPlate = faceMinY < -0.62F && dy < 0.012F && dx > 0.42F && dz > 0.62F;
        boolean unnamedLegacyShadowPlate = faceMinY < -0.90F && dy < 0.008F && dx > 0.90F && dz > 2.0F;
        if (unnamedLegacyShadowPlate) {
            return true;
        }
        //車体グループ内の偽影板(大きく平坦・車体下)だけを間引く
        if ((veryLowFlatPlate || lowUnderbodyPlate || broadHorizontalPlate) && (lower.equals("obj1") || lower.equals("obj2") || lower.equals("obj3")
                || lower.equals("body") || lower.startsWith("body_"))) {
            return true;
        }
        if (lower.equals("alpha") || lower.startsWith("alpha_")) {
            boolean e131UnderbodyShadowBox = faceMinY < -0.75F
                && faceMaxY < 0.05F
                && (dz > 18.0F || dx > 0.75F);
            return veryLowFlatPlate || e131UnderbodyShadowBox;
        }
        if (!lower.contains("shadow") && !lower.endsWith("_ms")) {
            return false;
        }
        boolean veryLong = dz > 12.0F || dx > 2.2F;
        boolean slabLike = dy < 1.4F;
        return veryLowFlatPlate || (underBody && veryLong && slabLike);
    }

    private static void addQuad(List<Vec3> verts, int[] vidx, float[] uvs, byte matId, BatchBuilder bb, int mirrorType) {
        int[] ix = new int[4];
        for (int i = 0; i < 4; i++) ix[i] = vidx[i];
        Vec3[] p = new Vec3[4];
        float[] u = new float[4];
        float[] v = new float[4];
        for (int i = 0; i < 4; i++) {
            int si = 3 - i;
            p[si] = verts.get(ix[i]);
            if (uvs != null) {
                u[si] = uvs[i * 2];
                v[si] = uvs[i * 2 + 1];
            }
        }
        emitQuad(p[0], p[1], p[2], p[3], u[0], v[0], u[1], v[1], u[2], v[2], u[3], v[3], bb, mirrorType);
    }

    private static void emitQuad(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3,
                                  float u0, float v0, float u1, float v1,
                                  float u2, float v2, float u3, float v3,
                                  BatchBuilder bb, int mirrorType) {
        if (!bb.markFace(new Vec3[]{p0, p1, p2, p3}, new float[]{u0, v0, u1, v1, u2, v2, u3, v3})) {
            return;
        }
        Vector3f e1 = new Vector3f((float) (p1.x - p0.x), (float) (p1.y - p0.y), (float) (p1.z - p0.z));
        Vector3f e2 = new Vector3f((float) (p2.x - p0.x), (float) (p2.y - p0.y), (float) (p2.z - p0.z));
        Vector3f n = e1.cross(e2);
        //★大きさで捨てない。文字のような細かい三角形は |n|^2 が 1e-10 台になるので、
        //閾値を置くと正常な面まで法線が真上へ差し替わる ([[FaceNormals]])。
        FaceNormals.normalize(n);
        bb.put(p0, n, u0, v0);
        bb.put(p1, n, u1, v1);
        bb.put(p2, n, u2, v2);
        bb.put(p3, n, u3, v3);
        if (mirrorType >= 0 && mirrorType <= 2 && !isFaceOnMirrorPlane(new Vec3[]{p0, p1, p2, p3}, mirrorType)) {
            Vec3 mp0 = mirror(p0, mirrorType);
            Vec3 mp3 = mirror(p3, mirrorType);
            Vec3 mp2 = mirror(p2, mirrorType);
            Vec3 mp1 = mirror(p1, mirrorType);
            if (!bb.markFace(new Vec3[]{mp0, mp3, mp2, mp1}, new float[]{u0, v0, u3, v3, u2, v2, u1, v1})) {
                return;
            }
            Vector3f mn = mirrorN(n, mirrorType);
            bb.put(mp0, mn, u0, v0);
            bb.put(mp3, mn, u3, v3);
            bb.put(mp2, mn, u2, v2);
            bb.put(mp1, mn, u1, v1);
        }
    }

    private static void addPolygonFan(List<Vec3> verts, int[] vidx, float[] uvs, int vertexCount, BatchBuilder bb, int mirrorType) {
        Vec3[] p = new Vec3[vertexCount];
        float[] localU = new float[vertexCount];
        float[] localV = new float[vertexCount];
        for (int i = 0; i < vertexCount; i++) {
            int si = vertexCount - 1 - i;
            p[si] = verts.get(vidx[i]);
            if (uvs != null) {
                localU[si] = uvs[i * 2];
                localV[si] = uvs[i * 2 + 1];
            }
        }

        for (int i = 1; i < vertexCount - 1; i++) {
            emitTri(
                p[0], p[i], p[i + 1],
                uvs == null ? 0.0F : localU[0], uvs == null ? 0.0F : localV[0],
                uvs == null ? 0.0F : localU[i], uvs == null ? 0.0F : localV[i],
                uvs == null ? 0.0F : localU[i + 1], uvs == null ? 0.0F : localV[i + 1],
                bb, mirrorType
            );
        }
    }

    private static void emitTri(Vec3 p0, Vec3 p1, Vec3 p2, float u0, float v0, float u1, float v1, float u2, float v2, BatchBuilder bb, int mirrorType) {
        if (!bb.markFace(new Vec3[]{p0, p1, p2, p2}, new float[]{u0, v0, u1, v1, u2, v2, u2, v2})) {
            return;
        }
        Vector3f e1 = new Vector3f((float) (p1.x - p0.x), (float) (p1.y - p0.y), (float) (p1.z - p0.z));
        Vector3f e2 = new Vector3f((float) (p2.x - p0.x), (float) (p2.y - p0.y), (float) (p2.z - p0.z));
        Vector3f n = e1.cross(e2);
        //★大きさで捨てない。文字のような細かい三角形は |n|^2 が 1e-10 台になるので、
        //閾値を置くと正常な面まで法線が真上へ差し替わる ([[FaceNormals]])。
        FaceNormals.normalize(n);
        // QUADSモードは4頂点/面が必要 → 3頂点の三角形は縮退クワッドとして扱う (v0,v1,v2,v2)
        bb.put(p0, n, u0, v0);
        bb.put(p1, n, u1, v1);
        bb.put(p2, n, u2, v2);
        bb.put(p2, n, u2, v2);
        if (mirrorType >= 0 && mirrorType <= 2 && !isFaceOnMirrorPlane(new Vec3[]{p0, p1, p2}, mirrorType)) {
            Vec3 mp0 = mirror(p0, mirrorType);
            Vec3 mp2 = mirror(p2, mirrorType);
            Vec3 mp1 = mirror(p1, mirrorType);
            if (!bb.markFace(new Vec3[]{mp0, mp2, mp1, mp1}, new float[]{u0, v0, u2, v2, u1, v1, u1, v1})) {
                return;
            }
            Vector3f mn = mirrorN(n, mirrorType);
            bb.put(mp0, mn, u0, v0);
            bb.put(mp2, mn, u2, v2);
            bb.put(mp1, mn, u1, v1);
            bb.put(mp1, mn, u1, v1);
        }
    }

    private static Vec3 mirror(Vec3 p, int type) {
        float x = (float) p.x;
        float y = (float) p.y;
        float z = (float) p.z;
        float[] m = switch (type) {
            case 0 -> new float[]{-1, 1, 1};
            case 1 -> new float[]{1, -1, 1};
            default -> new float[]{1, 1, -1};
        };
        return new Vec3(x * m[0], y * m[1], z * m[2]);
    }

    private static Vector3f mirrorN(Vector3f n, int type) {
        float[] m = switch (type) {
            case 0 -> new float[]{-1, 1, 1};
            case 1 -> new float[]{1, -1, 1};
            default -> new float[]{1, 1, -1};
        };
        Vector3f o = new Vector3f(n.x * m[0], n.y * m[1], n.z * m[2]);
        FaceNormals.normalize(o);
        return o;
    }

    private static boolean isFaceOnMirrorPlane(Vec3[] points, int mirrorType) {
        if (mirrorType < 0 || mirrorType > 2) return false;
        double epsilon = 1.0e-5;
        for (Vec3 p : points) {
            double value = mirrorType == 0 ? p.x : mirrorType == 1 ? p.y : p.z;
            if (Math.abs(value) > epsilon) {
                return false;
            }
        }
        return true;
    }



    private static String matchGroup(Pattern pat, String line) {
        Matcher mm = pat.matcher(line);
        return mm.find() ? mm.group(1) : null;
    }

    /**
     * {@link #resolveTexture} と同じ順序でパスだけ決める (ログ用・読み込みはしない)。
     */
    private static String resolveTexturePath(byte matId, List<String> materialOrder, List<String> materialTexPaths,
                                             Map<String, String> overrides) {
        int idx = matId & 0xFF;
        String matName = (idx < materialOrder.size()) ? materialOrder.get(idx) : null;
        if (matName == null && !materialOrder.isEmpty()) matName = materialOrder.get(0);
        String path = null;
        if (matName != null) path = overrides.get(matName);
        if (path == null) path = overrides.get(String.valueOf(idx));
        if (path == null) path = overrides.get("default");
        if (path == null && materialTexPaths != null && idx < materialTexPaths.size()) {
            String embedded = materialTexPaths.get(idx);
            if (embedded != null && !isWindowsAbsolutePath(embedded)) path = embedded;
        }
        return path == null ? "(白)" : path;
    }

    private static TextureInfo resolveTexture(byte matId, List<String> materialOrder, List<String> materialTexPaths, Map<String, String> overrides, TextureOpener opener) throws Exception {
        int idx = matId & 0xFF;
        String matName = (idx < materialOrder.size()) ? materialOrder.get(idx) : null;
        if (matName == null && !materialOrder.isEmpty()) matName = materialOrder.get(0);
        String path = null;
        // 1. material name lookup (e.g. "KQBody" -> "KQBody.png")
        if (matName != null) path = overrides.get(matName);
        // 2. numeric index lookup (e.g. "0" -> "texture.png")
        if (path == null) path = overrides.get(String.valueOf(idx));
        // 3. "default" 上書き — MQO埋め込みtexより優先
        if (path == null) path = overrides.get("default");
        //★「どれでもいいから overrides の先頭」という代替は<b>やらない</b> (本家に無い)。
        //`VehicleDefinition` の overrides は `Map.copyOf` の不変マップで、その反復順は
        //JVM 起動ごとに変わる (ImmutableCollections の SALT)。つまり先頭を採ると
        //<b>起動するたびに別の材質のテクスチャ</b>が貼られ、鼻先だけ車体の絵になる、
        //といった再現性の無い化け方をする。見つからないものは見つからないままにして、
        //下の MQO 埋め込み tex("...") → 白、という本家と同じ順に落とす。
        if (path == null && !overrides.isEmpty()) {
            RealTrainModUnofficial.LOGGER.warn(
                "[RTMU] 材質 {} (index {}) に対応するテクスチャ指定がありません。JSON にあるのは {}",
                matName, idx, overrides.keySet());
        }
        // 4. tex("...") embedded in MQO material line — skip Windows absolute paths (C:\...) that can't be resolved
        if (path == null && materialTexPaths != null && idx < materialTexPaths.size()) {
            String embedded = materialTexPaths.get(idx);
            if (embedded != null && !isWindowsAbsolutePath(embedded)) {
                path = embedded;
            }
        }
        if (path == null) path = "textures/misc/white.png";
        final String resolvedPath = path;
        final String resolvedMatName = matName;
        TextureBinding binding = TextureBinding.parse(resolvedPath);
        String cacheKey = opener.getPackKey() + "|" + binding.cacheKey();
        return TEXTURE_INFO_CACHE.computeIfAbsent(cacheKey, k -> {
            logModelLoadDetail("texture-resolve-mqo", "matId={} matName={} resolvedPath={}", matId, resolvedMatName, resolvedPath);
            return registerTextureFromZip(binding, opener);
        });
    }

    private static void loadScriptForModel(MqoModel model, Path packPath, String scriptPath) {
        loadScriptForModel(model, packPath, scriptPath, null);
    }

    private static void loadScriptForModel(MqoModel model, Path packPath, String scriptPath, String modelName) {
        if (model == null || packPath == null) {
            RealTrainModUnofficial.LOGGER.warn("loadScriptForModel: model or packPath is null");
            return;
        }
        String normalized = normalizeScriptPath(scriptPath);
        String leaf = normalized.contains("/") ? normalized.substring(normalized.lastIndexOf('/') + 1) : normalized;
        boolean hasExplicitPath = !normalized.isBlank();


        try {
            if (hasExplicitPath) {
                String legacyScript = VehicleModelPackManager.INSTANCE.getScript(normalized);
                if (legacyScript == null || legacyScript.isBlank()) {
                    legacyScript = VehicleModelPackManager.INSTANCE.getScript(leaf);
                }
                if (legacyScript != null && !legacyScript.isBlank()) {
                    TrainScriptSystem.loadScript(normalized, legacyScript, model, modelName);
                    return;
                }
            }
        } catch (Exception ignored) {
            // legacy resource manager may not be initialized or the script may not be available
        }

        try {
            if (Files.isDirectory(packPath)) {
                Path scriptFile = null;
                if (hasExplicitPath) {
                    scriptFile = resolveFilePathInPack(packPath, normalized);
                    if (scriptFile == null) {
                        scriptFile = resolveFilePathInPack(packPath, leaf);
                    }
                }
                if (scriptFile != null && Files.exists(scriptFile)) {
                    String script = PackTextDecoder.readText(scriptFile);
                    script = preprocessScriptIncludesForDirectory(scriptFile, rootDirectory(packPath));
                    TrainScriptSystem.loadScript(normalized, script, model, modelName);
                } else {
                    Path fallback = findFallbackScriptFile(packPath);
                    if (fallback != null) {
                        RealTrainModUnofficial.LOGGER.warn("Model script {} not found in pack directory {}; using fallback {}", normalized, com.portofino.realtrainmodunofficial.util.LogPaths.safe(packPath), fallback);
                        String script = PackTextDecoder.readText(fallback);
                        script = preprocessScriptIncludesForDirectory(fallback, rootDirectory(packPath));
                        TrainScriptSystem.loadScript(fallback.toString(), script, model, modelName);
                    } else {
                        if (hasExplicitPath) {
                            ResourceSearchResult external = findResource(normalized, packPath);
                            if (external != null && !packPath.equals(external.packPath())) {
                                loadScriptFromResource(model, external, normalized, modelName);
                            } else {
                                RealTrainModUnofficial.LOGGER.warn("Model script not found in pack directory: {} (normalized={})", com.portofino.realtrainmodunofficial.util.LogPaths.safe(packPath), normalized);
                            }
                        } else {
                            RealTrainModUnofficial.LOGGER.warn("No fallback model script found in pack directory: {}", com.portofino.realtrainmodunofficial.util.LogPaths.safe(packPath));
                        }
                    }
                }
            } else {
                try (ZipFile zf = new ZipFile(packPath.toFile())) {
                    ZipEntry entry = null;
                    if (hasExplicitPath) {
                        entry = findEntry(zf, normalized);
                        if (entry == null && !leaf.isBlank()) {
                            entry = findEntry(zf, leaf);
                        }
                    }
                    if (entry != null) {
                        try (InputStream in = zf.getInputStream(entry)) {
                            String script = PackTextDecoder.readText(in);
                            script = preprocessScriptIncludesForZip(zf, entry.getName(), script);
                            TrainScriptSystem.loadScript(normalized, script, model, modelName);
                        }
                    } else {
                        ZipEntry fallback = findFallbackScriptEntry(zf);
                        if (fallback != null) {
                            RealTrainModUnofficial.LOGGER.warn("Model script {} not found in pack zip {}; using fallback {}", normalized, com.portofino.realtrainmodunofficial.util.LogPaths.safe(packPath), fallback.getName());
                            try (InputStream in = zf.getInputStream(fallback)) {
                                String script = PackTextDecoder.readText(in);
                                script = preprocessScriptIncludesForZip(zf, fallback.getName(), script);
                                TrainScriptSystem.loadScript(fallback.getName(), script, model, modelName);
                            }
                        } else {
                            if (hasExplicitPath) {
                                ResourceSearchResult external = findResource(normalized, packPath);
                                if (external != null && !packPath.equals(external.packPath())) {
                                    loadScriptFromResource(model, external, normalized, modelName);
                                } else {
                                    RealTrainModUnofficial.LOGGER.warn("Model script not found in pack zip: {} (normalized={})", com.portofino.realtrainmodunofficial.util.LogPaths.safe(packPath), normalized);
                                }
                            } else {
                                RealTrainModUnofficial.LOGGER.warn("No fallback model script found in pack zip: {}", com.portofino.realtrainmodunofficial.util.LogPaths.safe(packPath));
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            RealTrainModUnofficial.LOGGER.warn("Failed to load script {} from pack {}", scriptPath, com.portofino.realtrainmodunofficial.util.LogPaths.safe(packPath), e);
        }
    }

    private static ScriptEngine loadStandaloneScript(Path packPath, String scriptPath, String modelName) {
        if (packPath == null) {
            return null;
        }
        String source = loadStandaloneScriptSource(packPath, scriptPath);
        if (source == null || source.isBlank()) {
            return null;
        }
        return TrainScriptSystem.loadStandaloneScript(scriptPath, source, modelName);
    }

    private static String loadStandaloneScriptSource(Path packPath, String scriptPath) {
        if (packPath == null) {
            return null;
        }
        String normalized = normalizeScriptPath(scriptPath);
        String leaf = normalized.contains("/") ? normalized.substring(normalized.lastIndexOf('/') + 1) : normalized;
        boolean hasExplicitPath = !normalized.isBlank();

        try {
            if (hasExplicitPath) {
                String legacyScript = VehicleModelPackManager.INSTANCE.getScript(normalized);
                if (legacyScript == null || legacyScript.isBlank()) {
                    legacyScript = VehicleModelPackManager.INSTANCE.getScript(leaf);
                }
                if (legacyScript != null && !legacyScript.isBlank()) {
                    return legacyScript;
                }
            }
        } catch (Exception ignored) {
        }

        try {
            if (Files.isDirectory(packPath)) {
                Path scriptFile = null;
                if (hasExplicitPath) {
                    scriptFile = resolveFilePathInPack(packPath, normalized);
                    if (scriptFile == null) {
                        scriptFile = resolveFilePathInPack(packPath, leaf);
                    }
                }
                if (scriptFile != null && Files.exists(scriptFile)) {
                    String script = PackTextDecoder.readText(scriptFile);
                    script = preprocessScriptIncludesForDirectory(scriptFile, rootDirectory(packPath));
                    return script;
                }
                if (hasExplicitPath) {
                    ResourceSearchResult external = findResource(normalized, packPath);
                    if (external != null) {
                        return readText(external);
                    }
                }
            } else {
                try (ZipFile zf = new ZipFile(packPath.toFile())) {
                    ZipEntry entry = null;
                    if (hasExplicitPath) {
                        entry = findEntry(zf, normalized);
                        if (entry == null && !leaf.isBlank()) {
                            entry = findEntry(zf, leaf);
                        }
                    }
                    if (entry != null) {
                        try (InputStream in = zf.getInputStream(entry)) {
                            String script = PackTextDecoder.readText(in);
                            script = preprocessScriptIncludesForZip(zf, entry.getName(), script);
                            return script;
                        }
                    }
                    if (hasExplicitPath) {
                        ResourceSearchResult external = findResource(normalized, packPath);
                        if (external != null) {
                            return readText(external);
                        }
                    }
                }
            }
        } catch (Exception e) {
            RealTrainModUnofficial.LOGGER.warn("Failed to load standalone script {} from {}", scriptPath, com.portofino.realtrainmodunofficial.util.LogPaths.safe(packPath), e);
        }
        return null;
    }

    private static void loadScriptFromResource(MqoModel model, ResourceSearchResult resource, String scriptPath, String modelName) throws IOException {
        if (resource.filePath() != null) {
            Path scriptFile = resource.filePath();
            String script = PackTextDecoder.readText(scriptFile);
            script = preprocessScriptIncludesForDirectory(scriptFile, rootDirectory(resource.packPath()));
            TrainScriptSystem.loadScript(scriptPath, script, model, modelName);
            return;
        }
        try (ZipFile zip = new ZipFile(resource.packPath().toFile())) {
            ZipEntry entry = zip.getEntry(resource.zipEntryName());
            if (entry == null) {
                return;
            }
            try (InputStream in = zip.getInputStream(entry)) {
                String script = PackTextDecoder.readText(in);
                script = preprocessScriptIncludesForZip(zip, entry.getName(), script);
                TrainScriptSystem.loadScript(scriptPath, script, model, modelName);
            }
        }
    }

    private static Path rootDirectory(Path packPath) {
        if (packPath == null) {
            return null;
        }
        return Files.isDirectory(packPath) ? packPath : packPath.getParent();
    }

    private static String preprocessScriptIncludesForDirectory(Path scriptFile, Path root) {
        try {
            return preprocessScriptIncludes(
                PackTextDecoder.readText(scriptFile),
                normalize(scriptFile.toString()),
                includePath -> resolveIncludeFromDirectory(scriptFile, root, includePath)
            );
        } catch (Exception e) {
            return safeRead(scriptFile);
        }
    }

    private static String safeRead(Path path) {
        try {
            return PackTextDecoder.readText(path);
        } catch (IOException e) {
            return "";
        }
    }

    private static String preprocessScriptIncludesForZip(ZipFile zipFile, String entryName, String content) {
        return preprocessScriptIncludes(content, normalize(entryName), includePath -> resolveIncludeFromZip(zipFile, entryName, includePath));
    }

    private static String preprocessScriptIncludes(String content, String scriptIdentifier, IncludeResolver resolver) {
        return preprocessScriptIncludes(content, scriptIdentifier, resolver, new HashSet<>());
    }

    private static final Pattern INCLUDE_DIRECTIVE =
            Pattern.compile("(?m)^\\s*//\\s*include\\s*<([^>]+)>\\s*$");

    /** include を単一パスで再帰展開する。循環は空文字で打ち切る。 */
    private static String preprocessScriptIncludes(String content, String scriptIdentifier, IncludeResolver resolver, Set<String> visiting) {
        if (content == null || content.isBlank()) {
            return content;
        }
        if (!visiting.add(scriptIdentifier)) {
            //循環: 内容は循環元で展開済み。空で打ち切る (再スキャンしないので無限ループしない)。
            return "";
        }
        try {
            Matcher matcher = INCLUDE_DIRECTIVE.matcher(content);
            StringBuilder sb = new StringBuilder(content.length());
            while (matcher.find()) {
                String includeTarget = matcher.group(1).trim();
                String replacement = "";
                try {
                    IncludeSource includeSource = resolver.resolve(includeTarget);
                    if (includeSource != null && includeSource.content() != null) {
                        replacement = preprocessScriptIncludes(includeSource.content(), includeSource.identifier(), resolver, visiting);
                    }
                } catch (Exception e) {
                    RealTrainModUnofficial.LOGGER.warn("Failed to resolve include '{}' in {}", includeTarget, scriptIdentifier, e);
                }
                matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement == null ? "" : replacement));
            }
            matcher.appendTail(sb);
            return sb.toString();
        } finally {
            visiting.remove(scriptIdentifier);
        }
    }

    private static IncludeSource resolveIncludeFromDirectory(Path scriptFile, Path root, String includePath) throws IOException {
        String normalizedInclude = normalize(includePath);
        Path parent = scriptFile.getParent();

        if (parent != null) {
            Path relative = parent.resolve(normalizedInclude).normalize();
            if (Files.exists(relative) && Files.isRegularFile(relative)) {
                return new IncludeSource(normalize(relative.toString()), PackTextDecoder.readText(relative));
            }
        }

        if (root != null) {
            Path rootResolved = root.resolve(normalizedInclude).normalize();
            if (Files.exists(rootResolved) && Files.isRegularFile(rootResolved)) {
                return new IncludeSource(normalize(rootResolved.toString()), PackTextDecoder.readText(rootResolved));
            }
            Path found = resolveFilePathInPack(root, normalizedInclude);
            if (found != null) {
                return new IncludeSource(normalize(found.toString()), PackTextDecoder.readText(found));
            }
            // assets/<namespace>/ ルートからの解決(RTM の //include は assets 名前空間相対)。
            try {
                String rel = normalize(root.relativize(scriptFile).toString());
                String assetsRoot = assetsNamespaceRoot(rel);
                if (!assetsRoot.isEmpty()) {
                    Path p = root.resolve(assetsRoot + normalizedInclude).normalize();
                    if (Files.exists(p) && Files.isRegularFile(p)) {
                        return new IncludeSource(normalize(p.toString()), PackTextDecoder.readText(p));
                    }
                }
            } catch (Exception ignored) {
            }
        }

        return null;
    }

    private static IncludeSource resolveIncludeFromZip(ZipFile zipFile, String currentEntryName, String includePath) throws IOException {
        String normalizedInclude = normalize(includePath);
        String current = normalize(currentEntryName);
        String parent = "";
        int slash = current.lastIndexOf('/');
        if (slash >= 0) {
            parent = current.substring(0, slash + 1);
        }

        ZipEntry relative = findEntry(zipFile, parent + normalizedInclude);
        if (relative == null) {
            relative = findEntry(zipFile, normalizedInclude);
        }
        if (relative == null) {
            //親相対/生パスで見つからなければassetsルートから解決
            String assetsRoot = assetsNamespaceRoot(current);
            if (!assetsRoot.isEmpty()) {
                relative = findEntry(zipFile, assetsRoot + normalizedInclude);
            }
        }
        if (relative == null) {
            return null;
        }

        try (InputStream in = zipFile.getInputStream(relative)) {
            return new IncludeSource(normalize(relative.getName()), PackTextDecoder.readText(in));
        }
    }

    private static String normalize(String path) {
        return path == null ? "" : path.replace('\\', '/');
    }

    /** "assets/minecraft/scripts/..." → "assets/minecraft/"。assets 配下でなければ ""。 */
    private static String assetsNamespaceRoot(String entryName) {
        String n = normalize(entryName);
        if (!n.startsWith("assets/")) return "";
        int second = n.indexOf('/', "assets/".length());
        return second >= 0 ? n.substring(0, second + 1) : "";
    }

    @FunctionalInterface
    private interface IncludeResolver {
        IncludeSource resolve(String includePath) throws Exception;
    }

    private record IncludeSource(String identifier, String content) {}

    private static Path findFallbackScriptFile(Path root) throws IOException {
        if (root == null || !Files.exists(root)) return null;
        Path found = null;
        try (var stream = Files.walk(root)) {
            for (Path file : (Iterable<Path>) stream::iterator) {
                if (!Files.isRegularFile(file)) continue;
                String relative = root.relativize(file).toString().replace('\\', '/');
                if (!relative.toLowerCase(Locale.ROOT).contains("/scripts/")) continue;
                if (!relative.toLowerCase(Locale.ROOT).endsWith(".js")) continue;
                if (found != null) {
                    return null;
                }
                found = file;
            }
        }
        return found;
    }

    private static ZipEntry findFallbackScriptEntry(ZipFile zf) {
        if (zf == null) return null;
        ZipEntry fallback = null;
        java.util.Enumeration<? extends ZipEntry> entries = zf.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (entry.isDirectory()) continue;
            String name = entry.getName().replace('\\', '/');
            if (!(name.toLowerCase(Locale.ROOT).contains("/scripts/") && name.toLowerCase(Locale.ROOT).endsWith(".js"))) continue;
            if (fallback != null) {
                return null;
            }
            fallback = entry;
        }
        return fallback;
    }

    /** 画像に中間アルファ(0/255 以外)のピクセルがあれば true (本当の半透明)。cutout の二値アルファは false。 */
    private static boolean hasPartialAlpha(com.mojang.blaze3d.platform.NativeImage img) {
        try {
            if (img.format() != com.mojang.blaze3d.platform.NativeImage.Format.RGBA) {
                return false;
            }
            int w = img.getWidth(), h = img.getHeight();
            int stepX = Math.max(1, w / 128);
            int stepY = Math.max(1, h / 128);
            int sampled = 0, partial = 0;
            for (int y = 0; y < h; y += stepY) {
                for (int x = 0; x < w; x += stepX) {
                    int a = (img.getPixelRGBA(x, y) >>> 24) & 0xFF;
                    sampled++;
                    if (a >= 8 && a <= 247) partial++;
                }
            }
            // 部分アルファの割合が高い=ガラス等の半透明テクスチャ。AA縁だけの車体テクスチャ(数%未満)は
            // 不透明のまま扱い、車体が透けないようにする。3%以上で半透明と判定。
            return sampled > 0 && (partial * 100) >= (sampled * 3);
        } catch (Throwable ignored) {
        }
        return false;
    }

    /** 半透明ピクセルが1つでもあるか(全画素判定)。 */
    private static boolean hasAnyTranslucentPixel(com.mojang.blaze3d.platform.NativeImage img) {
        try {
            if (img.format() != com.mojang.blaze3d.platform.NativeImage.Format.RGBA) {
                return false;
            }
            int w = img.getWidth(), h = img.getHeight();
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int a = (img.getPixelRGBA(x, y) >>> 24) & 0xFF;
                    if (a > 0x00 && a < 0xF0) {
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    /** pass1 マスクの解像度 (縦横)。粗くてよい: 判定は面の UV 範囲を「含むか」だけ。 */
    private static final int PASS1_MASK_RES = 128;

    //小バッチのバッファ経路化は実測悪化のため不採用

    /** pass1で色が出るテクセルの粗いマスクを作る。過大評価側に倒す。 */
    private static java.util.BitSet buildPass1Mask(com.mojang.blaze3d.platform.NativeImage img) {
        try {
            if (img.format() != com.mojang.blaze3d.platform.NativeImage.Format.RGBA) {
                return null;
            }
            int w = img.getWidth();
            int h = img.getHeight();
            if (w <= 0 || h <= 0) {
                return null;
            }
            java.util.BitSet mask = new java.util.BitSet(PASS1_MASK_RES * PASS1_MASK_RES);
            for (int y = 0; y < h; y++) {
                //テクセルが覆うマス範囲を全部塗る(小テクスチャで面が間引かれるのを防ぐ)
                int my0 = y * PASS1_MASK_RES / h;
                int my1 = Math.min(PASS1_MASK_RES - 1, ((y + 1) * PASS1_MASK_RES - 1) / h);
                for (int x = 0; x < w; x++) {
                    int a = (img.getPixelRGBA(x, y) >>> 24) & 0xFF;
                    if (a > 0x00 && a < 0xFF) {
                        int mx0 = x * PASS1_MASK_RES / w;
                        int mx1 = Math.min(PASS1_MASK_RES - 1, ((x + 1) * PASS1_MASK_RES - 1) / w);
                        for (int my = my0; my <= my1; my++) {
                            int row = my * PASS1_MASK_RES;
                            for (int mx = mx0; mx <= mx1; mx++) {
                                mask.set(row + mx);
                            }
                        }
                    }
                }
            }
            return mask.isEmpty() ? null : mask;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** ガラス帯(中間アルファのまとまり)を持つか。カットアウトと区別する。 */
    private static boolean hasGlassBand(com.mojang.blaze3d.platform.NativeImage img) {
        try {
            if (img.format() != com.mojang.blaze3d.platform.NativeImage.Format.RGBA) {
                return false;
            }
            int w = img.getWidth(), h = img.getHeight();
            int stepX = Math.max(1, w / 128);
            int stepY = Math.max(1, h / 128);
            int sampled = 0, band = 0;
            for (int y = 0; y < h; y += stepY) {
                for (int x = 0; x < w; x += stepX) {
                    int a = (img.getPixelRGBA(x, y) >>> 24) & 0xFF;
                    sampled++;
                    if (a >= 32 && a <= 224) band++;
                }
            }
            return sampled > 0 && (band * 1000L) >= (sampled * 15L);
        } catch (Throwable ignored) {
        }
        return false;
    }

    /** 床下蓋用の2x2白テクスチャ(setShaderColor でグレーに着色して使う)。遅延生成・キャッシュ。 */
    private static volatile ResourceLocation whiteTextureLoc;

    private static ResourceLocation getCapWhiteTexture() {
        ResourceLocation loc = whiteTextureLoc;
        if (loc != null) return loc;
        com.mojang.blaze3d.platform.NativeImage img = new com.mojang.blaze3d.platform.NativeImage(2, 2, false);
        for (int y = 0; y < 2; y++) {
            for (int x = 0; x < 2; x++) {
                img.setPixelRGBA(x, y, 0xFFFFFFFF);
            }
        }
        DynamicTexture tex = new DynamicTexture(img);
        loc = ResourceLocation.fromNamespaceAndPath(RealTrainModUnofficial.MODID, "dynamic/white");
        Minecraft.getInstance().getTextureManager().register(loc, tex);
        whiteTextureLoc = loc;
        return loc;
    }

    /** pass0用: 完全不透明ピクセルのみ残す。 */
    private static com.mojang.blaze3d.platform.NativeImage copyOpaqueOnlyAlpha(com.mojang.blaze3d.platform.NativeImage img) {
        int w = img.getWidth(), h = img.getHeight();
        com.mojang.blaze3d.platform.NativeImage dst = new com.mojang.blaze3d.platform.NativeImage(w, h, false);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = img.getPixelRGBA(x, y);            // 0xAABBGGRR (リトルエンディアン)
                int a = (p >>> 24) & 0xFF;
                //本家 renderBodyNormal は glAlphaFunc(GL_EQUAL, 1.0) = alpha が完全に 1.0 (255)
                //のピクセルだけを pass0 で描く。254 以下は全て pass1 (LESS 1.0 + blend) 側。
                int na = a >= 0xFF ? 0xFF : 0x00;
                dst.setPixelRGBA(x, y, (p & 0x00FFFFFF) | (na << 24));
            }
        }
        return dst;
    }

    /** pass1用: α<255のピクセルのみ残す。pass0と正確に相補。 */
    private static com.mojang.blaze3d.platform.NativeImage copyNonOpaqueAlpha(com.mojang.blaze3d.platform.NativeImage img) {
        int w = img.getWidth(), h = img.getHeight();
        com.mojang.blaze3d.platform.NativeImage dst = new com.mojang.blaze3d.platform.NativeImage(w, h, false);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = img.getPixelRGBA(x, y);
                int a = (p >>> 24) & 0xFF;
                int na = (a > 0x00 && a < 0xFF) ? a : 0x00;
                dst.setPixelRGBA(x, y, (p & 0x00FFFFFF) | (na << 24));
            }
        }
        return dst;
    }

    /** ガラス専用上限アルファ(0x73≈0.45)。これ以上の中間アルファ窓もここまで下げて確実に透かす。 */
    private static final int GLASS_MAX_ALPHA = 0x73;

    private static com.mojang.blaze3d.platform.NativeImage copyStainedGlassAlpha(com.mojang.blaze3d.platform.NativeImage img) {
        int w = img.getWidth(), h = img.getHeight();
        com.mojang.blaze3d.platform.NativeImage dst = new com.mojang.blaze3d.platform.NativeImage(w, h, false);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = img.getPixelRGBA(x, y);
                int a = (p >>> 24) & 0xFF;
                int na = (a > 0x00 && a < 0xF0) ? Math.min(a, GLASS_MAX_ALPHA) : 0x00;
                dst.setPixelRGBA(x, y, (p & 0x00FFFFFF) | (na << 24));
            }
        }
        return dst;
    }

    /** ガラス安定方式用テクスチャ。車体=255/窓=上限付き中間α/穴=0。 */
    private static com.mojang.blaze3d.platform.NativeImage copyGlassAlpha(com.mojang.blaze3d.platform.NativeImage img) {
        int w = img.getWidth(), h = img.getHeight();
        com.mojang.blaze3d.platform.NativeImage dst = new com.mojang.blaze3d.platform.NativeImage(w, h, false);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = img.getPixelRGBA(x, y);
                int a = (p >>> 24) & 0xFF;
                int na = a >= 0xF0 ? 0xFF : (a >= 0x1A ? Math.min(a, GLASS_MAX_ALPHA) : 0x00);
                dst.setPixelRGBA(x, y, (p & 0x00FFFFFF) | (na << 24));
            }
        }
        return dst;
    }

    private static TextureInfo registerTextureFromZip(TextureBinding binding, TextureOpener opener) {
        boolean alphaBlendOption = binding.options().contains("alphablend")
            || binding.options().contains("translucent")
            || binding.options().contains("glassalpha");
        try (InputStream in = opener.open(binding.path())) {
            if (in != null) {
                byte[] data = in.readAllBytes();
                com.mojang.blaze3d.platform.NativeImage img = com.mojang.blaze3d.platform.NativeImage.read(new ByteArrayInputStream(data));
                // テクスチャに「中間アルファ(0/255以外)」があれば本当の半透明 (ガラス等)。
                // cutout 用の二値アルファ(車体の穴)と区別し、本当の半透明だけ translucent 扱いにする。
                boolean partialAlpha = hasPartialAlpha(img);
                boolean glassBand = hasGlassBand(img);
                int key = Math.abs(binding.cacheKey().hashCode());
                DynamicTexture tex = new DynamicTexture(img);
                ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(RealTrainModUnofficial.MODID,
                    "dynamic/mqo/" + Integer.toHexString(key));
                Minecraft.getInstance().getTextureManager().register(loc, tex);
                ResourceLocation baseLoc = loc;
                ResourceLocation opaqueLoc = loc;
                ResourceLocation windowLoc = loc;
                if (alphaBlendOption || partialAlpha || glassBand) {
                    com.mojang.blaze3d.platform.NativeImage opaqueImg = copyOpaqueOnlyAlpha(img);
                    DynamicTexture opaqueTex = new DynamicTexture(opaqueImg);
                    opaqueLoc = ResourceLocation.fromNamespaceAndPath(RealTrainModUnofficial.MODID,
                        "dynamic/mqo/" + Integer.toHexString(key) + "_opq");
                    Minecraft.getInstance().getTextureManager().register(opaqueLoc, opaqueTex);
                    //pass1用: 半透明ピクセルだけ残し、不透明部分の再描画を防ぐ
                    com.mojang.blaze3d.platform.NativeImage windowImg = copyNonOpaqueAlpha(img);
                    DynamicTexture windowTex = new DynamicTexture(windowImg);
                    windowLoc = ResourceLocation.fromNamespaceAndPath(RealTrainModUnofficial.MODID,
                        "dynamic/mqo/" + Integer.toHexString(key) + "_win");
                    Minecraft.getInstance().getTextureManager().register(windowLoc, windowTex);
                }
                // 発光解決はサブライトテクスチャがあるときのみ(無条件だと二重描画)
                TextureInfo info = new TextureInfo(baseLoc, resolveLegacyLightTextures(binding, opener), alphaBlendOption || partialAlpha || glassBand, partialAlpha, glassBand, opaqueLoc, windowLoc);
                //割合判定 (3%/1.5%) は運転席窓のような小さなガラス領域を見逃すため、正確な全画素判定で上書き
                info.hasAnyTranslucentPixel = hasAnyTranslucentPixel(img);
                //pass1 で実際に色が出るテクセルの位置。面ごとの提出要否をこれで判定する。
                info.pass1Mask = buildPass1Mask(img);
                return info;
            }
        } catch (Exception e) {
            //★黙って握り潰さない。ここで落ちると 4x4 の白が貼られ、車体の一部が
            //「均一な白い面」になる (923/922 の鼻先など)。原因のパスが分からないと
            //テクスチャの問題なのか描画パスの問題なのか切り分けられない。
            RealTrainModUnofficial.LOGGER.warn("[RTMU] テクスチャを読めませんでした (白で代替): {} ({})",
                binding.path(), e.toString());
            return new TextureInfo(fallbackTexture(), new ResourceLocation[0], false);
        }
        RealTrainModUnofficial.LOGGER.warn("[RTMU] テクスチャが見つかりません (白で代替): {}", binding.path());
        return new TextureInfo(fallbackTexture(), new ResourceLocation[0], false);
    }

    private static ResourceLocation[] resolveLegacyLightTextures(TextureBinding binding, TextureOpener opener) {
        if (binding == null || !binding.hasLightTextures()) {
            return new ResourceLocation[0];
        }
        //★本家 TextureSet は subTextures[pass.id - 2] と<b>位置で</b>引く。
        //  i=0 → LIGHT(室内灯) / i=1 → LIGHT_FRONT(前照灯) / i=2 → LIGHT_BACK(尾灯)
        //以前はここで「見つかったものだけ詰めて」配列を作っていたため、
        //例えば _light0 が無く _light1/_light2 だけある材質で添字が 1 つずつ手前へずれ、
        //前照灯用テクスチャが室内灯パスに回っていた。前照灯・尾灯 (mode 2) では
        //pass3 と pass4 の両方が走るので、同じ面が二重に描かれて「重なって見える」。
        //本家と同じく<b>位置は固定し、無いものは null のまま</b>にする。
        List<String> explicitPaths = binding.lightTexturePaths();
        int count = Math.max(3, explicitPaths.size());
        ResourceLocation[] slots = new ResourceLocation[count];
        for (int i = 0; i < count; i++) {
            String candidate = i < explicitPaths.size() ? explicitPaths.get(i) : deriveLegacyLightTexturePath(binding.path(), i);
            if (candidate == null || candidate.isBlank()) {
                continue;
            }
            slots[i] = tryLoadOptionalTexture(candidate, opener, binding.cacheKey() + "#light" + i);
        }
        //末尾が全部 null なら配列ごと縮める (「発光材質でない」判定を変えないため)
        int last = -1;
        for (int i = 0; i < count; i++) {
            if (slots[i] != null) {
                last = i;
            }
        }
        if (last < 0) {
            return new ResourceLocation[0];
        }
        return java.util.Arrays.copyOf(slots, last + 1);
    }

    private static String deriveLegacyLightTexturePath(String basePath, int index) {
        if (basePath == null || basePath.isBlank()) {
            return "";
        }
        int dot = basePath.lastIndexOf('.');
        if (dot < 0) {
            return basePath + "_light" + index;
        }
        return basePath.substring(0, dot) + "_light" + index + basePath.substring(dot);
    }

    private static ResourceLocation loadOptionalTexture(String path, TextureOpener opener, String cacheKeySuffix) {
        try (InputStream in = opener.open(path)) {
            if (in == null) {
                return fallbackTexture();
            }
            byte[] data = in.readAllBytes();
            com.mojang.blaze3d.platform.NativeImage img = com.mojang.blaze3d.platform.NativeImage.read(new ByteArrayInputStream(data));
            DynamicTexture tex = new DynamicTexture(img);
            ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(
                RealTrainModUnofficial.MODID,
                "dynamic/mqo/" + Integer.toHexString(cacheKeySuffix.hashCode())
            );
            Minecraft.getInstance().getTextureManager().register(loc, tex);
            return loc;
        } catch (Exception ignored) {
            return fallbackTexture();
        }
    }

    private static ResourceLocation tryLoadOptionalTexture(String path, TextureOpener opener, String cacheKeySuffix) {
        try (InputStream in = opener.open(path)) {
            if (in == null) {
                return null;
            }
            byte[] data = in.readAllBytes();
            com.mojang.blaze3d.platform.NativeImage img = com.mojang.blaze3d.platform.NativeImage.read(new ByteArrayInputStream(data));
            DynamicTexture tex = new DynamicTexture(img);
            ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(
                RealTrainModUnofficial.MODID,
                "dynamic/mqo/" + Integer.toHexString(cacheKeySuffix.hashCode())
            );
            Minecraft.getInstance().getTextureManager().register(loc, tex);
            return loc;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean shouldTreatFaceAsTranslucent(TextureInfo textureInfo, String groupName, float[] uvs, int vertexCount, float avgY) {
        if (textureInfo == null) {
            return false;
        }
        if (textureInfo.isTranslucent || textureInfo.hasPartialAlpha || textureInfo.hasGlassBand) {
            return true;
        }
        // RTM packs often mark full-body SL/rod textures as AlphaBlend for cutout holes.
        // Those must stay in the opaque pass or the scripted body disappears.
        return false;
    }

    private static boolean isLegacyTransparentGroupName(String lowerGroupName) {
        if (lowerGroupName == null || lowerGroupName.isBlank()) {
            return false;
        }
        return lowerGroupName.equals("alpha")
            || lowerGroupName.equals("a")
            || lowerGroupName.startsWith("alpha_")
            || lowerGroupName.contains("glass")
            || lowerGroupName.contains("window")
            || lowerGroupName.contains("wind")
            || lowerGroupName.contains("trans")
            || lowerGroupName.contains("light")
            || lowerGroupName.contains("lamp")
            || lowerGroupName.contains("marker");
    }

    private static boolean isWindowsAbsolutePath(String path) {
        if (path == null || path.length() < 2) return false;
        char c = path.charAt(0);
        return ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')) && path.charAt(1) == ':';
    }

    /**
     * 車体の頂点変換を<b>CPU 側に固定</b>するか。
     *
     * <p>静的 VBO の高速経路は {@code mv = ModelView × pose} を CPU で合成して
     * <b>頂点変換を GPU</b>で行うが、発光パスやスクリプト描画は<b>CPU</b>で pose を掛けて提出する。
     * {@code (A·B)·v} と {@code A·(B·v)} は float32 で数 ULP ずれ、同一面でも深度が一致せず z-fighting する。
     *
     * <p>さらに VBO を使うかの判定には<b>フレームごとに変わる条件</b>が含まれる:
     * {@code InteriorLighting.isActive()} (車両位置の明るさ)、
     * {@code groupTransform != null} (座席回転・ドア・パンタが動いている間だけ CPU)、
     * スクリプトの色指定や UV 変換。そのため「動いている間は CPU / 止まると VBO」と
     * 経路が入れ替わり、切り替わった瞬間だけ深度がずれて一瞬重なって見える。
     *
     * <p>本家は変換経路が 1 つしかないのでこの問題自体が存在しない。経路を CPU 側へ固定する。
     */
    private static final boolean PIN_CPU_TRANSFORM = true;

    private static boolean shouldCullModelFaces(Object rawEntity) {
        //replay経路はentityがnullのため、描画中の車両からdoCullingを引く
        Object entity = rawEntity != null ? rawEntity
            : com.portofino.realtrainmodunofficial.client.DeferredTranslucentRenderer.currentVehicle();
        if (entity instanceof TrainEntity train) {
            VehicleDefinition def = VehicleRegistry.getById(train.getVehicleId());
            return def != null && def.isDoCulling();
        }
        //本家 RenderVehicleBase: doCulling はモデル設定に従う (既定 false = 両面描画)。
        //以前は本家系列車 (EntityTrainBase) だけ常に true にしていたが、doCulling=false 前提で
        //作られたパック (片面モデリングの内装等) の面が裏から消える。本家に合わせる。
        if (entity instanceof jp.ngt.rtm.entity.train.EntityTrainBase train) {
            VehicleDefinition def = VehicleRegistry.getById(train.getModelName());
            return def != null && def.isDoCulling();
        }
        //設置オブジェクト (架線/踏切/ポール/信号) も本家は同じ ModelObject.render を通り、
        //doCulling に従う。定義から引く。
        if (entity instanceof com.portofino.realtrainmodunofficial.blockentity.InstalledObjectBlockEntity io) {
            com.portofino.realtrainmodunofficial.installedobject.InstalledObjectDefinition def = io.getDefinition();
            return def != null && def.isDoCulling();
        }
        //既定はfalse(両面)。本家のdoCulling既定に合わせる
        return false;
    }


    private static ResourceLocation fallbackTexture() {
        if (fallbackWhite != null) return fallbackWhite;
        try {
            com.mojang.blaze3d.platform.NativeImage img = new com.mojang.blaze3d.platform.NativeImage(4, 4, false);
            for (int y = 0; y < 4; y++) {
                for (int x = 0; x < 4; x++) {
                    img.setPixelRGBA(x, y, 0xFFFFFFFF);
                }
            }
            DynamicTexture tex = new DynamicTexture(img);
            fallbackWhite = ResourceLocation.fromNamespaceAndPath(RealTrainModUnofficial.MODID, "dynamic/mqo/_white");
            Minecraft.getInstance().getTextureManager().register(fallbackWhite, tex);
        } catch (Exception e) {
            fallbackWhite = TextureManager.INTENTIONAL_MISSING_TEXTURE;
        }
        return fallbackWhite;
    }

    public static ResourceLocation getScriptTexture(String domain, String path, int frameIndex) {
        if (path == null || path.isBlank()) {
            return fallbackTexture();
        }
        ScriptTextureData data = getScriptTextureData(domain, path);
        if (data.frames().isEmpty()) {
            return fallbackTexture();
        }
        int index = Math.floorMod(frameIndex, data.frames().size());
        return data.frames().get(index);
    }

    public static ScriptTextureData getScriptTextureData(String domain, String path) {
        if (path == null || path.isBlank()) {
            return ScriptTextureData.fallback(fallbackTexture());
        }
        String namespace = domain == null || domain.isBlank() ? "minecraft" : domain;
        String normalizedPath = path.replace('\\', '/');
        String cacheKey = namespace + ":" + normalizedPath;
        return SCRIPT_TEXTURE_CACHE.computeIfAbsent(cacheKey, key -> loadScriptTextureData(namespace, normalizedPath));
    }

    public static ResourceLocation getScriptTextureByTick(String domain, String path, double tick, double fps) {
        ScriptTextureData data = getScriptTextureData(domain, path);
        if (data.frames().isEmpty()) {
            return fallbackTexture();
        }
        int index = data.resolveFrameIndex(tick, fps);
        return data.frames().get(index);
    }

    public static ResourceLocation getWhiteTexture() {
        return fallbackTexture();
    }

    private static ScriptTextureData loadScriptTextureData(String domain, String path) {
        try {
            String lower = path.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".gif")) {
                try (InputStream in = openScriptTextureStream(domain, path)) {
                    if (in == null) {
                        return ScriptTextureData.fallback(fallbackTexture());
                    }
                    return registerGifFrames(domain, path, in);
                }
            }
            if (lower.endsWith(".mp4")) {
                byte[] bytes = openScriptTextureBytes(domain, path);
                if (bytes == null || bytes.length == 0) {
                    return ScriptTextureData.fallback(fallbackTexture());
                }
                return registerMp4Frames(domain, path, bytes);
            }
            ScriptTextureData sequence = tryRegisterSequenceFrames(domain, path);
            if (sequence != null) {
                return sequence;
            }
            try (InputStream in = openScriptTextureStream(domain, path)) {
                if (in == null) {
                    return ScriptTextureData.fallback(fallbackTexture());
                }
                BufferedImage image = ImageIO.read(in);
                ResourceLocation frame = registerBufferedImage(domain, path, 0, image);
                return new ScriptTextureData(List.of(frame), List.of(50), image != null ? image.getWidth() : 1, image != null ? image.getHeight() : 1, 20.0D);
            }
        } catch (Exception e) {
            RealTrainModUnofficial.LOGGER.warn("Could not load script texture {}:{}: {}", domain, path, e.getMessage());
            return ScriptTextureData.fallback(fallbackTexture());
        }
    }

    private static InputStream openScriptTextureStream(String domain, String path) throws IOException {
        String normalizedPath = path.replace('\\', '/').replaceFirst("^/+", "");
        String resolvedDomain = domain == null || domain.isBlank() ? "minecraft" : domain;
        ResourceLocation resourceLocation = isVanillaResourcePathSafe(resolvedDomain, normalizedPath)
            ? ResourceLocation.tryBuild(resolvedDomain, normalizedPath)
            : null;
        if (resourceLocation != null) {
            var resource = Minecraft.getInstance().getResourceManager().getResource(resourceLocation);
            if (resource.isPresent()) {
                return resource.get().open();
            }
        }
        String entryName = "assets/" + resolvedDomain + "/" + normalizedPath;
        for (Path packDir : new Path[]{
                Minecraft.getInstance().gameDirectory.toPath().resolve("mods"),
                com.portofino.realtrainmodunofficial.DefaultAssetsFolder.get()}) {
            if (!Files.isDirectory(packDir)) {
                continue;
            }
            try (var files = Files.list(packDir)) {
                for (Path file : files.toList()) {
                    String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
                    if (!name.endsWith(".zip") && !name.endsWith(".jar")) {
                        continue;
                    }
                    ZipFile zip = new ZipFile(file.toFile());
                    ZipEntry entry = findEntry(zip, entryName);
                    if (entry == null) {
                        zip.close();
                        continue;
                    }
                    InputStream raw = zip.getInputStream(entry);
                    return new java.io.FilterInputStream(raw) {
                        @Override
                        public void close() throws IOException {
                            super.close();
                            zip.close();
                        }
                    };
                }
            }
        }
        return null;
    }

    private static boolean isVanillaResourcePathSafe(String namespace, String path) {
        if (namespace == null || path == null || namespace.isBlank() || path.isBlank()) {
            return false;
        }
        for (int i = 0; i < namespace.length(); i++) {
            char c = namespace.charAt(i);
            if (!((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '.')) {
                return false;
            }
        }
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (!((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '.' || c == '/')) {
                return false;
            }
        }
        return true;
    }

    private static byte[] openScriptTextureBytes(String domain, String path) throws IOException {
        try (InputStream in = openScriptTextureStream(domain, path)) {
            return in == null ? null : in.readAllBytes();
        }
    }

    private static ScriptTextureData tryRegisterSequenceFrames(String domain, String path) throws IOException {
        if (!path.contains("%")) {
            return null;
        }
        List<ResourceLocation> frames = new ArrayList<>();
        int width = 1;
        int height = 1;
        for (int i = 0; i < 512; i++) {
            String resolved = String.format(Locale.ROOT, path, i);
            byte[] bytes = openScriptTextureBytes(domain, resolved);
            if (bytes == null || bytes.length == 0) {
                break;
            }
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) {
                break;
            }
            width = image.getWidth();
            height = image.getHeight();
            frames.add(registerBufferedImage(domain, resolved, i, image));
        }
        if (frames.isEmpty()) {
            return null;
        }
        List<Integer> delays = new ArrayList<>(frames.size());
        for (int i = 0; i < frames.size(); i++) {
            delays.add(50);
        }
        return new ScriptTextureData(frames, delays, width, height, 20.0D);
    }

    private static ScriptTextureData registerGifFrames(String domain, String path, InputStream in) throws IOException {
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
        if (!readers.hasNext()) {
            return ScriptTextureData.fallback(fallbackTexture());
        }
        ImageReader reader = readers.next();
        List<ResourceLocation> frames = new ArrayList<>();
        List<Integer> delays = new ArrayList<>();
        int width = 1;
        int height = 1;
        try (ImageInputStream imageInput = ImageIO.createImageInputStream(in)) {
            reader.setInput(imageInput);
            int count = reader.getNumImages(true);
            BufferedImage composed = null;
            java.awt.Graphics2D graphics = null;
            for (int i = 0; i < count; i++) {
                BufferedImage frame = reader.read(i);
                width = frame.getWidth();
                height = frame.getHeight();
                if (composed == null) {
                    composed = new BufferedImage(frame.getWidth(), frame.getHeight(), BufferedImage.TYPE_INT_ARGB);
                    graphics = composed.createGraphics();
                }
                int left = 0;
                int top = 0;
                int delayMs = 50;
                try {
                    Node root = reader.getImageMetadata(i).getAsTree(reader.getImageMetadata(i).getNativeMetadataFormatName());
                    Node desc = findGifMetadataNode(root, "ImageDescriptor");
                    if (desc != null && desc.getAttributes() != null) {
                        Node leftNode = desc.getAttributes().getNamedItem("imageLeftPosition");
                        Node topNode = desc.getAttributes().getNamedItem("imageTopPosition");
                        if (leftNode != null) left = Integer.parseInt(leftNode.getNodeValue());
                        if (topNode != null) top = Integer.parseInt(topNode.getNodeValue());
                    }
                    Node gce = findGifMetadataNode(root, "GraphicControlExtension");
                    if (gce != null && gce.getAttributes() != null) {
                        Node delayNode = gce.getAttributes().getNamedItem("delayTime");
                        if (delayNode != null) {
                            delayMs = Math.max(20, Integer.parseInt(delayNode.getNodeValue()) * 10);
                        }
                    }
                } catch (Exception ignored) {
                }
                graphics.drawImage(frame, left, top, null);
                BufferedImage snapshot = new BufferedImage(composed.getWidth(), composed.getHeight(), BufferedImage.TYPE_INT_ARGB);
                snapshot.setData(composed.getData());
                frames.add(registerBufferedImage(domain, path, i, snapshot));
                delays.add(delayMs);
            }
            if (graphics != null) {
                graphics.dispose();
            }
        } finally {
            reader.dispose();
        }
        return new ScriptTextureData(frames, delays, width, height, 20.0D);
    }

    private static ScriptTextureData registerMp4Frames(String domain, String path, byte[] bytes) throws IOException {
        // MP4 support requires optional jcodec library - disabled by default
        return ScriptTextureData.fallback(fallbackTexture());
    }

    private static Node findGifMetadataNode(Node root, String nodeName) {
        if (root == null) {
            return null;
        }
        if (nodeName.equalsIgnoreCase(root.getNodeName())) {
            return root;
        }
        Node child = root.getFirstChild();
        while (child != null) {
            Node found = findGifMetadataNode(child, nodeName);
            if (found != null) {
                return found;
            }
            child = child.getNextSibling();
        }
        return null;
    }

    private static ResourceLocation registerBufferedImage(String domain, String path, int frame, BufferedImage image) {
        if (image == null) {
            return fallbackTexture();
        }
        com.mojang.blaze3d.platform.NativeImage nativeImage = new com.mojang.blaze3d.platform.NativeImage(image.getWidth(), image.getHeight(), true);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getRGB(x, y);
                int a = (argb >>> 24) & 0xFF;
                int r = (argb >>> 16) & 0xFF;
                int g = (argb >>> 8) & 0xFF;
                int b = argb & 0xFF;
                nativeImage.setPixelRGBA(x, y, (a << 24) | (b << 16) | (g << 8) | r);
            }
        }
        String safe = Integer.toHexString((domain + ":" + path + "#" + frame).hashCode());
        ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(RealTrainModUnofficial.MODID, "dynamic/script/" + safe);
        Minecraft.getInstance().getTextureManager().register(loc, new DynamicTexture(nativeImage));
        return loc;
    }

    public record ScriptTextureData(List<ResourceLocation> frames, List<Integer> delaysMs, int width, int height, double defaultFps) {
        public static ScriptTextureData fallback(ResourceLocation texture) {
            return new ScriptTextureData(List.of(texture), List.of(50), 1, 1, 20.0D);
        }

        public int resolveFrameIndex(double tick, double fpsOverride) {
            if (frames.isEmpty()) {
                return 0;
            }
            if (delaysMs.size() == frames.size()) {
                long millis = Math.max(0L, Math.round(tick * 50.0D));
                long total = 0L;
                for (int delay : delaysMs) {
                    total += Math.max(1, delay);
                }
                if (total > 0L) {
                    long wrapped = millis % total;
                    long cursor = 0L;
                    for (int i = 0; i < delaysMs.size(); i++) {
                        cursor += Math.max(1, delaysMs.get(i));
                        if (wrapped < cursor) {
                            return i;
                        }
                    }
                }
            }
            double fps = fpsOverride > 0.0D ? fpsOverride : defaultFps;
            return Math.floorMod((int) Math.floor((tick / 20.0D) * fps), frames.size());
        }
    }

    public static void renderModel(MqoModel model, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        renderModel(model, poseStack, buffer, packedLight, null);
    }

    public static void renderModel(MqoModel model, PoseStack poseStack, MultiBufferSource buffer, int packedLight, Object entity) {
        if (model == null) return;
        model.render(poseStack, buffer, packedLight, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, null, null, entity);
    }

    public static void renderModelPreferScript(MqoModel model, PoseStack poseStack, MultiBufferSource buffer, int packedLight, Object entity) {
        if (model == null) return;
        model.renderPreferScript(poseStack, buffer, packedLight, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, null, null, entity);
    }

    public static void renderModel(MqoModel model, PoseStack poseStack, MultiBufferSource buffer, int packedLight, GroupTransform groupTransform, Object entity) {
        if (model == null) return;
        model.render(poseStack, buffer, packedLight, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, null, groupTransform, entity);
    }

    @FunctionalInterface
    private interface TextureOpener {
        InputStream open(String path) throws Exception;
        default String getPackKey() {
            return "";
        }
    }

    private static final class TextureInfo {
        final ResourceLocation location;
        final ResourceLocation[] emissiveTextures;
        final boolean isTranslucent;
        /** テクスチャに中間アルファ(0/255以外)があるか。true=本当の半透明(ガラス等)。 */
        final boolean hasPartialAlpha;
        /** ガラス帯を持つか。trueなら必ずブレンド描画する。 */
        final boolean hasGlassBand;
        /** RTM pass0(不透明描画)用テクスチャ。車体だけ残し窓は穴。非AlphaBlendは元と同じ。 */
        final ResourceLocation opaqueLocation;
        /** RTM pass1(半透明)用テクスチャ。窓ガラスだけ残し車体は透過。非AlphaBlendは元と同じ。 */
        final ResourceLocation windowLocation;
        /** 半透明ピクセルが1つでもあるか(正確判定で上書きされる)。 */
        boolean hasAnyTranslucentPixel;
        /** pass1で色が出るテクセルの粗いマスク。null=全面描画。 */
        java.util.BitSet pass1Mask;

        TextureInfo(ResourceLocation location, ResourceLocation[] emissiveTextures, boolean isTranslucent) {
            this(location, emissiveTextures, isTranslucent, false, false, location, location);
        }

        TextureInfo(ResourceLocation location, ResourceLocation[] emissiveTextures, boolean isTranslucent, boolean hasPartialAlpha) {
            this(location, emissiveTextures, isTranslucent, hasPartialAlpha, false, location, location);
        }

        TextureInfo(ResourceLocation location, ResourceLocation[] emissiveTextures, boolean isTranslucent, boolean hasPartialAlpha, boolean hasGlassBand) {
            this(location, emissiveTextures, isTranslucent, hasPartialAlpha, hasGlassBand, location, location);
        }

        TextureInfo(ResourceLocation location, ResourceLocation[] emissiveTextures, boolean isTranslucent, boolean hasPartialAlpha, boolean hasGlassBand, ResourceLocation opaqueLocation, ResourceLocation windowLocation) {
            this.location = location;
            this.emissiveTextures = emissiveTextures == null ? new ResourceLocation[0] : emissiveTextures;
            this.isTranslucent = isTranslucent;
            this.hasPartialAlpha = hasPartialAlpha;
            this.hasGlassBand = hasGlassBand;
            this.opaqueLocation = opaqueLocation == null ? location : opaqueLocation;
            this.windowLocation = windowLocation == null ? location : windowLocation;
            this.hasAnyTranslucentPixel = hasPartialAlpha || hasGlassBand;
        }

        ResourceLocation emissiveTextureForPass(int pass) {
            int index = pass - 2;
            if (index < 0 || index >= emissiveTextures.length) {
                return null;
            }
            return emissiveTextures[index];
        }

    }

    private record TextureBinding(String path, Set<String> options, List<String> lightTexturePaths) {
        static TextureBinding parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return new TextureBinding("textures/misc/white.png", Set.of(), List.of());
            }
            int metaIndex = raw.indexOf(TEXTURE_META_SEPARATOR);
            if (metaIndex < 0) {
                return new TextureBinding(raw, Set.of(), List.of());
            }
            String path = raw.substring(0, metaIndex);
            String metadata = raw.substring(metaIndex + TEXTURE_META_SEPARATOR.length());
            if (metadata.isBlank()) {
                return new TextureBinding(path, Set.of(), List.of());
            }
            Set<String> options = new LinkedHashSet<>();
            List<String> lightTexturePaths = new ArrayList<>();
            for (String token : metadata.split(",")) {
                String trimmed = token.trim();
                if (!trimmed.isBlank()) {
                    String lowered = trimmed.toLowerCase(Locale.ROOT);
                    if (lowered.equals("light")
                        || lowered.equals("alphablend")
                        || lowered.equals("translucent")
                        || lowered.equals("glassalpha")) {
                        options.add(lowered);
                    } else if (lowered.equals("onetex") || lowered.equals("one_tex")) {
                        // RTM の "OneTex" フラグ: モデル全体が1テクスチャを共有する → 無視
                    } else {
                        lightTexturePaths.add(trimmed);
                    }
                }
            }
            return new TextureBinding(path, Set.copyOf(options), List.copyOf(lightTexturePaths));
        }

        boolean hasLightTextures() {
            return options.contains("light");
        }

        String cacheKey() {
            if (options.isEmpty() && lightTexturePaths.isEmpty()) {
                return path;
            }
            List<String> metadata = new ArrayList<>(options);
            metadata.addAll(lightTexturePaths);
            return path + TEXTURE_META_SEPARATOR + String.join(",", metadata);
        }
    }

    /** グループ名を受け取り、そのグループをレンダリングするかどうかを返す述語。 */
    @FunctionalInterface
    public interface GroupPredicate {
        boolean shouldRender(String groupName);
    }

    /** グループ名を受け取り、そのグループに対して追加の変換を行う関数。 */
    @FunctionalInterface
    public interface GroupTransform {
        void apply(PoseStack poseStack, String groupName);
        /** 変換しないグループでpush/popを省くための早期判定。 */
        default boolean mayModify(String groupName) {
            return true;
        }
    }

    /** スムージングをオブジェクト単位で適用する(マテリアル境界の継ぎ目防止)。 */
    private static void applyObjectWideSmoothing(Collection<BatchBuilder> builders, boolean smoothing) {
        if (builders == null || builders.isEmpty()) {
            return;
        }
        //smoothing=false でも必ず回す: depthBias 用の「位置共有バイアス法線」は
        //フラットモデルにも必要 (無いと light/display の押し出しで面が割れて隙間が開く)。
        Map<String, List<BatchBuilder>> byObject = new LinkedHashMap<>();
        for (BatchBuilder bb : builders) {
            if (bb != null && !bb.positions.isEmpty()) {
                byObject.computeIfAbsent(bb.groupName, k -> new ArrayList<>()).add(bb);
            }
        }
        for (List<BatchBuilder> cluster : byObject.values()) {
            applySmoothNormalsAcrossBatches(cluster, smoothing);
        }
    }

    /** 1 面 (連続4頂点チャンク)。normal は構築時に入れた面法線のスナップショット。 */
    private record SmoothFace(BatchBuilder builder, int firstVertex, Vector3f normal) {}

    /** 本家の頂点法線計算の忠実移植。面単位で隣接を数え、facet角以下の面法線を加算して正規化。 */
    private static void applySmoothNormalsAcrossBatches(Collection<BatchBuilder> builders, boolean smoothing) {
        if (builders == null || builders.isEmpty()) {
            return;
        }
        List<SmoothFace> faces = new ArrayList<>();
        //陰影用: 本家と同じ「座標が完全に一致する頂点だけを同一視」。
        //押し出し用: 従来どおり 1mm グリッド (下の bias の説明を参照)。
        Map<PosKey, List<SmoothFace>> byExactPosition = new HashMap<>();
        Map<PosKey, List<SmoothFace>> byGridPosition = new HashMap<>();
        for (BatchBuilder builder : builders) {
            if (builder == null || builder.positions.isEmpty()) {
                continue;
            }
            int vertexCount = builder.positions.size() / 8;
            builder.biasNormals = new float[vertexCount * 3];
            for (int first = 0; first + 3 < vertexCount; first += 4) {
                Vector3f normal = new Vector3f(
                    builder.positions.get(first * 8 + 3),
                    builder.positions.get(first * 8 + 4),
                    builder.positions.get(first * 8 + 5)
                );
                if (normal.lengthSquared() > 1.0E-8F) {
                    normal.normalize();
                } else {
                    normal.set(0.0F, 1.0F, 0.0F);
                }
                SmoothFace face = new SmoothFace(builder, first, normal);
                faces.add(face);
                for (int k = 0; k < 4; k++) {
                    int offset = (first + k) * 8;
                    register(byExactPosition, exactPositionKey(builder.positions, offset), face);
                    register(byGridPosition, gridPositionKey(builder.positions, offset), face);
                }
            }
        }
        java.util.stream.Stream<SmoothFace> stream = faces.size() > 4096
            ? faces.parallelStream()
            : faces.stream();
        stream.forEach(face -> {
            //本家: angleCos = cos(smoothingAngle)。facet の値をそのまま使う。
            float cosThreshold = (float) Math.cos(Math.toRadians(face.builder.smoothingAngle));
            for (int k = 0; k < 4; k++) {
                int vi = face.firstVertex + k;
                int o = vi * 8;
                //本家: vec = 自面の法線から開始し、他の面は面法線角が facet 以下なら加算
                Vector3f shade = new Vector3f(face.normal);
                //depthBias の押し出し方向: 同位置の全面の平均 (facet 閾値なし)。
                //閾値ありの法線で押すと硬いエッジで同位置の頂点が別方向に動き、
                //light/display グループの押し出しで面が割れて隙間が開く。
                Vector3f bias = new Vector3f(face.normal);
                List<SmoothFace> exact = byExactPosition.get(exactPositionKey(face.builder.positions, o));
                if (exact != null && smoothing) {
                    for (SmoothFace other : exact) {
                        if (other != face && face.normal.dot(other.normal) >= cosThreshold) {
                            shade.add(other.normal);
                        }
                    }
                }
                List<SmoothFace> grid = byGridPosition.get(gridPositionKey(face.builder.positions, o));
                if (grid != null) {
                    for (SmoothFace other : grid) {
                        if (other != face) {
                            bias.add(other.normal);
                        }
                    }
                }
                if (bias.lengthSquared() > 1.0E-8F) {
                    bias.normalize();
                } else {
                    bias.set(face.normal);
                }
                //読みはスナップショット (SmoothFace.normal) のみなので、書き込みは競合しない
                face.builder.biasNormals[vi * 3] = bias.x;
                face.builder.biasNormals[vi * 3 + 1] = bias.y;
                face.builder.biasNormals[vi * 3 + 2] = bias.z;
                if (smoothing) {
                    if (shade.lengthSquared() > 1.0E-8F) {
                        shade.normalize();
                    } else {
                        shade.set(face.normal);
                    }
                    face.builder.positions.set(o + 3, shade.x);
                    face.builder.positions.set(o + 4, shade.y);
                    face.builder.positions.set(o + 5, shade.z);
                }
            }
        });
    }

    /** 頂点の同一判定キー。生ビットなので「完全一致」も「1mm グリッド」も同じ器で扱える。 */
    private record PosKey(int x, int y, int z) {}

    private static void register(Map<PosKey, List<SmoothFace>> map, PosKey key, SmoothFace face) {
        List<SmoothFace> list = map.computeIfAbsent(key, ignored -> new ArrayList<>());
        //本家 !list.contains(face): 同じ面は同じ位置に 1 回だけ (縮退 p2==p3 の二重登録防止)。
        //1 面の 4 頂点は連続して登録するので、重複は必ず末尾に居る。
        if (list.isEmpty() || list.get(list.size() - 1) != face) {
            list.add(face);
        }
    }

    /**
     * 陰影用の頂点キー。<b>本家と同じく座標の完全一致</b>で同一視する。
     * <p>本家 {@code Vertex} は {@code hashCode} が生ビット由来・{@code equals} が 1e-4 許容なので、
     * HashMap 上では実質「ビットが同じ頂点だけが同じ束」になる。これに合わせる。
     * <p>★以前はここが {@code round(x*1000)} の <b>1mm グリッド</b>だった。1mm 未満しか離れていない
     * 別頂点まで束ねてしまうため、ナンバープレートの文字のように細かい面が集まる所で、
     * 本来混ざらない面の法線が混ざって<b>変な位置に影が出ていた</b>。D51 (D51-498_1.mqoz) の
     * body-2 で実測すると、本家アルゴリズムとの差は<b>頂点の 8.28% が 5°以上・最大 58.5°</b>。
     * 完全一致にすると 0% になる (0.1mm グリッドでも 0.19% 残る) ので、グリッドではなく完全一致にする。
     * <p>{@code -0.0F} は {@code 0.0F} へ寄せる。ミラー生成は座標に -1 を掛けるので、鏡像面上の
     * 頂点が {@code -0.0F} になり、寄せないとミラーの継ぎ目で法線が繋がらない
     * (本家は {@code x == 0.0F} の頂点を元の {@code Vertex} のまま使うので繋がる)。
     */
    private static PosKey exactPositionKey(List<Float> positions, int offset) {
        return new PosKey(
            Float.floatToIntBits(zeroNormalized(positions.get(offset))),
            Float.floatToIntBits(zeroNormalized(positions.get(offset + 1))),
            Float.floatToIntBits(zeroNormalized(positions.get(offset + 2))));
    }

    /**
     * depthBias の押し出し方向用の頂点キー。こちらは従来どおり 1mm グリッド。
     * <p>用途が「重なっている面を一緒に押し出して隙間を作らない」で、本家に対応物が無い
     * RTMU 独自処理なので、陰影とは別の粒度で構わない。むしろ完全一致にすると、
     * わずかにズレた頂点が別方向へ押されて light/display グループに隙間が開く。
     */
    private static PosKey gridPositionKey(List<Float> positions, int offset) {
        return new PosKey(
            Math.round(positions.get(offset) * 1000.0F),
            Math.round(positions.get(offset + 1) * 1000.0F),
            Math.round(positions.get(offset + 2) * 1000.0F));
    }

    private static float zeroNormalized(float value) {
        return value == 0.0F ? 0.0F : value;
    }

    private static final class BatchBuilder {
        final int order;
        final String groupName;
        final ResourceLocation texture;
        final ResourceLocation[] emissiveTextures;
        final boolean translucent;
        final int materialId;
        final float smoothingAngle;
        /** マテリアル col の不透明度 (1.0=不透明)。半透明ガラス等は <1。描画時に色のαへ乗算。 */
        float baseAlpha = 1.0F;
        /** マテリアル col の RGB (色タイント)。白以外は描画時に頂点色へ乗算。 */
        float baseColorR = 1.0F;
        float baseColorG = 1.0F;
        float baseColorB = 1.0F;
        /** テクスチャが明確なガラス帯を持つ=本当の半透明。強制カットアウトを免除する。 */
        boolean glassTranslucent = false;
        /** テクスチャに中間アルファのピクセル (ガラス帯/網等) があるか。無ければ pass1 は描く物が無い。 */
        boolean texHasTranslucentPixels = false;
        /** pass1 で色が出るテクセルの粗いマスク ({@link MqoModelLoader#buildPass1Mask})。 */
        java.util.BitSet pass1Mask = null;
        /** depthBias押し出し用の頂点法線(同位置全面の平均)。 */
        float[] biasNormals = null;
        /** RTM pass0(不透明描画)用のアルファテスト相当テクスチャ。 */
        ResourceLocation opaqueTexture = null;
        /** RTM pass1(半透明)用の窓ガラスのみテクスチャ。 */
        ResourceLocation windowTexture = null;
        final List<Float> positions = new ArrayList<>();
        final Set<String> faceSignatures = new HashSet<>();
        float minU = Float.POSITIVE_INFINITY;
        float maxU = Float.NEGATIVE_INFINITY;
        float minV = Float.POSITIVE_INFINITY;
        float maxV = Float.NEGATIVE_INFINITY;

        BatchBuilder(int order, String groupName, ResourceLocation texture, ResourceLocation[] emissiveTextures, int materialId, boolean translucent, float smoothingAngle) {
            this.order = order;
            this.groupName = groupName;
            this.texture = texture;
            this.emissiveTextures = emissiveTextures == null ? new ResourceLocation[0] : emissiveTextures;
            this.materialId = materialId;
            this.translucent = translucent;
            this.smoothingAngle = smoothingAngle;
        }

        void put(Vec3 p, Vector3f n, float u, float v) {
            positions.add((float) p.x);
            positions.add((float) p.y);
            positions.add((float) p.z);
            positions.add(n.x);
            positions.add(n.y);
            positions.add(n.z);
            positions.add(u);
            positions.add(v);
            minU = Math.min(minU, u);
            maxU = Math.max(maxU, u);
            minV = Math.min(minV, v);
            maxV = Math.max(maxV, v);
        }

        boolean markFace(Vec3[] points, float[] uv) {
            StringBuilder builder = new StringBuilder(points.length * 40);
            for (int i = 0; i < points.length; i++) {
                Vec3 point = points[i];
                builder.append(Float.floatToIntBits((float) point.x)).append(':')
                    .append(Float.floatToIntBits((float) point.y)).append(':')
                    .append(Float.floatToIntBits((float) point.z)).append(':');
                if (uv != null && uv.length >= (i + 1) * 2) {
                    builder.append(Float.floatToIntBits(uv[i * 2])).append(':')
                        .append(Float.floatToIntBits(uv[i * 2 + 1]));
                }
                builder.append('|');
            }
            return faceSignatures.add(builder.toString());
        }

        //スムージングは applyObjectWideSmoothing (オブジェクト単位・本家準拠) で済ませてある。
        //バッチ単位でやり直すと材質の境目で法線が切れるので、ここではやらない。
        Batch bake() {
            float[] data = new float[positions.size()];
            for (int i = 0; i < positions.size(); i++) data[i] = positions.get(i);
            float safeMinU = Float.isFinite(minU) ? minU : 0.0F;
            float safeMaxU = Float.isFinite(maxU) ? maxU : 1.0F;
            float safeMinV = Float.isFinite(minV) ? minV : 0.0F;
            float safeMaxV = Float.isFinite(maxV) ? maxV : 1.0F;
            Batch built = new Batch(order, groupName, texture, emissiveTextures, data, data.length / 8, materialId, translucent, safeMinU, safeMaxU, safeMinV, safeMaxV);
            built.baseAlpha = baseAlpha;
            built.baseColorR = baseColorR;
            built.baseColorG = baseColorG;
            built.baseColorB = baseColorB;
            built.glassTranslucent = glassTranslucent;
            built.texHasTranslucentPixels = texHasTranslucentPixels;
            built.pass1Mask = pass1Mask;
            built.biasNormals = biasNormals;
            built.opaqueTexture = opaqueTexture != null ? opaqueTexture : texture;
            built.windowTexture = windowTexture != null ? windowTexture : texture;
            return built;
        }

    }

    public static final class MqoModel {
        /** レガシースクリプトを走らせるパス数。pass2/3/4が発光テクスチャ0/1/2に対応。 */
        private static final int LEGACY_SCRIPT_PASS_COUNT = 5;

        /** 発光パスを描くか。先頭=前照灯、最後尾=尾灯、mode2=両灯。 */
        private static boolean shouldRenderEmissivePass(Object entity, int pass) {
            //本家 RenderVehicleBase.renderBodyLight と同じ条件 (本家系列車)。
            //  i=0(室内灯) mode==0||mode==1 / i=1(前照灯) mode==1&&isFrontEmpty||mode==2
            //  i=2(尾灯)   mode==1&&!isFrontEmpty&&isBackEmpty||mode==2
            //  単行(isSingleTrain かつ前後とも空)は進行方向で前照灯/尾灯を出し分ける。
            if (entity instanceof jp.ngt.rtm.entity.train.EntityTrainBase rtmTrain) {
                int mode = rtmTrain.getTrainStateData(
                        jp.ngt.rtm.entity.train.util.TrainState.TrainStateType.State_Light.id);
                int dir = rtmTrain.getTrainDirection();
                boolean frontEmpty = rtmTrain.getConnectedTrain(dir) == null;
                boolean backEmpty = rtmTrain.getConnectedTrain(1 - dir) == null;
                jp.ngt.rtm.modelpack.cfg.TrainConfig cfg = rtmTrain.getConfig();
                boolean single = cfg != null && cfg.isSingleTrain && frontEmpty && backEmpty;
                return switch (pass) {
                    case 2 -> mode == 0 || mode == 1;
                    case 3 -> single ? (mode == 1 && dir == 0) || mode == 2
                                     : (mode == 1 && frontEmpty) || mode == 2;
                    case 4 -> single ? (mode == 1 && dir == 1) || mode == 2
                                     : (mode == 1 && !frontEmpty && backEmpty) || mode == 2;
                    default -> true;
                };
            }
            if (!(entity instanceof com.portofino.realtrainmodunofficial.entity.TrainEntity train)) {
                //列車以外 (車/設置物) は従来どおり
                return true;
            }
            int mode = train.getLightMode();
            switch (pass) {
                case 2:
                    //室内灯
                    return train.isInteriorLightOn();
                case 3:
                    //前照灯
                    return mode == 2 || (mode == 1 && train.isLeadingCar());
                case 4:
                    //尾灯
                    return mode == 2 || (mode == 1 && !train.isLeadingCar() && train.isTrailingCar());
                default:
                    return true;
            }
        }
        private final List<Batch> batches;
        private final Map<String, List<Batch>> batchesByNormalizedGroup;

        private final ScriptModel scriptModel;
        private final Map<String, List<float[]>> groupQuadCornerCache = new ConcurrentHashMap<>();
        private final Map<String, net.minecraft.world.phys.Vec3> groupCenterCache = new ConcurrentHashMap<>();

        // 床下の蓋(下向き面)用。車体シェルの底Y・XZ範囲を遅延計算してキャッシュ。
        // 片面表示のままだと開いた底から中の暗い空間が透けて黒く見えるため、底に下向きの
        // グレー板を1枚足して塞ぐ(両面表示は使わない=禁止ルール遵守)。
        private boolean voxelModel;
        private volatile float[] bodyCapRect; // {minX, minZ, maxX, maxZ, bottomY}
        private volatile boolean bodyCapComputed;

        public MqoModel(List<Batch> batches, List<ResourceLocation> materialTextures) {
            //★提出順を本家に合わせる: 本家 ModelObject.renderWithTexture は
            //  for (材質 i) { currentMatId = i; renderer.render(...) }
            //で<b>材質が外側</b>。RTMU は (グループ, 材質) の初出順に持っていたので
            //実質「グループが外側」で、重なった面の<b>どちらが後に描かれるか</b>が本家と逆になる。
            //深度が同値な重なり面は「後に描いた方が残る」ので、順が違うと本家では見えない面が
            //手前に出る (D51 の「動かず貼り付いた斑」)。材質→初出順で並べ替えて本家と揃える。
            batches = new ArrayList<>(batches);
            batches.sort(java.util.Comparator.<Batch>comparingInt(b -> b.materialId)
                .thenComparingInt(b -> b.order));
            this.batches = batches;
            this.batchesByNormalizedGroup = buildBatchIndex(batches);
            this.scriptModel = new ScriptModel(materialTextures);
        }

        /** 元の (正規化前の) グループ名一覧。レールスクリプトの shouldRenderObject 判定用。 */
        public java.util.Set<String> getOriginalGroupNames() {
            java.util.Set<String> names = new LinkedHashSet<>();
            for (Batch batch : this.batches) {
                if (batch.groupName != null && !batch.groupName.isBlank()) {
                    names.add(batch.groupName);
                }
            }
            return names;
        }

        /** 台車・車輪・パンタ等(車体シェルでない)グループ名か。床下蓋のAABB計算から除外する。 */
        private static boolean isUnderTruckGroup(String lowerGroupName) {
            if (lowerGroupName == null || lowerGroupName.isBlank()) return true;
            return lowerGroupName.contains("bogie") || lowerGroupName.contains("truck")
                || lowerGroupName.contains("daisya") || lowerGroupName.contains("台車")
                || lowerGroupName.contains("wheel") || lowerGroupName.contains("sharin")
                || lowerGroupName.contains("車輪") || lowerGroupName.contains("pant")
                || lowerGroupName.contains("パンタ") || lowerGroupName.contains("rod")
                || lowerGroupName.contains("axle") || lowerGroupName.contains("spring")
                || lowerGroupName.contains("coupler") || lowerGroupName.contains("連結")
                || lowerGroupName.contains("brake");
        }

        /** 車体シェルの {minX,minZ,maxX,maxZ,bottomY} を遅延計算。蓋を持たない(該当面なし)なら null。 */
        private float[] getBodyCapRect() {
            if (bodyCapComputed) return bodyCapRect;
            synchronized (this) {
                if (!bodyCapComputed) {
                    bodyCapRect = computeBodyCapRect();
                    bodyCapComputed = true;
                }
            }
            return bodyCapRect;
        }

        private float[] computeBodyCapRect() {
            float minX = Float.MAX_VALUE, minZ = Float.MAX_VALUE, minY = Float.MAX_VALUE;
            float maxX = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
            boolean any = false;
            for (Batch b : batches) {
                if (b == null || b.data == null || b.vertexCount <= 0) continue;
                if (isUnderTruckGroup(b.groupNameLower)) continue;
                for (int i = 0; i < b.vertexCount; i++) {
                    int o = i * 8;
                    float x = b.data[o], y = b.data[o + 1], z = b.data[o + 2];
                    if (x < minX) minX = x; if (x > maxX) maxX = x;
                    if (z < minZ) minZ = z; if (z > maxZ) maxZ = z;
                    if (y < minY) minY = y;
                    any = true;
                }
            }
            if (!any || maxX <= minX || maxZ <= minZ) return null;
            // 横幅をわずかに内側へ詰める(外板と完全一致だと縁がはみ出して見えるのを防ぐ)。
            float insetX = (maxX - minX) * 0.02F;
            float insetZ = (maxZ - minZ) * 0.02F;
            return new float[]{minX + insetX, minZ + insetZ, maxX - insetX, maxZ - insetZ, minY};
        }

        /** 車体底面にグレー板を描き、底の抜けを塞ぐ。 */
        private void renderBodyBottomCap(PoseStack poseStack, float lightFactor) {
            float[] r = getBodyCapRect();
            if (r == null) return;
            float minX = r[0], minZ = r[1], maxX = r[2], maxZ = r[3], y = r[4];
            Matrix4f mat = poseStack.last().pose();
            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
            RenderSystem.setShaderTexture(0, getCapWhiteTexture());
            // 床下機器に馴染む暗めグレー。lightFactor で昼夜の明るさに追従。
            float g = 0.16F * lightFactor;
            RenderSystem.setShaderColor(g, g, g, 1.0F);
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            RenderSystem.depthMask(true);
            BufferBuilder b = Tesselator.getInstance().begin(
                VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            // 両巻きで2枚(自前の蓋なので両面OK)。どちらの視点からでも見える。
            capVertex(b, mat, minX, y, minZ); capVertex(b, mat, maxX, y, minZ);
            capVertex(b, mat, maxX, y, maxZ); capVertex(b, mat, minX, y, maxZ);
            capVertex(b, mat, minX, y, maxZ); capVertex(b, mat, maxX, y, maxZ);
            capVertex(b, mat, maxX, y, minZ); capVertex(b, mat, minX, y, minZ);
            BufferUploader.drawWithShader(b.buildOrThrow());
        }

        private static void capVertex(BufferBuilder b, Matrix4f mat, float x, float y, float z) {
            float tx = mat.m00()*x + mat.m10()*y + mat.m20()*z + mat.m30();
            float ty = mat.m01()*x + mat.m11()*y + mat.m21()*z + mat.m31();
            float tz = mat.m02()*x + mat.m12()*y + mat.m22()*z + mat.m32();
            b.addVertex(tx, ty, tz).setUv(0.5F, 0.5F).setColor(255, 255, 255, 255);
        }

        /** 床下の蓋をMultiBufferSource経由で描く(シェーダ有効時)。 */
        private void renderBodyBottomCapBuffered(PoseStack poseStack, MultiBufferSource buffer, int packedLight, int overlay) {
            float[] r = getBodyCapRect();
            if (r == null) return;
            float minX = r[0], minZ = r[1], maxX = r[2], maxZ = r[3], y = r[4];
            PoseStack.Pose pose = poseStack.last();
            Matrix4f mat = pose.pose();
            VertexConsumer vc = buffer.getBuffer(RenderType.entitySolid(getCapWhiteTexture()));
            int gray = 0x29; // 暗めグレー(41) アルベド。シェーダのライティングで陰影が付く。
            // 下向き(-Y)の面を両巻きで2枚(自前の蓋なので両面OK)。
            capVertexBuf(vc, mat, minX, y, minZ, gray, packedLight, overlay, 0, -1, 0);
            capVertexBuf(vc, mat, maxX, y, minZ, gray, packedLight, overlay, 0, -1, 0);
            capVertexBuf(vc, mat, maxX, y, maxZ, gray, packedLight, overlay, 0, -1, 0);
            capVertexBuf(vc, mat, minX, y, maxZ, gray, packedLight, overlay, 0, -1, 0);
            capVertexBuf(vc, mat, minX, y, maxZ, gray, packedLight, overlay, 0, 1, 0);
            capVertexBuf(vc, mat, maxX, y, maxZ, gray, packedLight, overlay, 0, 1, 0);
            capVertexBuf(vc, mat, maxX, y, minZ, gray, packedLight, overlay, 0, 1, 0);
            capVertexBuf(vc, mat, minX, y, minZ, gray, packedLight, overlay, 0, 1, 0);
        }

        private static void capVertexBuf(VertexConsumer vc, Matrix4f mat, float x, float y, float z,
                                          int gray, int packedLight, int overlay, float nx, float ny, float nz) {
            vc.addVertex(mat, x, y, z)
                .setColor(gray, gray, gray, 255)
                .setUv(0.5F, 0.5F)
                .setOverlay(overlay)
                .setLight(packedLight)
                .setNormal(nx, ny, nz);
        }

        long estimateMemoryBytes() {
            long bytes = 512L;
            bytes += (long) batches.size() * 160L;
            for (Batch batch : batches) {
                bytes += 64L;
                bytes += (long) batch.data.length * Float.BYTES;
                if (batch.groupName != null) {
                    bytes += (long) batch.groupName.length() * 2L;
                }
            }
            bytes += (long) scriptModel.textures.length * 64L;
            return bytes;
        }

        private static Map<String, List<Batch>> buildBatchIndex(List<Batch> batches) {
            Map<String, List<Batch>> index = new HashMap<>();
            for (Batch batch : batches) {
                String key = normalizeBatchGroupName(batch.groupName);
                index.computeIfAbsent(key, ignored -> new ArrayList<>()).add(batch);
            }
            return index;
        }

        private static String normalizeBatchGroupName(String groupName) {
            return groupName == null ? "" : groupName.trim().toLowerCase(Locale.ROOT);
        }

        private ScriptEngine scriptEngine;
        private TrainScriptSystem.ScriptModelRenderer scriptRenderer;
        private Boolean hasLegacyRenderFunction;
        private boolean legacyScriptDisabled;
        private int legacyScriptFailureCount;
        private final boolean[] observedLegacyPassActivity = new boolean[LEGACY_SCRIPT_PASS_COUNT];
        private int legacyPassObservationMask;
        // pass を最後に観測してから経過した呼び出し数。
        // 一定値毎に強制再観測することで、ライト ON 等の状態変化時に
        // skip を解除する。
        private final int[] passSinceRecheck = new int[LEGACY_SCRIPT_PASS_COUNT];
        private static final int PASS_RECHECK_INTERVAL = 40;  // 約1秒で再観測

        public void setScriptEngine(ScriptEngine engine, TrainScriptSystem.ScriptModelRenderer renderer) {
            this.scriptEngine = engine;
            this.scriptRenderer = renderer;
            this.hasLegacyRenderFunction = null;
            this.legacyScriptDisabled = false;
            this.legacyScriptFailureCount = 0;
            this.legacyPassObservationMask = 0;
            java.util.Arrays.fill(this.observedLegacyPassActivity, false);
        }

        public void setScriptEngine(Object engine) {
            if (engine instanceof ScriptEngine scriptEngine) {
                setScriptEngine(scriptEngine, null);
            }
        }

        public ScriptEngine getScriptEngine() {
            return scriptEngine;
        }

        public boolean hasRenderScript() {
            return scriptEngine != null && !legacyScriptDisabled;
        }

        public ScriptModel getScriptModel() {
            return scriptModel;
        }

        /** AABB {minX,minY,minZ,maxX,maxY,maxZ} を全頂点から計算。モデルが空なら単位ボックス。 */
        public float[] computeBounds() {
            float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
            float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
            for (Batch b : batches) {
                if (b == null || b.data == null) continue;
                for (int i = 0; i < b.vertexCount; i++) {
                    int o = i * 8;
                    float x = b.data[o], y = b.data[o + 1], z = b.data[o + 2];
                    if (x < minX) minX = x; if (x > maxX) maxX = x;
                    if (y < minY) minY = y; if (y > maxY) maxY = y;
                    if (z < minZ) minZ = z; if (z > maxZ) maxZ = z;
                }
            }
            if (minX > maxX) return new float[]{-0.5f, 0f, -0.5f, 0.5f, 2f, 0.5f};
            return new float[]{minX, minY, minZ, maxX, maxY, maxZ};
        }

        public boolean hasGroupNamed(String groupName) {
            if (groupName == null || groupName.isBlank()) {
                return false;
            }
            for (Batch batch : batches) {
                if (batch.groupName != null && batch.groupName.equalsIgnoreCase(groupName)) {
                    return true;
                }
            }
            return false;
        }

        public net.minecraft.world.phys.Vec3 getGroupCenter(String groupName) {
            String normalized = normalizeBatchGroupName(groupName);
            if (normalized.isEmpty()) {
                return null;
            }
            return groupCenterCache.computeIfAbsent(normalized, key -> {
                List<Batch> groupBatches = batchesByNormalizedGroup.get(key);
                if (groupBatches == null || groupBatches.isEmpty()) {
                    return null;
                }
                double minX = Double.POSITIVE_INFINITY;
                double minY = Double.POSITIVE_INFINITY;
                double minZ = Double.POSITIVE_INFINITY;
                double maxX = Double.NEGATIVE_INFINITY;
                double maxY = Double.NEGATIVE_INFINITY;
                double maxZ = Double.NEGATIVE_INFINITY;
                for (Batch b : groupBatches) {
                    if (b == null || b.data == null) continue;
                    for (int i = 0; i < b.vertexCount; i++) {
                        int o = i * 8;
                        double x = b.data[o];
                        double y = b.data[o + 1];
                        double z = b.data[o + 2];
                        minX = Math.min(minX, x);
                        minY = Math.min(minY, y);
                        minZ = Math.min(minZ, z);
                        maxX = Math.max(maxX, x);
                        maxY = Math.max(maxY, y);
                        maxZ = Math.max(maxZ, z);
                    }
                }
                if (!Double.isFinite(minX) || !Double.isFinite(maxX)) {
                    return null;
                }
                return new net.minecraft.world.phys.Vec3(
                    (minX + maxX) * 0.5D,
                    (minY + maxY) * 0.5D,
                    (minZ + maxZ) * 0.5D
                );
            });
        }

        /** 指定グループの各クワッド面の4隅座標を返す。 */
        public java.util.List<float[]> getGroupQuadCorners(java.util.Set<String> groupNames) {
            java.util.List<float[]> out = new java.util.ArrayList<>();
            if (groupNames == null || groupNames.isEmpty()) return out;
            java.util.Set<String> norm = new java.util.HashSet<>();
            for (String g : groupNames) {
                if (g != null && !g.isBlank()) norm.add(normalizeBatchGroupName(g));
            }
            if (norm.isEmpty()) {
                return out;
            }
            String cacheKey = String.join(",", new java.util.TreeSet<>(norm));
            List<float[]> cached = groupQuadCornerCache.get(cacheKey);
            if (cached != null) {
                return cached;
            }
            for (Batch b : batches) {
                if (b == null || b.data == null) continue;
                if (!norm.contains(normalizeBatchGroupName(b.groupName))) continue;
                for (int i = 0; i + 4 <= b.vertexCount; i += 4) {
                    float[] q = new float[12];
                    for (int c = 0; c < 4; c++) {
                        int o = (i + c) * 8;
                        q[c * 3] = b.data[o];
                        q[c * 3 + 1] = b.data[o + 1];
                        q[c * 3 + 2] = b.data[o + 2];
                    }
                    out.add(q);
                }
            }
            List<float[]> immutable = List.copyOf(out);
            groupQuadCornerCache.put(cacheKey, immutable);
            return immutable;
        }

        /** 車体MQO自身が車輪を持つか。汎用台車の二重描画を防ぐ判定。 */
        public boolean hasOwnWheelGroups() {
            for (Batch batch : batches) {
                String g = batch.groupNameLower;
                if (g == null) continue;
                if (g.startsWith("wheel") || g.contains("動輪") || g.contains("車輪")) {
                    return true;
                }
            }
            return false;
        }

        /**
         * 本体モデルが<b>自前の台車パーツ</b>を持つか。
         * <p>.ngtz の車両は台車を {@code bogieM_F} のようなパーツとしてモデルに内蔵しつつ、
         * JSON では {@code ModelBogie.class} を指していることがある。その場合に組込台車を
         * 重ねると二重になるので、こちらを優先する。
         * <p>{@link #hasOwnWheelGroups()} と分けてあるのは、あちらが「SL の動輪」判定で、
         * 実ファイルの台車モデルを持つ車両にも効いてしまうと本物の台車が消えるため。
         */
        /**
         * ボクセルモデル (.ngto/.ngtz) か。
         * <p>本家 {@code NGTZModel} はパーツごとに<b>自分のグリッドの中央</b>へ寄せて描き、
         * 実際の位置決めはパックのスクリプトが {@code glTranslatef} で行う。
         * そのためスクリプトが走っている間、ベイク済みモデルを別に描いてはいけない
         * (台車やドアが車両の中央に出る / 本体が二重になってチカチカする)。
         */
        public boolean isVoxelModel() {
            return this.voxelModel;
        }

        public boolean hasOwnBogieGroups() {
            for (Batch batch : batches) {
                String g = batch.groupNameLower;
                if (g == null) continue;
                if (g.contains("bogie") || g.contains("daisya") || g.contains("daisha")
                        || g.contains("sharin") || g.contains("台車")) {
                    return true;
                }
            }
            return false;
        }

        /** モデル内の全グループ名 (正規化済み: trim + toLowerCase) を返す。 */
        public java.util.Set<String> getAllNormalizedGroupNames() {
            java.util.Set<String> result = new java.util.LinkedHashSet<>();
            for (Batch batch : batches) {
                if (batch.groupName != null && !batch.groupName.isBlank()) {
                    result.add(batch.groupName.trim().toLowerCase(java.util.Locale.ROOT));
                }
            }
            return result;
        }

        public boolean hasTranslucentBatches() {
            for (Batch batch : batches) {
                if (batch.translucent) {
                    return true;
                }
            }
            return false;
        }

        public boolean hasOpaqueBatches() {
            for (Batch batch : batches) {
                if (!batch.translucent) {
                    return true;
                }
            }
            return false;
        }

        public int getBatchCount() {
            return batches.size();
        }

        public int getTranslucentBatchCount() {
            int count = 0;
            for (Batch batch : batches) {
                if (batch.translucent) {
                    count++;
                }
            }
            return count;
        }

        public int getTotalVertexCount() {
            int count = 0;
            for (Batch batch : batches) {
                count += batch.vertexCount;
            }
            return count;
        }

        public boolean hasLegacyLightTextures() {
            for (Batch batch : batches) {
                if (batch.emissiveTextures.length > 0) {
                    return true;
                }
            }
            return false;
        }

        public void renderNamedGroups(PoseStack poseStack, MultiBufferSource buffer, int packedLight, int overlay,
                                      boolean translucent, Set<String> normalizedGroupNames,
                                      TrainScriptSystem.ScriptModelRenderer scriptRenderer) {
            renderNamedGroups(poseStack, buffer, packedLight, overlay, translucent, normalizedGroupNames,
                scriptRenderer, null);
        }

        /** 通常表示で描かないヘルパーグループか。 */
        /** 常に非表示のヘルパーか(影/ガイドのみ。角度異体は含めない)。 */
        static boolean isShadowHelperGroup(String n) {
            if (n == null || n.isBlank()) {
                return false;
            }
            if (n.contains("影") || n.contains("shadow")) {
                return true;
            }
            //_msは実グループに使われるため除去しない。影は shadow/影/_kage のみ
            if (n.endsWith("_kage") || n.contains("_kage_")) {
                return true;
            }
            return n.endsWith("_guide") || n.endsWith("[obj]") || n.endsWith("_atari") || n.endsWith(" atari");
        }

        static boolean isNonRenderingHelperGroup(String n) {
            if (n == null || n.isBlank()) {
                return false;
            }
            if (n.contains("影") || n.contains("shadow")) {
                return true;
            }
            if (n.endsWith("_ms") || n.endsWith("_kage") || n.contains("_ms_") || n.contains("_kage_")) {
                return true;
            }
            if (n.endsWith("_guide") || n.endsWith("[obj]") || n.endsWith("_atari") || n.endsWith(" atari")) {
                return true;
            }
            String s = n.endsWith("(mx)") ? n.substring(0, n.length() - 4) : n;
            int dash = s.lastIndexOf('-');
            if (dash <= 0 || dash == s.length() - 1) {
                return false;
            }
            for (int i = dash + 1; i < s.length(); i++) {
                if (!Character.isDigit(s.charAt(i))) {
                    return false;
                }
            }
            try {
                return Integer.parseInt(s.substring(dash + 1)) >= 10;
            } catch (NumberFormatException e) {
                return false;
            }
        }

        /**
         * @param excludedGroups 本家 ResourceState.exclusionParts (正規化済み)。
         *                       RTM 標準スクリプトはドアの開閉をこれで表現する
         *                       (開いた側の扉パーツを除外リストに入れて消す)。null/空なら除外なし。
         */
        public void renderNamedGroups(PoseStack poseStack, MultiBufferSource buffer, int packedLight, int overlay,
                                      boolean translucent, Set<String> normalizedGroupNames,
                                      TrainScriptSystem.ScriptModelRenderer scriptRenderer,
                                      Set<String> excludedGroups) {
            if (normalizedGroupNames == null || normalizedGroupNames.isEmpty()) {
                return;
            }
            long secStart = com.portofino.realtrainmodunofficial.client.ClientRenderProfiler.sec();
            List<Batch> ordered = renderListCache.get(normalizedGroupNames);
            if (ordered == null) {
                Set<Batch> selected = new LinkedHashSet<>();
                for (String name : normalizedGroupNames) {
                    //ヘルパー除外は影のみ(角度異体まで消すと本体を巻き込む)
                    if (isShadowHelperGroup(name)) {
                        continue;
                    }
                    List<Batch> batches = batchesByNormalizedGroup.get(name);
                    if (batches != null && !batches.isEmpty()) {
                        selected.addAll(batches);
                    }
                }
                if (selected.isEmpty()) {
                    ordered = java.util.Collections.emptyList();
                } else {
                    ordered = new ArrayList<>(selected);
                    ordered.sort(java.util.Comparator.comparingInt(batch -> batch.order));
                }
                renderListCache.put(normalizedGroupNames, ordered);
            }
            if (ordered.isEmpty()) {
                com.portofino.realtrainmodunofficial.client.ClientRenderProfiler.secEnd(
                    com.portofino.realtrainmodunofficial.client.ClientRenderProfiler.SEC_GROUPS, secStart);
                return;
            }
            Object entity = scriptRenderer != null ? scriptRenderer.getCurrentEntity() : null;
            boolean fullbright = false;
            //door_*は除外を無視して常に描く(スライドで開閉を表現)
            GroupPredicate exclusionFilter = (excludedGroups == null || excludedGroups.isEmpty())
                ? null
                : groupName -> {
                    String g = groupName == null ? "" : groupName.trim().toLowerCase(Locale.ROOT);
                    if (g.startsWith("door")) {
                        return true; //ドアは除外せず常に描く (スライドで開閉、開閉アニメを対称に)
                    }
                    return !excludedGroups.contains(g);
                };
            renderSelectedBatches(ordered, poseStack, buffer, packedLight, overlay, translucent,
                exclusionFilter, null, scriptRenderer, entity, fullbright);
            com.portofino.realtrainmodunofficial.client.ClientRenderProfiler.secEnd(
                com.portofino.realtrainmodunofficial.client.ClientRenderProfiler.SEC_GROUPS, secStart);
        }

        // Set→ソート済みBatchリストのキャッシュ。毎フレームの確保とsortを省く
        private final java.util.IdentityHashMap<Set<String>, List<Batch>> renderListCache = new java.util.IdentityHashMap<>();


        /** 本家LIGHTパス相当。legacyPass 2=室内灯/3=前照灯/4=尾灯。 */
        public void renderNamedGroupsEmissive(PoseStack poseStack, MultiBufferSource buffer,
                                              int packedLight, int overlay,
                                              Set<String> normalizedGroupNames, int legacyPass) {
            renderNamedGroupsEmissive(poseStack, buffer, packedLight, overlay, normalizedGroupNames, legacyPass, null);
        }

        /**
         * @param groupTransform ドア/パンタ等の可動部変換。
         *        <p>★本家 {@code BasicVehiclePartsRenderer.render(entity, pass, …)} は
         *        <b>全パスで同じ変換</b>を通す。ここに渡さないと、発光パスだけ可動部が
         *        閉じた位置に描かれる — 室内灯を点けた E131 で「ドアは開いているのに
         *        内装のドアが開かない」ように見えたのはこれ (光る側だけ元位置に残る)。
         */
        public void renderNamedGroupsEmissive(PoseStack poseStack, MultiBufferSource buffer,
                                              int packedLight, int overlay,
                                              Set<String> normalizedGroupNames, int legacyPass,
                                              GroupTransform groupTransform) {
            if (normalizedGroupNames == null || normalizedGroupNames.isEmpty() || legacyPass < 2) {
                return;
            }
            long secStart = com.portofino.realtrainmodunofficial.client.ClientRenderProfiler.sec();
            // 本家 jp.ngt.rtm.render.ModelObject#render の発光ブロック:
            //     GLHelper.disableLighting();
            //     GLHelper.setLightmapMaxBrightness();
            //     this.renderWithTexture(entity, 2, par3);
            //★本家 RenderVehicleBase.renderBodyLight の忠実移植。
            //  isLightON = (i > 0) のときだけ
            //      disableLighting() + glEnable(GL_BLEND) + glBlendFunc(SRC_ALPHA, ONE_MINUS_SRC_ALPHA)
            //      + glColor4f(1,1,1,0.8) + setLightmapMaxBrightness()
            //  i == 0 (RenderPass.LIGHT) は<b>状態を一切変えない</b> = 通常ライティング・
            //  周囲の明るさ・ブレンド無し・深度書き込みあり。
            //  本家は頂点を押し出さないので depthBias も持たない。
            //  「LIGHT は NORMAL/TRANSPARENT の後」という順序は、1.21 の遅延バッファでは
            //  提出順では保証されないので renderReal 側でパスの区切りに flush を入れて担保する。
            boolean isLightON = legacyPass > 2;
            int light = isLightON ? net.minecraft.client.renderer.LightTexture.FULL_BRIGHT : packedLight;
            float alpha = isLightON ? 0.8F : 1.0F;
            boolean cullFaces = shouldCullModelFaces(null);
            float[] normalOut = new float[3];
            for (String name : normalizedGroupNames) {
                List<Batch> groupBatches = batchesByNormalizedGroup.get(name);
                if (groupBatches == null || groupBatches.isEmpty()) {
                    continue;
                }
                //可動部は変換を掛けた行列で描く (グループごとに push/pop)
                String rawGroupName = groupBatches.get(0).groupName;
                boolean willTransform = groupTransform != null && groupTransform.mayModify(rawGroupName);
                if (willTransform) {
                    poseStack.pushPose();
                    groupTransform.apply(poseStack, rawGroupName);
                }
                try {
                PoseStack.Pose pose = poseStack.last();
                Matrix4f mat = pose.pose();
                Matrix3f norm = pose.normal();
                for (Batch batch : groupBatches) {
                    ResourceLocation tex = batch.emissiveTextureForPass(legacyPass);
                    if (tex == null) {
                        //本家 renderWithTexture: `if (!this.textures[i].doLighting) continue;`
                        //Light フラグの無い材質は LIGHT パスで<b>単に飛ばす</b>。
                        //以前はここで「グループ名に light を含む」batch を素テクスチャの不透明で
                        //描き直していたが、本家に無い処理で、消灯時にもライトが点いて見える原因だった。
                        continue;
                    }
                    //★本家 RenderVehicleBase.renderBodyLight の忠実移植。
                    //  isLightON = (i > 0) のときだけ disableLighting + glEnable(GL_BLEND)
                    //  + glColor4f(1,1,1,0.8) + setLightmapMaxBrightness を掛ける。
                    //  i == 0 (RenderPass.LIGHT) は<b>何も変更せず</b>描く = 通常ライティング・
                    //  ブレンド無し・depthMask は既定の ON、つまり単なる不透明テクスチャ差し替え。
                    //  基本テクスチャが透明で実体が ***_light0.png 側にある面 (0系の鼻) は
                    //  この不透明描画が深度を書くことで、後から描くレールに突き抜かれなくなる。
                    //描画順は renderReal の flush で本家の即時描画と同じに保証されるので、
                    //深度をずらす細工は要らない。本家と同じ描画状態だけを使う。
                    //★前照灯/尾灯 (i>0) はポリゴンオフセット付き。同じ頂点に複数のライトパスが
                    //重なると 1.21 では勝敗が定まらず、ポリゴンが重なったように見えるため。
                    VertexConsumer vc = isLightON
                        ? buffer.getBuffer(com.portofino.realtrainmodunofficial.client.render
                            .RtmuRenderTypes.emissiveBlendLayered(tex, legacyPass, cullFaces))
                        //室内灯パスも本体と同一平面なので、深度だけずらしてちらつきを止める
                        //(本家は即時描画で「後勝ち」が保証されるため不要な処理)。
                        : buffer.getBuffer(com.portofino.realtrainmodunofficial.client.render
                            .RtmuRenderTypes.emissiveCutoutLayered(tex, cullFaces));
                    for (int i = 0; i < batch.vertexCount; i++) {
                        int o = i * 8;
                        float x = batch.data[o], y = batch.data[o + 1], z = batch.data[o + 2];
                        float nx = batch.data[o + 3], ny = batch.data[o + 4], nz = batch.data[o + 5];
                        float u = batch.data[o + 6], v = batch.data[o + 7];
                        //★本家は発光パスで頂点を押し出さない。手前に出す必要は
                        //ポリゴンオフセット (RenderType 側) で満たしている。
                        float tnx = norm.m00() * nx + norm.m10() * ny + norm.m20() * nz;
                        float tny = norm.m01() * nx + norm.m11() * ny + norm.m21() * nz;
                        float tnz = norm.m02() * nx + norm.m12() * ny + norm.m22() * nz;
                        normalizeNormal(tnx, tny, tnz, normalOut);
                        vc.addVertex(mat, x, y, z)
                            .setColor(1.0F, 1.0F, 1.0F, alpha)
                            .setUv(u, v)
                            .setOverlay(overlay)
                            .setLight(light)
                            .setNormal(normalOut[0], normalOut[1], normalOut[2]);
                    }
                }
                } finally {
                    if (willTransform) {
                        poseStack.popPose();
                    }
                }
            }
            com.portofino.realtrainmodunofficial.client.ClientRenderProfiler.secEnd(
                com.portofino.realtrainmodunofficial.client.ClientRenderProfiler.SEC_GROUPS, secStart);
        }

        /** 夜間グローの強度(0..1)。周囲光が暗いほど強い。 */
        private static float glowDarkness(int packedLight) {
            float skyMul = 1.0F;
            net.minecraft.client.multiplayer.ClientLevel level = Minecraft.getInstance().level;
            if (level != null) {
                skyMul = 1.0F - Math.min(11, level.getSkyDarken()) / 11.0F;
            }
            int block = net.minecraft.client.renderer.LightTexture.block(packedLight);
            int sky = net.minecraft.client.renderer.LightTexture.sky(packedLight);
            float ambient = Math.max(block, sky * skyMul) / 15.0F;
            float d = 1.0F - Math.min(1.0F, ambient);
            return d * d;
        }

        /** グローシェル拡大量の上限。大きな面のにじみ過ぎを防ぐ。 */
        private static final float GLOW_MAX_EXPAND = 0.35F;

        /** 発光クアッドを拡大したシェルを加算合成する(疑似ブルーム)。 */
        private static void emitGlowShell(VertexConsumer vc, Matrix4f mat, Matrix3f norm, Batch batch,
                                          float scale, float lift, float alpha, int overlay, float[] normalOut) {
            for (int q = 0; q + 3 < batch.vertexCount; q += 4) {
                int base = q * 8;
                float cx = 0.0F, cy = 0.0F, cz = 0.0F;
                for (int i = 0; i < 4; i++) {
                    int o = base + i * 8;
                    cx += batch.data[o];
                    cy += batch.data[o + 1];
                    cz += batch.data[o + 2];
                }
                cx *= 0.25F;
                cy *= 0.25F;
                cz *= 0.25F;
                for (int i = 0; i < 4; i++) {
                    int o = base + i * 8;
                    float dx = batch.data[o] - cx;
                    float dy = batch.data[o + 1] - cy;
                    float dz = batch.data[o + 2] - cz;
                    float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                    //拡大は倍率と絶対上限の小さい方 (巨大な発光帯が筋状に伸びるのを防ぐ)
                    float grow = Math.min(dist * (scale - 1.0F), GLOW_MAX_EXPAND);
                    float factor = dist > 1.0E-4F ? (dist + grow) / dist : 1.0F;
                    float nx = batch.data[o + 3], ny = batch.data[o + 4], nz = batch.data[o + 5];
                    float inv = (float) (1.0D / Math.sqrt(Math.max(1.0E-8F, nx * nx + ny * ny + nz * nz)));
                    float x = cx + dx * factor + nx * inv * lift;
                    float y = cy + dy * factor + ny * inv * lift;
                    float z = cz + dz * factor + nz * inv * lift;
                    float tnx = norm.m00() * nx + norm.m10() * ny + norm.m20() * nz;
                    float tny = norm.m01() * nx + norm.m11() * ny + norm.m21() * nz;
                    float tnz = norm.m02() * nx + norm.m12() * ny + norm.m22() * nz;
                    normalizeNormal(tnx, tny, tnz, normalOut);
                    vc.addVertex(mat, x, y, z)
                        .setColor(1.0F, 1.0F, 1.0F, alpha)
                        .setUv(batch.data[o + 6], batch.data[o + 7])
                        .setOverlay(overlay)
                        .setLight(net.minecraft.client.renderer.LightTexture.FULL_BRIGHT)
                        .setNormal(normalOut[0], normalOut[1], normalOut[2]);
                }
            }
        }


        /** このモデルに発光 (Light) マテリアルのバッチが 1 つでもあるか。 */
        public boolean hasEmissiveBatches() {
            for (Batch b : batches) {
                if (b.emissiveTextures.length > 0) {
                    return true;
                }
            }
            return false;
        }

        /**
         * 全グループの発光パス。本家 RenderVehicleBase.renderBodyLight は
         * Light マテリアルの面を ***_light0/1/2.png へ差し替えて車体ごと描き直す処理で、
         * スクリプトの有無とは無関係に走る。RTMU はこれをスクリプト経路にしか通しておらず、
         * rendererPath を持たないパック (100系等) でライトを点けても何も出なかった。
         */
        public void renderAllGroupsEmissive(PoseStack poseStack, MultiBufferSource buffer,
                                            int packedLight, int overlay, int legacyPass,
                                            GroupPredicate groupFilter) {
            renderAllGroupsEmissive(poseStack, buffer, packedLight, overlay, legacyPass, groupFilter, null);
        }

        public void renderAllGroupsEmissive(PoseStack poseStack, MultiBufferSource buffer,
                                            int packedLight, int overlay, int legacyPass,
                                            GroupPredicate groupFilter, GroupTransform groupTransform) {
            if (legacyPass < 2 || batchesByNormalizedGroup.isEmpty()) {
                return;
            }
            Set<String> names = groupFilter == null
                ? batchesByNormalizedGroup.keySet()
                : batchesByNormalizedGroup.entrySet().stream()
                    .filter(e -> !e.getValue().isEmpty()
                        && groupFilter.shouldRender(e.getValue().get(0).groupName))
                    .map(Map.Entry::getKey)
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            renderNamedGroupsEmissive(poseStack, buffer, packedLight, overlay, names, legacyPass, groupTransform);
        }

        /** このモデルのGPU VBOを全て解放する(キャッシュ追い出し時)。 */
        public void closeGpuResources() {
            if (com.mojang.blaze3d.systems.RenderSystem.isOnRenderThread()) {
                for (Batch b : batches) {
                    b.closeVbosNow();
                }
            } else {
                com.mojang.blaze3d.systems.RenderSystem.recordRenderCall(() -> {
                    for (Batch b : batches) {
                        b.closeVbosNow();
                    }
                });
            }
        }

        private Boolean cachedHasLightGroups;

        /** 旧式ライト(名前にlightを含む)グループか。 */
        static boolean isLightGroupName(String lowerGroupName) {
            return lowerGroupName != null && lowerGroupName.contains("light");
        }

        /** 旧式ライトグループを持つか。trueならLIGHTパスを回す。 */
        public boolean hasScriptLightGroups() {
            Boolean c = cachedHasLightGroups;
            if (c == null) {
                boolean found = false;
                for (Batch b : batches) {
                    if (isLightGroupName(b.groupNameLower)) {
                        found = true;
                        break;
                    }
                }
                c = found;
                cachedHasLightGroups = c;
            }
            return c;
        }

        /** 旧式ライトを不透明ジオメトリとして描く(ガラス経路には触れない)。 */



        private boolean executeScript(PoseStack poseStack, MultiBufferSource buffer, int packedLight, int overlay, int pass, Object entity) {
            if (scriptEngine == null || legacyScriptDisabled) {
                return false;
            }
            try {
                if (scriptRenderer != null) {
                    scriptRenderer.setRenderContext(poseStack, buffer, packedLight, overlay, pass, entity);
                }
                // RTM スクリプトは model.renderPart("...") で部品を描画する。
                // ScriptModel が現在の renderer を知らないと描画できないため、毎フレーム差し替える。
                if (scriptModel != null && scriptRenderer != null) {
                    scriptModel.setActiveRenderer(scriptRenderer);
                }
                //★スクリプトの実行回数は RTMU では制御しない (スクリプト任せ)。
                //以前はここで記録済み描画の再生キャッシュを使い、状態シグネチャが変わらない限り
                //JS を呼ばずに済ませていた。しかしシグネチャは RTMU が知っている状態
                //(速度/ドア/パンタ等) しか見られず、スクリプトが自前で進めるアニメは検出できない。
                //その結果 SR1 のパンタのように「スクリプトが動かしているのに静止扱い」となり、
                //再記録間隔ぶんカクついていた。毎フレーム素直に実行する。
                if (scriptEngine instanceof ScriptEngine engine) {
                    int renderedBatchesBefore = scriptRenderer != null ? scriptRenderer.getRenderedBatchCount() : 0;
                    // RTMレガシースクリプトは entity.getBogie(n) / entity.field_70177_z 等
                    // LegacyScriptExecutor のAPIを前提としている。生の TrainEntity ではなく
                    // LegacyScriptExecutor でラップして渡す。
                    Object scriptEntity = scriptRenderer != null ? scriptRenderer.scriptEntityFor(entity) : entity;
                    engine.put("poseStack", null);
                    engine.put("pass", pass);
                    engine.put("entity", scriptEntity);
                    engine.put("executer", scriptEntity);
                    engine.put("executor", scriptEntity);
                    if (hasLegacyRenderFunction == null) {
                        Object renderType = engine.eval("typeof render");
                        hasLegacyRenderFunction = "function".equals(renderType)
                            || (renderType != null && "function".equals(renderType.toString()));
                    }
                    if (Boolean.TRUE.equals(hasLegacyRenderFunction)) {
                        boolean rendered;
                        if (engine instanceof Invocable invocable) {
                            try {
                                invocable.invokeFunction("render", scriptEntity, pass, null);
                                rendered = scriptRenderer == null || scriptRenderer.getRenderedBatchCount() > renderedBatchesBefore;
                                if (scriptRenderer != null) scriptRenderer.endRecording(rendered);
                                noteLegacyPassActivity(pass, rendered);
                                return rendered;
                            } catch (NoSuchMethodException ignored) {
                            }
                        }
                        engine.eval("render(entity, pass, null);");
                        rendered = scriptRenderer == null || scriptRenderer.getRenderedBatchCount() > renderedBatchesBefore;
                        if (scriptRenderer != null) scriptRenderer.endRecording(rendered);
                        noteLegacyPassActivity(pass, rendered);
                        return rendered;
                    }
                }
            } catch (Exception e) {
                if (scriptRenderer != null) scriptRenderer.endRecording(false);
                // 例外時も登録済みグループは残す(クリアすると二重描画になる)
                legacyScriptFailureCount++;
                if (legacyScriptFailureCount >= 3) {
                    legacyScriptDisabled = true;
                    if (scriptRenderer != null) {
                        scriptRenderer.clearScriptRegisteredGroups();
                    }
                    RealTrainModUnofficial.LOGGER.warn(
                        "Legacy model script failed on pass {} three times; disabling script and using baked render for this model.",
                        pass, e);
                } else {
                    RealTrainModUnofficial.LOGGER.warn(
                        "Legacy model script failed on pass {} ({}/3 before disabling).",
                        pass, legacyScriptFailureCount, e);
                }
            } finally {
                if (scriptRenderer != null) {
                    // スクリプトが pushMatrix/popMatrix のバランスを崩したまま終了した場合に
                    // matrixDepth を 0 に戻す。残った push は外側の executeScript 呼び出し元の
                    // pushPose/popPose で吸収されるため、ここでは内部カウンタを 0 にするだけ。
                    scriptRenderer.restoreMatrixDepth(0);
                    scriptRenderer.clearRenderContext();
                }
            }
            return false;
        }

        private void noteLegacyPassActivity(int pass, boolean rendered) {
            if (pass < 0 || pass >= LEGACY_SCRIPT_PASS_COUNT) {
                return;
            }
            legacyPassObservationMask |= 1 << pass;
            if (rendered) {
                observedLegacyPassActivity[pass] = true;
            }
        }


        private void renderInternal(PoseStack poseStack, MultiBufferSource buffer, int packedLight, int overlay,
                                    boolean translucent, GroupPredicate groupFilter, GroupTransform groupTransform,
                                    TrainScriptSystem.ScriptModelRenderer scriptRenderer, Object entity) {
            boolean fullbright = false;
            renderSelectedBatches(this.batches, poseStack, buffer, packedLight, overlay, translucent, groupFilter, groupTransform, scriptRenderer, entity, fullbright);
        }

        private void renderSelectedBatches(List<Batch> selectedBatches, PoseStack poseStack, MultiBufferSource buffer, int packedLight, int overlay,
                                           boolean translucent, TrainScriptSystem.ScriptModelRenderer scriptRenderer, Object entity, boolean fullbright) {
            renderSelectedBatches(selectedBatches, poseStack, buffer, packedLight, overlay, translucent, null, null, scriptRenderer, entity, fullbright);
        }

        private void renderSelectedBatches(List<Batch> selectedBatches, PoseStack poseStack, MultiBufferSource buffer, int packedLight, int overlay,
                                           boolean translucent, GroupPredicate groupFilter, GroupTransform groupTransform,
                                           TrainScriptSystem.ScriptModelRenderer scriptRenderer, Object entity, boolean fullbright) {
            // シェーダー(Iris/Oculus)有効時は、フラットな直接GL経路ではなく法線付きの
            // バッファ経路で描画する。直接GL経路は頂点法線スムージングが効かず、影modで
            // 車体がカクついて見えるため(数値は一切変更しない・経路のみ切替)。
            long secLoop = com.portofino.realtrainmodunofficial.client.ClientRenderProfiler.sec();
            if (fullbright && com.portofino.realtrainmodunofficial.client.ShaderCompat.isShaderPackInUse()) {
                fullbright = false;
            }
            // ループ全体で保持する直近値。再設定を skip するため。
            int gr = scriptRenderer != null ? scriptRenderer.getColorRed255()   : 255;
            int gg = scriptRenderer != null ? scriptRenderer.getColorGreen255() : 255;
            int gb = scriptRenderer != null ? scriptRenderer.getColorBlue255()  : 255;
            int ga = scriptRenderer != null ? scriptRenderer.applyAlpha255(255) : 255;
            int lastBlendMode = -1; // 0=disabled, 1=blend(depthMask=false), 2=cutout(depthMask=true)
            int lastCullMode = -1;  // 0=両面(cull無効), 1=片面(cull有効)。batch ごとに切替。
            boolean useCull = shouldCullModelFaces(entity);
            // fullbright経路は周囲光をsetShaderColor係数で疑似再現する
            float lightFactor = fullbright ? computeFlatBrightness(packedLight) : 1.0F;
            if (fullbright) {
                RenderSystem.enableDepthTest();
                // カリングはbatchごとに切替。色は先頭で1度だけ設定
                RenderSystem.setShader(GameRenderer::getRendertypeCloudsShader);
                RenderSystem.setShaderColor(gr / 255f * lightFactor, gg / 255f * lightFactor, gb / 255f * lightFactor, ga / 255f);
            }

            for (Batch batch : selectedBatches) {
                if (scriptRenderer != null && shouldSkipLegacyPlaceholderGroup(batch.groupName)) {
                    continue;
                }
                if (groupFilter != null && !groupFilter.shouldRender(batch.groupName)) {
                    continue;
                }
                if (shouldSuppressPackSpecificShadowArtifact(entity, batch.groupNameLower)) {
                    continue;
                }
                int scriptPassNow = scriptRenderer != null ? scriptRenderer.getCurrentPass() : 0;
                if (translucent && !batch.translucent && scriptPassNow < 2) continue;
                //本家 renderBodyNormal (pass0) は glAlphaFunc(GL_EQUAL, 1.0)。フラグメント α は
                //「テクスチャ α × マテリアル col α」なので、col α < 1 のバッチ (色付き半透明ガラス) は
                //pass0 では 1 ピクセルも α==1.0 にならず何も描かれない = バッチごとスキップと等価。
                boolean colAlphaGlass = batch.translucent && batch.baseAlpha < 0.999F;
                if (!translucent && scriptPassNow < 2 && colAlphaGlass) {
                    continue;
                }
                if (scriptRenderer != null) {
                    scriptRenderer.currentMatId = batch.materialId;
                    scriptRenderer.onBatchRendered();
                }
                boolean willTransform = groupTransform != null && groupTransform.mayModify(batch.groupName);
                if (willTransform) {
                    poseStack.pushPose();
                }
                try {
                    if (willTransform) {
                        groupTransform.apply(poseStack, batch.groupName);
                    }

                    int scriptPass = scriptRenderer != null ? scriptRenderer.getCurrentPass() : 0;
                    boolean scriptTexture = scriptRenderer != null && scriptRenderer.getBoundTexture() != null;
                    ResourceLocation emissiveTexture = !scriptTexture && scriptPass >= 2 ? batch.emissiveTextureForPass(scriptPass) : null;
                    if (scriptPass >= 2 && !scriptTexture && emissiveTexture == null) {
                        continue;
                    }
                    String lowerGroupName = batch.groupNameLower;
                    //★本家 jp.ngt.rtm.entity.vehicle.RenderVehicleBase (1.12.2) の忠実移植。
                    //  renderBodyNormal      : glAlphaFunc(GL_EQUAL, 1.0) → <b>α==1.0 のピクセルだけ</b>
                    //  renderBodyTransparent : glAlphaFunc(GL_LESS,  1.0) → <b>α<1.0 のピクセルだけ</b> + ブレンド
                    //  (どちらも終了時に glAlphaFunc(GL_GEQUAL, 0.1) へ戻す)
                    //つまり pass0 と pass1 はアルファで<b>正確に相補</b>に分割される。
                    //1.21 には固定機能のアルファテストが無いので、同じことをテクスチャの
                    //貼り分け (opaqueTexture = α==255 のみ / windowTexture = α<255 のみ) で行う。
                    //
                    //以前ここを「元テクスチャをそのまま使う」へ変えたことがあるが、根拠にした
                    //「本家にその指定は無い」は<b>誤り</b>だった。KaizPatchX の ModelObject には
                    //確かに無いが、アルファ関数を設定しているのは<b>車両レンダラ側</b>である。
                    //分割を外すと pass1 が不透明部分まで重ねてブレンドし、窓が黒く潰れる (E257)。
                    ResourceLocation texture = scriptTexture
                        ? scriptRenderer.getBoundTexture()
                        : (emissiveTexture != null ? emissiveTexture
                            : firstNonNull(translucent ? batch.windowTexture : batch.opaqueTexture, batch.texture));

                    boolean forceCutout;
                    float depthBias;
                    if (scriptTexture) {
                        forceCutout = shouldForceLegacyAlphaCutout(batch, lowerGroupName, true)
                            || shouldForceCabCutout(batch, lowerGroupName, true)
                            || shouldForceDisplayCutout(batch, lowerGroupName, true)
                            || shouldForceShaderSafeCutout(entity, batch, lowerGroupName, true);
                        depthBias = getDepthBias(batch, lowerGroupName, true);
                    } else {
                        // scriptTexture=false の結果はバッチ構築時に 1 度計算してキャッシュ。
                        // SL のように毎フレーム数百回呼ばれる shouldForce*/getDepthBias の
                        // 文字列 contains/startsWith を完全に省ける。
                        if (!batch.cachedComputed) {
                            batch.cachedForceCutoutNoScriptTex =
                                shouldForceLegacyAlphaCutout(batch, lowerGroupName, false)
                                || shouldForceCabCutout(batch, lowerGroupName, false)
                                || shouldForceDisplayCutout(batch, lowerGroupName, false);
                            batch.cachedDepthBiasNoScriptTex = getDepthBias(batch, lowerGroupName, false);
                            batch.cachedComputed = true;
                        }
                        forceCutout = batch.cachedForceCutoutNoScriptTex
                            || shouldForceShaderSafeCutout(entity, batch, lowerGroupName, false);
                        depthBias = batch.cachedDepthBiasNoScriptTex;
                    }
                    boolean needsBlend = (translucent && batch.translucent)
                        || (!forceCutout && (scriptTexture || scriptPassNow >= 2));

                    int scriptRed   = scriptRenderer != null ? scriptRenderer.getColorRed255()   : 255;
                    int scriptGreen = scriptRenderer != null ? scriptRenderer.getColorGreen255() : 255;
                    int scriptBlue  = scriptRenderer != null ? scriptRenderer.getColorBlue255()  : 255;
                    int scriptAlpha = scriptRenderer != null ? scriptRenderer.applyAlpha255(255) : 255;
                    // マテリアル col の不透明度を乗算 (ガラス等 a<1 で半透明に透ける)。
                    if (batch.baseAlpha < 0.999F) {
                        scriptAlpha = Math.round(scriptAlpha * batch.baseAlpha);
                    }
                    // マテリアル col の RGB (色タイント) を乗算。本家 RTM は glColor4f で col 全体を
                    // 効かせる。白 (1,1,1) 以外のマテリアル (内装の色付き半透明ガラス等) の色を再現する。
                    if (batch.baseColorR < 0.999F || batch.baseColorG < 0.999F || batch.baseColorB < 0.999F) {
                        scriptRed   = Math.round(scriptRed   * batch.baseColorR);
                        scriptGreen = Math.round(scriptGreen * batch.baseColorG);
                        scriptBlue  = Math.round(scriptBlue  * batch.baseColorB);
                    }

                    if (fullbright) {
                        // Direct OpenGL path: fullbright, no lightmap (trains/entities)
                        // テクスチャは batch ごとに変わる可能性があるので毎回設定 (RenderSystem 内部
                        // で同値なら GL バインドはスキップされる)。
                        RenderSystem.setShaderTexture(0, texture);
                        // 色は通常スクリプトが頻繁には変えないので、ループ先頭で設定済みのものを
                        // 使う (scriptRed/Green/Blue/Alpha がループ内で変わったら更新)。
                        if (scriptRed != gr || scriptGreen != gg || scriptBlue != gb || scriptAlpha != ga) {
                            RenderSystem.setShaderColor(scriptRed / 255f * lightFactor, scriptGreen / 255f * lightFactor, scriptBlue / 255f * lightFactor, scriptAlpha / 255f);
                            gr = scriptRed; gg = scriptGreen; gb = scriptBlue; ga = scriptAlpha;
                        }
                        // カリングはdoCulling一括(本家準拠)。false=全両面/true=全片面
                        int desiredCull = useCull ? 1 : 0;
                        if (desiredCull != lastCullMode) {
                            lastCullMode = desiredCull;
                            if (desiredCull == 1) {
                                RenderSystem.enableCull();
                            } else {
                                RenderSystem.disableCull();
                            }
                        }
                        // ブレンドはバッチ単位。translucent/emissiveのみON。depthMaskは常にON
                        int desiredBlend = (batch.translucent || scriptTexture || scriptPassNow >= 2) ? 1 : 0;
                        if (desiredBlend != lastBlendMode) {
                            lastBlendMode = desiredBlend;
                            if (desiredBlend == 1) {
                                RenderSystem.enableBlend();
                                RenderSystem.blendFuncSeparate(
                                    GlStateManager.SourceFactor.SRC_ALPHA,
                                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                                    GlStateManager.SourceFactor.ONE,
                                    GlStateManager.DestFactor.ZERO);
                                // RTM本家どおり pass1 の depthMask は ON(デフォルトのまま)。窓は pass0 で
                                // 既にアルファテスト不透明描画されており、pass1 は同じ位置にブレンドを重ねる。
                                RenderSystem.depthMask(true);
                            } else if (desiredBlend == 2) {
                                RenderSystem.enableBlend();
                                RenderSystem.blendFuncSeparate(
                                    GlStateManager.SourceFactor.SRC_ALPHA,
                                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                                    GlStateManager.SourceFactor.ONE,
                                    GlStateManager.DestFactor.ZERO);
                                RenderSystem.depthMask(true);
                            } else {
                                RenderSystem.disableBlend();
                                RenderSystem.depthMask(true);
                            }
                        }
                        // GPU VBO 高速経路: depthBias=0 かつ scriptRenderer の UV 変換が無ければ
                        // 静的 VBO を再利用。
                        //★発光テクスチャを持つ材質は VBO 高速経路に載せない。
                        //VBO 経路は mv = ModelView × pose を CPU で合成して<b>頂点変換を GPU 側</b>で行うが、
                        //発光パス (renderNamedGroupsEmissive) は<b>CPU 側</b>で pose を掛けてから提出する。
                        //(A·B)·v と A·(B·v) は float32 で数 ULP ずれるため、同一面なのに深度が一致せず、
                        //pass0 と発光パスが z-fighting を起こす (ライト点灯時だけ内装がちらつく原因)。
                        //本家は変換経路が 1 つしかないのでこの問題自体が存在しない。
                        //経路を揃えるため、発光する材質は CPU 変換側に統一する。
                        boolean canUseStaticVbo = !PIN_CPU_TRANSFORM
                            && !com.portofino.realtrainmodunofficial.client.render
                                .InteriorLighting.isActive()
                            && depthBias == 0.0F
                            && batch.emissiveTextures.length == 0
                            && (scriptRenderer == null || !scriptRenderer.hasUvWindow());
                        com.mojang.blaze3d.vertex.VertexBuffer cachedVbo = canUseStaticVbo
                            ? batch.getOrBuildFullbrightVbo()
                            : null;
                        if (cachedVbo != null && !cachedVbo.isInvalid()) {
                            // drawWithShader の第1引数は完全な ModelView 行列が必要。
                            // poseStack.last().pose() はエンティティローカル変換のみ (カメラ view を含まない) なので、
                            // RenderSystem の ModelView を左から掛けないとカメラ空間描画になりカメラに追従する。
                            org.joml.Matrix4f mv = new org.joml.Matrix4f(RenderSystem.getModelViewMatrix()).mul(poseStack.last().pose());
                            cachedVbo.bind();
                            cachedVbo.drawWithShader(
                                mv,
                                RenderSystem.getProjectionMatrix(),
                                net.minecraft.client.renderer.GameRenderer.getRendertypeCloudsShader());
                            com.mojang.blaze3d.vertex.VertexBuffer.unbind();
                        } else {
                            // CPU フォールバック (scriptRenderer の UV/色変換が必要なフレーム等)
                            BufferBuilder builder = Tesselator.getInstance().begin(
                                VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL);
                            Matrix4f mat = poseStack.last().pose();
                            Matrix3f norm = poseStack.last().normal();
                            float[] normalOut = new float[3];
                            for (int i = 0; i < batch.vertexCount; i++) {
                                int o = i * 8;
                                float x = batch.data[o], y = batch.data[o + 1], z = batch.data[o + 2];
                                float nx = batch.data[o + 3], ny = batch.data[o + 4], nz = batch.data[o + 5];
                                float u = batch.data[o + 6], v = batch.data[o + 7];
                                if (depthBias != 0.0F) {
                                    //押し出しは biasNormals (同位置の全面平均)。面割れ防止。
                                    float bnx = nx, bny = ny, bnz = nz;
                                    if (batch.biasNormals != null) {
                                        int bo = i * 3;
                                        bnx = batch.biasNormals[bo]; bny = batch.biasNormals[bo + 1]; bnz = batch.biasNormals[bo + 2];
                                    }
                                    float il = (float)(1.0D / Math.sqrt(Math.max(1.0E-8F, bnx*bnx + bny*bny + bnz*bnz)));
                                    x += bnx*il*depthBias; y += bny*il*depthBias; z += bnz*il*depthBias;
                                }
                                if (scriptRenderer != null) {
                                    u = scriptRenderer.mapU(u, batch.minU, batch.maxU);
                                    v = scriptRenderer.mapV(v, batch.minV, batch.maxV);
                                }
                                float tx = mat.m00()*x + mat.m10()*y + mat.m20()*z + mat.m30();
                                float ty = mat.m01()*x + mat.m11()*y + mat.m21()*z + mat.m31();
                                float tz = mat.m02()*x + mat.m12()*y + mat.m22()*z + mat.m32();
                                float tnx = norm.m00()*nx + norm.m10()*ny + norm.m20()*nz;
                                float tny = norm.m01()*nx + norm.m11()*ny + norm.m21()*nz;
                                float tnz = norm.m02()*nx + norm.m12()*ny + norm.m22()*nz;
                                normalizeNormal(tnx, tny, tnz, normalOut);
                                builder.addVertex(tx, ty, tz)
                                    .setUv(u, v)
                                    .setColor(255, 255, 255, 255)
                                    .setNormal(normalOut[0], normalOut[1], normalOut[2]);
                            }
                            if (batch.vertexCount > 0) {
                                RenderSystem.setShader(GameRenderer::getRendertypeCloudsShader);
                                BufferUploader.drawWithShader(builder.buildOrThrow());
                            }
                        }
                    } else {
                        // Lightmap-aware path: block entities (rails, installed objects)
                        //本家厳密移植 (ユーザー選択): カリングは doCulling 一括 (不透明も半透明も同じ)。
                        //doCulling=false で両面、true で片面。「半透明だけ両面」は本家に無い。
                        boolean cullThisBatch = useCull;
                        //pass1 も深度は書く (本家 depthMask 既定 ON)。書かないと後で描くレールが透ける。
                        RenderType renderType;
                        if (needsBlend) {
                            //半透明も doCulling に従う (本家厳密化)。深度書き込み無し・提出順は据え置き。
                            renderType = cullThisBatch
                                ? com.portofino.realtrainmodunofficial.client.render.RtmuRenderTypes.glassNoDepthCull(texture)
                                : com.portofino.realtrainmodunofficial.client.render.RtmuRenderTypes.glassNoDepth(texture);
                        } else {
                            renderType = cullThisBatch ? RenderType.entityCutout(texture)
                                : RenderType.entityCutoutNoCull(texture);
                        }
                        //静的VBO高速経路。落ちたゲートを記録する(F8のVBO行)
                        int vboReason;
                        if (PIN_CPU_TRANSFORM) {
                            //経路固定 (PIN_CPU_TRANSFORM 参照)。VBO は使わない。
                            vboReason = com.portofino.realtrainmodunofficial.client.ClientRenderProfiler.VBO_BIAS;
                        } else if (captureMode) {
                            vboReason = com.portofino.realtrainmodunofficial.client.ClientRenderProfiler.VBO_CAPTURE;
                        } else if (needsBlend) {
                            vboReason = com.portofino.realtrainmodunofficial.client.ClientRenderProfiler.VBO_BLEND;
                        } else if (groupTransform != null) {
                            vboReason = com.portofino.realtrainmodunofficial.client.ClientRenderProfiler.VBO_TRANSFORM;
                        } else if (depthBias != 0.0F) {
                            vboReason = com.portofino.realtrainmodunofficial.client.ClientRenderProfiler.VBO_BIAS;
                        } else if (batch.emissiveTextures.length > 0) {
                            //★発光する材質は VBO 経路に載せない。
                            //VBO は mv = ModelView × pose を CPU 合成して<b>頂点変換を GPU</b>で行うが、
                            //発光パス (renderNamedGroupsEmissive) は<b>CPU</b>で pose を掛けてから提出する。
                            //(A·B)·v と A·(B·v) は float32 で数 ULP ずれるため、同一面でも深度が一致せず、
                            //pass0 と発光パスが画素単位で z-fighting する (内装がまだら状にちらつく)。
                            //本家は変換経路が 1 つしかないのでこの問題自体が存在しない。経路を CPU 側へ揃える。
                            //★判定は<b>バッチ単位</b>。モデル単位にすると発光しない面まで CPU 変換になり、
                            //発光を持つ車両の描画負荷が上がる (実機で悪化を確認)。
                            vboReason = com.portofino.realtrainmodunofficial.client.ClientRenderProfiler.VBO_BIAS;
                        } else if (overlay != net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY) {
                            vboReason = com.portofino.realtrainmodunofficial.client.ClientRenderProfiler.VBO_OVERLAY;
                        } else if (scriptRed != 255 || scriptGreen != 255 || scriptBlue != 255 || scriptAlpha != 255) {
                            vboReason = com.portofino.realtrainmodunofficial.client.ClientRenderProfiler.VBO_COLOR;
                        } else if (scriptRenderer != null && scriptRenderer.hasUvWindow()) {
                            vboReason = com.portofino.realtrainmodunofficial.client.ClientRenderProfiler.VBO_UV;
                        } else if (com.portofino.realtrainmodunofficial.client.ShaderCompat.isShaderPackInUse()) {
                            vboReason = com.portofino.realtrainmodunofficial.client.ClientRenderProfiler.VBO_SHADER;
                        } else {
                            long secVbo = com.portofino.realtrainmodunofficial.client.ClientRenderProfiler.sec();
                            boolean vboDrawn = drawBatchWithEntityVbo(batch, renderType, poseStack, packedLight);
                            com.portofino.realtrainmodunofficial.client.ClientRenderProfiler.secEnd(
                                com.portofino.realtrainmodunofficial.client.ClientRenderProfiler.SEC_VBO, secVbo);
                            if (vboDrawn) {
                                com.portofino.realtrainmodunofficial.client.ClientRenderProfiler.countVbo(
                                    com.portofino.realtrainmodunofficial.client.ClientRenderProfiler.VBO_OK);
                                //VBO 経路もドローコール数は同じだけ掛かる。重複描画が無いか一緒に数える。
                                com.portofino.realtrainmodunofficial.client.ClientRenderProfiler.countSlowBatch(
                                    batch.groupName, batch.vertexCount,
                                    com.portofino.realtrainmodunofficial.client.ClientRenderProfiler.VBO_OK);
                                com.portofino.realtrainmodunofficial.client.ClientRenderProfiler.countSlowBatchIdentity(
                                    batch.groupName,
                                    com.portofino.realtrainmodunofficial.client.ClientRenderProfiler.VBO_OK,
                                    System.identityHashCode(batch));
                                continue;
                            }
                            vboReason = com.portofino.realtrainmodunofficial.client.ClientRenderProfiler.VBO_BUILD;
                        }
                        com.portofino.realtrainmodunofficial.client.ClientRenderProfiler.countVbo(vboReason);
                        MultiBufferSource targetBuffer = buffer;
                        //★pass1 は<b>面を間引かない</b>。
                        //以前は「窓テクセルを踏む面だけ提出する」独自最適化を入れていたが、
                        //テクスチャの解像度や UV の取り方によっては車体の面まで落ちてしまい、
                        //そこが穴になって手前に描いてあるレールが覗く。本家 pass1 は
                        //材質の面を全部描いて alpha で抜くだけなので、そちらに合わせる。
                        float[] vData = batch.data;
                        float[] vBias = batch.biasNormals;
                        int vCount = batch.vertexCount;
                        if (vCount <= 0) {
                            continue;
                        }
                        //どのグループが CPU 経路に落ちているかをログに出す。
                        //※間引き<b>後</b>の実提出数を渡すこと。batch.vertexCount を渡していたときは
                        //  最適化が効いても数字が動かず、効果を読み違えた。
                        com.portofino.realtrainmodunofficial.client.ClientRenderProfiler.countSlowBatch(
                            batch.groupName, vCount, vboReason);
                        com.portofino.realtrainmodunofficial.client.ClientRenderProfiler.countSlowBatchIdentity(
                            batch.groupName, vboReason, System.identityHashCode(batch));
                        long secBuf = com.portofino.realtrainmodunofficial.client.ClientRenderProfiler.sec();
                        VertexConsumer consumer = targetBuffer.getBuffer(renderType);
                        com.portofino.realtrainmodunofficial.client.ClientRenderProfiler.secEnd(
                            com.portofino.realtrainmodunofficial.client.ClientRenderProfiler.SEC_GETBUF, secBuf);
                        //計測: CPU が毎フレーム流している頂点数 (F8 オーバーレイに出す)
                        com.portofino.realtrainmodunofficial.client.ClientRenderProfiler.addVertices(vCount);
                        long secSub = com.portofino.realtrainmodunofficial.client.ClientRenderProfiler.sec();
                        PoseStack.Pose pose = poseStack.last();
                        Matrix4f mat = pose.pose();
                        Matrix3f norm = pose.normal();
                        float[] normalOut = new float[3];
                        for (int i = 0; i < vCount; i++) {
                            int o = i * 8;
                            float x = vData[o], y = vData[o + 1], z = vData[o + 2];
                            float nx = vData[o + 3], ny = vData[o + 4], nz = vData[o + 5];
                            float u = vData[o + 6], v = vData[o + 7];
                            if (depthBias != 0.0F) {
                                //押し出しは biasNormals (同位置の全面平均)。面割れ防止。
                                float bnx = nx, bny = ny, bnz = nz;
                                if (vBias != null) {
                                    int bo = i * 3;
                                    bnx = vBias[bo]; bny = vBias[bo + 1]; bnz = vBias[bo + 2];
                                }
                                float il = (float)(1.0D / Math.sqrt(Math.max(1.0E-8F, bnx*bnx + bny*bny + bnz*bnz)));
                                x += bnx*il*depthBias; y += bny*il*depthBias; z += bnz*il*depthBias;
                            }
                            if (scriptRenderer != null) {
                                u = scriptRenderer.mapU(u, batch.minU, batch.maxU);
                                v = scriptRenderer.mapV(v, batch.minV, batch.maxV);
                            }
                            float tnx = norm.m00()*nx + norm.m10()*ny + norm.m20()*nz;
                            float tny = norm.m01()*nx + norm.m11()*ny + norm.m21()*nz;
                            float tnz = norm.m02()*nx + norm.m12()*ny + norm.m22()*nz;
                            normalizeNormal(tnx, tny, tnz, normalOut);
                            //頂点ごとのVector3f確保を避ける(GC削減)
                            //本家 enableCustomLighting 相当: 室内灯の方を向いた面を明るくする。
                            int vr = scriptRed;
                            int vg = scriptGreen;
                            int vb = scriptBlue;
                            if (com.portofino.realtrainmodunofficial.client.render.InteriorLighting.isActive()) {
                                float f = com.portofino.realtrainmodunofficial.client.render
                                    .InteriorLighting.factor(x, y, z, nx, ny, nz);
                                vr = Math.min(255, Math.round(vr * f
                                    * com.portofino.realtrainmodunofficial.client.render.InteriorLighting.red()));
                                vg = Math.min(255, Math.round(vg * f
                                    * com.portofino.realtrainmodunofficial.client.render.InteriorLighting.green()));
                                vb = Math.min(255, Math.round(vb * f
                                    * com.portofino.realtrainmodunofficial.client.render.InteriorLighting.blue()));
                            }
                            VertexWriter.addVertex(consumer, mat, x, y, z)
                                .setColor(vr, vg, vb, scriptAlpha)
                                .setUv(u, v)
                                .setOverlay(overlay)
                                .setLight(packedLight)
                                .setNormal(normalOut[0], normalOut[1], normalOut[2]);
                        }
                        com.portofino.realtrainmodunofficial.client.ClientRenderProfiler.secEnd(
                            com.portofino.realtrainmodunofficial.client.ClientRenderProfiler.SEC_SUBMIT, secSub);
                    }
                } finally {
                    if (willTransform) {
                        poseStack.popPose();
                    }
                }
            }

            // (床下の蓋は撤去: ユーザー報告「床に敷いた影のように見えて邪魔」のため。)

            if (fullbright) {
                RenderSystem.enableCull();
                RenderSystem.depthMask(true);
                RenderSystem.disableBlend();
                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            }
            com.portofino.realtrainmodunofficial.client.ClientRenderProfiler.secEnd(
                com.portofino.realtrainmodunofficial.client.ClientRenderProfiler.SEC_LOOP, secLoop);
        }

        public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, int overlay) {
            render(poseStack, buffer, packedLight, overlay, null, null, null);
        }

        public void renderPreferScript(PoseStack poseStack, MultiBufferSource buffer, int packedLight, int overlay,
                                       GroupPredicate groupFilter, GroupTransform groupTransform, Object entity) {
            // scriptとbakedを1段で完結させる(2段だと二重描画)
            boolean hasScript = scriptEngine != null;
            // 全不透明の後に半透明。deferTranslucentはスクリプト描画専用
            boolean deferTrans = scriptRenderer != null;
            try {
                if (scriptRenderer != null) {
                    scriptRenderer.resetRenderStatistics();
                }
                if (deferTrans) {
                    scriptRenderer.setDeferTranslucent(true);
                }
                if (hasScript) {
                    // scriptのrender()を全pass(0/1/2/3)で呼ぶ。重複は再描画チェックで防ぐ
                    for (int pass = 0; pass < LEGACY_SCRIPT_PASS_COUNT; pass++) {
                        //★パスの取捨も RTMU では判断しない (スクリプト任せ)。
                        //以前は「一度走らせて 1 バッチも出さなかったパスは以後スキップ」していたが、
                        //スクリプトは状態でパスごとの出力を変える (改札は通行可のときだけ pass 2 で
                        //矢印を描く)。出さなかった瞬間に見て切ると、その後どれだけ状態が変わっても
                        //二度と描かれない。RTM 標準の自動改札の矢印が出なかったのはこれ。
                        //★発光パスを RTMU の推測でスキップしない (スクリプト任せ)。
                        //以前は「light/dest/type… という名前のグループを持つか」で判定していたが、
                        //これは列車向けの名前の一覧でしかない。RTM 標準の自動改札は通行可の矢印を
                        //sign_F/sign_B という名前で pass 2 に描くため、この一覧に当たらず
                        //矢印が一切出なかった。何を pass 2 で描くかはスクリプトが決めること。
                        //室内灯/前照灯/尾灯は、点いているものだけ描く
                        if (pass >= 2 && !shouldRenderEmissivePass(entity, pass)) continue;
                        poseStack.pushPose();
                        try {
                            executeScript(poseStack, buffer, packedLight, overlay, pass, entity);
                        } finally {
                            try { poseStack.popPose(); } catch (Throwable ignored) {}
                        }
                    }
                }
                // baked render filter 組み立て (scriptedOpaqueGroups を 使う前にクリアしない)
                GroupPredicate opaqueFilter = groupFilter;
                GroupPredicate translucentFilter = groupFilter;
                if (hasScript && scriptRenderer != null && bakedFilterLogCount < 3) {
                    bakedFilterLogCount++;
                }
                if (hasScript && scriptRenderer != null && scriptRenderer.hasScriptRenderedGroups()) {
                    opaqueFilter = groupName ->
                        (groupFilter == null || groupFilter.shouldRender(groupName))
                            && scriptRenderer.shouldRenderBakedGroup(groupName, false);
                    translucentFilter = groupName ->
                        (groupFilter == null || groupFilter.shouldRender(groupName))
                            && scriptRenderer.shouldRenderBakedGroup(groupName, true);
                }
                // baked前にcurrentPassを0へ戻す(emissive判定の誤適用防止)
                if (scriptRenderer != null) {
                    scriptRenderer.clearRenderContext();
                }
                //pass0は全マテリアルを描く(hasOpaqueBatchesだけでゲートしない)
                if (hasOpaqueBatches() || hasTranslucentBatches()) {
                    renderInternal(poseStack, buffer, packedLight, overlay, false, opaqueFilter, groupTransform, scriptRenderer, entity);
                }
                if (hasTranslucentBatches() || (scriptRenderer != null && scriptRenderer.hasAlphaPassContent())) {
                    renderInternal(poseStack, buffer, packedLight, overlay, true, translucentFilter, groupTransform, scriptRenderer, entity);
                }
                //スクリプトを持たない車両の発光パス (室内灯/前照灯/尾灯)。
                if (!hasScript && hasEmissiveBatches()) {
                    for (int pass = 2; pass < LEGACY_SCRIPT_PASS_COUNT; pass++) {
                        if (!shouldRenderEmissivePass(entity, pass)) {
                            continue;
                        }
                        renderAllGroupsEmissive(poseStack, buffer, packedLight, overlay, pass, opaqueFilter, groupTransform);
                    }
                }
                // 全不透明(script + baked)描画後に、溜めておいた半透明を最後に一括描画する。
                if (deferTrans) {
                    scriptRenderer.flushDeferredTranslucent(poseStack, buffer);
                    scriptRenderer.setDeferTranslucent(false);
                }
            } finally {
                if (deferTrans) {
                    scriptRenderer.setDeferTranslucent(false);
                }
                if (scriptRenderer != null) {
                    scriptRenderer.clearRenderContext();
                }
            }
        }

        public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, int overlay, GroupPredicate groupFilter) {
            render(poseStack, buffer, packedLight, overlay, groupFilter, null, null);
        }

        public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, int overlay, GroupPredicate groupFilter, GroupTransform groupTransform) {
            render(poseStack, buffer, packedLight, overlay, groupFilter, groupTransform, null);
        }

        public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, int overlay,
                           GroupPredicate groupFilter, GroupTransform groupTransform, Object entity) {
            boolean hasScript = scriptEngine != null;
            boolean scriptRendered = false;
            boolean deferTrans = true;
            try {
                if (scriptRenderer != null) {
                    scriptRenderer.resetRenderStatistics();
                }
                if (deferTrans && scriptRenderer != null) {
                    scriptRenderer.setDeferTranslucent(true);
                }
                if (hasScript) {
                    for (int pass = 0; pass < LEGACY_SCRIPT_PASS_COUNT; pass++) {
                        //発光パスは RTMU の推測でスキップしない (何を描くかはスクリプトが決める)
                        //室内灯/前照灯/尾灯は、点いているものだけ描く
                        if (pass >= 2 && !shouldRenderEmissivePass(entity, pass)) continue;
                        // スクリプトが poseStack を破壊する事例 (rotate/translate を push/pop なしで多用、
                        // NaN を渡す等) に対する安全網。push/pop で囲んで corruption を局所化する。
                        poseStack.pushPose();
                        try {
                            scriptRendered |= executeScript(poseStack, buffer, packedLight, overlay, pass, entity);
                        } finally {
                            try { poseStack.popPose(); } catch (Throwable ignored) {}
                        }
                    }
                }
            } finally {
                if (scriptRenderer != null) {
                    scriptRenderer.clearRenderContext();
                }
            }
            GroupPredicate opaqueFilter = groupFilter;
            GroupPredicate translucentFilter = groupFilter;
            // baked render の filter 適用状態を一度だけ可視化する。
            if (hasScript && scriptRenderer != null && bakedFilterLogCount < 3) {
                bakedFilterLogCount++;
            }
            if (hasScript && scriptRenderer != null && scriptRenderer.hasScriptRenderedGroups()) {
                opaqueFilter = groupName ->
                    (groupFilter == null || groupFilter.shouldRender(groupName))
                        && scriptRenderer.shouldRenderBakedGroup(groupName, false);
                translucentFilter = groupName ->
                    (groupFilter == null || groupFilter.shouldRender(groupName))
                        && scriptRenderer.shouldRenderBakedGroup(groupName, true);
            }
            //本家 pass0 は AlphaBlend 材質の不透明ピクセル (α==255) も描く。全 AlphaBlend の
            //モデル (KQ 台車等) で pass0 が飛ぶと台車が消えるため、バッチがあれば必ず回す。
            if (hasOpaqueBatches() || hasTranslucentBatches()) {
                renderInternal(poseStack, buffer, packedLight, overlay, false, opaqueFilter, groupTransform, scriptRenderer, entity);
            }
            if (hasTranslucentBatches() || (scriptRenderer != null && scriptRenderer.hasAlphaPassContent())) {
                renderInternal(poseStack, buffer, packedLight, overlay, true, translucentFilter, groupTransform, scriptRenderer, entity);
            }
            //スクリプトを持たない車両の発光パス (室内灯/前照灯/尾灯)。
            //本家はスクリプトと無関係に Light マテリアルを ***_light0/1/2.png で描き直す。
            if (!hasScript && hasEmissiveBatches()) {
                for (int pass = 2; pass < LEGACY_SCRIPT_PASS_COUNT; pass++) {
                    if (!shouldRenderEmissivePass(entity, pass)) {
                        continue;
                    }
                    renderAllGroupsEmissive(poseStack, buffer, packedLight, overlay, pass, opaqueFilter, groupTransform);
                }
            }
            if (deferTrans && scriptRenderer != null) {
                scriptRenderer.flushDeferredTranslucent(poseStack, buffer);
                scriptRenderer.setDeferTranslucent(false);
            }
        }

        private void renderColorOverlay(PoseStack poseStack, MultiBufferSource buffer, int overlay,
                                        GroupPredicate groupFilter, int red, int green, int blue, int alpha) {
            final float surfaceBias = 0.0025F;
            for (Batch batch : batches) {
                if (groupFilter != null && !groupFilter.shouldRender(batch.groupName)) continue;
                ResourceLocation texture = batch.texture != null ? batch.texture : fallbackTexture();
                VertexConsumer consumer = buffer.getBuffer(
                    batch.translucent ? RenderType.entityTranslucentCull(texture) : RenderType.entityCutout(texture)
                );
                PoseStack.Pose pose = poseStack.last();
                Matrix4f mat = pose.pose();
                Matrix3f norm = pose.normal();
                float[] normalized = new float[3];
                for (int i = 0; i < batch.vertexCount; i++) {
                    int o = i * 8;
                    float vx = batch.data[o];
                    float vy = batch.data[o + 1];
                    float vz = batch.data[o + 2];
                    float nx = batch.data[o + 3];
                    float ny = batch.data[o + 4];
                    float nz = batch.data[o + 5];
                    //押し出しは biasNormals (同位置の全面平均)。面割れ防止。
                    float bnx = nx, bny = ny, bnz = nz;
                    if (batch.biasNormals != null) {
                        int bo = i * 3;
                        bnx = batch.biasNormals[bo]; bny = batch.biasNormals[bo + 1]; bnz = batch.biasNormals[bo + 2];
                    }
                    float localLength = (float) Math.sqrt(bnx * bnx + bny * bny + bnz * bnz);
                    if (localLength > 1.0E-6F) {
                        float scale = surfaceBias / localLength;
                        vx += bnx * scale;
                        vy += bny * scale;
                        vz += bnz * scale;
                    }
                    //発光オーバーレイ: 実法線だと diffuse シェーディングで側面/下面が
                    //最大 40% 減光して「昼でも夜でも暗い」ため、上向き法線 (×1.0) で描く
                    //(本家は GL_LIGHTING 無効の全光量描画 — softenNormalForVanilla と同じ理由)
                    VertexWriter.addVertex(consumer, mat, vx, vy, vz)
                        .setColor(red, green, blue, alpha)
                        .setUv(batch.data[o + 6], batch.data[o + 7])
                        .setOverlay(overlay)
                        .setLight(0x00F000F0)
                        .setNormal(0.0F, 1.0F, 0.0F);
                }
            }
        }

        static java.lang.reflect.Field shaderLightDirsField;
        static boolean shaderLightDirsFailed;

        /** 静的VBOによるバッチ描画。true=VBOで描画した。 */
        private static boolean drawBatchWithEntityVbo(Batch batch, RenderType renderType,
                                                      PoseStack poseStack, int packedLight) {
            com.mojang.blaze3d.vertex.VertexBuffer vbo = batch.getOrBuildEntityVbo(packedLight);
            if (vbo == null || vbo.isInvalid()) {
                return false;
            }
            java.lang.reflect.Field field = shaderLightDirsField;
            if (field == null) {
                if (shaderLightDirsFailed) {
                    return false;
                }
                try {
                    field = RenderSystem.class.getDeclaredField("shaderLightDirections");
                    field.setAccessible(true);
                    shaderLightDirsField = field;
                } catch (Throwable t) {
                    shaderLightDirsFailed = true;
                    return false;
                }
            }
            org.joml.Vector3f origL0 = null;
            org.joml.Vector3f origL1 = null;
            try {
                Object dirsObj = field.get(null);
                if (!(dirsObj instanceof org.joml.Vector3f[] dirs)
                    || dirs.length < 2 || dirs[0] == null || dirs[1] == null) {
                    return false;
                }
                origL0 = new org.joml.Vector3f(dirs[0]);
                origL1 = new org.joml.Vector3f(dirs[1]);
                org.joml.Matrix3f invRot = new org.joml.Matrix3f(poseStack.last().normal()).transpose();
                RenderSystem.setShaderLights(
                    invRot.transform(new org.joml.Vector3f(origL0)),
                    invRot.transform(new org.joml.Vector3f(origL1)));
                renderType.setupRenderState();
                try {
                    net.minecraft.client.renderer.ShaderInstance shader = RenderSystem.getShader();
                    if (shader == null) {
                        return false;
                    }
                    Matrix4f mv = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(poseStack.last().pose());
                    vbo.bind();
                    vbo.drawWithShader(mv, RenderSystem.getProjectionMatrix(), shader);
                    com.mojang.blaze3d.vertex.VertexBuffer.unbind();
                    return true;
                } finally {
                    renderType.clearRenderState();
                }
            } catch (Throwable t) {
                return false;
            } finally {
                if (origL0 != null) {
                    RenderSystem.setShaderLights(origL0, origL1);
                }
            }
        }

        /** 統合済みメッシュを描画する。true=描画した。 */
        public static boolean drawMergedVbo(com.mojang.blaze3d.vertex.VertexBuffer vbo, RenderType renderType,
                                            PoseStack poseStack) {
            if (vbo == null || vbo.isInvalid()) {
                return false;
            }
            java.lang.reflect.Field field = shaderLightDirsField;
            if (field == null) {
                if (shaderLightDirsFailed) {
                    return false;
                }
                try {
                    field = RenderSystem.class.getDeclaredField("shaderLightDirections");
                    field.setAccessible(true);
                    shaderLightDirsField = field;
                } catch (Throwable t) {
                    shaderLightDirsFailed = true;
                    return false;
                }
            }
            org.joml.Vector3f origL0 = null;
            org.joml.Vector3f origL1 = null;
            try {
                Object dirsObj = field.get(null);
                if (!(dirsObj instanceof org.joml.Vector3f[] dirs)
                    || dirs.length < 2 || dirs[0] == null || dirs[1] == null) {
                    return false;
                }
                origL0 = new org.joml.Vector3f(dirs[0]);
                origL1 = new org.joml.Vector3f(dirs[1]);
                org.joml.Matrix3f invRot = new org.joml.Matrix3f(poseStack.last().normal()).transpose();
                RenderSystem.setShaderLights(
                    invRot.transform(new org.joml.Vector3f(origL0)),
                    invRot.transform(new org.joml.Vector3f(origL1)));
                renderType.setupRenderState();
                try {
                    net.minecraft.client.renderer.ShaderInstance shader = RenderSystem.getShader();
                    if (shader == null) {
                        return false;
                    }
                    Matrix4f mv = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(poseStack.last().pose());
                    vbo.bind();
                    vbo.drawWithShader(mv, RenderSystem.getProjectionMatrix(), shader);
                    com.mojang.blaze3d.vertex.VertexBuffer.unbind();
                    return true;
                } finally {
                    renderType.clearRenderState();
                }
            } catch (Throwable t) {
                return false;
            } finally {
                if (origL0 != null) {
                    RenderSystem.setShaderLights(origL0, origL1);
                }
            }
        }

        private static void softenNormalForVanilla(float nx, float ny, float nz, float[] out) {
            // 上向き法線で全面フルブライト相当にする
            out[0] = 0.0F;
            out[1] = 1.0F;
            out[2] = 0.0F;
        }

        private static int resolveVertexLight(Object entity, String lowerGroupName, int packedLight) {
            return packedLight;
        }

        /** packedLightからフラット明るさ係数(0..1)を計算する。 */
        private static float computeFlatBrightness(int packedLight) {
            try {
                Minecraft mc = Minecraft.getInstance();
                net.minecraft.client.multiplayer.ClientLevel level = mc.level;
                if (level == null) return 1.0F;
                net.minecraft.world.level.dimension.DimensionType dim = level.dimensionType();
                int blockLevel = net.minecraft.client.renderer.LightTexture.block(packedLight);
                int skyLevel = net.minecraft.client.renderer.LightTexture.sky(packedLight);
                float skyMul = level.getSkyDarken(1.0F) * 0.95F + 0.05F;
                float skyB = net.minecraft.client.renderer.LightTexture.getBrightness(dim, skyLevel) * skyMul;
                float blockB = net.minecraft.client.renderer.LightTexture.getBrightness(dim, blockLevel);
                float b = Math.max(skyB, blockB);
                // 完全な暗黒で真っ黒にならないよう下限を少し残す(バニラも 0.04 程度のグレー混合がある)。
                return net.minecraft.util.Mth.clamp(b, 0.05F, 1.0F);
            } catch (Throwable ignored) {
                return 1.0F;
            }
        }

        private static void normalizeNormal(float nx, float ny, float nz, float[] out) {
            float lenSq = nx * nx + ny * ny + nz * nz;
            if (lenSq <= 1.0E-8F) {
                out[0] = 0.0F;
                out[1] = 1.0F;
                out[2] = 0.0F;
                return;
            }
            float invLen = (float) (1.0D / Math.sqrt(lenSq));
            out[0] = nx * invLen;
            out[1] = ny * invLen;
            out[2] = nz * invLen;
        }

        private static boolean hasActiveShaderPipeline() {
            long now = System.currentTimeMillis();
            if (now < shaderPipelineCacheUntilMillis) {
                return shaderPipelineCacheValue;
            }
            boolean active = false;
            try {
                Class<?> irisApiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                Object irisApi = irisApiClass.getMethod("getInstance").invoke(null);
                Object inUse = irisApiClass.getMethod("isShaderPackInUse").invoke(irisApi);
                if (inUse instanceof Boolean enabled) {
                    active = enabled;
                }
            } catch (Throwable ignored) {
            }
            shaderPipelineCacheValue = active;
            shaderPipelineCacheUntilMillis = now + 1000L;
            return active;
        }

        private static boolean shouldRenderReflectionOverlay(Object entity, Batch batch, String lowerGroupName, boolean scriptTexture) {
            return false;
        }

        private static boolean isGlassGroup(String lowerGroupName) {
            return lowerGroupName.contains("glass")
                || lowerGroupName.contains("window")
                || lowerGroupName.contains("wind");
        }

        private static boolean isLightGroup(String lowerGroupName) {
            return lowerGroupName.contains("light")
                || lowerGroupName.contains("lamp")
                || lowerGroupName.contains("marker");
        }

        private static boolean shouldUseGlassOnlyPass(Batch batch, String lowerGroupName) {
            if (batch == null || lowerGroupName == null || !batch.translucent) {
                return false;
            }
            // ボディ全体を AlphaBlend 指定している蒸機やロッド物はここに入れない。
            // 明示的にガラス/窓/alpha グループとして切られているものだけを対象にする。
            return lowerGroupName.equals("alpha")
                || lowerGroupName.startsWith("alpha_")
                || lowerGroupName.contains("glass")
                || lowerGroupName.contains("window")
                || lowerGroupName.contains("wind");
        }

        private static boolean shouldSuppressPackSpecificShadowArtifact(Object entity, String lowerGroupName) {
            if (!(entity instanceof TrainEntity train) || lowerGroupName == null) {
                return false;
            }
            String vehicleId = train.getVehicleId();
            if (vehicleId == null) {
                return false;
            }
            String lowerId = vehicleId.toLowerCase(Locale.ROOT);
            VehicleDefinition def = VehicleRegistry.getById(vehicleId);
            String lowerModelFile = def == null || def.getModelFile() == null
                ? ""
                : def.getModelFile().replace('\\', '/').toLowerCase(Locale.ROOT);
            // T-ONREC E131 パックは alpha / alpha_ グループの中に、窓ガラスではなく
            // 車体下へ大きく伸びる補助板が入っている。移植版ではこれが黒い「影板」として
            // 出てしまう。面単位の除外は bake 時に行い、ここでは丸ごと消さない。
            if (lowerId.startsWith("t-on_e131")
                    || lowerModelFile.startsWith("t-onrec/e131/")
                    || lowerModelFile.contains("/t-onrec/e131/")) {
                return false;
            }
            if (lowerId.startsWith("baru_keikyu")
                    || lowerId.contains("keikyu")
                    || lowerModelFile.startsWith("baru_keikyu_")
                    || lowerModelFile.contains("/baru_keikyu_")) {
                return lowerGroupName.equals("shadow")
                    || lowerGroupName.startsWith("shadow_")
                    || lowerGroupName.endsWith("_shadow");
            }
            if ((lowerId.startsWith("d51-498") || lowerModelFile.startsWith("d51-498"))
                    && lowerGroupName.equals("fl")) {
                return true;
            }
            return false;
        }

        private static boolean isInteriorGroup(String lowerGroupName) {
            return lowerGroupName.contains("seat")
                || lowerGroupName.contains("chair")
                || lowerGroupName.contains("bogie")
                || lowerGroupName.contains("wheel")
                || lowerGroupName.contains("pantograph")
                || lowerGroupName.contains("under")
                || lowerGroupName.contains("floor")
                || lowerGroupName.contains("panel");
        }

        private static boolean isBodyGroup(String lowerGroupName) {
            return !lowerGroupName.isBlank();
        }

        private static float getDepthBias(Batch batch, String lowerGroupName, boolean scriptTexture) {
            if (batch == null || scriptTexture) {
                return 0.0F;
            }
            if (isCabControlGroup(lowerGroupName)) {
                return 0.0F;
            }
            if (isScriptDisplayGroup(lowerGroupName)) {
                return 0.0002F;
            }
            if (isLegacyDisplayGroup(lowerGroupName)) {
                return 0.0002F;
            }
            //★ライトグループの頂点押し出しは廃止。
            //本家は頂点を一切押し出さない (RenderVehicleBase.renderBodyLight は
            //ブレンド alpha=0.8 と doRender ゲートだけ)。RTMU が名前で light と判定した
            //グループを 1mm 押し出していたため、室内灯まわりのポリゴンが浮いて
            //「重なって見える」状態になっていた (京葉線等)。
            //発光パスの深度分離は RtmuRenderTypes.emissiveBlendLayered の
            //POLYGON_OFFSET_LAYERING (GPU 側のポリゴンオフセット) が既に担っており、
            //頂点を動かす必要はない。
            if (!batch.translucent) {
                return 0.0F;
            }
            if (lowerGroupName.equals("alpha")) {
                return 0.0012F;
            }
            return 0.0F;
        }

        private static boolean shouldForceLegacyAlphaCutout(Batch batch, String lowerGroupName, boolean scriptTexture) {
            if (batch == null || scriptTexture || !batch.translucent) {
                return false;
            }
            if (lowerGroupName.contains("mask") && !isLegacyDisplayGroup(lowerGroupName)) {
                return true;
            }
            // 透過キーワードの無いグループはカットアウト扱いにする(深度を保つ)
            if (isLegacyDisplayGroup(lowerGroupName) || isScriptDisplayGroup(lowerGroupName)) {
                return false;
            }
            boolean hasTransparencyKeyword = isLegacyTransparentGroupName(lowerGroupName);
            if (!hasTransparencyKeyword) {
                return true;
            }
            return false;
        }

        private static boolean shouldForceDisplayCutout(Batch batch, String lowerGroupName, boolean scriptTexture) {
            if (batch == null || scriptTexture || !batch.translucent) {
                return false;
            }
            return isLegacyDisplayGroup(lowerGroupName) || isScriptDisplayGroup(lowerGroupName);
        }

        private static boolean shouldForceCabCutout(Batch batch, String lowerGroupName, boolean scriptTexture) {
            if (batch == null || scriptTexture || !batch.translucent) {
                return false;
            }
            return isCabControlGroup(lowerGroupName) || lowerGroupName.equals("cabpanel");
        }

        private static boolean shouldForceShaderSafeCutout(Object entity, Batch batch, String lowerGroupName, boolean scriptTexture) {
            if (batch == null || scriptTexture || !batch.translucent || !hasActiveShaderPipeline()) {
                return false;
            }
            if (!(entity instanceof TrainEntity) && !(entity instanceof LargeRailCoreBlockEntity) && !(entity instanceof InstalledObjectBlockEntity)) {
                return false;
            }
            if (isGlassGroup(lowerGroupName) || isLegacyDisplayGroup(lowerGroupName)) {
                return false;
            }
            return true;
        }

        private static boolean isLegacyDisplayGroup(String lowerGroupName) {
            if (lowerGroupName == null || lowerGroupName.isBlank()) {
                return false;
            }
            return lowerGroupName.equals("dest")
                || lowerGroupName.equals("type")
                || lowerGroupName.startsWith("dest") && lowerGroupName.length() > 4 && lowerGroupName.substring(4).chars().allMatch(Character::isDigit)
                || lowerGroupName.startsWith("type") && lowerGroupName.length() > 4 && lowerGroupName.substring(4).chars().allMatch(Character::isDigit);
        }

        private static boolean isScriptDisplayGroup(String lowerGroupName) {
            if (lowerGroupName == null || lowerGroupName.isBlank()) {
                return false;
            }
            return lowerGroupName.equals("sign")
                || lowerGroupName.startsWith("type_")
                || lowerGroupName.matches("s_[td][ab]\\d+");
        }

        private static boolean isCabControlGroup(String lowerGroupName) {
            if (lowerGroupName == null || lowerGroupName.isBlank()) {
                return false;
            }
            return lowerGroupName.equals("f")
                || lowerGroupName.equals("m")
                || lowerGroupName.equals("b")
                || lowerGroupName.equals("n")
                || lowerGroupName.equals("eb")
                || lowerGroupName.matches("b[1-7]")
                || lowerGroupName.matches("p[1-5]");
        }

        private static int computeReflectionAlpha(Matrix4f pose, float nx, float ny, float nz, float x, float y, float z, boolean glass) {
            float normalLenSq = nx * nx + ny * ny + nz * nz;
            if (normalLenSq <= 1.0E-8F) {
                return 0;
            }
            float normalInvLen = (float) (1.0D / Math.sqrt(normalLenSq));
            nx *= normalInvLen;
            ny *= normalInvLen;
            nz *= normalInvLen;

            float vx = pose.m00() * x + pose.m10() * y + pose.m20() * z + pose.m30();
            float vy = pose.m01() * x + pose.m11() * y + pose.m21() * z + pose.m31();
            float vz = pose.m02() * x + pose.m12() * y + pose.m22() * z + pose.m32();
            float viewLenSq = vx * vx + vy * vy + vz * vz;
            if (viewLenSq <= 1.0E-8F) {
                return 0;
            }
            float viewInvLen = (float) (1.0D / Math.sqrt(viewLenSq));
            vx = -vx * viewInvLen;
            vy = -vy * viewInvLen;
            vz = -vz * viewInvLen;

            float fresnel = 1.0F - Math.max(0.0F, nx * vx + ny * vy + nz * vz);
            fresnel *= fresnel;
            fresnel *= fresnel;
            float skyBias = Math.max(0.0F, ny) * 0.35F;
            float strength = glass ? 0.20F : 0.06F;
            float alpha = (fresnel * 0.8F + skyBias) * strength;
            int maxAlpha = glass ? 52 : 24;
            return Math.min(maxAlpha, Math.max(0, Math.round(alpha * 255.0F)));
        }

        private boolean shouldSkipLegacyPlaceholderGroup(String groupName) {
            if (groupName == null || groupName.isBlank()) {
                return false;
            }
            String lower = groupName.trim().toLowerCase(Locale.ROOT);
            if (lower.equals("dest") && hasGroupNamed("dest0")) {
                return true;
            }
            if (lower.equals("type") && hasGroupNamed("type0")) {
                return true;
            }
            return lower.equals("lever") && (
                hasGroupNamed("L_F")
                    || hasGroupNamed("L_M")
                    || hasGroupNamed("L_B")
            );
        }
    }

    public static void renderModel(MqoModel model, PoseStack poseStack, MultiBufferSource buffer, int packedLight, GroupPredicate groupFilter) {
        if (model == null) return;
        model.render(poseStack, buffer, packedLight, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, groupFilter);
    }

    public static void renderModelWithoutScript(MqoModel model, PoseStack poseStack, MultiBufferSource buffer, int packedLight, int overlay, boolean translucent, GroupPredicate groupFilter, TrainScriptSystem.ScriptModelRenderer renderer) {
        if (model == null) return;
        model.renderInternal(poseStack, buffer, packedLight, overlay, translucent, groupFilter, null, renderer, null);
    }

    public static void renderModelWithoutScript(MqoModel model, PoseStack poseStack, MultiBufferSource buffer,
                                                int packedLight, int overlay, boolean translucent,
                                                GroupPredicate groupFilter, GroupTransform groupTransform, Object entity) {
        if (model == null) return;
        model.renderInternal(poseStack, buffer, packedLight, overlay, translucent, groupFilter, groupTransform, model.scriptRenderer, entity);
    }

    public static void renderModelColorOverlay(MqoModel model, PoseStack poseStack, MultiBufferSource buffer,
                                               int overlay, GroupPredicate groupFilter,
                                               int red, int green, int blue, int alpha) {
        if (model == null) return;
        model.renderColorOverlay(poseStack, buffer, overlay, groupFilter, red, green, blue, alpha);
    }

    public static void renderModel(MqoModel model, PoseStack poseStack, MultiBufferSource buffer, int packedLight, GroupPredicate groupFilter, GroupTransform groupTransform) {
        if (model == null) return;
        model.render(poseStack, buffer, packedLight, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, groupFilter, groupTransform);
    }

    public static void renderModel(MqoModel model, PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                                   GroupPredicate groupFilter, GroupTransform groupTransform, Object entity) {
        if (model == null) return;
        model.renderPreferScript(poseStack, buffer, packedLight, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, groupFilter, groupTransform, entity);
    }

    private static final class Batch {
        final int order;
        final String groupName;
        final String groupNameLower;
        final ResourceLocation texture;
        final ResourceLocation[] emissiveTextures;
        final boolean translucent;
        final int materialId;
        /** マテリアル col の不透明度 (1.0=不透明)。半透明ガラス等は <1。描画時に色αへ乗算。 */
        float baseAlpha = 1.0F;
        /** マテリアル col の RGB (色タイント)。白 (1,1,1) 以外は描画時に頂点色へ乗算。 */
        float baseColorR = 1.0F;
        float baseColorG = 1.0F;
        float baseColorB = 1.0F;
        /** テクスチャが明確なガラス帯を持つ=本当の半透明。強制カットアウトを免除する。 */
        boolean glassTranslucent = false;
        /** テクスチャに中間アルファのピクセル (ガラス帯/網等) があるか。無ければ pass1 は描く物が無い。 */
        boolean texHasTranslucentPixels = false;
        /** pass1 で色が出るテクセルの粗いマスク ({@link MqoModelLoader#buildPass1Mask})。 */
        java.util.BitSet pass1Mask = null;
        /** depthBias押し出し用の頂点法線。nullなら頂点法線で押す。 */
        float[] biasNormals = null;
        /** RTM pass0(不透明描画)用のアルファテスト相当テクスチャ。 */
        ResourceLocation opaqueTexture = null;
        /** RTM pass1(半透明)用の窓ガラスのみテクスチャ。 */
        ResourceLocation windowTexture = null;
        /** このバッチをpass0で最後に描いたフレーム番号。 */
        long lastOpaqueDrawFrame = -1L;
        final float[] data;
        final int vertexCount;
        // scriptTexture=false 時の事前計算結果。SL/通常列車は大半の batch で
        // scriptTexture=false なので毎フレームの string 操作 (contains/startsWith)
        // を 1 度の構築時計算に置換できる。
        boolean cachedForceCutoutNoScriptTex;
        float cachedDepthBiasNoScriptTex;
        boolean cachedComputed;
        final float minU;
        final float maxU;
        final float minV;
        final float maxV;

        // GPU VBO キャッシュ。フルブライト経路の同じ batch を毎フレーム再
        // ビルドしていた CPU コストを 1 回ビルド + GPU 側 modelview 変換に
        // 置き換える。scriptRenderer のテクスチャ/UV/色変更が無い場合のみ使用。
        volatile com.mojang.blaze3d.vertex.VertexBuffer fullbrightVbo;
        volatile boolean vboBuildAttempted;
        volatile boolean vboBuildFailed;

        Batch(int order, String groupName, ResourceLocation texture, ResourceLocation[] emissiveTextures, float[] data, int vertexCount, int materialId, boolean translucent,
              float minU, float maxU, float minV, float maxV) {
            this.order = order;
            this.groupName = groupName;
            this.groupNameLower = groupName == null ? "" : groupName.toLowerCase(Locale.ROOT);
            this.texture = texture;
            this.emissiveTextures = emissiveTextures == null ? new ResourceLocation[0] : emissiveTextures;
            this.translucent = translucent;
            this.materialId = materialId;
            this.data = data;
            this.vertexCount = vertexCount;
            this.minU = minU;
            this.maxU = maxU;
            this.minV = minV;
            this.maxV = maxV;
        }

        ResourceLocation emissiveTextureForPass(int pass) {
            int index = pass - 2;
            if (index < 0 || index >= emissiveTextures.length) {
                return null;
            }
            return emissiveTextures[index];
        }

        /** フルブライト経路用VBO。bias値ごとにキャッシュ。 */
        //エンティティ経路の静的VBO。ライト値は頂点に焼く
        private com.mojang.blaze3d.vertex.VertexBuffer entityVbo;
        private int entityVboLight = Integer.MIN_VALUE;
        private boolean entityVboFailed;

        /** pass1 で実際に描かれる面だけを抜いた頂点列 (遅延生成)。{@link #pass1VertexCount} が -1 なら未計算。 */
        private float[] pass1Data;
        private float[] pass1BiasNormals;
        private int pass1VertexCount = -1;

        float[] pass1Data() {
            ensurePass1();
            return pass1Data;
        }

        /** {@link #pass1Data()} と添字が対応する biasNormals (深度押し出し用)。 */
        float[] pass1BiasNormals() {
            ensurePass1();
            return pass1BiasNormals;
        }

        int pass1VertexCount() {
            ensurePass1();
            return pass1VertexCount;
        }

        /** pass1で提出する面を窓テクセルを踏むものに絞る。マスク無し/UV範囲外は全面。 */
        private void ensurePass1() {
            if (pass1VertexCount >= 0) {
                return;
            }
            if (pass1Mask == null || data == null || vertexCount <= 0 || (vertexCount & 3) != 0) {
                pass1Data = data;
                pass1BiasNormals = biasNormals;
                pass1VertexCount = vertexCount;
                return;
            }
            float[] out = new float[data.length];
            float[] outBias = biasNormals != null ? new float[biasNormals.length] : null;
            int n = 0;
            for (int q = 0; q + 3 < vertexCount; q += 4) {
                float u0 = Float.MAX_VALUE, u1 = -Float.MAX_VALUE;
                float v0 = Float.MAX_VALUE, v1 = -Float.MAX_VALUE;
                for (int k = 0; k < 4; k++) {
                    int o = (q + k) * 8;
                    float u = data[o + 6];
                    float v = data[o + 7];
                    if (u < u0) u0 = u;
                    if (u > u1) u1 = u;
                    if (v < v0) v0 = v;
                    if (v > v1) v1 = v;
                }
                if (pass1MaskHits(u0, u1, v0, v1)) {
                    System.arraycopy(data, q * 8, out, n * 8, 32);
                    if (outBias != null) {
                        System.arraycopy(biasNormals, q * 3, outBias, n * 3, 12);
                    }
                    n += 4;
                }
            }
            pass1VertexCount = n;
            if (n == vertexCount) {
                pass1Data = data;
                pass1BiasNormals = biasNormals;
            } else {
                pass1Data = java.util.Arrays.copyOf(out, n * 8);
                pass1BiasNormals = outBias != null ? java.util.Arrays.copyOf(outBias, n * 3) : null;
            }
        }

        private boolean pass1MaskHits(float u0, float u1, float v0, float v1) {
            //タイリング等で UV が [0,1] を外れる面はマスクを引けない → 安全側で描く
            if (!(u0 >= -0.001F && u1 <= 1.001F && v0 >= -0.001F && v1 <= 1.001F)) {
                return true;
            }
            int x0 = clampMaskIndex((int) Math.floor(u0 * PASS1_MASK_RES));
            int x1 = clampMaskIndex((int) Math.ceil(u1 * PASS1_MASK_RES));
            int y0 = clampMaskIndex((int) Math.floor(v0 * PASS1_MASK_RES));
            int y1 = clampMaskIndex((int) Math.ceil(v1 * PASS1_MASK_RES));
            for (int y = y0; y <= y1; y++) {
                int row = y * PASS1_MASK_RES;
                for (int x = x0; x <= x1; x++) {
                    if (pass1Mask.get(row + x)) {
                        return true;
                    }
                }
            }
            return false;
        }

        private static int clampMaskIndex(int v) {
            return v < 0 ? 0 : Math.min(v, PASS1_MASK_RES - 1);
        }

        com.mojang.blaze3d.vertex.VertexBuffer getOrBuildEntityVbo(int packedLight) {
            if (entityVboFailed || vertexCount <= 0) {
                return null;
            }
            if (entityVbo != null && entityVboLight == packedLight && !entityVbo.isInvalid()) {
                return entityVbo;
            }
            try {
                com.mojang.blaze3d.vertex.BufferBuilder bb = com.mojang.blaze3d.vertex.Tesselator.getInstance().begin(
                    com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,
                    com.mojang.blaze3d.vertex.DefaultVertexFormat.NEW_ENTITY);
                for (int i = 0; i < vertexCount; i++) {
                    int o = i * 8;
                    bb.addVertex(data[o], data[o + 1], data[o + 2])
                        .setColor(255, 255, 255, 255)
                        .setUv(data[o + 6], data[o + 7])
                        .setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                        .setLight(packedLight)
                        .setNormal(data[o + 3], data[o + 4], data[o + 5]);
                }
                com.mojang.blaze3d.vertex.MeshData mesh = bb.build();
                if (mesh == null) {
                    return null;
                }
                if (entityVbo == null || entityVbo.isInvalid()) {
                    entityVbo = new com.mojang.blaze3d.vertex.VertexBuffer(
                        com.mojang.blaze3d.vertex.VertexBuffer.Usage.STATIC);
                }
                entityVbo.bind();
                entityVbo.upload(mesh);
                com.mojang.blaze3d.vertex.VertexBuffer.unbind();
                entityVboLight = packedLight;
                return entityVbo;
            } catch (Throwable t) {
                entityVboFailed = true;
                return null;
            }
        }

        com.mojang.blaze3d.vertex.VertexBuffer getOrBuildFullbrightVbo() {
            return getOrBuildFullbrightVbo(0.0F);
        }

        private volatile java.util.Map<Float, com.mojang.blaze3d.vertex.VertexBuffer> biasedVbos;

        com.mojang.blaze3d.vertex.VertexBuffer getOrBuildFullbrightVbo(float bias) {
            if (bias == 0.0F) {
                if (vboBuildFailed) return null;
                if (fullbrightVbo != null) return fullbrightVbo;
                synchronized (this) {
                    if (fullbrightVbo == null && !vboBuildFailed) {
                        vboBuildAttempted = true;
                        com.mojang.blaze3d.vertex.VertexBuffer vbo = buildVbo(0.0F);
                        if (vbo == null) vboBuildFailed = true;
                        else fullbrightVbo = vbo;
                    }
                }
                return fullbrightVbo;
            }
            // bias != 0: 別キャッシュ
            java.util.Map<Float, com.mojang.blaze3d.vertex.VertexBuffer> map = biasedVbos;
            if (map != null) {
                com.mojang.blaze3d.vertex.VertexBuffer cached = map.get(bias);
                if (cached != null) return cached;
            }
            synchronized (this) {
                if (biasedVbos == null) biasedVbos = new java.util.HashMap<>(2);
                com.mojang.blaze3d.vertex.VertexBuffer cached = biasedVbos.get(bias);
                if (cached != null) return cached;
                com.mojang.blaze3d.vertex.VertexBuffer vbo = buildVbo(bias);
                if (vbo != null) biasedVbos.put(bias, vbo);
                return vbo;
            }
        }

        private com.mojang.blaze3d.vertex.VertexBuffer buildVbo(float bias) {
            if (vertexCount <= 0) return null;
            try {
                com.mojang.blaze3d.vertex.BufferBuilder bb = com.mojang.blaze3d.vertex.Tesselator.getInstance().begin(
                    com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,
                    com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL);
                for (int i = 0; i < vertexCount; i++) {
                    int o = i * 8;
                    float x = data[o], y = data[o + 1], z = data[o + 2];
                    float nx = data[o + 3], ny = data[o + 4], nz = data[o + 5];
                    float u = data[o + 6], v = data[o + 7];
                    if (bias != 0.0F) {
                        //押し出しは biasNormals (同位置の全面平均)。面割れ防止。
                        float bnx = nx, bny = ny, bnz = nz;
                        if (biasNormals != null) {
                            int bo = i * 3;
                            bnx = biasNormals[bo]; bny = biasNormals[bo + 1]; bnz = biasNormals[bo + 2];
                        }
                        float il = (float)(1.0D / Math.sqrt(Math.max(1.0E-8F, bnx*bnx + bny*bny + bnz*bnz)));
                        x += bnx*il*bias; y += bny*il*bias; z += bnz*il*bias;
                    }
                    bb.addVertex(x, y, z)
                        .setUv(u, v)
                        .setColor(255, 255, 255, 255)
                        .setNormal(nx, ny, nz);
                }
                com.mojang.blaze3d.vertex.MeshData mesh = bb.build();
                if (mesh == null) return null;
                com.mojang.blaze3d.vertex.VertexBuffer vbo = new com.mojang.blaze3d.vertex.VertexBuffer(
                    com.mojang.blaze3d.vertex.VertexBuffer.Usage.STATIC);
                vbo.bind();
                vbo.upload(mesh);
                com.mojang.blaze3d.vertex.VertexBuffer.unbind();
                return vbo;
            } catch (Throwable t) {
                return null;
            }
        }

        /** このバッチのGPU VBOを解放する(描画スレッドで呼ぶこと)。 */
        void closeVbosNow() {
            if (fullbrightVbo != null) {
                if (!fullbrightVbo.isInvalid()) fullbrightVbo.close();
                fullbrightVbo = null;
            }
            vboBuildFailed = false;
            vboBuildAttempted = false;
            if (entityVbo != null) {
                if (!entityVbo.isInvalid()) entityVbo.close();
                entityVbo = null;
            }
            entityVboLight = Integer.MIN_VALUE;
            entityVboFailed = false;
            java.util.Map<Float, com.mojang.blaze3d.vertex.VertexBuffer> bv = biasedVbos;
            if (bv != null) {
                for (com.mojang.blaze3d.vertex.VertexBuffer v : bv.values()) {
                    if (v != null && !v.isInvalid()) v.close();
                }
                biasedVbos = null;
            }
        }
    }

    private static final class CachedModel {
        private final MqoModel model;
        private final long estimatedBytes;
        private long lastAccessNanos;

        CachedModel(MqoModel model, long estimatedBytes, long lastAccessNanos) {
            this.model = model;
            this.estimatedBytes = Math.max(1L, estimatedBytes);
            this.lastAccessNanos = lastAccessNanos;
        }

        MqoModel model() {
            return model;
        }

        long estimatedBytes() {
            return estimatedBytes;
        }

        long lastAccessNanos() {
            return lastAccessNanos;
        }

        void touch(long now) {
            this.lastAccessNanos = now;
        }
    }

    public static final class ScriptModel {
        public final ScriptMaterialTexture[] textures;
        /**
         * 本家 {@code jp.ngt.rtm.render.ModelObject.model} 相当のモデルグラフ。
         * <p>本家のレンダースクリプトは {@code init(modelSet, modelObj)} の第2引数から
         * {@code modelObj.model.groupObjects} でオブジェクト名を列挙する (E257 等)。
         * ここが無いと init が TypeError で落ち、Parts が 1 つも登録されないまま
         * render が即 return して車体が本来の形で描かれない。
         */
        public jp.ngt.ngtlib.renderer.model.PolygonModel model;
        // レンダー中のrendererを保持(スクリプトからの部品描画委譲用)
        private transient com.portofino.realtrainmodunofficial.script.TrainScriptSystem.ScriptModelRenderer activeRenderer;

        ScriptModel(List<ResourceLocation> materialTextures) {
            this.textures = new ScriptMaterialTexture[materialTextures.size()];
            for (int i = 0; i < materialTextures.size(); i++) {
                this.textures[i] = new ScriptMaterialTexture(new ScriptMaterial(materialTextures.get(i)));
            }
        }

        public void setActiveRenderer(com.portofino.realtrainmodunofficial.script.TrainScriptSystem.ScriptModelRenderer renderer) {
            this.activeRenderer = renderer;
        }

        // ---- 旧 RTM レンダースクリプト用 API ----
        public void renderPart(String group) {
            if (activeRenderer != null && group != null) activeRenderer.renderParts(group);
        }
        public void renderParts(Object groups) {
            if (activeRenderer != null && groups != null) activeRenderer.renderParts(groups);
        }
        public void renderAll() {
            // renderAll 相当: 空文字列で renderParts を呼ぶと全部品扱いの仕様。
            if (activeRenderer != null) activeRenderer.renderParts("*");
        }
        public void renderOnly(Object groups) {
            if (activeRenderer != null && groups != null) activeRenderer.renderParts(groups);
        }
        public void render(Object groups) {
            renderParts(groups);
        }
    }

    public static final class ScriptMaterialTexture {
        public ScriptMaterial material;

        ScriptMaterialTexture(ScriptMaterial material) {
            this.material = material;
        }
    }

    public static final class ScriptMaterial {
        public Object texture;

        ScriptMaterial(ResourceLocation texture) {
            this.texture = new ScriptTexture(texture);
        }
    }

    public static final class ScriptTexture {
        public String namespace;
        public String domain;
        public String path;
        public String resourcePath;

        ScriptTexture(ResourceLocation resource) {
            this.namespace = resource.getNamespace();
            this.domain = this.namespace;
            this.path = resource.getPath();
            this.resourcePath = this.path;
        }

        public String func_110624_b() {
            return namespace;
        }

        public String func_110623_a() {
            return path;
        }
    }
}
