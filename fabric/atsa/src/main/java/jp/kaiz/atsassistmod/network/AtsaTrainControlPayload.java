package jp.kaiz.atsassistmod.network;

import io.netty.buffer.ByteBuf;
import jp.kaiz.atsassistmod.controller.TrainControllerManager;
import jp.kaiz.atsassistmod.controller.trainprotection.TrainProtectionType;
import jp.ngt.rtm.entity.train.EntityTrainBase;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 保安装置セレクター (GUI) → サーバー。
 * 本家 PacketTrainProtectionSetter / PacketManualDrive / PacketTrainDriveMode の統合移植。
 * action: "set_tp"(value=保安装置id) / "manual"(value=0|1) / "drive_mode"(value=0:手動 1:TASC 2:TASC/ATO)
 */
public record AtsaTrainControlPayload(String action, int value) implements CustomPacketPayload {

    public static final Type<AtsaTrainControlPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("atsassistmod", "train_control")
    );

    public static final StreamCodec<ByteBuf, AtsaTrainControlPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, AtsaTrainControlPayload::action,
        ByteBufCodecs.VAR_INT, AtsaTrainControlPayload::value,
        AtsaTrainControlPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnServer(AtsaTrainControlPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (!(player.getVehicle() instanceof EntityTrainBase train)) {
                return;
            }
            var tc = TrainControllerManager.getTrainController(train);
            switch (payload.action()) {
                //本家 PacketTrainProtectionSetter
                case "set_tp" -> tc.setTrainProtection(TrainProtectionType.getType(payload.value()));
                //本家 PacketManualDrive
                case "manual" -> tc.setManualDrive(payload.value() != 0);
                //本家 PacketTrainDriveMode: 0=手動 (TASC/ATO 解除), 1=TASC (ATO のみ解除), 2=TASC/ATO
                case "drive_mode" -> {
                    if (payload.value() <= 0) {
                        tc.tascController.disable();
                    }
                    if (payload.value() <= 1) {
                        tc.disableATO();
                    }
                }
                default -> {
                }
            }
        });
    }
}
