package jp.ngt.ngtlib.block;

/**
 * 本家 {@code jp.ngt.ngtlib.block.TileEntityPlaceable} 相当。
 *
 * <p>NGTO Builder は設置の最後にこれで向きを与える:
 * <pre>
 * // lib_BlockBuilder.js:240-243
 * if (tile instanceof TileEntityPlaceable) {
 *     var rotation = tile.getRotation() + yaw;
 *     tile.setRotation(rotation, true);
 * }
 * </pre>
 *
 * <p>以前はダミーの<b>クラス</b>で、RTMU の実タイル (InstalledObjectBlockEntity) は
 * これを継承していなかった。そのため {@code instanceof} が常に false になり
 * <b>設置物の向きが一度も適用されず</b>、架線柱が線路と揃わず傾いて見えていた。
 * 実タイルに実装させるためインタフェースにしてある。
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
