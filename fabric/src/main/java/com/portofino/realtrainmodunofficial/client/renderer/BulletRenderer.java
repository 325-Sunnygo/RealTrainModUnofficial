package com.portofino.realtrainmodunofficial.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.portofino.realtrainmodunofficial.client.model.MqoModelLoader;
import jp.ngt.rtm.entity.EntityBullet;
import jp.ngt.rtm.item.ItemAmmunition.BulletType;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * 弾の描画。本家 {@code jp.ngt.rtm.entity.RenderBullet} の pass 0 の移植。
 *
 * <p>本家と同じモデル ({@code cannonball.obj} / {@code 200mm_rocket.mqo}) を
 * mod の jar から読んで使う。ライフル弾で「まだブロックを壊せる = 飛翔中」のものは
 * 本家と同じく<b>発光した細長い形</b>で描く (曳光弾)。
 *
 * <p>★本家 pass 1 のロケットの炎と、撃った人に出るマズルフラッシュ / レーザーの光条は未移植。
 */
public class BulletRenderer extends EntityRenderer<EntityBullet> {

    private static final String MODEL_CANNON = "models/entity/cannonball.obj";
    private static final String TEX_CANNON = "textures/entity/cannonball.png";
    private static final String MODEL_ROCKET = "models/entity/200mm_rocket.mqo";
    private static final String TEX_ROCKET = "textures/entity/200mm_rocket.png";

    public BulletRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(EntityBullet entity, float entityYaw, float partialTicks, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(
            Mth.lerp(partialTicks, entity.yRotO, entity.getYRot()) - 90.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(
            Mth.lerp(partialTicks, entity.xRotO, entity.getXRot())));
        poseStack.mulPose(Axis.ZP.rotationDegrees(-90.0F));

        BulletType type = entity.getBulletType();
        boolean brightBullet = (type == BulletType.rifle_5_56mm || type == BulletType.rifle_7_62mm
            || type == BulletType.rifle_12_7mm) && entity.getCanBreakBlock();

        if (brightBullet) {
            //本家: テクスチャを切って発光させ、縦に伸ばした形を黄色 (1.0,1.0,0.25) で描く
            if (type == BulletType.rifle_12_7mm) {
                poseStack.scale(0.1F, 1.5F, 0.1F);
            } else {
                poseStack.scale(0.05F, 0.75F, 0.05F);
            }
            MqoModelLoader.MqoModel model =
                MqoModelLoader.loadModelFromModResources(MODEL_CANNON, TEX_CANNON);
            MqoModelLoader.renderModelColorOverlay(model, poseStack, buffer,
                OverlayTexture.NO_OVERLAY, null, 255, 255, 64, 255);
        } else {
            String modelPath = MODEL_CANNON;
            String texPath = TEX_CANNON;
            if (type == BulletType.handgun_9mm || type == BulletType.rifle_5_56mm
                || type == BulletType.rifle_7_62mm) {
                poseStack.scale(0.05F, 0.05F, 0.05F);
            } else if (type == BulletType.rifle_12_7mm) {
                poseStack.scale(0.1F, 0.1F, 0.1F);
            } else if (type == BulletType.rocket) {
                modelPath = MODEL_ROCKET;
                texPath = TEX_ROCKET;
            }
            MqoModelLoader.MqoModel model =
                MqoModelLoader.loadModelFromModResources(modelPath, texPath);
            MqoModelLoader.renderModel(model, poseStack, buffer, packedLight);
        }

        poseStack.popPose();
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(EntityBullet entity) {
        return ResourceLocation.withDefaultNamespace("missingno");
    }
}
