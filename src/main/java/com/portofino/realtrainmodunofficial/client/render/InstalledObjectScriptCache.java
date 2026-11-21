package com.portofino.realtrainmodunofficial.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.portofino.realtrainmodunofficial.blockentity.InstalledObjectBlockEntity;
import com.portofino.realtrainmodunofficial.client.model.MqoModelLoader;
import jp.ngt.ngtlib.renderer.GLRecorder;
import net.minecraft.client.renderer.MultiBufferSource;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** スクリプト付き設置オブジェクト (標識/看板など、renderClass を持たず MqoMode… */
public final class InstalledObjectScriptCache {

    private static final Map<InstalledObjectBlockEntity, Cache> CACHES =
            Collections.synchronizedMap(new WeakHashMap<>());

    /** 使い回す記録。中の Cmd ごと再利用されるので、毎フレームのゴミが出ない。 */
    private static final ThreadLocal<GLRecorder> SCRATCH = ThreadLocal.withInitial(GLRecorder::new);

    private static final class Cache {
        /** スクリプトがジオメトリを出さない (baked が本体の) オブジェクト。 */
        boolean noCache;
    
        /** この設置物のスクリプトが時刻を読む (= 見た目が毎フレーム変わる) か。 */
        boolean timeDependent;
    }

    private InstalledObjectScriptCache() {
    }

    public static void render(InstalledObjectBlockEntity be, MqoModelLoader.MqoModel model,
                              PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay) {
        Cache c = CACHES.computeIfAbsent(be, k -> new Cache());
        // スクリプトが GLRecorder に何も出さない (baked が本体) オブジェクトは素通し…
        if (c.noCache) {
            MqoModelLoader.renderModelPreferScript(model, poseStack, buffer, packedLight, be);
            return;
        }
        // ★スクリプトの実行回数は RTMU では制御しない (スクリプト任せ)。
        GLRecorder rec = SCRATCH.get();
        rec.clear();
        // ★スクリプトが時刻を読んだかを見張る。読んでいたら毎フレーム動く物 (ミラーボールの回転・ 赤色灯…
        jp.ngt.rtm.render.PartsRenderer.clearTimeAccessed();
        GLRecorder.activate(rec);
        try {
            MqoModelLoader.renderModelPreferScript(model, poseStack, buffer, packedLight, be);
        } finally {
            GLRecorder.deactivate();
        }
        boolean timeDependent = jp.ngt.rtm.render.PartsRenderer.wasTimeAccessed();
        if (timeDependent) {
            // 時間で見た目が変わる物は毎フレーム作り直して提出する。
            c.timeDependent = true;
        }
        if (c.timeDependent) {
            VehicleScriptRenderers.replay(rec, poseStack, buffer, packedLight, packedOverlay, model, null);
            return;
        }
        if (rec.hasGeometry()) {
            // ★色ピッキング: 本家 PartsRenderer は通常描画のたびに当たりを取る。
            com.portofino.realtrainmodunofficial.client.ActionPartsHost pickHost =
                com.portofino.realtrainmodunofficial.client.ActionPartsPicker.hostOf(be);
            if (pickHost != null && com.portofino.realtrainmodunofficial.client.ActionPartsPicker.shouldCapture(be)) {
                com.portofino.realtrainmodunofficial.client.render.ActionPartsPickBuffer.begin(pickHost);
                try {
                    VehicleScriptRenderers.replay(rec, poseStack, buffer, packedLight, packedOverlay, model, null);
                } finally {
                    int pickedId = com.portofino.realtrainmodunofficial.client.render.ActionPartsPickBuffer.finish();
                    com.portofino.realtrainmodunofficial.client.ActionPartsPicker.setHoveredId(be, pickedId);
                }
                return;
            }
            // ★本家 RailPartsRenderer.renderRailStatic と同じ流れ: 内容キ…
            int key = 31 * rec.contentKey() + packedLight;
            boolean baked = ObjectMeshCache.draw(be, poseStack, key,
                // 焼くときは単位行列で再生する。カメラ相対の pose で焼くと カメラが動いた瞬間に設置物が付いて…
                buf -> VehicleScriptRenderers.replay(rec, new PoseStack(), buf,
                    packedLight, packedOverlay, model, null));
            if (!baked) {
                // シェーダーパック使用中など、焼き込みを使えないときは従来どおり CPU で提出
                VehicleScriptRenderers.replay(rec, poseStack, buffer, packedLight, packedOverlay, model, null);
            }
        } else {
            // スクリプトはジオメトリを出さない = baked が本体。以降キャッシュせず素通し。
            c.noCache = true;
        }
    }
}
