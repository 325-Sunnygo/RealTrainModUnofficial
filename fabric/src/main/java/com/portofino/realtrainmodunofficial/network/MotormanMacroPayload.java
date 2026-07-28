package com.portofino.realtrainmodunofficial.network;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import io.netty.buffer.ByteBuf;
import jp.ngt.rtm.entity.npc.EntityMotorman;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 運転士のマクロ設定 (クライアントの GUI で選んだ .txt の中身をサーバーへ送る)。
 * 本家 PacketNotice の "TMacro//..." 相当。
 */
public record MotormanMacroPayload(int entityId, String macro) implements CustomPacketPayload {

    public static final Type<MotormanMacroPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(RealTrainModUnofficial.MODID, "motorman_macro")
    );

    public static final StreamCodec<ByteBuf, MotormanMacroPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, MotormanMacroPayload::entityId,
        ByteBufCodecs.STRING_UTF8, MotormanMacroPayload::macro,
        MotormanMacroPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnServer(MotormanMacroPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (!(player.level().getEntity(payload.entityId()) instanceof EntityMotorman motorman)) {
                return;
            }
            //遠隔操作防止: 近くの運転士のみ
            if (motorman.distanceToSqr(player) > 64.0D * 64.0D) {
                return;
            }
            motorman.setMacro(payload.macro().split("\n"));
        });
    }
}
