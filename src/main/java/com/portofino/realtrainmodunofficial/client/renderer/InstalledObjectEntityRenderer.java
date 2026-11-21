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

/** 本家 EntityInstalledObject 系 (ATC / 列車検知器 / 車止め) の描… */
public class InstalledObjectEntityRenderer<T extends jp.ngt.rtm.entity.EntityInstalledObject>
        extends EntityRenderer<T> {

    private static final ResourceLocation WHITE =
        ResourceLocation.withDefaultNamespace("textures/misc/white.png");

    public InstalledObjectEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    /** 本家 RenderElectricalWiring.renderAllWire: root かつ可… */
    private void renderConnectionWires(jp.ngt.rtm.entity.EntityElectricalWiring wiring,
                                       float partialTick, PoseStack poseStack,
                                       MultiBufferSource buffer, int packedLight, int packedOverlay) {
        var connections = wiring.getConnectionList();
        if (connections.isEmpty()) {
            return;
        }
        Vec3 entityPos = new Vec3(wiring.getPreciseRenderPosX(),
                wiring.getPreciseRenderPosY(), wiring.getPreciseRenderPosZ());
        for (jp.ngt.rtm.electric.Connection connection : connections) {
            if (!connection.isRoot || !connection.type.isVisible) {
                continue;
            }
            InstalledObjectDefinition wireDef =
                    InstalledObjectBlockEntityRenderer.resolveWireDefinition(connection.wireName);
            if (wireDef == null) {
                continue;
            }
            Vec3 toWorld;
            if (connection.type == jp.ngt.rtm.electric.Connection.ConnectionType.TO_PLAYER) {
                toWorld = InstalledObjectBlockEntityRenderer.localPlayerHandPos();
            } else {
                net.minecraft.core.BlockPos target =
                        new net.minecraft.core.BlockPos(connection.x, connection.y, connection.z);
                jp.ngt.rtm.entity.EntityElectricalWiring other =
                        jp.ngt.rtm.entity.EntityElectricalWiring.find(wiring.level(), target);
                if (other != null) {
                    toWorld = new Vec3(other.getPreciseRenderPosX(),
                            other.getPreciseRenderPosY(), other.getPreciseRenderPosZ());
                } else {
                    toWorld = InstalledObjectBlockEntityRenderer.resolveWireAttachPoint(
                            wiring.level(), target);
                    if (toWorld == null) {
                        toWorld = Vec3.atCenterOf(target);
                    }
                }
            }
            if (toWorld == null) {
                continue;
            }
            MqoModelLoader.MqoModel model = InstalledObjectBlockEntityRenderer.hasRenderableWireModel(wireDef)
                    ? MqoModelLoader.loadModelFromPack(wireDef.getPackName(), wireDef.getModelFile(),
                        wireDef.getTextureOverrides(), wireDef.getScriptPath(), wireDef.isSmoothing())
                    : null;
            var wire = com.portofino.realtrainmodunofficial.client.render.WireScriptRenderers.get(wireDef);
            if (wire != null) {
                // poseStack の原点はエンティティ位置。
                wire.render(wiring, Vec3.ZERO, toWorld.subtract(entityPos), partialTick,
                        poseStack, buffer, packedLight, packedOverlay, model);
            }
        }
    }

    @Override
    public boolean shouldRender(T entity, net.minecraft.client.renderer.culling.Frustum frustum,
                                double camX, double camY, double camZ) {
        if (entity instanceof jp.ngt.rtm.entity.EntityElectricalWiring wiring
                && !wiring.getConnectionList().isEmpty()) {
            net.minecraft.world.phys.AABB box = entity.getBoundingBox().inflate(2.0D);
            for (jp.ngt.rtm.electric.Connection connection : wiring.getConnectionList()) {
                if (!connection.isRoot || !connection.type.isVisible) {
                    continue;
                }
                if (connection.type == jp.ngt.rtm.electric.Connection.ConnectionType.TO_PLAYER) {
                    box = box.inflate(32.0D);
                } else {
                    box = box.minmax(new net.minecraft.world.phys.AABB(
                            new net.minecraft.core.BlockPos(connection.x, connection.y, connection.z))
                            .inflate(2.0D));
                }
            }
            if (frustum.isVisible(box)) {
                return true;
            }
        }
        return super.shouldRender(entity, frustum, camX, camY, camZ);
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return WHITE;
    }

    @Override
    public void render(T entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        InstalledObjectDefinition def = InstalledObjectRegistry.getById(entity.getModelId());
        if (def != null && def.getModelFile() != null && !def.getModelFile().isBlank()) {
            MqoModelLoader.MqoModel model = MqoModelLoader.loadModelFromPack(
                def.getPackName(), def.getModelFile(), def.getTextureOverrides(), null, def.isSmoothing());
            if (model != null) {
                poseStack.pushPose();
                // 本家 RenderEntityInstalledObject: precise spawn dat…
                poseStack.translate(
                        entity.getPreciseRenderPosX() - entity.getX(),
                        entity.getPreciseRenderPosY() - entity.getY(),
                        entity.getPreciseRenderPosZ() - entity.getZ());
                float renderYaw = entity.getPreciseRenderYaw();
                float renderPitch = entity.getPreciseRenderPitch();
                // 本家 RenderEntityInstalledObject: ワイヤーは entity.rota…
                if (entity instanceof jp.ngt.rtm.entity.EntityElectricalWiring wiring) {
                    renderConnectionWires(wiring, partialTick, poseStack, buffer,
                            packedLight, OverlayTexture.NO_OVERLAY);
                }
                // 本家 RenderEntityInstalledObject: GL11.glRotatef(en…
                poseStack.mulPose(Axis.YP.rotationDegrees(renderYaw));
                // 本家 RenderEntityInstalledObject: cfg.followRailAng…
                if (def.isFollowRailAngle()) {
                    poseStack.mulPose(Axis.XP.rotationDegrees(renderPitch));
                    poseStack.mulPose(Axis.ZP.rotationDegrees(entity.rotationRoll));
                }
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
