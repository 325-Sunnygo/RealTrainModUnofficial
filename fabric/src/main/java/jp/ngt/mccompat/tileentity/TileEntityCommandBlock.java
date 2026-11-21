package jp.ngt.mccompat.tileentity;

import net.minecraft.world.level.block.entity.CommandBlockEntity;

/**
 * 1.7.10 net.minecraft.tileentity.TileEntityCommandBlock のスクリプト互換ラッパー。
 * 列車検知器 (hi03TrainDetector 等) のサーバースクリプトは、自分の真下を掘って
 * コマンドブロックを探し、そのコマンド文字列を設定ファイルとして読む:
 */
@SuppressWarnings("unused")
public final class TileEntityCommandBlock {

    public final CommandBlockEntity blockEntity;

    public TileEntityCommandBlock(CommandBlockEntity blockEntity) {
        this.blockEntity = blockEntity;
    }

    /** func_145993_a = getCommandBlockLogic */
    public CommandBlockLogic func_145993_a() {
        return new CommandBlockLogic(this.blockEntity.getCommandBlock().getCommand());
    }

    public CommandBlockLogic getCommandBlockLogic() {
        return this.func_145993_a();
    }

    /** func_174877_v = getPos */
    public net.minecraft.core.BlockPos func_174877_v() {
        return this.blockEntity.getBlockPos();
    }

    @Override
    public String toString() {
        return "TileEntityCommandBlock" + this.blockEntity.getBlockPos();
    }
}
