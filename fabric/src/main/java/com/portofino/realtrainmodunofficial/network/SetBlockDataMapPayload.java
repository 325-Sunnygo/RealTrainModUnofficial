package com.portofino.realtrainmodunofficial.network;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import com.portofino.realtrainmodunofficial.blockentity.InstalledObjectBlockEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;
import java.util.Map;

/**
 * 機械 (設置物) の DataMap を client → server で更新する。
 * 本家 BlockMachineBase の DataForm GUI が行う「フォームで入力した値をタイルの
 * DataMap に書き戻す」処理の移植。
 */
public record SetBlockDataMapPayload(BlockPos pos, Map<String, String> values) implements CustomPacketPayload {

    public static final Type<SetBlockDataMapPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(RealTrainModUnofficial.MODID, "set_block_data_map")
    );

    public static final StreamCodec<ByteBuf, SetBlockDataMapPayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeLong(payload.pos().asLong());
            ByteBufCodecs.VAR_INT.encode(buf, payload.values().size());
            for (Map.Entry<String, String> e : payload.values().entrySet()) {
                ByteBufCodecs.STRING_UTF8.encode(buf, e.getKey());
                ByteBufCodecs.STRING_UTF8.encode(buf, e.getValue());
            }
        },
        buf -> {
            BlockPos pos = BlockPos.of(buf.readLong());
            int size = ByteBufCodecs.VAR_INT.decode(buf);
            Map<String, String> map = new HashMap<>(size);
            for (int i = 0; i < size; i++) {
                map.put(ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf));
            }
            return new SetBlockDataMapPayload(pos, map);
        }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnServer(SetBlockDataMapPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().level().getBlockEntity(payload.pos())
                    instanceof InstalledObjectBlockEntity be) {
                payload.values().forEach(be::putScriptData);
            }
        });
    }
}
