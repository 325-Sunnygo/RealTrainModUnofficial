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
 * 駅ブロックを右クリックしたとき、サーバーがその駅の現在のタグビットと出る人数を添えて
 * クライアントへ送り、駅設定 GUI を開かせる。
 */
public record OpenStationScreenPayload(BlockPos pos, int bits, int capacity) implements CustomPacketPayload {

    public static final Type<OpenStationScreenPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(RealTrainModUnofficial.MODID, "open_station_screen"));

    public static final StreamCodec<ByteBuf, OpenStationScreenPayload> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, OpenStationScreenPayload::pos,
        ByteBufCodecs.VAR_INT, OpenStationScreenPayload::bits,
        ByteBufCodecs.VAR_INT, OpenStationScreenPayload::capacity,
        OpenStationScreenPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnClient(OpenStationScreenPayload payload, IPayloadContext context) {
        // ★クライアント専用クラス (Screen) をこのクラスから直接参照すると、専用サーバーでの
        // playToClient 登録時に Screen のロードが試みられてクラッシュする。ClientHooks (リフレクションで
        // クライアント側だけロード) を経由して隔離する。
        context.enqueueWork(() ->
            com.portofino.realtrainmodunofficial.ClientHooks.openStationScreen(
                payload.pos(), payload.bits(), payload.capacity()));
    }
}
