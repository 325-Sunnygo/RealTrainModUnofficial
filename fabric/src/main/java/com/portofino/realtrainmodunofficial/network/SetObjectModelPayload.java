package com.portofino.realtrainmodunofficial.network;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import com.portofino.realtrainmodunofficial.blockentity.InstalledObjectBlockEntity;
import com.portofino.realtrainmodunofficial.installedobject.InstalledObjectDefinition;
import com.portofino.realtrainmodunofficial.installedobject.InstalledObjectRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 設置済みの設置物のモデル/テクスチャを差し替える。
 * 本家 guiIdSelectTileEntityTexture (標識を素手で右クリック → テクスチャ選択) 用。
 * <p>
 * 標識は 1 テクスチャ = 1 定義なので、定義 ID を差し替えるとテクスチャが変わる。
 */
public record SetObjectModelPayload(BlockPos pos, String definitionId) implements CustomPacketPayload {

    public static final Type<SetObjectModelPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RealTrainModUnofficial.MODID, "set_object_model"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetObjectModelPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeBlockPos(p.pos());
                buf.writeUtf(p.definitionId(), 256);
            },
            buf -> new SetObjectModelPayload(buf.readBlockPos(), buf.readUtf(256)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnServer(SetObjectModelPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (!player.level().isLoaded(payload.pos())
                    || player.distanceToSqr(payload.pos().getCenter()) > 64.0D) {
                return;
            }
            if (!(player.level().getBlockEntity(payload.pos()) instanceof InstalledObjectBlockEntity be)) {
                return;
            }
            InstalledObjectDefinition definition = InstalledObjectRegistry.getById(payload.definitionId());
            //カテゴリをまたぐ差し替えは許さない (標識のGUIから信号機に化けたりしないように)。
            if (definition == null || definition.getCategory() != be.getCategory()) {
                return;
            }
            be.setDefinition(definition.getId(), be.getCategory(), be.getYaw());
            be.setChanged();
            player.level().sendBlockUpdated(payload.pos(), be.getBlockState(), be.getBlockState(), 3);
        });
    }
}
