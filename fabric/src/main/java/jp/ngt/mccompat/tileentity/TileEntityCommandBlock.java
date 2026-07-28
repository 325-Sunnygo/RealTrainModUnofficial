package jp.ngt.mccompat.tileentity;

import net.minecraft.world.level.block.entity.CommandBlockEntity;

/**
 * 1.7.10 net.minecraft.tileentity.TileEntityCommandBlock のスクリプト互換ラッパー。
 *
 * <p>列車検知器 (hi03TrainDetector 等) のサーバースクリプトは、自分の真下を掘って
 * コマンドブロックを探し、その<b>コマンド文字列を設定ファイルとして読む</b>:
 * <pre>
 *   var block = world.func_147438_o(x, y - i, z);
 *   if (block instanceof TileEntityCommandBlock) return block;
 *   ...
 *   var command = NGTUtil.getField(CommandBlockLogic.class, commandBlock.func_145993_a(),
 *                                  ["Command", "field_145763_e"]);
 *   var data = JSON.parse(command);   //{"setting":{"outputPos":[x,y,z]}, "train":{...}}
 * </pre>
 *
 * <p>1.21 の {@link CommandBlockEntity} には {@code instanceof} を通せないので、
 * {@code WorldCompat.func_147438_o} がコマンドブロックだけこのラッパーに包んで返す。
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
