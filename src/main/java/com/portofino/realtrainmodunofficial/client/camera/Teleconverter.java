package com.portofino.realtrainmodunofficial.client.camera;

/**
 * テレコンバーター (エクステンダー)。レンズとカメラの間に挟んで焦点距離を伸ばすアクセサリ。
 * 焦点距離が {@link #factor} 倍になる代わりに、開放 F 値が {@link #stopLoss} 段ぶん暗くなる
 * (実機と同じトレードオフ)。持っていると ; キーで着脱できる。
 */
public enum Teleconverter {
    NONE("none", "テレコン無し", 1.0F, 0),
    TC14("tc14", "1.4× テレコン", 1.4F, 1),
    TC20("tc20", "2.0× テレコン", 2.0F, 2);

    public final String id;
    public final String displayName;
    public final float factor;
    /** 開放 F 値が暗くなる段数 (F_STOPS のインデックスをこのぶん進める)。 */
    public final int stopLoss;

    Teleconverter(String id, String displayName, float factor, int stopLoss) {
        this.id = id;
        this.displayName = displayName;
        this.factor = factor;
        this.stopLoss = stopLoss;
    }

    public static Teleconverter forId(String id) {
        if (id != null) {
            for (Teleconverter t : values()) {
                if (t.id.equals(id)) {
                    return t;
                }
            }
        }
        return NONE;
    }
}
