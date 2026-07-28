package com.portofino.realtrainmodunofficial.network;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import jp.masa.signalcontrollermod.SignalType;
import jp.masa.signalcontrollermod.TileEntitySignalController;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * SignalControllerMod (masa300) 移植: 設定 GUI → サーバー反映
 * (原作 PacketSignalController 相当)。設定値と nextSignal/displayPos の
 * 全リストを一括で送る。
 */
public record SignalControllerPayload(BlockPos pos, String signalType,
                                      boolean above, boolean last, boolean repeat, boolean reducedSpeed,
                                      List<BlockPos> nextSignal, List<BlockPos> displayPos)
        implements CustomPacketPayload {

    public static final Type<SignalControllerPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RealTrainModUnofficial.MODID, "signal_controller"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SignalControllerPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeBlockPos(p.pos());
                buf.writeUtf(p.signalType());
                int flags = (p.above() ? 1 : 0) | (p.last() ? 2 : 0) | (p.repeat() ? 4 : 0) | (p.reducedSpeed() ? 8 : 0);
                buf.writeByte(flags);
                buf.writeVarInt(p.nextSignal().size());
                p.nextSignal().forEach(buf::writeBlockPos);
                buf.writeVarInt(p.displayPos().size());
                p.displayPos().forEach(buf::writeBlockPos);
            },
            buf -> {
                BlockPos pos = buf.readBlockPos();
                String type = buf.readUtf();
                int flags = buf.readByte();
                int n = buf.readVarInt();
                List<BlockPos> next = new ArrayList<>();
                for (int i = 0; i < n; i++) {
                    next.add(buf.readBlockPos());
                }
                int d = buf.readVarInt();
                List<BlockPos> disp = new ArrayList<>();
                for (int i = 0; i < d; i++) {
                    disp.add(buf.readBlockPos());
                }
                return new SignalControllerPayload(pos, type,
                        (flags & 1) != 0, (flags & 2) != 0, (flags & 4) != 0, (flags & 8) != 0, next, disp);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnServer(SignalControllerPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (player.distanceToSqr(payload.pos().getX(), payload.pos().getY(), payload.pos().getZ()) > 64.0D * 64.0D) {
                return;
            }
            if (!(player.level().getBlockEntity(payload.pos()) instanceof TileEntitySignalController controller)) {
                return;
            }
            controller.setSignalType(SignalType.getType(payload.signalType()));
            controller.setAbove(payload.above());
            controller.setLast(payload.last());
            controller.setRepeat(payload.repeat());
            controller.setReducedSpeed(payload.reducedSpeed());
            controller.getNextSignal().clear();
            controller.getNextSignal().addAll(payload.nextSignal());
            controller.getDisplayPos().clear();
            controller.getDisplayPos().addAll(payload.displayPos());
            controller.syncToClient();
        });
    }
}
