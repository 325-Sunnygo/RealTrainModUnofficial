package jp.ngt.mccompat;

import net.minecraft.world.phys.AABB;

/**
 * {@code AABB.INFINITE} は NeoForge が足しているもので、バニラ (Fabric) には無い。
 * <p>「描画範囲を無制限にする」用途でしか使っていないので、同じ値をここに置いて参照先を移す。
 */
public final class AABBCompat {

    /** NeoForge の {@code AABB.INFINITE} と同値。 */
    public static final AABB INFINITE = new AABB(
        Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY,
        Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);

    private AABBCompat() {
    }
}
