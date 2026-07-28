package jp.ngt.rtm;

/**
 * 本家 jp.ngt.rtm.RTMConfig の段階的移植 (レール関連のみ)。
 * TODO: NeoForge Config への接続。既定値は本家と同一。
 */
@SuppressWarnings("unused")
public final class RTMConfig {
    //設定カテゴリ名 (本家)
    public static final String CATEGORY_SOUND = "Sound";
    public static final String CATEGORY_RAIL = "Rail";
    public static final String CATEGORY_ITEM = "Item";
    public static final String CATEGORY_ENTITY = "Entity";
    public static final String CATEGORY_MODEL = "Model";
    public static final String CATEGORY_MOD = "MOD";
    public static final String CATEGORY_BLOCK = "Block";
    public static final String CATEGORY_MARKER = "Marker";
    public static final String CATEGORY_LOAD = "Load";

    /**
     * レール生成距離 (default:64, max:256)
     */
    public static short railGeneratingDistance = 64;
    /**
     * レール生成高さ (default:8, max:256)
     */
    public static short railGeneratingHeight = 8;

    public static short markerDisplayDistance = 100;

    //音量 (0.0〜1.0)。本家は 0〜100 の設定値を /100 する
    public static float trainSoundVol = 1.0F;
    public static float gunSoundVol = 1.0F;
    public static byte crossingGateSoundType;
    public static boolean expandPlayableSoundCount = true;

    //可聴距離 (ブロック)
    public static float trainRunningSoundRange = 48.0F;
    public static float trainJointSoundRange = 48.0F;
    public static float trainBrakeReleaseSoundRange = 32.0F;
    public static float trainHornSoundRange = 96.0F;
    public static float crossingGateSoundRange = 32.0F;

    public static boolean gunBreakBlock;
    public static boolean deleteBat;
    public static boolean useServerModelPack;
    public static boolean versionCheck;
    /** モデルのスムージング (法線平均化)。 */
    public static boolean smoothing = true;
    public static boolean use1122Marker;
    public static boolean markerDistanceMoreRealPosition = true;

    public static int mirrorTextureSize = 512;
    public static byte mirrorRenderingFrequency = 1;
    /** モデルパック読み込み速度 (1:遅 2:標準 3:速)。 */
    public static int loadSpeed = 2;
    public static int fixRTMCachedModelMemoryLimitMiB = 256;
    public static int fixRTMCachedModelProtectSeconds = 10;

    private RTMConfig() {
    }
}
