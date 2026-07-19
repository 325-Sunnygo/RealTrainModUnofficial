package jp.ngt.ngtlib.renderer;

/**
 * 本家 jp.ngt.ngtlib.renderer.NGTRenderer のスクリプト互換。
 * NGTO Builder の Prop/Liner ツールがミニチュア (NGTObject) のゴーストプレビューに使う。
 * 各ブロックを GLRecorder の RENDER_BLOCK として記録し、再生側
 * (VehicleScriptRenderers.replay) が BlockRenderDispatcher.renderSingleBlock で描く。
 */
@SuppressWarnings("unused")
public final class NGTRenderer {
    private NGTRenderer() {
    }

    /** NGTObject のプレビュー描画 (isOldVer 分岐: renderNGTObject(ngto, true))。 */
    public static void renderNGTObject(Object ngto, boolean translucent) {
        GLRecorder rec = GLRecorder.active();
        if (rec == null || !(ngto instanceof jp.ngt.ngtlib.block.NGTObject obj)) {
            return;
        }
        for (jp.ngt.ngtlib.block.BlockSet set : obj.blockList) {
            if (set == null) {
                continue;
            }
            net.minecraft.world.level.block.state.BlockState state = set.getState();
            if (state == null || state.isAir()) {
                continue;
            }
            rec.renderBlock(state, set.x, Math.max(set.y, 0), set.z);
        }
    }
}
