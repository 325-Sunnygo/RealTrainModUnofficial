package jp.ngt.ngtlib.block;

/**
 * 本家 jp.ngt.ngtlib.block.TileEntityPlaceable のプレースホルダ ({@link TileEntityCustom} を継承)。
 *
 * <p>NGTO Builder のスクリプトは {@code importPackage(Packages.jp.ngt.ngtlib.block)} 経由でこの名前を参照し、
 * 設置/Undo バックアップ時に {@code backupBlock instanceof TileEntityPlaceable} → {@code getRotation()} で
 * RTM 設置物 (回転を持つ看板/信号等) の向きを保存/復元する。<b>これが無いと設置ループがブロックごとに
 * {@code ReferenceError: "TileEntityPlaceable" is not defined} を投げ、ミニチュアが 1 個も置けない</b>。
 *
 * <p>RTMU の実タイルエンティティ (InstalledObjectBlockEntity 等) はこの互換クラスを継承しないため
 * {@code instanceof} は常に false = 設置される RTM 設置物の回転保存はスキップされる。MCTEU ミニチュアは
 * 主にバニラブロックの集合なので実害はほぼ無い ({@link TileEntityCustom} と同じ「名前解決だけ」方針)。
 */
public class TileEntityPlaceable extends TileEntityCustom {
    private float rotation;

    public float getRotation() {
        return this.rotation;
    }

    public void setRotation(float rotation, boolean sync) {
        this.rotation = rotation % 360.0F;
    }
}
