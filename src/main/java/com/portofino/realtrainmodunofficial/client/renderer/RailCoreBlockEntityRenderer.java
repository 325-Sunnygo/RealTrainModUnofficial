package com.portofino.realtrainmodunofficial.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.portofino.realtrainmodunofficial.blockentity.LargeRailCoreBlockEntity;
import com.portofino.realtrainmodunofficial.client.ClientRenderProfiler;
import com.portofino.realtrainmodunofficial.client.model.MqoModelLoader;
import com.portofino.realtrainmodunofficial.rail.RailDefinition;
import com.portofino.realtrainmodunofficial.rail.RailRegistry;
import com.portofino.realtrainmodunofficial.rail.util.RailMap;
import com.portofino.realtrainmodunofficial.rail.util.RailPosition;
import net.minecraft.util.Mth;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RailCoreBlockEntityRenderer implements BlockEntityRenderer<LargeRailCoreBlockEntity> {
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

    @Override
    public void render(LargeRailCoreBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay) {
        long profilerStart = ClientRenderProfiler.begin();
        // レールは不透明扱い(cutout)で描く。車体の半透明窓ガラスに奥のレールが遮蔽されて
        // 消える問題(中からガラス越しにレールが見えない)を防ぐため、半透明バッチも元テクスチャの
        // cutout として深度を書き込む。描画後に必ず解除する。
        MqoModelLoader.setRailCutoutMode(true);
        try {
            if (!be.isLoaded()) return;
            RailDefinition def = RailRegistry.getById(be.getRailDefinitionId());
            if (def == null) def = RailRegistry.getSelected();
            if (def == null) return;
            MqoModelLoader.MqoModel model = MqoModelLoader.loadModelForRail(def);
            if (model == null) return;
            boolean compatibilityHeavy = shouldUseCompatibilityRendering(def, model);
            RailMap[] maps = be.getAllRailMaps();
            if (maps.length == 0) return;
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
                com.portofino.realtrainmodunofficial.rail.util.Point[] points = be.getSwitchPoints();
                if (points != null && points.length > 0 && railModelHasSwitchParts(model)) {
                    // base/ballast/sleeper は本家同様 全 RailMap に沿って描画。マップ毎に微小Yオフセットを
                    // 与え、平行/収束区間で土台が同位置に重なって起きる z-fight(チカチカ)を防ぐ。
                    for (int mi = 0; mi < maps.length; mi++) {
                        if (maps[mi] != null) {
                            renderMapGroups(be, maps[mi], poseStack, buffer, packedLight, ox, oy, oz, mo, scale, model,
                                def, cameraDistanceSq, compatibilityHeavy, RailGroup.BASE, mi * 0.01F);
                        }
                    }
                    for (com.portofino.realtrainmodunofficial.rail.util.Point point : points) {
                        if (point != null) {
                            renderSwitchPoint(be, point, poseStack, buffer, packedLight, ox, oy, oz, mo, scale,
                                model, def, cameraDistanceSq, compatibilityHeavy);
                        }
                    }
                    return;
                }
                // フォールバック(Point 情報やパーツが無い): 本家 createRailPos 同様、全 RailMap を全描画。
                for (int mapIndex = 0; mapIndex < maps.length; mapIndex++) {
                    RailMap map = maps[mapIndex];
                    if (map != null) {
                        renderRailMap(be, map, mapIndex, layout, activeIndex, previousIndex, switchProgress,
                            poseStack, buffer, packedLight, ox, oy, oz, mo, scale, model, def, cameraDistanceSq, compatibilityHeavy,
                            null);
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
        } catch (Throwable t) {
            com.portofino.realtrainmodunofficial.RealTrainModUnofficial.LOGGER.warn("Skipping rail render at {} after renderer failure", be.getBlockPos(), t);
        } finally {
            MqoModelLoader.setRailCutoutMode(false);
            ClientRenderProfiler.endRail(profilerStart);
        }
    }

    private void renderRailMap(
        LargeRailCoreBlockEntity blockEntity,
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
            renderRailSample(
                blockEntity,
                sample.x,
                sample.y,
                sample.z,
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
            renderRailSample(
                blockEntity,
                sample.x,
                sample.y,
                sample.z,
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
        // railCutoutMode 中は不透明(cutout)パスが半透明バッチも元テクスチャで描くため、
        // 別途の半透明パスは不要(描くと同位置二重描画になる)。
        MqoModelLoader.renderModelWithoutScript(model, poseStack, buffer, packedLight,
            net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, false, filter, null);
        poseStack.popPose();
    }

    /** RailMap に沿って指定グループ(base/ballast 等)を全長描画する。 */
    private void renderMapGroups(LargeRailCoreBlockEntity be, RailMap map, PoseStack poseStack, MultiBufferSource buffer,
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
    private void renderSwitchPoint(LargeRailCoreBlockEntity be, com.portofino.realtrainmodunofficial.rail.util.Point point,
                                   PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                                   double ox, double oy, double oz, net.minecraft.world.phys.Vec3 mo, float scale,
                                   MqoModelLoader.MqoModel model, RailDefinition def, double cameraDistanceSq, boolean compatibilityHeavy) {
        if (point.branchDir == com.portofino.realtrainmodunofficial.rail.util.RailDir.NONE || point.rmBranch == null) {
            // 分岐なし区間: rmMain の半分(頂点→中間点)を両レールで描画。
            renderRailMapDynamic(be, point.rmMain, com.portofino.realtrainmodunofficial.rail.util.RailDir.NONE,
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
    private void renderRailMapDynamic(LargeRailCoreBlockEntity be, RailMap rms,
                                      com.portofino.realtrainmodunofficial.rail.util.RailDir dir, boolean par3, float move, int tongIndex,
                                      PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                                      double ox, double oy, double oz, net.minecraft.world.phys.Vec3 mo, float scale,
                                      MqoModelLoader.MqoModel model, RailDefinition def, double cameraDistanceSq, boolean compatibilityHeavy,
                                      float depthBias) {
        com.portofino.realtrainmodunofficial.rail.util.RailDir LEFT = com.portofino.realtrainmodunofficial.rail.util.RailDir.LEFT;
        com.portofino.realtrainmodunofficial.rail.util.RailDir RIGHT = com.portofino.realtrainmodunofficial.rail.util.RailDir.RIGHT;
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
            if (dir != com.portofino.realtrainmodunofficial.rail.util.RailDir.NONE && halfMax > 0) {
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
            // referenceIndex は LargeRailCoreBlockEntity で計算された active 番号なので
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
        LargeRailCoreBlockEntity blockEntity,
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
        LargeRailCoreBlockEntity blockEntity,
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
        // railCutoutMode 中は上の cutout パスが半透明バッチも元テクスチャで描画する。別途の
        // 半透明パスは不要(同位置二重描画になる)。これにより遠方/ガラス越しでもレールが消えない。
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
    public @NotNull AABB getRenderBoundingBox(LargeRailCoreBlockEntity be) {
        return be.getCachedRenderBounds();
    }

    @Override
    public boolean shouldRenderOffScreen(LargeRailCoreBlockEntity blockEntity) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 160;
    }
}
