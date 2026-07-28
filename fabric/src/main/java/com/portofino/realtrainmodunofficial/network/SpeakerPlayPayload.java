package com.portofino.realtrainmodunofficial.network;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import com.portofino.realtrainmodunofficial.client.sound.LegacyScriptSoundManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * スピーカーがレッドストーン信号を受けて鳴るとき、サーバーが範囲内プレイヤーへ送る再生パケット。
 * クライアントは指定座標で {@code soundId} を再生する。
 * volume は可聴範囲(ブロック)から算出済みの値（MC の LINEAR 減衰は概ね volume×16 ブロック）。
 */
public record SpeakerPlayPayload(double x, double y, double z, String soundId, float volume, float pitch)
        implements CustomPacketPayload {

    public static final Type<SpeakerPlayPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(RealTrainModUnofficial.MODID, "speaker_play")
    );

    public static final StreamCodec<ByteBuf, SpeakerPlayPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.DOUBLE,
        SpeakerPlayPayload::x,
        ByteBufCodecs.DOUBLE,
        SpeakerPlayPayload::y,
        ByteBufCodecs.DOUBLE,
        SpeakerPlayPayload::z,
        ByteBufCodecs.STRING_UTF8,
        SpeakerPlayPayload::soundId,
        ByteBufCodecs.FLOAT,
        SpeakerPlayPayload::volume,
        ByteBufCodecs.FLOAT,
        SpeakerPlayPayload::pitch,
        SpeakerPlayPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnClient(SpeakerPlayPayload payload, IPayloadContext context) {
        context.enqueueWork(() ->
            LegacyScriptSoundManager.playAt(
                payload.x(), payload.y(), payload.z(),
                payload.soundId(), payload.volume(), payload.pitch()
            )
        );
    }
}
