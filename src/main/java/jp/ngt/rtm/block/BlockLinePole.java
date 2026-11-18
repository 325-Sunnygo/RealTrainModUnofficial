package jp.ngt.rtm.block;

import com.portofino.realtrainmodunofficial.blockentity.InstalledObjectBlockEntity;
import com.portofino.realtrainmodunofficial.installedobject.InstalledObjectCategory;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/** 本家 jp.ngt.rtm.block.BlockLinePole の スクリプト互換ファサード。 */
public final class BlockLinePole {

    private BlockLinePole() {
    }

    /** 本家 BlockLinePole.isConnected。指定座標が「柱として繋がる相手」かを返す。 */
    public static boolean isConnected(Object world, int x, int y, int z, int type) {
        if (type != 0 && type != 1 && type != 2) {
            return true;
        }
        if (!(world instanceof BlockGetter level)) {
            return false;
        }
        BlockPos pos = new BlockPos(x, y, z);
        BlockState state = level.getBlockState(pos);
        // 本家: linePole / framework / signal が「柱」。
        boolean isPole = false;
        if (level.getBlockEntity(pos) instanceof InstalledObjectBlockEntity be) {
            InstalledObjectCategory category = be.getCategory();
            isPole = category == InstalledObjectCategory.OVERHEAD_LINE_POLE
                || category == InstalledObjectCategory.SIGNAL;
        }
        if (type == 0) {
            return isPole;
        }
        if (type == 1) {
            return isPole || state.isSolidRender(level, pos);
        }
        // type == 2
        return !state.isAir() && state.getFluidState().isEmpty();
    }
}
