package jp.kaiz.atsassistmod.block;

import com.mojang.serialization.MapCodec;
import jp.kaiz.atsassistmod.ATSAssistCore;
import jp.kaiz.atsassistmod.block.tileentity.IFTTTBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 本家 jp.kaiz.atsassistmod.block.IFTTT の移植 (IFTTTブロック)。
 * 右クリックで設定 GUI。RS出力はコンパレータ経由 (本家仕様)。
 */
public class IFTTTBlock extends BaseEntityBlock {

    public static final MapCodec<IFTTTBlock> CODEC = simpleCodec(IFTTTBlock::new);

    public IFTTTBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new IFTTTBlockEntity(pos, state);
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
        return createTickerHelper(type, ATSAssistCore.IFTTT_BE.get(), IFTTTBlockEntity::serverTick);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        if (level.isClientSide()) {
            jp.kaiz.atsassistmod.client.AtsaClientHelper.openIFTTTScreen(pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    //------------------------------------------------------------ コンパレータ出力 (本家 getComparatorInputOverride)

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof IFTTTBlockEntity ifttt ? ifttt.getRedStoneOutput() : 0;
    }
}
