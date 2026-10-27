package com.portofino.realtrainmodunofficial.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import jp.ngt.rtm.block.decoration.DecorationModel;
import jp.ngt.rtm.block.decoration.DecorationStore;
import jp.ngt.rtm.block.decoration.Element;
import jp.ngt.rtm.block.decoration.Face;
import jp.ngt.rtm.block.tileentity.TileEntityDecoration;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * 装飾ブロックの描画。本家 {@code RenderDecoration} の移植。
 *
 * <p>本家はブロックアトラスのスプライトを引くが、1.21 のアトラスはモデルが参照した
 * テクスチャしか繋がないので、面ごとのテクスチャを<b>ファイル直バインド</b>で描く。
 * UV はそのまま 0〜1 (アトラス内挿が無い分、本家より素直)。
 * 本家はライティングを切って描く (=フルブライト)。
 */
public class DecorationRenderer implements BlockEntityRenderer<TileEntityDecoration> {

    public DecorationRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(TileEntityDecoration tile, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {
        DecorationModel model = DecorationStore.INSTANCE.getModel(tile.getModelName());
        renderModel(model, poseStack, buffer, LightTexture.FULL_BRIGHT);
    }

    /** 面のテクスチャ指定 (1.12 形式 "ns:blocks/x") → 実ファイルパス。 */
    public static ResourceLocation toTexture(String texture) {
        if (texture == null || texture.isEmpty()) {
            return ResourceLocation.withDefaultNamespace("textures/block/stone.png");
        }
        String ns = "minecraft";
        String path = texture;
        int colon = texture.indexOf(':');
        if (colon >= 0) {
            ns = texture.substring(0, colon);
            path = texture.substring(colon + 1);
        }
        //1.13 で blocks/ → block/ に変わった。1.12 形式の指定も生かす
        if (path.startsWith("blocks/")) {
            path = "block/" + path.substring("blocks/".length());
        }
        //本家 rtm: のブロックテクスチャは RTMU 名前空間へ取り込んである
        if (ns.equals("rtm")) {
            ns = "realtrainmodunofficial";
        }
        return ResourceLocation.fromNamespaceAndPath(ns, "textures/" + path + ".png");
    }

    /** 本家 renderModel。編集画面のプレビューとアイテム描画からも使う。 */
    public static void renderModel(DecorationModel model, PoseStack poseStack,
                                   MultiBufferSource buffer, int packedLight) {
        if (model == null || model.elements == null) {
            return;
        }
        //テクスチャごとにバッファをまとめる (面の数だけ bind しない)
        Map<ResourceLocation, VertexConsumer> consumers = new HashMap<>();
        PoseStack.Pose pose = poseStack.last();
        for (Element element : model.elements) {
            if (element == null || element.faces == null) {
                continue;
            }
            for (Face face : element.faces) {
                if (face == null || face.vertex == null || face.vertex.length < 4) {
                    continue;
                }
                VertexConsumer consumer = consumers.computeIfAbsent(
                    toTexture(face.texture),
                    tex -> buffer.getBuffer(RenderType.entityCutoutNoCull(tex)));
                float shadow = face.shadow;
                //法線 (最初の3頂点から)
                float[] v0 = face.vertex[0];
                float[] v1 = face.vertex[1];
                float[] v2 = face.vertex[2];
                float ax = v1[0] - v0[0], ay = v1[1] - v0[1], az = v1[2] - v0[2];
                float bx = v2[0] - v0[0], by = v2[1] - v0[1], bz = v2[2] - v0[2];
                float nx = ay * bz - az * by;
                float ny = az * bx - ax * bz;
                float nz = ax * by - ay * bx;
                float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                if (len > 1.0E-6F) {
                    nx /= len;
                    ny /= len;
                    nz /= len;
                } else {
                    nx = 0.0F;
                    ny = 1.0F;
                    nz = 0.0F;
                }
                for (float[] vtx : face.vertex) {
                    consumer.addVertex(pose, vtx[0], vtx[1], vtx[2])
                        .setColor(shadow, shadow, shadow, 1.0F)
                        .setUv(vtx[3], vtx[4])
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(packedLight)
                        .setNormal(pose, nx, ny, nz);
                }
            }
        }
    }

    @Override
    public int getViewDistance() {
        return 128;
    }
}
