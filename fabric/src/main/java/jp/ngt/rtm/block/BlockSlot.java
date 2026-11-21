package jp.ngt.rtm.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.MapColor;
import org.jetbrains.annotations.Nullable;

/**
 * スロット (吸込口)。本家 {@code jp.ngt.rtm.block.BlockSlot} の移植。
 *
 * <p>レッドストーン信号を受けている間、向いている先の液体ブロックを吸い込み、
 * 反対側 (パイプが繋がっていればパイプの先) へ吐き出す。
 * フイゴを当てる相手でもある ({@link jp.ngt.rtm.item.ItemBellows})。
 *
 * <p>★本家はパイプ ({@code BlockPipe}) を辿って吐き出し先を選ぶが、RTMU のパイプは
 * 設置物 (InstalledObject) で別物なので<b>真後ろへ吐き出すだけ</b>にしてある。
 */
public class BlockSlot extends Block implements EntityBlock {
    public static final MapCodec<BlockSlot> CODEC = simpleCodec(p -> new BlockSlot());
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    public BlockSlot() {
        super(Properties.of()
            .mapColor(MapColor.COLOR_RED)
            .strength(2.0F, 10.0F)
            .sound(SoundType.STONE)
            .requiresCorrectToolForDrops());
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.UP));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        //本家 getStateForPlacement: クリックした面の反対を向く
        return this.defaultBlockState().setValue(FACING, context.getClickedFace().getOpposite());
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new jp.ngt.rtm.block.tileentity.TileEntitySlot(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                 BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return (lvl, pos, st, be) -> {
            if (be instanceof jp.ngt.rtm.block.tileentity.TileEntitySlot slot) {
                slot.serverTick();
            }
        };
    }

    /** 本家 {@code inhaleLiquid}: 向いている先の液体を吸い、反対側へ移す。 */
    public void inhaleLiquid(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof BlockSlot)) {
            return;
        }
        Direction facing = state.getValue(FACING);
        BlockPos from = pos.relative(facing);
        BlockState target = level.getBlockState(from);
        if (target.getFluidState().isEmpty()) {
            return;
        }
        BlockPos to = pos.relative(facing.getOpposite()).relative(facing.getOpposite());
        if (level.getBlockState(to).isAir()) {
            level.setBlock(to, target, 3);
            level.removeBlock(from, false);
        }
    }
}
