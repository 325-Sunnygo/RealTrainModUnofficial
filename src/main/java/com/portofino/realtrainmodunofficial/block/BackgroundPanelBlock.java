package com.portofino.realtrainmodunofficial.block;

import com.mojang.serialization.MapCodec;
import com.portofino.realtrainmodunofficial.ClientHooks;
import com.portofino.realtrainmodunofficial.RealTrainModUnofficialItems;
import com.portofino.realtrainmodunofficial.blockentity.BackgroundPanelBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 背景パネル。模型やジオラマの背景に写真を立てる。
 *
 * <p>置くと<b>紫と黒の格子 (未設定の印)</b> が出る。右クリックで大きさと画像を選び、
 * 保存すると その画像が出る。
 *
 * <p>ブロック自体は小さな台座だけ。絵は
 * {@code BackgroundPanelBlockEntityRenderer} が板として描く。
 */
public class BackgroundPanelBlock extends BaseEntityBlock {

    public static final MapCodec<BackgroundPanelBlock> CODEC = simpleCodec(p -> new BackgroundPanelBlock());

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    /** 当たり判定は足元の小さな台座だけ。背景の前を自由に歩けるようにする。 */
    private static final VoxelShape SHAPE = Shapes.box(0.3125D, 0.0D, 0.3125D, 0.6875D, 0.25D, 0.6875D);

    public BackgroundPanelBlock() {
        super(BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_GRAY)
            .strength(0.5F)
            .sound(SoundType.WOOD)
            .noOcclusion()
            .pushReaction(PushReaction.BLOCK));
        registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        //置いた人の方を向ける (絵がこちらを向く)
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BackgroundPanelBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        //台座も絵もブロックエンティティレンダラが描く
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            ClientHooks.openBackgroundPanelScreen(pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * ★中ボタン (ピックブロック) で<b>設定ごと</b>複製する。
     *
     * <p>バニラは中ボタンではブロックエンティティの中身を写さない
     * (Ctrl を押しながらのときだけ写す)。背景パネルは画像と大きさが本体なので、
     * 素の中ボタンでも写るように、ここで {@code BLOCK_ENTITY_DATA} を載せて返す。
     * 置き直すと {@code BlockItem} が同じ中身を戻すので、そのまま複製になる。
     */
    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        ItemStack stack = new ItemStack(RealTrainModUnofficialItems.BACKGROUND_PANEL_ITEM.get());
        if (level instanceof Level lv && lv.getBlockEntity(pos) instanceof BackgroundPanelBlockEntity be) {
            be.saveToItem(stack, lv.registryAccess());
        }
        return stack;
    }

    /** 壊しても設定を失わないよう、中身ごとアイテムに戻す。 */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && !player.isCreative()
                && level.getBlockEntity(pos) instanceof BackgroundPanelBlockEntity be) {
            ItemStack drop = new ItemStack(RealTrainModUnofficialItems.BACKGROUND_PANEL_ITEM.get());
            be.saveToItem(drop, level.registryAccess());
            Block.popResource(level, pos, drop);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}
