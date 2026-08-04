package com.portofino.realtrainmodunofficial.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.portofino.realtrainmodunofficial.block.BackgroundPanelBlock;
import com.portofino.realtrainmodunofficial.blockentity.BackgroundPanelBlockEntity;
import com.portofino.realtrainmodunofficial.client.render.BackgroundTextures;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;

/**
 * 背景パネルの描画。
 *
 * <p>置いたブロックの位置から、向いている方向に垂直な<b>板</b>を 1 枚立てる。
 * 画像が未設定なら<b>紫と黒の格子</b> (Minecraft の「テクスチャが無い」印) を出す。
 * これが出ていれば「置けているが画像がまだ」と一目で分かる。
 *
 * <p>裏からも見えるように<b>両面描く</b>。背景なので影は落とさずフルブライト。
 */
public class BackgroundPanelBlockEntityRenderer
        implements BlockEntityRenderer<BackgroundPanelBlockEntity> {

    /** 未設定のときに出す紫と黒の格子。 */
    private static final ResourceLocation MISSING = MissingTextureAtlasSprite.getLocation();

    public BackgroundPanelBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(BackgroundPanelBlockEntity be, float partialTick, PoseStack pose,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {
        BackgroundTextures.Loaded loaded = BackgroundTextures.get(be.getImage());
        ResourceLocation tex = loaded == null ? MISSING : loaded.location();
        float width = be.getScale();
        //画像の縦横比で高さを決める。未設定 (格子) のときは 16:9 で置く
        float height = width * (loaded == null || loaded.width() <= 0
            ? 9.0F / 16.0F : (float) loaded.height() / (float) loaded.width());

        Direction facing = be.getBlockState().hasProperty(BackgroundPanelBlock.FACING)
            ? be.getBlockState().getValue(BackgroundPanelBlock.FACING) : Direction.NORTH;

        pose.pushPose();
        //ブロックの中心へ。そこから板を立てる
        pose.translate(0.5D, be.getOffsetY(), 0.5D);
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-facing.toYRot()));

        VertexConsumer vc = buffer.getBuffer(RenderType.entityTranslucentCull(tex));
        Matrix4f m = pose.last().pose();
        float hw = width * 0.5F;

        //表 (facing の向き)。左下 → 右下 → 右上 → 左上
        quad(vc, m, pose, -hw, 0.0F, 0.0F, hw, height, 0.0F, false);
        //裏。同じ面を逆回りで置く (両面から見えるように)
        quad(vc, m, pose, -hw, 0.0F, 0.0F, hw, height, 0.0F, true);

        pose.popPose();
    }

    private static void quad(VertexConsumer vc, Matrix4f m, PoseStack pose,
                             float x0, float y0, float z, float x1, float y1, float z1,
                             boolean back) {
        float nz = back ? 1.0F : -1.0F;
        if (back) {
            vertex(vc, m, pose, x0, y0, z, 1.0F, 1.0F, nz);
            vertex(vc, m, pose, x0, y1, z, 1.0F, 0.0F, nz);
            vertex(vc, m, pose, x1, y1, z, 0.0F, 0.0F, nz);
            vertex(vc, m, pose, x1, y0, z, 0.0F, 1.0F, nz);
        } else {
            vertex(vc, m, pose, x1, y0, z, 1.0F, 1.0F, nz);
            vertex(vc, m, pose, x1, y1, z, 1.0F, 0.0F, nz);
            vertex(vc, m, pose, x0, y1, z, 0.0F, 0.0F, nz);
            vertex(vc, m, pose, x0, y0, z, 0.0F, 1.0F, nz);
        }
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, PoseStack pose,
                               float x, float y, float z, float u, float v, float nz) {
        vc.addVertex(m, x, y, z)
          .setColor(0xFFFFFFFF)
          .setUv(u, v)
          .setOverlay(OverlayTexture.NO_OVERLAY)
          //背景なので影を落とさない
          .setLight(0x00F000F0)
          .setNormal(pose.last(), 0.0F, 0.0F, nz);
    }

    /** ★これが無いと、足元のブロックが画面外に出た瞬間に背景ごと消える。 */
    @Override
    public AABB getRenderBoundingBox(BackgroundPanelBlockEntity be) {
        return be.getRenderBoundingBox();
    }

    /** 大きい背景は遠くからでも見えてほしい。 */
    @Override
    public int getViewDistance() {
        return 256;
    }
}
