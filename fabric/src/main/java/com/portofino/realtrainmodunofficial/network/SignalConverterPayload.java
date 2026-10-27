package com.portofino.realtrainmodunofficial.network;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import jp.ngt.rtm.electric.TileEntitySignalConverter;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** 信号変換機の設定 (本家 PacketSignalConverter 相当)。 */
public record SignalConverterPayload(BlockPos pos, int onTrue, int onFalse, int comparator)
        implements CustomPacketPayload {

    public static final Type<SignalConverterPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(RealTrainModUnofficial.MODID, "signal_converter"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SignalConverterPayload> STREAM_CODEC =
        StreamCodec.of((buf, v) -> {
            BlockPos.STREAM_CODEC.encode(buf, v.pos());
            buf.writeVarInt(v.onTrue());
            buf.writeVarInt(v.onFalse());
            buf.writeVarInt(v.comparator());
        }, buf -> new SignalConverterPayload(
            BlockPos.STREAM_CODEC.decode(buf), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnServer(SignalConverterPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (player.distanceToSqr(payload.pos().getCenter()) > 64.0D * 64.0D) {
                return;
            }
            if (player.level().getBlockEntity(payload.pos()) instanceof TileEntitySignalConverter converter) {
                converter.setSignalProp(payload.onTrue(), payload.onFalse(), payload.comparator());
            }
        });
    }
}
