package com.portofino.realtrainmodunofficial.network;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import com.portofino.realtrainmodunofficial.blockentity.MarkerBlockEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** レンチ設定GUI → サーバ: マーカーのアンカー(曲線)・カント(傾き)を更新する。 */
public record ConfigureMarkerPayload(BlockPos pos, float anchorYaw, float anchorPitch,
                                     float anchorLengthHorizontal, float anchorLengthVertical,
                                     float cantCenter, float cantEdge, float cantRandom) implements CustomPacketPayload {
    public static final Type<ConfigureMarkerPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(RealTrainModUnofficial.MODID, "configure_marker")
    );

    // composite は最大6要素のため手動コーデックにする (フィールドが8個)。
    public static final StreamCodec<ByteBuf, ConfigureMarkerPayload> STREAM_CODEC = StreamCodec.of(
        (buf, p) -> {
            BlockPos.STREAM_CODEC.encode(buf, p.pos);
            buf.writeFloat(p.anchorYaw);
            buf.writeFloat(p.anchorPitch);
            buf.writeFloat(p.anchorLengthHorizontal);
            buf.writeFloat(p.anchorLengthVertical);
            buf.writeFloat(p.cantCenter);
            buf.writeFloat(p.cantEdge);
            buf.writeFloat(p.cantRandom);
        },
        buf -> new ConfigureMarkerPayload(
            BlockPos.STREAM_CODEC.decode(buf),
            buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
            buf.readFloat(), buf.readFloat(), buf.readFloat()
        )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnServer(ConfigureMarkerPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (!(player.level().getBlockEntity(payload.pos()) instanceof MarkerBlockEntity marker)) {
                return;
            }
            marker.configure(payload.anchorYaw(), payload.anchorPitch(),
                payload.anchorLengthHorizontal(), payload.anchorLengthVertical(),
                payload.cantCenter(), payload.cantEdge(), payload.cantRandom());
            player.displayClientMessage(Component.literal("マーカー設定を更新しました"), true);
        });
    }
}
