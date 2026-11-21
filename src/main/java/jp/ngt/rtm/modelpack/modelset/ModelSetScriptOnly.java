package jp.ngt.rtm.modelpack.modelset;

import jp.ngt.rtm.modelpack.cfg.ModelConfig;

/** スクリプトだけを持つ定義 (NPC / 設置物 / 機械) 用の ModelSet。 */
public class ModelSetScriptOnly extends ModelSetBase<ModelSetScriptOnly.ScriptConfig> {

    public ModelSetScriptOnly(String name, String modelType, String serverScriptPath,
                              String guiScriptPath, String guiTexture, String buttonTexture) {
        this(name, modelType, serverScriptPath, guiScriptPath, guiTexture, buttonTexture, null, null);
    }

    public ModelSetScriptOnly(String name, String modelType, String serverScriptPath,
                              String guiScriptPath, String guiTexture, String buttonTexture,
                              float[] offset, float[] modelBodyPos) {
        super(new ScriptConfig(name, modelType, serverScriptPath, guiScriptPath, guiTexture, buttonTexture,
                offset, modelBodyPos));
    }

    protected ModelSetScriptOnly() {
        super();
    }

    @Override
    public ScriptConfig getDummyConfig() {
        return new ScriptConfig("Dummy", "", null, null, null, null);
    }

    /** 本家 ModelConfig のスクリプト関連フィールドだけを持つ最小構成。 */
    public static class ScriptConfig extends ModelConfig {
        private final String name;
        private final String modelType;
        /** 本家 MachineConfig.machineType 相当。 */
        public String machineType = "";
        /** 本家 MachineConfig.followRailAngle 相当。 */
        public boolean followRailAngle;
        public boolean smoothing;
        public boolean doCulling;
        /** 本家 MachineConfig.offset 相当。 */
        public final float[] offset;
        /** 本家 MachineConfig.modelPartsBody.pos 相当。 */
        public final ModelPartsBody modelPartsBody;

        public ScriptConfig(String name, String modelType, String serverScriptPath,
                            String guiScriptPath, String guiTexture, String buttonTexture) {
            this(name, modelType, serverScriptPath, guiScriptPath, guiTexture, buttonTexture, null, null);
        }

        public ScriptConfig(String name, String modelType, String serverScriptPath,
                            String guiScriptPath, String guiTexture, String buttonTexture,
                            float[] offset, float[] modelBodyPos) {
            this.name = name == null ? "" : name;
            this.modelType = modelType == null ? "" : modelType;
            this.serverScriptPath = serverScriptPath;
            this.guiScriptPath = guiScriptPath;
            this.guiTexture = guiTexture;
            this.buttonTexture = buttonTexture;
            this.offset = offset == null ? new float[]{0.0F, 0.0F, 0.0F} : offset.clone();
            this.modelPartsBody = new ModelPartsBody(modelBodyPos);
        }

        @Override
        public String getName() {
            return this.name;
        }

        @Override
        public String getModelType() {
            return this.modelType;
        }
    }

    public static final class ModelPartsBody {
        public final float[] pos;

        public ModelPartsBody(float[] pos) {
            this.pos = pos == null ? new float[]{0.0F, 0.0F, 0.0F} : pos.clone();
        }
    }
}
