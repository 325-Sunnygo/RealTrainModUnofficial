package com.portofino.realtrainmodunofficial.client;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import org.lwjgl.glfw.GLFW;

/**
 * 軽量なクライアント側描画プロファイラ。
 * 1 秒単位で描画カテゴリごとの合計時間を集計して、必要な時だけ HUD に出す。
 */
@EventBusSubscriber(modid = RealTrainModUnofficial.MODID, value = Dist.CLIENT)
public final class ClientRenderProfiler {
    private static final String[] CATEGORY_NAMES = {"Rail", "RailOld", "Train", "Object"};
    private static final int CATEGORY_RAIL = 0;
    private static final int CATEGORY_TRAIN = 2;
    /** 分岐など、統合メッシュに乗らず毎フレーム逐次描画しているレール。 */
    private static final int CATEGORY_RAIL_OLD = 1;
    private static final int CATEGORY_OBJECT = 3;

    private static final long[] totalsNs = new long[CATEGORY_NAMES.length];
    private static final int[] counts = new int[CATEGORY_NAMES.length];
    private static final long[] displayTotalsNs = new long[CATEGORY_NAMES.length];
    private static final int[] displayCounts = new int[CATEGORY_NAMES.length];

    // CPU が 1 秒間に VertexConsumer へ書き込んだ頂点数 / バッチ数。
    // 「重さの正体は頂点スループット」なのかを数字で確定させるための計測。
    private static long verticesThisSecond;
    private static long batchesThisSecond;
    private static long displayVertices;
    private static long displayBatches;
    private static int framesThisSecond;
    private static int displayFrames;

    // レールが「統合メッシュ (1本=1VBO)」で描けているか、それとも逐次描画に落ちているか。
    // レールが重いときの切り分け用: fallback が多ければ焼き込みが効いていない。
    private static int railMergedThisSecond;
    private static int railFallbackThisSecond;
    private static int railBakeThisSecond;
    // GL ステートを切り替えた回数と VBO を描いた回数。重さは「描画回数」より
    // 「ステート切替の回数」で決まるので、両方を出して効果を見る。
    private static int railStateSetupThisSecond;
    private static int railVboDrawThisSecond;
    private static int displayRailMerged;
    private static int displayRailFallback;
    private static int displayRailBake;
    private static int displayRailStateSetup;
    private static int displayRailVboDraw;

    public static void countRailStateSetup() {
        railStateSetupThisSecond++;
    }

    public static void countRailVboDraw() {
        railVboDrawThisSecond++;
    }

    public static void countRailMerged() {
        railMergedThisSecond++;
    }

    public static void countRailFallback() {
        railFallbackThisSecond++;
    }

    public static void countRailBake() {
        railBakeThisSecond++;
    }

    private static long lastSnapshotNs = System.nanoTime();
    private static boolean overlayEnabled;

    /** 1 バッチ (= 1 回の描画呼び出し) 分の頂点数を計上する。 */
    public static void addVertices(int vertexCount) {
        verticesThisSecond += vertexCount;
        batchesThisSecond++;
        if (inTrain) {
            trainVerticesThisSecond += vertexCount;
            trainBatchesThisSecond++;
        }
    }

    /**
     * 列車描画中フラグ。addVertices を「列車ぶん」と「それ以外」に分けて数えるために使う。
     * の実測で、列車を見た瞬間に batches が 96→232 (+136)、頂点が +24000 に増える一方
     * スクリプト時間はほぼ変わらなかった。
     */
    private static boolean inTrain;
    private static long trainVerticesThisSecond;
    private static long trainBatchesThisSecond;
    private static long displayTrainVertices;
    private static long displayTrainBatches;

    public static void beginTrainGeometry() {
        inTrain = true;
    }

    public static void endTrainGeometry() {
        inTrain = false;
    }

    /**
     * 静的 VBO 経路をどの条件で落ちたかの内訳。
     * 実測で「7 バッチ・31,564 頂点・14ms/frame」= 全バッチが CPU 頂点提出に落ちていた。
     */
    public static final String[] VBO_SKIP_NAMES = {
        "OK(VBO使用)", "capture", "blend", "transform", "depthBias",
        "overlay", "color", "uvWindow", "shader", "build失敗", "小バッチ"
    };
    public static final int VBO_OK = 0;
    public static final int VBO_CAPTURE = 1;
    public static final int VBO_BLEND = 2;
    public static final int VBO_TRANSFORM = 3;
    public static final int VBO_BIAS = 4;
    public static final int VBO_OVERLAY = 5;
    public static final int VBO_COLOR = 6;
    public static final int VBO_UV = 7;
    public static final int VBO_SHADER = 8;
    public static final int VBO_BUILD = 9;
    /** 小さすぎて VBO 経路が割に合わないバッチ (バッファ経路へ回してまとめて描く)。 */
    public static final int VBO_SMALL = 10;

