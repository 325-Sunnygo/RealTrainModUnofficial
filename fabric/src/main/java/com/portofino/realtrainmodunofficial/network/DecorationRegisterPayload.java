package com.portofino.realtrainmodunofficial.network;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import jp.ngt.rtm.block.decoration.DecorationModel;
import jp.ngt.rtm.block.decoration.DecorationStore;
import jp.ngt.rtm.item.ItemDecoration;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 装飾ブロックのモデル登録 (C2S)。本家 PacketNotice "decoration:json" の移植。
 * サーバーが保存して全クライアントへ {@link DecorationSyncPayload} で配る。
 * 本家 GuiDecorationBlock.saveModel は同時に手持ちアイテムへモデル名を書くので、
 * ここでもメインハンドの装飾ブロックにモデル名を書き戻す。
 */
public record DecorationRegisterPayload(String json) implements CustomPacketPayload {

    public static final Type<DecorationRegisterPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RealTrainModUnofficial.MODID, "decoration_register"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DecorationRegisterPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> buf.writeUtf(p.json(), 262144),
            buf -> new DecorationRegisterPayload(buf.readUtf(262144)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnServer(DecorationRegisterPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            DecorationStore.INSTANCE.registerModel(payload.json(), player.server);
            try {
                DecorationModel model = DecorationModel.fromJson(payload.json());
                ItemStack held = player.getMainHandItem();
                if (model != null && held.getItem() instanceof ItemDecoration) {
                    ItemDecoration.setModel(held, model.name);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}
