package com.portofino.realtrainmodunofficial.network;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import com.portofino.realtrainmodunofficial.formation.TrainFormation;
import com.portofino.realtrainmodunofficial.formation.TrainFormationData;
import com.portofino.realtrainmodunofficial.item.FormationItem;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/**
 * 編成の編集画面で「保存」を押したとき、手に持っている編成アイテムへ編成を書き込む。
 * 画面はクライアントにしか無いので、持ち物への保存はサーバーでやる必要がある。
 */
public record SetFormationPayload(List<String> vehicleIds) implements CustomPacketPayload {

    /** 1 編成の最大両数。受け取り側でも切る (壊れたパケットで暴れないように)。 */
    private static final int MAX_CARS = 30;

    public static final Type<SetFormationPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(RealTrainModUnofficial.MODID, "set_formation"));

    public static final StreamCodec<ByteBuf, SetFormationPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(MAX_CARS)),
        SetFormationPayload::vehicleIds,
        SetFormationPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnServer(SetFormationPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (player == null) {
                return;
            }
            ItemStack stack = findFormationItem(player);
            if (stack == null) {
                return;
            }
            TrainFormation formation = new TrainFormation();
            List<String> ids = payload.vehicleIds();
            if (ids != null) {
                for (String id : ids) {
                    if (id != null && !id.isBlank()) {
                        formation.addVehicle(id);
                    }
                }
            }
            TrainFormationData.setFormation(stack, formation);
        });
    }

    /** 手に持っている編成アイテム。両手を見る。 */
    private static ItemStack findFormationItem(Player player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (!stack.isEmpty() && stack.getItem() instanceof FormationItem) {
                return stack;
            }
        }
        return null;
    }
}
