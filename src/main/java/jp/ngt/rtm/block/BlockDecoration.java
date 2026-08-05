package jp.ngt.rtm.block;

import com.mojang.serialization.MapCodec;
import jp.ngt.rtm.block.tileentity.TileEntityDecoration;
import jp.ngt.rtm.item.ItemDecoration;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 装飾ブロック。本家 {@code jp.ngt.rtm.block.BlockDecoration} の移植。
 * 当たり判定はフルキューブ、描画は BER (DecorationRenderer)。
 */
public class BlockDecoration extends BaseEntityBlock {

    public static final MapCodec<BlockDecoration> CODEC = simpleCodec(p -> new BlockDecoration(p));

    public BlockDecoration(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileEntityDecoration(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    /** 本家 (fixRtm) getPickBlock: モデル名入りの装飾アイテムを返す。 */
    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        ItemStack stack = new ItemStack(
            com.portofino.realtrainmodunofficial.RealTrainModUnofficialItems.ITEM_DECORATION.get());
        if (level.getBlockEntity(pos) instanceof TileEntityDecoration tile) {
            ItemDecoration.setModel(stack, tile.getModelName());
        }
        return stack;
    }

    @Override
    protected int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return level.getMaxLightLevel();
    }
}
