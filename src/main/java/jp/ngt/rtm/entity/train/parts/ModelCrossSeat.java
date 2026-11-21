package jp.ngt.rtm.entity.train.parts;

/**
 * 本家 ModelCrossSeat (1.7.10) の忠実移植。
 * 1.7.10 ModelRenderer と同じ座標系 (1/16 ブロック) とバニラと同じ
 * ボックス UV レイアウト (top/bottom/+Z/-Z/+X/-X) を保持する。
 */
public final class ModelCrossSeat {
    public static final int TEXTURE_WIDTH = 64;
    public static final int TEXTURE_HEIGHT = 64;

    /** Shape1: tex(0,31) addBox(-7,-5,0,14,5,10) point(0,0,-1) — 座面。 */
    public static final int SEAT_U = 0, SEAT_V = 31;
    public static final float[] SEAT_BOX = {-7.0F, -5.0F, 0.0F, 14.0F, 5.0F, 10.0F};
    public static final float[] SEAT_POINT = {0.0F, 0.0F, -1.0F};

    /** Shape2: tex(0,16) addBox(-8,-3,0,16,3,12) point(0,-5,-2) — クッション。 */
    public static final int CUSHION_U = 0, CUSHION_V = 16;
    public static final float[] CUSHION_BOX = {-8.0F, -3.0F, 0.0F, 16.0F, 3.0F, 12.0F};
    public static final float[] CUSHION_POINT = {0.0F, -5.0F, -2.0F};

    /** Shape3: tex(0,0) addBox(-8,-27,-1.5,16,13,3) point(0,6,4) rotX=-0.296706 — 背もたれ。 */
    public static final int BACK_U = 0, BACK_V = 0;
    public static final float[] BACK_BOX = {-8.0F, -27.0F, -1.5F, 16.0F, 13.0F, 3.0F};
    public static final float[] BACK_POINT = {0.0F, 6.0F, 4.0F};
    public static final float BACK_BASE_ANGLE_X = -0.296706F;

    private ModelCrossSeat() {
    }

    /** ボックスの幅 (x)。 */
    public static float width(float[] box) { return box[3]; }

    /** ボックスの高さ (y)。 */
    public static float height(float[] box) { return box[4]; }

    /** ボックスの奥行 (z)。 */
    public static float depth(float[] box) { return box[5]; }
}
