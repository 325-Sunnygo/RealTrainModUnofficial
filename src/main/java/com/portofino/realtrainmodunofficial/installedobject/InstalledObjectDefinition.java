package com.portofino.realtrainmodunofficial.installedobject;

import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;

public class InstalledObjectDefinition {
    private final String id;
    private final String displayName;
    private final String packName;
    private final InstalledObjectCategory category;
    private final String modelFile;
    private final String scriptPath;
    private final String buttonTexture;
    /**
     * 本家 KaizPatchX の customIconTexture (ModelConfig)。
     * 設定すると、アイテムの絵が既定のアイコンではなくこの画像になる。
     */
    private String customIconTexture = "";
    private final Map<String, String> textureOverrides;
    private final Vec3 modelOffset;
    private final float modelScale;
    private final boolean smoothing;
    private final float width;
    private final float height;
    private final float depth;
    private final String signTexture;
    private final String emissiveTexture;
    private final String runningSound;
    private final Map<Integer, List<String>> signalLightGroups;
    private final List<String> renderObjects;
    private final Vec3 scriptBodyPos;
    private final int signFrame;
    private final int backTexture;
    private Vec3 wireAttachPos = Vec3.ZERO;
    // ワイヤー用パラメータ(WireConfig 相当)。コンストラクタ後に setWireParams で設定。
    private float sectionLength = 0.5F;
    private float deflectionCoefficient = 0.0F;
    /** 本家 WireConfig.lengthCoefficient。既定 0 = 長さによる補正なし。 */
    private float lengthCoefficient = 0.0F;
    // 看板用パラメータ(本家 SignboardConfig 相当)。コンストラクタ後に setSignboardParams で設定。
    // animationCycle: frame を1コマ進めるのに要する tick 数。
    private int animationCycle = 1;
    private int color = 0;
    private int lightValue = 0;
    // 本家 ModelConfig.serverScriptPath。サーバー側で毎 tick onUpdate(entity, executer) が呼ばれる
    // スクリプト (列車検知器など)。コンストラクタ引数が既に多いので setter で後付けする。
    private String serverScriptPath = "";
    // 本家 ModelConfig.doCulling (既定 false = 両面描画)。上と同じくコンストラクタが肥大するため setter。
    private boolean doCulling = false;
    // 本家 MachineConfig.rotateByMetadata。true の照明 (サーチライト/回転灯/灯台灯) は
    // RenderMachine と同じく「クリック面 (meta 0-5) の回転 ∘ プレイヤー向き」で描く。
    private boolean rotateByMetadata = false;

    public boolean isRotateByMetadata() {
        return rotateByMetadata;
    }

    public void setRotateByMetadata(boolean rotateByMetadata) {
        this.rotateByMetadata = rotateByMetadata;
    }

    public String getServerScriptPath() {
        return serverScriptPath;
    }

    public void setServerScriptPath(String serverScriptPath) {
        this.serverScriptPath = serverScriptPath == null ? "" : serverScriptPath;
    }

    /**
     * 本家 ModelConfig.doCulling。既定 false = 両面描画。
     * 本家 ModelObject.render は車両も設置オブジェクトも通る共通経路で、
     * if (!cfg.doCulling) GL11.glDisable(GL_CULL_FACE); と一律に扱う。
     */
    public boolean isDoCulling() {
        return doCulling;
    }

    public void setDoCulling(boolean doCulling) {
        this.doCulling = doCulling;
    }

    public boolean hasServerScript() {
        return !this.serverScriptPath.isBlank();
    }

    public int getAnimationCycle() {
        return animationCycle;
    }

    public int getColor() {
        return color;
    }

    public int getLightValue() {
        return lightValue;
    }

    /** 本家 SignboardConfig.init 準拠の既定値補正。 */
    public void setSignboardParams(int animationCycle, int color, int lightValue) {
        this.animationCycle = animationCycle <= 0 ? 1 : animationCycle;
        this.color = color < 0 ? 0x101010 : color;
        this.lightValue = lightValue;
    }

    public float getSectionLength() {
        return sectionLength;
    }

    public float getDeflectionCoefficient() {
        return deflectionCoefficient;
    }

    /**
     * 本家 WireConfig.lengthCoefficient。長いほどたるみを小さくする係数で、
     * 本家は alpha = deflectionCoefficient * cos(pitch) / (1+lengthCoefficient)^水平長 に使う。
     * 既定の架線 (ModelWire_BasicWireBlack.json) は 0.0625 を指定している。
     */
    public float getLengthCoefficient() {
        return lengthCoefficient;
    }

    public void setWireParams(float sectionLength, float deflectionCoefficient) {
        setWireParams(sectionLength, deflectionCoefficient, this.lengthCoefficient);
    }

    public void setWireParams(float sectionLength, float deflectionCoefficient, float lengthCoefficient) {
        if (sectionLength > 0.0F) {
            this.sectionLength = sectionLength;
        }
        this.deflectionCoefficient = Math.max(0.0F, deflectionCoefficient);
        this.lengthCoefficient = Math.max(0.0F, lengthCoefficient);
    }

    public Vec3 getWireAttachPos() {
        return wireAttachPos;
    }

    public void setWireAttachPos(Vec3 wireAttachPos) {
        this.wireAttachPos = wireAttachPos == null ? Vec3.ZERO : wireAttachPos;
    }

    // 本家 ModelConnector_*.json の connectorType ("Relay" 等)。NGTO Builder の Wire ツールが
    // 碍子アイテムの getSubType==="Relay" でリレー碍子を判定するのに使う。
    private String subType = "";

    public String getSubType() {
        return subType;
    }

    /** #customIconTexture。空なら既定のアイコン。 */
    public String getCustomIconTexture() {
        return customIconTexture;
    }

