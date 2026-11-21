package com.portofino.realtrainmodunofficial.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.portofino.realtrainmodunofficial.vehicle.VehicleDefinition;
import com.portofino.realtrainmodunofficial.vehicle.VehicleRegistry;
import jp.ngt.ngtlib.math.Vec3;
import jp.ngt.rtm.entity.train.EntityBogie;
import jp.ngt.rtm.entity.train.EntityTrainBase;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** jp.ngt.rtm.entity.train.EntityBogie 用レンダラ — 本家 Re… */
public class RtmBogieRenderer extends EntityRenderer<EntityBogie> {

    public RtmBogieRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(EntityBogie entity) {
        return ResourceLocation.withDefaultNamespace("missingno");
    }

    @Override
    public boolean shouldRender(EntityBogie bogie, net.minecraft.client.renderer.culling.Frustum frustum,
                                double camX, double camY, double camZ) {
        return super.shouldRender(bogie, frustum, camX, camY, camZ);
    }

    @Override
    public void render(EntityBogie bogie, float entityYaw, float partialTicks, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        EntityTrainBase train = bogie.getTrain();
        if (train == null) {
            return;
        }
        VehicleDefinition def = VehicleRegistry.getById(train.getModelName());
        if (def == null || def.getBogies().isEmpty()) {
            return;
        }
        int index = bogie.getBogieId();
        if (index < 0 || index >= def.getBogies().size()) {
            return;
        }
        VehicleDefinition.BogieDefinition bogieDef = def.getBogies().get(index);
        if (bogieDef == null || bogieDef.modelFile() == null || bogieDef.modelFile().isBlank()
                || BogieRenderer.isDummyBogieModel(bogieDef.modelFile())) {
            return;
        }
        // ★蒸気機関車 (/9600 等) の二重描画対策。
        double bogieFX = Mth.lerp(partialTicks, bogie.xOld, bogie.getX());
        double bogieFY = Mth.lerp(partialTicks, bogie.yOld, bogie.getY());
        double bogieFZ = Mth.lerp(partialTicks, bogie.zOld, bogie.getZ());

        float[][] cfgPos = train.getConfig().getBogiePos();
        Vec3 v31 = new Vec3(cfgPos[index][0], cfgPos[index][1], cfgPos[index][2]);
        v31 = v31.rotateAroundX(Mth.lerp(partialTicks, train.xRotO, train.getXRot()));
        v31 = v31.rotateAroundY(Mth.rotLerp(partialTicks, train.yRotO, train.getYRot()));
        double chordX = v31.getX() + Mth.lerp(partialTicks, train.xOld, train.getX());
        double chordY = v31.getY() + Mth.lerp(partialTicks, train.yOld, train.getY());
        double chordZ = v31.getZ() + Mth.lerp(partialTicks, train.zOld, train.getZ());
        // 本家 RenderBogie と同じ: 車体位置からの弦の位置を使う。
        // 分岐で別枝へスナップする独自処理はガタガタの原因になるため持たない。
        double targetX = chordX;
        double targetY = chordY;
        double targetZ = chordZ;

        poseStack.pushPose();
        try {
            poseStack.translate(targetX - bogieFX, targetY - bogieFY, targetZ - bogieFZ);

            float roll = Mth.lerp(partialTicks, bogie.prevRotationRoll, bogie.rotationRoll);
            if (Math.abs(roll) > 0.001F) {
                poseStack.mulPose(Axis.ZP.rotationDegrees(roll));
            }

            float yaw = Mth.rotLerp(partialTicks, bogie.yRotO, bogie.getYRot());
            float pitch = Mth.lerp(partialTicks, bogie.xRotO, bogie.getXRot());
            // renderWorldBogie が yaw(Y)/-pitch(X)/scale を適用する
            BogieRenderer.renderWorldBogie(poseStack, bogieDef, def, buffer, packedLight, yaw, pitch, partialTicks);
        } finally {
            poseStack.popPose();
        }
    }
}
