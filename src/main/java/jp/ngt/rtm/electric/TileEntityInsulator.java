package jp.ngt.rtm.electric;

/** 本家 jp.ngt.rtm.electric.TileEntityInsulator 相当。 */
public interface TileEntityInsulator {

    /** 電線の取付点 (ブロック底面中央からの相対座標)。碍子以外は null。 */
    jp.ngt.ngtlib.math.Vec3 getWirePos();

    /** モデル名 (パックの定義名。例: "baru_insulator_bx_1")。 */
    String getModelName();
}
