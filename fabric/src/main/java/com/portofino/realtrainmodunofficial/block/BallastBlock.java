package com.portofino.realtrainmodunofficial.block;

import com.mojang.serialization.MapCodec;
import com.portofino.realtrainmodunofficial.blockentity.BallastBlockEntity;
import jp.ngt.rtm.rail.util.RailMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 道床ブロック。高さ1px(1/16) = カーペット相当の薄板。見た目あり（バラスト/砂利テクスチャ）。
 * 対応レールコア(LargeRailCore)位置を BlockEntity に保持し、道床を壊すとレールも撤去される
 * (ユーザー要望)。列車設置時のレール検出 (getRailMapAt) でも道床からコアを引ける。
 * 当たり判定はこのブロック自体(y-1)が持つため、列車/プレイヤーは道床の上に立つ。
 */
public class BallastBlock extends BaseEntityBlock {
    public static final MapCodec<BallastBlock> CODEC = simpleCodec(p -> new BallastBlock());
    /** 高さ1px のカーペット相当スラブ */
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 1, 16);

    public BallastBlock() {
        super(BlockBehaviour.Properties.of()
            .sound(SoundType.GRAVEL)
            .strength(0.6F, 3.0F)
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
        // 通常のブロックモデル(砂利テクスチャ)で描画する。
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BallastBlockEntity(pos, state);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        // 道床を壊したら対応するレールコアも撤去する (ユーザー要望)。
        // RailMap.suppressRailRemoval 中(レール自身の撤去処理)は連鎖させない。
        if (!level.isClientSide && state.getBlock() != newState.getBlock() && !RailMap.suppressRailRemoval) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof BallastBlockEntity bbe) {
                BlockPos corePos = bbe.getCorePos();
                if (corePos != null && level.getBlockState(corePos).getBlock() instanceof LargeRailCoreBlock) {
                    level.removeBlock(corePos, false);
                }
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
