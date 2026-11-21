package jp.ngt.rtm.modelpack;

import jp.ngt.ngtlib.io.NGTJson;
import jp.ngt.rtm.modelpack.cfg.ModelConfig;
import jp.ngt.rtm.modelpack.cfg.TrainConfig;
import jp.ngt.rtm.modelpack.cfg.VehicleConfig;
import jp.ngt.rtm.modelpack.modelset.ModelSetBase;
import jp.ngt.rtm.modelpack.modelset.ModelSetTrain;
import jp.ngt.rtm.modelpack.modelset.ModelSetVehicle;

/** 本家 jp.ngt.rtm.modelpack.ModelPackManager のスクリプト互換… */
public final class ModelPackManager {
    public static final ModelPackManager INSTANCE = new ModelPackManager();

    /**
     * KaizPatchX 処理系: registerModelset(type, json) で json を Gson リフレクション
     * (NGTJson.getObjectFromJson) により cfg クラスへ直接読み込み、cfg.init() 後に
     * リフレクションで ModelSet を生成・登録する。
     */
    private final java.util.Map<String, java.util.Map<String, ModelSetBase>> registeredModelSetMap =
            new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<String, TypeEntry> typeMap = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<String, ModelSetBase> dummyMap = new java.util.concurrent.ConcurrentHashMap<>();

    static {
        // 本家 CommonProxy/ClientProxy の registerType と同じ (RTMU が扱う車両系のみ)
        INSTANCE.registerType("ModelTrain", TrainConfig.class, ModelSetTrain.class);
        INSTANCE.registerType("ModelVehicle", VehicleConfig.class, ModelSetVehicle.class);
    }

    private ModelPackManager() {
    }

    public jp.ngt.mccompat.ResourceLocation getResource(String domain, String path) {
        return new jp.ngt.mccompat.ResourceLocation(domain, path);
    }

    /** 本家 getResource(String): "domain:path" を分割 (既定 dom… */
    public jp.ngt.mccompat.ResourceLocation getResource(String path) {
        String domain = "minecraft";
        if (path != null && path.contains(":")) {
            String[] sa = path.split(":", 2);
            domain = sa[0];
            path = sa[1];
        }
        return getResource(domain, path);
    }

    /** 本家は ResourceLocation を取る。 */
    public String getScript(Object resource) {
        if (resource == null) {
            return null;
        }
        if (resource instanceof String s) {
            return getScript(s);
        }
        if (resource instanceof jp.ngt.mccompat.ResourceLocation rl) {
            return getScript(rl.toString());
        }
        if (resource instanceof net.minecraft.resources.ResourceLocation rl) {
            return getScript(rl.toString());
        }
        return getScript(String.valueOf(resource));
    }

    /** 本家 SC_INCLUDE: //include <path> を再帰展開する。 */
    private static final java.util.regex.Pattern SC_INCLUDE =
            java.util.regex.Pattern.compile("//include <(.+)>");

    /** 本家 scriptCache: fileName → include 展開済みソース。 */
    private final java.util.Map<String, String> scriptCache = new java.util.concurrent.ConcurrentHashMap<>();

    /** 本家 ModelPackManager.getScript(String) 完全移植。 */
    public String getScript(String path) {
        if (path == null) {
            return null;
        }
        return this.loadScript(path);
    }

    /** 本家 ModelPackManager.loadScript 完全移植 (include 再帰 +… */
    private String loadScript(String fileName) {
        String cached = this.scriptCache.get(fileName);
        if (cached != null) {
            return cached;
        }

        byte[] bytes = jp.ngt.ngtlib.io.NGTFileLoader.findAsset(fileName);
        if (bytes == null) {
            return null;
        }
        // 本家は indention=true で読む (改行保持)。
        String rawScript = jp.ngt.ngtlib.io.NGTText.decodeText(bytes)
                .replaceAll("\r\n|[\n\r\u2028\u2029\u0085]", "\n");
        while (true) {
            java.util.regex.Matcher matcher = SC_INCLUDE.matcher(rawScript);
            if (!matcher.find()) {
                break;
            }
            String includePath = matcher.group(1);
            String rep = this.loadScript(includePath);
            if (rep == null) {
                throw new IllegalStateException(
                        "Missing script include: " + includePath + " (required by " + fileName + ")");
            }
            rawScript = matcher.replaceFirst(java.util.regex.Matcher.quoteReplacement(rep));
        }

        this.scriptCache.put(fileName, rawScript);
        return rawScript;
    }