    private static final int[] vboSkips = new int[VBO_SKIP_NAMES.length];
    private static final int[] displayVboSkips = new int[VBO_SKIP_NAMES.length];
    /** 表示で「多い順に上位数件」を選ぶときの選択済みフラグ (描画スレッド専用)。 */
    private static final boolean[] usedVboIndex = new boolean[VBO_SKIP_NAMES.length];

    public static void countVbo(int reason) {
        if (reason >= 0 && reason < vboSkips.length) {
            vboSkips[reason]++;
        }
    }

    /**
     * VBO 経路を落ちたバッチの内訳 (グループ名 → 頂点数)。
     * 「1 両で 17,704 頂点が半透明扱い」は窓にしては多すぎるため、本当にガラスなのか、
     * 不透明な内装/車体が誤って半透明に分類されているのかを名前で確かめる。
     */
    private static final java.util.Map<String, int[]> slowBatches = new java.util.HashMap<>();

    /**
     * グループごとの「実体バッチ数」。1 秒間に現れた異なるバッチインスタンスを数える。
     * 描画回数だけでは「小バッチが多数ある」のか「同じバッチを何度も描いている」のか区別できず、
     * 打つ手が正反対 (バッチ併合 vs 重複描画の除去) になるため、実体数も併せて数える。
     */
    private static final java.util.Map<String, java.util.Set<Integer>> slowBatchIds = new java.util.HashMap<>();

    public static void countSlowBatch(String groupName, int verts, int reason) {
        if (!overlayEnabled || groupName == null) {
            return;
        }
        String key = groupName + " [" + VBO_SKIP_NAMES[reason] + "]";
        int[] v = slowBatches.computeIfAbsent(key, k -> new int[2]);
        v[0] += verts;
        v[1]++;
    }

    /** #countSlowBatch と対で呼ぶ。batch の identity で実体数を数える。 */
    public static void countSlowBatchIdentity(String groupName, int reason, int identity) {
        if (!overlayEnabled || groupName == null) {
            return;
        }
        slowBatchIds.computeIfAbsent(groupName + " [" + VBO_SKIP_NAMES[reason] + "]",
            k -> new java.util.HashSet<>()).add(identity);
    }

    /** スナップショット時に上位を 1 度だけログへ出す (画面が狭いので詳細はログで見る)。 */
    private static void logSlowBatches(int frames) {
        if (slowBatches.isEmpty() || frames <= 0) {
            return;
        }
        java.util.List<java.util.Map.Entry<String, int[]>> list =
            new java.util.ArrayList<>(slowBatches.entrySet());
        list.sort((a, b) -> Integer.compare(b.getValue()[0], a.getValue()[0]));
        StringBuilder sb = new StringBuilder("[Perf] VBO を落ちたバッチ (頂点/frame 降順):");
        int shown = 0;
        for (java.util.Map.Entry<String, int[]> e : list) {
            if (shown++ >= 8) {
                break;
            }
            java.util.Set<Integer> ids = slowBatchIds.get(e.getKey());
            int distinct = ids == null ? 0 : ids.size();
            int drawsPerFrame = e.getValue()[1] / frames;
            sb.append("\n    ").append(e.getKey())
              .append("  ").append(e.getValue()[0] / frames).append(" 頂点/f")
              .append("  描画 ").append(drawsPerFrame).append(" 回/f")
              .append("  実体 ").append(distinct).append(" 個")
              // 実体数より描画回数が多い = 同じバッチを何度も描いている (重複描画)
              .append(distinct > 0 && drawsPerFrame > distinct
                  ? "  → 重複 x" + (drawsPerFrame / distinct) : "");
        }
        com.portofino.realtrainmodunofficial.RealTrainModUnofficial.LOGGER.info(sb.toString());
        slowBatches.clear();
        slowBatchIds.clear();
    }

