package com.portofino.realtrainmodunofficial.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import com.portofino.realtrainmodunofficial.client.model.MqoModelLoader;
import jp.ngt.ngtlib.renderer.GLRecorder;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * レール 1 本ぶんの描画を「1 つの統合メッシュ (VBO)」に焼いて使い回す。
 *
 * 本家 RTM 1.7.10 はレール 1 本をディスプレイリストに 1 回だけ焼き、以後は GPU 側に
 * 置きっぱなしにしていた。1.21 移植ではそれが「毎フレーム、0.5m 刻みの位置ごとに
 * モデルを描画呼び出し」になっており、50m のレール 1 本で 900 回以上の draw call
 * (setupRenderState + ユニフォーム転送 + bind/draw) を毎フレーム発行していた
 * (実測 2.83ms/本 = レール 10 本で 28ms/フレーム)。
 *
 * ここでは既存の描画コードに頂点を吐かせて (MeshCapture) それを 1 つにまとめ、
 * 毎フレームは RenderType ごとに 1 回だけ描く。頂点の中身も RenderType の選択も
 * 既存コードが決めたものをそのまま使うため、見た目は変わらない。
 */
public final class RailMeshCache {

    /**
     * 1 本のレールが統合メッシュ化できる頂点数の上限 (異常に巨大なレール対策)。
     * 超えた場合は従来の逐次描画にフォールバックする。
     */
    private static final long MAX_MERGED_VERTICES = 4_000_000L;

    /**
     * 同時に保持するレールメッシュ数の上限 (VRAM 上限)。超えたら古いものから解放する。
     */
    private static final int MAX_ENTRIES = 512;

    /**
     * 1 フレームあたりに新規/再焼き込みするレールの上限。<b>スパイク対策の要。</b>
     * <p>
     * 駅・車両基地に入るとチャンクロードで多数のレールが一斉に初回焼き込みされ、
     * 1 フレームに集中して「レール負荷が普通 10 なのに時々 190」というスタッタになる
     * (焼き込みは約 2.8ms/本)。ここで 1 フレームの焼き込みを N 本までに制限し、
     * 残りは次フレーム以降へ回す。<b>最終的な見た目は同じ</b>で、初回だけ数フレーム遅れて
     * 出る (再焼き待ちは古いメッシュをそのまま描くので不可視にならない)。
     */
    private static final int MAX_BAKES_PER_FRAME = 8;

    private static int bakesThisFrame;

    /**
     * 同一レールを再焼き込みする最短フレーム間隔。<b>クロスポイント等の churn 対策。</b>
     * <p>
     * variant (トング位置) は 8 段階に量子化済みだが、量子化の境界でトングが揺れると
     * variant が毎tick変わり得る。そのまま焼き直すと「静止して見えるのに毎tick再焼き込みされて
     * 重い」(bake が下がらない) 状態になる。variant/light の変化による焼き直しは、直近に焼いて
     * から最低このフレーム数は見送り、古いメッシュを描く。強制再焼き (パック再読込・モデル差替) は
     * 対象外。実アニメは量子化で 8 段階なので、この間隔では見た目が変わらない。
     */
    private static final int REBAKE_MIN_FRAMES = 8;

    /** 毎フレーム +1 される描画フレーム番号 (再焼き間隔の判定用)。 */
    private static int frameCounter;

    private static final Map<BlockPos, RailMesh> CACHE = new LinkedHashMap<>(64, 0.75F, true);

    private static Level lastLevel;

    /** 毎フレーム先頭 (RailDrawQueue.flush 前) に呼ぶ。焼き込み枠をリセットし、フレーム番号を進める。 */
    public static void beginFrame() {
        bakesThisFrame = 0;
        frameCounter++;
    }

    private RailMeshCache() {
    }

    /**
     * @param variant 同じレールでも見た目が変わる要因 (分岐の開通方向など)。変われば焼き直す。
     */
    private record RailMesh(List<MeshCapture.Section> sections, MqoModelLoader.MqoModel model,
                            int light, long variant, int bakeFrame) {
        void close() {
            for (MeshCapture.Section section : sections) {
                section.close();
            }
        }
    }

