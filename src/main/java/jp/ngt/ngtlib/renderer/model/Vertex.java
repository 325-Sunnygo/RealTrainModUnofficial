package jp.ngt.ngtlib.renderer.model;

import jp.ngt.ngtlib.math.Vec3;

/**
 * 本家 jp.ngt.ngtlib.renderer.model.Vertex のスクリプト互換移植。
 */
public class Vertex {
    //本家の Vertex は setVec/add/expand/normalize で書き換わるため可変
    public float x;
    public float y;
    public float z;

    public Vertex(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Vertex(double x, double y, double z) {
        this((float) x, (float) y, (float) z);
    }

    /** 本家 Vertex.create。精度指定は RTMU では float 固定なので無視する。 */
    public static Vertex create(float x, float y, float z, VecAccuracy accuracy) {
        return new Vertex(x, y, z);
    }

    public static Vertex create(Vec3 vec, VecAccuracy accuracy) {
        return new Vertex((float) vec.getX(), (float) vec.getY(), (float) vec.getZ());
    }

    public float getX() {
        return this.x;
    }

    public float getY() {
        return this.y;
    }

    public float getZ() {
        return this.z;
    }

    /** 本家 setVec: 座標を書き換える。 */
    public void setVec(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    /** 本家 add: 自身に加算して自身を返す。 */
    public Vertex add(Vertex vertex) {
        this.setVec(this.x + vertex.getX(), this.y + vertex.getY(), this.z + vertex.getZ());
        return this;
    }

    /** 本家 expand: 自身を定数倍して自身を返す。 */
    public Vertex expand(float scale) {
        this.setVec(this.x * scale, this.y * scale, this.z * scale);
        return this;
    }

    /** 本家 normalize: 単位ベクトル化する (長さ0なら何もしない)。 */
    public void normalize() {
        double length = Math.sqrt((double) this.x * this.x + (double) this.y * this.y + (double) this.z * this.z);
        if (length < 1.0e-9D) {
            return;
        }
        double inv = 1.0D / length;
        this.setVec((float) (this.x * inv), (float) (this.y * inv), (float) (this.z * inv));
    }

    public Vertex copy(VecAccuracy accuracy) {
        return new Vertex(this.x, this.y, this.z);
    }

    public Vertex copy() {
        return new Vertex(this.x, this.y, this.z);
    }

    public Vec3 toVec() {
        return new Vec3(this.x, this.y, this.z);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Vertex other)) return false;
        return Float.compare(this.x, other.x) == 0
                && Float.compare(this.y, other.y) == 0
                && Float.compare(this.z, other.z) == 0;
    }

    @Override
    public int hashCode() {
        return 31 * (31 * Float.hashCode(this.x) + Float.hashCode(this.y)) + Float.hashCode(this.z);
    }
}