    /**
     * 列車描画 (Train=約5ms/両) の内側の区間計測。
     * スクリプト/頂点数/ドローコール数は実測でシロだった (2026-07-20)。
     */
    public static final String[] SECTION_NAMES = {
        "lookup", "riders", "scripted", "baked", "signs",
        "record", "replay", "groups", "loop", "vbo", "getBuf", "submit",
        "tess", "modelGrp"
    };
    public static final int SEC_LOOKUP = 0;
    public static final int SEC_RIDERS = 1;
    public static final int SEC_SCRIPTED = 2;
    public static final int SEC_BAKED = 3;
    public static final int SEC_SIGNS = 4;
    public static final int SEC_RECORD = 5;
    public static final int SEC_REPLAY = 6;
    public static final int SEC_GROUPS = 7;
    public static final int SEC_LOOP = 8;
    public static final int SEC_VBO = 9;
    public static final int SEC_GETBUF = 10;
    public static final int SEC_SUBMIT = 11;
    /** replay 内の DRAW_TESS (drawTess = 方向幕/モニタ等の tessellator 描画)。 */
    public static final int SEC_TESS = 12;
    /**
     */
    public static final int SEC_MODELGRP = 13;

    private static final long[] sectionNs = new long[SECTION_NAMES.length];
    private static final int[] sectionCounts = new int[SECTION_NAMES.length];
    private static final long[] displaySectionNs = new long[SECTION_NAMES.length];
    private static final int[] displaySectionCounts = new int[SECTION_NAMES.length];

    /** 区間開始。計測 OFF 中は 0 を返し #secEnd は何もしない (通常プレイ無負荷)。 */
    public static long sec() {
        return overlayEnabled ? System.nanoTime() : 0L;
    }

    /** 区間終了。列車描画中 (#inTrain) のぶんだけ数える。 */
    public static void secEnd(int section, long startNs) {
        if (startNs == 0L || !inTrain) {
            return;
        }
        sectionNs[section] += System.nanoTime() - startNs;
        sectionCounts[section]++;
    }

    public static void countFrame() {
        framesThisSecond++;
    }

    private ClientRenderProfiler() {
    }

    public static void toggleOverlay() {
        overlayEnabled = !overlayEnabled;
        // スクリプト計測 (共通側カウンタ) も連動させる。計測点の ScriptUtil は共通コードなので
        // クライアント専用のこのクラスを直接参照できず、カウンタだけ別クラスに分けてある。
        com.portofino.realtrainmodunofficial.perf.RtmuProfiler.enabled = overlayEnabled;
        if (!overlayEnabled) {
            com.portofino.realtrainmodunofficial.perf.RtmuProfiler.reset();
        }
    }

    public static long begin() {
        return System.nanoTime();
    }

    public static void endRailOld(long startNs) {
        record(CATEGORY_RAIL_OLD, startNs);
    }

    public static void endRail(long startNs) {
        record(CATEGORY_RAIL, startNs);
    }

    public static void endTrain(long startNs) {
        record(CATEGORY_TRAIN, startNs);
    }

    public static void endInstalledObject(long startNs) {
        record(CATEGORY_OBJECT, startNs);
    }

    private static synchronized void record(int category, long startNs) {
        long elapsed = System.nanoTime() - startNs;
        totalsNs[category] += elapsed;
        counts[category]++;
        snapshotIfNeeded();
    }

    private static void snapshotIfNeeded() {
        long now = System.nanoTime();
        if (now - lastSnapshotNs < 1_000_000_000L) {
            return;
        }
        System.arraycopy(totalsNs, 0, displayTotalsNs, 0, totalsNs.length);
        System.arraycopy(counts, 0, displayCounts, 0, counts.length);
        for (int i = 0; i < totalsNs.length; i++) {
            totalsNs[i] = 0L;
            counts[i] = 0;
        }
        displayVertices = verticesThisSecond;
        displayBatches = batchesThisSecond;
        displayTrainVertices = trainVerticesThisSecond;
        displayTrainBatches = trainBatchesThisSecond;
        System.arraycopy(vboSkips, 0, displayVboSkips, 0, vboSkips.length);
        System.arraycopy(sectionNs, 0, displaySectionNs, 0, sectionNs.length);
        System.arraycopy(sectionCounts, 0, displaySectionCounts, 0, sectionCounts.length);
        java.util.Arrays.fill(sectionNs, 0L);
        java.util.Arrays.fill(sectionCounts, 0);
        displayFrames = framesThisSecond;
        logSlowBatches(framesThisSecond);
        displayRailMerged = railMergedThisSecond;
        displayRailFallback = railFallbackThisSecond;
        displayRailBake = railBakeThisSecond;
        displayRailStateSetup = railStateSetupThisSecond;
        displayRailVboDraw = railVboDrawThisSecond;
        verticesThisSecond = 0L;
        batchesThisSecond = 0L;
        trainVerticesThisSecond = 0L;
        trainBatchesThisSecond = 0L;
        java.util.Arrays.fill(vboSkips, 0);
        framesThisSecond = 0;
        railMergedThisSecond = 0;
        railFallbackThisSecond = 0;
        railBakeThisSecond = 0;
        railStateSetupThisSecond = 0;
        railVboDrawThisSecond = 0;
        lastSnapshotNs = now;

        // 画面のオーバーレイと同じ内容をログにも残す。
        // スクリーンショットを撮らなくても、ログを見れば負荷の内訳が分かるようにする。
        if (overlayEnabled) {
            logSnapshot();
        }
    }

