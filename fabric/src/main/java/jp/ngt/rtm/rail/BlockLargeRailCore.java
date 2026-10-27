package jp.ngt.rtm.rail;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/** 本家 jp.ngt.rtm.rail.BlockLargeRailCore の忠実移植。 */
public class BlockLargeRailCore extends BlockLargeRailBase {
    public static final net.minecraft.world.level.block.state.properties.BooleanProperty SECTIONED=net.minecraft.world.level.block.state.properties.BooleanProperty.create("sectioned");
    public static final MapCodec<BlockLargeRailCore> CODEC = simpleCodec(props -> new BlockLargeRailCore(2, props));

    public BlockLargeRailCore(int par1, Properties props) {
        super(par1, props);
        registerDefaultState(stateDefinition.any().setValue(SECTIONED,false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(SECTIONED)?new jp.kaiz.kaizpatch.rtm.rail.TileEntityLargeRailSectionCore(pos,state):new TileEntityLargeRailNormalCore(pos, state);
    }

    @Override protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<net.minecraft.world.level.block.Block,BlockState> builder) {super.createBlockStateDefinition(builder);builder.add(SECTIONED);}

    @Override
    public boolean isCore() {
        return true;
    }
}
