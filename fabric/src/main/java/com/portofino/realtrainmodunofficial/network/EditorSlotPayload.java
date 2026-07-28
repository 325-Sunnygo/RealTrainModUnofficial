package com.portofino.realtrainmodunofficial.network;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import jp.ngt.mcte.editor.EditorSelection;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * エディタのスロットへ手持ちのブロックを入れる (neo mcte)。
 *
 * <p>MCTEU の画面はスロットをコンテナではなく<b>自前描画</b>していて、クリックで
 * 手持ちのアイテムを写す方式。こちらもそれに合わせる。
 * アイテムは消費しない (見本として登録するだけ)。
 */
public record EditorSlotPayload(int slot, boolean clear) implements CustomPacketPayload {

    public static final Type<EditorSlotPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(RealTrainModUnofficial.MODID, "editor_slot")
    );

    public static final StreamCodec<ByteBuf, EditorSlotPayload> STREAM_CODEC =
        StreamCodec.of((buf, p) -> {
            FriendlyByteBuf b = new FriendlyByteBuf(buf);
            b.writeVarInt(p.slot());
            b.writeBoolean(p.clear());
        }, buf -> {
            FriendlyByteBuf b = new FriendlyByteBuf(buf);
            return new EditorSlotPayload(b.readVarInt(), b.readBoolean());
        });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnServer(EditorSlotPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            if (player == null) {
                return;
            }
            EditorSelection editor = EditorSelection.of(player);
            int slot = payload.slot();
            if (slot != EditorSelection.SLOT_FILL && slot != EditorSelection.SLOT_REPLACE) {
                return;
            }
            if (payload.clear()) {
                editor.setItem(slot, ItemStack.EMPTY);
                return;
            }
            //手持ち (メイン優先、無ければオフハンド) のブロックを 1 個だけ写す。消費しない。
            ItemStack src = player.getMainHandItem();
            if (!(src.getItem() instanceof net.minecraft.world.item.BlockItem)) {
                src = player.getOffhandItem();
            }
            if (src.getItem() instanceof net.minecraft.world.item.BlockItem) {
                ItemStack copy = src.copy();
                copy.setCount(1);
                editor.setItem(slot, copy);
            }
        });
    }
}
