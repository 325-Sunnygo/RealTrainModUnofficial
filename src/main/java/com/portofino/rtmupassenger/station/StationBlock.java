package com.portofino.rtmupassenger.station;

import com.mojang.serialization.MapCodec;
import com.portofino.rtmupassenger.PassengerMod;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

/**
 * 駅ブロック。範囲内に停車しドアの開いた列車へ、待機乗客が自動で乗り降りする。
 * 右クリックで駅を登録し、登録駅数を表示する (行き先は他の登録駅から選ばれる)。
 */
public class StationBlock extends BaseEntityBlock {

    public static final MapCodec<StationBlock> CODEC = simpleCodec(StationBlock::new);

    public StationBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StationBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != PassengerMod.STATION_BE.get()) {
            return null;
        }
        return createTickerHelper(type, PassengerMod.STATION_BE.get(), StationBlockEntity::serverTick);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        // 右クリック: 駅を登録し、現在のタグを添えて設定 GUI をクライアントで開く
        // (旧: Shift+右クリックで 1 個ずつ切替 → GUI で複数タグを ON/OFF に変更)。
        if (!level.isClientSide && level instanceof net.minecraft.server.level.ServerLevel sl
                && level.getBlockEntity(pos) instanceof StationBlockEntity
                && player instanceof net.minecraft.server.level.ServerPlayer sp) {
            StationRegistry reg = StationRegistry.get(sl);
            reg.add(pos);
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(sp,
                    new com.portofino.realtrainmodunofficial.network.OpenStationScreenPayload(
                            pos, reg.tagBits(pos)));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())
                && level.getBlockEntity(pos) instanceof StationBlockEntity be) {
            be.onDestroyed();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
