package jp.ngt.rtm.modelpack;

/**
 * 本家 {@code jp.ngt.rtm.modelpack.IModelSelector} の移植。
 *
 * <p>スクリプトが受け取る「モデルを持つ主体」(列車/車両/設置物) の共通 API。
 * 本家は {@code ResourceState}/{@code ModelSetBase} を型で要求するが、RTMU では
 * 実装クラスごとに ResourceState の型が異なる ({@code ResourceState} /
 * {@code ResourceStateCompat}) ため、戻り値は {@code Object} にして
 * どちらでも実装できるようにしている (スクリプトは動的型付けなので影響なし)。
 *
 * <p>一部のメソッドは {@code default} 実装にして、既存クラスへ無改修で
 * {@code implements IModelSelector} を付けられるようにしている。
 */
public interface IModelSelector {

    /** 本家 getResourceState。 */
    Object getResourceState();

    /** 本家 getModelType ("ModelTrain" 等)。 */
    default String getModelType() {
        return "";
    }

    /** 本家 getModelName。 */
    String getModelName();

    /** 本家 setModelName。 */
    void setModelName(String name);

    /** 本家 getPos。{x,y,z} または {entityId,-1,0}。 */
    default int[] getPos() {
        return new int[]{0, 0, 0};
    }

    /** 本家 closeGui(メイド/選択GUI)。既定は何もしない。 */
    default boolean closeGui(String guiName, Object state) {
        return false;
    }

    /** 本家 getModelSet。RTMU は {@code ModelSetCompat} (= ModelSetBase 派生) を返す。 */
    Object getModelSet();
}
