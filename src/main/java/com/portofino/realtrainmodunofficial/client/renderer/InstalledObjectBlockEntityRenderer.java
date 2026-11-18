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
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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
    /** 信号の点灯用テクスチャ差し替えマップ (定義ID → overrides)。 */
    private static final Map<String, Map<String, String>> LIGHT_TEXTURE_OVERRIDES = new ConcurrentHashMap<>();

    public InstalledObjectBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(InstalledObjectBlockEntity blockEntity, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {
        long profilerStart = ClientRenderProfiler.begin();
        InstalledObjectDefinition definition = InstalledObjectRegistry.getById(blockEntity.getDefinitionId());
        // 本家 RenderElectricalWiring.renderAllWire: root の接続…
        boolean drewConnectionWire = renderConnectionWires(blockEntity, poseStack, buffer, packedLight, packedOverlay);
        if (definition == null) {
            ClientRenderProfiler.endInstalledObject(profilerStart);
            return;
        }
        // 本家の既定旗 (textures/flag/Flag_*.json)。
        if (blockEntity.getCategory() == InstalledObjectCategory.FLAG
                && definition.getFlagParams() != null) {
            TextureFlagRenderer.render(blockEntity, definition, partialTick, poseStack, buffer, packedLight);
            ClientRenderProfiler.endInstalledObject(profilerStart);
            return;
        }
        Vec3 cameraPos = net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        Vec3 center = blockEntity.getRenderCenter();
        double cameraDistanceSq = cameraPos.distanceToSqr(center);
        // 旧形式ワイヤー (中間ブロック式 / 旧ワールドの書き換え済みタイル)。
        if (!drewConnectionWire
                && blockEntity.getWireStart() != null && blockEntity.getWireEnd() != null
                && blockEntity.getWireStart().equals(blockEntity.getBlockPos())) {
            renderWire(blockEntity, definition, poseStack, buffer, cameraDistanceSq, cameraPos, packedLight, packedOverlay);
        }
        // ワイヤー定義のモデルは架線のジオメトリそのもの (本家 ModelSetWire)。
        if (blockEntity.getCategory() == InstalledObjectCategory.WIRE) {
            ClientRenderProfiler.endInstalledObject(profilerStart);
            return;
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
                        poseStack.translate(0.5D, 0.5D, 0.5D);
                        Vec3 renderOffset = blockEntity.getRenderOffset();
                        poseStack.translate(renderOffset.x, renderOffset.y, renderOffset.z);
                        applyAdjustments(poseStack, blockEntity);
                        poseStack.translate(definition.getModelOffset().x, definition.getModelOffset().y, definition.getModelOffset().z);
                        poseStack.scale(definition.getModelScale(), definition.getModelScale(), definition.getModelScale());
                    } else if (definition.isRotateByMetadata()) {
                        // 本家 RenderMachine (rotateByMetadata=true の照明 = サーチ…
                        poseStack.translate(0.5D, 0.0D, 0.5D);
                        Vec3 renderOffset = blockEntity.getRenderOffset();
                        poseStack.translate(renderOffset.x, renderOffset.y, renderOffset.z);
                        applyAdjustments(poseStack, blockEntity);
                        poseStack.translate(0.0D, 0.5D, 0.0D);
                        // 取り付け面を持たない (この修正より前に置かれた) 物は、本家の既定である meta=1 (上向き…
                        int mountFace = blockEntity.getMountFace() >= 0 ? blockEntity.getMountFace() : 1;
                        applyLightMetadataRotation(poseStack, mountFace);
                        poseStack.translate(0.0D, -0.5D, 0.0D);
                        // 本家 getRotation = round(180 - playerYaw)。
                        float lightYaw = blockEntity.getYaw();
                        if (mountFace == 0) {
                            lightYaw = -lightYaw;
                        }
                        poseStack.mulPose(Axis.YP.rotationDegrees(lightYaw));
                        poseStack.translate(definition.getModelOffset().x, definition.getModelOffset().y, definition.getModelOffset().z);
                        poseStack.scale(definition.getModelScale(), definition.getModelScale(), definition.getModelScale());
                    } else if (blockEntity.getMountFace() >= 0 || isConnectorCategory(blockEntity.getCategory())) {
                        // 本家 RenderElectricalWiring.renderConnector 準拠: ブロッ…
                        poseStack.translate(0.5D, 0.5D, 0.5D);
                        Vec3 renderOffset = blockEntity.getRenderOffset();
                        poseStack.translate(renderOffset.x, renderOffset.y, renderOffset.z);
                        applyAdjustments(poseStack, blockEntity);
                        applyHonkeMountFaceRotation(poseStack,
                            blockEntity.getMountFace() >= 0 ? blockEntity.getMountFace() : 1);
                        poseStack.translate(definition.getModelOffset().x, definition.getModelOffset().y, definition.getModelOffset().z);
                        poseStack.scale(definition.getModelScale(), definition.getModelScale(), definition.getModelScale());
                    } else if (blockEntity.getCategory() == InstalledObjectCategory.SCAFFOLD
                            || blockEntity.getCategory() == InstalledObjectCategory.STAIR
                            || blockEntity.getCategory() == InstalledObjectCategory.PLANT
                            || blockEntity.getCategory() == InstalledObjectCategory.MECHANISM) {
                        // 本家 RenderOrnament / RenderMechanism: 原点はブロック中心 (+…
                        poseStack.translate(0.5D, 0.5D, 0.5D);
                        Vec3 renderOffset = blockEntity.getRenderOffset();
                        poseStack.translate(renderOffset.x, renderOffset.y, renderOffset.z);
                        applyAdjustments(poseStack, blockEntity);
                        if (blockEntity.getCategory() == InstalledObjectCategory.PLANT) {
                            // 本家は植物だけ setRotation (プレイヤー向き 15 度刻み) を持つ
                            poseStack.mulPose(Axis.YP.rotationDegrees(blockEntity.getYaw()));
                        } else if (blockEntity.getCategory() == InstalledObjectCategory.MECHANISM) {
                            // 本家 RenderMechanism: dir * 90 度
                            poseStack.mulPose(Axis.YP.rotationDegrees(
                                net.minecraft.util.Mth.wrapDegrees(blockEntity.getDir() * 90.0F)));
                        }
                        // ★足場/階段はここで回さない。本家は rotation を一度も設定せず (常に 0)、 向きはス…
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
                        if (blockEntity.getMountPitch() != 0.0F) {
                            poseStack.mulPose(Axis.XP.rotationDegrees(blockEntity.getMountPitch()));
                        }
                        if (blockEntity.getMountRoll() != 0.0F) {
                            poseStack.mulPose(Axis.ZP.rotationDegrees(blockEntity.getMountRoll()));
                        }
                        poseStack.translate(definition.getModelOffset().x, definition.getModelOffset().y, definition.getModelOffset().z);
                        poseStack.scale(definition.getModelScale(), definition.getModelScale(), definition.getModelScale());
                    }
                    // 踏切/改札: 本家式スクリプト描画 (MachinePartsRenderer + Nashorn…
                    boolean machineScriptCategory = blockEntity.getCategory() == InstalledObjectCategory.CROSSING
                            || blockEntity.getCategory() == InstalledObjectCategory.TICKET_GATE
                            || blockEntity.getCategory() == InstalledObjectCategory.SIGNAL
                            || blockEntity.getCategory() == InstalledObjectCategory.FLUORESCENT
                            || blockEntity.getCategory() == InstalledObjectCategory.OVERHEAD_LINE_POLE
                            || blockEntity.getCategory() == InstalledObjectCategory.PIPE
                            || blockEntity.getCategory() == InstalledObjectCategory.POINT
                            // ★照明もここに含める。本家の RenderMirrorBall.js / RenderRevolv…
                            || blockEntity.getCategory() == InstalledObjectCategory.LIGHT
                            // ★碍子/コネクタ (架線柱含む) もここ。
                            || blockEntity.getCategory() == InstalledObjectCategory.SCAFFOLD
                            || blockEntity.getCategory() == InstalledObjectCategory.STAIR
                            || blockEntity.getCategory() == InstalledObjectCategory.PLANT
                            || blockEntity.getCategory() == InstalledObjectCategory.FLAG
                            || blockEntity.getCategory() == InstalledObjectCategory.MECHANISM
                            || isConnectorCategory(blockEntity.getCategory());
                    boolean hasMachineScript = definition.getScriptPath() != null && !definition.getScriptPath().isBlank();
                    // これらのブロック検知信号パックは json の machineType が "Light" のため…
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
                            // 警報灯/現示灯の発光オーバーレイ (スクリプトの pass2 は diffuse で減光する こと…
                            boolean scriptDrivenSignal = blockEntity.getCategory() == InstalledObjectCategory.SIGNAL
                                    && machineScripted.isBlockDetection();
                            // ★踏切には overlay を掛けない。 Light テクスチャで光らせる (MachineScr…
                            if (blockEntity.getCategory() == InstalledObjectCategory.SIGNAL
                                    && !scriptDrivenSignal) {
                                renderActiveLights(blockEntity, definition, poseStack, buffer, packedOverlay);
                            }
                            poseStack.popPose();
                            pushed = false;
                            // 本家 RenderSignal.renderBaseTileEntity: 置き換えた元ブロック…
                            renderSignalBase(blockEntity, partialTick, poseStack, buffer, packedLight, packedOverlay);
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
                    // ★改札もスクリプト経路を通す。 以前は「スクリプト経路だと扉の開閉 transform が渡らず開…
                    boolean takeScriptPath = !customCrossingGateRendering
                        && definition.getScriptPath() != null && !definition.getScriptPath().isBlank();
                    boolean lightsDrawnInBranch = false;
                    if (takeScriptPath) {
                        com.portofino.realtrainmodunofficial.client.render.InstalledObjectScriptCache.render(
                            blockEntity, model, poseStack, buffer, packedLight, packedOverlay);
                    } else if (blockEntity.getCategory() == InstalledObjectCategory.SIGNAL
                            && definition.isRotateBody()) {
                        // 本家 BasicSignalPartsRenderer: 柱 (fixture) は設置面の4方位…
                        if (!definition.getFixtureObjects().isEmpty()) {
                            MqoModelLoader.GroupPredicate fixtureFilter =
                                groupName -> matchesAnyRenderObject(groupName, definition.getFixtureObjects());
                            MqoModelLoader.renderModelWithoutScript(model, poseStack, buffer, packedLight, packedOverlay,
                                false, fixtureFilter, null, blockEntity);
                        }
                        float bodyRot = net.minecraft.util.Mth.wrapDegrees(
                            blockEntity.getSignalBodyYaw() - blockEntity.getYaw());
                        Vec3 bodyPos = definition.getScriptBodyPos();
                        poseStack.pushPose();
                        poseStack.translate(bodyPos.x, bodyPos.y, bodyPos.z);
                        poseStack.mulPose(Axis.YP.rotationDegrees(bodyRot));
                        poseStack.translate(-bodyPos.x, -bodyPos.y, -bodyPos.z);
                        MqoModelLoader.renderModelWithoutScript(model, poseStack, buffer, packedLight, packedOverlay,
                            false, filter, transform, blockEntity);
                        if (!veryFar && shouldRenderSupplementalActiveLights(blockEntity, definition, false)) {
                            renderActiveLights(blockEntity, definition, poseStack, buffer, packedOverlay);
                            lightsDrawnInBranch = true;
                        }
                        poseStack.popPose();
                    } else {
                        MqoModelLoader.renderModelWithoutScript(model, poseStack, buffer, packedLight, packedOverlay, false, filter, transform, blockEntity);
                        if (model.hasTranslucentBatches() && cameraDistanceSq < translucentThreshold * translucentThreshold) {
                            MqoModelLoader.renderModelWithoutScript(model, poseStack, buffer, packedLight, packedOverlay, true, filter, transform, blockEntity);
                        }
                    }
                    if (!lightsDrawnInBranch && !veryFar && shouldRenderSupplementalActiveLights(blockEntity, definition, customCrossingGateRendering)) {
                        renderActiveLights(blockEntity, definition, poseStack, buffer, packedOverlay);
                    }
                    poseStack.popPose();
                    pushed = false;
                    // 本家 RenderSignal.renderBaseTileEntity: 置き換えた元ブロック…
                    renderSignalBase(blockEntity, partialTick, poseStack, buffer, packedLight, packedOverlay);
                } catch (Throwable t) {
                    if (pushed) {
                        try { poseStack.popPose(); } catch (Throwable ignored) {}
                    }
                    com.portofino.realtrainmodunofficial.RealTrainModUnofficial.LOGGER.warn(
                        "Installed object render failed for {}.", definition.getId(), t);
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




    /** 本家 RenderSignal.renderBaseTileEntity: 信号が置き換えた元ブロ… */
    private void renderSignalBase(InstalledObjectBlockEntity signal, float partialTick, PoseStack poseStack,
                                  MultiBufferSource buffer, int packedLight, int packedOverlay) {
        if (signal.getCategory() != InstalledObjectCategory.SIGNAL) {
            return;
        }
        net.minecraft.nbt.CompoundTag orig = signal.getSignalOrigTileNbt();
        net.minecraft.world.level.Level level = signal.getLevel();
        if (orig == null || level == null) {
            return;
        }
        try {
            InstalledObjectBlockEntity base =
                com.portofino.realtrainmodunofficial.RealTrainModUnofficialBlockEntities.INSTALLED_OBJECT.get()
                    .create(signal.getBlockPos(), signal.getBlockState());
            if (base == null) {
                return;
            }
            base.setLevel(level);
            base.loadWithComponents(orig, level.registryAccess());
            // 元が設置物でない (普通のブロック) / 元も信号 (無限再帰) は描かない。
            if (base.getDefinition() != null && base.getCategory() != InstalledObjectCategory.SIGNAL) {
                render(base, partialTick, poseStack, buffer, packedLight, packedOverlay);
            }
        } catch (Throwable t) {
            // 元タイルが復元できない場合は描くものが無いので黙って諦める
        }
    }

    private void renderWire(InstalledObjectBlockEntity blockEntity, InstalledObjectDefinition definition,
                            PoseStack poseStack, MultiBufferSource buffer,
                            double cameraDistanceSq, Vec3 cameraPos, int packedLight, int packedOverlay) {
        renderWireBetween(blockEntity, definition, blockEntity.getWireStart(), blockEntity.getWireEnd(),
            poseStack, buffer, cameraDistanceSq, cameraPos, packedLight, packedOverlay);
    }

    private void renderWireBetween(InstalledObjectBlockEntity blockEntity, InstalledObjectDefinition definition,
                            BlockPos startPos, BlockPos endPos, PoseStack poseStack, MultiBufferSource buffer,
                            double cameraDistanceSq, Vec3 cameraPos, int packedLight, int packedOverlay) {
        BlockPos start = startPos;
        BlockPos end = endPos;
        if (start == null || end == null) {
            return;
        }
        // BlockEntityRenderer の poseStack 原点はブロックの「角(atLowe…
        Vec3 origin = Vec3.atLowerCornerOf(blockEntity.getBlockPos());
        Vec3 fromWorld = resolveWireAttachPoint(blockEntity.getLevel(), start);
        Vec3 toWorld = resolveWireAttachPoint(blockEntity.getLevel(), end);
        if (fromWorld == null || toWorld == null) {
            return;
        }
        Vec3 from = fromWorld.subtract(origin);
        Vec3 to = toWorld.subtract(origin);

        MqoModelLoader.MqoModel model = hasRenderableWireModel(definition)
            ? MqoModelLoader.loadModelFromPack(definition.getPackName(), definition.getModelFile(),
                definition.getTextureOverrides(), definition.getScriptPath(), definition.isSmoothing())
            : null;

        // ★本家 RenderElectricalWiring.renderWire と同じ形。
        com.portofino.realtrainmodunofficial.client.render.WireScriptRenderers.Scripted wire =
            com.portofino.realtrainmodunofficial.client.render.WireScriptRenderers.get(definition);
        if (wire != null) {
            wire.render(blockEntity, from, to, 1.0F, poseStack, buffer, packedLight, packedOverlay, model);
        }
    }









    /** 本家 RenderElectricalWiring.renderAllWire の移植。 */
    private boolean renderConnectionWires(InstalledObjectBlockEntity blockEntity, PoseStack poseStack,
                                          MultiBufferSource buffer, int packedLight, int packedOverlay) {
        java.util.List<jp.ngt.rtm.electric.Connection> connections = blockEntity.getConnectionList();
        if (connections.isEmpty()) {
            return false;
        }
        Vec3 cameraPos = net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double cameraDistanceSq = cameraPos.distanceToSqr(blockEntity.getRenderCenter());
        boolean drew = false;
        for (int i = 0; i < connections.size(); i++) {
            jp.ngt.rtm.electric.Connection connection = connections.get(i);
            if (!connection.isRoot || !connection.type.isVisible) {
                continue;
            }
            InstalledObjectDefinition wireDef = resolveWireDefinition(connection.wireName);
            if (wireDef == null) {
                continue;
            }
            // ★本家 RenderElectricalWiring.getConnectedTarget の T…
            if (connection.type == jp.ngt.rtm.electric.Connection.ConnectionType.TO_PLAYER) {
                renderWireToPlayer(blockEntity, wireDef, poseStack, buffer, packedLight, packedOverlay);
                drew = true;
                continue;
            }
            renderWireBetween(blockEntity, wireDef, blockEntity.getBlockPos(),
                new BlockPos(connection.x, connection.y, connection.z),
                poseStack, buffer, cameraDistanceSq, cameraPos, packedLight, packedOverlay);
            drew = true;
        }
        return drew;
    }

    /** 接続待ちの仮ワイヤーをローカルプレイヤーの手元へ描く (本家 getConnectedTarget… */
    private void renderWireToPlayer(InstalledObjectBlockEntity blockEntity, InstalledObjectDefinition definition,
                                    PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay) {
        Vec3 toWorld = localPlayerHandPos();
        Vec3 fromWorld = resolveWireAttachPoint(blockEntity.getLevel(), blockEntity.getBlockPos());
        if (toWorld == null || fromWorld == null) {
            return;
        }
        Vec3 origin = Vec3.atLowerCornerOf(blockEntity.getBlockPos());
        Vec3 from = fromWorld.subtract(origin);
        Vec3 to = toWorld.subtract(origin);
        MqoModelLoader.MqoModel model = hasRenderableWireModel(definition)
            ? MqoModelLoader.loadModelFromPack(definition.getPackName(), definition.getModelFile(),
                definition.getTextureOverrides(), definition.getScriptPath(), definition.isSmoothing())
            : null;
        com.portofino.realtrainmodunofficial.client.render.WireScriptRenderers.Scripted wire =
            com.portofino.realtrainmodunofficial.client.render.WireScriptRenderers.get(definition);
        if (wire != null) {
            wire.render(blockEntity, from, to, 1.0F, poseStack, buffer, packedLight, packedOverlay, model);
        }
    }

    /** 本家 RenderElectricalWiring.getConnectedTarget の TO… */
    public static Vec3 localPlayerHandPos() {
        net.minecraft.client.player.LocalPlayer p =
            net.minecraft.client.Minecraft.getInstance().player;
        if (p == null) {
            return null;
        }
        float partialTick = 1.0F;
        float f9 = p.getAttackAnim(partialTick);
        float f10 = net.minecraft.util.Mth.sin(net.minecraft.util.Mth.sqrt(f9) * (float) Math.PI);
        jp.ngt.ngtlib.math.Vec3 v = new jp.ngt.ngtlib.math.Vec3(-0.46D, -0.2D, 0.65D);
        v = v.rotateAroundX(-net.minecraft.util.Mth.lerp(partialTick, p.xRotO, p.getXRot()));
        v = v.rotateAroundY(-net.minecraft.util.Mth.lerp(partialTick, p.yRotO, p.getYRot()));
        v = v.rotateAroundY(f10 * 0.5F);
        v = v.rotateAroundX(-f10 * 0.7F);
        double x0 = net.minecraft.util.Mth.lerp(partialTick, p.xOld, p.getX()) + v.getX();
        double y0 = net.minecraft.util.Mth.lerp(partialTick, p.yOld, p.getY()) + v.getY();
        double z0 = net.minecraft.util.Mth.lerp(partialTick, p.zOld, p.getZ()) + v.getZ();
        if (!net.minecraft.client.Minecraft.getInstance().options.getCameraType().isFirstPerson()) {
            float f11 = (float) Math.toRadians(net.minecraft.util.Mth.lerp(partialTick, p.yBodyRotO, p.yBodyRot));
            double d7 = net.minecraft.util.Mth.sin(f11);
            double d9 = net.minecraft.util.Mth.cos(f11);
            float eye = 1.62F;
            x0 = net.minecraft.util.Mth.lerp(partialTick, p.xOld, p.getX()) - d9 * 0.35D - d7 * 0.85D;
            y0 = net.minecraft.util.Mth.lerp(partialTick, p.yOld, p.getY()) + eye - 0.45D;
            z0 = net.minecraft.util.Mth.lerp(partialTick, p.zOld, p.getZ()) - d7 * 0.35D + d9 * 0.85D;
        }
        return new Vec3(x0, y0, z0);
    }

    /** 接続が持つワイヤーモデル名 → 定義。完全 ID → 末尾名の順で解決する。 */
    public static InstalledObjectDefinition resolveWireDefinition(String wireName) {
        if (wireName == null || wireName.isEmpty()) {
            return null;
        }
        InstalledObjectDefinition def = InstalledObjectRegistry.getById(wireName);
        if (def == null) {
            def = InstalledObjectRegistry.getByBareName(
                wireName.substring(wireName.lastIndexOf(':') + 1), InstalledObjectCategory.WIRE);
        }
        return def;
    }

    /** 本家 TileEntityConnectorBase 系 (碍子・入出力コネクタ・架線柱)。 */
    private static boolean isConnectorCategory(InstalledObjectCategory category) {
        return category == InstalledObjectCategory.INSULATOR
            || category == InstalledObjectCategory.CONNECTOR_INPUT
            || category == InstalledObjectCategory.CONNECTOR_OUTPUT;
    }

    public static boolean hasRenderableWireModel(InstalledObjectDefinition definition) {
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

    /** 接続点の座標。解決できなければ null。 */
    public static Vec3 resolveWireAttachPoint(Level level, BlockPos pos) {
        if (level != null) {
            jp.ngt.rtm.entity.EntityElectricalWiring entityEndpoint =
                    jp.ngt.rtm.entity.EntityElectricalWiring.find(level, pos);
            if (entityEndpoint != null) {
                return new Vec3(entityEndpoint.getPreciseRenderPosX(),
                        entityEndpoint.getPreciseRenderPosY(), entityEndpoint.getPreciseRenderPosZ());
            }
        }
        if (level != null && level.getBlockEntity(pos) instanceof InstalledObjectBlockEntity endpoint) {
            InstalledObjectDefinition endpointDef = InstalledObjectRegistry.getById(endpoint.getDefinitionId());
            if (endpointDef != null) {
                // ★取付点はタイルに聞く (本家 TileEntityConnectorBase.wirePos)。
                jp.ngt.ngtlib.math.Vec3 tileWirePos = endpoint.getWirePos();
                Vec3 wp = tileWirePos != null
                    ? new Vec3(tileWirePos.getX(), tileWirePos.getY(), tileWirePos.getZ())
                    : endpointDef.getWireAttachPos();

                // ★ 面に取り付けた碍子 (通常の架線柱はこれ)。
                if (endpoint.getMountFace() >= 0 || endpoint.getMountPitch() == 0.0F) {
                    int face = endpoint.getMountFace() >= 0 ? endpoint.getMountFace() : 1;
                    jp.ngt.ngtlib.math.Vec3 rotated = rotateWirePosByMountFace(
                        new jp.ngt.ngtlib.math.Vec3(wp.x, wp.y, wp.z), face);
                    return Vec3.atLowerCornerOf(pos)
                        .add(0.5D, 0.5D, 0.5D)
                        .add(endpoint.getRenderOffset())
                        .add(rotated.getX(), rotated.getY(), rotated.getZ());
                }

                // 傾けて置いてある物 (列車検知器など) だけは、モデルが底面中央 + (180-yaw) で 描か…
                Vec3 tilted = rotateX(new Vec3(wp.x, wp.y, wp.z), endpoint.getMountPitch());
                Vec3 rotated = rotateY(tilted, 180.0D - endpoint.getYaw());
                return Vec3.atLowerCornerOf(pos)
                    .add(0.5D, 0.0D, 0.5D)
                    .add(endpoint.getRenderOffset())
                    .add(rotated);
            }
        }
        // 変換器などタイルはあるが設置物ではない相手はブロック中心 (従来動作)。
        if (level != null && level.getBlockEntity(pos) != null) {
            return Vec3.atCenterOf(pos);
        }
        // タイルが無い (相手が壊された・未ロード) — 本家 getConnectedTarget は描か…
        return null;
    }

    /** 本家 TileEntityConnectorBase.updateWirePos の忠実移植。 */
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

    // Axis.XP.rotationDegrees と同じ +X 軸まわりの右手回転(描画と接続点を一…
    private static Vec3 rotateX(Vec3 vec, double degrees) {
        if (degrees == 0.0D || vec == null || vec.equals(Vec3.ZERO)) {
            return vec == null ? Vec3.ZERO : vec;
        }
        double radians = Math.toRadians(degrees);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        return new Vec3(vec.x, vec.y * cos - vec.z * sin, vec.y * sin + vec.z * cos);
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

    /** 改札(TICKET_GATE)の扉(doorL/doorR)を、閉時にヒンジ周りで回して通路を塞ぐ。 */



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

    /** 本家 GuiChangeOffset の微調整 (scale → roll → pitch → y… */
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

    /** 本家 RenderElectricalWiring の meta (クリック面 0-5) 回転。 */
    /** 本家 RenderMachine の rotateByMetadata 面回転 (meta = ク… */
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
        return matchesAnyRenderObject(groupName, definition.getRenderObjects());
    }

    /** groupName が指定オブジェクト名のどれかに一致するか (本家 modelParts の o… */
    private static boolean matchesAnyRenderObject(String groupName, java.util.List<String> expected) {
        if (expected == null || expected.isEmpty()) {
            return false;
        }
        for (String name : expected) {
            if (groupMatches(groupName, name)) {
                return true;
            }
        }
        return false;
    }

    /** 本家 RenderSignBoard の移植。 */
    /** 本家 RenderRailroadSign の移植。 */
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
        final float w = 0.25F;      // 板の半径 (本家 w)
        final float d = 0.0675F;    // 板の Z 方向オフセット (本家 d)

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
        // ★ VertexConsumer は「使う直前に」取る。
        if (texture != null) {
            VertexConsumer plate = buffer.getBuffer(RenderType.entityCutoutNoCull(texture));
            // 表: テクスチャそのまま
            signVertex(plate, pose, w, -w, d, 1.0F, 1.0F, packedLight, packedOverlay, 0xFFFFFF, 0.0F, 0.0F, 1.0F);
            signVertex(plate, pose, w, w, d, 1.0F, 0.0F, packedLight, packedOverlay, 0xFFFFFF, 0.0F, 0.0F, 1.0F);
            signVertex(plate, pose, -w, w, d, 0.0F, 0.0F, packedLight, packedOverlay, 0xFFFFFF, 0.0F, 0.0F, 1.0F);
            signVertex(plate, pose, -w, -w, d, 0.0F, 1.0F, packedLight, packedOverlay, 0xFFFFFF, 0.0F, 0.0F, 1.0F);

            // 裏: 本家は同じテクスチャを貼ったまま色 0 (黒) で塗る。 透明部分はそのまま抜ける。
            final float backD = d - 0.002F;
            signVertex(plate, pose, -w, -w, backD, 0.0F, 1.0F, packedLight, packedOverlay, 0x000000, 0.0F, 0.0F, -1.0F);
            signVertex(plate, pose, -w, w, backD, 0.0F, 0.0F, packedLight, packedOverlay, 0x000000, 0.0F, 0.0F, -1.0F);
            signVertex(plate, pose, w, w, backD, 1.0F, 0.0F, packedLight, packedOverlay, 0x000000, 0.0F, 0.0F, -1.0F);
            signVertex(plate, pose, w, -w, backD, 1.0F, 1.0F, packedLight, packedOverlay, 0x000000, 0.0F, 0.0F, -1.0F);
        }
        poseStack.popPose();

        // ---- ポール ---- 本家: 吊り下げのときはポールの根元を 0.5 下げる (板から天井ま…
        if (hanging) {
            poseStack.translate(0.0F, -0.5F, 0.0F);
        }
        // 本家 NGTRenderer.renderPole(tessellator, 0.0625F, 1…
        VertexConsumer solid = buffer.getBuffer(RenderType.entityCutoutNoCull(SolidTexture.white()));
        renderPole(solid, poseStack.last(), 0.0625F, 1.5F, 0x404040, packedLight, packedOverlay);
        poseStack.popPose();
    }

    /** 本家 NGTRenderer.renderPole 相当の 16 角柱。 */
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

    /** 本家 RenderSignBoard の meta/dir 分岐そのまま: 板を設置面へ寄せる。 */
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
        // 本家 BasicSignalPartsRenderer の点灯パス: 現示灯は「点灯用テクスチャ…
        String lightTexture = definition.getEmissiveTexture();
        boolean useLightTexture = blockEntity.isSignal() && lightTexture != null && !lightTexture.isBlank();
        // テクスチャ差し替えマップは毎フレーム作らず定義ごとに使い回す (モデルキャッシュのキーにも使われる…
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
        // RTM signal scripts treat the active lamp groups a…
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
        return true;
    }

    private static List<String> resolveActiveLightGroups(InstalledObjectBlockEntity blockEntity, InstalledObjectDefinition definition) {
        if (blockEntity == null || definition == null) {
            return List.of();
        }
        if (blockEntity.isSignal()) {
            int signal = blockEntity.getLegacySignalState();
            // ★定義に光グループが無いなら光らせない。本家はグループ名を決め打ちしない (信号の点灯は Sign…
            List<String> groups = selectSignalLightGroups(definition.getSignalLightGroups(), signal);
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
            // 本家スクリプト準拠: light=0 → light2+light3、light=1 → ligh…
            groups.addAll(state == 0 ? CROSSING_LIGHT_RIGHT_LEGACY : CROSSING_LIGHT_LEFT_LEGACY);
            groups.addAll(CROSSING_LIGHT_COMMON_LEGACY);
            // 近代命名(light_l/light_r)のパックにも対応。
            groups.addAll(state == 0 ? CROSSING_LIGHT_RIGHT : CROSSING_LIGHT_LEFT);
            return groups;
        }
        // 照明(LIGHT): レッドストーンで電力が入っている間、定義された発光パーツを全て点灯する。
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

    /** グループ名 → 比較用に正規化した名前 (小文字化 + "_"/"-" 除去) のキャッシュ。 */
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
        // 小文字化だけの一致も compact 同士の一致に含まれる ("_"/"-" を落としても 同名な…
        return compactGroupName(candidate).equals(compactGroupName(expected));
    }

    /** 本家 BasicSignalPartsRenderer の点灯パーツ選択。 */
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


    private static int[] signalColorForGroup(String group) {
        String lower = group == null ? "" : group.toLowerCase();
        // α255 = 完全発光。半透明だと暗い下地 (夜間の世界光) と混ざって 「信号/踏切の光が暗い」…
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

    @Override
    public @NotNull AABB getRenderBoundingBox(InstalledObjectBlockEntity blockEntity) {
        if (blockEntity.getCategory() == InstalledObjectCategory.WIRE && blockEntity.getWireStart() != null && blockEntity.getWireEnd() != null) {
            Vec3 a = Vec3.atCenterOf(blockEntity.getWireStart());
            Vec3 b = Vec3.atCenterOf(blockEntity.getWireEnd());
            return new AABB(a, b).inflate(2.0D);
        }
        AABB box = new AABB(blockEntity.getBlockPos()).inflate(4.0D);
        // 接続式 (本家構造) の架線: root 接続の相手まで覆う。
        java.util.List<jp.ngt.rtm.electric.Connection> connections = blockEntity.getConnectionList();
        for (int i = 0; i < connections.size(); i++) {
            jp.ngt.rtm.electric.Connection c = connections.get(i);
            if (c.isRoot && c.type.isVisible) {
                box = box.minmax(new AABB(new BlockPos(c.x, c.y, c.z)).inflate(2.0D));
            }
        }
        return box;
    }

    /** ★架線でも false のままにする。 true にすると「グローバル BlockEntity」と… */
    @Override
    public boolean shouldRenderOffScreen(InstalledObjectBlockEntity blockEntity) {
        return false;
    }

    @Override
    public int getViewDistance() {
        return 192;
    }
}
