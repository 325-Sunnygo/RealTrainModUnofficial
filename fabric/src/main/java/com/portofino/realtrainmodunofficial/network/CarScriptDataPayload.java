package com.portofino.realtrainmodunofficial.network;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import com.portofino.realtrainmodunofficial.entity.CarEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * クライアント→サーバの scriptData(DataMap) 同期。
 *
 * <p>SuperRailBuilder3 等は render(クライアント)スクリプトで GUI 入力を受け取り、設置点や
 * isBuilding 等を DataMap へ書く。サーバの onUpdate がそれを読んで実際にレールを敷くため、
 * クライアントの書き込みをサーバの同一エンティティへ届ける必要がある(RTMUは従来サーバ→
 * クライアント同期しか無かった)。</p>
 */
public record CarScriptDataPayload(int entityId, String key, String value) implements CustomPacketPayload {
    public static final Type<CarScriptDataPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(RealTrainModUnofficial.MODID, "car_script_data")
    );

    public static final StreamCodec<ByteBuf, CarScriptDataPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.INT, CarScriptDataPayload::entityId,
        ByteBufCodecs.STRING_UTF8, CarScriptDataPayload::key,
        ByteBufCodecs.STRING_UTF8, CarScriptDataPayload::value,
        CarScriptDataPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnServer(CarScriptDataPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() == null) {
                return;
            }
            Entity entity = context.player().level().getEntity(payload.entityId());
            if (entity instanceof CarEntity car) {
                car.setScriptDataValue(payload.key(), payload.value());
            }
        });
    }
}
