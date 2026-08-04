package com.portofino.realtrainmodunofficial.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.portofino.realtrainmodunofficial.blockentity.InstalledObjectBlockEntity;
import com.portofino.realtrainmodunofficial.client.ClientRenderProfiler;
import com.portofino.realtrainmodunofficial.client.model.MqoModelLoader;
import com.portofino.realtrainmodunofficial.client.signboard.SignboardTextRenderer;
import com.portofino.realtrainmodunofficial.client.signboard.SolidTexture;
import com.portofino.realtrainmodunofficial.installedobject.InstalledObjectCategory;
import com.portofino.realtrainmodunofficial.installedobject.InstalledObjectDefinition;
import com.portofino.realtrainmodunofficial.installedobject.InstalledObjectRegistry;
import com.portofino.realtrainmodunofficial.signboard.SignboardText;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.joml.Matrix4f;

public class InstalledObjectBlockEntityRenderer implements BlockEntityRenderer<InstalledObjectBlockEntity> {
    /** 本家 SignalLevel.HIGH_SPEED_PROCEED.level — 現示の上限。 */
    private static final int MAX_SIGNAL_LEVEL = 6;
    /** 点灯用テクスチャをそのままの色で全光量表示する (色付けしない)。 */
    private static final int[] SIGNAL_LIT_COLOR = {255, 255, 255, 255};
    private static final Set<String> GREEN_GROUPS = Set.of("light1", "light2");
    private static final Set<String> YELLOW_GROUPS = Set.of("light3", "light5");
    private static final Set<String> RED_GROUPS = Set.of("light4");
    private static final Set<String> CROSSING_SCRIPT_ONLY_GROUPS = Set.of("light_l", "light_r");
    private static final List<String> CROSSING_LIGHT_LEFT = List.of("light_l", "lightl", "light-left", "lightleft", "lighta", "light_a");
    private static final List<String> CROSSING_LIGHT_RIGHT = List.of("light_r", "lightr", "light-right", "lightright", "lightb", "light_b");
    private static final List<String> CROSSING_LIGHT_LEFT_LEGACY = List.of("light1");
    private static final List<String> CROSSING_LIGHT_RIGHT_LEGACY = List.of("light2");
    private static final List<String> CROSSING_LIGHT_COMMON_LEGACY = List.of("light3");
    private static final Map<String, Long> FAILED_RENDER_UNTIL_NANOS = new ConcurrentHashMap<>();
    /** 信号の点灯用テクスチャ差し替えマップ (定義ID → overrides)。毎フレームの Map 生成を避ける。 */
    private static final Map<String, Map<String, String>> LIGHT_TEXTURE_OVERRIDES = new ConcurrentHashMap<>();

