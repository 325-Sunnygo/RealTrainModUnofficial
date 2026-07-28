package com.portofino.realtrainmodunofficial.network;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import com.portofino.rtmupassenger.station.StationRegistry;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 駅設定 GUI で選んだタグ (住宅街/工業地帯/…) をサーバーへ送り、駅のタグを更新する。
 * (旧: Shift+右クリックでの 1 個ずつのタグ切替を置き換え)
 */
public record SetStationTagsPayload(BlockPos pos, int bits) implements CustomPacketPayload {

    public static final Type<SetStationTagsPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(RealTrainModUnofficial.MODID, "set_station_tags"));

    public static final StreamCodec<ByteBuf, SetStationTagsPayload> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, SetStationTagsPayload::pos,
        ByteBufCodecs.VAR_INT, SetStationTagsPayload::bits,
        SetStationTagsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnServer(SetStationTagsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (player == null || !(player.level() instanceof ServerLevel sl)) {
                return;
            }
            // 操作距離チェック (別の駅を遠隔で書き換えられないように)。
            if (player.distanceToSqr(payload.pos().getX() + 0.5D, payload.pos().getY() + 0.5D,
                    payload.pos().getZ() + 0.5D) > 64.0D) {
                return;
            }
            StationRegistry.get(sl).setBits(payload.pos(), payload.bits());
        });
    }
}
