package com.portofino.realtrainmodunofficial.client.camera;

/**
 * 撮り鉄カメラの交換レンズ。実在レンズの構成に寄せてある。
 *
 * <p>レンズは「焦点距離の範囲 (mm)」と「開放 F 値 (一番明るくできる絞り)」で性能が決まる。
 * 望遠になるほど背景が圧縮され、開放が明るいほど背景が大きくボケる
 * ({@link CameraState#getBokehStrength()} が焦点距離と F 値の両方を見る)。
 *
 * <ul>
 *   <li>{@link #KIT} … カメラ標準装備。レンズアイテムを持っていなくても使える基本ズーム</li>
 *   <li>それ以外 … {@code LensItem} として存在するアイテム。持っていると L キーで換装できる</li>
 * </ul>
 */
public enum CameraLens {

    //id,              表示名,                    焦点min, 焦点max, 開放F,  単焦点
    KIT("kit", "標準ズーム 24-70mm F4", 24.0F, 70.0F, 4.0F, false),
    WIDE("wide", "広角ズーム 16-35mm F4", 16.0F, 35.0F, 4.0F, false),
    STANDARD("standard", "大口径標準 24-70mm F2.8", 24.0F, 70.0F, 2.8F, false),
    TELE("tele", "望遠ズーム 70-200mm F2.8", 70.0F, 200.0F, 2.8F, false),
    SUPER_TELE("super_tele", "超望遠ズーム 100-400mm F5.6", 100.0F, 400.0F, 5.6F, false),
    SANNI("sanni", "サンニッパ 300mm F2.8", 300.0F, 300.0F, 2.8F, true),
    NIFTY("nifty", "単焦点 50mm F1.4", 50.0F, 50.0F, 1.4F, true);

    public final String id;
    public final String displayName;
    public final float focalMin;
    public final float focalMax;
    /** 開放 F 値 (このレンズで一番明るくできる絞り = 一番小さい F 値)。 */
    public final float wideFStop;
    public final boolean prime;

    CameraLens(String id, String displayName, float focalMin, float focalMax, float wideFStop, boolean prime) {
        this.id = id;
        this.displayName = displayName;
        this.focalMin = focalMin;
        this.focalMax = focalMax;
        this.wideFStop = wideFStop;
        this.prime = prime;
    }

    /** 表示用の短い名前 (ファインダー左上)。単焦点は「300mm F2.8」、ズームは「70-200mm F2.8」。 */
    public String shortLabel() {
        if (prime) {
            return String.format("%dmm F%s", Math.round(focalMin), fmt(wideFStop));
        }
        return String.format("%d-%dmm F%s", Math.round(focalMin), Math.round(focalMax), fmt(wideFStop));
    }

    private static String fmt(float v) {
        return v == Math.floor(v) ? String.valueOf((int) v) : String.valueOf(v);
    }

    public static CameraLens forId(String id) {
        if (id != null) {
            for (CameraLens l : values()) {
                if (l.id.equals(id)) {
                    return l;
                }
            }
        }
        return KIT;
    }
}
