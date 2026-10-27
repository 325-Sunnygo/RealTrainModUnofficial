package jp.ngt.rtm.block;

import com.mojang.serialization.MapCodec;
import jp.ngt.rtm.block.tileentity.TileEntityMovingMachine;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * 移動装置。本家 {@code jp.ngt.rtm.block.BlockMovingMachine} の移植。
 *
 * <p>2 つ置いてバールで右クリックすると繋がり、その 2 点の間でブロックの塊を運ぶ。
 * バール以外で右クリックすると設定画面 (大きさ・ずらし・速さ)。
 */
public class BlockMovingMachine extends BaseEntityBlock {
    public static final MapCodec<BlockMovingMachine> CODEC = simpleCodec(p -> new BlockMovingMachine(false));

    /** true = 乗り物生成器 (本家メタ 1)。 */
    public final boolean generator;

    public BlockMovingMachine(boolean generator) {
        super(Properties.of()
            .mapColor(MapColor.METAL)
            .strength(2.0F, 10.0F)
            .sound(SoundType.METAL)
            .requiresCorrectToolForDrops());
        this.generator = generator;
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
        return new TileEntityMovingMachine(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                 BlockEntityType<T> type) {
        return (lvl, pos, st, be) -> {
            if (be instanceof TileEntityMovingMachine tile) {
                tile.tick();
            }
        };
    }

    /** バール = 繋ぐ / それ以外 = 設定画面。 */
    @Override
    protected net.minecraft.world.ItemInteractionResult useItemOn(
            ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.is(com.portofino.realtrainmodunofficial.RealTrainModUnofficialItems.CROWBAR_ITEM.get())) {
            if (!level.isClientSide()
                && level.getBlockEntity(pos) instanceof TileEntityMovingMachine tile) {
                if (this.generator) {
                    //本家メタ 1: バールで乗り物を生成
                    tile.generateVehicle(player);
                } else if (!tile.hasPair()) {
                    tile.searchMM();
                }
            }
            return net.minecraft.world.ItemInteractionResult.sidedSuccess(level.isClientSide());
        }
        return net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.isClientSide() && level.getBlockEntity(pos) instanceof TileEntityMovingMachine tile) {
            com.portofino.realtrainmodunofficial.ClientHooks.openMovingMachineScreen(tile.getCore().getBlockPos());
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    /** 信号が変わったら進む向きを見直す。 */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
                                   BlockPos fromPos, boolean movedByPiston) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof TileEntityMovingMachine tile) {
            tile.onBlockChanged();
        }
    }

    /** 壊したら運んでいるブロックを戻してから解除する。 */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof TileEntityMovingMachine tile) {
            tile.reset(true);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}
