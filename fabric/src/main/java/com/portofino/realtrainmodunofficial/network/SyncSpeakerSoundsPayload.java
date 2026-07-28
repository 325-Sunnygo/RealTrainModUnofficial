package com.portofino.realtrainmodunofficial.network;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import com.portofino.realtrainmodunofficial.installedobject.SpeakerSoundConfig;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/**
 * サーバー → クライアント。 スピーカーの音源ID→音名マッピング全体を同期する。
 * 接続時 / 設定変更時に送られ、クライアントは GUI のプレビュー表示などに使う。
 */
public record SyncSpeakerSoundsPayload(List<String> sounds) implements CustomPacketPayload {
    public static final Type<SyncSpeakerSoundsPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(RealTrainModUnofficial.MODID, "sync_speaker_sounds")
    );

    public static final StreamCodec<ByteBuf, SyncSpeakerSoundsPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()),
        SyncSpeakerSoundsPayload::sounds,
        SyncSpeakerSoundsPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnClient(SyncSpeakerSoundsPayload payload, IPayloadContext context) {
        context.enqueueWork(() ->
            SpeakerSoundConfig.replaceAll(payload.sounds().toArray(new String[0]))
        );
    }
}
