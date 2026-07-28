package com.portofino.realtrainmodunofficial.client.camera;

import net.minecraft.util.Mth;

/**
 * カメラの設定値。本家 jp.ngt.rtm.gui.camera.Camera を撮り鉄向けに作り直したもの。
 *
 * <p>本家は「ズーム倍率 1〜30」「感度 0〜1」「フォーカス 0〜1 (深度バッファの生値)」という
 * 内部単位をそのまま UI に出していた。ここでは実際のカメラと同じ<b>焦点距離(mm) / F値 /
 * シャッター速度 / ピント距離(m)</b> で持つ。撮り鉄が「300mm F2.8 で 1/500」と言えるようにするため。
 */
public final class CameraState {

    // ---- 焦点距離 (mm) ----
    // 28mm を等倍とし、本家の最大 30 倍 = 840mm 相当まで伸ばす。
    public static final float BASE_FOCAL_MM = 28.0F;
    public static final float MIN_FOCAL_MM = 18.0F;
    public static final float MAX_FOCAL_MM = 840.0F;

    // ---- F値 (絞り) ----
    // 開けるほどボケる。f/22 は実質パンフォーカス。
    private static final float[] F_STOPS = {
        1.4F, 1.8F, 2.0F, 2.8F, 4.0F, 5.6F, 8.0F, 11.0F, 16.0F, 22.0F
    };
    private static final int F_STOP_DEFAULT = 3; // f/2.8

    // ---- シャッター速度 ----
    // 分母。1/1000 は止まる、1/15 以下で流し撮りになる。
    private static final int[] SHUTTER_DENOM = {
        1000, 500, 250, 125, 60, 30, 15, 8, 4
    };
    private static final int SHUTTER_DEFAULT = 1; // 1/500

    // ---- ピント距離 (m) ----
    public static final float MIN_FOCUS_M = 0.5F;
    public static final float MAX_FOCUS_M = 300.0F;

    public enum FocusMode {
        /** 画面中央の被写体に合わせ続ける (シングルAF相当) */
        AF_S("AF-S"),
        /** 一番近い列車を追い続ける。走ってくる列車を撮るとき用 */
        AF_C("AF-C"),
        /** 手動。置きピンに使う */
        MF("MF");

        public final String label;

        FocusMode(String label) {
            this.label = label;
        }
    }

    public enum Grid {
        OFF("OFF"),
        THIRDS("三分割"),
        GOLDEN("対角"),
        SQUARE("方眼");

        public final String label;

        Grid(String label) {
            this.label = label;
        }
    }

    public enum AspectGuide {
        OFF("OFF", 0.0F),
        R3_2("3:2", 3.0F / 2.0F),
        R16_9("16:9", 16.0F / 9.0F),
        R1_1("1:1", 1.0F);

        public final String label;
        public final float ratio;

        AspectGuide(String label, float ratio) {
            this.label = label;
            this.ratio = ratio;
        }
    }

    private float focalMm = 50.0F;
    private int fStopIndex = F_STOP_DEFAULT;
    private int shutterIndex = SHUTTER_DEFAULT;
    private float focusDistance = 20.0F;
    private FocusMode focusMode = FocusMode.AF_S;
    private Grid grid = Grid.THIRDS;
    private AspectGuide aspectGuide = AspectGuide.OFF;
    private boolean level = true;

    // ---- 焦点距離 ----

    public float getFocalMm() {
        return focalMm;
    }

    /**
     * ズーム操作。望遠側ほど 1 ステップの mm 変化を大きくする (対数的)。
     * そうしないと 28→50mm が一瞬で終わり、300→800mm が異常に遅くなる。
     */
    public void zoom(float steps) {
        float factor = (float) Math.pow(1.06D, steps);
        focalMm = Mth.clamp(focalMm * factor, MIN_FOCAL_MM, MAX_FOCAL_MM);
    }

