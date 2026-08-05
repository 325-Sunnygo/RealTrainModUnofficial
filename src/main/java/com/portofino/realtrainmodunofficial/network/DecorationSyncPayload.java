package com.portofino.realtrainmodunofficial.network;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import jp.ngt.rtm.block.decoration.DecorationStore;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 装飾ブロックのモデル配布 (S2C)。本家 PacketNotice "decoration:json" の移植。
 */
public record DecorationSyncPayload(String json) implements CustomPacketPayload {

    public static final Type<DecorationSyncPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RealTrainModUnofficial.MODID, "decoration_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DecorationSyncPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> buf.writeUtf(p.json(), 262144),
            buf -> new DecorationSyncPayload(buf.readUtf(262144)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnClient(DecorationSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> DecorationStore.INSTANCE.setModel(payload.json()));
    }
}
