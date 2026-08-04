package com.portofino.rtmuautodrive;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * 駅列車ブロック。列車運転スポナーと同じく<b>レールから 5 ブロック以内</b>に置く。
 * ここが自動運転の停止位置になる。
 */
public class StationStopBlock extends BaseEntityBlock {

    public static final MapCodec<StationStopBlock> CODEC = simpleCodec(StationStopBlock::new);

    public StationStopBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StationStopBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        StationStopRegistry.get(server).setName(pos, "");
        if (placer instanceof Player player && TrainDispatcherBlockEntity.findRail(level, pos) == null) {
            player.displayClientMessage(Component.translatable("message.rtmuautodrive.no_rail_near"), false);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof StationStopBlockEntity be) {
            //駅名を付ける画面を開く (スポナーの一覧にこの名前が出る)
            ClientBridge.openStationName(player, pos, be.getStationName());
        }
        return InteractionResult.CONSUME;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel server) {
            StationStopRegistry.get(server).remove(pos);
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
