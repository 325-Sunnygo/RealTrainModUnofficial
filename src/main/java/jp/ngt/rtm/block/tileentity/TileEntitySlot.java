package jp.ngt.rtm.block.tileentity;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficialBlockEntities;
import jp.ngt.rtm.block.BlockSlot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** スロットの中身。本家 jp.ngt.rtm.block.tileentity.TileEntity… */
public class TileEntitySlot extends BlockEntity {
    private int count;

    public TileEntitySlot(BlockPos pos, BlockState state) {
        super(RealTrainModUnofficialBlockEntities.SLOT.get(), pos, state);
    }

    public void serverTick() {
        ++this.count;
        if (this.count > 4) {
            this.count = 0;
        }
        if (this.count == 0 && this.level != null && this.level.hasNeighborSignal(this.getBlockPos())) {
            if (this.getBlockState().getBlock() instanceof BlockSlot slot) {
                slot.inhaleLiquid(this.level, this.getBlockPos());
            }
        }
    }
}
