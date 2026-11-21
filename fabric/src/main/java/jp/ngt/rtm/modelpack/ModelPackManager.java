package jp.ngt.rtm.modelpack;

import com.portofino.realtrainmodunofficial.vehicle.VehicleRegistry;
import jp.ngt.mccompat.ResourceLocation;
import jp.ngt.ngtlib.io.NGTFileLoader;
import jp.ngt.ngtlib.io.NGTJson;
import jp.ngt.ngtlib.io.NGTText;
import jp.ngt.rtm.modelpack.cfg.ModelConfig;
import jp.ngt.rtm.modelpack.cfg.TrainConfig;
import jp.ngt.rtm.modelpack.cfg.VehicleConfig;
import jp.ngt.rtm.modelpack.modelset.ModelSetBase;
import jp.ngt.rtm.modelpack.modelset.ModelSetTrain;
import jp.ngt.rtm.modelpack.modelset.ModelSetVehicle;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 本家 KaizPatchX jp.ngt.rtm.modelpack.ModelPackManager の移植。
 *
 * <p>KaizPatchX と同じ処理系:
 * <ul>
 *   <li>{@link #registerType} でタイプ (cfg クラス / ModelSet クラス) を登録</li>
 *   <li>{@link #registerModelset} で json を Gson リフレクション ({@link NGTJson#getObjectFromJson})
 *       により cfg クラスへ直接読み込み、cfg.init() 後にリフレクションで ModelSet を生成・登録</li>
 *   <li>{@link #getModelSet} / {@link #getModelList} で登録済み ModelSet を参照 (無ければダミー)</li>
 *   <li>{@code //include <path>} 再帰展開つきスクリプト取得 ({@link #getScript})</li>
 * </ul>
 *
 * <p>旧 RTMU の「JSON を手で解析して VehicleDefinition を作る」処理は削除済み。
 * 車両定義は VehiclePackLoader がこのマネージャへ登録し、アダプタ
 * (TrainConfigAdapter / VehicleConfigAdapter) は登録済み cfg をそのまま参照する。
 * ModelRail / ModelConnector 等は従来どおり各レジストリへフォールバックする。
 */
public final class ModelPackManager {
    public static final ModelPackManager INSTANCE = new ModelPackManager();

    private static final Pattern SC_INCLUDE = Pattern.compile("//include <(.+)>");

    /**
     * 全ModelSet
     */
    private final Map<String, Map<String, ModelSetBase>> allModelSetMap = new ConcurrentHashMap<>();
    /**
     * SMPで使用可能なModelSet (本家 PacketModelSet 相当が未接続のため常に空)
     */
    private final Map<String, Map<String, ModelSetBase>> smpModelSetMap = new ConcurrentHashMap<>();
    private final Map<String, TypeEntry> typeMap = new ConcurrentHashMap<>();
    private final Map<String, ModelSetBase> dummyMap = new ConcurrentHashMap<>();
    /**
     * モデルファイルのキャッシュ
     */
    private final Map<String, Object> modelFileMap = new ConcurrentHashMap<>();

    static {
        // 本家 CommonProxy/ClientProxy の registerType と同じ (RTMU が扱う車両系のみ)
        INSTANCE.registerType("ModelTrain", TrainConfig.class, ModelSetTrain.class);
        INSTANCE.registerType("ModelVehicle", VehicleConfig.class, ModelSetVehicle.class);
    }

    private ModelPackManager() {
    }

    /**
     * ModelSetのタイプを登録
     */
    public void registerType(String type, Class<? extends ModelConfig> cfg, Class<? extends ModelSetBase> set) {
        TypeEntry entry = new TypeEntry(cfg, set);
        this.typeMap.put(type, entry);
        this.allModelSetMap.put(type, new ConcurrentHashMap<>());
        this.smpModelSetMap.put(type, new ConcurrentHashMap<>());
        ModelSetBase dummy = this.getNewModelSet(entry, new Class[]{});
        this.dummyMap.put(type, dummy);
    }

    /**
     * jsonからモデルを登録 (本家 registerModelset と同一処理)
     *
     * @return モデル名
     */
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
        this.allModelSetMap.get(type).compute(name, (n, previousSet) -> set);
        return name;
    }

    /**
     * SMPの時のみServer側で呼ばれる(PlayerLoggedInEvent)。RTMU はまだネットワーク転送が無いため no-op。
     */
    public void sendModelSetsToClient(Object player) {
    }

    /**
     * SMPの時のみClient側で呼ばれる。RTMU はまだネットワーク転送が無いため no-op。
     */
    public void addModelSetName(int count, String type, String name) {
    }

    private ModelSetBase getNewModelSet(TypeEntry entry, Class[] parameterTypes, Object... parameters) {
        try {
            Constructor cons0 = entry.modelsetClass.getConstructor(parameterTypes);
            return (ModelSetBase) cons0.newInstance(parameters);
        } catch (ReflectiveOperationException e) {
            String name = "";
            if (parameters.length > 0) {
                name = ((ModelConfig) parameters[0]).getName();
            }
            throw new ModelPackException("On construct ModelSet", name, e);
        }
    }

    @SuppressWarnings("unchecked")
    public <T extends ModelSetBase> T getModelSet(String type, String name) {
        boolean isSMPClient = false;// 本家 NGTUtil.isSMP() && !NGTUtil.isServer()。RTMU は未接続
        Map<String, Map<String, ModelSetBase>> map = isSMPClient ? this.smpModelSetMap : this.allModelSetMap;
        Map<String, ModelSetBase> typeMap0 = map.get(type);
        if (typeMap0 != null) {
            ModelSetBase modelSet = typeMap0.get(name);
            if (modelSet != null) {
                return (T) modelSet;
            }
        }
        // 登録されていない (旧経路のレール/設置物/レガシー互換)。従来の shim へフォールバック。
        return (T) this.getFallbackModelSet(type, name);
    }

    /**
     * ModelPackManager 未登録時のフォールバック (旧 shim):
     * レール / 設置物 / VehicleRegistry 経由のレガシー互換。
     */
    private ModelSetBase getFallbackModelSet(String type, String name) {
        if ("ModelTrain".equals(type) || "ModelVehicle".equals(type)) {
            var definition = VehicleRegistry.getById(name);
            if (definition == null) {
                for (var candidate : VehicleRegistry.getAll()) {
                    if (candidate.getDisplayName().equals(name)) {
                        definition = candidate;
                        break;
                    }
                }
            }
            if ("ModelVehicle".equals(type)) {
                return definition == null
                        ? new ModelSetVehicle()
                        : new ModelSetVehicle(jp.ngt.rtm.modelpack.cfg.VehicleConfigAdapter.get(definition));
            }
            return definition == null
                    ? new jp.ngt.rtm.modelpack.modelset.ModelSetCompat()
                    : new jp.ngt.rtm.modelpack.modelset.ModelSetCompat(jp.ngt.rtm.modelpack.cfg.TrainConfigAdapter.get(definition.getId()));
        }
        if ("ModelRail".equals(type)) {
            var definition = com.portofino.realtrainmodunofficial.rail.RailRegistry.getById(name);
            return definition == null
                    ? new jp.ngt.rtm.modelpack.modelset.ModelSetRail()
                    : new jp.ngt.rtm.rail.util.RailProperty(name, net.minecraft.world.level.block.Blocks.GRAVEL, 0, 0.0625F).getModelSet();
        }
        com.portofino.realtrainmodunofficial.installedobject.InstalledObjectDefinition def =
                com.portofino.realtrainmodunofficial.installedobject.InstalledObjectRegistry.getByBareName(
                        name, com.portofino.realtrainmodunofficial.installedobject.InstalledObjectCategory.INSULATOR);
        return new ModelSetShim(def);
    }

    @SuppressWarnings("unchecked")
    public List<ModelSetBase> getModelList(String type) {
        // 本家 getModelList と同じ (SMP 未接続なので常に allModelSetMap)
        Map<String, ModelSetBase> map = this.allModelSetMap.get(type);
        List<ModelSetBase> list = new ArrayList<>();
        if (map != null) {
            list.addAll(map.values());
        }
        list.sort(Comparator.comparing(o -> String.valueOf(((ModelConfig) o.getConfig()).getName())));
        return list;
    }

    /** 本家getModelList: 型キー(文字列以外=従来 shim)。 */
    public List<Object> getModelList(Object type) {
        if (type instanceof String s && this.typeMap.containsKey(s)) {
            return new ArrayList<>(this.getModelList(s));
        }
        return new ArrayList<>();
    }

    public void setModelFile(String key, Object value) {
        this.modelFileMap.put(key, value);
    }

    public Object getModelFileObject(String key) {
        return this.modelFileMap.get(key);
    }

    /** 本家getModelFile: モデルグラフを返す(無ければ空)。 */
    public jp.ngt.ngtlib.renderer.model.PolygonModel getModelFile(String key) {
        Object cached = this.modelFileMap.get(key);
        if (cached instanceof jp.ngt.ngtlib.renderer.model.PolygonModel pm) {
            return pm;
        }
        try {
            byte[] bytes = NGTFileLoader.findAsset(key);
            if (bytes == null) {
                bytes = NGTFileLoader.findAsset("models/" + key);
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

    // ---- リソース / スクリプト (旧実装 = 本家 getResource/getScript を維持) ----

    public ResourceLocation getResource(String domain, String path) {
        return new ResourceLocation(domain, path);
    }

    /**
     * 本家 getResource(String): "domain:path" を分割 (既定 domain=minecraft)。
     * スクリプトの自前 include (eval(NGTText.readText(INSTANCE.getResource(path)))) が
     * 単一引数で呼ぶため必須。
     */
    public ResourceLocation getResource(String path) {
        String domain = "minecraft";
        if (path != null && path.contains(":")) {
            String[] sa = path.split(":", 2);
            domain = sa[0];
            path = sa[1];
        }
        return getResource(domain, path);
    }

    /**
     * 本家は ResourceLocation を取る。スクリプトからは ResourceLocation / 文字列パスの
     * どちらも渡ってくるため、Object 版で受けてパス文字列へ落とす。
     */
    public String getScript(Object resource) {
        if (resource == null) {
            return null;
        }
        if (resource instanceof String s) {
            return getScript(s);
        }
        if (resource instanceof ResourceLocation rl) {
            return getScript(rl.toString());
        }
        if (resource instanceof net.minecraft.resources.ResourceLocation rl) {
            return getScript(rl.toString());
        }
        return getScript(String.valueOf(resource));
    }

    /** 本家 ModelPackManager.getScript(String) 完全移植。 */
    public String getScript(String path) {
        if (path == null) {
            return null;
        }
        return this.loadScript(path);
    }

    /** 本家 ModelPackManager.loadScript 完全移植 (include 再帰 + キャッシュ)。 */
    private String loadScript(String fileName) {
        String cached = this.scriptCache.get(fileName);
        if (cached != null) {
            return cached;
        }

        byte[] bytes = NGTFileLoader.findAsset(fileName);
        if (bytes == null) {
            return null;
        }
        // 本家は indention=true で読む (改行保持)。文字コードは MS932 フォールバック付き。
        String rawScript = NGTText.decodeText(bytes)
                .replaceAll("\r\n|[\n\r\u2028\u2029\u0085]", "\n");
        while (true) {
            Matcher matcher = SC_INCLUDE.matcher(rawScript);
            if (!matcher.find()) {
                break;
            }
            String includePath = matcher.group(1);
            String rep = this.loadScript(includePath);
            rawScript = matcher.replaceFirst(
                    Matcher.quoteReplacement(rep == null ? "" : rep));
        }

        this.scriptCache.put(fileName, rawScript);
        return rawScript;
    }

    /** キャッシュ破棄 (診断コマンド用)。 */
    public void clearScriptCache() {
        this.scriptCache.clear();
    }

    private final Map<String, String> scriptCache = new ConcurrentHashMap<>();

    /** 本家getType: 型キー(そのまま返す)。 */
    public String getType(String type) {
        return type;
    }

    /** getModelSet の戻り値: getConfig.wirePos (double[3]) と getConfig.getName を提供。 */
    public static final class ModelSetShim extends ModelSetBase<ConfigShim> {
        private final ConfigShim config;
        private final boolean missing;

        ModelSetShim(com.portofino.realtrainmodunofficial.installedobject.InstalledObjectDefinition def) {
            super(new ConfigShim(def));
            this.config = new ConfigShim(def);
            this.missing = def == null;
        }

        public ConfigShim getDummyConfig() {
            return new ConfigShim(null);
        }

        @Override
        public boolean isDummy() {
            return missing;
        }

        public ConfigShim getConfig() {
            return config;
        }
    }

    public static final class ConfigShim {
        /** 本家 ConnectorConfig.wirePos ([x,y,z])。スクリプトが直接 index 参照する。 */
        public final double[] wirePos;
        private final String name;

        ConfigShim(com.portofino.realtrainmodunofficial.installedobject.InstalledObjectDefinition def) {
            if (def != null && def.getWireAttachPos() != null) {
                net.minecraft.world.phys.Vec3 wp = def.getWireAttachPos();
                this.wirePos = new double[]{wp.x, wp.y, wp.z};
            } else {
                this.wirePos = new double[]{0.0D, -0.5D, 0.0D};//本家の既定 (モデル無し碍子)
            }
            this.name = def == null ? "" : def.getBareName();
        }

        public String getName() {
            return name;
        }
    }

    /**
     * 本家 ModelPackManager.TypeEntry と同じ。
     */
    public static class TypeEntry {
        public final Class<? extends ModelConfig> cfgClass;
        public final Class<? extends ModelSetBase> modelsetClass;

        public TypeEntry(Class<? extends ModelConfig> cls1, Class<? extends ModelSetBase> cls2) {
            this.cfgClass = cls1;
            this.modelsetClass = cls2;
        }
    }
}
