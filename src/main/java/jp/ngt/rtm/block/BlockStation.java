package jp.ngt.rtm.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * 駅コア。本家 {@code jp.ngt.rtm.block.BlockStation} の移植。
 * 右クリックで駅名を編集する。
 */
public class BlockStation extends BaseEntityBlock {
    public static final MapCodec<BlockStation> CODEC = simpleCodec(p -> new BlockStation());

    public BlockStation() {
        super(Properties.of()
            .mapColor(MapColor.STONE)
            .strength(2.0F, 10.0F)
            .sound(SoundType.STONE)
            .requiresCorrectToolForDrops());
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
        return new jp.ngt.rtm.block.tileentity.TileEntityStation(pos, state);
    }

    /** 本家 onBlockActivated: クライアント側で駅名編集画面を開く。 */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.isClientSide()) {
            com.portofino.realtrainmodunofficial.ClientHooks.openStationCoreScreen(pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
