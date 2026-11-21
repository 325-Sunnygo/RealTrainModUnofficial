package jp.ngt.ngtlib.block;

/**
 * 本家 jp.ngt.ngtlib.block.TileEntityPlaceable 相当。
 * NGTO Builder は設置の最後にこれで向きを与える:
 */
public interface TileEntityPlaceable {

    /** 現在の向き (度)。 */
    float getRotation();

    /** 向きを設定する。sync=true でクライアントへ反映。 */
    void setRotation(float rotation, boolean sync);

    float getOffsetX();

    float getOffsetY();

    float getOffsetZ();

    /** 本家 setOffset: 設置物のモデル位置オフセット。 */
    void setOffset(double offsetX, double offsetY, double offsetZ, boolean sync);
}
