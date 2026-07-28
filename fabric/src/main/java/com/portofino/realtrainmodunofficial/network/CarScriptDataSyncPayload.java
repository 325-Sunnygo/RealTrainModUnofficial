package com.portofino.realtrainmodunofficial.network;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import com.portofino.realtrainmodunofficial.entity.CarEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;
import java.util.Map;

/**
 * サーバ→クライアントの CarEntity scriptData(DataMap) 同期。
 *
 * <p>SuperRailBuilder3 の render(クライアント)スクリプトは、サーバ onUpdate が設定した
 * {@code hostPlayerEntityId} 等を読んで GUI を起動する。これが同期されないとクライアントで
 * GUI が出ない。TrainScriptDataPayload の CarEntity 版。</p>
 */
public record CarScriptDataSyncPayload(int entityId, Map<String, String> data) implements CustomPacketPayload {
    public static final Type<CarScriptDataSyncPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(RealTrainModUnofficial.MODID, "car_script_data_sync")
    );

    public static final StreamCodec<ByteBuf, CarScriptDataSyncPayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {
            ByteBufCodecs.INT.encode(buf, payload.entityId());
            Map<String, String> map = payload.data();
            ByteBufCodecs.INT.encode(buf, map.size());
            for (Map.Entry<String, String> entry : map.entrySet()) {
                ByteBufCodecs.STRING_UTF8.encode(buf, entry.getKey());
                ByteBufCodecs.STRING_UTF8.encode(buf, entry.getValue());
            }
        },
        buf -> {
            int id = ByteBufCodecs.INT.decode(buf);
            int size = ByteBufCodecs.INT.decode(buf);
            Map<String, String> map = new HashMap<>(size);
            for (int i = 0; i < size; i++) {
                String key = ByteBufCodecs.STRING_UTF8.decode(buf);
                String value = ByteBufCodecs.STRING_UTF8.decode(buf);
                map.put(key, value);
            }
            return new CarScriptDataSyncPayload(id, map);
        }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnClient(CarScriptDataSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level == null) return;
            if (minecraft.level.getEntity(payload.entityId()) instanceof CarEntity car) {
                car.applyScriptDataSync(payload.data());
            }
        });
    }
}
