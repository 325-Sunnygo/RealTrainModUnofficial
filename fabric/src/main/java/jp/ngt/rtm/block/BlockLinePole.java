package jp.ngt.rtm.block;

import com.portofino.realtrainmodunofficial.blockentity.InstalledObjectBlockEntity;
import com.portofino.realtrainmodunofficial.installedobject.InstalledObjectCategory;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 本家 jp.ngt.rtm.block.BlockLinePole の <b>スクリプト互換ファサード</b>。
 *
 * <p>本家では架線柱は専用ブロックだったが、RTMU では他の設置物と同じ
 * {@code InstalledObjectBlock} (カテゴリ OVERHEAD_LINE_POLE) に載っている。
 * ただし架線柱のレンダースクリプト RenderConnectablePole.js が
 * <pre>
 *   importPackage(Packages.jp.ngt.rtm.block);
 *   var conXP = BlockLinePole.isConnected(world, x + 1, y, z, 0);
 * </pre>
 * とこのクラスを名前で直接呼ぶので、同じパッケージ・同じシグネチャで用意しておく。
 * これが無いと隣の柱とつながる腕 (partXP/partXN/partZP/partZN) が出ず、柱が単体で立つだけになる。
 */
public final class BlockLinePole {

    private BlockLinePole() {
    }

    /**
     * 本家 BlockLinePole.isConnected。指定座標が「柱として繋がる相手」かを返す。
     *
     * @param type 0=柱(架線柱/信号柱)のみ / 1=柱または不透過ブロック / 2=空気と液体以外 / それ以外=常に true
     */
    public static boolean isConnected(Object world, int x, int y, int z, int type) {
        if (type != 0 && type != 1 && type != 2) {
            return true;
        }
        if (!(world instanceof BlockGetter level)) {
            return false;
        }
        BlockPos pos = new BlockPos(x, y, z);
        BlockState state = level.getBlockState(pos);
        //本家: linePole / framework / signal が「柱」。RTMU ではどれも InstalledObjectBlock なので
        //ブロックの種類ではなくブロックエンティティのカテゴリで見る。
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
