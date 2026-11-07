package jp.masa.signalcontrollermod;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

/**
 * SignalControllerMod (作者: masa300, https://github.com/masa300/SignalControllerMod)
 * の SignalController ブロック 1.21.1 移植。右クリックで設定 GUI。
 */
public class SignalController extends BaseEntityBlock {
    public static final MapCodec<SignalController> CODEC = simpleCodec(props -> new SignalController(props));

    public SignalController(Properties props) {
        super(props.strength(0.5F).sound(SoundType.STONE));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileEntitySignalController(
                com.portofino.realtrainmodunofficial.RealTrainModUnofficialBlockEntities.SIGNAL_CONTROLLER.get(), pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit) {
        if (world.isClientSide && world.getBlockEntity(pos) instanceof TileEntitySignalController controller) {
            com.portofino.realtrainmodunofficial.ClientHooks.openSignalControllerScreen(controller);
        }
        return InteractionResult.sidedSuccess(world.isClientSide);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return (lvl, pos, st, be) -> {
            if (be instanceof TileEntitySignalController controller) {
                controller.tick();
            }
        };
    }
}
