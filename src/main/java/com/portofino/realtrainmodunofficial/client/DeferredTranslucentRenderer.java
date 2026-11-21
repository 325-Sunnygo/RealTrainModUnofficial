package com.portofino.realtrainmodunofficial.client;

/** 描画中の車両/設置物を覚えておくだけの置き場。 */
public final class DeferredTranslucentRenderer {

    private static Object currentVehicle;

    private DeferredTranslucentRenderer() {
    }

    /** 車両描画の開始/終了で呼ぶ (finally で必ず null に戻すこと)。 */
    public static void setCurrentVehicle(Object entity) {
        currentVehicle = entity;
    }

    /** いま描画中の車両 (無ければ null)。 */
    public static Object currentVehicle() {
        return currentVehicle;
    }
}
