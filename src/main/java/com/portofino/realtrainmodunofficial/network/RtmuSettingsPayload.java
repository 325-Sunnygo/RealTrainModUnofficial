package com.portofino.realtrainmodunofficial.network;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import com.portofino.realtrainmodunofficial.RtmuSettings;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * クライアントの RTMU 設定 (自動カント / 自動高さ) をサーバーへ同期する。
 * レール生成はサーバー側で行うため、敷設プレイヤーの設定をサーバーが保持して参照する。
 */
public record RtmuSettingsPayload(boolean autoCant, int autoHeightLevel) implements CustomPacketPayload {

    public static final Type<RtmuSettingsPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(RealTrainModUnofficial.MODID, "rtmu_settings"));

    public static final StreamCodec<ByteBuf, RtmuSettingsPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.BOOL, RtmuSettingsPayload::autoCant,
        ByteBufCodecs.VAR_INT, RtmuSettingsPayload::autoHeightLevel,
        RtmuSettingsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnServer(RtmuSettingsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (player != null) {
                RtmuSettings.setServerValues(player.getUUID(), payload.autoCant(), payload.autoHeightLevel());
            }
        });
    }
}
