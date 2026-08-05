package com.portofino.realtrainmodunofficial.network;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import jp.ngt.rtm.block.tileentity.TileEntityStation;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** 駅コアの駅名をサーバーへ送る。本家 {@code PacketNBT.sendToServer(tileEntity)} 相当。 */
public record StationNamePayload(BlockPos pos, String name) implements CustomPacketPayload {

    public static final Type<StationNamePayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(RealTrainModUnofficial.MODID, "station_name"));

    public static final StreamCodec<RegistryFriendlyByteBuf, StationNamePayload> STREAM_CODEC =
        StreamCodec.composite(
            BlockPos.STREAM_CODEC, StationNamePayload::pos,
            ByteBufCodecs.stringUtf8(256), StationNamePayload::name,
            StationNamePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnServer(StationNamePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (!player.level().isLoaded(payload.pos())
                || player.distanceToSqr(payload.pos().getCenter()) > 64.0D * 64.0D) {
                return;
            }
            if (player.level().getBlockEntity(payload.pos()) instanceof TileEntityStation station) {
                station.setStationName(payload.name());
            }
        });
    }
}
