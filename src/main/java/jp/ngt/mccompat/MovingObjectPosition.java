package jp.ngt.mccompat;

import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * 1.7.10 MovingObjectPosition のスクリプト互換 (SRG フィールド名)。
 * BlockUtil.getMOPFromPlayer が返す。
 */
public final class MovingObjectPosition {
    /** blockX */
    public final int field_72311_b;
    /** blockY */
    public final int field_72312_c;
    /** blockZ */
    public final int field_72309_d;
    /** hitVec */
    public final Vec3Compat field_72307_f;
    /** sideHit */
    public final int field_72310_e;
    /**
     * sideHit (1.8+ の EnumFacing 版)。
     * <pre>NGTO Builder.zip!.../Wire/render_Wire.js:538  mop.field_178784_b</pre>
     * スクリプトは続けて {@code .func_176745_a()} でインデックスを取るため、
     * 生の {@link net.minecraft.core.Direction} を持たせる。
     */
    public final net.minecraft.core.Direction field_178784_b;

    /** typeOfHit: 0=MISS(TILE?), ここでは常にブロックヒットのみ生成 */
    public final String typeOfHit = "BLOCK";

    public MovingObjectPosition(BlockHitResult hit) {
        this.field_72311_b = hit.getBlockPos().getX();
        this.field_72312_c = hit.getBlockPos().getY();
        this.field_72309_d = hit.getBlockPos().getZ();
        this.field_72307_f = new Vec3Compat(hit.getLocation());
        this.field_72310_e = hit.getDirection().ordinal();
        this.field_178784_b = hit.getDirection();
    }

    /** func_178782_a = getBlockPos (1.12) */
    public net.minecraft.core.BlockPos func_178782_a() {
        return new net.minecraft.core.BlockPos(field_72311_b, field_72312_c, field_72309_d);
    }

    public static MovingObjectPosition of(HitResult hit) {
        if (hit instanceof BlockHitResult bhr && hit.getType() == HitResult.Type.BLOCK) {
            return new MovingObjectPosition(bhr);
        }
        return null;
    }

    /**
     * 1.7.10 Vec3 互換 (xCoord/yCoord/zCoord の SRG 名)。
     */
    public static final class Vec3Compat {
        /** xCoord */
        public final double field_72450_a;
        /** yCoord */
        public final double field_72448_b;
        /** zCoord */
        public final double field_72449_c;

        public Vec3Compat(net.minecraft.world.phys.Vec3 v) {
            this.field_72450_a = v.x;
            this.field_72448_b = v.y;
            this.field_72449_c = v.z;
        }
    }
}
