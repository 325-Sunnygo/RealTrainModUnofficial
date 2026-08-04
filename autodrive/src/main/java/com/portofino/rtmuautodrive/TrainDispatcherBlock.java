package com.portofino.rtmuautodrive;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/** 列車運転スポナー。右クリックで名前と編成アイテムを設定する。 */
public class TrainDispatcherBlock extends BaseEntityBlock {

    public static final MapCodec<TrainDispatcherBlock> CODEC = simpleCodec(TrainDispatcherBlock::new);

    public TrainDispatcherBlock(Properties properties) {
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
        return new TrainDispatcherBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!(level.getBlockEntity(pos) instanceof TrainDispatcherBlockEntity be)) {
            return;
        }
        if (placer != null) {
            //置いた人の向きへ発車する
            be.setLaunchYaw(placer.getYRot());
        }
        if (level instanceof ServerLevel server) {
            DispatcherRegistry.get(server).setName(pos, be.getDispatcherName());
            //レールが近くに無ければその場で知らせる (5 ブロック以内が条件)
            if (placer instanceof Player player && TrainDispatcherBlockEntity.findRail(level, pos) == null) {
                player.displayClientMessage(
                        Component.translatable("message.rtmuautodrive.no_rail_near"), false);
            }
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof TrainDispatcherBlockEntity be) {
            player.openMenu(be, buf -> buf.writeBlockPos(pos));
        }
        return InteractionResult.CONSUME;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof TrainDispatcherBlockEntity be) {
                //中の編成アイテムを落とす
                Block.popResource(level, pos, be.getItem(0));
            }
            if (level instanceof ServerLevel server) {
                DispatcherRegistry.get(server).remove(pos);
            }
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
