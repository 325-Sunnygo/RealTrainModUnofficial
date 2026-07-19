package com.portofino.realtrainmodunofficial.network;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 駅ブロックを右クリックしたとき、サーバーがその駅の現在のタグビットを添えてクライアントへ送り、
 * 駅設定 GUI を開かせる。クライアントは受け取ったビットで {@code StationScreen} を開く。
 */
public record OpenStationScreenPayload(BlockPos pos, int bits) implements CustomPacketPayload {

    public static final Type<OpenStationScreenPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(RealTrainModUnofficial.MODID, "open_station_screen"));

    public static final StreamCodec<ByteBuf, OpenStationScreenPayload> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, OpenStationScreenPayload::pos,
        ByteBufCodecs.VAR_INT, OpenStationScreenPayload::bits,
        OpenStationScreenPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnClient(OpenStationScreenPayload payload, IPayloadContext context) {
        context.enqueueWork(() ->
            net.minecraft.client.Minecraft.getInstance().setScreen(
                new com.portofino.rtmupassenger.client.StationScreen(payload.pos(), payload.bits())));
    }
}
