package com.portofino.realtrainmodunofficial.network;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import com.portofino.realtrainmodunofficial.blockentity.InstalledObjectBlockEntity;
import com.portofino.realtrainmodunofficial.installedobject.SpeakerSoundConfig;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Arrays;

/**
 * クライアント → サーバー。 スピーカーGUIからの設定送信。
 * <ul>
 *   <li>{@code soundSlot >= 1}: 音源ID(1-64)に {@code soundName} を割り当て（全体共通・config保存）し、
 *       全クライアントへ同期する。</li>
 *   <li>{@code range >= 1}: そのスピーカーブロックの可聴範囲を設定する。</li>
 * </ul>
 * 変更しない項目は soundSlot=0 / range=0 を送る。
 */
public record ConfigureSpeakerPayload(BlockPos pos, int soundSlot, String soundName, int range)
        implements CustomPacketPayload {

    public static final Type<ConfigureSpeakerPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(RealTrainModUnofficial.MODID, "configure_speaker")
    );

    public static final StreamCodec<ByteBuf, ConfigureSpeakerPayload> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC,
        ConfigureSpeakerPayload::pos,
        ByteBufCodecs.INT,
        ConfigureSpeakerPayload::soundSlot,
        ByteBufCodecs.STRING_UTF8,
        ConfigureSpeakerPayload::soundName,
        ByteBufCodecs.INT,
        ConfigureSpeakerPayload::range,
        ConfigureSpeakerPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnServer(ConfigureSpeakerPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (payload.soundSlot() >= 1 && payload.soundSlot() <= SpeakerSoundConfig.MAX_SOUND_ID) {
                //本家 TileEntitySpeaker.setSound 準拠: 音の登録はスピーカーごと
                //(グローバル設定は旧ワールドの読み取りフォールバックとして残す)
                if (player.level().getBlockEntity(payload.pos()) instanceof InstalledObjectBlockEntity speakerBe
                        && speakerBe.isSpeaker()) {
                    speakerBe.setSpeakerSound(payload.soundSlot(), payload.soundName());
                } else {
                    SpeakerSoundConfig.setSound(payload.soundSlot(), payload.soundName(), true);
                    String[] snapshot = SpeakerSoundConfig.snapshot();
                    SyncSpeakerSoundsPayload sync = new SyncSpeakerSoundsPayload(Arrays.asList(snapshot));
                    if (player.getServer() != null) {
                        for (ServerPlayer p : player.getServer().getPlayerList().getPlayers()) {
                            PacketDistributor.sendToPlayer(p, sync);
                        }
                    }
                }
            }
            if (payload.range() >= 1
                && player.level().getBlockEntity(payload.pos()) instanceof InstalledObjectBlockEntity be
                && be.isSpeaker()) {
                be.setSpeakerRange(payload.range());
            }
        });
    }
}