    /** キャッシュ破棄 (診断コマンド用)。 */
    public void clearScriptCache() {
        this.scriptCache.clear();
    }

    /** 本家 getModelSet(type, name)。 */
    /* 本家 ModelPackManager と同じく、モデル定義ごとに ModelSet を 1 個だ… */
    private final java.util.Map<String, jp.ngt.rtm.modelpack.modelset.ModelSetBase<?>> trainModelSets =
            new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<String, jp.ngt.rtm.modelpack.modelset.ModelSetBase<?>> vehicleModelSets =
            new java.util.concurrent.ConcurrentHashMap<>();

    private final java.util.Map<String, jp.ngt.rtm.modelpack.modelset.ModelSetBase<?>> scriptOnlyModelSets =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** NPC / 設置物 / 機械など、ModelConfig を直接持たない定義用。 */
    public jp.ngt.rtm.modelpack.modelset.ModelSetBase<?> getScriptOnlyModelSet(
            String key, String name, String modelType, String serverScriptPath,
            String guiScriptPath, String guiTexture, String buttonTexture) {
        return this.getScriptOnlyModelSet(key, name, modelType, serverScriptPath,
                guiScriptPath, guiTexture, buttonTexture, null, null);
    }

    public jp.ngt.rtm.modelpack.modelset.ModelSetBase<?> getScriptOnlyModelSet(
            String key, String name, String modelType, String serverScriptPath,
            String guiScriptPath, String guiTexture, String buttonTexture,
            float[] offset, float[] modelBodyPos) {
        if (key == null) {
            key = name;
        }
        jp.ngt.rtm.modelpack.modelset.ModelSetBase<?> cached = this.scriptOnlyModelSets.get(key);
        if (cached != null) {
            return cached;
        }
        jp.ngt.rtm.modelpack.modelset.ModelSetBase<?> created =
                new jp.ngt.rtm.modelpack.modelset.ModelSetScriptOnly(
                        name, modelType, serverScriptPath, guiScriptPath, guiTexture, buttonTexture,
                        offset, modelBodyPos);
        jp.ngt.rtm.modelpack.modelset.ModelSetBase<?> previous = this.scriptOnlyModelSets.putIfAbsent(key, created);
        return previous != null ? previous : created;
    }

    /** 定義を削除/再読み込みしたときに ModelSet を作り直す。 */
    public void forgetModelSet(String name) {
        if (name == null) {
            return;
        }
        this.trainModelSets.remove(name);
        this.vehicleModelSets.remove(name);
        this.scriptOnlyModelSets.remove(name);
    }

    public void clearModelSetCache() {
        this.registeredModelSetMap.values().forEach(java.util.Map::clear);
        this.trainModelSets.clear();
        this.vehicleModelSets.clear();
        this.scriptOnlyModelSets.clear();
        jp.ngt.rtm.rail.util.RailProperty.clearModelSetCache();
        this.clearScriptCache();
    }

