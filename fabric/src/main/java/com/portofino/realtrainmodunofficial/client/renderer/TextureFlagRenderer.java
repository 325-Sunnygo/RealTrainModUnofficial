package com.portofino.realtrainmodunofficial.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.portofino.realtrainmodunofficial.blockentity.InstalledObjectBlockEntity;
import com.portofino.realtrainmodunofficial.client.PackButtonTextureCache;
import com.portofino.realtrainmodunofficial.installedobject.InstalledObjectCategory;
import com.portofino.realtrainmodunofficial.installedobject.InstalledObjectDefinition;
import jp.ngt.ngtlib.renderer.ModelSolid;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * 本家 {@code RenderFlag} の移植。テクスチャ記述子 (textures/flag/Flag_*.json) の旗を
 * ポール + はためく布として手続き描画する。
 *
 * <p>波は本家どおり 10°/tick で進み、位相はブロック座標から決める
 * (本家は BE ごとに乱数初期化。座標ハッシュなら同期不要で同じ見た目になる)。
 * 上のブロックも旗ならポールだけ描く (本家の連結ポール)。
 */
public final class TextureFlagRenderer {

    private static final ResourceLocation POLE_TEXTURE =
        ResourceLocation.withDefaultNamespace("textures/ornament/iron.png");

    private TextureFlagRenderer() {
    }

    /** FLAG カテゴリで旗記述子を持つときだけ描く。描いたら true。 */
    public static boolean render(InstalledObjectBlockEntity blockEntity, InstalledObjectDefinition definition,
                                 float partialTick, PoseStack poseStack, MultiBufferSource buffer,
                                 int packedLight) {
        InstalledObjectDefinition.FlagParams params = definition.getFlagParams();
        if (params == null) {
            return false;
        }
        poseStack.pushPose();
        //本家 renderFlag: 原点 = ブロック底面中心
        poseStack.translate(0.5D, 0.0D, 0.5D);

        renderPole(poseStack, buffer, packedLight, params.poleLength());

        //上も旗なら布は描かない (ポール連結)
        boolean flagAbove = false;
        BlockPos above = blockEntity.getBlockPos().above();
        if (blockEntity.getLevel() != null
                && blockEntity.getLevel().getBlockEntity(above) instanceof InstalledObjectBlockEntity aboveBe) {
            flagAbove = aboveBe.getCategory() == InstalledObjectCategory.FLAG;
        }
        if (!flagAbove) {
            renderCloth(blockEntity, params, partialTick, poseStack, buffer, packedLight);
        }
        poseStack.popPose();
        return true;
    }

