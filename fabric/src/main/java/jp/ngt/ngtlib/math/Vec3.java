package jp.ngt.ngtlib.math;

import net.minecraft.util.Mth;

/**
 * 完全独立のVecクラス
 * S/C問わず使えるように
 * Scriptからの仕様も想定
 * (本家 NGTLib jp.ngt.ngtlib.math.Vec3 の忠実移植。rotateAround* は「度」入力)
 */
public class Vec3 {
    public static final Vec3 ZERO = new Vec3(0.0D, 0.0D, 0.0D);

    private double x;
    private double y;
    private double z;

    public Vec3(double x, double y, double z) {
        this.set(x, y, z);
    }

    protected void set(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public double getX() {
        return this.x;
    }

    public double getY() {
        return this.y;
    }

    public double getZ() {
        return this.z;
    }

    public double length() {
        return Math.sqrt(this.getX() * this.getX() + this.getY() * this.getY() + this.getZ() * this.getZ());
    }

    public double lengthSq(double px, double py, double pz) {
        double dx = px - this.getX();
        double dy = py - this.getY();
        double dz = pz - this.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * @param par1 度
     */
    public Vec3 rotateAroundX(float par1) {
        float rad = NGTMath.toRadians(par1);
        float f1 = Mth.cos(rad);
        float f2 = Mth.sin(rad);
        double d0 = this.x;
        double d1 = this.y * (double) f1 + this.z * (double) f2;
        double d2 = this.z * (double) f1 - this.y * (double) f2;
        return new Vec3(d0, d1, d2);
    }

    /**
     * @param par1 度
     */
    public Vec3 rotateAroundY(float par1) {
        float rad = NGTMath.toRadians(par1);
        float f1 = Mth.cos(rad);
        float f2 = Mth.sin(rad);
        double d0 = this.x * (double) f1 + this.z * (double) f2;
        double d1 = this.y;
        double d2 = this.z * (double) f1 - this.x * (double) f2;
        return new Vec3(d0, d1, d2);
    }

    /**
     * @param par1 度
     */
    public Vec3 rotateAroundZ(float par1) {
        float rad = NGTMath.toRadians(par1);
        float f1 = Mth.cos(rad);
        float f2 = Mth.sin(rad);
        double d0 = this.x * (double) f1 + this.y * (double) f2;
        double d1 = this.y * (double) f1 - this.x * (double) f2;
        double d2 = this.z;
        return new Vec3(d0, d1, d2);
    }

    public Vec3 add(double x, double y, double z) {
        return new Vec3(this.getX() + x, this.getY() + y, this.getZ() + z);
    }

    public Vec3 add(Vec3 vec) {
        return this.add(vec.getX(), vec.getY(), vec.getZ());
    }

    public Vec3 sub(Vec3 vec) {
        return this.add(-vec.getX(), -vec.getY(), -vec.getZ());
    }

    public Vec3 multi(double num) {
        return new Vec3(this.getX() * num, this.getY() * num, this.getZ() * num);
    }

    /**
     * 外積
     */
    public Vec3 crossProduct(Vec3 par1) {
        return new Vec3(
                this.y * par1.z - this.z * par1.y,
                this.z * par1.x - this.x * par1.z,
                this.x * par1.y - this.y * par1.x
        );
    }

    /**
     * 内積
     */
    public double dotProduct(Vec3 vec) {
        return this.x * vec.x + this.y * vec.y + this.z * vec.z;
    }

    public Vec3 normalize() {
        double d1 = 1.0D / this.length();
        return new Vec3(this.x * d1, this.y * d1, this.z * d1);
    }

    public float getYaw() {
        return (float) NGTMath.toDegrees(Math.atan2(this.x, this.z));
    }

    public float getPitch() {
        double xz = Math.sqrt(this.x * this.x + this.z * this.z);
        return (float) NGTMath.toDegrees(Math.atan2(this.y, xz));
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Vec3) {
            Vec3 vec = (Vec3) obj;
            return vec.getX() == this.getX() && vec.getY() == this.getY() && vec.getZ() == this.getZ();
        }
        return false;
    }

    public double getAngle(Vec3 vec) {
        return Math.acos(this.getAngleCos(vec));
    }

    public double getAngleCos(Vec3 vec) {
        double d0 = this.dotProduct(vec) / (this.length() * vec.length());
        return Math.min(d0, 1.0D);
    }

    // NOTE: 本家には toNGTVec() があるが、NGTVec (MC Vec3 依存) は必要になった時点で移植する

    //以下は本家に無い別名。旧 RTMU の JS スタブが提供していた名前で、
    //それ向けに書かれたスクリプトが落ちないよう実クラス側にも用意する。

    /** {@link #multi(double)} の別名。 */
    public Vec3 scale(double s) {
        return this.multi(s);
    }

    public Vec3 multiply(double s) {
        return this.multi(s);
    }

    /** {@link #sub(Vec3)} の別名。 */
    public Vec3 subtract(Vec3 other) {
        return this.sub(other);
    }

    /** {@link #dotProduct(Vec3)} の別名。 */
    public double dot(Vec3 other) {
        return this.dotProduct(other);
    }

    /** 長さの 2 乗 (本家 lengthSq は始点指定版しか無いのでここで計算する)。 */
    public double lengthSquared() {
        return this.getX() * this.getX() + this.getY() * this.getY() + this.getZ() * this.getZ();
    }

    public double distanceTo(Vec3 other) {
        return this.sub(other).length();
    }

}
