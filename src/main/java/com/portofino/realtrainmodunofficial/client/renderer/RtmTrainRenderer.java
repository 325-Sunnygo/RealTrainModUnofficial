package com.portofino.realtrainmodunofficial.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.portofino.realtrainmodunofficial.client.ClientRenderProfiler;
import com.portofino.realtrainmodunofficial.client.model.MqoModelLoader;
import com.portofino.realtrainmodunofficial.vehicle.VehicleDefinition;
import com.portofino.realtrainmodunofficial.vehicle.VehicleRegistry;
import jp.ngt.rtm.entity.train.EntityTrain;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

import java.util.Locale;

/** jp.ngt.rtm.entity.train.EntityTrain 用レンダラ (Phase… */
public class RtmTrainRenderer extends EntityRenderer<EntityTrain> {

    public RtmTrainRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(EntityTrain entity) {
        return ResourceLocation.withDefaultNamespace("missingno");
    }

    @Override
    public boolean shouldRender(EntityTrain entity, Frustum frustum, double camX, double camY, double camZ) {
        double half = Math.max(3.0D, entity.getConfig().trainDistance + 3.0D);
        AABB bounds = new AABB(
                entity.getX() - half, entity.getY() - 2.0D, entity.getZ() - half,
                entity.getX() + half, entity.getY() + 5.0D, entity.getZ() + half);
        return frustum.isVisible(bounds);
    }