    /** 画角倍率 (等倍 = 28mm)。FOV 計算に使う。 */
    public float getZoomScale() {
        return focalMm / BASE_FOCAL_MM;
    }

    // ---- 絞り ----

    public float getFStop() {
        return F_STOPS[fStopIndex];
    }

    public void stepAperture(int dir) {
        fStopIndex = Mth.clamp(fStopIndex + dir, 0, F_STOPS.length - 1);
    }

    /**
     * ボケの強さ (0=パンフォーカス, 1=最大)。
     * <p>
     * 実際の被写界深度は「焦点距離が長いほど / F値が小さいほど / 被写体が近いほど」浅くなる。
     * 撮り鉄で望遠を出したときに背景が溶けてほしいので、焦点距離も効かせる。
     */
    public float getBokehStrength() {
        //F値: f/1.4 で 1.0、f/22 で ほぼ 0
        float aperture = Mth.clamp((22.0F - getFStop()) / (22.0F - 1.4F), 0.0F, 1.0F);
        //焦点距離: 28mm で 0.35、300mm 以上で 1.0
        float focal = Mth.clamp((focalMm - 18.0F) / (300.0F - 18.0F), 0.0F, 1.0F);
        focal = 0.35F + 0.65F * focal;
        return aperture * aperture * focal;
    }

    // ---- シャッター速度 ----

    public int getShutterDenominator() {
        return SHUTTER_DENOM[shutterIndex];
    }

    public void stepShutter(int dir) {
        shutterIndex = Mth.clamp(shutterIndex + dir, 0, SHUTTER_DENOM.length - 1);
    }

    /**
     * 前フレームを残す割合 (0=止まる, 0.9=激しく流れる)。
     * 1/1000 秒なら残さない。遅いシャッターほど前フレームが濃く残り、
     * 列車を追ってカメラを振ると背景だけが流れる = 流し撮りになる。
     */
    public float getMotionBlend() {
        if (shutterIndex <= 1) {
            return 0.0F;
        }
        //1/250 → 0.25、1/4 → 0.88 くらい
        float t = (float) (shutterIndex - 1) / (float) (SHUTTER_DENOM.length - 1 - 1);
        return 0.25F + 0.63F * t;
    }

    // ---- ピント ----

    public float getFocusDistance() {
        return focusDistance;
    }

    public void setFocusDistance(float meters) {
        focusDistance = Mth.clamp(meters, MIN_FOCUS_M, MAX_FOCUS_M);
    }

    /** MF のピント送り。近距離ほど細かく動かす。 */
    public void stepFocus(float steps) {
        float factor = (float) Math.pow(1.05D, steps);
        setFocusDistance(focusDistance * factor);
    }

    public FocusMode getFocusMode() {
        return focusMode;
    }

    public void cycleFocusMode() {
        FocusMode[] all = FocusMode.values();
        focusMode = all[(focusMode.ordinal() + 1) % all.length];
    }

    // ---- ファインダー ----

    public Grid getGrid() {
        return grid;
    }

    public void cycleGrid() {
        Grid[] all = Grid.values();
        grid = all[(grid.ordinal() + 1) % all.length];
    }

    public AspectGuide getAspectGuide() {
        return aspectGuide;
    }

    public void cycleAspectGuide() {
        AspectGuide[] all = AspectGuide.values();
        aspectGuide = all[(aspectGuide.ordinal() + 1) % all.length];
    }

    public boolean isLevelShown() {
        return level;
    }

    public void toggleLevel() {
        level = !level;
    }

    /** 「300mm F2.8 1/500」形式。撮影したスクリーンショットのファイル名にも使う。 */
    public String describe() {
        return String.format("%dmm F%s 1/%d",
            Math.round(focalMm),
            getFStop() == Math.floor(getFStop()) ? String.valueOf((int) getFStop()) : String.valueOf(getFStop()),
            getShutterDenominator());
    }
}
