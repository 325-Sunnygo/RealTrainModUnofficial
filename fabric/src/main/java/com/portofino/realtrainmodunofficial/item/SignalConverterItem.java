package com.portofino.realtrainmodunofficial.item;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficialBlocks;
import jp.ngt.rtm.electric.BlockSignalConverter;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * 信号変換機のアイテム。本家はメタ 0〜3 の 4 種 (RS入力 / RS出力 / +1 / -1)。
 * ★右クリックでのタイプ切替は本家に無い。<b>アイテムの時点で種類が決まる</b>。
 */
public class SignalConverterItem extends BlockItem {
    private final int type;

    public SignalConverterItem(int type) {
        super(RealTrainModUnofficialBlocks.SIGNAL_CONVERTER.get(), new Properties());
        this.type = type;
    }

    @Nullable
    @Override
    protected BlockState getPlacementState(BlockPlaceContext context) {
        BlockState state = super.getPlacementState(context);
        return state == null ? null : state.setValue(BlockSignalConverter.TYPE, this.type);
    }
}