    public InstalledObjectBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(InstalledObjectBlockEntity blockEntity, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {
        long profilerStart = ClientRenderProfiler.begin();
        InstalledObjectDefinition definition = InstalledObjectRegistry.getById(blockEntity.getDefinitionId());
        if (definition == null) {
            ClientRenderProfiler.endInstalledObject(profilerStart);
            return;
        }
        Long failedUntil = FAILED_RENDER_UNTIL_NANOS.get(definition.getId());
        if (failedUntil != null) {
            if (System.nanoTime() < failedUntil) {
                ClientRenderProfiler.endInstalledObject(profilerStart);
                return;
            }
            FAILED_RENDER_UNTIL_NANOS.remove(definition.getId(), failedUntil);
        }
        Vec3 cameraPos = net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        Vec3 center = blockEntity.getRenderCenter();
        double cameraDistanceSq = cameraPos.distanceToSqr(center);
        // ワイヤー: wireStart/wireEnd があればケーブル(ジオメトリ)を描く。
        // ケーブルのみ(中間の設置物モデルは出さない)。
        if (blockEntity.getWireStart() != null && blockEntity.getWireEnd() != null) {
            renderWire(blockEntity, definition, poseStack, buffer, cameraDistanceSq, cameraPos, packedLight, packedOverlay);
            if (blockEntity.getCategory() == InstalledObjectCategory.WIRE) {
                ClientRenderProfiler.endInstalledObject(profilerStart);
                return;
            }
        }
        if (definition.getModelFile() != null && !definition.getModelFile().isBlank()) {
            MqoModelLoader.MqoModel model = MqoModelLoader.loadModelFromPack(
                definition.getPackName(),
                definition.getModelFile(),
                definition.getTextureOverrides(),
                definition.getScriptPath(),
                definition.isSmoothing()
            );
            if (model != null) {
                boolean pushed = false;
                try {
                    boolean compatibilityHeavy = shouldUseCompatibilityRendering(definition, model);
                    boolean customCrossingGateRendering = shouldUseCustomCrossingGateRendering(blockEntity, definition);
                    double farThreshold = compatibilityHeavy ? 56.0D : 80.0D;
                    double veryFarThreshold = compatibilityHeavy ? 96.0D : 140.0D;
                    double translucentThreshold = compatibilityHeavy ? 44.0D : 72.0D;
                    boolean far = cameraDistanceSq > farThreshold * farThreshold;
                    boolean veryFar = cameraDistanceSq > veryFarThreshold * veryFarThreshold;
                    poseStack.pushPose();
                    pushed = true;
                    if (blockEntity.getCategory() == InstalledObjectCategory.FLUORESCENT
                            || blockEntity.getCategory() == InstalledObjectCategory.OVERHEAD_LINE_POLE
                            || blockEntity.getCategory() == InstalledObjectCategory.PIPE) {
                        // 本家 RenderOrnament: 飾り物はブロック中心を原点にするだけで回転しない。
                        // 蛍光灯: 取付方向 (0..7) に応じた ±0.4375 の寄せと Y90度回転は
                        // RenderFluorescent.js が entity.getDir を見て自分で行う。
                        poseStack.translate(0.5D, 0.5D, 0.5D);
                        Vec3 renderOffset = blockEntity.getRenderOffset();
                        poseStack.translate(renderOffset.x, renderOffset.y, renderOffset.z);
                        applyAdjustments(poseStack, blockEntity);
                        poseStack.translate(definition.getModelOffset().x, definition.getModelOffset().y, definition.getModelOffset().z);
                        poseStack.scale(definition.getModelScale(), definition.getModelScale(), definition.getModelScale());
                    } else if (definition.isRotateByMetadata()) {
                        // 本家 RenderMachine (rotateByMetadata=true の照明 = サーチライト等) の移植:
                        // ブロック垂直中心 (+0.5) を軸に meta(クリック面 0-5) で回し、-0.5 で戻してから
                        // プレイヤー向き (getYaw) を掛ける。汎用の getMountFace 分岐 (碍子の面回転) とは別物。
                        poseStack.translate(0.5D, 0.0D, 0.5D);
                        Vec3 renderOffset = blockEntity.getRenderOffset();
                        poseStack.translate(renderOffset.x, renderOffset.y, renderOffset.z);
                        applyAdjustments(poseStack, blockEntity);
                        poseStack.translate(0.0D, 0.5D, 0.0D);
                        // 取り付け面を持たない (この修正より前に置かれた) 物は、本家の既定である
                        // meta=1 (上向き=面回転なし) として扱う。置き直さなくても本家の配置になる。
                        int mountFace = blockEntity.getMountFace() >= 0 ? blockEntity.getMountFace() : 1;
                        applyLightMetadataRotation(poseStack, mountFace);
                        poseStack.translate(0.0D, -0.5D, 0.0D);
                        // 本家 getRotation = round(180 - playerYaw)。RTMU の yaw は playerYaw なので
                        // YP(180 - yaw) が本家 rotate(getRotation) と一致する。meta==0 は本家同様に反転。
                        // 本家 RenderMachine: 保存してある rotationYaw をそのまま回す。
                        // ★ここで 180 - yaw としてはいけない。設置時の honkeRotation が
                        // 既に本家の式 (-playerYaw + 180 の量子化) で保存しているので、
                        // 二重に補正することになり、しかも 180-yaw は回転ではなく鏡映なので
                        // 向きによって反対を向く。
                        float lightYaw = blockEntity.getYaw();
                        if (mountFace == 0) {
                            lightYaw = -lightYaw;
                        }
                        poseStack.mulPose(Axis.YP.rotationDegrees(lightYaw));
                        poseStack.translate(definition.getModelOffset().x, definition.getModelOffset().y, definition.getModelOffset().z);
                        poseStack.scale(definition.getModelScale(), definition.getModelScale(), definition.getModelScale());
                    } else if (blockEntity.getMountFace() >= 0) {
                        // 本家 RenderElectricalWiring (碍子/コネクタ) 準拠:
                        // ブロック中心 (+0.5,+0.5,+0.5) を基準に、クリック面 (meta 0-5) で回転。
                        poseStack.translate(0.5D, 0.5D, 0.5D);
                        Vec3 renderOffset = blockEntity.getRenderOffset();
                        poseStack.translate(renderOffset.x, renderOffset.y, renderOffset.z);
                        applyAdjustments(poseStack, blockEntity);
                        applyHonkeMountFaceRotation(poseStack, blockEntity.getMountFace());
                        poseStack.translate(definition.getModelOffset().x, definition.getModelOffset().y, definition.getModelOffset().z);
                        poseStack.scale(definition.getModelScale(), definition.getModelScale(), definition.getModelScale());
                    } else {
                        poseStack.translate(0.5D, 0.0D, 0.5D);
                        Vec3 renderOffset = blockEntity.getRenderOffset();
                        poseStack.translate(renderOffset.x, renderOffset.y, renderOffset.z);
                        applyAdjustments(poseStack, blockEntity);
                        // 保存値がそのまま本家の rotationYaw (上の照明分岐と同じ理由)
                        poseStack.mulPose(Axis.YP.rotationDegrees(blockEntity.getYaw()));
                        // 壁挿し碍子は横倒し(mountPitch)にする。0なら通常の縦置き。
                        // 列車検知器ではレールの勾配(mountPitch)とカント(mountRoll)になる。
                        if (blockEntity.getMountPitch() != 0.0F) {
                            poseStack.mulPose(Axis.XP.rotationDegrees(blockEntity.getMountPitch()));
                        }
                        if (blockEntity.getMountRoll() != 0.0F) {
                            poseStack.mulPose(Axis.ZP.rotationDegrees(blockEntity.getMountRoll()));
                        }
                        poseStack.translate(definition.getModelOffset().x, definition.getModelOffset().y, definition.getModelOffset().z);
                        poseStack.scale(definition.getModelScale(), definition.getModelScale(), definition.getModelScale());
                    }
                    // 踏切/改札: 本家式スクリプト描画 (MachinePartsRenderer + Nashorn)。成功時は旧近似パスをスキップ。
                    // 改札は本家 RenderTurnstile01.js が getMovingCount(entity)>0 で扉を回す (開閉アニメ)。
                    boolean machineScriptCategory = blockEntity.getCategory() == InstalledObjectCategory.CROSSING
                            || blockEntity.getCategory() == InstalledObjectCategory.TICKET_GATE
                            || blockEntity.getCategory() == InstalledObjectCategory.SIGNAL
                            || blockEntity.getCategory() == InstalledObjectCategory.FLUORESCENT
                            || blockEntity.getCategory() == InstalledObjectCategory.OVERHEAD_LINE_POLE
                            || blockEntity.getCategory() == InstalledObjectCategory.PIPE
                            || blockEntity.getCategory() == InstalledObjectCategory.POINT
                            // ★照明もここに含める。本家の RenderMirrorBall.js / RenderRevolvingLight.js は
                            // renderClass に MachinePartsRenderer を指定していて、この経路でしか走らない。
                            // 以前は「ブロック検知スクリプトのときだけ」に絞っていたため、ミラーボールや
                            // 赤色灯はスクリプトが一切実行されず、素モデルが描かれるだけで回らなかった。
                            || blockEntity.getCategory() == InstalledObjectCategory.LIGHT;
                    boolean hasMachineScript = definition.getScriptPath() != null && !definition.getScriptPath().isBlank();
                    // これらのブロック検知信号パックは json の machineType が "Light" のため SIGNAL でなく
                    // LIGHT に分類され、本来スクリプト経路に載らず素モデルで全レンズが描かれていた(=複数点灯)。
                    // → LIGHT でも「ブロック検知スクリプト(searchBlockAndMeta)」のときだけスクリプト経路に載せる。
                    com.portofino.realtrainmodunofficial.client.render.MachineScriptRenderers.Scripted machineScripted =
                        (hasMachineScript && (machineScriptCategory
                            || blockEntity.getCategory() == InstalledObjectCategory.LIGHT))
                            ? com.portofino.realtrainmodunofficial.client.render.MachineScriptRenderers.get(definition)
                            : null;
                    boolean useMachineScript = machineScripted != null
                            && (machineScriptCategory
                                || (blockEntity.getCategory() == InstalledObjectCategory.LIGHT
                                    && machineScripted.isBlockDetection()));
                    if (useMachineScript
                                && machineScripted.render(blockEntity, partialTick, poseStack, buffer, packedLight, packedOverlay, model)) {
                            // 警報灯/現示灯の発光オーバーレイ (スクリプトの pass2 は diffuse で減光する
                            // ことがあるため、ここで確実に全光量の発光を重ねる)。
                            // 信号はスクリプトが点灯パーツを描いても素のテクスチャ (消灯レンズ) のままなので、
                            // 点灯用テクスチャを貼った現示灯をここで重ねる。
                            boolean scriptDrivenSignal = blockEntity.getCategory() == InstalledObjectCategory.SIGNAL
                                    && machineScripted.isBlockDetection();
                            // ★踏切には overlay を掛けない。
                            // Light テクスチャで光らせる (MachineScriptRenderers が本家どおり再生する)。
                            if (blockEntity.getCategory() == InstalledObjectCategory.SIGNAL
                                    && !scriptDrivenSignal) {
                                renderActiveLights(blockEntity, definition, poseStack, buffer, packedOverlay);
                            }
                            poseStack.popPose();
                            pushed = false;
                            ClientRenderProfiler.endInstalledObject(profilerStart);
                            return;
                        }
                    MqoModelLoader.GroupPredicate filter = groupName ->
                        shouldRenderDefinedObjectGroup(groupName, definition)
                            && (!(far || compatibilityHeavy || customCrossingGateRendering)
                                || shouldRenderInstalledObjectGroup(groupName, blockEntity, definition, cameraDistanceSq, compatibilityHeavy));
                    MqoModelLoader.GroupTransform transform = customCrossingGateRendering
                        ? (stack, groupName) -> applyCrossingGateTransform(stack, blockEntity, groupName)
                        : null;
                    // ★改札もスクリプト経路を通す。
                    // 以前は「スクリプト経路だと扉の開閉 transform が渡らず開きっぱなしになる」として
                    // 改札だけ除外していたが、本家の RenderTurnstile01.js は扉を自分で開閉する
                    // (getMovingCount>0 で doorL/doorR を ±90 度回す)。
                    // ★スクリプトを実行するかどうかを RTMU の都合で決めない (本家準拠)。
                    boolean takeScriptPath = !customCrossingGateRendering
                        && definition.getScriptPath() != null && !definition.getScriptPath().isBlank();
                    if (takeScriptPath) {
                        com.portofino.realtrainmodunofficial.client.render.InstalledObjectScriptCache.render(
                            blockEntity, model, poseStack, buffer, packedLight, packedOverlay);
                    } else {
                        MqoModelLoader.renderModelWithoutScript(model, poseStack, buffer, packedLight, packedOverlay, false, filter, transform, blockEntity);
                        if (model.hasTranslucentBatches() && cameraDistanceSq < translucentThreshold * translucentThreshold) {
                            MqoModelLoader.renderModelWithoutScript(model, poseStack, buffer, packedLight, packedOverlay, true, filter, transform, blockEntity);
                        }
                    }
                    if (!veryFar && shouldRenderSupplementalActiveLights(blockEntity, definition, customCrossingGateRendering)) {
                        renderActiveLights(blockEntity, definition, poseStack, buffer, packedOverlay);
                    }
                    poseStack.popPose();
                    pushed = false;
                } catch (Throwable t) {
                    if (pushed) {
                        try { poseStack.popPose(); } catch (Throwable ignored) {}
                    }
                    FAILED_RENDER_UNTIL_NANOS.put(definition.getId(), System.nanoTime() + 5_000_000_000L);
                    com.portofino.realtrainmodunofficial.RealTrainModUnofficial.LOGGER.warn(
                        "Skipping installed object render for {} for 5 seconds after renderer failure.",
                        definition.getId(), t);
                }
                ClientRenderProfiler.endInstalledObject(profilerStart);
                return;
            }
        }
        if (blockEntity.getCategory() == InstalledObjectCategory.RAILROAD_SIGN) {
            renderRailroadSign(blockEntity, definition, poseStack, buffer, packedLight, packedOverlay);
            ClientRenderProfiler.endInstalledObject(profilerStart);
            return;
        }
        if (blockEntity.getCategory() == InstalledObjectCategory.SIGNBOARD) {
            renderSignboard(blockEntity, definition, poseStack, buffer, packedLight, packedOverlay);
        }
        ClientRenderProfiler.endInstalledObject(profilerStart);
    }

    private void renderWire(InstalledObjectBlockEntity blockEntity, InstalledObjectDefinition definition,
                            PoseStack poseStack, MultiBufferSource buffer,
                            double cameraDistanceSq, Vec3 cameraPos, int packedLight, int packedOverlay) {
        BlockPos start = blockEntity.getWireStart();
        BlockPos end = blockEntity.getWireEnd();
        if (start == null || end == null) {
            return;
        }
        // BlockEntityRenderer の poseStack 原点はブロックの「角(atLowerCornerOf)」なので、
        // 接続点(world)も角基準の相対座標に変換する。中心基準で引くと水平に 0.5 ズレる。
        Vec3 origin = Vec3.atLowerCornerOf(blockEntity.getBlockPos());
        Vec3 fromWorld = resolveWireAttachPoint(blockEntity.getLevel(), start);
        Vec3 toWorld = resolveWireAttachPoint(blockEntity.getLevel(), end);
        Vec3 from = fromWorld.subtract(origin);
        Vec3 to = toWorld.subtract(origin);

        String wireScript = definition.getScriptPath();
        boolean hasScript = wireScript != null && !wireScript.isBlank();
        String normalizedScript = hasScript ? wireScript.toLowerCase(java.util.Locale.ROOT).replace('\\', '/') : "";
        boolean hasRenderableModel = hasRenderableWireModel(definition);
        MqoModelLoader.MqoModel model = hasRenderableModel
            ? MqoModelLoader.loadModelFromPack(definition.getPackName(), definition.getModelFile(),
                definition.getTextureOverrides(), definition.getScriptPath(), definition.isSmoothing())
            : null;

        // どの経路で描いているかを定義ごとに 1 回だけ出す (架線柱が本家と違う見た目になる問題の切り分け用)
        logWireRouteOnce(definition, hasScript, wireScript, model);

        if (model != null && renderKnownScriptWireModel(blockEntity, definition, model, from, to,
            normalizedScript, poseStack, buffer, packedLight, packedOverlay)) {
            return;
        }

        // ★ 本家式: パックの rendererPath (WirePartsRenderer) をそのまま実行する。
        // 架線柱パックは描画を renderWireStatic/renderWireDynamic に書いており、それを
        // 呼ばずに自前の近似 (モデルを線に沿って等間隔で並べる) で描いていたため、
        // Baru's Pole のような作り込んだパックが本家と違う見た目になっていた。
        // ★model == null でもスクリプトを走らせること。
        if (hasScript) {
            com.portofino.realtrainmodunofficial.client.render.WireScriptRenderers.Scripted scripted =
                com.portofino.realtrainmodunofficial.client.render.WireScriptRenderers.get(definition);
            boolean drawn = scripted != null && scripted.render(blockEntity, from, to, 1.0F, poseStack, buffer,
                packedLight, packedOverlay, model);
            logWireScriptResultOnce(definition, scripted != null, drawn);
            if (drawn) {
                return;
            }
        }


        // BasicWire / SimpleCatenary / モデル無しは本家の単線系スクリプトとして描く。
        if (hasScript || model == null) {
            renderBasicWireCable(from, to, packedLight, poseStack, buffer);
            return;
        }

        Vec3 d = to.subtract(from);
        double length = d.length();
        if (length < 1.0e-4) {
            return;
        }
        // NGT Vec3.getYaw/getPitch と同じ式。
        float yaw = (float) Math.toDegrees(Math.atan2(d.x, d.z));
        double xz = Math.sqrt(d.x * d.x + d.z * d.z);
        float pitch = (float) Math.toDegrees(Math.atan2(d.y, xz));
        float sectionLength = definition.getSectionLength(); // 定義(JSON)の sectionLength を使う(隙間防止)
        int split = Math.max(1, (int) Math.floor(length / sectionLength));
        // 描画負荷の上限(長すぎる電線でセクション数が爆発しないように)。
        split = Math.min(split, 256);
        float scaleY = (float) ((length / (double) split) / sectionLength);

        poseStack.pushPose();
        try {
            poseStack.translate(from.x, from.y, from.z);
            // モデルの +Y 軸を線方向へ向ける(本家と同じ yaw+180 / pitch-90)。
            poseStack.mulPose(Axis.YP.rotationDegrees(yaw + 180.0F));
            poseStack.mulPose(Axis.XP.rotationDegrees(pitch - 90.0F));
            poseStack.scale(1.0F, scaleY, 1.0F);
            boolean hasTranslucent = model.hasTranslucentBatches();
            for (int i = 0; i < split; i++) {
                MqoModelLoader.renderModelWithoutScript(model, poseStack, buffer, packedLight, packedOverlay,
                    false, null, null, blockEntity);
                if (hasTranslucent) {
                    MqoModelLoader.renderModelWithoutScript(model, poseStack, buffer, packedLight, packedOverlay,
                        true, null, null, blockEntity);
                }
                poseStack.translate(0.0F, sectionLength, 0.0F);
            }
        } finally {
            poseStack.popPose();
        }
    }

    /** 架線 1 本ごとに「どの描画経路に入ったか」を定義単位で 1 回だけ記録する。 */
    private static final java.util.Set<String> WIRE_ROUTE_LOGGED =
        java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * 架線柱パックが本家と違う見た目になる問題の切り分け用。
     * 本家式スクリプト描画 (com.portofino.realtrainmodunofficial.client.render.WireScriptRenderers)
     * が動いていれば "Wire script renderer initialized" が出るはずだが、ログに一度も現れない。
     */
    private static void logWireRouteOnce(InstalledObjectDefinition definition, boolean hasScript,
                                         String wireScript, MqoModelLoader.MqoModel model) {
        if (definition == null || !WIRE_ROUTE_LOGGED.add(definition.getId())) {
            return;
        }
    }

    private static final java.util.Set<String> WIRE_SCRIPT_LOGGED =
        java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** 本家式スクリプト描画を「呼べたか」「実際に描けたか」を定義単位で 1 回だけ記録する。 */
    private static void logWireScriptResultOnce(InstalledObjectDefinition definition, boolean hasRenderer, boolean drawn) {
        if (definition == null || !WIRE_SCRIPT_LOGGED.add(definition.getId())) {
            return;
        }
    }

    private static boolean hasRenderableWireModel(InstalledObjectDefinition definition) {
        if (definition == null) {
            return false;
        }
        String modelFile = definition.getModelFile();
        if (modelFile == null || modelFile.isBlank()) {
            return false;
        }
        String normalized = modelFile.toLowerCase(java.util.Locale.ROOT).replace('\\', '/');
        return !normalized.endsWith("model_none.mqo");
    }

    private static Vec3 resolveWireAttachPoint(Level level, BlockPos pos) {
        if (level != null && level.getBlockEntity(pos) instanceof InstalledObjectBlockEntity endpoint) {
            InstalledObjectDefinition endpointDef = InstalledObjectRegistry.getById(endpoint.getDefinitionId());
            if (endpointDef != null) {
                Vec3 wp = endpointDef.getWireAttachPos();

                // ★ 面に取り付けた碍子 (通常の架線柱はこれ)。
                // 本家 TileEntityConnectorBase.updateWirePos + RenderElectricalWiring.renderAllWire:
                if (endpoint.getMountFace() >= 0) {
                    jp.ngt.ngtlib.math.Vec3 rotated = rotateWirePosByMountFace(
                        new jp.ngt.ngtlib.math.Vec3(wp.x, wp.y, wp.z), endpoint.getMountFace());
                    return Vec3.atLowerCornerOf(pos)
                        .add(0.5D, 0.5D, 0.5D)
                        .add(endpoint.getRenderOffset())
                        .add(rotated.getX(), rotated.getY(), rotated.getZ());
                }

                // 地面置き (取付面なし)。モデルは底面中央 + (180-yaw) で描かれるので接続点も同じに。
                Vec3 tilted = rotateX(new Vec3(wp.x, wp.y, wp.z), endpoint.getMountPitch());
                Vec3 rotated = rotateY(tilted, 180.0D - endpoint.getYaw());
                return Vec3.atLowerCornerOf(pos)
                    .add(0.5D, 0.0D, 0.5D)
                    .add(endpoint.getRenderOffset())
                    .add(rotated);
            }
        }
        return Vec3.atCenterOf(pos);
    }

    /**
     * 本家 TileEntityConnectorBase.updateWirePos の忠実移植。
     * 取付面 (0=下 1=上 2=北 3=南 4=西 5=東) に応じて wirePos を回す。
     */
    private static jp.ngt.ngtlib.math.Vec3 rotateWirePosByMountFace(jp.ngt.ngtlib.math.Vec3 vec, int face) {
        switch (face) {
            case 0:
                return vec.rotateAroundZ(180.0F);
            case 2:
                return vec.rotateAroundX(-90.0F).rotateAroundY(180.0F);
            case 3:
                return vec.rotateAroundX(-90.0F);
            case 4:
                return vec.rotateAroundX(-90.0F).rotateAroundY(-90.0F);
            case 5:
                return vec.rotateAroundX(-90.0F).rotateAroundY(90.0F);
            case 1:
            default:
                return vec;
        }
    }

    private static Vec3 rotateY(Vec3 vec, double degrees) {
        if (vec == null || vec.equals(Vec3.ZERO)) {
            return Vec3.ZERO;
        }
        double radians = Math.toRadians(degrees);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        return new Vec3(vec.x * cos + vec.z * sin, vec.y, vec.z * cos - vec.x * sin);
    }

    // Axis.XP.rotationDegrees と同じ +X 軸まわりの右手回転(描画と接続点を一致させる用)。
    private static Vec3 rotateX(Vec3 vec, double degrees) {
        if (degrees == 0.0D || vec == null || vec.equals(Vec3.ZERO)) {
            return vec == null ? Vec3.ZERO : vec;
        }
        double radians = Math.toRadians(degrees);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        return new Vec3(vec.x, vec.y * cos - vec.z * sin, vec.y * sin + vec.z * cos);
    }

    private boolean renderKnownScriptWireModel(InstalledObjectBlockEntity blockEntity, InstalledObjectDefinition definition,
                                               MqoModelLoader.MqoModel model, Vec3 from, Vec3 to, String script,
                                               PoseStack poseStack, MultiBufferSource buffer,
                                               int packedLight, int packedOverlay) {
        if (script == null || script.isBlank()) {
            return false;
        }
        if (script.endsWith("wire51/renderbeam1.js")) {
            renderWire51Beam(blockEntity, definition, model, from, to, poseStack, buffer, packedLight, packedOverlay);
            return true;
        }
        if (script.endsWith("wire51/renderwire.js")) {
            renderScaledZWireModel(blockEntity, definition, model, from, to, 10.0D, "obj1",
                poseStack, buffer, packedLight, packedOverlay);
            return true;
        }
        if (script.endsWith("wire51/renderbracket.js")) {
            renderScaledZWireModel(blockEntity, definition, model, from, to, 3.0D, "obj1",
                poseStack, buffer, packedLight, packedOverlay);
            return true;
        }
        if (script.endsWith("wire51/renderbracketd.js")) {
            renderScaledZWireModel(blockEntity, definition, model, from, to, 4.0D, "obj1",
                poseStack, buffer, packedLight, packedOverlay);
            return true;
        }
        return false;
    }

    private void renderWire51Beam(InstalledObjectBlockEntity blockEntity, InstalledObjectDefinition definition,
                                  MqoModelLoader.MqoModel model, Vec3 from, Vec3 to, PoseStack poseStack,
                                  MultiBufferSource buffer, int packedLight, int packedOverlay) {
        Vec3 d = to.subtract(from);
        double length = d.length();
        if (length < 1.0e-4D) {
            return;
        }

        int maxPos = Math.max(1, (int) Math.floor(length / 2.0D) * 2);
        maxPos = Math.min(maxPos, 256);
        double move = length / (double) maxPos;
        float scale = (float) move;
        int halfMaxPos = maxPos / 2;

        poseStack.pushPose();
        try {
            applyZWireOrientation(poseStack, from, d);
            poseStack.scale(definition.getModelScale(), definition.getModelScale(), definition.getModelScale());
            for (int i = 0; i < maxPos; i++) {
                String group;
                double offsetZ = 0.0D;
                if (i == 0) {
                    group = "BeamR1";
                    offsetZ = 2.0D;
                } else if (i < halfMaxPos) {
                    group = "BeamR2";
                    offsetZ = 1.0D;
                } else if (i < maxPos - 1) {
                    group = "BeamL2";
                } else {
                    group = "BeamL1";
                    offsetZ = -1.0D;
                }

                poseStack.pushPose();
                poseStack.translate(0.0D, 0.0D, move * i + offsetZ);
                poseStack.scale(1.0F, 1.0F, scale);
                renderWireModelGroup(model, poseStack, buffer, packedLight, packedOverlay, blockEntity, group);
                poseStack.popPose();
            }
        } finally {
            poseStack.popPose();
        }
    }

    private void renderScaledZWireModel(InstalledObjectBlockEntity blockEntity, InstalledObjectDefinition definition,
                                        MqoModelLoader.MqoModel model, Vec3 from, Vec3 to, double baseLength,
                                        String groupName, PoseStack poseStack, MultiBufferSource buffer,
                                        int packedLight, int packedOverlay) {
        Vec3 d = to.subtract(from);
        double length = d.length();
        if (length < 1.0e-4D || baseLength <= 0.0D) {
            return;
        }

        // 本家Wire51の描画スクリプト(renderWire.js 等)を忠実移植:
        // rate = length / baseLength;            // wire=10, bracket=3, bracketD=4
        // rotate(yaw,'Y'); rotate(-pitch,'X');   // applyZWireOrientation
        // glScalef(1, 1, rate);                  // +Z(線方向)のみを電線長へ伸ばす
        // wire.render;                         // 1回だけ(タイルしない)
        // Catenary1 は +Z 軸長 1000(×0.01=10ブロック)で作られているため、Z を rate 倍すれば
        // 碍子から碍子へ正しい太さ・たるみ(Y方向 -0.81)で張られる。
        float rate = (float) (length / baseLength);

        poseStack.pushPose();
        try {
            applyZWireOrientation(poseStack, from, d);
            float modelScale = definition.getModelScale();
            poseStack.scale(modelScale, modelScale, modelScale);
            poseStack.scale(1.0F, 1.0F, rate);
            renderWireModelGroup(model, poseStack, buffer, packedLight, packedOverlay, blockEntity, groupName);
        } finally {
            poseStack.popPose();
        }
    }

    private static void applyZWireOrientation(PoseStack poseStack, Vec3 from, Vec3 d) {
        double xz = Math.sqrt(d.x * d.x + d.z * d.z);
        // 本家Wire51スクリプト(renderWire/renderBeam/renderBracket)準拠:
        // yaw = vec.getYaw; pit = -vec.getPitch;
        // rotate(yaw,'Y'); rotate(pit,'X');
        // モデルは +Z 軸が線方向に作られている(Catenary1 dZ=1000≒10ブロック)。
        float yaw = (float) Math.toDegrees(Math.atan2(d.x, d.z));
        float pitch = (float) Math.toDegrees(Math.atan2(d.y, xz));
        poseStack.translate(from.x, from.y, from.z);
        poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(-pitch));
    }

