package jp.ngt.rtm.modelpack;

/** 本家 jp.ngt.rtm.modelpack.IModelSelector の移植。 */
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

    /** 本家 getModelSet。RTMU は ModelSetCompat (= ModelSetB… */
    Object getModelSet();
}
