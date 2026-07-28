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
 * スピーカーブロックが壊された等で、指定座標で再生中の音を止めるパケット。
 * クライアントは {@link LegacyScriptSoundManager#stopAt} でその位置の音を停止する
 * (長い音がブロック破壊後も鳴り続ける問題の対策)。
 */
public record SpeakerStopPayload(double x, double y, double z) implements CustomPacketPayload {

    public static final Type<SpeakerStopPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(RealTrainModUnofficial.MODID, "speaker_stop")
    );

    public static final StreamCodec<ByteBuf, SpeakerStopPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.DOUBLE, SpeakerStopPayload::x,
        ByteBufCodecs.DOUBLE, SpeakerStopPayload::y,
        ByteBufCodecs.DOUBLE, SpeakerStopPayload::z,
        SpeakerStopPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnClient(SpeakerStopPayload payload, IPayloadContext context) {
        context.enqueueWork(() ->
            LegacyScriptSoundManager.stopAt(payload.x(), payload.y(), payload.z())
        );
    }
}
