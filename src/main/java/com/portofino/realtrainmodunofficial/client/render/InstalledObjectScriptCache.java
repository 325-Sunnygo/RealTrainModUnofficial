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
 * {@link #REFRESH_FRAMES} が時間依存要素の取りこぼしの安全網。見た目は不変。
 *
 * <p>baked (非スクリプトグループの VBO 直描画) は GLRecorder を通らないが、RTM の描画スクリプトは
 * 通常<b>全グループをスクリプトで描く</b> (baked は空) ため再生で不足しない。スクリプトが
 * ジオメトリを一切出さない (baked が本体の) オブジェクトはキャッシュせず素通しする。
 */
public final class InstalledObjectScriptCache {

    private static final int REFRESH_FRAMES = 8;
    private static final Map<InstalledObjectBlockEntity, Cache> CACHES =
            Collections.synchronizedMap(new WeakHashMap<>());

    private static final class Cache {
        boolean valid;
        boolean noCache;
        long sig;
        int framesSinceRun;
        GLRecorder rec;
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
        long sig = be.renderStateSignature();
        //ヒット: 記録を再生するだけ (Nashorn 実行なし)。
        if (c.valid && c.sig == sig && c.framesSinceRun < REFRESH_FRAMES) {
            com.portofino.realtrainmodunofficial.perf.RtmuProfiler.addObject(true);
            c.framesSinceRun++;
            VehicleScriptRenderers.replay(c.rec, poseStack, buffer, packedLight, packedOverlay, model, null);
            return;
        }
        com.portofino.realtrainmodunofficial.perf.RtmuProfiler.addObject(false);
        //ミス: GLRecorder を有効にして renderPreferScript を実行し、スクリプト部を記録。
        //(記録中はスクリプトの GL 命令は記録先へ流れ buffer には出ない。baked は buffer へ直接出る。)
        GLRecorder rec = new GLRecorder();
        GLRecorder.activate(rec);
        try {
            MqoModelLoader.renderModelPreferScript(model, poseStack, buffer, packedLight, be);
        } finally {
            GLRecorder.deactivate();
        }
        if (rec.hasGeometry()) {
            c.valid = true;
            c.sig = sig;
            c.framesSinceRun = 0;
            c.rec = rec;
            //このフレーム分もここで再生 (ミス時も含め毎フレーム描く)。
            VehicleScriptRenderers.replay(rec, poseStack, buffer, packedLight, packedOverlay, model, null);
        } else {
            //スクリプトはジオメトリを出さない = baked が本体。以降キャッシュせず素通し。
            //このフレームは renderPreferScript が baked を既に描画済みなので追加描画は不要。
            c.noCache = true;
        }
    }
}
