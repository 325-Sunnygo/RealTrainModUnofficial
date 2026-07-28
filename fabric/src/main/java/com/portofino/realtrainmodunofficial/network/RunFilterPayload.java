package com.portofino.realtrainmodunofficial.network;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import jp.ngt.mcte.editor.EditorSelection;
import io.netty.buffer.ByteBuf;
import jp.ngt.mcte.editor.filter.EditFilter;
import jp.ngt.mcte.editor.filter.Filters;
import jp.ngt.mcte.editor.filter.UndoHistory;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 編集フィルタの実行 (neo mcte)。
 * パラメータは 名前=値 を改行で並べた 1 本の文字列で送る。
 */
public record RunFilterPayload(String filterName, String params) implements CustomPacketPayload {

    public static final String UNDO = "__undo__";
    /** エディタ画面を開く。 */
    public static final String OPEN = "__open__";
    /** 選択を解除する。 */
    public static final String CLEAR = "__clear__";
    /** やり直す。 */
    public static final String REDO = "__redo__";

    public static final Type<RunFilterPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(RealTrainModUnofficial.MODID, "run_filter")
    );

    public static final StreamCodec<ByteBuf, RunFilterPayload> STREAM_CODEC =
        StreamCodec.of((buf, p) -> {
            FriendlyByteBuf b = new FriendlyByteBuf(buf);
            b.writeUtf(p.filterName(), 64);
            b.writeUtf(p.params() == null ? "" : p.params(), 8192);
        }, buf -> {
            FriendlyByteBuf b = new FriendlyByteBuf(buf);
            return new RunFilterPayload(b.readUtf(64), b.readUtf(8192));
        });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnServer(RunFilterPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            if (player == null || !(player.level() instanceof ServerLevel level)) {
                return;
            }
            if (OPEN.equals(payload.filterName())) {
                // 画面はクライアントが開く。ここでは選択の器だけ用意する。
                EditorSelection.of(player);
                return;
            }
            if (CLEAR.equals(payload.filterName())) {
                EditorSelection ed = EditorSelection.peek(player);
                if (ed != null) {
                    jp.ngt.mcte.editor.filter.UndoHistory.forget(ed);
                }
                EditorSelection.clear(player);
                player.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable("msg.realtrainmodunofficial.editor.cleared"), true);
                return;
            }
            EditorSelection editor = EditorSelection.peek(player);
            if (editor == null) {
                player.displayClientMessage(Component.translatable("msg.realtrainmodunofficial.editor.no_selection"), true);
                return;
            }

            if (REDO.equals(payload.filterName())) {
                int n = UndoHistory.redo(level, editor);
                player.displayClientMessage(n > 0
                    ? Component.translatable("msg.realtrainmodunofficial.editor.redone", n)
                    : Component.translatable("msg.realtrainmodunofficial.editor.nothing_to_redo"), true);
                return;
            }
            if (UNDO.equals(payload.filterName())) {
                int n = UndoHistory.undo(level, editor);
                player.displayClientMessage(n > 0
                    ? Component.translatable("msg.realtrainmodunofficial.editor.undone", n)
                    : Component.translatable("msg.realtrainmodunofficial.editor.nothing_to_undo"), true);
                return;
            }

            if (!editor.hasEnd()) {
                player.displayClientMessage(Component.translatable("msg.realtrainmodunofficial.editor.no_selection"), true);
                return;
            }

            EditFilter filter = Filters.byName(payload.filterName());
            if (filter == null) {
                return;
            }
            applyParams(filter, payload.params());

            // ★上限を超える範囲は実行しない。誤って巨大範囲を選んだままフィルタを流すと
            // サーバが固まるので、走らせる前に断る。
            long volume = editor.getVolume();
            if (volume > jp.ngt.mcte.editor.filter.EditorOps.MAX_BLOCKS) {
                player.displayClientMessage(Component.translatable(
                    "msg.realtrainmodunofficial.editor.too_large", volume,
                    jp.ngt.mcte.editor.filter.EditorOps.MAX_BLOCKS), true);
                return;
            }

            int changed;
            try {
                changed = filter.apply(level, player, editor);
            } catch (Exception e) {
                RealTrainModUnofficial.LOGGER.warn("[RTMU] フィルタ {} が失敗", payload.filterName(), e);
                player.displayClientMessage(Component.translatable(
                    "msg.realtrainmodunofficial.editor.failed", payload.filterName()), true);
                return;
            }
            player.displayClientMessage(Component.translatable(
                "msg.realtrainmodunofficial.editor.done", payload.filterName(), changed), true);
        });
    }

    /** 名前=値 の並びを設定へ流し込む。 */
    private static void applyParams(EditFilter filter, String params) {
        if (params == null || params.isBlank()) {
            return;
        }
        for (String line : params.split("\n")) {
            int eq = line.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            var p = filter.config().get(line.substring(0, eq));
            if (p != null) {
                p.set(line.substring(eq + 1));
            }
        }
    }
}
