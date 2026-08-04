package com.portofino.realtrainmodunofficial.network;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import com.portofino.realtrainmodunofficial.blockentity.BackgroundPanelBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 背景パネルの設定をサーバーへ送る。<b>画像そのものを載せる</b>。
 *
 * <p>置き場フォルダを作らず、選んだファイルをその場で取り込む形にしたため。
 * 画面側で {@link BackgroundPanelBlockEntity#MAX_IMAGE_BYTES} 以下に縮小してから送る。
 */
public record BackgroundPanelPayload(BlockPos pos, byte[] image, String name,
                                     float scale, float offsetY)
        implements CustomPacketPayload {

    public static final Type<BackgroundPanelPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(RealTrainModUnofficial.MODID, "background_panel"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BackgroundPanelPayload> STREAM_CODEC =
        StreamCodec.composite(
            BlockPos.STREAM_CODEC, BackgroundPanelPayload::pos,
            ByteBufCodecs.byteArray(BackgroundPanelBlockEntity.MAX_IMAGE_BYTES), BackgroundPanelPayload::image,
            ByteBufCodecs.STRING_UTF8, BackgroundPanelPayload::name,
            ByteBufCodecs.FLOAT, BackgroundPanelPayload::scale,
            ByteBufCodecs.FLOAT, BackgroundPanelPayload::offsetY,
            BackgroundPanelPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnServer(BackgroundPanelPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            //不正なパケット対策: 遠くのブロックは触らせない
            if (player.distanceToSqr(payload.pos().getX(), payload.pos().getY(), payload.pos().getZ())
                    > 64.0D * 64.0D) {
                return;
            }
            if (payload.image() != null && payload.image().length > BackgroundPanelBlockEntity.MAX_IMAGE_BYTES) {
                return;
            }
            if (player.level().getBlockEntity(payload.pos())
                    instanceof BackgroundPanelBlockEntity be) {
                be.apply(payload.image(), payload.name(), payload.scale(), payload.offsetY());
            }
        });
    }
}
