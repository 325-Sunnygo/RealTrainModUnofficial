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
 * 運転士のスキン変更 (GUI で選択 → サーバーで entityData に設定 → 全クライアントへ同期)。
 */
public record MotormanSkinPayload(int entityId, String skin) implements CustomPacketPayload {

    public static final Type<MotormanSkinPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(RealTrainModUnofficial.MODID, "motorman_skin")
    );

    public static final StreamCodec<ByteBuf, MotormanSkinPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, MotormanSkinPayload::entityId,
        ByteBufCodecs.STRING_UTF8, MotormanSkinPayload::skin,
        MotormanSkinPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnServer(MotormanSkinPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (!(player.level().getEntity(payload.entityId()) instanceof EntityMotorman motorman)) {
                return;
            }
            if (motorman.distanceToSqr(player) > 64.0D * 64.0D) {
                return;
            }
            //ファイル名として安全な文字だけ許可 (パス区切りを含む値は拒否)
            String skin = payload.skin();
            if (skin.contains("/") || skin.contains("\\") || skin.contains("..")) {
                return;
            }
            motorman.setSkin(skin);
        });
    }
}
