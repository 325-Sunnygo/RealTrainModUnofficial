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
    private float offsetX, offsetY, offsetZ;
    private float roll, pitch, yaw;
    private float scale = 1.0F;

    public float getRotation() {
        return this.rotation;
    }

    public void setRotation(float rotation, boolean sync) {
        this.rotation = rotation % 360.0F;
    }

    /** 本家 setRotation(yaw, pitch, roll): 3 軸まとめて設定。 */
    public void setRotation(float yaw, float pitch, float roll) {
        this.setRotationYaw(yaw, false);
        this.setRotationPitch(pitch, false);
        this.setRotationRoll(roll, false);
    }

    public float getOffsetX() {
        return this.offsetX;
    }

    public float getOffsetY() {
        return this.offsetY;
    }

    public float getOffsetZ() {
        return this.offsetZ;
    }

    /** 本家 setOffset: 設置物のモデル位置オフセット。 */
    public void setOffset(float offsetX, float offsetY, float offsetZ, boolean sync) {
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.offsetZ = offsetZ;
    }

    public void setOffset(float offsetX, float offsetY, float offsetZ) {
        this.setOffset(offsetX, offsetY, offsetZ, false);
    }

    public float getRotationRoll() {
        return this.roll;
    }

    public void setRotationRoll(float roll, boolean sync) {
        this.roll = roll % 360.0F;
    }

    public float getRotationPitch() {
        return this.pitch;
    }

    public void setRotationPitch(float pitch, boolean sync) {
        this.pitch = pitch % 360.0F;
    }

    public float getRotationYaw() {
        return this.yaw;
    }

    public void setRotationYaw(float yaw, boolean sync) {
        this.yaw = yaw % 360.0F;
    }

    public float getScale() {
        return this.scale;
    }

    public void setScale(float scale, boolean sync) {
        this.scale = scale;
    }

    public void setScale(float scale) {
        this.setScale(scale, false);
    }
}