    /**
     * 統合メッシュで 1 本を描く。
     *
     * @param rebuilt true = 記録 (GLRecorder) が作り直された → メッシュも焼き直す
     * @return true = 描画した (呼び出し元は逐次描画をスキップする)
     */
    public static boolean draw(BlockPos pos, GLRecorder rec, MqoModelLoader.MqoModel model,
                               PoseStack poseStack, int packedLight, int packedOverlay, boolean rebuilt) {
        if (rec == null || model == null || rec.isEmpty()) {
            com.portofino.realtrainmodunofficial.client.ClientRenderProfiler.countRailFallback();
            return false;
        }
        dropIfLevelChanged();

        return drawBaked(pos, 0L, model, poseStack, packedLight, packedOverlay, rebuilt,
            (ps, buffer) -> RailScriptRenderers.replayForCapture(rec, ps, buffer, packedLight, packedOverlay, model));
    }

    /**
     * 描画コードを渡して統合メッシュに焼き、以後は VBO を描くだけにする汎用版。
     * <p>
     * GLRecorder を持たない経路 (分岐の旧パイプライン) からも使えるようにしたもの。
     * {@code baker} は<b>単位行列の PoseStack</b> で呼ばれるので、頂点はレール原点
     * (ブロック隅) 基準になる。毎フレームの描画時に本物の pose を掛けるため、結果は
     * 逐次描画と数学的に同じ。
     *
     * @param variant      見た目が変わる要因 (分岐の開通方向など)。変われば焼き直す
     * @param forceRebuild true = 強制的に焼き直す
     * @return true = 描画を予約した (呼び出し元は逐次描画をスキップする)
     */
    public static boolean drawBaked(BlockPos pos, long variant, MqoModelLoader.MqoModel model,
                                    PoseStack poseStack, int packedLight, int packedOverlay,
                                    boolean forceRebuild, Baker baker) {
        if (model == null || baker == null) {
            com.portofino.realtrainmodunofficial.client.ClientRenderProfiler.countRailFallback();
            return false;
        }
        //★ Iris/Oculus のシェーダーパック使用中は統合メッシュ (VBO) を使わない。
        //
        //この経路は焼いた VBO を RenderSystem.getShader() で直接描く (RailDrawQueue)。
        //シェーダーパックが有効だとコアシェーダーが Iris のものに差し替わっており、
        //頂点フォーマットも uniform (特に modelView) も噛み合わない。結果、レールが
        //ワールド座標ではなくビュー座標で描かれ、<b>視点を動かすとレールが画面に貼り付いて
        //ついてくる</b> (ユーザー報告のバグ)。
        //
        //シェーダー使用中は逐次描画にフォールバックする。MultiBufferSource 経由なら
        //Iris が正しく捕まえてくれる。軽量化は効かなくなるが、壊れるよりはよい。
        if (com.portofino.realtrainmodunofficial.client.ShaderCompat.isShaderPackInUse()) {
            //焼き済みの VBO は使わないので解放する (空なら何もしない)。
            if (!CACHE.isEmpty()) {
                clear();
            }
            com.portofino.realtrainmodunofficial.client.ClientRenderProfiler.countRailFallback();
            return false;
        }
        dropIfLevelChanged();

        RailMesh mesh = CACHE.get(pos);
        //必ず焼く要因: 強制再焼き (パック再読込)・未焼き・モデル差替。
        boolean mustBake = forceRebuild || mesh == null || mesh.model() != model;
        //任意で焼く要因: ライト or 見た目 (variant=トング位置) が変わった。
        boolean optionalBake = !mustBake && (mesh.light() != packedLight || mesh.variant() != variant);
        //churn 対策: 任意再焼きは、直近に焼いてから REBAKE_MIN_FRAMES 経つまで見送り、古いメッシュを
        //描く。量子化の境界でトングが揺れて variant が毎tick変わっても、再焼きは間隔内に制限される
        //(静止して見えるクロスポイントが再焼きされ続けて重い、を解消)。見た目は最大数フレーム古いだけ。
        if (optionalBake && (frameCounter - mesh.bakeFrame()) < REBAKE_MIN_FRAMES) {
            optionalBake = false;
        }
        boolean needsBake = mustBake || optionalBake;
        if (needsBake) {
            if (bakesThisFrame >= MAX_BAKES_PER_FRAME) {
                //★スパイク対策: このフレームの焼き込み枠を使い切った → 次フレームへ回す。
                if (mesh != null) {
                    //再焼き待ち: 古いメッシュをそのまま描く (光/形が最大数フレーム古いだけ・
                    //不可視にはしない)。needsBake は次フレームも真なので枠が空き次第焼き直る。
                    // (下の描画へフォールスルー)
                } else {
                    //初回焼き待ち: まだ焼けていない。逐次描画に落とすと結局スパイクするので、
                    //ここは「描かない」で次フレームへ回す (数フレームで焼けて出る = 分散)。
                    //true を返すと呼び出し側は逐次描画フォールバックをしない。
                    return true;
                }
            } else {
                RailMesh old = CACHE.remove(pos);
                if (old != null) {
                    old.close();
                }
                com.portofino.realtrainmodunofficial.client.ClientRenderProfiler.countRailBake();
                bakesThisFrame++;
                mesh = bake(baker, model, packedLight, variant);
                if (mesh == null) {
                    //頂点数が上限超え等で焼けなかった → 毎フレーム逐次描画に落ちる (重い)
                    com.portofino.realtrainmodunofficial.client.ClientRenderProfiler.countRailFallback();
                    return false;
                }
                CACHE.put(pos, mesh);
                trim();
            }
        }

        for (MeshCapture.Section section : mesh.sections()) {
            //即時描画はしない。RenderType ごとにまとめて描くためキューへ積む
            //(GL のステート設定/破棄をレール 1 本ごとに走らせるのが重さの正体だった)。
            if (!RailDrawQueue.enqueue(section.vbo(), section.renderType(), poseStack)) {
                //VBO が無効になった → このレールは統合メッシュを諦めて逐次描画に戻す
                CACHE.remove(pos);
                mesh.close();
                com.portofino.realtrainmodunofficial.client.ClientRenderProfiler.countRailFallback();
                return false;
            }
        }
        com.portofino.realtrainmodunofficial.client.ClientRenderProfiler.countRailMerged();
        return true;
    }

