package com.portofino.realtrainmodunofficial.network;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import jp.ngt.rtm.block.tileentity.TileEntityMovingMachine;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** 移動装置の設定をサーバーへ送る。 */
public record MovingMachinePayload(BlockPos pos, int width, int height, int depth,
                                   int offsetX, int offsetY, int offsetZ,
                                   float speed, boolean guide, int vehicleType) implements CustomPacketPayload {

    public static final Type<MovingMachinePayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(RealTrainModUnofficial.MODID, "moving_machine"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MovingMachinePayload> STREAM_CODEC =
        StreamCodec.of((buf, v) -> {
            BlockPos.STREAM_CODEC.encode(buf, v.pos());
            buf.writeVarInt(v.width()); buf.writeVarInt(v.height()); buf.writeVarInt(v.depth());
            buf.writeVarInt(v.offsetX()); buf.writeVarInt(v.offsetY()); buf.writeVarInt(v.offsetZ());
            buf.writeFloat(v.speed()); buf.writeBoolean(v.guide());
            buf.writeVarInt(v.vehicleType());
        }, buf -> new MovingMachinePayload(
            BlockPos.STREAM_CODEC.decode(buf),
            buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
            buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
            buf.readFloat(), buf.readBoolean(), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnServer(MovingMachinePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (player.distanceToSqr(payload.pos().getCenter()) > 64.0D * 64.0D) {
                return;
            }
            if (player.level().getBlockEntity(payload.pos()) instanceof TileEntityMovingMachine tile) {
                tile.setData(payload.width(), payload.height(), payload.depth(),
                    payload.offsetX(), payload.offsetY(), payload.offsetZ(),
                    Math.max(0.0078125F, Math.min(1.0F, payload.speed())), payload.guide());
                tile.vehicleType = Math.floorMod(payload.vehicleType(), 3);
            }
        });
    }
}
