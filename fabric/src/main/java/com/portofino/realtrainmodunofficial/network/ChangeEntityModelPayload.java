package com.portofino.realtrainmodunofficial.network;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 設置済みエンティティのモデル差し替え (本家 GuiSelectModel → PacketSelectModel 相当)。
 * 本家は列車をシフト右クリックすると guiIdSelectEntityModel が開き、決定すると
 * そのエンティティの modelName / DataMap / 名前 / 色 が書き換わる。
 */
public record ChangeEntityModelPayload(int entityId, String modelId, String dataMapValue,
                                       String customName, int color)
        implements CustomPacketPayload {

    public static final Type<ChangeEntityModelPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(RealTrainModUnofficial.MODID, "change_entity_model")
    );

    public static final StreamCodec<ByteBuf, ChangeEntityModelPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT,
        ChangeEntityModelPayload::entityId,
        ByteBufCodecs.STRING_UTF8,
        ChangeEntityModelPayload::modelId,
        ByteBufCodecs.STRING_UTF8,
        ChangeEntityModelPayload::dataMapValue,
        ByteBufCodecs.STRING_UTF8,
        ChangeEntityModelPayload::customName,
        ByteBufCodecs.INT,
        ChangeEntityModelPayload::color,
        ChangeEntityModelPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnServer(ChangeEntityModelPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (player == null || payload.modelId() == null || payload.modelId().isBlank()) {
                return;
            }
            Entity entity = player.level().getEntity(payload.entityId());
            if (!(entity instanceof jp.ngt.rtm.entity.vehicle.EntityVehicleBase<?> vehicle)) {
                return;
            }
            // 本家と同じ範囲チェック (GUI を開いた相手から離れていないか)
            if (player.distanceToSqr(vehicle) > 64.0D * 64.0D) {
                return;
            }

            vehicle.setModelName(payload.modelId());

            jp.ngt.rtm.modelpack.state.ResourceState state = vehicle.getResourceState();
            if (state != null) {
                state.color = payload.color();
                if (payload.customName() != null && !payload.customName().isBlank()) {
                    state.setName(payload.customName());
                }
                if (payload.dataMapValue() != null && !payload.dataMapValue().isBlank()) {
                    state.setArg(payload.dataMapValue(), true);
                }
            }

            // モデルが変わると座席位置 (slotPos) も変わるので、床を作り直させる。
            vehicle.onModelChanged();
        });
    }
}
