package jp.ngt.rtm.item;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficialBlocks;
import com.portofino.realtrainmodunofficial.RealTrainModUnofficialItems;
import jp.ngt.rtm.block.BlockMetalSlab;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 金ノコ。本家 {@code jp.ngt.rtm.item.ItemHacksaw} の移植。
 * 冷えた鋼材 (steel_slab の温度 0) を切って鋼インゴットにする。
 */
public class ItemHacksaw extends Item {
    public ItemHacksaw() {
        super(new Properties().stacksTo(1).durability(Tiers.IRON.getUses()));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        BlockPos pos = context.getClickedPos();
        ItemStack itemStack = context.getItemInHand();
        if (player == null || !player.mayUseItemAt(pos, context.getClickedFace(), itemStack)) {
            return InteractionResult.FAIL;
        }

        BlockState state = level.getBlockState(pos);
        if (state.is(RealTrainModUnofficialBlocks.STEEL_SLAB.get())
            && state.getValue(BlockMetalSlab.TEMPERATURE) == 0) {
            if (level.isClientSide()) {
                return InteractionResult.SUCCESS;
            }
            player.spawnAtLocation(new ItemStack(RealTrainModUnofficialItems.INGOT_STEEL_ITEM.get(), 1));
            level.removeBlock(pos, false);
            itemStack.hurtAndBreak(1, player, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.FAIL;
    }

    /** 本家 canDestroyBlockInCreative=false 相当。 */
    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        return false;
    }
}
