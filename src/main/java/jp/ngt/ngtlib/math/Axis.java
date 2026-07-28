package jp.ngt.ngtlib.math;

import net.minecraft.core.Direction;

/**
 * 本家 jp.ngt.ngtlib.math.Axis の移植。
 * 宣言順が本家と同一であること (ordinal がスクリプトから見える)。
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

    /** 本家 face.func_176745_a 相当。 */
    public int faceIndex() {
        return this.face.get3DDataValue();
    }
}