    public void setCustomIconTexture(String customIconTexture) {
        this.customIconTexture = customIconTexture == null ? "" : customIconTexture.trim();
    }

    public void setSubType(String subType) {
        this.subType = subType == null ? "" : subType;
    }

    /** id ("category:pack:name") の末尾 = 本家の定義名 (スクリプトが比較する bare name)。 */
    public String getBareName() {
        int idx = id.lastIndexOf(':');
        return idx >= 0 ? id.substring(idx + 1) : id;
    }

    public InstalledObjectDefinition(String id, String displayName, String packName, InstalledObjectCategory category,
                                     String modelFile, String scriptPath, Map<String, String> textureOverrides,
                                     Vec3 modelOffset, float modelScale, boolean smoothing,
                                     float width, float height, float depth, String signTexture) {
        this(id, displayName, packName, category, modelFile, scriptPath, "", textureOverrides, modelOffset, modelScale,
            smoothing, width, height, depth, signTexture, "", "", Map.of(), Vec3.ZERO, 1, 1);
    }

    public InstalledObjectDefinition(String id, String displayName, String packName, InstalledObjectCategory category,
                                     String modelFile, String scriptPath, String buttonTexture, Map<String, String> textureOverrides,
                                     Vec3 modelOffset, float modelScale, boolean smoothing,
                                     float width, float height, float depth, String signTexture,
                                     String emissiveTexture, String runningSound, Map<Integer, List<String>> signalLightGroups,
                                     Vec3 scriptBodyPos) {
        this(id, displayName, packName, category, modelFile, scriptPath, buttonTexture, textureOverrides, modelOffset,
            modelScale, smoothing, width, height, depth, signTexture, emissiveTexture, runningSound,
            signalLightGroups, List.of(), scriptBodyPos, 1, 1);
    }

    public InstalledObjectDefinition(String id, String displayName, String packName, InstalledObjectCategory category,
                                     String modelFile, String scriptPath, String buttonTexture, Map<String, String> textureOverrides,
                                     Vec3 modelOffset, float modelScale, boolean smoothing,
                                     float width, float height, float depth, String signTexture,
                                     String emissiveTexture, String runningSound, Map<Integer, List<String>> signalLightGroups,
                                     Vec3 scriptBodyPos, int signFrame, int backTexture) {
        this(id, displayName, packName, category, modelFile, scriptPath, buttonTexture, textureOverrides, modelOffset,
            modelScale, smoothing, width, height, depth, signTexture, emissiveTexture, runningSound,
            signalLightGroups, List.of(), scriptBodyPos, signFrame, backTexture);
    }

    public InstalledObjectDefinition(String id, String displayName, String packName, InstalledObjectCategory category,
                                     String modelFile, String scriptPath, String buttonTexture, Map<String, String> textureOverrides,
                                     Vec3 modelOffset, float modelScale, boolean smoothing,
                                     float width, float height, float depth, String signTexture,
                                     String emissiveTexture, String runningSound, Map<Integer, List<String>> signalLightGroups,
                                     List<String> renderObjects, Vec3 scriptBodyPos, int signFrame, int backTexture) {
        this.id = id;
        this.displayName = displayName;
        this.packName = packName;
        this.category = category;
        this.modelFile = modelFile;
        this.scriptPath = scriptPath;
        this.buttonTexture = buttonTexture == null ? "" : buttonTexture;
        this.textureOverrides = textureOverrides == null ? Map.of() : Map.copyOf(textureOverrides);
        this.modelOffset = modelOffset == null ? Vec3.ZERO : modelOffset;
        this.modelScale = modelScale <= 0.0F ? 1.0F : modelScale;
        this.smoothing = smoothing;
        this.width = width <= 0.0F ? 1.0F : width;
        this.height = height <= 0.0F ? 1.0F : height;
        this.depth = depth <= 0.0F ? 0.125F : depth;
        this.signTexture = signTexture == null ? "" : signTexture;
        this.emissiveTexture = emissiveTexture == null ? "" : emissiveTexture;
        this.runningSound = runningSound == null ? "" : runningSound;
        this.signalLightGroups = signalLightGroups == null ? Map.of() : Map.copyOf(signalLightGroups);
        this.renderObjects = renderObjects == null ? List.of() : List.copyOf(renderObjects);
        this.scriptBodyPos = scriptBodyPos == null ? Vec3.ZERO : scriptBodyPos;
        this.signFrame = Math.max(1, signFrame);
        this.backTexture = backTexture;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPackName() {
        return packName;
    }

    public InstalledObjectCategory getCategory() {
        return category;
    }

    public String getModelFile() {
        return modelFile;
    }

    public String getScriptPath() {
        return scriptPath;
    }

    public String getButtonTexture() {
        return buttonTexture;
    }

    public Map<String, String> getTextureOverrides() {
        return textureOverrides;
    }

    public Vec3 getModelOffset() {
        return modelOffset;
    }

    public float getModelScale() {
        return modelScale;
    }

    public boolean isSmoothing() {
        return smoothing;
    }

    public float getWidth() {
        return width;
    }

    public float getHeight() {
        return height;
    }

    public float getDepth() {
        return depth;
    }

    public String getSignTexture() {
        return signTexture;
    }

    public String getEmissiveTexture() {
        return emissiveTexture;
    }

    public String getRunningSound() {
        return runningSound;
    }

    public Map<Integer, List<String>> getSignalLightGroups() {
        return signalLightGroups;
    }

    public List<String> getRenderObjects() {
        return renderObjects;
    }

    public int getSignFrame() {
        return signFrame;
    }

    public int getBackTexture() {
        return backTexture;
    }

    public Vec3 getScriptBodyPos() {
        return scriptBodyPos;
    }
}