    private static void logSnapshot() {
        StringBuilder sb = new StringBuilder("[Profiler]");
        for (int i = 0; i < CATEGORY_NAMES.length; i++) {
            double totalMs = displayTotalsNs[i] / 1_000_000.0D;
            double avgMs = displayCounts[i] > 0 ? totalMs / displayCounts[i] : 0.0D;
            sb.append(String.format(java.util.Locale.ROOT, " %s=%.2fms/%.3favg(%d)",
                CATEGORY_NAMES[i], totalMs, avgMs, displayCounts[i]));
        }
        long vertsPerFrame = displayFrames > 0 ? displayVertices / displayFrames : 0L;
        int setupPerFrame = displayFrames > 0 ? displayRailStateSetup / displayFrames : 0;
        int drawPerFrame = displayFrames > 0 ? displayRailVboDraw / displayFrames : 0;
        // Nashorn の実行時間だけを切り出したもの。「レールが重い」と言われたときに
        // スクリプトなのかそれ以外なのかを、ログだけで切り分けられるようにしておく。
        double scriptMsPerFrame = displayFrames > 0
            ? com.portofino.realtrainmodunofficial.perf.RtmuProfiler.lastScriptMillis() / displayFrames
            : 0.0D;
        sb.append(String.format(java.util.Locale.ROOT, " | script=%.2fms/f(%d)",
            scriptMsPerFrame, com.portofino.realtrainmodunofficial.perf.RtmuProfiler.lastScriptCalls()));
        sb.append(String.format(java.util.Locale.ROOT,
            " | fps=%d verts/f=%d | railMesh merged=%d fallback=%d bake=%d state/f=%d vbo/f=%d",
            displayFrames, vertsPerFrame,
            displayRailMerged, displayRailFallback, displayRailBake, setupPerFrame, drawPerFrame));
        com.portofino.realtrainmodunofficial.RealTrainModUnofficial.LOGGER.info(sb.toString());
        logTrainSections();
    }

