package com.portofino.realtrainmodunofficial.item;

import com.portofino.realtrainmodunofficial.blockentity.InstalledObjectBlockEntity;
import com.portofino.realtrainmodunofficial.signal.SignalAspect;
import com.portofino.realtrainmodunofficial.signal.SignalNetworkSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

/**
 * 信号へ番号を振り直すための通信機です。
 * 再度右クリックすると新番号を採番し直すので、旧リンクは自然に無効になります。
 */
public class SignalCommunicatorItem extends Item {
    public SignalCommunicatorItem() {
        super(new Properties().stacksTo(1));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level) || context.getPlayer() == null) {
            return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
        }
        BlockPos pos = context.getClickedPos();
        if (!(level.getBlockEntity(pos) instanceof InstalledObjectBlockEntity blockEntity) || !blockEntity.isSignal()) {
            return InteractionResult.PASS;
        }
        SignalNetworkSavedData data = SignalNetworkSavedData.get(level);
        int newChannel = data.assignNewChannel(level, pos, blockEntity.getSignalChannel(), blockEntity.getSignalAspect());
        blockEntity.setSignalChannel(newChannel, false);
        // 番号を振り直した瞬間から赤に戻しておくと、未確認のまま進行現示に残りません。
        blockEntity.setSignalAspect(SignalAspect.STOP, true);
        context.getPlayer().displayClientMessage(Component.literal("信号番号: " + newChannel), false);
        return InteractionResult.SUCCESS;
    }
}
