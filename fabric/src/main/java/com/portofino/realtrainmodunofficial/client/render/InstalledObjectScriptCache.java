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
 * 描く汎用スクリプト = {@code renderPreferScript} 経路) の描画キャッシュ。
 *
 * <p><b>問題</b>: {@code renderPreferScript} は毎フレーム Nashorn の {@code render()} を pass 0-3
 * 実行していた。標識を多数 (120 個や旧 1.7.10 ワールドの標識群) 置くと、その毎フレーム実行が主コストに
 * なって重かった。信号/踏切 ({@link MachineScriptRenderers}) は既に GLRecorder キャッシュ済みだったが、
 * この汎用スクリプト経路は未キャッシュだった。
 *
 * <p><b>対策</b>: 状態シグネチャ ({@link InstalledObjectBlockEntity#renderStateSignature()}) が
 * 変わらない間はスクリプトの描画結果 (GLRecorder 記録) を再生し、Nashorn 再実行を省く。
 *
 * <p>baked (非スクリプトグループの VBO 直描画) は GLRecorder を通らないが、RTM の描画スクリプトは
 * 通常<b>全グループをスクリプトで描く</b> (baked は空) ため再生で不足しない。スクリプトが
 * ジオメトリを一切出さない (baked が本体の) オブジェクトはキャッシュせず素通しする。
 */
public final class InstalledObjectScriptCache {

    private static final Map<InstalledObjectBlockEntity, Cache> CACHES =
            Collections.synchronizedMap(new WeakHashMap<>());

    /** 使い回す記録。中の Cmd ごと再利用されるので、毎フレームのゴミが出ない。 */
    private static final ThreadLocal<GLRecorder> SCRATCH = ThreadLocal.withInitial(GLRecorder::new);

    private static final class Cache {
        /**
         * スクリプトがジオメトリを出さない (baked が本体の) オブジェクト。
         * <p>★以前ここには valid/sig/rec があったが、判定側が消えていて<b>一度も読まれておらず</b>、
         * 記録と再生が丸ごと無駄になっていた。焼き込みは {@link ObjectMeshCache} が持つ。
         */
        boolean noCache;
    }

    private InstalledObjectScriptCache() {
    }

    public static void render(InstalledObjectBlockEntity be, MqoModelLoader.MqoModel model,
                              PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay) {
        Cache c = CACHES.computeIfAbsent(be, k -> new Cache());
        //スクリプトが GLRecorder に何も出さない (baked が本体) オブジェクトは素通し (記録できないため)。
        if (c.noCache) {
            MqoModelLoader.renderModelPreferScript(model, poseStack, buffer, packedLight, be);
            return;
        }
        //★スクリプトの実行回数は RTMU では制御しない (スクリプト任せ)。
        //シグネチャは RTMU が知っている状態しか見られず、スクリプトが自前で進める
        //アニメ (点滅・スクロール・可動部) を検出できないため、間引くと不定期にカクつく。
        //GLRecorder を有効にして renderPreferScript を実行し、スクリプト部を記録。
        //(記録中はスクリプトの GL 命令は記録先へ流れ buffer には出ない。baked は buffer へ直接出る。)
        //★毎フレーム new しない。設置物 150 個 × 毎フレームだと記録の生成だけで効いてくる。
        //使い方はこのメソッドの中で閉じている (焼き込みの baker も同期実行) ので使い回して安全。
        GLRecorder rec = SCRATCH.get();
        rec.clear();
        GLRecorder.activate(rec);
        try {
            MqoModelLoader.renderModelPreferScript(model, poseStack, buffer, packedLight, be);
        } finally {
            GLRecorder.deactivate();
        }
        if (rec.hasGeometry()) {
            //★本家 RailPartsRenderer.renderRailStatic と同じ流れ:
            //  内容キーが同じなら焼き直さず、GPU に置いた頂点をそのまま描く。
            //  スクリプトは毎フレーム走らせたままなので点滅もスクロールも止まらない
            //  (動けば記録が変わる → キーが変わる → その場で焼き直す)。
            //  明るさは頂点に焼き込まれるので、本家が createStaticRenderKey に brightness を
            //  混ぜているのと同じく packedLight もキーに含める。
            int key = 31 * rec.contentKey() + packedLight;
            boolean baked = ObjectMeshCache.draw(be, poseStack, key,
                //焼くときは<b>単位行列</b>で再生する。カメラ相対の pose で焼くと
                //カメラが動いた瞬間に設置物が付いてきてしまう。
                buf -> VehicleScriptRenderers.replay(rec, new PoseStack(), buf,
                    packedLight, packedOverlay, model, null));
            if (!baked) {
                //シェーダーパック使用中など、焼き込みを使えないときは従来どおり CPU で提出
                com.portofino.realtrainmodunofficial.perf.RtmuProfiler.addObject(false);
                VehicleScriptRenderers.replay(rec, poseStack, buffer, packedLight, packedOverlay, model, null);
            }
        } else {
            //スクリプトはジオメトリを出さない = baked が本体。以降キャッシュせず素通し。
            //このフレームは renderPreferScript が baked を既に描画済みなので追加描画は不要。
            c.noCache = true;
        }
    }
}
