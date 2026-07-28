package jp.ngt.rtm.electric;

/**
 * 本家 jp.ngt.rtm.electric.TileEntityConnectorBase のプレースホルダ (碍子/コネクタ系の基底)。
 *
 * <p>NGTO Builder の Wire ツールが {@code setModelName()} 内で
 * {@code NGTUtil.setValueToField(TileEntityConnectorBase.class, tileEntity, name, "modelName")} と
 * <b>クラスを reflection のフィールド探索起点</b>として渡す。これが無いと
 * {@code ReferenceError: "TileEntityConnectorBase" is not defined} でワイヤー設置ループが落ちる。
 *
 * <p>RTMU では碍子も {@code InstalledObjectBlockEntity} で {@code modelName} フィールドを持たないため、
 * {@link jp.ngt.ngtlib.util.NGTUtil#setValueToField(Class, Object, Object, String)} は無害に no-op する
 * (ワイヤー設置は継続。碍子モデルの個別指定だけが効かない)。碍子モデル指定まで対応するなら
 * RTMU 側の碍子にこのクラス階層/フィールドを持たせる。
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
