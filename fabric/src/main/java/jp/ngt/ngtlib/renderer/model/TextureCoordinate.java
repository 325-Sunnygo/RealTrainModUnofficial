package jp.ngt.ngtlib.renderer.model;

/**
 * 本家 jp.ngt.ngtlib.renderer.model.TextureCoordinate。
 * RTMU の Face は UV を float[] で持つため、スクリプトが
 * face.textureCoordinates[i].getU と書けるようにするビュー。
 */
public class TextureCoordinate {
    private float u;
    private float v;

    private final VecAccuracy accuracy;
    public TextureCoordinate(float u,float v) { this(u,v,VecAccuracy.MEDIUM); }
    private TextureCoordinate(float u,float v,VecAccuracy accuracy) {
        this.accuracy=accuracy==null?VecAccuracy.MEDIUM:accuracy;
        setUV(u,v);
    }
    private float encode(float value) { return accuracy==VecAccuracy.LOW ? (short)(value*2000F)*0.0005F : value; }

    /** 本家 create。LOW は座標と異なりゼロ方向へ切り捨てて short に格納する。 */
    public static TextureCoordinate create(float u, float v, VecAccuracy accuracy) {
        return new TextureCoordinate(u, v,accuracy);
    }

    public float getU() {
        return this.u;
    }

    public float getV() {
        return this.v;
    }

    public void setUV(float u, float v) {
        this.u = encode(u);
        this.v = encode(v);
    }

    public TextureCoordinate copy() {
        return new TextureCoordinate(this.u, this.v,accuracy);
    }
}
