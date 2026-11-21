package jp.ngt.mccompat;

/** 1.7.10 net.minecraft.util.Vec3 のスクリプト互換。 */
public final class Vec3Compat {
    /** xCoord */
    public final double field_72450_a;
    /** yCoord */
    public final double field_72448_b;
    /** zCoord */
    public final double field_72449_c;

    public Vec3Compat(double x, double y, double z) {
        this.field_72450_a = x;
        this.field_72448_b = y;
        this.field_72449_c = z;
    }

    public Vec3Compat(net.minecraft.world.phys.Vec3 v) {
        this(v.x, v.y, v.z);
    }

    public double getX() {
        return this.field_72450_a;
    }

    public double getY() {
        return this.field_72448_b;
    }

    public double getZ() {
        return this.field_72449_c;
    }
}
