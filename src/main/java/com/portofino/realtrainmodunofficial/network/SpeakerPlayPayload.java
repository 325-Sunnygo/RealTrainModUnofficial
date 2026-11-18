package com.portofino.realtrainmodunofficial.network;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import com.portofino.realtrainmodunofficial.client.sound.LegacyScriptSoundManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** 本家 PacketPlaySound 相当: サーバー発の音をクライアントで鳴らす。 */
public record SpeakerPlayPayload(int entityId, double x, double y, double z, String soundId,
                                 float volume, float pitch, float range)
        implements CustomPacketPayload {

    public static final Type<SpeakerPlayPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(RealTrainModUnofficial.MODID, "speaker_play")
    );

    public static final StreamCodec<ByteBuf, SpeakerPlayPayload> STREAM_CODEC = StreamCodec.of(
        (buf, msg) -> {
            FriendlyByteBuf b = new FriendlyByteBuf(buf);
            b.writeInt(msg.entityId);
            b.writeDouble(msg.x);
            b.writeDouble(msg.y);
            b.writeDouble(msg.z);
            b.writeUtf(msg.soundId, 256);
            b.writeFloat(msg.volume);
            b.writeFloat(msg.pitch);
            b.writeFloat(msg.range);
        },
        buf -> {
            FriendlyByteBuf b = new FriendlyByteBuf(buf);
            return new SpeakerPlayPayload(b.readInt(), b.readDouble(), b.readDouble(), b.readDouble(),
                    b.readUtf(256), b.readFloat(), b.readFloat(), b.readFloat());
        });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnClient(SpeakerPlayPayload payload, IPayloadContext context) {
        context.enqueueWork(() ->
            LegacyScriptSoundManager.playOriginal(
                payload.entityId(), payload.x(), payload.y(), payload.z(),
                payload.soundId(), payload.volume(), payload.pitch(), payload.range()
            )
        );
    }
}
