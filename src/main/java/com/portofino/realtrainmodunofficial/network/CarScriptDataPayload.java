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

/** クライアント→サーバの scriptData(DataMap) 同期。 */
public record CarScriptDataPayload(int entityId, String key, String value) implements CustomPacketPayload {
    public static final Type<CarScriptDataPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(RealTrainModUnofficial.MODID, "car_script_data")
    );

    public static final StreamCodec<ByteBuf, CarScriptDataPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.INT, CarScriptDataPayload::entityId,
        ByteBufCodecs.stringUtf8(256), CarScriptDataPayload::key,
        // ★既定の STRING_UTF8 は 32767 文字まで。
        ByteBufCodecs.stringUtf8(262144), CarScriptDataPayload::value,
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
