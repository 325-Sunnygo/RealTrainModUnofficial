package jp.ngt.rtm.rail;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * 勾配コアブロック (本家 BlockLargeRailSlopeCore 相当)。
 */
public class BlockLargeRailSlopeCore extends BlockLargeRailSlopeBase {
    public static final MapCodec<BlockLargeRailSlopeCore> CODEC = simpleCodec(props -> new BlockLargeRailSlopeCore(2, props));

    public BlockLargeRailSlopeCore(int par1, Properties props) {
        super(par1, props);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileEntityLargeRailSlopeCore(pos, state);
    }

    @Override
    public boolean isCore() {
        return true;
    }
}
