package com.portofino.realtrainmodunofficial.network;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import com.portofino.realtrainmodunofficial.RealTrainModUnofficialItems;
import com.portofino.realtrainmodunofficial.blockentity.InstalledObjectBlockEntity;
import com.portofino.realtrainmodunofficial.installedobject.InstalledObjectCategory;
import com.portofino.realtrainmodunofficial.item.TicketItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 本家 GuiTicketVendor / VendorScreenSelectTicket の「切符」「回数券」ボタン。
 * 本家は PacketNotice("vendor:ticket" / "vendor:ticketbook") を投げていた。
 */
public record BuyTicketPayload(BlockPos pos, boolean book) implements CustomPacketPayload {

    public static final Type<BuyTicketPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RealTrainModUnofficial.MODID, "buy_ticket"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BuyTicketPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeBlockPos(p.pos());
                buf.writeBoolean(p.book());
            },
            buf -> new BuyTicketPayload(buf.readBlockPos(), buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnServer(BuyTicketPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            //券売機の前にいることを確かめる (GUI を開かずにパケットだけ送られるのを防ぐ)。
            if (!player.level().isLoaded(payload.pos())
                    || player.distanceToSqr(payload.pos().getCenter()) > 64.0D) {
                return;
            }
            if (!(player.level().getBlockEntity(payload.pos()) instanceof InstalledObjectBlockEntity be)
                    || be.getCategory() != InstalledObjectCategory.TICKET_VENDOR) {
                return;
            }
            TicketItem item = (payload.book()
                    ? RealTrainModUnofficialItems.TICKET_BOOK_ITEM
                    : RealTrainModUnofficialItems.TICKET_ITEM).get();
            ItemStack ticket = item.createIssued();
            if (!player.getInventory().add(ticket)) {
                player.drop(ticket, false);
            }
        });
    }
}
