package jp.kaiz.atsassistmod.block;

import com.mojang.serialization.MapCodec;
import jp.kaiz.atsassistmod.ATSAssistCore;
import jp.kaiz.atsassistmod.block.tileentity.GroundUnitBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 本家 jp.kaiz.atsassistmod.block.GroundUnit の移植 (地上子ブロック)。
 * unit_type (0-15) = 本家のメタデータ。右クリックで設定 GUI、種類ごとに見た目が変わる。
 */
public class GroundUnitBlock extends BaseEntityBlock {

    public static final IntegerProperty UNIT_TYPE = IntegerProperty.create("unit_type", 0, 15);
    public static final MapCodec<GroundUnitBlock> CODEC = simpleCodec(p -> new GroundUnitBlock(p));

    public GroundUnitBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(UNIT_TYPE, 0));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(UNIT_TYPE);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GroundUnitBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return createTickerHelper(type, ATSAssistCore.GROUND_UNIT_BE.get(), GroundUnitBlockEntity::serverTick);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        if (level.isClientSide()) {
            jp.kaiz.atsassistmod.client.AtsaClientHelper.openGroundUnitScreen(pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    //------------------------------------------------------------ レッドストーン出力 (TASC 停止位置)

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, net.minecraft.core.Direction direction) {
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof GroundUnitBlockEntity gu ? gu.getRedStoneOutput() : 0;
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, net.minecraft.core.Direction direction) {
        return this.getSignal(state, level, pos, direction);
    }
}
