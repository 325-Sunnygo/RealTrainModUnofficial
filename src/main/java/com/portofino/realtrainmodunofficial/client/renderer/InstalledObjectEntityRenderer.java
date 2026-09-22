package com.portofino.realtrainmodunofficial.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.portofino.realtrainmodunofficial.client.model.MqoModelLoader;
import com.portofino.realtrainmodunofficial.installedobject.InstalledObjectDefinition;
import com.portofino.realtrainmodunofficial.installedobject.InstalledObjectRegistry;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * 本家 {@code EntityInstalledObject} 系 (ATC / 列車検知器 / 車止め) の描画。
 * 設置物ブロックと同じ定義モデル ({@link InstalledObjectDefinition}) をエンティティ位置に描く。
 *
 * <p>本家 {@code RenderEntityInstalledObject} はレンダラを
 * {@code ModelSetMachine} の {@code modelObj.render(entity, cfg, pass, partialTick)} で描き、
 * {@code pass} に {@code MinecraftForgeClient.getRenderPass()} (0=不透明 / 1=透過) を渡す。
 * つまり rendererPath スクリプトは <b>pass 0 と pass 1 の 2 回</b>実行される。
 * よって既定の列車検知器モデル Torii のように {@code rendererPath} を持つモデルは、
 * ここでもスクリプト経路 ({@code MachineScriptRenderers}) を通す必要がある
 * (通さないと TRANSPARENT 専用パーツまで素で描かれ、鳥居に余計な歯車/板が付いて見える)。
 */
public class InstalledObjectEntityRenderer<T extends jp.ngt.rtm.entity.EntityInstalledObject>
        extends EntityRenderer<T> {

    private static final ResourceLocation WHITE =
        ResourceLocation.withDefaultNamespace("textures/misc/white.png");

    public InstalledObjectEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return WHITE;
    }

    @Override
    public void render(T entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        InstalledObjectDefinition def = InstalledObjectRegistry.getById(entity.getModelName());
        if (def != null && def.getModelFile() != null && !def.getModelFile().isBlank()) {
            MqoModelLoader.MqoModel model = MqoModelLoader.loadModelFromPack(
                def.getPackName(), def.getModelFile(), def.getTextureOverrides(), null, def.isSmoothing());
            if (model != null) {
                poseStack.pushPose();
                // 設置物ブロックの描画と同じ向き規約 (ヨーは -yaw)。
                poseStack.mulPose(Axis.YP.rotationDegrees(-entityYaw));
                Vec3 off = def.getModelOffset();
                poseStack.translate(off.x, off.y, off.z);
                float s = def.getModelScale();
                poseStack.scale(s, s, s);
                // ★本家準拠: rendererPath があればスクリプトが pass 0/1 を担当する。
                com.portofino.realtrainmodunofficial.client.render.MachineScriptRenderers.Scripted scripted =
                    com.portofino.realtrainmodunofficial.client.render.MachineScriptRenderers.get(def);
                if (scripted == null
                        || !scripted.renderEntity(entity, partialTick, poseStack, buffer, packedLight,
                            OverlayTexture.NO_OVERLAY, model)) {
                    MqoModelLoader.renderModelWithoutScript(model, poseStack, buffer, packedLight,
                        OverlayTexture.NO_OVERLAY, false, null, null, null);
                }
                poseStack.popPose();
            }
        }
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }
}