    private static void renderWireModelGroup(MqoModelLoader.MqoModel model, PoseStack poseStack,
                                             MultiBufferSource buffer, int packedLight, int packedOverlay,
                                             InstalledObjectBlockEntity blockEntity, String groupName) {
        MqoModelLoader.GroupPredicate filter = groupName == null || groupName.isBlank()
            ? null
            : candidate -> groupMatches(candidate, groupName);
        MqoModelLoader.renderModelWithoutScript(model, poseStack, buffer, packedLight, packedOverlay,
            false, filter, null, blockEntity);
        if (model.hasTranslucentBatches()) {
            MqoModelLoader.renderModelWithoutScript(model, poseStack, buffer, packedLight, packedOverlay,
                true, filter, null, blockEntity);
        }
    }

    /**
     * 本家RTM の基本ワイヤー(RenderBasicWire.js renderWireDynamic)を忠実再現したケーブル描画。
     * XZ面リボン(ワイヤー色)と Y面リボン(黒)の十字を、たるみ式 fh=((j-8)/16)^2-0.25)*1.5 で描く。
     */
    private void renderBasicWireCable(Vec3 from, Vec3 to, int packedLight, PoseStack poseStack, MultiBufferSource buffer) {
        double x = to.x - from.x;
        double y = to.y - from.y;
        double z = to.z - from.z;
        double hor = Math.sqrt(x * x + z * z);
        if (hor < 1.0e-6) hor = 1.0e-6;
        double x1 = x / hor, z1 = z / hor;
        final int split = 16;
        final double w = 0.025D;
        VertexConsumer c = buffer.getBuffer(RenderType.leash());
        Matrix4f mat = poseStack.last().pose();
        // XZ リボン色(暗灰)/ Y リボン色(黒)。RTM は XZ=ワイヤー色, Y=0(黒)。
        final int xr = 26, xg = 26, xb = 26;
        final int yr = 6, yg = 6, yb = 6;
        // --- XZ 面リボン ---
        float lastX = 0, lastY = 0, lastZ = 0;
        for (int j = 0; j <= split; j++) {
            double ft = j / (double) split;
            double f2 = (j - 8.0) / split;
            double fh = (f2 * f2 - 0.25) * 1.5;
            double px = from.x + x * ft, py = from.y + y * ft + fh, pz = from.z + z * ft;
            c.addVertex(mat, (float) (px - w * z1), (float) py, (float) (pz + w * x1))
                .setColor(xr, xg, xb, 255).setLight(packedLight);
            lastX = (float) (px + w * z1); lastY = (float) py; lastZ = (float) (pz - w * x1);
            c.addVertex(mat, lastX, lastY, lastZ).setColor(xr, xg, xb, 255).setLight(packedLight);
        }
        // --- 縮退ブリッジ(XZ最後の頂点 → Y最初の頂点)で strip を分離 ---
        double fh0 = (((0 - 8.0) / split) * ((0 - 8.0) / split) - 0.25) * 1.5;
        float firstYx = (float) from.x, firstYy = (float) (from.y + fh0 + w), firstYz = (float) from.z;
        c.addVertex(mat, lastX, lastY, lastZ).setColor(yr, yg, yb, 255).setLight(packedLight);
        c.addVertex(mat, firstYx, firstYy, firstYz).setColor(yr, yg, yb, 255).setLight(packedLight);
        // --- Y 面リボン ---
        for (int j = 0; j <= split; j++) {
            double ft = j / (double) split;
            double f2 = (j - 8.0) / split;
            double fh = (f2 * f2 - 0.25) * 1.5;
            double px = from.x + x * ft, py = from.y + y * ft + fh, pz = from.z + z * ft;
            c.addVertex(mat, (float) px, (float) (py + w), (float) pz).setColor(yr, yg, yb, 255).setLight(packedLight);
            c.addVertex(mat, (float) px, (float) (py - w), (float) pz).setColor(yr, yg, yb, 255).setLight(packedLight);
        }
    }