    /**
     * Train区間の 1 秒スナップショットをログに出す。単位は全て ms/frame。
     * 「未計上」「判定他」の残差こそが見たい数字 (計測していない場所に犯人がいる)。
     */
    private static void logTrainSections() {
        if (displaySectionCounts[SEC_SCRIPTED] == 0 && displaySectionCounts[SEC_BAKED] == 0
            && displaySectionCounts[SEC_LOOP] == 0) {
            return;
        }
        double f = Math.max(1, displayFrames);
        double[] ms = new double[SECTION_NAMES.length];
        for (int i = 0; i < ms.length; i++) {
            ms[i] = displaySectionNs[i] / 1_000_000.0D / f;
        }
        double trainTotal = displayTotalsNs[CATEGORY_TRAIN] / 1_000_000.0D / f;
        // 親から子を引いた残差。負になったら入れ子の理解が間違っている合図なのでそのまま出す。
        double unattributed = trainTotal
            - (ms[SEC_LOOKUP] + ms[SEC_RIDERS] + ms[SEC_SCRIPTED] + ms[SEC_BAKED] + ms[SEC_SIGNS]);
        double scriptedOther = ms[SEC_SCRIPTED] - ms[SEC_RECORD] - ms[SEC_REPLAY];
        double replayOther = ms[SEC_REPLAY] - ms[SEC_GROUPS] - ms[SEC_TESS] - ms[SEC_MODELGRP];
        double groupsOther = ms[SEC_GROUPS] - ms[SEC_LOOP];
        double loopOther = ms[SEC_LOOP] - ms[SEC_VBO] - ms[SEC_GETBUF] - ms[SEC_SUBMIT];
        com.portofino.realtrainmodunofficial.RealTrainModUnofficial.LOGGER.info(String.format(
            java.util.Locale.ROOT,
            "[Profiler] Train区間 ms/f: 全体=%.2f | lookup=%.2f riders=%.2f scripted=%.2f baked=%.2f"
                + " signs=%.2f 未計上=%.2f | scripted内: record=%.2f replay=%.2f 他=%.2f"
                + " | replay内: groups=%.2f tess=%.2f(%d回/f) mGrp=%.2f(%d回/f) 姿勢他=%.2f"
                + " | groups内: loop=%.2f emissive他=%.2f"
                + " | loop内: vbo=%.2f(%d回/f) getBuf=%.2f(%d回/f) submit=%.2f(%d回/f) 判定他=%.2f",
            trainTotal, ms[SEC_LOOKUP], ms[SEC_RIDERS], ms[SEC_SCRIPTED], ms[SEC_BAKED],
            ms[SEC_SIGNS], unattributed, ms[SEC_RECORD], ms[SEC_REPLAY], scriptedOther,
            ms[SEC_GROUPS],
            ms[SEC_TESS], Math.round(displaySectionCounts[SEC_TESS] / f),
            ms[SEC_MODELGRP], Math.round(displaySectionCounts[SEC_MODELGRP] / f),
            replayOther, ms[SEC_LOOP], groupsOther,
            ms[SEC_VBO], Math.round(displaySectionCounts[SEC_VBO] / f),
            ms[SEC_GETBUF], Math.round(displaySectionCounts[SEC_GETBUF] / f),
            ms[SEC_SUBMIT], Math.round(displaySectionCounts[SEC_SUBMIT] / f),
            loopOther));
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || !overlayEnabled) {
            return;
        }

        countFrame();
        snapshotIfNeeded();

        GuiGraphics graphics = event.getGuiGraphics();
        Font font = mc.font;
        int x = 8;
        int y = 8;
        int lineHeight = font.lineHeight + 2;
        int width = 0;
        String[] lines = new String[CATEGORY_NAMES.length + 8];
        lines[0] = "Profiler [F8]";
        for (int i = 0; i < CATEGORY_NAMES.length; i++) {
            double totalMs = displayTotalsNs[i] / 1_000_000.0D;
            double avgMs = displayCounts[i] > 0 ? totalMs / displayCounts[i] : 0.0D;
            lines[i + 1] = CATEGORY_NAMES[i] + ": "
                + String.format(java.util.Locale.ROOT, "%.2f ms", totalMs)
                + " / "
                + String.format(java.util.Locale.ROOT, "%.2f avg", avgMs)
                + " (" + displayCounts[i] + ")";
        }
        // CPU が毎フレーム VertexConsumer に流している頂点数 (= 重さの正体)
        long vertsPerFrame = displayFrames > 0 ? displayVertices / displayFrames : 0L;
        long batchesPerFrame = displayFrames > 0 ? displayBatches / displayFrames : 0L;
        int setupPerFrame = displayFrames > 0 ? displayRailStateSetup / displayFrames : 0;
        int drawPerFrame = displayFrames > 0 ? displayRailVboDraw / displayFrames : 0;
        lines[CATEGORY_NAMES.length + 2] = "RailMesh: merged " + displayRailMerged
            + " / fallback " + displayRailFallback
            + " / bake " + displayRailBake
            + "  (state " + setupPerFrame + "/f, vbo " + drawPerFrame + "/f)";
        long trainVertsPerFrame = displayFrames > 0 ? displayTrainVertices / displayFrames : 0L;
        long trainBatchesPerFrame = displayFrames > 0 ? displayTrainBatches / displayFrames : 0L;
        lines[CATEGORY_NAMES.length + 1] = "Verts/frame: " + vertsPerFrame
            + "  (batches " + batchesPerFrame + ", fps " + displayFrames + ")"
            + "  [列車ぶん " + trainVertsPerFrame + " / batch " + trainBatchesPerFrame + "]";

