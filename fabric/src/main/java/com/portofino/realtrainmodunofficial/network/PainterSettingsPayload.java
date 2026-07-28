package com.portofino.realtrainmodunofficial.network;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import com.portofino.realtrainmodunofficial.item.PainterItem;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** ペインターの設定変更 (neo mcte)。手に持っているスタックだけに効く。 */
public record PainterSettingsPayload(boolean offHand, String block, int size, String shape, boolean onlySolid)
        implements CustomPacketPayload {

    public static final Type<PainterSettingsPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(RealTrainModUnofficial.MODID, "painter_settings")
    );

    public static final StreamCodec<ByteBuf, PainterSettingsPayload> STREAM_CODEC =
        StreamCodec.of((buf, p) -> {
            FriendlyByteBuf b = new FriendlyByteBuf(buf);
            b.writeBoolean(p.offHand());
            b.writeUtf(p.block() == null ? "" : p.block(), 128);
            b.writeVarInt(p.size());
            b.writeUtf(p.shape() == null ? "" : p.shape(), 16);
            b.writeBoolean(p.onlySolid());
        }, buf -> {
            FriendlyByteBuf b = new FriendlyByteBuf(buf);
            return new PainterSettingsPayload(b.readBoolean(), b.readUtf(128), b.readVarInt(),
                b.readUtf(16), b.readBoolean());
        });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnServer(PainterSettingsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            if (player == null) {
                return;
            }
            ItemStack stack = player.getItemInHand(payload.offHand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
            if (stack.isEmpty() || !(stack.getItem() instanceof PainterItem)) {
                return;
            }
            CompoundTag tag = PainterItem.getTag(stack);
            tag.putString(PainterItem.KEY_BLOCK, payload.block() == null ? "" : payload.block().trim());
            tag.putInt(PainterItem.KEY_SIZE, Math.max(1, Math.min(16, payload.size())));
            tag.putString(PainterItem.KEY_SHAPE,
                PainterItem.SHAPE_CUBE.equals(payload.shape()) ? PainterItem.SHAPE_CUBE : PainterItem.SHAPE_SPHERE);
            tag.putBoolean(PainterItem.KEY_ONLY_SOLID, payload.onlySolid());
            PainterItem.setTag(stack, tag);
        });
    }
}
