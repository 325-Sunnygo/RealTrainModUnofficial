package jp.ngt.mccompat;

import net.minecraft.world.phys.AABB;

/**
 * new AABB(Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY) は NeoForge が足しているもので、バニラ (Fabric) には無い。
 * 「描画範囲を無制限にする」用途でしか使っていないので、同じ値をここに置いて参照先を移す。
 */
public final class AABBCompat {

    /** NeoForge の new AABB(Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY) と同値。 */
    public static final AABB INFINITE = new AABB(
        Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY,
        Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);

    private AABBCompat() {
    }
}
