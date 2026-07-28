package jp.ngt.mccompat;

import net.minecraft.world.phys.AABB;

/**
 * 1.7.10 net.minecraft.util.AxisAlignedBB のスクリプト互換ラッパー。
 * 列車検知器のサーバースクリプトは自分の当たり判定を縦に広げてから
 * その中の列車を探す:
 */
@SuppressWarnings("unused")
public final class AxisAlignedBB {

    public final AABB aabb;

    // === 1.7.10 SRG フィールド (スクリプトが直接読むことがある) ===
    /** minX */
    public final double field_72340_a;
    /** minY */
    public final double field_72338_b;
    /** minZ */
    public final double field_72339_c;
    /** maxX */
    public final double field_72336_d;
    /** maxY */
    public final double field_72337_e;
    /** maxZ */
    public final double field_72334_f;

    public AxisAlignedBB(AABB aabb) {
        this.aabb = aabb;
        this.field_72340_a = aabb.minX;
        this.field_72338_b = aabb.minY;
        this.field_72339_c = aabb.minZ;
        this.field_72336_d = aabb.maxX;
        this.field_72337_e = aabb.maxY;
        this.field_72334_f = aabb.maxZ;
    }

    public AxisAlignedBB(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        this(new AABB(minX, minY, minZ, maxX, maxY, maxZ));
    }

    /**
     * func_72330_a = 1.7.10 AxisAlignedBB.getBoundingBox(minX,minY,minZ,maxX,maxY,maxZ) の静的ファクトリ。
     * ATS 地上子の server_ATS_Beacon.js が列車検知の当たり判定を作るのに
     * AxisAlignedBB.func_72330_a(...) を呼ぶ。
     */
    public static AxisAlignedBB func_72330_a(double minX, double minY, double minZ,
                                             double maxX, double maxY, double maxZ) {
        return new AxisAlignedBB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    /** ラッパー / 実体どちらからでも 1.21 の AABB を取り出す。 */
    public static AABB unwrap(Object obj) {
        if (obj instanceof AxisAlignedBB w) {
            return w.aabb;
        }
        if (obj instanceof AABB b) {
            return b;
        }
        return null;
    }

    /** func_72314_b = expand(x, y, z)。1.21 では inflate。 */
    public AxisAlignedBB func_72314_b(double x, double y, double z) {
        return new AxisAlignedBB(this.aabb.inflate(x, y, z));
    }

    public AxisAlignedBB expand(double x, double y, double z) {
        return this.func_72314_b(x, y, z);
    }

    /** func_72317_d = offset(x, y, z) */
    public AxisAlignedBB func_72317_d(double x, double y, double z) {
        return new AxisAlignedBB(this.aabb.move(x, y, z));
    }

    public AxisAlignedBB offset(double x, double y, double z) {
        return this.func_72317_d(x, y, z);
    }

    /** func_72326_a = intersectsWith(other) */
    public boolean func_72326_a(Object other) {
        AABB o = unwrap(other);
        return o != null && this.aabb.intersects(o);
    }

    /** func_72318_a = isVecInside(vec) */
    public boolean func_72318_a(Object vec) {
        if (vec instanceof net.minecraft.world.phys.Vec3 v) {
            return this.aabb.contains(v);
        }
        if (vec instanceof jp.ngt.ngtlib.math.Vec3 v) {
            return this.aabb.contains(v.getX(), v.getY(), v.getZ());
        }
        return false;
    }

    @Override
    public String toString() {
        return "AxisAlignedBB" + this.aabb;
    }
}
