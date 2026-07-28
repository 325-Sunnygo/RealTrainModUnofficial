package com.portofino.realtrainmodunofficial.client;

/**
 * 描画中の車両/設置物を覚えておくだけの置き場。
 *
 * <p>元は「車両のガラスをレールより後に描く」遅延バッファだったが、<b>本家にはその処理が無く</b>、
 * 車体越しにレールが透けて見える原因になっていたので撤去した。半透明は本家どおり
 * 提出順のまま描く。
 *
 * <p>車両描画は GLRecorder に記録して replay する経路があり、その replay 内では
 * scriptRenderer=null → entity=null になる。カリング判定 (doCulling) が entity を
 * 引けなくなるため、この参照をフォールバックに使う。
 */
public final class DeferredTranslucentRenderer {

    private static Object currentVehicle;

    private DeferredTranslucentRenderer() {
    }

    /** 車両描画の開始/終了で呼ぶ (finally で必ず null に戻すこと)。 */
    public static void setCurrentVehicle(Object entity) {
        currentVehicle = entity;
    }

    /**
     * いま描画中の車両 (無ければ null)。
     * <p>{@code MqoModelLoader.shouldCullModelFaces} が entity のフォールバックに使う。
     */
    public static Object currentVehicle() {
        return currentVehicle;
    }
}