    @Override
    public void render(EntityTrain entity, float entityYaw, float partialTicks, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        // 計測: 設置される列車は EntityTrain なのに、Profiler の Train カテゴ…
        long profilerStart =
            com.portofino.realtrainmodunofficial.client.ClientRenderProfiler.begin();
        com.portofino.realtrainmodunofficial.client.ClientRenderProfiler.beginTrainGeometry();
        try {
            renderTrain(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
        } finally {
            com.portofino.realtrainmodunofficial.client.ClientRenderProfiler.endTrainGeometry();
            com.portofino.realtrainmodunofficial.client.ClientRenderProfiler.endTrain(profilerStart);
        }
    }

    private final java.util.Map<EntityTrain,Integer> poseChecks=new java.util.WeakHashMap<>();
    private void reportInitialPose(EntityTrain entity,VehicleDefinition def,MqoModelLoader.MqoModel model) {
        int checked=poseChecks.getOrDefault(entity,0);
        if(checked>=2 || (checked==1 && entity.tickCount<40))return;
        poseChecks.put(entity,checked+1);
        com.portofino.realtrainmodunofficial.RealTrainModUnofficial.LOGGER.info(
            "[RTMU] vehicle pose: model={} tick={} yaw={} pitch={} roll={} previousRoll={} scale={} bounds={}",
            def.getId(),entity.tickCount,entity.getYRot(),entity.getXRot(),entity.rotationRoll,entity.prevRotationRoll,def.getModelScale(),java.util.Arrays.toString(model.getSizeBox()));
    }

    private void renderTrain(EntityTrain entity, float entityYaw, float partialTicks, PoseStack poseStack,
                             MultiBufferSource buffer, int packedLight) {
        long tSec = ClientRenderProfiler.sec();
        VehicleDefinition def = VehicleRegistry.getById(entity.getModelName());
        if (def == null) {
            return;
        }
        MqoModelLoader.MqoModel model = MqoModelLoader.loadModelForVehicle(def);
        if (model == null) {
            return;
        }
        reportInitialPose(entity,def,model);
        // 本家 preRenderBody: 室内灯 ON かつ周囲が暗いなら車体の通常描画をフルブライトで…
        final int interiorLightState = entity.getTrainStateData(
            jp.ngt.rtm.entity.train.util.TrainState.TrainStateType.State_InteriorLight.id);
        final int bodyLight = TrainEntityRenderer.applyInteriorLighting(entity.level(),
            entity.getX(), entity.getY(), entity.getZ(),
            !def.getInteriorLights().isEmpty(), interiorLightState, packedLight);

        ClientRenderProfiler.secEnd(ClientRenderProfiler.SEC_LOOKUP, tSec);

        // 乗員を車体より先に描画する。車体は半透明バッチ (AlphaBlend) が深度を書くため、 列車…
        tSec = ClientRenderProfiler.sec();
        this.renderRidersFirst(entity, partialTicks, poseStack, buffer, packedLight);
        ClientRenderProfiler.secEnd(ClientRenderProfiler.SEC_RIDERS, tSec);

        poseStack.pushPose();
        try {
            // 本家 RenderVehicleBase: yaw → -pitch → roll → offset
            float yaw = Mth.rotLerp(partialTicks, entity.yRotO, entity.getYRot());
            poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
            float pitch = Mth.lerp(partialTicks, entity.xRotO, entity.getXRot());
            poseStack.mulPose(Axis.XP.rotationDegrees(-pitch));
            float roll = Mth.lerp(partialTicks, entity.prevRotationRoll, entity.rotationRoll);
            poseStack.mulPose(Axis.ZP.rotationDegrees(roll));

            poseStack.translate(def.getModelOffset().x, def.getModelOffset().y, def.getModelOffset().z);

            // ★前照灯・尾灯の光 (本家 RenderVehicleBase.renderLightEffect…
            renderLightEffect(entity, def, poseStack, buffer);
            // ★ボクセルモデル (.ngto/.ngtz) はここで縮尺を掛けない。
            if (!model.isVoxelModel()) {
                poseStack.scale(def.getModelScale(), def.getModelScale(), def.getModelScale());
            }

            // 本家式スクリプト描画 (Nashorn): 成功したらベイクドパスはスキップ
            tSec = ClientRenderProfiler.sec();
            com.portofino.realtrainmodunofficial.client.render.VehicleScriptRenderers.Scripted scripted =
                    com.portofino.realtrainmodunofficial.client.render.VehicleScriptRenderers.get(def);
            if (scripted != null) {
                scripted.render(entity, partialTicks, poseStack, buffer,
                    bodyLight, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, model);
            }
            ClientRenderProfiler.secEnd(ClientRenderProfiler.SEC_SCRIPTED, tSec);

            // ★スクリプトを持つ車両は、それが何も描かなくても素モデルへ逃がさない。
            if (scripted == null) {
                MqoModelLoader.GroupPredicate filter =
                        groupName -> shouldRenderGroup(groupName);

                // ★ ドアの開閉。 車両 JSON の door_left / door_right (「このグルー…
                java.util.Map<String, java.util.List<TrainEntityRenderer.PartsStep>> leftChains =
                        TrainEntityRenderer.partsChains(def.getLeftDoors());
                java.util.Map<String, java.util.List<TrainEntityRenderer.PartsStep>> rightChains =
                        TrainEntityRenderer.partsChains(def.getRightDoors());
                var partDrawOrder = com.portofino.realtrainmodunofficial.client.model.LegacyVehiclePartOrder.create(
                        def.getLeftDoors(),def.getRightDoors());
                MqoModelLoader.GroupTransform doorTransform = new MqoModelLoader.GroupTransform() {
                    @Override public int drawOrder(String groupName) { return partDrawOrder.getOrDefault(groupName,0); }
                    @Override public void apply(PoseStack stack, String groupName) {
                        if (leftChains.isEmpty() && rightChains.isEmpty()) {
                            TrainEntityRenderer.applyDoorTransform(stack, def.getLeftDoors(), groupName, entity.doorMoveL, true);
                            TrainEntityRenderer.applyDoorTransform(stack, def.getRightDoors(), groupName, entity.doorMoveR, false);
                            return;
                        }
                        TrainEntityRenderer.applyPartsTransform(stack, leftChains, groupName, entity.doorMoveL);
                        TrainEntityRenderer.applyPartsTransform(stack, rightChains, groupName, entity.doorMoveR);
                    }
                    @Override public boolean mayModify(String groupName) {
                        if (groupName == null || groupName.isEmpty()) return false;
                        if (leftChains.isEmpty() && rightChains.isEmpty()) {
                            // ドア定義が無いなら動かす対象も無い (名前推定はしない)。
                            return false;
                        }
                        return TrainEntityRenderer.hasPartsTransform(leftChains, groupName)
                                || TrainEntityRenderer.hasPartsTransform(rightChains, groupName);
                    }
                };
                tSec = ClientRenderProfiler.sec();
                // この経路も entity を渡せない (renderModel の 6 引数版) ため、カリング判…
                com.portofino.realtrainmodunofficial.client.DeferredTranslucentRenderer.setCurrentVehicle(entity);
                try {
                    // entity を渡す版を使う。 発光パスの点灯判定 (前照灯/尾灯/室内灯) も引けない。
                    boolean interiorLightingActive =
                        !def.getInteriorLights().isEmpty()
                        && bodyLight == net.minecraft.client.renderer.LightTexture.FULL_BRIGHT;
                    com.portofino.realtrainmodunofficial.client.render.InteriorLighting.begin(
                        interiorLightingActive ? def.getInteriorLights() : null,
                        interiorLightState == 2, entity.level().getGameTime() * 50L);
                    try {
                        // One complete normal pass, then one complete alpha…
                        final int lightForBody = bodyLight;
                        com.portofino.realtrainmodunofficial.client.render.LocalSpaceBatch.run(
                            poseStack, buffer, (localPose, localBuffer) -> {
                                MqoModelLoader.renderModel(model, localPose, localBuffer,
                                    lightForBody, filter, doorTransform, entity);
                                return true;
                            });
                    } finally {
                        com.portofino.realtrainmodunofficial.client.render.InteriorLighting.end();
                    }
                } finally {
                    com.portofino.realtrainmodunofficial.client.DeferredTranslucentRenderer.setCurrentVehicle(null);
                }
                ClientRenderProfiler.secEnd(ClientRenderProfiler.SEC_BAKED, tSec);
            }

            // ★ 方向幕 (JSON の rollsigns)。
            tSec = ClientRenderProfiler.sec();
            TrainEntityRenderer.renderConfiguredRollsigns(
                    entity.getTrainStateData(
                            jp.ngt.rtm.entity.train.util.TrainState.TrainStateType.State_Destination.id),
                    entity.getRollsignAnimation(),
                    def, poseStack, buffer, packedLight);
            // RTMU 追加: 種別幕 (方向幕とは別の State_Type インデックスで別テクスチャを表示)
            TrainEntityRenderer.renderConfiguredTypeSigns(
                    entity.getTrainStateData(
                            jp.ngt.rtm.entity.train.util.TrainState.TrainStateType.State_Type.id),
                    def, poseStack, buffer, packedLight);
            ClientRenderProfiler.secEnd(ClientRenderProfiler.SEC_SIGNS, tSec);
        } finally {
            poseStack.popPose();
        }
    }

    /** 前照灯・尾灯の光。本家 RenderVehicleBase.renderLightEffect の… */
    private static void renderLightEffect(EntityTrain entity, VehicleDefinition def,
                                          PoseStack poseStack, MultiBufferSource buffer) {
        int mode = entity.getTrainStateData(
            jp.ngt.rtm.entity.train.util.TrainState.TrainStateType.State_Light.id);
        if (mode <= 0) {
            return;
        }
        int dir = entity.getTrainDirection();
        boolean frontFree = entity.getConnectedTrain(dir) == null;
        boolean rearFree = entity.getConnectedTrain(1 - dir) == null;
        int ambient = TrainEntityRenderer.ambientLightValue(
            entity.level(), entity.getX(), entity.getY(), entity.getZ());
        com.portofino.realtrainmodunofficial.client.render.VehicleLightEffect.render(
            def, mode, dir, frontFree, rearFree,
            entity.getX(), entity.getY(), entity.getZ(),
            entity.getYRot(), entity.getXRot(),
            ambient, poseStack, buffer);
    }

    /** 乗員 (運転士等) を車体より先に描画する。 */
    private void renderRidersFirst(EntityTrain entity, float partialTicks, PoseStack poseStack,
                                   MultiBufferSource buffer, int packedLight) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        var dispatcher = mc.getEntityRenderDispatcher();
        for (net.minecraft.world.entity.Entity rider : entity.getPassengers()) {
            // 一人称の自分自身は描かない
            if (rider == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson()) {
                continue;
            }
            double rx = Mth.lerp(partialTicks, rider.xOld, rider.getX())
                    - Mth.lerp(partialTicks, entity.xOld, entity.getX());
            double ry = Mth.lerp(partialTicks, rider.yOld, rider.getY())
                    - Mth.lerp(partialTicks, entity.yOld, entity.getY());
            double rz = Mth.lerp(partialTicks, rider.zOld, rider.getZ())
                    - Mth.lerp(partialTicks, entity.zOld, entity.getZ());
            float riderYaw = Mth.rotLerp(partialTicks, rider.yRotO, rider.getYRot());
            try {
                dispatcher.render(rider, rx, ry, rz, riderYaw, partialTicks, poseStack, buffer,
                        dispatcher.getPackedLightCoords(rider, partialTicks));
            } catch (Throwable ignored) {
                // 乗員描画の失敗で車体描画を巻き込まない
            }
        }
    }

