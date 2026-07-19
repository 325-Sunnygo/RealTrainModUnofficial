package com.portofino.realtrainmodunofficial.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import jp.ngt.rtm.rail.TileEntityLargeRailCore;
import com.portofino.realtrainmodunofficial.client.ClientRenderProfiler;
import com.portofino.realtrainmodunofficial.client.model.MqoModelLoader;
import com.portofino.realtrainmodunofficial.rail.RailDefinition;
import com.portofino.realtrainmodunofficial.rail.RailRegistry;
import jp.ngt.rtm.rail.util.RailMap;
import jp.ngt.rtm.rail.util.RailPosition;
import net.minecraft.util.Mth;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RailCoreBlockEntityRenderer implements BlockEntityRenderer<TileEntityLargeRailCore> {
    private record RailSample(double x, double y, double z, float yaw, float pitch, float roll) {}
    private record RailCacheKey(BlockPos corePos, int mapId, int sampleMax) {}
    private static final Map<RailCacheKey, RailSample[]> SAMPLE_CACHE = new ConcurrentHashMap<>();
    /**
     * RTM本家の RailPartsRendererBase#createRailPos と同じ length*2 を基準にする。
     * Baru系は pos 番号で枕木・バラスト周期を決めるため、ここを増やすと部品間隔が詰まって壊れる。
     */
    private static int computeRailSampleMax(RailMap map, double length, RailDefinition definition, double cameraDistanceSq) {
        // RTM本家 RailPartsRendererBase#createRailPos と同じ length*2 固定。
        // 枕木・バラスト・レールの間隔は視点距離で変えない (ユーザー要望「視界でレールの間隔が
        // 変わる、変えないで」)。以前はカメラ距離で density / cap を下げて LOD していたが、
        // 近づくと枕木の数が変わって見えていた。距離非依存の固定密度・固定キャップにする。
        double density = 2.0D;
        int samples = Math.max(2, (int) Math.ceil(length * density));
        if (length < 2.5D) {
            samples = Math.min(samples, 12);
        }
        return Math.min(samples, 768);
    }

/**
     * 直線のみ: Baru 等は全サンプルでレールを描くため Z-fighting しやすい。極小の Y オフセットで深度を分散。
     * 曲線ではベジェと整合したサンプル間隔のためオフセットしない。
     */
    private static float depthJitter(int pos) {
        return ((pos & 15) - 7.5f) * 1.2e-6f;
    }

    private static int computeRenderStride(double cameraDistanceSq, boolean compatibilityHeavy) {
        // 枕木・バラストの周期が視点で変わらないことを優先する。
        return 1;
    }

    private enum RenderSwitchLayout {
        NONE,
        BASIC,
        SINGLE_CROSS,
        SCISSORS,
        DIAMOND
    }

    public RailCoreBlockEntityRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    /**
     * packedLight (block/sky 各 16bit) を成分ごとに max 合成
     */
    private static int maxPackedLight(int a, int b) {
        int blockA = a & 0xFFFF;
        int blockB = b & 0xFFFF;
        int skyA = (a >> 16) & 0xFFFF;
        int skyB = (b >> 16) & 0xFFFF;
        return (Math.max(skyA, skyB) << 16) | Math.max(blockA, blockB);
    }

    @Override
    public void render(TileEntityLargeRailCore be, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay) {
        long profilerStart = ClientRenderProfiler.begin();
        try {
            if (!be.isLoaded()) return;
            // レールがたまに真っ暗になる対策: ディスパッチャの packedLight はコア BE の
            // ブロック位置でサンプリングされるが、コアは道床/地面の中にあることが多く
            // 光量 0 になる。<b>ほぼ真っ暗な時だけ</b>レール上面 (1〜2 ブロック上) で取り直す。
            // 常に max 合成すると日陰・夕暮れでも空の明るさで描かれ、
            // 「周囲は暗いのにレールだけ明るい」不自然さが出る (ユーザー報告)。
            if (be.getLevel() != null) {
                int blockL = packedLight & 0xFFFF;
                int skyL = (packedLight >> 16) & 0xFFFF;
                if (blockL <= 16 && skyL <= 16) { //座標圧縮前で 1 段以下 = 埋没サンプリングとみなす
                    net.minecraft.core.BlockPos bp = be.getBlockPos();
                    int above1 = net.minecraft.client.renderer.LevelRenderer.getLightColor(be.getLevel(), bp.above());
                    int above2 = net.minecraft.client.renderer.LevelRenderer.getLightColor(be.getLevel(), bp.above(2));
                    packedLight = maxPackedLight(packedLight, maxPackedLight(above1, above2));
                }
            }
            RailDefinition def = RailRegistry.getById(be.getRailDefinitionId());
            if (def == null) def = RailRegistry.getSelected();
            if (def == null) return;
            MqoModelLoader.MqoModel model = MqoModelLoader.loadModelForRail(def);
            if (model == null) return;
            RailMap[] maps = be.getAllRailMaps();
            if (maps == null || maps.length == 0) return;

            //重ねレール (サブレール): 同じ RailMap 上に各サブレール定義のモデルを描く。メインより
            //先に描く (どちらも不透明なので順序は問題にならない)。サブレールが無い通常のレールは
            //この分岐に入らないので影響なし。
            if (be.subRails != null && !be.subRails.isEmpty()) {
                renderSubRails(be, maps, partialTick, poseStack, buffer, packedLight, packedOverlay);
            }

            // 本家式レール描画 (作り直し後の標準パス)。
            //  スクリプト付き: renderRailStatic をスクリプトが実行し、デフォルト配置は
            //  スクリプトが renderer.renderStaticParts を呼んだ時のみ (位置毎 shouldRenderObject
            //  = 端トリミング/枕木循環)。スクリプト無し: renderStaticParts 相当のみ。
            //  分岐コアのみ既存パイプラインへフォールバック (トング描画未移植)。
            com.portofino.realtrainmodunofficial.client.render.RailScriptRenderers.Scripted scripted =
                com.portofino.realtrainmodunofficial.client.render.RailScriptRenderers.get(def);
            if (scripted != null) {
                if (scripted.render(be, maps, partialTick, poseStack, buffer, packedLight, packedOverlay, model)) {
                    return;
                }
            } else if (com.portofino.realtrainmodunofficial.client.render.RailScriptRenderers.renderPlain(
                    be, maps, poseStack, buffer, packedLight, packedOverlay, model)) {
                return;
            }
            //ここから下は旧パイプライン。トング (可動レール) を持つ分岐コアだけがここに来る。
            //
            //かつてはここで毎フレーム「0.5m 刻みの位置ごとにモデルを描画呼び出し」していて、
            //実測 12.4ms/本。視界に 1 本あるだけでレール描画時間のほぼ全部を食っていた。
            //
            //そこでこの経路も統合メッシュに焼く。トングは動くが、動くのは転てつを切り替えた
            //一瞬だけなので、アニメ中だけ従来どおり逐次描画し、落ち着いたら焼いて使い回す。
            //焼いたメッシュは「開通方向」をキーにしているので、転てつすれば自動で焼き直る。
            long legacyStart = ClientRenderProfiler.begin();
            try {
                //★ 焼き直しのキーは「トングの位置」で決める。
                //  以前は開通方向 (activeIndex) をキーにしていたが、トングの位置は
                //  renderSwitchPoint が Point.getMovement() から読んでいるため、
                //  開通方向だけ見ても転てつを検知できず、焼いたメッシュが固まったままだった。
                //分岐トング付きレールも<b>常に焼き込み経路へ通す</b>。
                //
                //以前は「トングがアニメ中か」を movement が中間値かで判定し、中間値なら焼かず
                //毎フレーム逐次描画 (実測 12〜16ms/本) していた。ところが BaruRail の高架レール等は
                //トングが<b>中間値のまま静止</b>していて永遠に「アニメ中」と誤判定され、視界に数本
                //あるだけで fps が一桁になっていた (RailOld スパイクの真因)。
                //
                //焼き込みキー variant には Point.getMovement() (トング位置) が入っているので、
                //RailMeshCache は<b>トングが実際に動いた時だけ</b>焼き直す。さらに 1 フレームの
                //焼き込み本数は上限付き ({@link RailMeshCache#MAX_BAKES_PER_FRAME}) で分散され、
                //焼き直し待ちの間は直前のメッシュを描くので、静止分岐は焼いて使い回し (ほぼ0ms)、
                //実際にトングが動いている一瞬もコストが跳ねない。見た目は変わらない。
                long variant = switchVariantKey(be);
                RailDefinition bakedDef = def;
                //packedLight はこのメソッド内で再代入しているのでラムダに直接は渡せない
                final int bakedLight = packedLight;
                final long bakedVariant = variant;
                boolean forceRebuild = be.shouldRerenderRail;
                boolean drew = com.portofino.realtrainmodunofficial.client.render.RailMeshCache.drawBaked(
                    be.getBlockPos(), bakedVariant, model, poseStack, bakedLight, packedOverlay, forceRebuild,
                    (ps, buf) -> renderLegacy(be, bakedDef, model, maps, 1.0F, ps, buf,
                        bakedLight, packedOverlay));
                if (drew) {
                    be.shouldRerenderRail = false;
                    return;
                }
                renderLegacy(be, def, model, maps, partialTick, poseStack, buffer, packedLight, packedOverlay);
            } finally {
                ClientRenderProfiler.endRailOld(legacyStart);
            }
        } catch (Throwable t) {
            com.portofino.realtrainmodunofficial.RealTrainModUnofficial.LOGGER.warn("Skipping rail render at {} after renderer failure", be.getBlockPos(), t);
        } finally {
            ClientRenderProfiler.endRail(profilerStart);
        }
    }

    /**
     * 重ねレール (サブレール) をすべて描く。各サブレールはメインと同じ RailMap 上に、
     * サブレール定義<b>自身</b>のモデル+スクリプトで描かれる (RailScriptRenderers.renderSubRail)。
     * これが無いと、重ねレールは追加・保存されても描画されず「重ねても見た目が変わらない」状態になる。
     */
    private void renderSubRails(TileEntityLargeRailCore be, RailMap[] maps, float partialTick,
                                PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay) {
        for (jp.ngt.rtm.rail.util.RailProperty sub : be.subRails) {
            if (sub == null || sub.railModel == null || sub.railModel.isBlank()) {
                continue;
            }
            RailDefinition subDef = RailRegistry.getById(sub.railModel);
            if (subDef == null) {
                continue;
            }
            MqoModelLoader.MqoModel subModel = MqoModelLoader.loadModelForRail(subDef);
            if (subModel == null) {
                continue;
            }
            com.portofino.realtrainmodunofficial.client.render.RailScriptRenderers.renderSubRail(
                    be, maps, partialTick, poseStack, buffer, packedLight, packedOverlay, subDef, subModel);
        }
    }

    /**
     * 焼いたメッシュの「見た目の要因」。トングの位置が変われば別物として焼き直す。
     * <p>
     * {@code Point.getMovement()} は {@code moveCount / MAX_COUNT} で、レッドストーン入力に
     * 応じて 0 〜 1 を毎 tick 1 段ずつ動く。ここを見ないと転てつしてもメッシュが更新されない。
     */
    private static long switchVariantKey(TileEntityLargeRailCore be) {
        long key = 1L;
        jp.ngt.rtm.rail.util.Point[] points = be.getSwitchPoints();
        if (points != null) {
            for (jp.ngt.rtm.rail.util.Point p : points) {
                //getMovement() を 8 段階に量子化して混ぜる。トングは 80tick かけて動く
                //(1 段 = 1/80 = 0.0125) ので、RS 入力の揺れで moveCount が ±1 微振動しても
                //量子化値は変わらない。これをしないと、静止したはずのクロスポイント等が毎tick
                //微振動で variant が変わり、<b>再焼き込みされ続けて重い</b> (bake が下がらない)。
                //実際に転てつが動く時は 8 段階で焼き直すので見た目はほぼ同じ。
                int q = p == null ? 0 : Math.round(p.getMovement() * 8.0F);
                key = key * 31L + q;
            }
        }
        //Point を持たない分岐 (フォールバック描画) 用に開通方向も混ぜる
        key = key * 31L + be.getActiveSegmentIndex();
        key = key * 31L + be.getPreviousSegmentIndex();
        return key;
    }


    /**
     * 旧パイプライン本体 (トング付き分岐)。統合メッシュへの焼き込みからも、逐次描画からも
     * 同じコードが呼ばれる。渡された poseStack がレール原点基準であることが前提。
     */
    private void renderLegacy(TileEntityLargeRailCore be, RailDefinition def,
                              MqoModelLoader.MqoModel model, RailMap[] maps, float partialTick,
                              PoseStack poseStack, MultiBufferSource buffer,
                              int packedLight, int packedOverlay) {
            //compatibilityHeavy はこのパスでしか使わないので、早期 return の後で求める。
            boolean compatibilityHeavy = shouldUseCompatibilityRendering(def, model);
            net.minecraft.world.phys.Vec3 cameraPos = net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            double cameraDistanceSq = cameraPos.distanceToSqr(be.getBlockPos().getX() + 0.5, be.getBlockPos().getY() + 0.5, be.getBlockPos().getZ() + 0.5);

            BlockPos origin = be.getBlockPos();
            double ox = origin.getX();
            double oy = origin.getY();
            double oz = origin.getZ();
            net.minecraft.world.phys.Vec3 mo = def.getModelOffset();
            float scale = def.getModelScale();

            if (maps.length > 1) {
                RenderSwitchLayout layout = detectSwitchLayout(be.getRailPositions());
                int activeIndex = Mth.clamp(be.getActiveSegmentIndex(), 0, maps.length - 1);
                int previousIndex = Mth.clamp(be.getPreviousSegmentIndex(), 0, maps.length - 1);
                float switchProgress = be.getSwitchProgress(partialTick);
                // 本家RTM準拠の分岐描画(LibRenderRail.js)。Point があるレールは:
                //  ・base/ballast/sleeper は全 RailMap に沿って描画(地面なので重なりOK)
                //  ・レール本体(railL/railR)とトング(L0/L1/R0/R1)は Point 単位で半分ずつ + トング分離アニメ
                // これでレール本体が二重に重ならず、転てつ時にトングが動く(本家と同じ)。
                jp.ngt.rtm.rail.util.Point[] points = be.getSwitchPoints();
                if (points != null && points.length > 0 && railModelHasSwitchParts(model)) {
                    // base/ballast/sleeper は本家同様 全 RailMap に沿って描画。マップ毎に微小Yオフセットを
                    // 与え、平行/収束区間で土台が同位置に重なって起きる z-fight(チカチカ)を防ぐ。
                    for (int mi = 0; mi < maps.length; mi++) {
                        if (maps[mi] != null) {
                            renderMapGroups(be, maps[mi], poseStack, buffer, packedLight, ox, oy, oz, mo, scale, model,
                                def, cameraDistanceSq, compatibilityHeavy, RailGroup.BASE, mi * 0.01F);
                        }
                    }
                    for (jp.ngt.rtm.rail.util.Point point : points) {
                        if (point != null) {
                            renderSwitchPoint(be, point, poseStack, buffer, packedLight, ox, oy, oz, mo, scale,
                                model, def, cameraDistanceSq, compatibilityHeavy);
                        }
                    }
                    return;
                }
                // フォールバック(Point 情報やパーツが無い): 本家 createRailPos 同様、全 RailMap を全描画。
                // trimEnds=true: 端のサンプルを半ピース内側に寄せ、レール端からモデルが
                // 飛び出さないようにする (AR 系パックのダブルクロッシング対策)。
                for (int mapIndex = 0; mapIndex < maps.length; mapIndex++) {
                    RailMap map = maps[mapIndex];
                    if (map != null) {
                        renderRailMap(be, map, mapIndex, layout, activeIndex, previousIndex, switchProgress,
                            poseStack, buffer, packedLight, ox, oy, oz, mo, scale, model, def, cameraDistanceSq, compatibilityHeavy,
                            null, true);
                    }
                }
                return;
            }

            int activeIndex = Mth.clamp(be.getActiveSegmentIndex(), 0, maps.length - 1);
            RailMap activeMap = maps[activeIndex];
            if (activeMap != null) {
                renderRailMap(be, activeMap, activeIndex, RenderSwitchLayout.NONE, activeIndex, activeIndex, 1.0F,
                    poseStack, buffer, packedLight, ox, oy, oz, mo, scale, model, def, cameraDistanceSq, compatibilityHeavy, null);
            }
    }

    private void renderRailMap(
        TileEntityLargeRailCore blockEntity,
        RailMap map,
        int mapIndex,
        RenderSwitchLayout layout,
        int activeIndex,
        int previousIndex,
        float switchProgress,
        PoseStack poseStack,
        MultiBufferSource buffer,
        int packedLight,
        double ox,
        double oy,
        double oz,
        net.minecraft.world.phys.Vec3 mo,
        float scale,
        MqoModelLoader.MqoModel model,
        RailDefinition definition,
        double cameraDistanceSq,
        boolean compatibilityHeavy,
        RailSample[] primarySamples
    ) {
        renderRailMap(blockEntity, map, mapIndex, layout, activeIndex, previousIndex, switchProgress,
            poseStack, buffer, packedLight, ox, oy, oz, mo, scale, model, definition, cameraDistanceSq,
            compatibilityHeavy, primarySamples, false);
    }

    private void renderRailMap(
        TileEntityLargeRailCore blockEntity,
        RailMap map,
        int mapIndex,
        RenderSwitchLayout layout,
        int activeIndex,
        int previousIndex,
        float switchProgress,
        PoseStack poseStack,
        MultiBufferSource buffer,
        int packedLight,
        double ox,
        double oy,
        double oz,
        net.minecraft.world.phys.Vec3 mo,
        float scale,
        MqoModelLoader.MqoModel model,
        RailDefinition definition,
        double cameraDistanceSq,
        boolean compatibilityHeavy,
        RailSample[] primarySamples,
        boolean trimEnds
    ) {
        double length = map.getLength();
        if (length < 1.0e-4) {
            return;
        }

        int max = computeRailSampleMax(map, length, definition, cameraDistanceSq);
        RailSample[] samples = getOrCreateSamples(map, new BlockPos((int) ox, (int) oy, (int) oz), max);
        int stride = computeRenderStride(cameraDistanceSq, compatibilityHeavy);
        int[] clip = computeSwitchClip(map, mapIndex, layout, activeIndex, previousIndex, switchProgress, max);
        // 重なり除去: 基準ルート(primarySamples)と重なっている根元(先頭サンプル)は描かない。
        // ルート0と十分離れた(分岐した)位置から描くことで、トランクのレール二重描画を防ぐ。
        // 閾値は「ルート0とほぼ一致しているトランク部分」だけを消す小さめの値にする。大きいと
        // 分岐ルートがトランクから離れた所からしか描かれず、トランクと繋がらず「分岐に見えない」。
        int divergenceStart = primarySamples == null ? 0 : computeDivergenceStart(samples, primarySamples, 0.15);
        int startIndex = Math.min(Math.max(0, Math.max(clip[0], divergenceStart)), samples.length - 1);
        int endIndex = Math.max(startIndex, samples.length - 1 - Math.max(0, clip[1]));
        for (int i = startIndex; i <= endIndex; i += stride) {
            RailSample sample = samples[i];
            double[] p = trimmedSamplePos(samples, i, trimEnds);
            renderRailSample(
                blockEntity,
                p[0],
                p[1],
                p[2],
                sample.yaw,
                sample.pitch,
                sample.roll,
                i,
                max,
                poseStack,
                buffer,
                packedLight,
                ox,
                oy,
                oz,
                mo,
                scale,
                model,
                cameraDistanceSq,
                compatibilityHeavy
            );
        }
        if (endIndex > startIndex && (endIndex - startIndex) % stride != 0) {
            RailSample sample = samples[endIndex];
            double[] p = trimmedSamplePos(samples, endIndex, trimEnds);
            renderRailSample(
                blockEntity,
                p[0],
                p[1],
                p[2],
                sample.yaw,
                sample.pitch,
                sample.roll,
                endIndex,
                max,
                poseStack,
                buffer,
                packedLight,
                ox,
                oy,
                oz,
                mo,
                scale,
                model,
                cameraDistanceSq,
                compatibilityHeavy
            );
        }
    }

    /**
     * trimEnds 時、端 (先頭/末尾) のサンプルを半ピース (0.25m) 内側へ寄せた座標を返す。
     * ピース中心が端点に来るとモデルが半分レール外へ飛び出すため、端では面一にする。
     */
    private static double[] trimmedSamplePos(RailSample[] samples, int index, boolean trimEnds) {
        RailSample sample = samples[index];
        double sx = sample.x;
        double sy = sample.y;
        double sz = sample.z;
        if (trimEnds && samples.length >= 2 && (index == 0 || index == samples.length - 1)) {
            RailSample toward = samples[index == 0 ? 1 : samples.length - 2];
            double dx = toward.x - sx;
            double dy = toward.y - sy;
            double dz = toward.z - sz;
            double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (len > 1.0e-6) {
                double k = 0.25D / len;
                sx += dx * k;
                sy += dy * k;
                sz += dz * k;
            }
        }
        return new double[]{sx, sy, sz};
    }

    // ===== 本家RTM準拠の分岐レンダリング (LibRenderRail.js / RenderRailStandardHP.js 移植) =====
    private static final float TONG_MOVE = 0.35F;
    private static final float TONG_POS = 1.0F / 10.0F;
    private static final float HALF_GAUGE = 0.5647F;
    private static final float YAW_RATE = 450.0F;

    /** どのレールパーツ群を描画するか。 */
    private enum RailGroup { BASE, NON_BRANCH, LEFT, RIGHT, TONG_FL, TONG_BL, TONG_FR, TONG_BR }

    /** レールモデルが本家分岐パーツ(トング L0/L1/R0/R1 とレール railL/railR)を持つか。 */
    private static boolean railModelHasSwitchParts(MqoModelLoader.MqoModel model) {
        java.util.Set<String> groups = model.getAllNormalizedGroupNames();
        if (groups == null) return false;
        boolean hasTong = false, hasRail = false;
        for (String g : groups) {
            String n = g.toLowerCase(java.util.Locale.ROOT);
            if (n.equals("l0") || n.equals("l1") || n.equals("r0") || n.equals("r1")) hasTong = true;
            if (n.equals("raill") || n.equals("railr")) hasRail = true;
        }
        return hasTong && hasRail;
    }

    private static boolean matchesRailGroup(String groupName, RailGroup group) {
        if (groupName == null) return false;
        String n = groupName.toLowerCase(java.util.Locale.ROOT);
        switch (group) {
            case BASE:
                return n.contains("base") || n.contains("ballast") || n.contains("sleeper") || n.contains("guide");
            case NON_BRANCH: // 両レール+締結装置(分岐なし区間)
                return n.equals("raill") || n.equals("sidel") || n.equals("railr") || n.equals("sider")
                    || n.equals("springl") || n.equals("boltl") || n.equals("springr") || n.equals("boltr");
            case LEFT:
                return n.equals("raill") || n.equals("sidel");
            case RIGHT:
                return n.equals("railr") || n.equals("sider");
            case TONG_FL: return n.equals("l0");
            case TONG_BL: return n.equals("l1");
            case TONG_FR: return n.equals("r0");
            case TONG_BR: return n.equals("r1");
        }
        return false;
    }

    private static float sigmoid2(float x) {
        float d0 = x * 3.5F;
        float d1 = d0 / (float) Math.sqrt(1.0 + d0 * d0);
        return d1 * 0.75F + 0.25F;
    }

    /** モデルの指定グループだけを現在の poseStack で描画(不透明+半透明)。 */
    private void renderModelGroup(MqoModelLoader.MqoModel model, PoseStack poseStack, MultiBufferSource buffer,
                                  int packedLight, net.minecraft.world.phys.Vec3 mo, float scale, RailGroup group) {
        poseStack.pushPose();
        poseStack.translate(mo.x, mo.y, mo.z);
        poseStack.scale(scale, scale, scale);
        MqoModelLoader.GroupPredicate filter = g -> matchesRailGroup(g, group);
        MqoModelLoader.renderModelWithoutScript(model, poseStack, buffer, packedLight,
            net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, false, filter, null);
        if (model.hasTranslucentBatches()) {
            MqoModelLoader.renderModelWithoutScript(model, poseStack, buffer, packedLight,
                net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, true, filter, null);
        }
        poseStack.popPose();
    }

    /** RailMap に沿って指定グループ(base/ballast 等)を全長描画する。 */
    private void renderMapGroups(TileEntityLargeRailCore be, RailMap map, PoseStack poseStack, MultiBufferSource buffer,
                                 int packedLight, double ox, double oy, double oz, net.minecraft.world.phys.Vec3 mo, float scale,
                                 MqoModelLoader.MqoModel model, RailDefinition def, double cameraDistanceSq, boolean compatibilityHeavy,
                                 RailGroup group, float depthBias) {
        double length = map.getLength();
        if (length < 1.0e-4) return;
        int max = computeRailSampleMax(map, length, def, cameraDistanceSq);
        RailSample[] samples = getOrCreateSamples(map, new BlockPos((int) ox, (int) oy, (int) oz), max);
        int stride = computeRenderStride(cameraDistanceSq, compatibilityHeavy);
        for (int i = 0; i < samples.length; i += stride) {
            RailSample s = samples[i];
            poseStack.pushPose();
            poseStack.translate(s.x - ox, s.y - oy - 0.0625 + depthJitter(i) + depthBias, s.z - oz);
            poseStack.mulPose(Axis.YP.rotationDegrees(s.yaw));
            poseStack.mulPose(Axis.XP.rotationDegrees(-s.pitch));
            poseStack.mulPose(Axis.ZP.rotationDegrees(s.roll));
            renderModelGroup(model, poseStack, buffer, packedLight, mo, scale, group);
            poseStack.popPose();
        }
    }

    /** 本家 LibRenderRail.renderPoint 移植。Point ごとにレール本体+トングを描く。 */
    private void renderSwitchPoint(TileEntityLargeRailCore be, jp.ngt.rtm.rail.util.Point point,
                                   PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                                   double ox, double oy, double oz, net.minecraft.world.phys.Vec3 mo, float scale,
                                   MqoModelLoader.MqoModel model, RailDefinition def, double cameraDistanceSq, boolean compatibilityHeavy) {
        if (point.branchDir == jp.ngt.rtm.rail.util.RailDir.NONE || point.rmBranch == null) {
            // 分岐なし区間: rmMain の半分(頂点→中間点)を両レールで描画。
            renderRailMapDynamic(be, point.rmMain, jp.ngt.rtm.rail.util.RailDir.NONE,
                point.mainDirIsPositive, 0.0F, 0, poseStack, buffer, packedLight, ox, oy, oz, mo, scale, model, def, cameraDistanceSq, compatibilityHeavy, 0.0F);
            return;
        }
        float movement = point.getMovement();
        int tongIndex = (int) Math.floor(point.rmMain.getLength() * 2.0 * TONG_POS);
        float move = movement * TONG_MOVE;
        // rmMain は基準(bias 0)、rmBranch は分岐点付近で同位置に重なるため微小に持ち上げて z-fight 回避。
        renderRailMapDynamic(be, point.rmMain, point.branchDir, point.mainDirIsPositive, move, tongIndex,
            poseStack, buffer, packedLight, ox, oy, oz, mo, scale, model, def, cameraDistanceSq, compatibilityHeavy, 0.0F);
        move = (1.0F - movement) * TONG_MOVE;
        renderRailMapDynamic(be, point.rmBranch, point.branchDir.invert(), point.branchDirIsPositive, move, tongIndex,
            poseStack, buffer, packedLight, ox, oy, oz, mo, scale, model, def, cameraDistanceSq, compatibilityHeavy, 0.012F);
    }

    /** 本家 LibRenderRail.renderRailMapDynamic 移植。半分の区間+トング分離アニメ。 */
    private void renderRailMapDynamic(TileEntityLargeRailCore be, RailMap rms,
                                      jp.ngt.rtm.rail.util.RailDir dir, boolean par3, float move, int tongIndex,
                                      PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                                      double ox, double oy, double oz, net.minecraft.world.phys.Vec3 mo, float scale,
                                      MqoModelLoader.MqoModel model, RailDefinition def, double cameraDistanceSq, boolean compatibilityHeavy,
                                      float depthBias) {
        jp.ngt.rtm.rail.util.RailDir LEFT = jp.ngt.rtm.rail.util.RailDir.LEFT;
        jp.ngt.rtm.rail.util.RailDir RIGHT = jp.ngt.rtm.rail.util.RailDir.RIGHT;
        double railLength = rms.getLength();
        int max = computeRailSampleMax(rms, railLength, def, cameraDistanceSq);
        int halfMax = max / 2;
        // 中間点(halfMax)を隣の Point と二重描画して z-fight しないよう、後半側は halfMax+1 から開始。
        int startIndex = par3 ? 0 : halfMax + 1;
        int endIndex = par3 ? halfMax : max;
        RailSample[] samples = getOrCreateSamples(rms, new BlockPos((int) ox, (int) oy, (int) oz), max);
        boolean flip = (par3 && dir == LEFT) || (!par3 && dir == RIGHT);
        float dirFixture = flip ? -1.0F : 1.0F;
        for (int i = startIndex; i <= endIndex && i < samples.length; i++) {
            RailSample s = samples[i];
            poseStack.pushPose();
            // 本家 LibRenderRail と同じく yaw/pitch のみ(roll/カントは適用しない)。depthBias で
            // rmMain/rmBranch を微小に上下させ、分岐点付近の同位置 z-fight(チカチカ)を防ぐ。
            poseStack.translate(s.x - ox, s.y - oy - 0.0625 + depthJitter(i) + depthBias, s.z - oz);
            poseStack.mulPose(Axis.YP.rotationDegrees(s.yaw));
            poseStack.mulPose(Axis.XP.rotationDegrees(-s.pitch));
            // 分岐してない側のレール(開始位置が逆なら左右反対のパーツ)
            renderModelGroup(model, poseStack, buffer, packedLight, mo, scale, flip ? RailGroup.RIGHT : RailGroup.LEFT);
            if (dir != jp.ngt.rtm.rail.util.RailDir.NONE && halfMax > 0) {
                // トング分離: 分岐点側ほど開く(sigmoid)。move=0 で開通(密着)。
                float sep = (float) (par3 ? i : (max - i)) / (float) halfMax;
                sep = (1.0F - sigmoid2(sep)) * move * dirFixture;
                float halfGaugeMove = dirFixture * HALF_GAUGE;
                poseStack.translate(sep - halfGaugeMove, 0.0D, 0.0D);
                float yaw2 = sep * YAW_RATE / (float) railLength * (par3 ? -1.0F : 1.0F);
                poseStack.mulPose(Axis.YP.rotationDegrees(yaw2));
                poseStack.translate(halfGaugeMove, 0.0D, 0.0D);
                // 分岐してる側のレール/トング
                if (dir == LEFT) {
                    if (par3) {
                        if (i == tongIndex) renderModelGroup(model, poseStack, buffer, packedLight, mo, scale, RailGroup.TONG_BL);
                        else if (i > tongIndex) renderModelGroup(model, poseStack, buffer, packedLight, mo, scale, RailGroup.LEFT);
                    } else {
                        if (i == max - tongIndex) renderModelGroup(model, poseStack, buffer, packedLight, mo, scale, RailGroup.TONG_FR);
                        else if (i < max - tongIndex) renderModelGroup(model, poseStack, buffer, packedLight, mo, scale, RailGroup.RIGHT);
                    }
                } else { // RIGHT
                    if (par3) {
                        if (i == tongIndex) renderModelGroup(model, poseStack, buffer, packedLight, mo, scale, RailGroup.TONG_BR);
                        else if (i > tongIndex) renderModelGroup(model, poseStack, buffer, packedLight, mo, scale, RailGroup.RIGHT);
                    } else {
                        if (i == max - tongIndex) renderModelGroup(model, poseStack, buffer, packedLight, mo, scale, RailGroup.TONG_FL);
                        else if (i < max - tongIndex) renderModelGroup(model, poseStack, buffer, packedLight, mo, scale, RailGroup.LEFT);
                    }
                }
            } else {
                // 分岐なし区間: 反対側レールも描画(両レール)。
                renderModelGroup(model, poseStack, buffer, packedLight, mo, scale, flip ? RailGroup.LEFT : RailGroup.RIGHT);
            }
            poseStack.popPose();
        }
    }

    /**
     * RTM 互換: 分岐の非アクティブ方向をレールの「分岐点側」から徐々に切り詰めて
     * 「割れている」見た目を作る。
     * activeIndex / previousIndex / switchProgress で smoothstep 補間する。
     *
     * 戻り値は {clipFromStart, clipFromEnd} で、サンプル番号で何個分カットするかを表す。
     * pos=0 を分岐点側と仮定し、非アクティブ側は clipFromStart を増やしてレールの根本を隠す。
     */
    private static int[] computeSwitchClip(RailMap map, int mapIndex, RenderSwitchLayout layout,
                                           int activeIndex, int previousIndex, float switchProgress, int sampleMax) {
        if (layout == RenderSwitchLayout.NONE || sampleMax <= 0) {
            return new int[]{0, 0};
        }
        // DIAMOND は単純な交差で、両方向とも常に有効
        if (layout == RenderSwitchLayout.DIAMOND) {
            return new int[]{0, 0};
        }

        boolean active = isMapActiveForLayout(mapIndex, activeIndex, layout);
        boolean previouslyActive = isMapActiveForLayout(mapIndex, previousIndex, layout);

        float t = Mth.clamp(switchProgress, 0.0F, 1.0F);
        t = t * t * (3.0F - 2.0F * t); // smoothstep

        float clipRatio;
        if (active && previouslyActive) {
            clipRatio = 0.0F;
        } else if (active) {
            // 切り替わって有効になる: 切り詰め量 1→0
            clipRatio = 1.0F - t;
        } else if (previouslyActive) {
            // 有効から無効になる: 切り詰め量 0→1
            clipRatio = t;
        } else {
            clipRatio = 1.0F;
        }

        // 最大 70% まで切り詰め、根本付近で「割れて」見えるようにする
        int maxClip = Math.max(1, sampleMax * 7 / 10);
        int clipStart = Math.round(maxClip * clipRatio);
        return new int[]{clipStart, 0};
    }

    /**
     * samples の先頭から、primary(基準ルート)と重なっている区間の長さ(クリップ数)を返す。
     * samples[i] が primary のどのサンプルからも threshold より離れたら、そこが分岐開始点。
     * 分岐レールはこの位置から描けば、根元のトランクが基準ルートと二重に描かれない(本家RTM挙動)。
     */
    private static int computeDivergenceStart(RailSample[] samples, RailSample[] primary, double threshold) {
        if (samples == null || primary == null || samples.length == 0 || primary.length == 0) {
            return 0;
        }
        double t2 = threshold * threshold;
        for (int i = 0; i < samples.length; i++) {
            RailSample s = samples[i];
            double best = Double.MAX_VALUE;
            for (RailSample p : primary) {
                double dx = s.x - p.x;
                double dy = s.y - p.y;
                double dz = s.z - p.z;
                double d2 = dx * dx + dy * dy + dz * dz;
                if (d2 < best) {
                    best = d2;
                    if (best <= t2) break;
                }
            }
            if (best > t2) {
                return i; // ここから基準ルートと離れる=分岐開始
            }
        }
        // 全サンプルが基準ルートと重なる(=同一/逆向きルート)。ほぼ全クリップして二重描画を避ける。
        return samples.length - 1;
    }

    private static boolean isMapActiveForLayout(int mapIndex, int referenceIndex, RenderSwitchLayout layout) {
        if (layout == RenderSwitchLayout.SINGLE_CROSS) {
            // 0,1 = 通常 (シングルクロスのストレート), 2 = 渡り
            if (referenceIndex == 2) return mapIndex == 2;
            return mapIndex == 0 || mapIndex == 1;
        }
        if (layout == RenderSwitchLayout.SCISSORS) {
            // 信号 ON でストレート区間が有効、OFF で対角区間が有効。
            // referenceIndex は TileEntityLargeRailCore で計算された active 番号なので
            // そのままピンポイント比較する。
            return mapIndex == referenceIndex;
        }
        return mapIndex == referenceIndex;
    }

    private static RenderSwitchLayout detectSwitchLayout(RailPosition[] railPositions) {
        if (railPositions == null) {
            return RenderSwitchLayout.NONE;
        }
        int count = railPositions.length;
        int switchCount = 0;
        for (RailPosition rp : railPositions) {
            if (rp == null) {
                return RenderSwitchLayout.NONE;
            }
            if (rp.switchType == 1) {
                switchCount++;
            }
        }
        if (count == 4 && switchCount == 2) {
            return RenderSwitchLayout.BASIC;
        }
        if (count == 6 && switchCount == 4) {
            return RenderSwitchLayout.SINGLE_CROSS;
        }
        if (count == 8 && switchCount == 8) {
            return hasSameDirectionPair(railPositions) ? RenderSwitchLayout.SCISSORS : RenderSwitchLayout.DIAMOND;
        }
        if (count == 4 && switchCount == 4) {
            return RenderSwitchLayout.DIAMOND;
        }
        return RenderSwitchLayout.NONE;
    }

    private static boolean hasSameDirectionPair(RailPosition[] railPositions) {
        for (int i = 0; i < railPositions.length; i++) {
            RailPosition a = railPositions[i];
            if (a == null || a.switchType != 1) {
                continue;
            }
            for (int j = i + 1; j < railPositions.length; j++) {
                RailPosition b = railPositions[j];
                if (b != null && b.switchType == 1 && (a.direction & 7) == (b.direction & 7)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static RailSample[] getOrCreateSamples(RailMap map, BlockPos corePos, int sampleMax) {
        RailCacheKey key = new RailCacheKey(corePos, System.identityHashCode(map), sampleMax);
        return SAMPLE_CACHE.computeIfAbsent(key, unused -> {
            RailSample[] samples = new RailSample[sampleMax + 1];
            for (int i = 0; i <= sampleMax; i++) {
                double[] point = map.getRailPos(sampleMax, i);
                samples[i] = new RailSample(
                    point[1],
                    map.getRailHeight(sampleMax, i),
                    point[0],
                    map.getRailYaw(sampleMax, i),
                    map.getRailPitch(sampleMax, i),
                    map.getCant(sampleMax, i)
                );
            }
            return samples;
        });
    }

    private void renderInterpolatedMap(
        TileEntityLargeRailCore blockEntity,
        RailMap previousMap,
        RailMap activeMap,
        float progress,
        PoseStack poseStack,
        MultiBufferSource buffer,
        int packedLight,
        double ox,
        double oy,
        double oz,
        net.minecraft.world.phys.Vec3 mo,
        float scale,
        MqoModelLoader.MqoModel model,
        RailDefinition definition,
        double cameraDistanceSq,
        boolean compatibilityHeavy
    ) {
        int previousMax = computeRailSampleMax(previousMap, previousMap.getLength(), definition, cameraDistanceSq);
        int activeMax = computeRailSampleMax(activeMap, activeMap.getLength(), definition, cameraDistanceSq);
        int max = Math.max(previousMax, activeMax);
        int stride = computeRenderStride(cameraDistanceSq, compatibilityHeavy);
        for (int i = 0; i <= max; i += stride) {
            float t = max <= 0 ? 0.0F : i / (float) max;
            int previousIndex = Mth.clamp(Math.round(t * previousMax), 0, previousMax);
            int activeIndex = Mth.clamp(Math.round(t * activeMax), 0, activeMax);
            double[] previousPoint = previousMap.getRailPos(previousMax, previousIndex);
            double[] activePoint = activeMap.getRailPos(activeMax, activeIndex);
            double wx = Mth.lerp(progress, previousPoint[1], activePoint[1]);
            double wy = Mth.lerp(progress, previousMap.getRailHeight(previousMax, previousIndex), activeMap.getRailHeight(activeMax, activeIndex));
            double wz = Mth.lerp(progress, previousPoint[0], activePoint[0]);
            float yaw = Mth.rotLerp(progress, previousMap.getRailYaw(previousMax, previousIndex), activeMap.getRailYaw(activeMax, activeIndex));
            float pitch = Mth.rotLerp(progress, previousMap.getRailPitch(previousMax, previousIndex), activeMap.getRailPitch(activeMax, activeIndex));
            float roll = Mth.rotLerp(progress, previousMap.getCant(previousMax, previousIndex), activeMap.getCant(activeMax, activeIndex));
            renderRailSample(blockEntity, wx, wy, wz, yaw, pitch, roll, i, max, poseStack, buffer, packedLight, ox, oy, oz, mo, scale, model, cameraDistanceSq, compatibilityHeavy);
        }
        if (max > 0 && max % stride != 0) {
            double[] previousPoint = previousMap.getRailPos(previousMax, previousMax);
            double[] activePoint = activeMap.getRailPos(activeMax, activeMax);
            double wx = Mth.lerp(progress, previousPoint[1], activePoint[1]);
            double wy = Mth.lerp(progress, previousMap.getRailHeight(previousMax, previousMax), activeMap.getRailHeight(activeMax, activeMax));
            double wz = Mth.lerp(progress, previousPoint[0], activePoint[0]);
            float yaw = Mth.rotLerp(progress, previousMap.getRailYaw(previousMax, previousMax), activeMap.getRailYaw(activeMax, activeMax));
            float pitch = Mth.rotLerp(progress, previousMap.getRailPitch(previousMax, previousMax), activeMap.getRailPitch(activeMax, activeMax));
            float roll = Mth.rotLerp(progress, previousMap.getCant(previousMax, previousMax), activeMap.getCant(activeMax, activeMax));
            renderRailSample(blockEntity, wx, wy, wz, yaw, pitch, roll, max, max, poseStack, buffer, packedLight, ox, oy, oz, mo, scale, model, cameraDistanceSq, compatibilityHeavy);
        }
    }

    private void renderRailSample(
        TileEntityLargeRailCore blockEntity,
        double wx,
        double wy,
        double wz,
        float yaw,
        float pitch,
        float roll,
        int pos,
        int max,
        PoseStack poseStack,
        MultiBufferSource buffer,
        int packedLight,
        double ox,
        double oy,
        double oz,
        net.minecraft.world.phys.Vec3 mo,
        float scale,
        MqoModelLoader.MqoModel model,
        double cameraDistanceSq,
        boolean compatibilityHeavy
    ) {
        //本家 1.7.10 の TESR と同じく「レールのその区間の位置」の明るさで描く。
        //コア 1 点の明るさ (常に埋没 → 上ブロック=空の明るさで救済) を全長に使うと、
        //日陰・夜間・トンネル外でレールだけ明るく浮く。区間ごとにレール面すぐ上で
        //サンプリングし、完全に真っ暗 (ブロック内サンプリング) の時だけ 1 つ上で救済。
        if (blockEntity.getLevel() != null) {
            net.minecraft.core.BlockPos sp = net.minecraft.core.BlockPos.containing(wx, wy + 0.25D, wz);
            int segLight = net.minecraft.client.renderer.LevelRenderer.getLightColor(blockEntity.getLevel(), sp);
            if (segLight == 0) {
                segLight = net.minecraft.client.renderer.LevelRenderer.getLightColor(blockEntity.getLevel(), sp.above());
            }
            packedLight = segLight;
        }
        poseStack.pushPose();
        float yBump = depthJitter(pos);
        poseStack.translate(wx - ox, wy - oy - 0.0625 + yBump, wz - oz);
        poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(-pitch));
        poseStack.mulPose(Axis.ZP.rotationDegrees(roll));
        poseStack.translate(mo.x, mo.y, mo.z);
        poseStack.scale(scale, scale, scale);
        MqoModelLoader.renderModelWithoutScript(
            model,
            poseStack,
            buffer,
            packedLight,
            net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,
            false,
            groupName -> shouldRenderRailGroup(model, groupName, pos, max, compatibilityHeavy, cameraDistanceSq),
            null
        );
        double translucentThreshold = compatibilityHeavy ? 38.0D : 72.0D;
        if (model.hasTranslucentBatches() && cameraDistanceSq < translucentThreshold * translucentThreshold) {
            MqoModelLoader.renderModelWithoutScript(
                model,
                poseStack,
                buffer,
                packedLight,
                net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,
                true,
                groupName -> shouldRenderRailGroup(model, groupName, pos, max, compatibilityHeavy, cameraDistanceSq),
                null
            );
        }
        poseStack.popPose();
    }

    private static boolean shouldRenderRailGroup(MqoModelLoader.MqoModel model, String groupName, int pos, int max,
                                                 boolean compatibilityHeavy, double cameraDistanceSq) {
        String lower = groupName == null ? "" : groupName.toLowerCase(java.util.Locale.ROOT);
        if (lower.matches("[lr][0-9]+")) {
            return false;
        }
        if (lower.startsWith("ballast")) {
            int ballastIndex = parseTrailingNumber(lower);
            if (ballastIndex <= 0) {
                return true;
            }
            if (model != null && model.hasGroupNamed("ballast04")) {
                return ballastIndex == (pos % 16) + 1;
            }
            if (model != null && model.hasGroupNamed("ballast03")) {
                if (pos % 10 == 0) {
                    return ballastIndex == 2;
                }
                if ((pos + 1) % 10 == 0) {
                    return ballastIndex == 3;
                }
                return ballastIndex == 1;
            }
            return ballastIndex == 1;
        }
        if (lower.startsWith("sleeper_point")) {
            return false;
        }
        if (lower.equals("ladder")) {
            return (pos + 1) % 10 == 0 || (pos + 5) % 10 == 0 || (pos + 9) % 10 == 0;
        }
        if (compatibilityHeavy) {
            if (lower.contains("glass")
                || lower.contains("alpha")
                || lower.contains("window")) {
                return false;
            }
            if (cameraDistanceSq > 2500.0D && (lower.contains("detail")
                || lower.contains("bolt")
                || lower.contains("plate")
                || lower.contains("side"))) {
                return false;
            }
        }
        boolean endpoint = pos == 0 || pos == max;
        // 一部パックは end/cap 名で端面パーツを持つ。中間で出すと長い先端が飛び出す。
        if (lower.contains("end") || lower.contains("cap") || lower.contains("terminal")) {
            return endpoint;
        }
        return true;
    }

    private static boolean shouldUseCompatibilityRendering(RailDefinition definition, MqoModelLoader.MqoModel model) {
        if (definition == null || model == null) {
            return false;
        }
        return model.getTotalVertexCount() >= 9_000
            || model.getBatchCount() >= 72
            || model.getTranslucentBatchCount() >= 10;
    }

    private static int parseTrailingNumber(String value) {
        int end = value.length();
        int start = end;
        while (start > 0 && Character.isDigit(value.charAt(start - 1))) {
            start--;
        }
        if (start == end) {
            return -1;
        }
        try {
            return Integer.parseInt(value.substring(start, end));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    @Override
    public @NotNull AABB getRenderBoundingBox(TileEntityLargeRailCore be) {
        return be.getCachedRenderBounds();
    }

    @Override
    public boolean shouldRenderOffScreen(TileEntityLargeRailCore blockEntity) {
        //true 必須: false だとコアブロックのあるチャンクセクションが視界から外れた瞬間に
        //レール全体が消える (BE はセクション単位でカリングされるため)。本家 1.7.10 も
        //ignoreFrustumCheck 相当で常時描画 (「レールはその場にある」)。
        return true;
    }

    @Override
    public int getViewDistance() {
        //RTMU 設定でレール描画距離を変更可能 (既定 128)。「レールの描画が短い」対策。
        return com.portofino.realtrainmodunofficial.RtmuSettings.clampRailRenderDistance(
            com.portofino.realtrainmodunofficial.RtmuSettings.railRenderDistance);
    }
}
