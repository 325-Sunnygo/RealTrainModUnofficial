package jp.ngt.rtm.modelpack.cfg;

import java.util.Arrays;

/**
 * 本家 jp.ngt.rtm.modelpack.cfg.TrainConfig の段階的移植 (物理・台車関連フィールド)。
 * 既定値は本家 init と同一。
 */
public class TrainConfig {
    /** 本家 IConfigWithType の型名。スクリプトが cfg.TYPE で判定する。 */
    public static final String TYPE = "ModelTrain";

    public String trainName = "";
    public String trainType = "EC";
    /** 本家 ModelConfig.smoothing (NGTO Builder の renderStatic が getConfig.smoothing を読む)。 */
    public boolean smoothing;

    public boolean muteJointSound;
    /** 3つ以上の車輪対応, 台車前後別 */
    public float[][] jointDelay;
    private float[][] bogiePos;
    public float trainDistance;
    public float accelerateion;//本家のスペルミスを維持
    public float[] accelerateions;
    public float[] maxSpeed;
    public float[] deccelerations;
    public float rolling;
    public float rollSpeedCoefficient = 0.02F;
    public float rollVariationCoefficient = 2.53F;
    public float rollWidthCoefficient = 0.3F;
    public boolean useVariableAcceleration;
    public boolean useVariableDeceleration;
    public boolean notDisplayCab;
    public float[][] pantoPos;
    public Object[][] smoke;
    public float[][] playerPos;

    /** 本家 slotPos: {x, y, z, seatType} — EntityFloor (座席/フロア) のスポーン位置 */
    private float[][] slotPos = new float[0][];

    public String[] rollsignNames = new String[0];
    public boolean isSingleTrain;
    public float wheelRotationSpeed = 1.0F;

    public String sound_Joint;
    public String sound_JointReverb;
    public String sound_Notch;
    public String sound_BrakeRelease;
    public String sound_BrakeRelease2;
    public String sound_CpFin;
    /** レバーサ操作音。 */
    public String sound_Reversal;

    /** 車体モデル (本家 VehicleConfig.model / getModel)。 */
    public Object model;
    /** 台車モデル (本家 bogieModel3 / getBogieModel(i))。 */
    public Object[] bogieModel;

    /** 本家 init の既定値適用。 */
    public void init() {
        if (this.bogiePos == null) {
            this.bogiePos = new float[][]{{0.0F, 0.0F, 7.125F}, {0.0F, 0.0F, -7.125F}};
        }
        if (this.trainDistance <= 0.0F) {
            this.trainDistance = 10.125F;
        }
        if (this.accelerateion <= 0.0F) {
            this.accelerateion = 0.001736F;
        }
        if (this.maxSpeed == null || (!this.notDisplayCab && this.maxSpeed.length != 5)) {
            this.maxSpeed = new float[]{0.36F, 0.72F, 1.08F, 1.44F, 1.80F};
        }
        if (this.accelerateions == null || (!this.notDisplayCab && this.accelerateions.length != this.maxSpeed.length)) {
            this.accelerateions = new float[this.maxSpeed.length];
            Arrays.fill(this.accelerateions, this.accelerateion);
        }
        if (this.deccelerations == null || (!this.notDisplayCab && this.deccelerations.length != 9)) {
            this.deccelerations = new float[]{-0.0002F, -0.0005F, -0.001F, -0.0015F, -0.002F, -0.0025F, -0.003F, -0.0035F, -0.01F};
        }
        this.rolling *= 5.0F;
        if (this.jointDelay == null) {
            float f0 = 1.9F;
            this.jointDelay = new float[][]{{0.0F, f0}, {0.0F, f0}};
        }
        if (this.playerPos == null) {
            this.playerPos = new float[][]{{0.8F, 0.0F, 9.187F}, {-0.8F, 0.0F, -9.187F}};
        }
    }

    public float[][] getBogiePos() {
        return this.bogiePos;
    }

    public void setBogiePos(float[][] pos) {
        this.bogiePos = pos;
    }

    public float[][] getPlayerPos() {
        return this.playerPos;
    }

    public float[][] getSlotPos() {
        return this.slotPos;
    }

    public void setSlotPos(float[][] pos) {
        this.slotPos = pos != null ? pos : new float[0][];
    }

    public String getName() {
        return this.trainName;
    }

    public String getSubType() {
        return this.trainType;
    }

    /** 本家 getModelType: 設定の型名。 */
    public String getModelType() {
        return TYPE;
    }

    /** 本家 getModel: 車体モデル。 */
    public Object getModel() {
        return this.model;
    }

    /** 本家 getBogieModel(i): i 番目の台車モデル。未設定なら null。 */
    public Object getBogieModel(int index) {
        if (this.bogieModel == null || index < 0 || index >= this.bogieModel.length) {
            return null;
        }
        return this.bogieModel[index];
    }

    /** 本家 getDummyConfig: モデル未解決時に使う空設定。 */
    public static TrainConfig getDummyConfig() {
        TrainConfig config = new TrainConfig();
        config.trainName = "Dummy";
        config.trainType = "N";
        config.init();
        return config;
    }
}
