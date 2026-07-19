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
 * モデル供給は暫定で VehicleDefinition/BogieRenderer (Phase 4 で ModelSetTrainClient.bogieModels に置換)。
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
        //軽量化: 車体を車両描画距離で消したとき台車だけ浮かないよう、台車も同じ距離で間引く。
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
        //★蒸気機関車 (D51/9600 等) の二重描画対策。
        //
        //SL は動輪＋主連棒/連結棒 (ロッド) が<b>車体 MQO 側</b>に動輪グループ (wheel/車輪/動輪) として
        //含まれ、車体スクリプトが車輪回転に合わせてロッドごと動かす。ところが別台車エンティティ
        //(この RtmBogieRenderer) も同じ位置に台車モデルの車輪を描くため、走行中に動輪・ロッドが
        //二重に見えていた (報告: D51・9600、台車 DT650・DT580 の「機関車本体」)。本家は動輪を
        //車体側だけで描くので、<b>車体が自前の車輪グループを持つ車両はこの台車描画を丸ごと
        //スキップ</b>して車体側に一本化する。
        //
        //以前はこの判定を「台車モデルが .class のときだけ」に限っていたため、DT650/DT580 のように
        //実モデルファイル (.mqo/.obj) を持つ SL 台車では二重描画が残っていた。台車モデルの種別に
        //依らず車体の車輪グループ有無で判定する。逆に、車体が車輪を持たない車両 (大多数の電車。
        //300系新幹線のように body/yukashita/horo だけの車体を含む) では台車を通常どおり描くので、
        //車体が宙に浮く問題は起きない。
        com.portofino.realtrainmodunofficial.client.model.MqoModelLoader.MqoModel body =
            com.portofino.realtrainmodunofficial.client.model.MqoModelLoader.loadModelForVehicle(def);
        if (body != null && body.hasOwnWheelGroups()) {
            return;
        }

        //★毎フレーム、補間済みの車体位置から弦上の bogiePos を求め、実レール(弧)上の最寄り点へ
        //スナップして描く。急カーブでは弦のままだと台車がレールから外れ、逆に per-tick でスナップ
        //すると高速時に台車が車体の毎フレーム補間へ追従しきれず次第に遅れて見える。描画フレーム
        //単位で「補間車体位置→弧」を計算することで、どの速度でも車体と同期しつつレールに乗る。
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
        //弧へスナップ (レール未検出時は弦のまま)。物理状態は変更しない純粋な描画補正。
        double[] arc = bogie.snapToRailArc(chordX, chordY, chordZ);
        double targetX = arc != null ? arc[0] : chordX;
        double targetY = arc != null ? arc[1] : chordY;
        double targetZ = arc != null ? arc[2] : chordZ;

        poseStack.pushPose();
        try {
            poseStack.translate(targetX - bogieFX, targetY - bogieFY, targetZ - bogieFZ);

            float roll = Mth.lerp(partialTicks, bogie.prevRotationRoll, bogie.rotationRoll);
            if (Math.abs(roll) > 0.001F) {
                poseStack.mulPose(Axis.ZP.rotationDegrees(roll));
            }

            float yaw = Mth.rotLerp(partialTicks, bogie.yRotO, bogie.getYRot());
            float pitch = Mth.lerp(partialTicks, bogie.xRotO, bogie.getXRot());
            //renderWorldBogie が yaw(Y)/-pitch(X)/scale を適用する
            BogieRenderer.renderWorldBogie(poseStack, bogieDef, def, buffer, packedLight, yaw, pitch, partialTicks);
        } finally {
            poseStack.popPose();
        }
    }
}
