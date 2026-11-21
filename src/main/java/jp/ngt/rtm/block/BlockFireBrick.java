package jp.ngt.rtm.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.MapColor;

/** 耐火レンガ / 熱風炉レンガ。本家 jp.ngt.rtm.block.BlockFireBrick… */
public class BlockFireBrick extends Block {
    public static final MapCodec<BlockFireBrick> CODEC = simpleCodec(p -> new BlockFireBrick(true));
    /** 本家のメタ (溜まった熱)。 */
    public static final IntegerProperty HEAT = IntegerProperty.create("heat", 0, 15);

    private final boolean changeColor;

    public BlockFireBrick(boolean randomTick) {
        super(buildProperties(randomTick));
        this.changeColor = randomTick;
        this.registerDefaultState(this.stateDefinition.any().setValue(HEAT, 0));
    }

    private static Properties buildProperties(boolean randomTick) {
        Properties p = Properties.of()
            .mapColor(MapColor.COLOR_RED)
            .strength(2.0F, 10.0F)
            .sound(SoundType.STONE)
            .requiresCorrectToolForDrops();
        if (randomTick) {
            p = p.randomTicks().lightLevel(state -> state.getValue(HEAT) > 0 ? state.getValue(HEAT) : 0);
        }
        return p;
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HEAT);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!this.changeColor) {
            return;
        }
        // 本家 BlockFireBrick.updateTick (hotStoveBrick): 隣の空…
        int heat = state.getValue(HEAT);
        int n = 16 - heat;
        for (Direction face : Direction.values()) {
            BlockPos target = pos.relative(face);
            BlockState targetState = level.getBlockState(target);
            if (targetState.isAir()) {
                if (heat > 0 && random.nextInt(n) == 0) {
                    level.setBlock(target, com.portofino.realtrainmodunofficial
                        .RealTrainModUnofficialBlocks.FURNACE_FIRE.get().defaultBlockState()
                        .setValue(jp.ngt.ngtlib.block.BlockLiquidBase.AMOUNT, 15), 2);
                    level.scheduleTick(target,
                        com.portofino.realtrainmodunofficial.RealTrainModUnofficialBlocks.FURNACE_FIRE.get(), 5);
                    level.setBlock(pos, state.setValue(HEAT, 0), 2);
                    return;
                }
            } else if (targetState.getBlock() instanceof BlockFurnaceFire gasBlock && !gasBlock.isFire()) {
                int m0 = targetState.getValue(jp.ngt.ngtlib.block.BlockLiquidBase.AMOUNT);
                if (heat < 15 && m0 > 0) {
                    level.setBlock(pos, state.setValue(HEAT, heat + 1), 2);
                    level.setBlock(target, targetState.setValue(
                        jp.ngt.ngtlib.block.BlockLiquidBase.AMOUNT, m0 - 1), 2);
                    return;
                }
            } else if (targetState.is(Blocks.LAVA) && heat < 15) {
                level.setBlock(pos, state.setValue(HEAT, heat + 1), 2);
                return;
            }
        }
    }

    public boolean isHotStove() {
        return this.changeColor;
    }
}
