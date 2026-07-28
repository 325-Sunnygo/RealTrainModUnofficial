package jp.kaiz.atsassistmod.network;

import io.netty.buffer.ByteBuf;
import jp.kaiz.atsassistmod.block.tileentity.IFTTTBlockEntity;
import jp.kaiz.atsassistmod.ifttt.IFTTTContainer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * IFTTT 設定 GUI → サーバー。本家 PacketIFTTT の移植。
 * mode: 0=追加/変更 (index==-1 で追加), 2=削除, 3=AnyMatch 切替。
 * container は IFTTTContainer.toNbt() (mode 3 では空タグ)。
 */
public record IFTTTUpdatePayload(BlockPos pos, int mode, int index, CompoundTag container,
                                 boolean anyMatch) implements CustomPacketPayload {

    public static final Type<IFTTTUpdatePayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("atsassistmod", "ifttt_update")
    );

    public static final StreamCodec<ByteBuf, IFTTTUpdatePayload> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC.cast(), IFTTTUpdatePayload::pos,
        ByteBufCodecs.VAR_INT, IFTTTUpdatePayload::mode,
        ByteBufCodecs.VAR_INT, IFTTTUpdatePayload::index,
        ByteBufCodecs.TRUSTED_COMPOUND_TAG, IFTTTUpdatePayload::container,
        ByteBufCodecs.BOOL, IFTTTUpdatePayload::anyMatch,
        IFTTTUpdatePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnServer(IFTTTUpdatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (payload.pos().distSqr(player.blockPosition()) > 64 * 64) {
                return;
            }
            if (!(player.level().getBlockEntity(payload.pos()) instanceof IFTTTBlockEntity tile)) {
                return;
            }
            //本家 PacketIFTTT.onMessage と同じ分岐
            if (payload.mode() == 3) {
                tile.setAnyMatch(payload.anyMatch());
            } else {
                IFTTTContainer container = IFTTTContainer.fromNbt(payload.container());
                if (container == null) {
                    return;
                }
                if (payload.index() == -1) {
                    tile.addIFTTT(container);
                } else if (payload.mode() == 2) {
                    tile.removeIFTTT(container, payload.index());
                } else {
                    tile.setIFTTT(container, payload.index());
                }
            }
            tile.setChangedAndSync();
        });
    }
}