    public jp.ngt.rtm.modelpack.modelset.ModelSetBase<?> getTrainModelSet(String name) {
        if (name == null || name.isEmpty()) {
            return new jp.ngt.rtm.modelpack.modelset.ModelSetTrain();
        }
        jp.ngt.rtm.modelpack.modelset.ModelSetBase<?> cached = this.trainModelSets.get(name);
        if (cached != null) {
            return cached;
        }
        com.portofino.realtrainmodunofficial.vehicle.VehicleDefinition def =
                com.portofino.realtrainmodunofficial.vehicle.VehicleRegistry.getById(name);
        if (def == null) {
            def = com.portofino.realtrainmodunofficial.vehicle.VehicleRegistry.getAll().stream()
                    .filter(candidate -> candidate.getDisplayName().equals(name))
                    .findFirst().orElse(null);
        }
        jp.ngt.rtm.modelpack.modelset.ModelSetBase<?> created;
        if (def == null) {
            created = new jp.ngt.rtm.modelpack.modelset.ModelSetTrain();
        } else {
            jp.ngt.rtm.modelpack.cfg.TrainConfig cfg =
                    jp.ngt.rtm.modelpack.cfg.TrainConfigAdapter.get(def.getId());
            if (cfg.serverScriptPath == null || cfg.serverScriptPath.isBlank()) {
                cfg.serverScriptPath = def.getServerScriptPath();
            }
            if (cfg.soundScriptPath == null || cfg.soundScriptPath.isBlank()) {
                cfg.soundScriptPath = def.getSoundScriptPath();
            }
            if (cfg.getModel() != null
                    && (cfg.getModel().rendererPath == null || cfg.getModel().rendererPath.isBlank())) {
                cfg.getModel().rendererPath = def.getScriptPath();
            }
            try {
                created = net.neoforged.fml.loading.FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.CLIENT
                        ? new jp.ngt.rtm.modelpack.modelset.ModelSetTrainClient(cfg)
                        : new jp.ngt.rtm.modelpack.modelset.ModelSetTrain(cfg);
            } catch (Throwable t) {
                // 1 両の壊れたスクリプトでサーバーの tick を落とさない。
                com.portofino.realtrainmodunofficial.RealTrainModUnofficial.LOGGER.error(
                        "[RTMU] train model set load failed: {}", def.getId(), t);
                created = new jp.ngt.rtm.modelpack.modelset.ModelSetTrain();
            }
        }
        jp.ngt.rtm.modelpack.modelset.ModelSetBase<?> previous = this.trainModelSets.putIfAbsent(name, created);
        return previous != null ? previous : created;
    }

    public jp.ngt.rtm.modelpack.modelset.ModelSetBase<?> getVehicleModelSet(String name) {
        if (name == null || name.isEmpty()) {
            return new jp.ngt.rtm.modelpack.modelset.ModelSetVehicle();
        }
        jp.ngt.rtm.modelpack.modelset.ModelSetBase<?> cached = this.vehicleModelSets.get(name);
        if (cached != null) {
            return cached;
        }
        com.portofino.realtrainmodunofficial.vehicle.VehicleDefinition def =
                com.portofino.realtrainmodunofficial.vehicle.VehicleRegistry.getById(name);
        if (def == null) {
            def = com.portofino.realtrainmodunofficial.vehicle.VehicleRegistry.getAll().stream()
                    .filter(candidate -> candidate.getDisplayName().equals(name))
                    .findFirst().orElse(null);
        }
        jp.ngt.rtm.modelpack.modelset.ModelSetBase<?> created;
        if (def == null) {
            created = new jp.ngt.rtm.modelpack.modelset.ModelSetVehicle();
        } else {
            jp.ngt.rtm.modelpack.cfg.VehicleConfig cfg =
                    jp.ngt.rtm.modelpack.cfg.VehicleConfigAdapter.get(def);
            if (cfg.serverScriptPath == null || cfg.serverScriptPath.isBlank()) {
                cfg.serverScriptPath = def.getServerScriptPath();
            }
            if (cfg.soundScriptPath == null || cfg.soundScriptPath.isBlank()) {
                cfg.soundScriptPath = def.getSoundScriptPath();
            }
            try {
                created = net.neoforged.fml.loading.FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.CLIENT
                        ? new jp.ngt.rtm.modelpack.modelset.ModelSetVehicleClient(cfg)
                        : new jp.ngt.rtm.modelpack.modelset.ModelSetVehicle(cfg);
            } catch (Throwable t) {
                com.portofino.realtrainmodunofficial.RealTrainModUnofficial.LOGGER.error(
                        "[RTMU] vehicle model set load failed: {}", def.getId(), t);
                created = new jp.ngt.rtm.modelpack.modelset.ModelSetVehicle();
            }
        }
        jp.ngt.rtm.modelpack.modelset.ModelSetBase<?> previous = this.vehicleModelSets.putIfAbsent(name, created);
        return previous != null ? previous : created;
    }

