package com.portofino.realtrainmodunofficial.network;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;
import java.util.Map;

/**
 * DataMap 同期の <b>client → server</b> 方向。
 *
 * <p>{@link DataMapSyncPayload} は server → client だけで、逆向きが無かった。
 * NGTO Builder のように<b>描画スクリプト (クライアント) が操作を受け取り、
 * サーバースクリプトが実処理をする</b>パックは、この向きが無いと何も起きない。
 * (Enter で {@code dataMap.setBoolean("isBuilding", true, 1)} を立てても、
 *  サーバー側の {@code getBoolean("isBuilding")} は永久に false のまま)
 *
 * <p>値の表記は server → client と同じ型プレフィクス付き文字列 ("I:5" / "B:true" 等)。
 */
public record DataMapClientSyncPayload(int entityId, Map<String, String> data) implements CustomPacketPayload {

    public static final Type<DataMapClientSyncPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(RealTrainModUnofficial.MODID, "data_map_client_sync")
    );

    public static final StreamCodec<ByteBuf, DataMapClientSyncPayload> STREAM_CODEC = StreamCodec.of(
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
            return new DataMapClientSyncPayload(id, map);
        }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnServer(DataMapClientSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().level().getEntity(payload.entityId())
                    instanceof jp.ngt.rtm.entity.vehicle.EntityVehicleBase<?> vehicle) {
                //操作できるのは乗っている本人だけ。他人の車両を書き換えられないようにする。
                if (!vehicle.hasPassenger(context.player())) {
                    return;
                }
                var dataMap = vehicle.getResourceState().getDataMap();
                payload.data().forEach(dataMap::applySyncedValue);
            }
        });
    }
}
