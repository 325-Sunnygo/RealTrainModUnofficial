package jp.ngt.rtm.rail;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * 本家 jp.ngt.rtm.rail.BlockLargeRailSwitchBase の忠実移植。
 * (本家の onNeighborBlockChange はコメントアウト済 = 分岐のRS判定は Point.onUpdate のポーリング)
 */
public class BlockLargeRailSwitchBase extends BlockLargeRailBase {
    public static final MapCodec<BlockLargeRailSwitchBase> CODEC = simpleCodec(props -> new BlockLargeRailSwitchBase(2, props));

    public BlockLargeRailSwitchBase(int par1, Properties props) {
        super(par1, props);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileEntityLargeRailSwitchBase(pos, state);
    }

    @Override
    public boolean isCore() {
        return false;
    }
}
