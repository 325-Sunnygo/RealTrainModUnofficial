package com.portofino.realtrainmodunofficial.network;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import jp.ngt.rtm.entity.train.parts.EntityCargoWithModel;
import jp.ngt.rtm.item.ItemCargo;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 貨物のモデル選択をサーバーへ送る。
 *
 * @param entityId 設置済み貨物のエンティティ id。アイテム側なら -1
 * @param offHand  アイテム側のときだけ意味がある
 */
public record CargoModelPayload(int entityId, boolean offHand, String modelId) implements CustomPacketPayload {

    public static final Type<CargoModelPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(RealTrainModUnofficial.MODID, "cargo_model"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CargoModelPayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CargoModelPayload::entityId,
            ByteBufCodecs.BOOL, CargoModelPayload::offHand,
            ByteBufCodecs.stringUtf8(256), CargoModelPayload::modelId,
            CargoModelPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnServer(CargoModelPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (payload.entityId() >= 0) {
                Entity entity = player.level().getEntity(payload.entityId());
                if (player.distanceToSqr(entity == null ? player : entity) > 64.0D * 64.0D) {
                    return;
                }
                if (entity instanceof EntityCargoWithModel cargo) {
                    cargo.setModelId(payload.modelId());
                    cargo.writeCargoToItem();
                } else if (entity instanceof jp.ngt.rtm.entity.npc.EntityNPC npc) {
                    npc.setModelId(payload.modelId());
                }
                return;
            }
            InteractionHand hand = payload.offHand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof ItemCargo) {
                ItemCargo.setModelId(stack, payload.modelId());
            } else if (stack.getItem() instanceof com.portofino.realtrainmodunofficial.item.NpcItem) {
                com.portofino.realtrainmodunofficial.item.NpcItem.setModelId(stack, payload.modelId());
            }
        });
    }
}
