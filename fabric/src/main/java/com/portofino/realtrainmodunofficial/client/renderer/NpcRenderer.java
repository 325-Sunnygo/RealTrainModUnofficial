package com.portofino.realtrainmodunofficial.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.portofino.realtrainmodunofficial.client.model.MqoModelLoader;
import com.portofino.realtrainmodunofficial.npc.NpcDefinition;
import jp.ngt.rtm.entity.npc.EntityNPC;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * NPC の描画。本家 {@code RenderNPC} 相当。
 *
 * <p>パック定義が MQO を持つならそれを描き、{@code texture} だけならバニラの人型に貼る。
 */
public class NpcRenderer extends EntityRenderer<EntityNPC> {

    private final PlayerModel<net.minecraft.world.entity.LivingEntity> playerModel;

    public NpcRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.playerModel = new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false);
    }

    @Override
    public void render(EntityNPC entity, float entityYaw, float partialTicks, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        NpcDefinition def = entity.getDefinition();
        //生き物になったので胴体の向き (yBodyRot) を使う
        float bodyYaw = Mth.rotLerp(partialTicks, entity.yBodyRotO, entity.yBodyRot);

        poseStack.pushPose();
        if (def != null && def.hasModel()) {
            poseStack.mulPose(Axis.YP.rotationDegrees(-bodyYaw));
            MqoModelLoader.MqoModel model = MqoModelLoader.loadModelFromPack(
                def.getPackName(), def.getModelFile(), def.getTextureOverrides(), null, true);
            MqoModelLoader.renderModel(model, poseStack, buffer, packedLight);
        } else {
            //バニラの人型に絵を貼る (本家の texture だけの NPC)。歩行と頭の向きも動かす。
            //★変換の順番はバニラ LivingEntityRenderer と同じ「回す → 反転 → 下げる」。
            //  反転を先にすると Y 回転の向きが逆になり、NPC が反対を向く。
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - bodyYaw));
            poseStack.scale(-1.0F, -1.0F, 1.0F);
            poseStack.translate(0.0F, -1.501F, 0.0F);
            float limbSwing = entity.walkAnimation.position(partialTicks);
            float limbSwingAmount = Math.min(entity.walkAnimation.speed(partialTicks), 1.0F);
            float headYaw = Mth.rotLerp(partialTicks, entity.yHeadRotO, entity.yHeadRot) - bodyYaw;
            float headPitch = Mth.lerp(partialTicks, entity.xRotO, entity.getXRot());
            //★EntityModel.young の既定は true。バニラは LivingEntityRenderer が毎フレーム
            //  isBaby() を入れているが、この描画器は EntityRenderer 直下なので自分で入れる。
            //  入れ忘れると HumanoidModel が子供比率 (頭 0.75 / 体 0.5) で描き、NPC が小さくなる。
            this.playerModel.young = entity.isBaby();
            this.playerModel.riding = entity.isPassenger();
            this.playerModel.attackTime = entity.getAttackAnim(partialTicks);
            this.playerModel.setupAnim(entity, limbSwing, limbSwingAmount,
                entity.tickCount + partialTicks, headYaw, headPitch);
            this.playerModel.renderToBuffer(poseStack,
                buffer.getBuffer(RenderType.entityCutoutNoCull(this.getTextureLocation(entity))),
                packedLight, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
        }
        poseStack.popPose();

        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    /**
     * 既定スキン。
     * ★本家 (1.7.10/1.12) の {@code textures/entity/steve.png} は 1.13 で
     * {@code textures/entity/player/wide/steve.png} へ移動していて、<b>1.21 に存在しない</b>。
     * 本家パックの定義 (ModelNPC_MannequinSteve など) はこの旧パスを書いているので、読み替える。
     */
    private static final ResourceLocation DEFAULT_SKIN =
        ResourceLocation.withDefaultNamespace("textures/entity/player/wide/steve.png");

    @Override
    public ResourceLocation getTextureLocation(EntityNPC entity) {
        NpcDefinition def = entity.getDefinition();
        if (def == null || def.getSkinTexture().isBlank()) {
            return DEFAULT_SKIN;
        }
        String path = remapLegacyVanillaSkin(def.getSkinTexture());
        ResourceLocation base = DEFAULT_SKIN;
        try {
            ResourceLocation loc = ResourceLocation.fromNamespaceAndPath("minecraft", path);
            if (net.minecraft.client.Minecraft.getInstance().getResourceManager()
                    .getResource(loc).isPresent()) {
                base = loc;
            }
        } catch (Exception ignored) {
            //綴りが 1.21 の規則 (小文字のみ) に合わない → パック内探索へ
        }
        if (base == DEFAULT_SKIN) {
            //同梱アセットに無い = 外部パックのテクスチャ。zip/フォルダから読んで動的登録する。
            var info = com.portofino.realtrainmodunofficial.client.PackButtonTextureCache
                .get(def.getPackName(), path);
            if (info != null && info.location() != null) {
                base = info.location();
            }
        }
        //★本家パックのスキンは 64x32 の並び (左右の腕・脚が同じ領域)。
        //  1.21 の人型は 1.8 以降の並びなので、そのまま貼ると左腕と左脚が消える。
        //  バニラと同じ旧スキン変換を通す。
        return NpcSkinLoader.getOrConvert(def.getPackName(), path, base);
    }

    /** 1.13 のフラット化でバニラのスキンが移動した分の読み替え。 */
    private static String remapLegacyVanillaSkin(String path) {
        return switch (path) {
            case "textures/entity/steve.png" -> "textures/entity/player/wide/steve.png";
            case "textures/entity/alex.png" -> "textures/entity/player/slim/alex.png";
            default -> path;
        };
    }
}
