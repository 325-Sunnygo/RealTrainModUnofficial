package com.portofino.realtrainmodunofficial.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.portofino.realtrainmodunofficial.client.model.MqoModelLoader;
import com.portofino.realtrainmodunofficial.entity.CarEntity;
import com.portofino.realtrainmodunofficial.vehicle.VehicleDefinition;
import com.portofino.realtrainmodunofficial.vehicle.VehicleRegistry;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

import static com.portofino.realtrainmodunofficial.RealTrainModUnofficial.MODID;

/**
 * 自動車の描画。
 * エンティティの vehicleId が指す車両定義のモデル/テクスチャ/レンダースクリプトを描画する。
 */
@OnlyIn(Dist.CLIENT)
public final class CarRenderer extends EntityRenderer<CarEntity> {
    private static final ResourceLocation FALLBACK_TEXTURE =
        ResourceLocation.fromNamespaceAndPath(MODID, "textures/car/toyota_prius-phv.png");

    public CarRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    @NotNull
    public ResourceLocation getTextureLocation(@NotNull CarEntity entity) {
        return FALLBACK_TEXTURE;
    }

    @Override
    public void render(@NotNull CarEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       @NotNull MultiBufferSource bufferSource, int packedLight) {
        // SRB のマーカー描画基準(MCWrapper.getPosX → renderPosX)が PoseStack 原点と同じ
        // partialTick で entity 補間位置を出せるよう、現在の partialTick を共有する。
        com.portofino.realtrainmodunofficial.client.ScriptClientCompat.currentRenderPartialTick = partialTick;
        // スクリプトが読む Minecraft/Player ラッパー (field_71462_r 等) を毎フレーム更新
        jp.ngt.mccompat.Minecraft.refresh();
        VehicleDefinition def = VehicleRegistry.getById(entity.getVehicleId());
        if (def == null) {
            super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
            return;
        }

        // 本家式: rendererPath スクリプトを Nashorn (VehicleScriptRenderers) で実行。
        // SRB3/NGTO Builder の GUI・マーカー・入力処理はこの render 内で動く。
        var scripted = com.portofino.realtrainmodunofficial.client.render.VehicleScriptRenderers.get(def);
        if (scripted != null) {
            poseStack.pushPose();
            try {
                poseStack.mulPose(Axis.YP.rotationDegrees(-entityYaw));
                // ボディモデルはスクリプト無しロード (レガシースクリプトを起動させない)
                MqoModelLoader.MqoModel bodyModel = MqoModelLoader.loadModelForVehicleNoScript(def);
                scripted.render(entity, partialTick, poseStack, bufferSource,
                        packedLight, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, bodyModel);
            } catch (Throwable ignored) {
            } finally {
                poseStack.popPose();
            }
        }
        // ★スクリプトを持つ車両は、それが何も描かなくても素モデルへ逃がさない (本家に代替描画は無い)。
        //   スクリプトを持たない車両だけがこの経路。
        if (scripted == null) {
            MqoModelLoader.MqoModel model = MqoModelLoader.loadModelForVehicle(def);
            if (model != null) {
                poseStack.pushPose();
                try {
                    poseStack.mulPose(Axis.YP.rotationDegrees(-entityYaw));
                    poseStack.translate(def.getModelOffset().x, def.getModelOffset().y, def.getModelOffset().z);
                    poseStack.scale(def.getModelScale(), def.getModelScale(), def.getModelScale());
                    MqoModelLoader.renderModel(model, poseStack, bufferSource, packedLight, null, null, entity);
                } catch (Throwable ignored) {
                    // 個別車両の描画失敗で他を巻き込まない。
                } finally {
                    poseStack.popPose();
                }
            }
        }
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }
}