    private static boolean shouldRenderInstalledObjectGroup(String groupName, InstalledObjectBlockEntity blockEntity,
                                                            InstalledObjectDefinition definition, double cameraDistanceSq,
                                                            boolean compatibilityHeavy) {
        if (groupName == null || groupName.isBlank()) {
            return true;
        }
        String normalized = groupName.toLowerCase(java.util.Locale.ROOT);
        if (usesBuiltinCrossingGateLayout(definition) && CROSSING_SCRIPT_ONLY_GROUPS.contains(normalized)) {
            return false;
        }
        if (cameraDistanceSq > 140.0D * 140.0D) {
            if (normalized.contains("detail")
                || normalized.contains("under")
                || normalized.contains("inside")
                || normalized.contains("step")
                || normalized.contains("ladder")
                || normalized.contains("handle")
                || normalized.contains("lever")) {
                return false;
            }
        }
        if (cameraDistanceSq > 80.0D * 80.0D) {
            if (normalized.contains("glass")
                || normalized.contains("alpha")
                || normalized.contains("screen")
                || normalized.contains("panel")) {
                return false;
            }
        }
        if (compatibilityHeavy) {
            if (normalized.contains("glass")
                || normalized.contains("alpha")
                || normalized.contains("window")
                || normalized.contains("screen")
                || normalized.contains("display")) {
                return false;
            }
            if (cameraDistanceSq > 56.0D * 56.0D && (normalized.contains("detail")
                || normalized.contains("cover")
                || normalized.contains("frame")
                || normalized.contains("inside")
                || normalized.contains("back"))) {
                return false;
            }
        }
        return true;
    }

