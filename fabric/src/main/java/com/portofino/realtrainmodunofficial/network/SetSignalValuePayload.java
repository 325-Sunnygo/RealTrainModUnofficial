package com.portofino.realtrainmodunofficial.network;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import com.portofino.realtrainmodunofficial.blockentity.SignalRemoteBlockEntity;
import com.portofino.realtrainmodunofficial.signal.SignalAspect;
import com.portofino.realtrainmodunofficial.signal.SignalNetworkSavedData;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SetSignalValuePayload(BlockPos pos, int signalValue) implements CustomPacketPayload {
    public static final Type<SetSignalValuePayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(RealTrainModUnofficial.MODID, "set_signal_value")
    );

    public static final StreamCodec<ByteBuf, SetSignalValuePayload> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC,
        SetSignalValuePayload::pos,
        ByteBufCodecs.INT,
        SetSignalValuePayload::signalValue,
        SetSignalValuePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnServer(SetSignalValuePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (!(player.level().getBlockEntity(payload.pos()) instanceof SignalRemoteBlockEntity blockEntity)) {
                return;
            }
            int linkedChannel = blockEntity.getLinkedChannel();
            if (linkedChannel <= 0) {
                player.displayClientMessage(Component.literal("先に信号番号を入力してください"), true);
                return;
            }
            SignalNetworkSavedData data = SignalNetworkSavedData.get(player.serverLevel());
            if (!data.hasChannel(linkedChannel)) {
                player.displayClientMessage(Component.literal("信号番号が無効になっています"), true);
                return;
            }
            SignalAspect aspect = SignalAspect.byLegacyValue(payload.signalValue());
            data.setAspect(player.serverLevel().getServer(), linkedChannel, aspect);
            player.displayClientMessage(Component.literal("signal値 " + payload.signalValue() + " を送信しました"), true);
        });
    }
}
