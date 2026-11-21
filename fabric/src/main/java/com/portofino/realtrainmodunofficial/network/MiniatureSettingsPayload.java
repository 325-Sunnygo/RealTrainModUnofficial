package com.portofino.realtrainmodunofficial.network;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import com.portofino.realtrainmodunofficial.item.MiniatureItem;
import io.netty.buffer.ByteBuf;
import jp.ngt.mcte.item.ItemMiniature;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * ミニチュアの設定変更 (neo mcte)。クライアントの設定画面 → サーバ。
 * ★変更は手に持っているそのスタックにしか効かない。
 */
public record MiniatureSettingsPayload(
    boolean offHand,
    float scale,
    float offsetX,
    float offsetY,
    float offsetZ,
    int mode,
    int lightValue,
    String name
) implements CustomPacketPayload {

    public static final Type<MiniatureSettingsPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(RealTrainModUnofficial.MODID, "miniature_settings")
    );

    // composite は 6 要素までなので手書き。順序は encode/decode で必ず一致させること。
    public static final StreamCodec<ByteBuf, MiniatureSettingsPayload> STREAM_CODEC =
        StreamCodec.of((buf, p) -> {
            buf.writeBoolean(p.offHand());
            buf.writeFloat(p.scale());
            buf.writeFloat(p.offsetX());
            buf.writeFloat(p.offsetY());
            buf.writeFloat(p.offsetZ());
            buf.writeInt(p.mode());
            buf.writeInt(p.lightValue());
            new net.minecraft.network.FriendlyByteBuf(buf).writeUtf(p.name() == null ? "" : p.name(), 64);
        }, buf -> new MiniatureSettingsPayload(
            buf.readBoolean(),
            buf.readFloat(),
            buf.readFloat(),
            buf.readFloat(),
            buf.readFloat(),
            buf.readInt(),
            buf.readInt(),
            new net.minecraft.network.FriendlyByteBuf(buf).readUtf(64)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnServer(MiniatureSettingsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            if (player == null) {
                return;
            }
            ItemStack stack = player.getItemInHand(payload.offHand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
            if (stack.isEmpty() || !(stack.getItem() instanceof MiniatureItem)) {
                return;
            }
            CustomData data = stack.get(DataComponents.CUSTOM_DATA);
            CompoundTag tag = data != null ? data.copyTag() : new CompoundTag();

            // 値域を必ずサーバ側で締める (クライアントの値をそのまま信じない)
            float scale = clamp(payload.scale(), 0.001F, 16.0F);
            ItemMiniature.setScale(scale, tag);
            ItemMiniature.setOffset(tag,
                clamp(payload.offsetX(), -64.0F, 64.0F),
                clamp(payload.offsetY(), -64.0F, 64.0F),
                clamp(payload.offsetZ(), -64.0F, 64.0F));
            ItemMiniature.setMode(tag, ItemMiniature.MiniatureMode.byId(payload.mode()));
            ItemMiniature.setLightValue(tag, Math.max(0, Math.min(15, payload.lightValue())));

            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));

            // 本家 GuiItemMiniature は名前も編集できる
            String name = payload.name() == null ? "" : payload.name().trim();
            if (name.isEmpty()) {
                stack.remove(DataComponents.CUSTOM_NAME);
            } else {
                stack.set(DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal(name));
            }
        });
    }

    private static float clamp(float v, float min, float max) {
        if (Float.isNaN(v)) {
            return min;
        }
        return Math.max(min, Math.min(max, v));
    }
}