    public jp.ngt.rtm.modelpack.modelset.ModelSetBase<?> getModelSet(String type, String name) {
        // KaizPatchX 処理系で登録済みなら登録済み ModelSet を優先して返す
        java.util.Map<String, ModelSetBase> registered = this.registeredModelSetMap.get(type);
        if (registered != null && name != null) {
            ModelSetBase set = registered.get(name);
            if (set != null) {
                return set;
            }
        }
        if("ModelTrain".equals(type)) {
            return getTrainModelSet(name);
        }
        if("ModelVehicle".equals(type)) {
            return getVehicleModelSet(name);
        }
        if("ModelRail".equals(type)) {
            var definition=com.portofino.realtrainmodunofficial.rail.RailRegistry.getById(name);
            return definition==null?new jp.ngt.rtm.modelpack.modelset.ModelSetRail():new jp.ngt.rtm.rail.util.RailProperty(name,net.minecraft.world.level.block.Blocks.GRAVEL,0,0.0625F).getModelSet();
        }
        com.portofino.realtrainmodunofficial.installedobject.InstalledObjectDefinition def =
            com.portofino.realtrainmodunofficial.installedobject.InstalledObjectRegistry.getByBareName(
                name, com.portofino.realtrainmodunofficial.installedobject.InstalledObjectCategory.INSULATOR);
        return new ModelSetShim(def);
    }

    /** getModelSet の戻り値: getConfig.wirePos (double[3]) と… */
    public static final class ModelSetShim extends jp.ngt.rtm.modelpack.modelset.ModelSetBase<ConfigShim> {
        private final ConfigShim config;
        private final boolean missing;

        ModelSetShim(com.portofino.realtrainmodunofficial.installedobject.InstalledObjectDefinition def) {
            super(new ConfigShim(def));
            this.config = new ConfigShim(def);this.missing=def==null;
        }

        public ConfigShim getDummyConfig() {return new ConfigShim(null);}
        @Override public boolean isDummy() {return missing;}
        public ConfigShim getConfig() {
            return config;
        }
    }

    public static final class ConfigShim {
        /** 本家 ConnectorConfig.wirePos ([x,y,z])。 */
        public final double[] wirePos;
        private final String name;

        ConfigShim(com.portofino.realtrainmodunofficial.installedobject.InstalledObjectDefinition def) {
            if (def != null && def.getWireAttachPos() != null) {
                net.minecraft.world.phys.Vec3 wp = def.getWireAttachPos();
                this.wirePos = new double[]{wp.x, wp.y, wp.z};
            } else {
                this.wirePos = new double[]{0.0D, -0.5D, 0.0D};  // 本家の既定 (モデル無し碍子)
            }
            this.name = def == null ? "" : def.getBareName();
        }

        public String getName() {
            return name;
        }
    }

    /** 本家getType: 型キー(そのまま返す)。 */
    public String getType(String type) {
        return type;
    }

    /** 本家getModelList: 登録済み (文字列型キー) / 未対応=空。 */
    public java.util.List<Object> getModelList(Object type) {
        if (type instanceof String s && this.typeMap.containsKey(s)) {
            return new java.util.ArrayList<>(this.getModelList(s));
        }
        return new java.util.ArrayList<>();
    }

    /** 本家getModelFile: モデルグラフを返す(無ければ空)。 */
    public jp.ngt.ngtlib.renderer.model.PolygonModel getModelFile(String key) {
        try {
            byte[] bytes = jp.ngt.ngtlib.io.NGTFileLoader.findAsset(key);
            if (bytes == null) {
                bytes = jp.ngt.ngtlib.io.NGTFileLoader.findAsset("models/" + key);
            }
            if (bytes != null) {
                return jp.ngt.ngtlib.renderer.model.ModelLoader.parse(bytes, key);
            }
        } catch (Exception ignored) {
        }
        return new jp.ngt.ngtlib.renderer.model.PolygonModel();
    }

