package jp.ngt.rtm.block;

import com.portofino.realtrainmodunofficial.blockentity.InstalledObjectBlockEntity;
import com.portofino.realtrainmodunofficial.installedobject.InstalledObjectCategory;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;

/**
 * 足場の接続判定。本家 {@code jp.ngt.rtm.block.BlockScaffold} のうち、
 * <b>描画スクリプトから呼ばれる静的メソッドだけ</b>を移植したもの。
 *
 * <p>本家の {@code RenderScaffold.js} は
 * {@code BlockScaffold.getConnectionType(world, x, y, z, dir)} を直に呼ぶ。
 * ★このクラスが無いとスクリプトが例外で止まり、<b>手すりが 4 方向とも描かれる</b>。
 *
 * <p>足場ブロックそのものは RTMU では設置物 ({@code InstalledObjectBlock}) が兼ねている。
 */
public final class BlockScaffold {

    /** なし:0 / 足場(Z向き):1 / 足場(X向き):2 / 階段:3 / 立方体:4 */
    public static byte getConnectionType(BlockGetter level, int x, int y, int z, int dir) {
        if (level == null) {
            return 0;
        }
        BlockPos pos = new BlockPos(x, y, z);
        InstalledObjectCategory category = categoryAt(level, pos);
        if (category == InstalledObjectCategory.SCAFFOLD) {
            int d2 = dirAt(level, pos);
            return (byte) ((d2 == 0 || d2 == 2) ? 1 : 2);
        }
        if (category == InstalledObjectCategory.STAIR) {
            return stairFlag(level, pos, dir);
        }
        //本家は「1 つ下が階段」も見る (階段の上り口)
        if (categoryAt(level, pos.below()) == InstalledObjectCategory.STAIR) {
            return stairFlag(level, pos.below(), dir);
        }
        return level.getBlockState(pos).isSolidRender(level, pos) ? (byte) 4 : (byte) 0;
    }

    private static byte stairFlag(BlockGetter level, BlockPos pos, int dir) {
        int d2 = dirAt(level, pos);
        boolean flag = (dir == 1 && (d2 == 1 || d2 == 3)) || (dir == 0 && (d2 == 0 || d2 == 2));
        return (byte) (flag ? 3 : 0);
    }

    private static InstalledObjectCategory categoryAt(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof InstalledObjectBlockEntity be ? be.getCategory() : null;
    }

    /**
     * ★向きは必ず {@code InstalledObjectBlockEntity.getDir()} から取る。
     * 描画スクリプトも {@code entity.getDir()} を使うので、当たり判定と食い違わせない。
     */
    public static int dirAt(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof InstalledObjectBlockEntity be ? be.getDir() : 0;
    }

    private BlockScaffold() {
    }
}
