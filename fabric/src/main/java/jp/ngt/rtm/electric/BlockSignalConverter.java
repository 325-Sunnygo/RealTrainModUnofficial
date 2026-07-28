package jp.ngt.rtm.electric;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 本家 jp.ngt.rtm.electric.BlockSignalConverter の移植。
 * 右クリックでタイプ切替 (RSIn/RSOut/Increment/Decrement/Wireless)。
 * ワイヤーで配線網に接続して使用する。
 */
public class BlockSignalConverter extends BaseEntityBlock {
    public static final MapCodec<BlockSignalConverter> CODEC = simpleCodec(p -> new BlockSignalConverter(p));
    public static final IntegerProperty TYPE = IntegerProperty.create("type", 0, 4);

    public BlockSignalConverter(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(TYPE, 0));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TYPE);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileEntitySignalConverter(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return createTickerHelper(type,
                com.portofino.realtrainmodunofficial.RealTrainModUnofficialBlockEntities.SIGNAL_CONVERTER.get(),
                TileEntitySignalConverter::tick);
    }

    /**
     * 本家: 右クリックでタイプ切替
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            int next = (state.getValue(TYPE) + 1) % 5;
            level.setBlock(pos, state.setValue(TYPE, next), 3);
            SignalConverterType type = SignalConverterType.getType(next);
            player.displayClientMessage(Component.literal("信号変換器: " + typeName(type)), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static String typeName(SignalConverterType type) {
        return switch (type) {
            case RSIn -> "RS入力 (レッドストーン→配線)";
            case RSOut -> "RS出力 (配線→レッドストーン)";
            case Increment -> "インクリメント (+1)";
            case Decrement -> "デクリメント (-1)";
            case Wireless -> "無線 (未対応)";
        };
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return state.getValue(TYPE) == SignalConverterType.RSOut.id;
    }

    @Override
    protected int getSignal(BlockState state, net.minecraft.world.level.BlockGetter getter, BlockPos pos, net.minecraft.core.Direction direction) {
        if (state.getValue(TYPE) == SignalConverterType.RSOut.id
                && getter.getBlockEntity(pos) instanceof TileEntitySignalConverter converter) {
            return net.minecraft.util.Mth.clamp(converter.getElectricity(), 0, 15);
        }
        return 0;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        //RSIn の即時反応 (tick でも監視しているが応答性向上)
        if (!level.isClientSide && state.getValue(TYPE) == SignalConverterType.RSIn.id
                && level.getBlockEntity(pos) instanceof TileEntitySignalConverter converter) {
            TileEntitySignalConverter.tick(level, pos, state, converter);
        }
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
    }
}
