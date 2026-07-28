package com.portofino.realtrainmodunofficial.network;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import com.portofino.realtrainmodunofficial.blockentity.SignalRemoteBlockEntity;
import com.portofino.realtrainmodunofficial.blockentity.SignalStateBlockEntity;
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

public record BindSignalReceiverPayload(BlockPos pos, int channel) implements CustomPacketPayload {
    public static final Type<BindSignalReceiverPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(RealTrainModUnofficial.MODID, "bind_signal_receiver")
    );

    public static final StreamCodec<ByteBuf, BindSignalReceiverPayload> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC,
        BindSignalReceiverPayload::pos,
        ByteBufCodecs.INT,
        BindSignalReceiverPayload::channel,
        BindSignalReceiverPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnServer(BindSignalReceiverPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            SignalNetworkSavedData data = SignalNetworkSavedData.get(player.serverLevel());
            if (!data.hasChannel(payload.channel())) {
                player.displayClientMessage(Component.literal("その番号の信号は見つかりません"), true);
                return;
            }
            if (player.level().getBlockEntity(payload.pos()) instanceof SignalRemoteBlockEntity blockEntity) {
                blockEntity.setLinkedChannel(payload.channel());
                player.level().sendBlockUpdated(payload.pos(), blockEntity.getBlockState(), blockEntity.getBlockState(), 3);
            } else if (player.level().getBlockEntity(payload.pos()) instanceof SignalStateBlockEntity blockEntity) {
                blockEntity.setLinkedChannel(payload.channel());
                player.level().sendBlockUpdated(payload.pos(), blockEntity.getBlockState(), blockEntity.getBlockState(), 3);
            } else {
                return;
            }
            player.displayClientMessage(Component.literal("信号番号 " + payload.channel() + " を受信しました"), true);
        });
    }
}
