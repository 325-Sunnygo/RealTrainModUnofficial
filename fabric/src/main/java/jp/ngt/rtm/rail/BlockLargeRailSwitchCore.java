package jp.ngt.rtm.rail;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * 分岐コアブロック (本家では BlockLargeRailCore のバリアント登録)。
 */
public class BlockLargeRailSwitchCore extends BlockLargeRailBase {
    public static final MapCodec<BlockLargeRailSwitchCore> CODEC = simpleCodec(props -> new BlockLargeRailSwitchCore(2, props));

    public BlockLargeRailSwitchCore(int par1, Properties props) {
        super(par1, props);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileEntityLargeRailSwitchCore(pos, state);
    }

    @Override
    public boolean isCore() {
        return true;
    }
}
