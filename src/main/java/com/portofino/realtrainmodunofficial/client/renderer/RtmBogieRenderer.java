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

/**
 * jp.ngt.rtm.entity.train.EntityBogie 用レンダラ — 本家 RenderBogie の忠実移植。
 * 描画位置は車体基準の bogiePos へ補正 (本家: RenderMng の補完値を引いて train 相対位置に置き直す)。
 */
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
        // 軽量化: 車体を車両描画距離で消したとき台車だけ浮かないよう、台車も同じ距離で間引く。
        if (com.portofino.realtrainmodunofficial.RtmuSettings.beyondVehicleRenderDistance(
                bogie.getX(), bogie.getY(), bogie.getZ(), camX, camY, camZ)) {
            return false;
        }
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
        // SL は動輪＋主連棒/連結棒 (ロッド) が車体 MQO 側に動輪グループ (wheel/車輪/動輪) として
        // 含まれ、車体スクリプトが車輪回転に合わせてロッドごと動かす。
        // ★本家 RenderBogie は台車モデルを無条件に描く (ダミーの時だけ missing model)。
        // ★毎フレーム、補間済みの車体位置から弦上の bogiePos を求め、実レール(弧)上の最寄り点へ
        // スナップして描く。
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
        // 弧へスナップ (レール未検出時は弦のまま)。物理状態は変更しない純粋な描画補正。
        double[] arc = bogie.snapToRailArc(chordX, chordY, chordZ);
        double targetX = arc != null ? arc[0] : chordX;
        double targetY = arc != null ? arc[1] : chordY;
        double targetZ = arc != null ? arc[2] : chordZ;

        poseStack.pushPose();
        try {
            poseStack.translate(targetX - bogieFX, targetY - bogieFY, targetZ - bogieFZ);

            // ★姿勢もレールから取れたならそれを使う。
            // パケット由来の姿勢は 1.4 度刻みに量子化されているうえ、回転が止まると
            // 自分の値が返ってくるので滑らかにならない。レールは連続なので刻みが無い。
            float roll = arc != null ? (float) arc[5]
                : Mth.lerp(partialTicks, bogie.prevRotationRoll, bogie.rotationRoll);
            if (Math.abs(roll) > 0.001F) {
                poseStack.mulPose(Axis.ZP.rotationDegrees(roll));
            }

            float yaw = arc != null ? (float) arc[3]
                : Mth.rotLerp(partialTicks, bogie.yRotO, bogie.getYRot());
            float pitch = arc != null ? (float) arc[4]
                : Mth.lerp(partialTicks, bogie.xRotO, bogie.getXRot());
            // renderWorldBogie が yaw(Y)/-pitch(X)/scale を適用する
            BogieRenderer.renderWorldBogie(poseStack, bogieDef, def, buffer, packedLight, yaw, pitch, partialTicks);
        } finally {
            poseStack.popPose();
        }
    }
}
