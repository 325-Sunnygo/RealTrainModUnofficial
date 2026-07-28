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

/**
 * jp.ngt.rtm.entity.train.EntityTrain 用レンダラ (Phase 2)。
 * 本家 RenderVehicleBase の変換順を踏襲: translate → yaw(Y) → -pitch(X) → roll(Z) → config offset。
 * モデル供給は暫定で VehicleDefinition/MqoModelLoader (Phase 4 で ModelSetTrainClient に置換)。
 */
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
        //軽量化: 車両描画距離が有効なら、それより遠い車両は描画しない (最大負荷=毎フレーム
        //スクリプト実行を丸ごと省く)。既定 0 = 無制限 = バニラどおり。
        if (com.portofino.realtrainmodunofficial.RtmuSettings.beyondVehicleRenderDistance(
                entity.getX(), entity.getY(), entity.getZ(), camX, camY, camZ)) {
            return false;
        }
        double half = Math.max(3.0D, entity.getConfig().trainDistance + 3.0D);
        AABB bounds = new AABB(
                entity.getX() - half, entity.getY() - 2.0D, entity.getZ() - half,
                entity.getX() + half, entity.getY() + 5.0D, entity.getZ() + half);
        return frustum.isVisible(bounds);
    }

    @Override
    public void render(EntityTrain entity, float entityYaw, float partialTicks, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        //計測: 設置される列車は EntityTrain なのに、Profiler の Train カテゴリは旧実装の
        //TrainEntityRenderer にしか入っておらず常に 0 だった (= 本命のコストが計測外)。
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
        //本家 preRenderBody: 室内灯 ON かつ周囲が暗いなら車体の通常描画をフルブライトで描く。
        //★本家は preRenderBody/postRenderBody で<b>車体パスだけ</b>を挟む。台車・乗客・
        //ライト効果には掛からないので、ここでも body 用の値を別に持つ。
        final int interiorLightState = entity.getTrainStateData(
            jp.ngt.rtm.entity.train.util.TrainState.TrainStateType.State_InteriorLight.id);
        final int bodyLight = TrainEntityRenderer.applyInteriorLighting(entity.level(),
            entity.getX(), entity.getY(), entity.getZ(),
            !def.getInteriorLights().isEmpty(), interiorLightState, packedLight);

        ClientRenderProfiler.secEnd(ClientRenderProfiler.SEC_LOOKUP, tSec);

        //乗員を車体より先に描画する。車体は半透明バッチ (AlphaBlend) が深度を書くため、
        //列車→乗員の順に描かれると乗員がガラス/車体越しに遮蔽されて透明に見える
        //(本家 1.7.10 は不透明ピクセル先行の 2 パスだったため発生しなかった問題)。
        tSec = ClientRenderProfiler.sec();
        this.renderRidersFirst(entity, partialTicks, poseStack, buffer, packedLight);
        ClientRenderProfiler.secEnd(ClientRenderProfiler.SEC_RIDERS, tSec);

        poseStack.pushPose();
        try {
            //本家 RenderVehicleBase: yaw → -pitch → roll → offset
            float yaw = Mth.rotLerp(partialTicks, entity.yRotO, entity.getYRot());
            poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
            float pitch = Mth.lerp(partialTicks, entity.xRotO, entity.getXRot());
            poseStack.mulPose(Axis.XP.rotationDegrees(-pitch));
            float roll = Mth.lerp(partialTicks, entity.prevRotationRoll, entity.rotationRoll);
            poseStack.mulPose(Axis.ZP.rotationDegrees(roll));

            poseStack.translate(def.getModelOffset().x, def.getModelOffset().y, def.getModelOffset().z);
            //★ボクセルモデル (.ngto/.ngtz) はここで縮尺を掛けない。本家は
            //NGTOParts.render の中だけで glScalef(scale) するので、スクリプトが台車/ドアを
            //置く glTranslatef はブロック単位のまま。ここで掛けると全部が中央に寄る。
            if (!model.isVoxelModel()) {
                poseStack.scale(def.getModelScale(), def.getModelScale(), def.getModelScale());
            }

            //本家式スクリプト描画 (Nashorn): 成功したらベイクドパスはスキップ
            tSec = ClientRenderProfiler.sec();
            com.portofino.realtrainmodunofficial.client.render.VehicleScriptRenderers.Scripted scripted =
                    com.portofino.realtrainmodunofficial.client.render.VehicleScriptRenderers.get(def);
            boolean scriptRendered = scripted != null && scripted.render(entity, partialTicks, poseStack, buffer,
                    bodyLight, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, model);
            ClientRenderProfiler.secEnd(ClientRenderProfiler.SEC_SCRIPTED, tSec);

            if (!scriptRendered) {
                MqoModelLoader.GroupPredicate filter =
                        groupName -> shouldRenderGroup(groupName);

                //★ ドアの開閉。
                //
                //車両 JSON の door_left / door_right (「このグループを開くとき何処へ動かすか」) による
                //開閉は<b>旧 TrainEntity のレンダラーにしか実装されていなかった</b>。実際に描画されるのは
                //こちらの本家系レンダラーなので、スクリプトを持たないパック (=大半の車両) では
                //ドアが一切動かなかった。ここで同じ変換を適用する。
                //本家 BasicVehiclePartsRenderer.render と同じ変換。動かす部品は JSON の
                //objects で決まる (名前の推測はしない)。パンタも同じ仕組みで動く。
                java.util.Map<String, java.util.List<TrainEntityRenderer.PartsStep>> leftChains =
                        TrainEntityRenderer.partsChains(def.getLeftDoors());
                java.util.Map<String, java.util.List<TrainEntityRenderer.PartsStep>> rightChains =
                        TrainEntityRenderer.partsChains(def.getRightDoors());
                MqoModelLoader.GroupTransform doorTransform = new MqoModelLoader.GroupTransform() {
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
                            return groupName.length() >= 4 && TrainEntityRenderer.containsDoorWord(groupName);
                        }
                        return TrainEntityRenderer.hasPartsTransform(leftChains, groupName)
                                || TrainEntityRenderer.hasPartsTransform(rightChains, groupName);
                    }
                };
                tSec = ClientRenderProfiler.sec();
                //この経路も entity を渡せない (renderModel の 6 引数版) ため、カリング判定
                //(shouldCullModelFaces) が車両の doCulling を引けず両面描画に落ちる。
                //スクリプト経路と同じフォールバックに載せる。
                com.portofino.realtrainmodunofficial.client.DeferredTranslucentRenderer.setCurrentVehicle(entity);
                try {
                    //entity を渡す版を使う。渡さないとカリング (doCulling) も
                    //発光パスの点灯判定 (前照灯/尾灯/室内灯) も引けない。
                    //本家 preRenderBody / postRenderBody と同じ位置で室内灯を挟む。
                    //★本家 preRenderBody は室内灯の GL 点光源を
                    //  「室内灯が点いていて、かつ周囲が暗い (getLightValue < 7)」ときだけ有効にする。
                    //RTMU はここを無条件で有効にしていたため、車内にある光源に対して
                    //<b>車体外面は法線が逆を向き係数 0.2 (ambient のみ)</b> になり、
                    //日中でも車体が暗く描かれていた。
                    //判定は applyInteriorLighting が既に同じ条件で行っているので、
                    //その結果 (bodyLight が FULL_BRIGHT になったか) を使って揃える。
                    boolean interiorLightingActive =
                        !def.getInteriorLights().isEmpty()
                        && bodyLight == net.minecraft.client.renderer.LightTexture.FULL_BRIGHT;
                    com.portofino.realtrainmodunofficial.client.render.InteriorLighting.begin(
                        interiorLightingActive ? def.getInteriorLights() : null,
                        interiorLightState == 2, entity.level().getGameTime() * 50L);
                    try {
                        //★<b>静的な車体は VBO へ焼いて使い回す。</b>
                        //
                        //ここは以前まで<b>毎フレーム全頂点を CPU で変換して書き出して</b>いた。
                        //スクリプトを持つ車両は焼き込み (VehicleMeshCache) に載っていたが、
                        //素の車両はこの経路なので載っておらず、300 系のような同梱車両でも
                        //1 両置くだけで描画時間が跳ね上がっていた。
                        //
                        //焼けないフレーム (枠が埋まった・毎フレーム絵が変わる) は
                        //<b>自動で下の CPU 経路へ落ちる</b>ので、最悪でも従来と同じ。
                        //走行中やドア開閉中は VehicleMeshCache が「動く車両」と判定して
                        //焼くのを諦めるため、焼き直しで却って重くなることも無い。
                        //★<b>動く部品と動かない車体を分ける。</b>
                        //
                        //一緒に焼くと、ドアやパンタが少し動いただけで<b>車体まるごと</b>
                        //焼き直しになる。動くグループは車体全体から見ればごく一部なので、
                        //そこだけ毎フレーム CPU で描き、残りは焼いたまま使い回す。
                        //こうすると焼き込みキーからドアの開き具合が消え、
                        //ドアを開け閉めしても車体は焼き直されない。
                        MqoModelLoader.GroupPredicate staticFilter =
                            n -> filter.shouldRender(n) && !doorTransform.mayModify(n);
                        MqoModelLoader.GroupPredicate movingFilter =
                            n -> filter.shouldRender(n) && doorTransform.mayModify(n);

                        int bakeKey = bodyBakeKey(entity, bodyLight,
                            interiorLightingActive, interiorLightState);
                        //同じ車両定義・同じ状態なら<b>編成の全車で 1 本の VBO を共有</b>する。
                        boolean bodyBaked = com.portofino.realtrainmodunofficial.client.render
                            .VehicleMeshCache.draw(def.getId(), entity, 2, poseStack, bakeKey,
                                //焼くときは単位行列で再生する
                                //(カメラ相対の pose で焼くと視点に付いてくる)。
                                //動く部品は入れないので変換も渡さない。
                                buf -> MqoModelLoader.renderModel(model, new PoseStack(), buf,
                                    bodyLight, staticFilter, null, entity));
                        if (!bodyBaked) {
                            MqoModelLoader.renderModel(model, poseStack, buffer, bodyLight,
                                staticFilter, null, entity);
                        }
                        //動く部品 (ドア・パンタ等)。持たない車両ならここは何も描かない。
                        MqoModelLoader.renderModel(model, poseStack, buffer, bodyLight,
                            movingFilter, doorTransform, entity);
                    } finally {
                        com.portofino.realtrainmodunofficial.client.render.InteriorLighting.end();
                    }
                } finally {
                    com.portofino.realtrainmodunofficial.client.DeferredTranslucentRenderer.setCurrentVehicle(null);
                }
                ClientRenderProfiler.secEnd(ClientRenderProfiler.SEC_BAKED, tSec);
            }

            //★ 方向幕 (JSON の rollsigns)。
            //
            //本家 RenderVehicleBase は<b>スクリプト描画とは別に</b>方向幕を描く。RTMU では
            //旧 TrainEntityRenderer にしか実装が無く、実際に列車を描くこちらには無かったため、
            //京急パックのように「幕はエンジン任せ・車体だけスクリプト」なパックで幕が出なかった。
            //スクリプトの有無で切り分けてはいけない (自前で幕を描くパックは rollsigns を空にしている)。
            tSec = ClientRenderProfiler.sec();
            TrainEntityRenderer.renderConfiguredRollsigns(
                    entity.getTrainStateData(
                            jp.ngt.rtm.entity.train.util.TrainState.TrainStateType.State_Destination.id),
                    entity.getRollsignAnimation(),
                    def, poseStack, buffer, packedLight);
            //RTMU 追加: 種別幕 (方向幕とは別の State_Type インデックスで別テクスチャを表示)
            TrainEntityRenderer.renderConfiguredTypeSigns(
                    entity.getTrainStateData(
                            jp.ngt.rtm.entity.train.util.TrainState.TrainStateType.State_Type.id),
                    def, poseStack, buffer, packedLight);
            ClientRenderProfiler.secEnd(ClientRenderProfiler.SEC_SIGNS, tSec);
        } finally {
            poseStack.popPose();
        }
    }

    /**
     * 乗員 (運転士等) を車体より先に描画する。
     * 通常の描画ループでも描かれるが、同一変換の二重描画は視覚上問題にならない。
     */
    private void renderRidersFirst(EntityTrain entity, float partialTicks, PoseStack poseStack,
                                   MultiBufferSource buffer, int packedLight) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        var dispatcher = mc.getEntityRenderDispatcher();
        for (net.minecraft.world.entity.Entity rider : entity.getPassengers()) {
            //一人称の自分自身は描かない
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
                //乗員描画の失敗で車体描画を巻き込まない
            }
        }
    }

    /**
     * ヘルパーグループ (shadow/guide/atari/影ms/連結曲げ変種) の除外。
     * 台車グループは別台車モデルがある場合のみ非表示 (EntityBogie 側が描画)。
     */

    /**
     * 焼いた車体を使い回してよいかの判定キー。
     *
     * <p>頂点の中身が変わる要素を全部混ぜる。<b>1 つでも入れ忘れると古い絵が残る</b>
     * (踏切のランプが固まったのと同じ壊れ方をする)。
     *
     * <ul>
     *   <li>明るさ … 車両ごとに 1 つ。頂点へ焼き込まれる</li>
     *   <li>ドアの開き具合 … 部品の位置が動く</li>
     *   <li>室内灯 … 頂点色が変わる。<b>点いている間は時刻も混ぜる</b>ので、
     *       明滅する実装でも固まらない (そのぶん焼き直しになるが、
     *       VehicleMeshCache が動く車両と判定して CPU 経路へ落とす)</li>
     * </ul>
     */
    private static int bodyBakeKey(jp.ngt.rtm.entity.train.EntityTrainBase entity, int bodyLight,
                                   boolean interiorLightingActive, int interiorLightState) {
        //★ドアの開き具合は<b>入れない</b>。動く部品は焼き込みに含めず毎フレーム描くので、
        //  ここに入れるとドアを触るたび車体が焼き直しになるだけで意味が無い。
        int key = bodyLight;
        key = 31 * key + (interiorLightingActive ? 1 : 0);
        key = 31 * key + interiorLightState;
        if (interiorLightingActive) {
            key = 31 * key + (int) entity.level().getGameTime();
        }
        return key;
    }

    private static boolean shouldRenderGroup(String groupName) {
        if (groupName == null || groupName.isBlank()) {
            return true;
        }
        String n = groupName.toLowerCase(Locale.ROOT);
        //本家のパックは「影」という名前の<b>偽の影ポリゴン</b>を持つことがある (1.7.10 時代の名残)。
        //半透明マテリアルで描かれるが<b>深度も書き込む</b>ため、レールの上に乗ると
        //レールが深度テストに落ちて描かれなくなる (「レールが透ける」)。
        //1.21 では要らないので描かない。
        if (n.contains("影")) {
            return false;
        }
        if (n.contains("shadow")) {
            return false;
        }
        if (n.endsWith("_ms") || n.endsWith("_kage") || n.contains("_ms_") || n.contains("_kage_")) {
            return false;
        }
        if (n.endsWith("_guide") || n.endsWith("[obj]") || n.endsWith("_atari") || n.endsWith(" atari")) {
            return false;
        }
        if (isAngleBendVariant(n)) {
            return false;
        }
        //★本家は名前で台車グループを隠さない (TrainEntityRenderer と同じ理由)。
        return true;
    }

    /**
     * 連結曲げ用の角度バリアント (末尾 "-NN" で NN>=10、(mx) は先に剥がす)。
     */
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
