package com.portofino.realtrainmodunofficial.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CrossingGateBlock extends Block {
    public static final MapCodec<CrossingGateBlock> CODEC = simpleCodec(CrossingGateBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    private static final VoxelShape BASE_SHAPE = box(5.0D, 0.0D, 5.0D, 11.0D, 16.0D, 11.0D);
    private static final VoxelShape ARM_UP_NORTH = box(8.0D, 10.0D, 8.0D, 10.0D, 26.0D, 10.0D);
    private static final VoxelShape ARM_UP_EAST = box(8.0D, 10.0D, 8.0D, 10.0D, 26.0D, 10.0D);
    private static final VoxelShape ARM_DOWN_NORTH = box(7.0D, 10.0D, 0.0D, 9.0D, 12.0D, 16.0D);
    private static final VoxelShape ARM_DOWN_EAST = box(0.0D, 10.0D, 7.0D, 16.0D, 12.0D, 9.0D);

    public CrossingGateBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(POWERED, false));
    }

    public CrossingGateBlock() {
        this(BlockBehaviour.Properties.of().sound(SoundType.METAL).strength(1.0F, 6.0F).noOcclusion());
    }

    @Override
    protected @NotNull MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState()
            .setValue(FACING, context.getHorizontalDirection())
            .setValue(POWERED, context.getLevel().hasNeighborSignal(context.getClickedPos()));
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        if (!level.isClientSide) {
            updatePoweredState(level, pos, state);
        }
        super.onPlace(state, level, pos, oldState, isMoving);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        if (!level.isClientSide) {
            updatePoweredState(level, pos, state);
        }
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
    }

    @Override
    public void tick(@NotNull BlockState state, @NotNull ServerLevel level, @NotNull BlockPos pos, @NotNull RandomSource random) {
        updatePoweredState(level, pos, state);
    }

    private void updatePoweredState(Level level, BlockPos pos, BlockState state) {
        boolean powered = level.hasNeighborSignal(pos);
        if (powered != state.getValue(POWERED)) {
            level.setBlock(pos, state.setValue(POWERED, powered), Block.UPDATE_ALL);
        }
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @NotNull VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext context) {
        return getGateShape(state);
    }

    @Override
    protected @NotNull VoxelShape getCollisionShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext context) {
        return getGateShape(state);
    }

    private VoxelShape getGateShape(BlockState state) {
        Direction facing = state.getValue(FACING);
        boolean powered = state.getValue(POWERED);
        boolean northSouth = facing == Direction.NORTH || facing == Direction.SOUTH;
        VoxelShape arm = powered
            ? (northSouth ? ARM_DOWN_NORTH : ARM_DOWN_EAST)
            : (northSouth ? ARM_UP_NORTH : ARM_UP_EAST);
        return net.minecraft.world.phys.shapes.Shapes.or(BASE_SHAPE, arm);
    }
}