    /** 本家loadModel。 */
    public jp.ngt.ngtlib.renderer.model.PolygonModel loadModel(String modelName, int drawMode, boolean addModelMap, Object cfg) {
        return getModelFile(modelName);
    }

    /** 本家registerType(Object): スクリプト互換 no-op。 */
    public void registerType(String type, Object cfg, Object set) {
    }

    /** 本家addModelSetName: no-op。 */
    public void addModelSetName(int count, String type, String name) {
    }

    /** 本家registerModelset(Object...): スクリプト互換 no-op。 */
    public void registerModelset(Object... args) {
    }

    // ---- KaizPatchX 処理系 (registerType / registerModelset / getModelList) ----

    /** ModelSetのタイプを登録 (本家 registerType と同一処理)。 */
    public void registerType(String type, Class<? extends ModelConfig> cfg, Class<? extends ModelSetBase> set) {
        TypeEntry entry = new TypeEntry(cfg, set);
        this.typeMap.put(type, entry);
        this.registeredModelSetMap.put(type, new java.util.concurrent.ConcurrentHashMap<>());
        ModelSetBase dummy = this.getNewModelSet(entry, new Class[]{});
        this.dummyMap.put(type, dummy);
    }

    /** jsonからモデルを登録 (本家 registerModelset と同一処理)。@return モデル名 */
    public String registerModelset(String type, String json) {
        return this.registerModelset(type, json, null);
    }

    public String registerModelset(String type, String json, String sourceFile) {
        TypeEntry entry = this.typeMap.get(type);
        if (entry == null) {
            throw new ModelPackException("Unknown model type", type);
        }
        ModelConfig cfg = NGTJson.getObjectFromJson(json, entry.cfgClass);
        cfg.init();
        ModelSetBase set = this.getNewModelSet(entry, new Class[]{entry.cfgClass}, cfg);
        String name = cfg.getName();
        if (name == null || name.isEmpty()) {
            throw new ModelPackException("Model name is missing", sourceFile);
        }
        this.registeredModelSetMap.get(type).compute(name, (n, previousSet) -> set);
        return name;
    }

    private ModelSetBase getNewModelSet(TypeEntry entry, Class[] parameterTypes, Object... parameters) {
        try {
            java.lang.reflect.Constructor cons0 = entry.modelsetClass.getConstructor(parameterTypes);
            return (ModelSetBase) cons0.newInstance(parameters);
        } catch (ReflectiveOperationException e) {
            String name = "";
            if (parameters.length > 0) {
                name = ((ModelConfig) parameters[0]).getName();
            }
            throw new ModelPackException("On construct ModelSet", name, e);
        }
    }

    /** 本家 getModelList(String): 登録済み ModelSet の名前順リスト。 */
    public java.util.List<ModelSetBase> getModelList(String type) {
        java.util.Map<String, ModelSetBase> map = this.registeredModelSetMap.get(type);
        java.util.List<ModelSetBase> list = new java.util.ArrayList<>();
        if (map != null) {
            list.addAll(map.values());
        }
        list.sort(java.util.Comparator.comparing(o -> String.valueOf(((ModelConfig) o.getConfig()).getName())));
        return list;
    }

    public static class TypeEntry {
        public final Class<? extends ModelConfig> cfgClass;
        public final Class<? extends ModelSetBase> modelsetClass;

        public TypeEntry(Class<? extends ModelConfig> cls1, Class<? extends ModelSetBase> cls2) {
            this.cfgClass = cls1;
            this.modelsetClass = cls2;
        }
    }

    /** 本家sendModelSetsToClient: no-op。 */
    public void sendModelSetsToClient(Object... args) {
    }

    /** 本家setModelFile: no-op。 */
    public void setModelFile(Object... args) {
    }

}
