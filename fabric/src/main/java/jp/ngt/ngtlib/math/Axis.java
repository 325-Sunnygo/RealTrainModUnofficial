package jp.ngt.ngtlib.math;

import net.minecraft.core.Direction;

/**
 * 本家 {@code jp.ngt.ngtlib.math.Axis} の移植。
 *
 * <p>宣言順が本家と同一であること (ordinal がスクリプトから見える)。本家の
 * {@code EnumFacing} は 1.21 の {@link Direction} に対応する。本家が
 * {@code axis.face.func_176745_a()} で引いていたインデックスは
 * {@link Direction#get3DDataValue()} と同じ値なので {@link #faceIndex()} で提供する。
 *
 * <p>使用例 (本家 RenderMotor.js / RenderClutch.js / RenderReversGear.js):
 * <pre>var rotation = renderer.getRotation(entity, Axis.POSITIVE_Y);</pre>
 */
public enum Axis {
    POSITIVE_X(Direction.EAST),
    NEGATIVE_X(Direction.WEST),
    POSITIVE_Y(Direction.UP),
    NEGATIVE_Y(Direction.DOWN),
    POSITIVE_Z(Direction.SOUTH),
    NEGATIVE_Z(Direction.NORTH);

    public final Direction face;

    Axis(Direction face) {
        this.face = face;
    }

    /** 本家 {@code face.func_176745_a()} 相当。 */
    public int faceIndex() {
        return this.face.get3DDataValue();
    }
}
