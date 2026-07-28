package com.portofino.realtrainmodunofficial.block;

import com.mojang.serialization.MapCodec;
import com.portofino.realtrainmodunofficial.ClientHooks;
import com.portofino.realtrainmodunofficial.RealTrainModUnofficialBlockEntities;
import com.portofino.realtrainmodunofficial.blockentity.SignalRemoteBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * 受信機と変更機は見た目と役割だけが違うので、同じブロック実装を共有します。
 */
public class SignalRemoteBlock extends BaseEntityBlock {
    public enum Mode {
        RECEIVER,
        CHANGER,
        VALUE_INPUT
    }

    private final Mode mode;
    private final MapCodec<SignalRemoteBlock> codec;

    public SignalRemoteBlock(BlockBehaviour.Properties properties, Mode mode) {
        super(properties);
        this.mode = mode;
        this.codec = simpleCodec(blockProperties -> new SignalRemoteBlock(blockProperties, this.mode));
    }

    public SignalRemoteBlock(Mode mode) {
        this(BlockBehaviour.Properties.of().sound(SoundType.METAL).strength(1.0F, 6.0F), mode);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return codec;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return box(1.0D, 0.0D, 1.0D, 15.0D, 14.0D, 15.0D);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getShape(state, level, pos, context);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SignalRemoteBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return null;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
        openScreen(level, pos);
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        openScreen(level, pos);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private void openScreen(Level level, BlockPos pos) {
        if (!level.isClientSide) {
            return;
        }
        switch (mode) {
            case CHANGER -> ClientHooks.openSignalChangerScreen(pos);
            case VALUE_INPUT -> ClientHooks.openSignalValueScreen(pos);
            case RECEIVER -> {
                if (level.getBlockEntity(pos) instanceof SignalRemoteBlockEntity blockEntity
                    && blockEntity.getLinkedChannel() > 0) {
                    ClientHooks.openSignalChangerScreen(pos);
                } else {
                    ClientHooks.openSignalReceiverScreen(pos);
                }
            }
        }
    }
}