    private static boolean shouldUseCustomCrossingGateRendering(InstalledObjectBlockEntity blockEntity, InstalledObjectDefinition definition) {
        return blockEntity != null
            && definition != null
            && blockEntity.getCategory() == InstalledObjectCategory.CROSSING
            && definition.getScriptPath() != null
            && usesBuiltinCrossingGateLayout(definition);
    }

    private static void applyCrossingGateTransform(PoseStack poseStack, InstalledObjectBlockEntity blockEntity, String groupName) {
        if (blockEntity == null || groupName == null) {
            return;
        }
        String normalized = groupName.toLowerCase(java.util.Locale.ROOT);
        if (!normalized.equals("bar0") && !normalized.equals("bar1")
            && !normalized.equals("bar") && !normalized.equals("bar2")) {
            return;
        }
        CrossingTransform transform = resolveCrossingTransform(blockEntity, normalized);
        if (transform == null) {
            return;
        }
        float move = (float) ((blockEntity.getBarMoveCount() / 90.0F) * transform.degrees());
        poseStack.translate(transform.pivotX(), transform.pivotY(), transform.pivotZ());
        poseStack.mulPose(Axis.ZP.rotationDegrees(move));
        poseStack.translate(-transform.pivotX(), -transform.pivotY(), -transform.pivotZ());
    }

    /**
     * 改札(TICKET_GATE)の扉(doorL/doorR)を、閉時にヒンジ周りで回して通路を塞ぐ。
     * 本家RTM: モデル静止位置=開、閉(canThrough=false)で扉を回転。
     */

    /** group(s) のモデル座標 AABB {minX,minY,minZ,maxX,maxY,maxZ}。取得できなければ null。 */
    // 碍子モデルの実描画上端Y(ベイク座標=×0.01適用済み)。電線をモデル先端から出すために使う。
    private static double modelTopY(MqoModelLoader.MqoModel model) {
        if (model == null) {
            return Double.NaN;
        }
        java.util.Set<String> groups = model.getAllNormalizedGroupNames();
        if (groups == null || groups.isEmpty()) {
            return Double.NaN;
        }
        java.util.List<float[]> quads = model.getGroupQuadCorners(groups);
        if (quads == null || quads.isEmpty()) {
            return Double.NaN;
        }
        double maxY = -Double.MAX_VALUE;
        for (float[] q : quads) {
            for (int c = 0; c < 4; c++) {
                maxY = Math.max(maxY, q[c * 3 + 1]);
            }
        }
        return maxY == -Double.MAX_VALUE ? Double.NaN : maxY;
    }

    private static float[] groupBounds(MqoModelLoader.MqoModel model, String groupName) {
        java.util.List<float[]> quads = model.getGroupQuadCorners(java.util.Set.of(groupName));
        if (quads == null || quads.isEmpty()) {
            return null;
        }
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        for (float[] q : quads) {
            for (int c = 0; c < 4; c++) {
                float x = q[c * 3], y = q[c * 3 + 1], z = q[c * 3 + 2];
                minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                minY = Math.min(minY, y); maxY = Math.max(maxY, y);
                minZ = Math.min(minZ, z); maxZ = Math.max(maxZ, z);
            }
        }
        return new float[]{minX, minY, minZ, maxX, maxY, maxZ};
    }

    private static CrossingTransform resolveCrossingTransform(InstalledObjectBlockEntity blockEntity, String groupName) {
        String scriptPath = getCrossingScriptPath(InstalledObjectRegistry.getById(blockEntity.getDefinitionId()));
        boolean turnRight = blockEntity.getModelName().endsWith("R");
        if (scriptPath.contains("hi03rendercrossinggate")) {
            double degrees = turnRight ? 85.0D : -85.0D;
            if ("bar2".equals(groupName)) {
                return new CrossingTransform(-0.5303D, 6.0287D, 0.0D, -degrees);
            }
            return new CrossingTransform(0.0D, 0.9056D, 0.0D, degrees);
        }
        if (scriptPath.contains("masacrossinggate")) {
            double degrees = turnRight ? 90.0D : -90.0D;
            return new CrossingTransform(0.02D, 0.92D, 0.0D, degrees);
        }
        if ("bar0".equals(groupName) || "bar1".equals(groupName)) {
            double degrees = turnRight ? 90.0D : -90.0D;
            return new CrossingTransform(0.0D, 0.5337D, -0.24D, degrees);
        }
        return null;
    }

    private static boolean isSupportedCustomCrossingScript(String scriptPath) {
        String normalized = scriptPath == null ? "" : scriptPath.toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("rendercrossinggate")
            || normalized.contains("crossinggate");
    }

    private static boolean usesBuiltinCrossingGateLayout(InstalledObjectDefinition definition) {
        String scriptPath = getCrossingScriptPath(definition);
        return scriptPath.contains("rendercrossinggate01");
    }

    private static String getCrossingScriptPath(InstalledObjectDefinition definition) {
        return definition == null || definition.getScriptPath() == null
            ? ""
            : definition.getScriptPath().toLowerCase(java.util.Locale.ROOT);
    }

    /** 本家 GuiChangeOffset の微調整 (scale → roll → pitch → yaw、本家 RenderSignal と同順) */
    private static void applyAdjustments(PoseStack poseStack, InstalledObjectBlockEntity be) {
        float scale = be.getAdjustScale();
        if (scale != 1.0F) {
            poseStack.scale(scale, scale, scale);
        }
        if (be.getAdjustRoll() != 0.0F) {
            poseStack.mulPose(Axis.ZP.rotationDegrees(be.getAdjustRoll()));
        }
        if (be.getAdjustPitch() != 0.0F) {
            poseStack.mulPose(Axis.XP.rotationDegrees(be.getAdjustPitch()));
        }
        if (be.getAdjustYaw() != 0.0F) {
            poseStack.mulPose(Axis.YP.rotationDegrees(be.getAdjustYaw()));
        }
    }

