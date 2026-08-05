package jp.ngt.rtm.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;

/**
 * RTM 専用作業台 / レール用作業台。本家 {@code jp.ngt.rtm.block.BlockTrainWorkBench} の移植。
 *
 * <p>本家はメタ 0/1 の 1 ブロックだが、1.21 にメタが無いので 2 ブロックに分けてある
 * ([[rtmu-train-item-split]] と同じ考え方)。中身は <b>5x5 のクラフト台</b>。
 */
public class BlockTrainWorkBench extends Block {
    public static final MapCodec<BlockTrainWorkBench> CODEC = simpleCodec(p -> new BlockTrainWorkBench(false));

    private final boolean railType;

    public BlockTrainWorkBench(boolean railType) {
        super(Properties.of()
            .mapColor(MapColor.STONE)
            .strength(2.5F)
            .sound(SoundType.WOOD));
        this.railType = railType;
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!level.isClientSide()) {
            player.openMenu(this.menuProvider(pos));
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    private MenuProvider menuProvider(BlockPos pos) {
        return new SimpleMenuProvider(
            (id, inv, p) -> new com.portofino.realtrainmodunofficial.menu.WorkBenchMenu(id, inv,
                net.minecraft.world.inventory.ContainerLevelAccess.create(p.level(), pos)),
            Component.translatable(this.railType
                ? "block.realtrainmodunofficial.rail_workbench"
                : "block.realtrainmodunofficial.train_workbench"));
    }
}
