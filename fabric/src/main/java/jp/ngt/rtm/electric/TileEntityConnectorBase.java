package jp.ngt.rtm.electric;

/**
 * 本家 jp.ngt.rtm.electric.TileEntityConnectorBase のプレースホルダ (碍子/コネクタ系の基底)。
 * NGTO Builder の Wire ツールが setModelName 内で
 * NGTUtil.setValueToField(TileEntityConnectorBase.class, tileEntity, name, "modelName") と
 * クラスを reflection のフィールド探索起点として渡す。
 */
public abstract class TileEntityConnectorBase {

    /** 本家 modelName: 碍子/コネクタのモデル名 (スクリプトが reflection で書く)。 */
    protected String modelName = "";

    public String getModelName() {
        return this.modelName;
    }

    public void setModelName(String name) {
        this.modelName = name == null ? "" : name;
    }

    /** 本家 getDefaultName: モデル未指定時の既定。 */
    public String getDefaultName() {
        return "";
    }

    public String getModelType() {
        return "ModelConnector";
    }

    public Object getModelSet() {
        return jp.ngt.rtm.modelpack.ModelPackManager.INSTANCE.getModelSet(this.getModelType(), this.getModelName());
    }

    /**
     * 本家 wirePos: 電線を張り出す位置 (ブロック中心からの相対)。
     * updateWirePos が設定する。
     */
    public jp.ngt.ngtlib.math.Vec3 wirePos = jp.ngt.ngtlib.math.Vec3.ZERO;

    public jp.ngt.ngtlib.math.Vec3 getWirePos() {
        return this.wirePos;
    }

    /**
     * 本家 updateWirePos: 設定の wirePos をブロックの向きで回して実座標にする。
     * cfg が null なら自分のモデル設定から取る。
     */
    public void updateWirePos(Object cfg) {
    }

    public boolean closeGui(String par1, Object par2) {
        return true;
    }

}
