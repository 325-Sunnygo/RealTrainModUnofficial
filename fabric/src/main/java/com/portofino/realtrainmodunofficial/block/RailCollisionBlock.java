package com.portofino.realtrainmodunofficial.block;

import com.mojang.serialization.MapCodec;
import com.portofino.realtrainmodunofficial.RealTrainModUnofficialItems;
import com.portofino.realtrainmodunofficial.blockentity.RailCollisionBlockEntity;
import com.portofino.realtrainmodunofficial.blockentity.LargeRailCoreBlockEntity;
import jp.ngt.rtm.rail.util.RailMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * レールの「薄い当たり判定」専用ブロック。
 * 見た目は描かず、破壊されたら対応するレールコアも削除する。
 */
public class RailCollisionBlock extends BaseEntityBlock {
    public static final MapCodec<RailCollisionBlock> CODEC = simpleCodec(RailCollisionBlock::new);
    // 本家RTM準拠: ブロック底からレール面の高さまでのスラブ box(0,0,0,16,railTop,16)。
    // 平坦レールは surfaceY=0 → 1px の薄いスラブ(=カーペット)。坂はレール面まで床から詰めるので
    // 「ブロック全体」にも「浮いた薄板」にもならず、レール形状に沿って当たる/狙える/壊せる。
    private static VoxelShape railShape(BlockGetter level, BlockPos pos) {
        float s = 0.0f;
        if (level.getBlockEntity(pos) instanceof RailCollisionBlockEntity rbe) {
            s = rbe.getSurfaceY();
        }
        double top = Math.max(1.0D, Math.min(16.0D, s * 16.0D)); // 最低 1px(本家の 0.0625 相当)
        return box(0.0D, 0.0D, 0.0D, 16.0D, top, 16.0D);
    }

    public RailCollisionBlock(BlockBehaviour.Properties props) {
        super(props);
    }

    public RailCollisionBlock() {
        this(BlockBehaviour.Properties.of()
            .sound(SoundType.METAL)
            .strength(0.1F, 0.1F)
            .noOcclusion()
            .isSuffocating((s, g, p) -> false)
            .isViewBlocking((s, g, p) -> false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return railShape(level, pos);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return railShape(level, pos);
    }

    @Override
    public VoxelShape getInteractionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return railShape(level, pos);
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        // 中ボタン(ピックブロック): レール当たり判定からコアを辿り、種類+形状(長さ)を
        // まるごとコピーした RailItem を返す。地面を右クリックすれば同じレールを再配置できる。
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof RailCollisionBlockEntity rbe) {
            BlockPos corePos = rbe.getCorePos();
            if (corePos != null && level.getBlockEntity(corePos) instanceof LargeRailCoreBlockEntity core) {
                return LargeRailCoreBlock.createRailCloneStack(corePos, core);
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public void onRemove(BlockState state, net.minecraft.world.level.Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!level.isClientSide && state.getBlock() != newState.getBlock() && !RailMap.suppressRailRemoval) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof RailCollisionBlockEntity rbe) {
                BlockPos corePos = rbe.getCorePos();
                if (corePos != null && level.getBlockState(corePos).getBlock() instanceof LargeRailCoreBlock) {
                    level.removeBlock(corePos, false);
                }
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RailCollisionBlockEntity(pos, state);
    }

    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.PASS;
        }
        if (!player.getItemInHand(hand).is(RealTrainModUnofficialItems.CROWBAR_ITEM.get())) {
            return InteractionResult.PASS;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof RailCollisionBlockEntity collision) {
            BlockPos corePos = collision.getCorePos();
            if (corePos != null && level.getBlockState(corePos).getBlock() instanceof LargeRailCoreBlock) {
                level.removeBlock(corePos, false);
                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.PASS;
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, net.minecraft.world.level.block.Block block, BlockPos fromPos, boolean isMoving) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof RailCollisionBlockEntity collision) {
            BlockPos corePos = collision.getCorePos();
            if (corePos != null && level.getBlockEntity(corePos) instanceof com.portofino.realtrainmodunofficial.blockentity.LargeRailCoreBlockEntity core) {
                core.requestSwitchStateRefresh();
            }
        }
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
    }
}
