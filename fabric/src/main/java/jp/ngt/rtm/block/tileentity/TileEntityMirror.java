package jp.ngt.rtm.block.tileentity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import jp.ngt.rtm.block.BlockMirror;

public final class TileEntityMirror extends BlockEntity {
    public TileEntityMirror(BlockPos pos,BlockState state){super(com.portofino.realtrainmodunofficial.RealTrainModUnofficialBlockEntities.MIRROR.get(),pos,state);}
    public int getAlpha(){return getBlockState().getValue(BlockMirror.ALPHA)*17;}
}
