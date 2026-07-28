package com.portofino.realtrainmodunofficial.block;

import com.mojang.serialization.MapCodec;
import com.portofino.realtrainmodunofficial.RealTrainModUnofficialItems;
import com.portofino.realtrainmodunofficial.blockentity.MiniatureBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 設置済みミニチュア (neo mcte)。本家 MCTE BlockMiniature 相当。
 * 見た目は全て com.portofino.realtrainmodunofficial.blockentity.MiniatureBlockEntity
 * が持つ中身を描く。ブロック自体は描かない (RenderShape#INVISIBLE)。
 */
public class MiniatureBlock extends BaseEntityBlock {

    public static final MapCodec<MiniatureBlock> CODEC = simpleCodec(p -> new MiniatureBlock());

    /** 当たり判定は 1 ブロック丸ごとではなく中央の小さな箱。模型の周りを歩けるようにする。 */
    private static final VoxelShape SHAPE = Shapes.box(0.25D, 0.0D, 0.25D, 0.75D, 0.5D, 0.75D);

    public MiniatureBlock() {
        super(BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(0.8F)
            .sound(SoundType.METAL)
            .noOcclusion()
            .lightLevel(state -> 0)
            .pushReaction(PushReaction.BLOCK));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MiniatureBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        // 中身はブロックエンティティレンダラが描く。ブロック本体は描かない。
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    // ★@Override を付けないこと: これは NeoForge が足したメソッドで、バニラには無い
    public int getLightEmission(BlockState state, BlockGetter level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof MiniatureBlockEntity be) {
            return Math.max(0, Math.min(15, be.getLightValue()));
        }
        return 0;
    }

    /**
     * 壊したら中身ごとアイテムに戻す。
     * これが無いと設置した瞬間に中身が失われる。設定 (縮尺/オフセット/モード) も往復させる。
     */
    @Override
    public ItemStack getCloneItemStack(net.minecraft.world.level.LevelReader level, BlockPos pos, BlockState state) {
        return createItem(level.getBlockEntity(pos));
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state,
                                        net.minecraft.world.entity.player.Player player) {
        if (!level.isClientSide && !player.isCreative()) {
            ItemStack drop = createItem(level.getBlockEntity(pos));
            if (!drop.isEmpty()) {
                Block.popResource(level, pos, drop);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    private static ItemStack createItem(BlockEntity be) {
        ItemStack stack = new ItemStack(RealTrainModUnofficialItems.MINIATURE_ITEM.get());
        if (be instanceof MiniatureBlockEntity mini) {
            CompoundTag tag = mini.getMiniatureTag();
            if (!tag.isEmpty()) {
                stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            }
        }
        return stack;
    }
}
