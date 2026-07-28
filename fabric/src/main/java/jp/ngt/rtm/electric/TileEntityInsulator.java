package jp.ngt.rtm.electric;

/**
 * 本家 jp.ngt.rtm.electric.TileEntityInsulator 相当。
 *
 * <p>架線柱パックの描画スクリプトは、周囲のブロックを走査して
 * {@code searchTileEntity instanceof TileEntityInsulator} で碍子 (コネクタ) を探し、
 * その {@code wirePos} と車両名からブラケット (腕金) の種類と位置を決める。
 *
 * <p>移植では碍子も他の設置物と同じ {@code InstalledObjectBlockEntity} なので、
 * 本家のようなクラス階層が作れない。そこで<b>インターフェース</b>として用意し、
 * 設置物のブロックエンティティに実装させることで {@code instanceof} を成立させる。
 * 碍子でない設置物は {@link #getWirePos()} が null を返すため、スクリプト側の
 * {@code if (searchTileEntity.wirePos === null) continue;} で正しく弾かれる。
 */
public interface TileEntityInsulator {

    /**
     * 電線の取付点 (ブロック底面中央からの相対座標)。碍子以外は null。
     * <p>
     * スクリプトは {@code tile.wirePos} と書く。Nashorn はプロパティ参照を
     * getter に解決するため、このメソッド名で {@code .wirePos} として読める。
     */
    jp.ngt.ngtlib.math.Vec3 getWirePos();

    /**
     * モデル名 (パックの定義名。例: "baru_insulator_bx_1")。
     */
    String getModelName();
}
