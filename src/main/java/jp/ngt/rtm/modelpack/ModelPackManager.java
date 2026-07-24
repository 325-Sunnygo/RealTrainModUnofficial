package jp.ngt.rtm.modelpack;

/**
 * 本家 jp.ngt.rtm.modelpack.ModelPackManager のスクリプト互換最小移植。
 * スクリプトは INSTANCE.getResource(domain, path) を呼ぶ。
 * TODO(Phase 4): 型システム (registerType/registerModelset) の本実装。
 */
public final class ModelPackManager {
    public static final ModelPackManager INSTANCE = new ModelPackManager();

    private ModelPackManager() {
    }

    public jp.ngt.mccompat.ResourceLocation getResource(String domain, String path) {
        return new jp.ngt.mccompat.ResourceLocation(domain, path);
    }

    /**
     * 本家 getResource(String): "domain:path" を分割 (既定 domain=minecraft)。
     * スクリプトの自前 include (eval(NGTText.readText(INSTANCE.getResource(path)))) が
     * 単一引数で呼ぶため必須。無いと TypeError で include が全滅し、依存スクリプト
     * (render_function.js 等) が読まれず MCVersionChecker 未定義などに連鎖する。
     */
    public jp.ngt.mccompat.ResourceLocation getResource(String path) {
        String domain = "minecraft";
        if (path != null && path.contains(":")) {
            String[] sa = path.split(":", 2);
            domain = sa[0];
            path = sa[1];
        }
        return getResource(domain, path);
    }

    public String getScript(String path) {
        byte[] bytes = jp.ngt.ngtlib.io.NGTFileLoader.findAsset(path);
        return bytes != null ? new String(bytes, java.nio.charset.StandardCharsets.UTF_8) : null;
    }

    /**
     * 本家 getModelSet(type, name)。NGTO Builder の Wire ツールが
     * {@code getModelSet("ModelConnector", modelName).getConfig().wirePos} で碍子の電線取付点を読む
     * (null ガード無しで参照するため、見つからなくても既定 wirePos を返す非 null が必須)。
     */
    public ModelSetShim getModelSet(String type, String name) {
        com.portofino.realtrainmodunofficial.installedobject.InstalledObjectDefinition def =
            com.portofino.realtrainmodunofficial.installedobject.InstalledObjectRegistry.getByBareName(
                name, com.portofino.realtrainmodunofficial.installedobject.InstalledObjectCategory.INSULATOR);
        return new ModelSetShim(def);
    }

    /** getModelSet の戻り値: getConfig().wirePos (double[3]) と getConfig().getName() を提供。 */
    public static final class ModelSetShim {
        private final ConfigShim config;

        ModelSetShim(com.portofino.realtrainmodunofficial.installedobject.InstalledObjectDefinition def) {
            this.config = new ConfigShim(def);
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
                this.wirePos = new double[]{0.0D, -0.5D, 0.0D};  //本家の既定 (モデル無し碍子)
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

    /** 本家getModelList: モデル一覧(未対応=空)。 */
    public java.util.List<Object> getModelList(Object type) {
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

    /** 本家registerType: no-op。 */
    public void registerType(String type, Object cfg, Object set) {
    }

    /** 本家addModelSetName: no-op。 */
    public void addModelSetName(int count, String type, String name) {
    }

    /** 本家registerModelset: no-op。 */
    public void registerModelset(Object... args) {
    }

    /** 本家sendModelSetsToClient: no-op。 */
    public void sendModelSetsToClient(Object... args) {
    }

    /** 本家setModelFile: no-op。 */
    public void setModelFile(Object... args) {
    }

}