    /**
     * 記録済みの描画コマンドを再生し、既存コードが吐いた頂点をそのまま 1 つの VBO に焼く。
     * 単位行列の PoseStack で再生するので、頂点はレール原点 (ブロック隅) 基準になる。
     * 毎フレームの描画時に本物の pose を掛けるため、結果は逐次描画と数学的に同じ。
     */
    /** 統合メッシュに焼く描画コード。単位行列の PoseStack と捕獲用バッファが渡される。 */
    public interface Baker {
        void render(PoseStack poseStack, net.minecraft.client.renderer.MultiBufferSource buffer);
    }

    private static RailMesh bake(Baker baker, MqoModelLoader.MqoModel model, int packedLight, long variant) {
        MeshCapture.Source source = new MeshCapture.Source();
        boolean prevCapture = MqoModelLoader.captureMode;
        MqoModelLoader.captureMode = true;
        try {
            baker.render(new PoseStack(), source);
        } catch (Throwable t) {
            RealTrainModUnofficial.LOGGER.warn("Rail mesh bake failed", t);
            return null;
        } finally {
            MqoModelLoader.captureMode = prevCapture;
        }

        long vertices = source.totalVertices();
        if (vertices <= 0L || vertices > MAX_MERGED_VERTICES) {
            return null;
        }
        try {
            List<MeshCapture.Section> sections = source.upload();
            if (sections.isEmpty()) {
                return null;
            }
            return new RailMesh(sections, model, packedLight, variant, frameCounter);
        } catch (Throwable t) {
            RealTrainModUnofficial.LOGGER.warn("Rail mesh upload failed", t);
            return null;
        }
    }

    private static void trim() {
        while (CACHE.size() > MAX_ENTRIES) {
            Iterator<Map.Entry<BlockPos, RailMesh>> it = CACHE.entrySet().iterator();
            if (!it.hasNext()) {
                return;
            }
            Map.Entry<BlockPos, RailMesh> eldest = it.next();
            it.remove();
            eldest.getValue().close();
        }
    }

    /**
     * ワールドが変わったら全部解放する (VRAM を持ち越さない)。
     */
    private static void dropIfLevelChanged() {
        Level level = Minecraft.getInstance().level;
        if (level != lastLevel) {
            clear();
            lastLevel = level;
        }
    }

    public static void clear() {
        for (RailMesh mesh : CACHE.values()) {
            mesh.close();
        }
        CACHE.clear();
    }

    /**
     * レールが壊された / 作り直された時に呼ぶ (VBO を解放する)。
     */
    public static void invalidate(BlockPos pos) {
        RailMesh mesh = CACHE.remove(pos);
        if (mesh != null) {
            mesh.close();
        }
    }
}
