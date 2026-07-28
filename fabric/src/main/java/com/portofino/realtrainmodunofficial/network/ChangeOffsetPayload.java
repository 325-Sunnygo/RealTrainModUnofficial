package com.portofino.realtrainmodunofficial.network;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import com.portofino.realtrainmodunofficial.blockentity.InstalledObjectBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 本家 GuiChangeOffset (バール右クリックの微調整 GUI) のサーバー反映。
 * Offset X/Y/Z, Rotation Roll/Pitch/Yaw, Scale。
 */
public record ChangeOffsetPayload(BlockPos pos, float offX, float offY, float offZ,
                                  float roll, float pitch, float yaw, float scale)
        implements CustomPacketPayload {

    public static final Type<ChangeOffsetPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RealTrainModUnofficial.MODID, "change_offset"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ChangeOffsetPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeBlockPos(p.pos());
                buf.writeFloat(p.offX());
                buf.writeFloat(p.offY());
                buf.writeFloat(p.offZ());
                buf.writeFloat(p.roll());
                buf.writeFloat(p.pitch());
                buf.writeFloat(p.yaw());
                buf.writeFloat(p.scale());
            },
            buf -> new ChangeOffsetPayload(buf.readBlockPos(),
                    buf.readFloat(), buf.readFloat(), buf.readFloat(),
                    buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnServer(ChangeOffsetPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (player.distanceToSqr(payload.pos().getX(), payload.pos().getY(), payload.pos().getZ()) > 64.0D * 64.0D) {
                return;
            }
            if (player.level().getBlockEntity(payload.pos()) instanceof InstalledObjectBlockEntity be) {
                be.setAdjustments(payload.offX(), payload.offY(), payload.offZ(),
                        payload.roll(), payload.pitch(), payload.yaw(), payload.scale());
            }
        });
    }
}
