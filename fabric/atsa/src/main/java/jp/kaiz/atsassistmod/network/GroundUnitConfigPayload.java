package jp.kaiz.atsassistmod.network;

import io.netty.buffer.ByteBuf;
import jp.kaiz.atsassistmod.block.tileentity.GroundUnitBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 地上子の設定 (GUI → サーバー)。本家 PacketGroundUnitTile 相当。
 * 中身は BlockEntity と同じ NBT 形式 (unitType / linkRedStone / 各パラメータ)。
 */
public record GroundUnitConfigPayload(BlockPos pos, CompoundTag config) implements CustomPacketPayload {

    public static final Type<GroundUnitConfigPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("atsassistmod", "ground_unit_config")
    );

    public static final StreamCodec<ByteBuf, GroundUnitConfigPayload> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC.cast(), GroundUnitConfigPayload::pos,
        ByteBufCodecs.TRUSTED_COMPOUND_TAG, GroundUnitConfigPayload::config,
        GroundUnitConfigPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnServer(GroundUnitConfigPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (payload.pos().distSqr(player.blockPosition()) > 64 * 64) {
                return;
            }
            if (player.level().getBlockEntity(payload.pos()) instanceof GroundUnitBlockEntity be) {
                be.applyConfig(payload.config());
            }
        });
    }
}
