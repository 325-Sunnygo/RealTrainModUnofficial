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
}
