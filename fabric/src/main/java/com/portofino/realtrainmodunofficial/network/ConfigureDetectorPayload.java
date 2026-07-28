package com.portofino.realtrainmodunofficial.network;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import com.portofino.realtrainmodunofficial.blockentity.InstalledObjectBlockEntity;
import com.portofino.realtrainmodunofficial.installedobject.InstalledObjectCategory;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 列車検知器の設定 (出力先の座標 + 検知時に置くか消すか)。
 *
 * @param pos           検知器の位置
 * @param hasTarget     出力先を設定しているか (false なら出力しない)
 * @param target        出力先の座標
 * @param placeOnDetect true = 検知したら置く / false = 検知したら消す
 */
public record ConfigureDetectorPayload(BlockPos pos, boolean hasTarget, BlockPos target, boolean placeOnDetect)
        implements CustomPacketPayload {

    /**
     * 検知器から出力先までの上限(ブロック)。壊れたパケットで遠隔地のチャンクを
     * 巻き込まないための歯止め。
     */
    private static final int MAX_TARGET_DISTANCE = 256;
    private static final double MAX_REACH_SQ = 64.0D * 64.0D;

    public static final Type<ConfigureDetectorPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RealTrainModUnofficial.MODID, "configure_detector"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ConfigureDetectorPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeBlockPos(payload.pos());
                buf.writeBoolean(payload.hasTarget());
                buf.writeBlockPos(payload.target());
                buf.writeBoolean(payload.placeOnDetect());
            },
            buf -> new ConfigureDetectorPayload(
                    buf.readBlockPos(), buf.readBoolean(), buf.readBlockPos(), buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnServer(ConfigureDetectorPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            BlockPos pos = payload.pos();
            if (player.distanceToSqr(pos.getX(), pos.getY(), pos.getZ()) > MAX_REACH_SQ) {
                return;
            }
            if (!(player.level().getBlockEntity(pos) instanceof InstalledObjectBlockEntity be)
                    || be.getCategory() != InstalledObjectCategory.TRAIN_DETECTOR) {
                return;
            }
            BlockPos target = null;
            if (payload.hasTarget()) {
                target = payload.target();
                if (!target.closerThan(pos, MAX_TARGET_DISTANCE)
                        || target.getY() < player.level().getMinBuildHeight()
                        || target.getY() >= player.level().getMaxBuildHeight()) {
                    player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                            "screen.realtrainmodunofficial.train_detector.out_of_range", MAX_TARGET_DISTANCE), true);
                    return;
                }
            }
            be.configureDetector(target, payload.placeOnDetect());
        });
    }
}