        // ★スクリプト実行の内訳 (軽量化の主指標)。
        // Train カテゴリの時間には描画も含まれるため、Nashorn 実行だけを切り出して見る。
        double scriptMs = com.portofino.realtrainmodunofficial.perf.RtmuProfiler.avgScriptMillis();
        lines[CATEGORY_NAMES.length + 3] = "Script: "
            + String.format(java.util.Locale.ROOT, "%.2f ms/f", scriptMs)
            + String.format(java.util.Locale.ROOT, " (%.0f%% of 60fps)", scriptMs / 16.6D * 100.0D)
            + String.format(java.util.Locale.ROOT, ", %.1f calls/f",
                com.portofino.realtrainmodunofficial.perf.RtmuProfiler.avgScriptCalls());
        lines[CATEGORY_NAMES.length + 4] = "Cache: 車両 hit "
            + com.portofino.realtrainmodunofficial.perf.RtmuProfiler.lastVehicleCacheHits()
            + " / 実行 " + com.portofino.realtrainmodunofficial.perf.RtmuProfiler.lastVehicleCacheMisses()
            + "  設置物 hit " + com.portofino.realtrainmodunofficial.perf.RtmuProfiler.lastObjectCacheHits()
            + " / 実行 " + com.portofino.realtrainmodunofficial.perf.RtmuProfiler.lastObjectCacheMisses();
        // VBO 経路の内訳: 0 でないものだけを多い順に並べる (どのゲートで落ちているかの特定用)
        StringBuilder vbo = new StringBuilder("VBO: ");
        int shown = 0;
        for (int pass = 0; pass < VBO_SKIP_NAMES.length && shown < 4; pass++) {
            int best = -1;
            int bestCount = 0;
            for (int i = 0; i < displayVboSkips.length; i++) {
                if (displayVboSkips[i] > bestCount && !usedVboIndex[i]) {
                    bestCount = displayVboSkips[i];
                    best = i;
                }
            }
            if (best < 0) {
                break;
            }
            usedVboIndex[best] = true;
            vbo.append(VBO_SKIP_NAMES[best]).append(' ').append(bestCount).append("  ");
            shown++;
        }
        java.util.Arrays.fill(usedVboIndex, false);
        lines[CATEGORY_NAMES.length + 5] = vbo.toString();

        // Train区間の要約 (詳細はログの [Profiler] Train区間 行)。単位 ms/f。
        double secF = Math.max(1, displayFrames);
        double msScripted = displaySectionNs[SEC_SCRIPTED] / 1_000_000.0D / secF;
        double msRecord = displaySectionNs[SEC_RECORD] / 1_000_000.0D / secF;
        double msReplay = displaySectionNs[SEC_REPLAY] / 1_000_000.0D / secF;
        double msGroups = displaySectionNs[SEC_GROUPS] / 1_000_000.0D / secF;
        double msLoop = displaySectionNs[SEC_LOOP] / 1_000_000.0D / secF;
        double msVbo = displaySectionNs[SEC_VBO] / 1_000_000.0D / secF;
        double msGetBuf = displaySectionNs[SEC_GETBUF] / 1_000_000.0D / secF;
        double msSubmit = displaySectionNs[SEC_SUBMIT] / 1_000_000.0D / secF;
        double msTess = displaySectionNs[SEC_TESS] / 1_000_000.0D / secF;
        double msModelGrp = displaySectionNs[SEC_MODELGRP] / 1_000_000.0D / secF;
        lines[CATEGORY_NAMES.length + 6] = String.format(java.util.Locale.ROOT,
            "Train区間: scr %.2f (rec %.2f rep %.2f) grp %.2f tess %.2f mGrp %.2f",
            msScripted, msRecord, msReplay, msGroups, msTess, msModelGrp);
        lines[CATEGORY_NAMES.length + 7] = String.format(java.util.Locale.ROOT,
            "  loop %.2f = vbo %.2f + buf %.2f + sub %.2f + 判定 %.2f",
            msLoop, msVbo, msGetBuf, msSubmit, msLoop - msVbo - msGetBuf - msSubmit);

        // フレーム境界を確定 (この描画イベントは 1 フレームに 1 回)
        com.portofino.realtrainmodunofficial.perf.RtmuProfiler.endFrame();

        for (String line : lines) {
            width = Math.max(width, font.width(line));
        }

        graphics.fill(x - 4, y - 4, x + width + 6, y + lineHeight * lines.length + 2, 0x90000000);
        for (int i = 0; i < lines.length; i++) {
            graphics.drawString(font, lines[i], x, y + i * lineHeight, 0xFFFFFF, false);
        }
    }
}
