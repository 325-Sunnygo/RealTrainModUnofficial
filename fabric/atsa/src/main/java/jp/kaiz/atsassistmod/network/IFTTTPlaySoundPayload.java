package jp.kaiz.atsassistmod.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * IFTTT 音声再生 (サーバー → 全クライアント)。本家 PacketPlaySoundIFTTT の移植。
 * finish=true で該当 IFTTT ブロックの音を停止。repeat=true でループ再生。
 */
public record IFTTTPlaySoundPayload(BlockPos tilePos, boolean finish,
                                    int x, int y, int z,
                                    String sound, boolean repeat, int radius) implements CustomPacketPayload {

    public static final Type<IFTTTPlaySoundPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("atsassistmod", "ifttt_play_sound")
    );

    public static final StreamCodec<ByteBuf, IFTTTPlaySoundPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public IFTTTPlaySoundPayload decode(ByteBuf buf) {
            BlockPos tilePos = BlockPos.STREAM_CODEC.cast().decode(buf);
            boolean finish = buf.readBoolean();
            int x = buf.readInt();
            int y = buf.readInt();
            int z = buf.readInt();
            String sound = ByteBufCodecs.STRING_UTF8.decode(buf);
            boolean repeat = buf.readBoolean();
            int radius = buf.readInt();
            return new IFTTTPlaySoundPayload(tilePos, finish, x, y, z, sound, repeat, radius);
        }

        @Override
        public void encode(ByteBuf buf, IFTTTPlaySoundPayload payload) {
            BlockPos.STREAM_CODEC.cast().encode(buf, payload.tilePos());
            buf.writeBoolean(payload.finish());
            buf.writeInt(payload.x());
            buf.writeInt(payload.y());
            buf.writeInt(payload.z());
            ByteBufCodecs.STRING_UTF8.encode(buf, payload.sound());
            buf.writeBoolean(payload.repeat());
            buf.writeInt(payload.radius());
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnClient(IFTTTPlaySoundPayload payload, IPayloadContext context) {
        context.enqueueWork(() ->
                jp.kaiz.atsassistmod.client.IFTTTClientSounds.handle(payload));
    }
}