    /** 本家 RenderFlag.renderPole → NGTRenderer.renderPole (半径 1/16 の 16 角柱)。 */
    private static void renderPole(PoseStack poseStack, MultiBufferSource buffer, int packedLight, float poleLength) {
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(POLE_TEXTURE));
        PoseStack.Pose pose = poseStack.last();
        float[][] sp = ModelSolid.sphere;
        float r = 0.0625F;
        int l0 = (int) (poleLength * 16.0F);
        for (int l = 0; l < l0; ++l) {
            for (int i1 = 0; i1 < 16; ++i1) {
                float minU = i1 * 0.0625F;
                float maxU = (i1 + 1) * 0.0625F;
                float minV = l * 0.0625F;
                float maxV = (l + 1) * 0.0625F;
                float l1 = l * 0.0625F;
                int ii0 = 64 + i1;
                int ii2 = 64 + (i1 + 1) % 16;
                //外向き + 内向き (本家は両面を明示的に描く)
                vertex(consumer, pose, sp[ii0][0] * r, sp[ii0][1] * r + l1, sp[ii0][2] * r, maxU, maxV, packedLight);
                vertex(consumer, pose, sp[ii0][0] * r, sp[ii0][1] * r + 0.0625F + l1, sp[ii0][2] * r, maxU, minV, packedLight);
                vertex(consumer, pose, sp[ii2][0] * r, sp[ii2][1] * r + 0.0625F + l1, sp[ii2][2] * r, minU, minV, packedLight);
                vertex(consumer, pose, sp[ii2][0] * r, sp[ii2][1] * r + l1, sp[ii2][2] * r, minU, maxV, packedLight);
                vertex(consumer, pose, sp[ii2][0] * r, sp[ii2][1] * r + l1, sp[ii2][2] * r, maxU, maxV, packedLight);
                vertex(consumer, pose, sp[ii2][0] * r, sp[ii2][1] * r + 0.0625F + l1, sp[ii2][2] * r, maxU, minV, packedLight);
                vertex(consumer, pose, sp[ii0][0] * r, sp[ii0][1] * r + 0.0625F + l1, sp[ii0][2] * r, minU, minV, packedLight);
                vertex(consumer, pose, sp[ii0][0] * r, sp[ii0][1] * r + l1, sp[ii0][2] * r, minU, maxV, packedLight);
            }
        }
    }

    private static void renderCloth(InstalledObjectBlockEntity blockEntity, InstalledObjectDefinition.FlagParams params,
                                    float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();
        poseStack.translate(0.0F, params.poleLength(), 0.0F);
        float yaw = blockEntity.getYaw();
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(yaw));

        //本家 TileEntityFlag: wave += 10/tick、初期位相は乱数 → 座標ハッシュで代替
        long time = blockEntity.getLevel() != null ? blockEntity.getLevel().getGameTime() : 0L;
        float phase = (blockEntity.getBlockPos().hashCode() & 0xFFFF) % 360;
        float wave = ((time * 10.0F + partialTick * 10.0F + phase) % 360.0F);

        ResourceLocation texture = resolveTexture(blockEntity, params.texture());
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(texture));
        PoseStack.Pose pose = poseStack.last();

        float wind = 1.0F;
        float windInv = 1.0F - wind;
        float h = params.height();
        float w = params.width();
        int resV = params.resolutionV();
        int resU = params.resolutionU();

        for (int i = 0; i < resV; ++i) {
            float v0 = (float) i / resV;
            float v1 = (float) (i + 1) / resV;
            for (int j = 0; j < resU; ++j) {
                float u0 = (float) j / resU;
                float u1 = (float) (j + 1) / resU;
                float u0w = u0 * w;
                float u1w = u1 * w;

                clothVertex(consumer, pose, wave, yaw, u1, v0, u1w, windInv, wind, h, packedLight);
                clothVertex(consumer, pose, wave, yaw, u1, v1, u1w, windInv, wind, h, packedLight);
                clothVertex(consumer, pose, wave, yaw, u0, v1, u0w, windInv, wind, h, packedLight);
                clothVertex(consumer, pose, wave, yaw, u0, v0, u0w, windInv, wind, h, packedLight);
            }
        }
        poseStack.popPose();
    }

    private static void clothVertex(VertexConsumer consumer, PoseStack.Pose pose, float wave, float yaw,
                                    float u, float v, float uw, float windInv, float wind, float h, int packedLight) {
        float r = getR(wave, u, v);
        float d = getWave(r, u);
        float nr = getNormalR(r + yaw);
        consumer.addVertex(pose, d, -(v + windInv * uw) * h, uw * wind)
            .setColor(255, 255, 255, 255)
            .setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(packedLight)
            .setNormal(pose, Mth.sin(nr), 0.0F, Mth.cos(nr));
    }

    private static float getR(float r, float u, float v) {
        return -(float) Math.toRadians(r + 360.0F / (3.0F * u + 1.0F) * (v + 1.0F));
    }

    private static float getWave(float r, float u) {
        return Mth.sin(r) * u * 0.15F;
    }

    private static float getNormalR(float r) {
        return (float) Math.toRadians(45.0F * Mth.cos(r) + 90.0F);
    }

    /**
     * 旗テクスチャの解決。同梱アセット (minecraft ns) → パック内ファイルの順。
     */
    public static ResourceLocation resolveTexture(String packName, String path) {
        //ResourceLocation は大文字不可 → 同梱の旗テクスチャは小文字化して同梱してある
        ResourceLocation direct = ResourceLocation.withDefaultNamespace(
            path.toLowerCase(java.util.Locale.ROOT));
        if (Minecraft.getInstance().getResourceManager().getResource(direct).isPresent()) {
            return direct;
        }
        PackButtonTextureCache.ButtonTextureInfo info = PackButtonTextureCache.get(packName, path);
        return info != null && info.location() != null ? info.location() : direct;
    }

    private static ResourceLocation resolveTexture(InstalledObjectBlockEntity blockEntity, String path) {
        //ResourceLocation は大文字不可 → 同梱の旗テクスチャは小文字化して同梱してある
        ResourceLocation direct = ResourceLocation.withDefaultNamespace(
            path.toLowerCase(java.util.Locale.ROOT));
        if (Minecraft.getInstance().getResourceManager().getResource(direct).isPresent()) {
            return direct;
        }
        InstalledObjectDefinition def = com.portofino.realtrainmodunofficial.installedobject
            .InstalledObjectRegistry.getById(blockEntity.getDefinitionId());
        if (def != null) {
            PackButtonTextureCache.ButtonTextureInfo info =
                PackButtonTextureCache.get(def.getPackName(), path);
            if (info != null && info.location() != null) {
                return info.location();
            }
        }
        return direct;
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose,
                               float x, float y, float z, float u, float v, int packedLight) {
        consumer.addVertex(pose, x, y, z)
            .setColor(255, 255, 255, 255)
            .setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(packedLight)
            .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }
}
