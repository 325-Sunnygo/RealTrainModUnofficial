package com.portofino.rtmupassenger.station;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 停止位置目標。透明で当たり判定なしのマーカー。
 * 乗客はここへ歩いてきて、列車が停まりドアが開くまで待つ。
 */
public class StopTargetBlock extends Block {

    public static final MapCodec<StopTargetBlock> CODEC = simpleCodec(StopTargetBlock::new);

    // 中央あたりの薄いマーカー枠 (アウトライン用)。衝突には使わない。
    private static final VoxelShape OUTLINE = Shapes.box(0.3D, 0.0D, 0.3D, 0.7D, 0.5D, 0.7D);

    public StopTargetBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return OUTLINE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty(); //通り抜け可能
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }
}
