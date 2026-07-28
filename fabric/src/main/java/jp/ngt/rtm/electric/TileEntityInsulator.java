package jp.ngt.rtm.electric;

/**
 * 本家 jp.ngt.rtm.electric.TileEntityInsulator 相当。
 * 架線柱パックの描画スクリプトは、周囲のブロックを走査して
 * searchTileEntity instanceof TileEntityInsulator で碍子 (コネクタ) を探し、
 * その wirePos と車両名からブラケット (腕金) の種類と位置を決める。
 */
public interface TileEntityInsulator {

    /**
     * 電線の取付点 (ブロック底面中央からの相対座標)。碍子以外は null。
     * スクリプトは tile.wirePos と書く。
     */
    jp.ngt.ngtlib.math.Vec3 getWirePos();

    /** モデル名 (パックの定義名。例: "baru_insulator_bx_1")。 */
    String getModelName();
}
