package jp.ngt.ngtlib.math;

/**
 * 本家 {@code jp.ngt.ngtlib.math.NGTVec} の移植。
 *
 * <p>本家は可変な {@code net.minecraft.util.Vec3} (1.7.10) を継承し、
 * {@code setValue} / {@code addVector} などで<b>自身を書き換える</b>。
 * 1.21 にその Vec3 は無いため、本家 NGTLib の {@link Vec3} を継承して
 * 同じ API・同じ挙動 (自身を再利用) を提供する。
 */
public class NGTVec extends Vec3 {

    public NGTVec(double x, double y, double z) {
        super(x, y, z);
    }

    /** 自身の成分を書き換えて返す。 */
    public Vec3 setValue(double x, double y, double z) {
        this.set(x, y, z);
        return this;
    }

    public Vec3 setValue(Vec3 par1) {
        return this.setValue(par1.getX(), par1.getY(), par1.getZ());
    }

    /** 本家 addVector: 自身に加算して返す。 */
    public Vec3 addVector(double x, double y, double z) {
        return this.setValue(this.getX() + x, this.getY() + y, this.getZ() + z);
    }

    public Vec3 addVector(Vec3 par1) {
        return this.addVector(par1.getX(), par1.getY(), par1.getZ());
    }

    @Override
    public Vec3 crossProduct(Vec3 par1) {
        return this.setValue(
            this.getY() * par1.getZ() - this.getZ() * par1.getY(),
            this.getZ() * par1.getX() - this.getX() * par1.getZ(),
            this.getX() * par1.getY() - this.getY() * par1.getX());
    }

    @Override
    public Vec3 normalize() {
        double length = this.length();
        if (length < 1.0E-4D) {
            return this.setValue(0.0D, 0.0D, 0.0D);
        }
        double d1 = 1.0D / length;
        return this.setValue(this.getX() * d1, this.getY() * d1, this.getZ() * d1);
    }
}
