package com.portofino.realtrainmodunofficial.network;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import com.portofino.realtrainmodunofficial.RealTrainModUnofficialComponents;
import com.portofino.realtrainmodunofficial.item.RailItem;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Sends a small rail preview endpoint offset from the client to the server.
 */
public record RailPreviewAdjustPayload(int dx, int dy, int dz) implements CustomPacketPayload {
    /**
     * Payload type used by the network channel.
     */
    public static final Type<RailPreviewAdjustPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(RealTrainModUnofficial.MODID, "rail_preview_adjust")
    );

    /**
     * Codec used to serialize preview adjustment packets.
     */
    public static final StreamCodec<ByteBuf, RailPreviewAdjustPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.INT,
        RailPreviewAdjustPayload::dx,
        ByteBufCodecs.INT,
        RailPreviewAdjustPayload::dy,
        ByteBufCodecs.INT,
        RailPreviewAdjustPayload::dz,
        RailPreviewAdjustPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Applies the preview adjustment to the rail item held by the sending player.
     */
    public static void handleOnServer(RailPreviewAdjustPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (player == null) {
                return;
            }
            for (InteractionHand hand : InteractionHand.values()) {
                ItemStack stack = player.getItemInHand(hand);
                if (stack.getItem() instanceof RailItem && apply(stack, payload.dx(), payload.dy(), payload.dz())) {
                    return;
                }
            }
        });
    }

    /**
     * Applies the preview adjustment to an item stack.
     */
    public static boolean apply(ItemStack stack, int dx, int dy, int dz) {
        CompoundTag tag = stack.get(RealTrainModUnofficialComponents.RAIL_PREVIEW_START.get());
        if (tag == null || !tag.contains("X") || !tag.contains("Y") || !tag.contains("Z")) {
            return false;
        }
        CompoundTag copy = tag.copy();
        copy.putInt("OffsetX", clampOffset(copy.getInt("OffsetX") + dx));
        copy.putInt("OffsetY", clampOffset(copy.getInt("OffsetY") + dy));
        copy.putInt("OffsetZ", clampOffset(copy.getInt("OffsetZ") + dz));
        stack.set(RealTrainModUnofficialComponents.RAIL_PREVIEW_START.get(), copy);
        return true;
    }

    private static int clampOffset(int value) {
        return Math.max(-64, Math.min(64, value));
    }
}
