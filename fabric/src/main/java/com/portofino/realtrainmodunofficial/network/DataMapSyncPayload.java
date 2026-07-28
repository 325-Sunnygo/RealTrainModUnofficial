package com.portofino.realtrainmodunofficial.network;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
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
 * EntityVehicleBase (本家系列車/車両) の DataMap 同期 (server → client)。
 * flag=1 で書かれたエントリを EntityTrainBase が定期配信する。
 * 値は型プレフィクス付き文字列 ("I:5" / "D:1.5" / "B:true" / "S:xxx")。
 * ATSA の HUD (ATSAssist_HUD) や表示系スクリプトがサーバー側の値を読むために必要。
 */
public record DataMapSyncPayload(int entityId, Map<String, String> data) implements CustomPacketPayload {

    public static final Type<DataMapSyncPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(RealTrainModUnofficial.MODID, "data_map_sync")
    );

    public static final StreamCodec<ByteBuf, DataMapSyncPayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {
            ByteBufCodecs.VAR_INT.encode(buf, payload.entityId());
            ByteBufCodecs.VAR_INT.encode(buf, payload.data().size());
            for (Map.Entry<String, String> entry : payload.data().entrySet()) {
                ByteBufCodecs.STRING_UTF8.encode(buf, entry.getKey());
                ByteBufCodecs.STRING_UTF8.encode(buf, entry.getValue());
            }
        },
        buf -> {
            int id = ByteBufCodecs.VAR_INT.decode(buf);
            int size = ByteBufCodecs.VAR_INT.decode(buf);
            Map<String, String> map = new HashMap<>(size);
            for (int i = 0; i < size; i++) {
                map.put(ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf));
            }
            return new DataMapSyncPayload(id, map);
        }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnClient(DataMapSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level == null) {
                return;
            }
            if (minecraft.level.getEntity(payload.entityId())
                    instanceof jp.ngt.rtm.entity.vehicle.EntityVehicleBase<?> vehicle) {
                var dataMap = vehicle.getResourceState().getDataMap();
                payload.data().forEach(dataMap::applySyncedValue);
            }
        });
    }
}
