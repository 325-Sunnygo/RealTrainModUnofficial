package com.portofino.realtrainmodunofficial.network;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import com.portofino.realtrainmodunofficial.blockentity.InstalledObjectBlockEntity;
import com.portofino.realtrainmodunofficial.installedobject.InstalledObjectCategory;
import com.portofino.realtrainmodunofficial.installedobject.InstalledObjectDefinition;
import com.portofino.realtrainmodunofficial.installedobject.InstalledObjectRegistry;
import com.portofino.realtrainmodunofficial.signboard.SignboardAnimeType;
import com.portofino.realtrainmodunofficial.signboard.SignboardText;
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
 * 看板エディタ (SignboardScreen) の保存。
 * 本家 PacketSelectResource (ResourceStateSignboard の同期) に相当する。
 * 本家のパケットは「選んだテクスチャ + 文字 + 時刻表設定」を丸ごと送っていたので、
 * こちらも definitionId (= テクスチャ) を含める。
 */
public record SaveSignboardPayload(BlockPos pos, String definitionId, String ttSetting, List<SignboardText> texts)
        implements CustomPacketPayload {

    /**
     * 1枚の看板に貼れる文字数の上限 (壊れた/悪意あるパケットで際限なく食わないように)。
     */
    private static final int MAX_TEXTS = 64;
    /**
     * 到達距離の上限 (本家は距離判定なし)。
     */
    private static final double MAX_REACH_SQ = 64.0D * 64.0D;

    public static final Type<SaveSignboardPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RealTrainModUnofficial.MODID, "save_signboard"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SaveSignboardPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeBlockPos(payload.pos());
                buf.writeUtf(payload.definitionId());
                buf.writeUtf(payload.ttSetting());
                List<SignboardText> texts = payload.texts();
                int count = Math.min(MAX_TEXTS, texts.size());
                buf.writeVarInt(count);
                for (int i = 0; i < count; i++) {
                    SignboardText text = texts.get(i);
                    buf.writeUtf(text.text == null ? "" : text.text);
                    buf.writeUtf(text.font == null ? "" : text.font);
                    buf.writeVarInt(text.style);
                    buf.writeInt(text.color);
                    buf.writeFloat(text.posU);
                    buf.writeFloat(text.posV);
                    buf.writeFloat(text.size);
                    buf.writeFloat(text.width);
                    buf.writeVarInt(text.animeType.ordinal());
                    buf.writeFloat(text.animeSpeed);
                }
            },
            buf -> {
                BlockPos pos = buf.readBlockPos();
                String definitionId = buf.readUtf();
                String ttSetting = buf.readUtf();
                int count = Math.min(MAX_TEXTS, buf.readVarInt());
                List<SignboardText> texts = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    SignboardText text = new SignboardText();
                    text.text = buf.readUtf();
                    text.font = buf.readUtf();
                    text.style = buf.readVarInt() & 3;
                    text.color = buf.readInt() & 0xFFFFFF;
                    //壊れた値 (0 や NaN) が入ると描画側で 0 除算や不正な頂点になるので、ここで潰す。
                    text.posU = finite(buf.readFloat(), 0.0F);
                    text.posV = finite(buf.readFloat(), 0.0F);
                    text.size = positive(buf.readFloat(), 0.25F);
                    text.width = positive(buf.readFloat(), 1.5F);
                    text.animeType = SignboardAnimeType.byOrdinal(buf.readVarInt());
                    text.animeSpeed = positive(buf.readFloat(), 1.0F);
                    texts.add(text);
                }
                return new SaveSignboardPayload(pos, definitionId, ttSetting, texts);
            });

    private static float finite(float value, float fallback) {
        return Float.isFinite(value) ? value : fallback;
    }

    private static float positive(float value, float fallback) {
        return Float.isFinite(value) && value > 0.0F ? value : fallback;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnServer(SaveSignboardPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            BlockPos pos = payload.pos();
            if (player.distanceToSqr(pos.getX(), pos.getY(), pos.getZ()) > MAX_REACH_SQ) {
                return;
            }
            if (!(player.level().getBlockEntity(pos) instanceof InstalledObjectBlockEntity be)
                    || be.getCategory() != InstalledObjectCategory.SIGNBOARD) {
                return;
            }
            //エディタでテクスチャを選び直していたら差し替える。存在しない/別カテゴリの ID は無視。
            String definitionId = payload.definitionId();
            if (definitionId != null && !definitionId.isBlank() && !definitionId.equals(be.getDefinitionId())) {
                InstalledObjectDefinition def = InstalledObjectRegistry.getById(definitionId);
                if (def != null && def.getCategory() == InstalledObjectCategory.SIGNBOARD) {
                    be.setDefinition(definitionId, InstalledObjectCategory.SIGNBOARD, be.getYaw());
                }
            }
            be.setSignboardData(payload.texts(), payload.ttSetting());
        });
    }
}