    /**
     * 本家 RenderElectricalWiring の meta (クリック面 0-5) 回転。
     * 0=下面(天井吊り)=Z180, 1=上面=そのまま, 2-5=側面=横倒し(取り付け面向き)。
     */
    /**
     * 本家 RenderMachine の rotateByMetadata 面回転 (meta = クリック面 0-5)。ブロック垂直中心を軸に回す。
     * 碍子/コネクタの #applyHonkeMountFaceRotation (RenderElectricalWiring) とは別物で、
     * 照明 (サーチライト/回転灯/灯台灯) 専用。GL11.glRotatef の符号をそのまま Axis.*.rotationDegrees へ移植。
     */
    private static void applyLightMetadataRotation(PoseStack poseStack, int face) {
        switch (face) {
            case 0 -> poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));
            case 2 -> poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
            case 3 -> poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
            case 4 -> poseStack.mulPose(Axis.ZP.rotationDegrees(90.0F));
            case 5 -> poseStack.mulPose(Axis.ZP.rotationDegrees(-90.0F));
            case 1 -> {
            }
            default -> {
            }
        }
    }

    private static void applyHonkeMountFaceRotation(PoseStack poseStack, int face) {
        switch (face) {
            case 0 -> poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));
            case 1 -> {
            }
            case 2 -> {
                poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
                poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
            }
            case 3 -> poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
            case 4 -> {
                poseStack.mulPose(Axis.YP.rotationDegrees(-90.0F));
                poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
            }
            case 5 -> {
                poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
                poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
            }
            default -> {
            }
        }
    }

    private static boolean shouldUseCompatibilityRendering(InstalledObjectDefinition definition, MqoModelLoader.MqoModel model) {
        if (definition == null || model == null) {
            return false;
        }
        boolean hasScript = definition.getScriptPath() != null && !definition.getScriptPath().isBlank();
        return model.getTotalVertexCount() >= 12_000
            || model.getBatchCount() >= 96
            || model.getTranslucentBatchCount() >= 16
            || (hasScript && model.getBatchCount() >= 64);
    }

    private static boolean shouldRenderDefinedObjectGroup(String groupName, InstalledObjectDefinition definition) {
        if (definition == null || definition.getRenderObjects().isEmpty()) {
            return true;
        }
        for (String expected : definition.getRenderObjects()) {
            if (groupMatches(groupName, expected)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 本家 RenderSignBoard の移植。
     * 本家の看板は「板1枚」ではなく 厚みのある箱 (width x height x depth) で、
     * ブロック中心を原点に置き、設置面 (mountFace) の側へ寄せて描く。
     */
    /**
     * 本家 RenderRailroadSign の移植。
     * ポール (直径 1/8・高さ 1.5 の円柱) の上に、選んだテクスチャを貼った板を立てるだけ。
     */
    private void renderRailroadSign(InstalledObjectBlockEntity blockEntity, InstalledObjectDefinition definition,
                                    PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay) {
        String signTexture = definition.getSignTexture();
        ResourceLocation texture = signTexture == null || signTexture.isBlank()
            ? null
            : MqoModelLoader.resolvePackTexture(definition.getPackName(), signTexture);

        // 本家 RenderRailroadSign.flipVertical: 真上が空でなければ吊り下げ。
        boolean hanging = blockEntity.getLevel() != null
            && !blockEntity.getLevel().isEmptyBlock(blockEntity.getBlockPos().above());
        // 本家: f0 = 1.25 (立てる) / -0.25 (吊る)
        float plateY = hanging ? -0.25F : 1.25F;
        final float w = 0.25F;      //板の半径 (本家 w)
        final float d = 0.0675F;    //板の Z 方向オフセット (本家 d)

        poseStack.pushPose();
        poseStack.translate(0.5D, 0.0D, 0.5D);
        Vec3 renderOffset = blockEntity.getRenderOffset();
        poseStack.translate(renderOffset.x, renderOffset.y, renderOffset.z);
        applyAdjustments(poseStack, blockEntity);

        // ---- 板 ----
        poseStack.pushPose();
        poseStack.translate(0.0F, plateY, 0.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees(blockEntity.getYaw()));
        PoseStack.Pose pose = poseStack.last();
        // ★ VertexConsumer は「使う直前に」取る。MultiBufferSource は別の RenderType を
        // 要求された時点で前のバッファを閉じるので、先に取っておくと後で書き込んだ瞬間に
        // IllegalStateException: Not building! で落ちる (標識を置くとクラッシュしていた原因)。
        if (texture != null) {
            VertexConsumer plate = buffer.getBuffer(RenderType.entityCutoutNoCull(texture));
            // 表: テクスチャそのまま
            signVertex(plate, pose, w, -w, d, 1.0F, 1.0F, packedLight, packedOverlay, 0xFFFFFF, 0.0F, 0.0F, 1.0F);
            signVertex(plate, pose, w, w, d, 1.0F, 0.0F, packedLight, packedOverlay, 0xFFFFFF, 0.0F, 0.0F, 1.0F);
            signVertex(plate, pose, -w, w, d, 0.0F, 0.0F, packedLight, packedOverlay, 0xFFFFFF, 0.0F, 0.0F, 1.0F);
            signVertex(plate, pose, -w, -w, d, 0.0F, 1.0F, packedLight, packedOverlay, 0xFFFFFF, 0.0F, 0.0F, 1.0F);

            // 裏: 本家は同じテクスチャを貼ったまま色 0 (黒) で塗る。
            // 透明部分はそのまま抜ける。
            final float backD = d - 0.002F;
            signVertex(plate, pose, -w, -w, backD, 0.0F, 1.0F, packedLight, packedOverlay, 0x000000, 0.0F, 0.0F, -1.0F);
            signVertex(plate, pose, -w, w, backD, 0.0F, 0.0F, packedLight, packedOverlay, 0x000000, 0.0F, 0.0F, -1.0F);
            signVertex(plate, pose, w, w, backD, 1.0F, 0.0F, packedLight, packedOverlay, 0x000000, 0.0F, 0.0F, -1.0F);
            signVertex(plate, pose, w, -w, backD, 1.0F, 1.0F, packedLight, packedOverlay, 0x000000, 0.0F, 0.0F, -1.0F);
        }
        poseStack.popPose();

        // ---- ポール ----
        // 本家: 吊り下げのときはポールの根元を 0.5 下げる (板から天井まで届かせる)。
        if (hanging) {
            poseStack.translate(0.0F, -0.5F, 0.0F);
        }
        // 本家 NGTRenderer.renderPole(tessellator, 0.0625F, 1.5F, false) + 色 0x404040
        // 板を描き終えてからバッファを取る (先に取ると板の描画でこのバッファが閉じられてしまう)。
        VertexConsumer solid = buffer.getBuffer(RenderType.entityCutoutNoCull(SolidTexture.white()));
        renderPole(solid, poseStack.last(), 0.0625F, 1.5F, 0x404040, packedLight, packedOverlay);
        poseStack.popPose();
    }

    /**
     * 本家 NGTRenderer.renderPole 相当の 16 角柱。テクスチャは使わず単色で塗る。
     * (本家は球モデルの赤道リングを流用していたが、やっていることは単位円なので三角関数で出す)
     */
    private static void renderPole(VertexConsumer consumer, PoseStack.Pose pose,
                                   float radius, float length, int color,
                                   int packedLight, int packedOverlay) {
        final int sides = 16;
        for (int i = 0; i < sides; i++) {
            double a0 = (Math.PI * 2.0D / sides) * i;
            double a1 = (Math.PI * 2.0D / sides) * (i + 1);
            float x0 = (float) (Math.cos(a0) * radius);
            float z0 = (float) (Math.sin(a0) * radius);
            float x1 = (float) (Math.cos(a1) * radius);
            float z1 = (float) (Math.sin(a1) * radius);
            // 法線は面の中央方向 (外向き)
            float nx = (float) Math.cos((a0 + a1) * 0.5D);
            float nz = (float) Math.sin((a0 + a1) * 0.5D);
            signVertex(consumer, pose, x0, 0.0F, z0, 0.0F, 1.0F, packedLight, packedOverlay, color, nx, 0.0F, nz);
            signVertex(consumer, pose, x0, length, z0, 0.0F, 0.0F, packedLight, packedOverlay, color, nx, 0.0F, nz);
            signVertex(consumer, pose, x1, length, z1, 1.0F, 0.0F, packedLight, packedOverlay, color, nx, 0.0F, nz);
            signVertex(consumer, pose, x1, 0.0F, z1, 1.0F, 1.0F, packedLight, packedOverlay, color, nx, 0.0F, nz);
        }
    }

    private void renderSignboard(InstalledObjectBlockEntity blockEntity, InstalledObjectDefinition definition,
                                 PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay) {
        String signTexture = definition.getSignTexture();
        ResourceLocation texture = signTexture == null || signTexture.isBlank()
            ? null
            : MqoModelLoader.resolvePackTexture(definition.getPackName(), signTexture);
        if (texture == null) {
            renderSignboardOutline(definition, poseStack, buffer);
            return;
        }

        float halfWidth = definition.getWidth() * 0.5F;
        float halfHeight = definition.getHeight() * 0.5F;
        float halfDepth = Math.max(0.01F, definition.getDepth() * 0.5F);
        int frame = Math.max(1, definition.getSignFrame());
        int cycle = Math.max(1, definition.getAnimationCycle());
        int backTex = definition.getBackTexture();
        int dir = blockEntity.getSignDirection();
        int mountFace = blockEntity.getMountFace();

        // 本家: frame>1 ならカウンタで V をずらしてコマ送りする。
        float minV = 0.0F;
        float maxV = 1.0F;
        if (frame > 1) {
            int f = (blockEntity.getSignCounter() / cycle) % frame;
            minV = (float) f / frame;
            maxV = (float) (f + 1) / frame;
        }

        poseStack.pushPose();
        poseStack.translate(0.5D, 0.5D, 0.5D);
        Vec3 renderOffset = blockEntity.getRenderOffset();
        poseStack.translate(renderOffset.x, renderOffset.y, renderOffset.z);
        applyAdjustments(poseStack, blockEntity);
        poseStack.mulPose(Axis.YP.rotationDegrees(dir * -90.0F));
        applySignboardMountOffset(poseStack, mountFace, dir, halfWidth, halfHeight, halfDepth);

        PoseStack.Pose pose = poseStack.last();
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(texture));
        // 本家: backTexture==1 はテクスチャを左右に割って表/裏に貼る。
        float frontMaxU = backTex == 1 ? 0.5F : 1.0F;
        float backMinU = backTex == 1 ? 0.5F : 0.0F;

        // 表 (+Z 側)
        signVertex(consumer, pose, halfWidth, -halfHeight, halfDepth, frontMaxU, maxV, packedLight, packedOverlay, 0xFFFFFF, 0.0F, 0.0F, 1.0F);
        signVertex(consumer, pose, halfWidth, halfHeight, halfDepth, frontMaxU, minV, packedLight, packedOverlay, 0xFFFFFF, 0.0F, 0.0F, 1.0F);
        signVertex(consumer, pose, -halfWidth, halfHeight, halfDepth, 0.0F, minV, packedLight, packedOverlay, 0xFFFFFF, 0.0F, 0.0F, 1.0F);
        signVertex(consumer, pose, -halfWidth, -halfHeight, halfDepth, 0.0F, maxV, packedLight, packedOverlay, 0xFFFFFF, 0.0F, 0.0F, 1.0F);

        // 裏 (-Z 側)。backTexture==2 のときだけ単色なので後段でまとめて塗る。
        if (backTex != 2) {
            signVertex(consumer, pose, -halfWidth, -halfHeight, -halfDepth, 1.0F, maxV, packedLight, packedOverlay, 0xFFFFFF, 0.0F, 0.0F, -1.0F);
            signVertex(consumer, pose, -halfWidth, halfHeight, -halfDepth, 1.0F, minV, packedLight, packedOverlay, 0xFFFFFF, 0.0F, 0.0F, -1.0F);
            signVertex(consumer, pose, halfWidth, halfHeight, -halfDepth, backMinU, minV, packedLight, packedOverlay, 0xFFFFFF, 0.0F, 0.0F, -1.0F);
            signVertex(consumer, pose, halfWidth, -halfHeight, -halfDepth, backMinU, maxV, packedLight, packedOverlay, 0xFFFFFF, 0.0F, 0.0F, -1.0F);
        }

        // 単色部分 (側面 4 面 + backTexture==2 の裏面)
        int color = definition.getColor();
        VertexConsumer solid = buffer.getBuffer(RenderType.entityCutoutNoCull(SolidTexture.white()));
        if (backTex == 2) {
            signVertex(solid, pose, -halfWidth, -halfHeight, -halfDepth, 0.0F, 1.0F, packedLight, packedOverlay, color, 0.0F, 0.0F, -1.0F);
            signVertex(solid, pose, -halfWidth, halfHeight, -halfDepth, 0.0F, 0.0F, packedLight, packedOverlay, color, 0.0F, 0.0F, -1.0F);
            signVertex(solid, pose, halfWidth, halfHeight, -halfDepth, 1.0F, 0.0F, packedLight, packedOverlay, color, 0.0F, 0.0F, -1.0F);
            signVertex(solid, pose, halfWidth, -halfHeight, -halfDepth, 1.0F, 1.0F, packedLight, packedOverlay, color, 0.0F, 0.0F, -1.0F);
        }
        // 本家: 縁は板の色より少し暗くする。
        int edgeColor = Math.max(0, color - 0x101010);
        // 上面
        signQuad(solid, pose, packedLight, packedOverlay, edgeColor, 0.0F, 1.0F, 0.0F,
            halfWidth, halfHeight, halfDepth, halfWidth, halfHeight, -halfDepth,
            -halfWidth, halfHeight, -halfDepth, -halfWidth, halfHeight, halfDepth);
        // 下面
        signQuad(solid, pose, packedLight, packedOverlay, edgeColor, 0.0F, -1.0F, 0.0F,
            -halfWidth, -halfHeight, halfDepth, -halfWidth, -halfHeight, -halfDepth,
            halfWidth, -halfHeight, -halfDepth, halfWidth, -halfHeight, halfDepth);
        // 右面 (+X)
        signQuad(solid, pose, packedLight, packedOverlay, edgeColor, 1.0F, 0.0F, 0.0F,
            halfWidth, -halfHeight, -halfDepth, halfWidth, halfHeight, -halfDepth,
            halfWidth, halfHeight, halfDepth, halfWidth, -halfHeight, halfDepth);
        // 左面 (-X)
        signQuad(solid, pose, packedLight, packedOverlay, edgeColor, -1.0F, 0.0F, 0.0F,
            -halfWidth, -halfHeight, halfDepth, -halfWidth, halfHeight, halfDepth,
            -halfWidth, halfHeight, -halfDepth, -halfWidth, -halfHeight, -halfDepth);

        renderSignboardTexts(blockEntity, definition, poseStack, buffer,
            halfWidth, halfHeight, halfDepth, backTex, packedLight, packedOverlay);

        poseStack.popPose();
    }

    /**
     * 本家 RenderSignBoard の meta/dir 分岐そのまま: 板を設置面へ寄せる。
     * meta は設置時にクリックした面 (Direction.ordinal: DOWN=0, UP=1, N=2, S=3, W=4, E=5)。
     */
    private static void applySignboardMountOffset(PoseStack poseStack, int meta, int dir,
                                                  float halfWidth, float halfHeight, float halfDepth) {
        if (meta < 0) {
            // 旧データ (設置面なし)。中心のまま置く。
            return;
        }
        if (meta == 0) {
            // 天井から吊るす
            poseStack.translate(0.0F, 0.5F - halfHeight, 0.0F);
        } else if (meta == 1) {
            // 床から立てる
            poseStack.translate(0.0F, halfHeight - 0.5F, 0.0F);
        } else if ((dir == 1 && meta == 4) || (dir == 3 && meta == 5)
            || (dir == 0 && meta == 3) || (dir == 2 && meta == 2)) {
            poseStack.translate(0.0F, 0.0F, halfDepth - 0.5F);
        } else if ((dir == 1 && meta == 3) || (dir == 3 && meta == 2)
            || (dir == 0 && meta == 5) || (dir == 2 && meta == 4)) {
            poseStack.translate(halfWidth - 0.5F, 0.0F, 0.0F);
        } else {
            poseStack.translate(0.5F - halfWidth, 0.0F, 0.0F);
        }
    }

    /** 本家: 板に貼り付けた文字を描く。表と裏の振り分けは backTexture による。 */
    private static void renderSignboardTexts(InstalledObjectBlockEntity blockEntity, InstalledObjectDefinition definition,
                                             PoseStack poseStack, MultiBufferSource buffer,
                                             float halfWidth, float halfHeight, float halfDepth,
                                             int backTex, int packedLight, int packedOverlay) {
        List<SignboardText> texts = blockEntity.getSignTexts();
        if (texts.isEmpty()) {
            return;
        }
        String ttSetting = blockEntity.getSignTtSetting();
        // 板の面より僅かに手前に出して Z ファイティングを避ける (本家も +0.01)。
        float z = halfDepth + 0.01F;
        // backTexture==1 は「テクスチャの左半分=表、右半分=裏」。
        // 幅 width*2 (表と裏を横に並べたもの) なので、表/裏の境目は posU == width。
        float backThreshold = definition.getWidth();

        for (SignboardText text : texts) {
            SignboardTextRenderer.Frame frame = SignboardTextRenderer.frameFor(text, ttSetting);
            if (!frame.shouldDraw()) {
                continue;
            }
            VertexConsumer consumer = buffer.getBuffer(RenderType.entityTranslucent(frame.image().getTexture()));
            float w = frame.width();
            float h = text.size;

            boolean onFront = backTex != 1 || text.posU < backThreshold;
            if (onFront) {
                // posU は板の左端から、posV は板の上端から。
                frame.image().render(poseStack.last(), consumer,
                    text.posU - halfWidth, halfHeight - text.posV, z, w, h,
                    frame.minU(), 0.0F, frame.maxU(), 1.0F, packedLight, packedOverlay);
            }

            boolean onBack = backTex == 0 || (backTex == 1 && text.posU >= backThreshold);
            if (onBack) {
                poseStack.pushPose();
                poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
                float x = text.posU - halfWidth;
                if (backTex == 1) {
                    x -= definition.getWidth();
                }
                frame.image().render(poseStack.last(), consumer,
                    x, halfHeight - text.posV, z, w, h,
                    frame.minU(), 0.0F, frame.maxU(), 1.0F, packedLight, packedOverlay);
                poseStack.popPose();
            }
        }
    }

    private void renderSignboardOutline(InstalledObjectDefinition definition, PoseStack poseStack, MultiBufferSource buffer) {
        VertexConsumer consumer = buffer.getBuffer(RenderType.lines());
        double halfWidth = definition.getWidth() * 0.5D;
        double height = definition.getHeight();
        double halfDepth = Math.max(0.02D, definition.getDepth() * 0.5D);
        LevelRenderer.renderLineBox(
            poseStack,
            consumer,
            0.5D - halfWidth, 0.0D, 0.5D - halfDepth,
            0.5D + halfWidth, height, 0.5D + halfDepth,
            1.0F, 0.95F, 0.6F, 0.9F
        );
    }

    private static void signQuad(VertexConsumer consumer, PoseStack.Pose pose, int packedLight, int packedOverlay, int color,
                                 float nx, float ny, float nz,
                                 float x1, float y1, float z1, float x2, float y2, float z2,
                                 float x3, float y3, float z3, float x4, float y4, float z4) {
        signVertex(consumer, pose, x1, y1, z1, 0.0F, 1.0F, packedLight, packedOverlay, color, nx, ny, nz);
        signVertex(consumer, pose, x2, y2, z2, 1.0F, 1.0F, packedLight, packedOverlay, color, nx, ny, nz);
        signVertex(consumer, pose, x3, y3, z3, 1.0F, 0.0F, packedLight, packedOverlay, color, nx, ny, nz);
        signVertex(consumer, pose, x4, y4, z4, 0.0F, 0.0F, packedLight, packedOverlay, color, nx, ny, nz);
    }

    private static void signVertex(VertexConsumer consumer, PoseStack.Pose pose,
                                   float x, float y, float z, float u, float v,
                                   int packedLight, int packedOverlay, int color,
                                   float nx, float ny, float nz) {
        consumer.addVertex(pose.pose(), x, y, z)
            .setColor((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, 255)
            .setUv(u, v)
            .setOverlay(packedOverlay)
            .setLight(packedLight)
            .setNormal(pose, nx, ny, nz);
    }

    private void renderActiveLights(InstalledObjectBlockEntity blockEntity, InstalledObjectDefinition definition,
                                    PoseStack poseStack, MultiBufferSource buffer, int packedOverlay) {
        List<String> groups = resolveActiveLightGroups(blockEntity, definition);
        if (groups.isEmpty()) {
            return;
        }
        // 本家 BasicSignalPartsRenderer の点灯パス: 現示灯は「点灯用テクスチャ (lightTexture)」を
        // 貼った同じポリゴンを全光量で描く。消灯時の signalTexture は暗いレンズなので、
        // 色はテクスチャ側が持っている (グループ名から色を推測してはいけない — light1/light2/light3 は
        // 踏切の警報灯と名前が衝突していて、どの現示でも赤く塗られていた)。
        String lightTexture = definition.getEmissiveTexture();
        boolean useLightTexture = blockEntity.isSignal() && lightTexture != null && !lightTexture.isBlank();
        // テクスチャ差し替えマップは毎フレーム作らず定義ごとに使い回す (モデルキャッシュのキーにも使われる)。
        MqoModelLoader.MqoModel emissiveModel = MqoModelLoader.loadModelFromPack(
            definition.getPackName(),
            definition.getModelFile(),
            useLightTexture
                ? LIGHT_TEXTURE_OVERRIDES.computeIfAbsent(definition.getId(), id -> Map.of("default", lightTexture))
                : definition.getTextureOverrides(),
            "",
            definition.isSmoothing()
        );
        if (emissiveModel == null) {
            return;
        }
        // RTM signal scripts treat the active lamp groups as a separate emissive pass.
        // We mirror that here so packs light up even when the legacy script only toggles groups.
        for (String group : groups) {
            int[] color = useLightTexture ? SIGNAL_LIT_COLOR : signalColorForGroup(group);
            MqoModelLoader.renderModelColorOverlay(
                emissiveModel,
                poseStack,
                buffer,
                packedOverlay,
                candidate -> groupMatches(candidate, group),
                color[0], color[1], color[2], color[3]
            );
        }
    }

    private static boolean shouldRenderSupplementalActiveLights(InstalledObjectBlockEntity blockEntity,
                                                                InstalledObjectDefinition definition,
                                                                boolean customCrossingGateRendering) {
        if (blockEntity == null || definition == null) {
            return false;
        }
        if (customCrossingGateRendering) {
            return true;
        }
        // 踏切の警報灯は、スクリプト付きパックでも本体描画側で発光オーバーレイを出す。
        // 本家RTMの踏切スクリプトは pass2(全光量)で警報灯を交互描画するが、腕スクリプト等が
        // pass2 を出さない/RTMUのpass最適化で省かれると点灯しないため、ここで確実に発光させる
        // (resolveActiveLightGroups が getLightCount に応じて light1/light2 を交互+light3 を返す)。
        return true;
    }

    private static List<String> resolveActiveLightGroups(InstalledObjectBlockEntity blockEntity, InstalledObjectDefinition definition) {
        if (blockEntity == null || definition == null) {
            return List.of();
        }
        if (blockEntity.isSignal()) {
            int signal = blockEntity.getLegacySignalState();
            List<String> groups = selectSignalLightGroups(definition.getSignalLightGroups(), signal);
            if (groups.isEmpty()) {
                groups = fallbackSignalGroups(signal);
            }
            return groups == null ? List.of() : groups;
        }
        if (blockEntity.getCategory() == InstalledObjectCategory.CROSSING && blockEntity.isPowered()) {
            int state = Math.floorMod(blockEntity.getLightCount(), 2);
            InstalledObjectDefinition crossingDefinition = InstalledObjectRegistry.getById(blockEntity.getDefinitionId());
            String scriptPath = getCrossingScriptPath(crossingDefinition);
            if (scriptPath.contains("rendercrossinggate01")) {
                return state == 0 ? CROSSING_LIGHT_RIGHT : CROSSING_LIGHT_LEFT;
            }
            java.util.ArrayList<String> groups = new java.util.ArrayList<>();
            // 本家スクリプト準拠: light=0 → light2+light3、light=1 → light1+light3 を点灯。
            // light3(common)は両状態で常時点灯させる(モデルに無ければ無視される)。
            groups.addAll(state == 0 ? CROSSING_LIGHT_RIGHT_LEGACY : CROSSING_LIGHT_LEFT_LEGACY);
            groups.addAll(CROSSING_LIGHT_COMMON_LEGACY);
            // 近代命名(light_l/light_r)のパックにも対応。
            groups.addAll(state == 0 ? CROSSING_LIGHT_RIGHT : CROSSING_LIGHT_LEFT);
            return groups;
        }
        // 照明(LIGHT): レッドストーンで電力が入っている間、定義された発光パーツを全て点灯する。
        // パックは信号と同じ "lights": ["S(1) P(部品名)", ...] 形式で発光部を定義する。
        if (blockEntity.getCategory() == InstalledObjectCategory.LIGHT && blockEntity.isPowered()) {
            java.util.List<String> lit = new java.util.ArrayList<>();
            for (List<String> group : definition.getSignalLightGroups().values()) {
                if (group != null) lit.addAll(group);
            }
            return lit;
        }
        return List.of();
    }

    private record CrossingTransform(double pivotX, double pivotY, double pivotZ, double degrees) {}

    /**
     * グループ名 → 比較用に正規化した名前 (小文字化 + "_"/"-" 除去) のキャッシュ。
     * groupMatches は「毎フレーム × 設置物 × バッチ数 × 定義パーツ数」呼ばれるため、
     * ここで文字列を作ると使い捨ての String が大量に出る (GC 負荷)。
     */
    private static final Map<String, String> COMPACT_GROUP_NAMES = new ConcurrentHashMap<>();

    private static String compactGroupName(String name) {
        String cached = COMPACT_GROUP_NAMES.get(name);
        if (cached != null) {
            return cached;
        }
        String compact = name.toLowerCase(java.util.Locale.ROOT).replace("_", "").replace("-", "");
        // グループ名は有限 (モデルのパーツ名 + 定義のパーツ名) なので上限を切らずに保持できる。
        COMPACT_GROUP_NAMES.put(name, compact);
        return compact;
    }

    private static boolean groupMatches(String candidate, String expected) {
        if (candidate == null || expected == null) {
            return false;
        }
        // 小文字化だけの一致も compact 同士の一致に含まれる ("_"/"-" を落としても
        // 同名なら等しい) ため、正規化 1 回で従来と同じ判定になる。
        return compactGroupName(candidate).equals(compactGroupName(expected));
    }

    /**
     * 本家 BasicSignalPartsRenderer の点灯パーツ選択。
     * S(n) を昇順に見て、最初に「現示 <= n」となるエントリ *だけ* を点灯する。
     */
    private static List<String> selectSignalLightGroups(Map<Integer, List<String>> lights, int signal) {
        if (lights == null || lights.isEmpty() || signal <= 0) {
            return List.of();
        }
        int level = Math.min(signal, MAX_SIGNAL_LEVEL);
        int matched = Integer.MAX_VALUE;
        for (int declared : lights.keySet()) {
            if (level <= declared && declared < matched) {
                matched = declared;
            }
        }
        if (matched == Integer.MAX_VALUE) {
            return List.of();
        }
        List<String> groups = lights.get(matched);
        return groups == null ? List.of() : groups;
    }

    private static List<String> fallbackSignalGroups(int legacyState) {
        return switch (legacyState) {
            case 1 -> List.of("light4");
            case 2 -> List.of("light4", "light3");
            case 3 -> List.of("light3");
            case 4 -> List.of("light3", "light5");
            case 5 -> List.of("light2");
            case 6 -> List.of("light1", "light5");
            case 7 -> List.of("light1", "light2");
            default -> List.of();
        };
    }

    private static int[] signalColorForGroup(String group) {
        String lower = group == null ? "" : group.toLowerCase();
        // α255 = 完全発光。半透明だと暗い下地 (夜間の世界光) と混ざって
        // 「信号/踏切の光が暗い」見た目になるため不透明でフルブライト描画する
        if (CROSSING_LIGHT_LEFT.contains(lower) || CROSSING_LIGHT_RIGHT.contains(lower)
            || CROSSING_LIGHT_LEFT_LEGACY.contains(lower) || CROSSING_LIGHT_RIGHT_LEGACY.contains(lower)
            || CROSSING_LIGHT_COMMON_LEGACY.contains(lower)) {
            return new int[] {255, 72, 48, 255};
        }
        if (RED_GROUPS.contains(lower)) {
            return new int[] {255, 56, 32, 255};
        }
        if (YELLOW_GROUPS.contains(lower)) {
            return new int[] {255, 210, 64, 255};
        }
        if (GREEN_GROUPS.contains(lower)) {
            return new int[] {64, 255, 120, 255};
        }
        return new int[] {255, 255, 255, 230};
    }

    // ★@Override を付けないこと: これは NeoForge が足したメソッドで、バニラには無い
    public @NotNull AABB getRenderBoundingBox(InstalledObjectBlockEntity blockEntity) {
        if (blockEntity.getCategory() == InstalledObjectCategory.WIRE && blockEntity.getWireStart() != null && blockEntity.getWireEnd() != null) {
            Vec3 a = Vec3.atCenterOf(blockEntity.getWireStart());
            Vec3 b = Vec3.atCenterOf(blockEntity.getWireEnd());
            return new AABB(a, b).inflate(2.0D);
        }
        return new AABB(blockEntity.getBlockPos()).inflate(4.0D);
    }

    @Override
    public boolean shouldRenderOffScreen(InstalledObjectBlockEntity blockEntity) {
        return blockEntity.getCategory() == InstalledObjectCategory.WIRE;
    }

    @Override
    public int getViewDistance() {
        return 192;
    }
}
