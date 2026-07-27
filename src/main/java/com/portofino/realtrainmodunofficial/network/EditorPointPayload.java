package com.portofino.realtrainmodunofficial.network;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import jp.ngt.mcte.editor.EditorSelection;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 選択範囲の点を動かす (neo mcte)。
 *
 * <p>本家 MCTE の「編集モード中は視点の先に合わせて範囲が伸びる」挙動のための通信。
 * クライアントが毎 tick 見ている先を判定し、変わったときだけ送る。
 *
 * @param which 0 = 始点、1 = 終点
 */
public record EditorPointPayload(int which, BlockPos pos) implements CustomPacketPayload {

    public static final Type<EditorPointPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(RealTrainModUnofficial.MODID, "editor_point")
    );

    public static final StreamCodec<ByteBuf, EditorPointPayload> STREAM_CODEC =
        StreamCodec.of((buf, p) -> {
            FriendlyByteBuf b = new FriendlyByteBuf(buf);
            b.writeVarInt(p.which());
            b.writeBlockPos(p.pos());
        }, buf -> {
            FriendlyByteBuf b = new FriendlyByteBuf(buf);
            return new EditorPointPayload(b.readVarInt(), b.readBlockPos());
        });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnServer(EditorPointPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            if (player == null) {
                return;
            }
            Level level = player.level();
            EditorSelection editor = EditorSelection.of(player);
            //★離れすぎた座標は無視する。改造クライアントから遠方を書き換えられないように。
            if (!payload.pos().closerToCenterThan(player.position(), 256.0D)) {
                return;
            }
            if (payload.which() == 0) {
                //始点だけ動かす。終点は保ったままにする (伸ばす操作なので確定を解除しない)
                editor.setStartKeepEnd(payload.pos());
            } else {
                editor.setEnd(payload.pos());
            }
        });
    }
}
