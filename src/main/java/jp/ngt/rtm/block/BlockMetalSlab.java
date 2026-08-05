package jp.ngt.rtm.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 鋼材 (薄板)。本家 {@code jp.ngt.rtm.block.BlockMetalSlab} の移植。
 *
 * <p>本家はメタで温度 (15→0) を持ち、ランダム tick で 1 ずつ下がる。
 * 温度が残っている間は乗ると焼ける。冷えたら金ノコで鋼インゴットにできる。
 */
public class BlockMetalSlab extends Block {
    public static final MapCodec<BlockMetalSlab> CODEC = simpleCodec(p -> new BlockMetalSlab());
    /** 本家のメタ。0 = 冷えている。 */
    public static final IntegerProperty TEMPERATURE = IntegerProperty.create("temperature", 0, 15);

    private static final VoxelShape SHAPE = box(0.0D, 0.0D, 0.0D, 16.0D, 1.0D, 16.0D);

    public BlockMetalSlab() {
        super(Properties.of()
            .mapColor(MapColor.METAL)
            .strength(2.0F, 10.0F)
            .sound(SoundType.METAL)
            .lightLevel(state -> state.getValue(TEMPERATURE) > 0 ? 8 : 0)
            .randomTicks()
            .noOcclusion()
            .pushReaction(PushReaction.BLOCK));
        this.registerDefaultState(this.stateDefinition.any().setValue(TEMPERATURE, 0));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TEMPERATURE);
    }

    @Override
    protected VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level,
                                  BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (state.getValue(TEMPERATURE) > 0) {
            entity.hurt(level.damageSources().hotFloor(), 1.0F);
            entity.setRemainingFireTicks(20);
        }
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int temp = state.getValue(TEMPERATURE);
        if (temp > 0) {
            level.setBlock(pos, state.setValue(TEMPERATURE, temp - 1), 2);
        }
    }
}
