package jp.ngt.ngtlib.renderer.model;

/**
 * 本家 jp.ngt.ngtlib.renderer.model.TextureCoordinate。
 * RTMU の Face は UV を float[] で持つため、スクリプトが
 * {@code face.textureCoordinates[i].getU()} と書けるようにするビュー。
 */
public class TextureCoordinate {
    private float u;
    private float v;

    public TextureCoordinate(float u, float v) {
        this.u = u;
        this.v = v;
    }

    /** 本家 create。精度指定は RTMU では float 固定なので無視する。 */
    public static TextureCoordinate create(float u, float v, VecAccuracy accuracy) {
        return new TextureCoordinate(u, v);
    }

    public float getU() {
        return this.u;
    }

    public float getV() {
        return this.v;
    }

    public void setUV(float u, float v) {
        this.u = u;
        this.v = v;
    }

    public TextureCoordinate copy() {
        return new TextureCoordinate(this.u, this.v);
    }
}
