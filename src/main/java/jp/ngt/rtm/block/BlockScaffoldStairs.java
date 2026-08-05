package jp.ngt.rtm.block;

import com.portofino.realtrainmodunofficial.blockentity.InstalledObjectBlockEntity;
import com.portofino.realtrainmodunofficial.installedobject.InstalledObjectCategory;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;

/**
 * 足場の階段の接続判定。本家 {@code jp.ngt.rtm.block.BlockScaffoldStairs} のうち、
 * <b>描画スクリプトから呼ばれる静的メソッドだけ</b>を移植したもの。
 *
 * <p>{@code RenderStair.js} / {@code RenderEscalatorUp/Down.js} が
 * {@code BlockScaffoldStairs.getConnectionType(world, x, y, z, dir)} を直に呼ぶ。
 * ★このクラスが無いとスクリプトが例外で止まり、全パーツが描かれてぐちゃぐちゃに見える
 * ({@link BlockScaffold} と同じ罠)。
 *
 * <p>本家との違い: 階段の一致判定は「<b>同じ向き (dir == dir2)</b> のときだけ 3」。
 * 足場側 ({@link BlockScaffold}) の 3 とは条件が違うので共用しない。
 */
public final class BlockScaffoldStairs {

    /** なし:0 / 足場(Z向き):1 / 足場(X向き):2 / 同じ向きの階段:3 / 立方体:4 */
    public static byte getConnectionType(BlockGetter level, int x, int y, int z, int dir) {
        if (level == null) {
            return 0;
        }
        BlockPos pos = new BlockPos(x, y, z);
        InstalledObjectCategory category = categoryAt(level, pos);
        if (category == InstalledObjectCategory.SCAFFOLD) {
            int d2 = BlockScaffold.dirAt(level, pos);
            return (byte) ((d2 == 0 || d2 == 2) ? 1 : 2);
        }
        if (category == InstalledObjectCategory.STAIR) {
            return sameDir(level, pos, dir);
        }
        //本家は上下 1 ブロックの階段も見る (上り口/下り口)
        if (categoryAt(level, pos.below()) == InstalledObjectCategory.STAIR) {
            return sameDir(level, pos.below(), dir);
        }
        if (categoryAt(level, pos.above()) == InstalledObjectCategory.STAIR) {
            return sameDir(level, pos.above(), dir);
        }
        return level.getBlockState(pos).isSolidRender(level, pos) ? (byte) 4 : (byte) 0;
    }

    private static byte sameDir(BlockGetter level, BlockPos pos, int dir) {
        return (byte) (BlockScaffold.dirAt(level, pos) == dir ? 3 : 0);
    }

    private static InstalledObjectCategory categoryAt(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof InstalledObjectBlockEntity be ? be.getCategory() : null;
    }

    private BlockScaffoldStairs() {
    }
}
