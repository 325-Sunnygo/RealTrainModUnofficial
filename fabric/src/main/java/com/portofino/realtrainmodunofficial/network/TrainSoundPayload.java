package com.portofino.realtrainmodunofficial.network;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import com.portofino.realtrainmodunofficial.client.sound.LegacyScriptSoundManager;
import com.portofino.realtrainmodunofficial.entity.TrainEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record TrainSoundPayload(int trainEntityId, String soundId, float volume, float pitch) implements CustomPacketPayload {
    public static final Type<TrainSoundPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(RealTrainModUnofficial.MODID, "train_sound")
    );

    public static final StreamCodec<ByteBuf, TrainSoundPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.INT,
        TrainSoundPayload::trainEntityId,
        ByteBufCodecs.STRING_UTF8,
        TrainSoundPayload::soundId,
        ByteBufCodecs.FLOAT,
        TrainSoundPayload::volume,
        ByteBufCodecs.FLOAT,
        TrainSoundPayload::pitch,
        TrainSoundPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * サーバーから、その列車を見ている全プレイヤーへ 1 回だけ音を鳴らす。
     * legacySoundId は本家の書式 ("rtm:sounds/train/lever.ogg" 等) でよい。
     */
    public static void broadcast(net.minecraft.world.entity.Entity train, String legacySoundId, float volume, float pitch) {
        if (train == null || train.level().isClientSide() || legacySoundId == null || legacySoundId.isBlank()) {
            return;
        }
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingEntityAndSelf(
            train, new TrainSoundPayload(train.getId(), legacySoundId, volume, pitch));
    }

    public static void handleOnClient(TrainSoundPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level == null || payload.soundId() == null || payload.soundId().isBlank()) {
                return;
            }
            //旧 TrainEntity / 本家系 EntityTrainBase のどちらでも鳴らす
            //(以前は TrainEntity 決め打ちで、実際に出る本家系の列車では黙って捨てていた)
            net.minecraft.world.entity.Entity entity = minecraft.level.getEntity(payload.trainEntityId());
            if (LegacyScriptSoundManager.isTrain(entity)) {
                //サーバー発の離散イベント音 (レバー/警笛/緩解音等): 抑制なしで送られた回数だけ鳴らす
                //(連続ノッチ操作のガタガタ音は本家挙動。スクリプト用の抑制を通すと欠落する)
                LegacyScriptSoundManager.playLegacyId(entity, payload.soundId(), payload.volume(), payload.pitch(), false, true);
            }
        });
    }
}
