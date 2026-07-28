package com.portofino.realtrainmodunofficial.network;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import com.portofino.realtrainmodunofficial.item.MiniatureItem;
import io.netty.buffer.ByteBuf;
import jp.ngt.mcte.item.ItemMiniature;
import jp.ngt.ngtlib.block.NGTObject;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 保存済み NGTO をミニチュアへ読み込む (neo mcte)。
 * ファイル名だけを送り、実体はサーバ側のゲームフォルダから読む。
 */
public record MiniatureLoadPayload(boolean offHand, String fileName) implements CustomPacketPayload {

    public static final Type<MiniatureLoadPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(RealTrainModUnofficial.MODID, "miniature_load")
    );

    public static final StreamCodec<ByteBuf, MiniatureLoadPayload> STREAM_CODEC =
        StreamCodec.of((buf, p) -> {
            buf.writeBoolean(p.offHand());
            new FriendlyByteBuf(buf).writeUtf(p.fileName(), 255);
        }, buf -> new MiniatureLoadPayload(
            buf.readBoolean(),
            new FriendlyByteBuf(buf).readUtf(255)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnServer(MiniatureLoadPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            if (player == null) {
                return;
            }
            ItemStack stack = player.getItemInHand(payload.offHand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
            if (stack.isEmpty() || !(stack.getItem() instanceof MiniatureItem)) {
                return;
            }
            // ★ファイル名にパス区切りを許さない (任意のファイルを読ませない)
            String name = payload.fileName();
            if (name == null || name.isBlank() || name.contains("/") || name.contains("\\") || name.contains("..")) {
                return;
            }
            java.io.File file = MiniatureFiles.dir().resolve(name).toFile();
            if (!file.isFile()) {
                player.displayClientMessage(Component.literal("ミニチュア: ファイルが見つかりません: " + name), true);
                return;
            }
            NGTObject obj;
            try {
                obj = NGTObject.importFromFile(file);
            } catch (Exception e) {
                player.displayClientMessage(Component.literal("ミニチュア: 読み込みに失敗しました: " + name), true);
                return;
            }
            if (obj == null) {
                return;
            }
            CustomData data = stack.get(DataComponents.CUSTOM_DATA);
            CompoundTag tag = data != null ? data.copyTag() : new CompoundTag();
            ItemMiniature.setNGTObject(obj, tag);
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            player.displayClientMessage(Component.literal("ミニチュア: " + name + " を読み込みました"), true);
        });
    }
}
