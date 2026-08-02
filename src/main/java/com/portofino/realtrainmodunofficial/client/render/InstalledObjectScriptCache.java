package com.portofino.realtrainmodunofficial.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.portofino.realtrainmodunofficial.blockentity.InstalledObjectBlockEntity;
import com.portofino.realtrainmodunofficial.client.model.MqoModelLoader;
import jp.ngt.ngtlib.renderer.GLRecorder;
import net.minecraft.client.renderer.MultiBufferSource;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * スクリプト付き設置オブジェクト (標識/看板など、renderClass を持たず MqoModel の scriptEngine で
 * 描く汎用スクリプト = renderPreferScript 経路) の描画キャッシュ。
 * 問題: renderPreferScript は毎フレーム Nashorn の render を pass 0-3
 * 実行していた。
 */
public final class InstalledObjectScriptCache {

    private static final Map<InstalledObjectBlockEntity, Cache> CACHES =
            Collections.synchronizedMap(new WeakHashMap<>());

    /** 使い回す記録。中の Cmd ごと再利用されるので、毎フレームのゴミが出ない。 */
    private static final ThreadLocal<GLRecorder> SCRATCH = ThreadLocal.withInitial(GLRecorder::new);

    private static final class Cache {
        /**
         * スクリプトがジオメトリを出さない (baked が本体の) オブジェクト。
         * ★以前ここには valid/sig/rec があったが、判定側が消えていて一度も読まれておらず、
         * 記録と再生が丸ごと無駄になっていた。焼き込みは ObjectMeshCache が持つ。
         */
        boolean noCache;
    
        /**
         * この設置物のスクリプトが時刻を読む (= 見た目が毎フレーム変わる) か。
         * 一度でも読んだら以後キャッシュしない。
         */
        boolean timeDependent;
    }

    private InstalledObjectScriptCache() {
    }

    public static void render(InstalledObjectBlockEntity be, MqoModelLoader.MqoModel model,
                              PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay) {
        Cache c = CACHES.computeIfAbsent(be, k -> new Cache());
        // スクリプトが GLRecorder に何も出さない (baked が本体) オブジェクトは素通し (記録できないため)。
        if (c.noCache) {
            MqoModelLoader.renderModelPreferScript(model, poseStack, buffer, packedLight, be);
            return;
        }
        // ★スクリプトの実行回数は RTMU では制御しない (スクリプト任せ)。
        // シグネチャは RTMU が知っている状態しか見られず、スクリプトが自前で進める
        // アニメ (点滅・スクロール・可動部) を検出できないため、間引くと不定期にカクつく。
        // ★毎フレーム new しない。設置物 150 個 × 毎フレームだと記録の生成だけで効いてくる。
        GLRecorder rec = SCRATCH.get();
        rec.clear();
        // ★スクリプトが時刻を読んだかを見張る。読んでいたら毎フレーム動く物 (ミラーボールの回転・
        // 赤色灯の回転・点滅) なので、焼き込みに載せてはいけない。
        // 本家も renderRailDynamic 相当は焼き込みの外で毎フレーム実行している。
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
            // (焼くと内容キーが毎フレーム変わり、焼き込み枠を食いつぶしたうえで
            //  古いメッシュが出続けて「回らない」状態になる)
            c.timeDependent = true;
        }
        if (c.timeDependent) {
            com.portofino.realtrainmodunofficial.perf.RtmuProfiler.addObject(false);
            VehicleScriptRenderers.replay(rec, poseStack, buffer, packedLight, packedOverlay, model, null);
            return;
        }
        if (rec.hasGeometry()) {
            // ★本家 RailPartsRenderer.renderRailStatic と同じ流れ:
            // 内容キーが同じなら焼き直さず、GPU に置いた頂点をそのまま描く。
            int key = 31 * rec.contentKey() + packedLight;
            boolean baked = ObjectMeshCache.draw(be, poseStack, key,
                // 焼くときは単位行列で再生する。カメラ相対の pose で焼くと
                // カメラが動いた瞬間に設置物が付いてきてしまう。
                buf -> VehicleScriptRenderers.replay(rec, new PoseStack(), buf,
                    packedLight, packedOverlay, model, null));
            if (!baked) {
                // シェーダーパック使用中など、焼き込みを使えないときは従来どおり CPU で提出
                com.portofino.realtrainmodunofficial.perf.RtmuProfiler.addObject(false);
                VehicleScriptRenderers.replay(rec, poseStack, buffer, packedLight, packedOverlay, model, null);
            }
        } else {
            // スクリプトはジオメトリを出さない = baked が本体。以降キャッシュせず素通し。
            // このフレームは renderPreferScript が baked を既に描画済みなので追加描画は不要。
            c.noCache = true;
        }
    }
}
