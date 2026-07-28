package jp.kaiz.atsassistmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.portofino.realtrainmodunofficial.RealTrainModUnofficialItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;

/**
 * 本家 jp.kaiz.atsassistmod.render.TileEntityBeamRenderer の移植。
 * RTM のバールを持っている間、地上子 / IFTTT ブロックの位置に
 * 水色 (RGB 0,190,246 / α32) の光の柱を y=256 まで表示する (設置場所を探しやすくする)。
 */
public class BeamRenderer<T extends BlockEntity> implements BlockEntityRenderer<T> {

    //本家: 幅 0.3 (0.35..0.65)、ビーコンビームのテクスチャ
    private static final float MIN = 0.5F - 0.15F;
    private static final float MAX = 0.5F + 0.15F;

    public static boolean isHoldingCrowbar() {
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return false;
        }
        return player.getMainHandItem().is(RealTrainModUnofficialItems.CROWBAR_ITEM.get())
                || player.getOffhandItem().is(RealTrainModUnofficialItems.CROWBAR_ITEM.get());
    }

    @Override
    public void render(T blockEntity, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (!isHoldingCrowbar()) {
            return;
        }
        BlockPos pos = blockEntity.getBlockPos();
        float height = 256.0F - pos.getY();
        if (height <= 0) {
            return;
        }
        //本家: beacon_beam テクスチャの半透明カラム (fullbright / 両面 / depth write なし)
        VertexConsumer buffer = bufferSource.getBuffer(
                RenderType.beaconBeam(BeaconRenderer.BEAM_LOCATION, true));
        Matrix4f matrix = poseStack.last().pose();
        int r = 0, g = 190, b = 246, a = 32;
        float v1 = height;
        //四面 (本家と同じく内外両方見えるよう BeaconBeam レンダタイプは両面描画)
        quad(buffer, matrix, MIN, MAX, MIN, MIN, height, r, g, b, a, v1);   //西面
        quad(buffer, matrix, MAX, MIN, MAX, MAX, height, r, g, b, a, v1);   //東面
        quad(buffer, matrix, MIN, MIN, MAX, MIN, height, r, g, b, a, v1);   //北面
        quad(buffer, matrix, MAX, MAX, MIN, MAX, height, r, g, b, a, v1);   //南面
    }

    private static void quad(VertexConsumer buffer, Matrix4f matrix,
                             float x1, float x2, float z1, float z2, float height,
                             int r, int g, int b, int a, float vMax) {
        vertex(buffer, matrix, x1, height, z1, r, g, b, a, 1.0F, vMax);
        vertex(buffer, matrix, x1, 0.0F, z1, r, g, b, a, 1.0F, 0.0F);
        vertex(buffer, matrix, x2, 0.0F, z2, r, g, b, a, 0.0F, 0.0F);
        vertex(buffer, matrix, x2, height, z2, r, g, b, a, 0.0F, vMax);
    }

    private static void vertex(VertexConsumer buffer, Matrix4f matrix,
                               float x, float y, float z, int r, int g, int b, int a, float u, float v) {
        buffer.addVertex(matrix, x, y, z)
                .setColor(r, g, b, a)
                .setUv(u, v)
                .setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                .setLight(0xF000F0)
                .setNormal(0.0F, 1.0F, 0.0F);
    }

    //遠くからでも柱が見えるように (本家 getMaxRenderDistanceSquared=65536 / INFINITE_EXTENT_AABB)

    @Override
    public boolean shouldRenderOffScreen(T blockEntity) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 256;
    }

    @Override
    public AABB getRenderBoundingBox(T blockEntity) {
        BlockPos pos = blockEntity.getBlockPos();
        return new AABB(pos.getX() - 1, blockEntity.getLevel() == null ? -64 : blockEntity.getLevel().getMinBuildHeight(),
                pos.getZ() - 1, pos.getX() + 2, 320, pos.getZ() + 2);
    }
}
