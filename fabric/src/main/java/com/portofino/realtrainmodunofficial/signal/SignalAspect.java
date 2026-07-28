package com.portofino.realtrainmodunofficial.signal;

/**
 * RTM風の遠隔信号で使う現示一覧です。
 * ボタン順とIDを固定して、保存データとUIが同じ値を共有します。
 */
public enum SignalAspect {
    STOP(0, "停止(R)", 1),
    WARNING(1, "警戒(YY)", 4),
    CAUTION(2, "注意(Y)", 3),
    REDUCE(3, "減速(YG)", 6),
    RESTRICTED(4, "抑速", 2),
    PROCEED(5, "進行(G)", 5),
    HIGH_SPEED(6, "高速進行(GG)", 7);

    private final int id;
    private final String label;
    private final int legacyValue;

    SignalAspect(int id, String label, int legacyValue) {
        this.id = id;
        this.label = label;
        this.legacyValue = legacyValue;
    }

    public int getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }

    public int getLegacyValue() {
        return legacyValue;
    }

    public static SignalAspect byId(int id) {
        for (SignalAspect aspect : values()) {
            if (aspect.id == id) {
                return aspect;
            }
        }
        return STOP;
    }

    public static SignalAspect byLegacyValue(int legacyValue) {
        for (SignalAspect aspect : values()) {
            if (aspect.legacyValue == legacyValue) {
                return aspect;
            }
        }
        return STOP;
    }
}
