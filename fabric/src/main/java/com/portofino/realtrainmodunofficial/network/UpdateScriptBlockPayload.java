package com.portofino.realtrainmodunofficial.network;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import com.portofino.realtrainmodunofficial.blockentity.ScriptBlockEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record UpdateScriptBlockPayload(BlockPos pos, String script, boolean runOnRedstone, boolean executeNow) implements CustomPacketPayload {
    public static final Type<UpdateScriptBlockPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(RealTrainModUnofficial.MODID, "update_script_block")
    );

    public static final StreamCodec<ByteBuf, UpdateScriptBlockPayload> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC,
        UpdateScriptBlockPayload::pos,
        ByteBufCodecs.STRING_UTF8,
        UpdateScriptBlockPayload::script,
        ByteBufCodecs.BOOL,
        UpdateScriptBlockPayload::runOnRedstone,
        ByteBufCodecs.BOOL,
        UpdateScriptBlockPayload::executeNow,
        UpdateScriptBlockPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnServer(UpdateScriptBlockPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (!(player.level() instanceof ServerLevel serverLevel)) {
                return;
            }
            if (!(serverLevel.getBlockEntity(payload.pos()) instanceof ScriptBlockEntity blockEntity)) {
                return;
            }
            blockEntity.configure(payload.script(), payload.runOnRedstone());
            boolean executed = false;
            if (payload.executeNow()) {
                executed = blockEntity.runScript(serverLevel);
            }
            serverLevel.sendBlockUpdated(payload.pos(), blockEntity.getBlockState(), blockEntity.getBlockState(), 3);
            player.displayClientMessage(Component.literal(executed ? "スクリプトを実行しました" : "スクリプトブロックを保存しました"), true);
        });
    }
}
