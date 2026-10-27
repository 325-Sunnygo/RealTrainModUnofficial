package jp.ngt.rtm.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

/**
 * 鉄柱。本家 {@code jp.ngt.rtm.block.BlockIronPillar} の移植。
 * 登れる (はしご扱い)。同じブロックが隣にあるとその面を描かない。
 */
public class BlockIronPillar extends Block {
    public static final MapCodec<BlockIronPillar> CODEC = simpleCodec(p -> new BlockIronPillar());

    public BlockIronPillar() {
        super(Properties.of()
            .mapColor(MapColor.METAL)
            .strength(2.0F, 10.0F)
            .sound(SoundType.METAL)
            .noOcclusion()
            .requiresCorrectToolForDrops());
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    /** 本家 shouldSideBeRendered: 隣が同一ブロックのときだけ面を描かない。 */
    @Override
    protected boolean skipRendering(BlockState state, BlockState adjacentState, Direction direction) {
        return adjacentState.is(this);
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }
}