    /** 焼き込みフォールバック時のグループ選別。 スクリプトがまだ非同期コンパイル中/読込失敗のとき、全て… */
    private static boolean shouldRenderGroup(String groupName) {
        if (groupName == null || groupName.isBlank()) {
            return true;
        }
        String n = groupName.toLowerCase(java.util.Locale.ROOT);
        // パックが持つ偽の影ポリゴン (半透明かつ深度を書くためレールが透ける)
        if (n.contains("影") || n.contains("shadow")) {
            return false;
        }
        if (n.endsWith("_ms") || n.endsWith("_kage") || n.contains("_ms_") || n.contains("_kage_")) {
            return false;
        }
        if (n.endsWith("_guide") || n.endsWith("[obj]") || n.endsWith("_atari") || n.endsWith(" atari")) {
            return false;
        }
        // 連結曲げ用の角度バリアント (body-90 / body-180(mx) / bogie1-90…
        return !isAngleBendVariant(n);
    }

    /** 末尾 "-NN" で NN>=10 の角度バリアントか ((mx) 鏡像サフィックスは先に剥がす)。 */
    private static boolean isAngleBendVariant(String normalized) {
        String s = normalized.endsWith("(mx)") ? normalized.substring(0, normalized.length() - 4) : normalized;
        int dash = s.lastIndexOf('-');
        if (dash <= 0 || dash == s.length() - 1) {
            return false;
        }
        for (int i = dash + 1; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) {
                return false;
            }
        }
        try {
            return Integer.parseInt(s.substring(dash + 1)) >= 10;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
