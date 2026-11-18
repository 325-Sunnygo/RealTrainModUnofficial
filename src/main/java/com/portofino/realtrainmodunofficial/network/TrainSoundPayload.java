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

public record TrainSoundPayload(int trainEntityId, String soundId, float volume, float pitch, float range) implements CustomPacketPayload {
    public TrainSoundPayload(int id,String sound,float volume,float pitch) { this(id,sound,volume,pitch,16F); }
    public TrainSoundPayload {
        if(!Float.isFinite(range) || range<0 || range>256)throw new IllegalArgumentException("Invalid sound range");
    }
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
        ByteBufCodecs.FLOAT,
        TrainSoundPayload::range,
        TrainSoundPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** サーバーから、その列車を見ている全プレイヤーへ 1 回だけ音を鳴らす。 */
    public static void broadcast(net.minecraft.world.entity.Entity train, String legacySoundId, float volume, float pitch) {
        broadcast(train,legacySoundId,volume,pitch,16F);
    }
    public static void broadcast(net.minecraft.world.entity.Entity train,String legacySoundId,float volume,float pitch,float range) {
        if (train == null || train.level().isClientSide() || legacySoundId == null || legacySoundId.isBlank() || range<=0) {
            return;
        }
        // 本家 PacketPlaySound と同じ 256 ブロック以内へ送る (範囲はクライアント減衰で決まる)。
        if (train.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            var payload = new TrainSoundPayload(train.getId(), legacySoundId, volume, pitch, range);
            for (net.minecraft.server.level.ServerPlayer player : serverLevel.players()) {
                if (player.distanceToSqr(train) <= 256.0D * 256.0D) {
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, payload);
                }
            }
        }
    }

    public static void handleOnClient(TrainSoundPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level == null || payload.soundId() == null || payload.soundId().isBlank()) {
                return;
            }
            // 旧 TrainEntity / 本家系 EntityTrainBase のどちらでも鳴らす (以前…
            net.minecraft.world.entity.Entity entity = minecraft.level.getEntity(payload.trainEntityId());
            if (LegacyScriptSoundManager.isTrain(entity) || entity instanceof jp.ngt.rtm.entity.train.EntityBogie) {
                // サーバー発の離散イベント音 (レバー/警笛/緩解音等): 抑制なしで送られた回数だけ鳴らす (連続…
                LegacyScriptSoundManager.playLegacyId(entity, payload.soundId(), payload.volume(), payload.pitch(), false, true,payload.range());
            }
        });
    }
}
