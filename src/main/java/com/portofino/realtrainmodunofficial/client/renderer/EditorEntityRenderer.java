package com.portofino.realtrainmodunofficial.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.portofino.realtrainmodunofficial.entity.EditorEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;

/**
 * エディタの選択範囲の描画 (neo mcte)。本家 MCTE RenderEditor 相当。
 * 本家は選択範囲を枠 + 半透明の面で描く。ここも同じにしてある。
 */
public class EditorEntityRenderer extends EntityRenderer<EditorEntity> {

    /**
     * 選択範囲の面。MCTEU mcte_selection_faces 相当。
     * POSITION_COLOR / QUADS / 加算合成 / 両面 / 深度書き込みあり。
     */
    private static final RenderType SELECTION_FACES = RenderType.create(
        "rtmu_selection_faces",
        com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR,
        com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, 1536, false, true,
        RenderType.CompositeState.builder()
            .setShaderState(net.minecraft.client.renderer.RenderStateShard.POSITION_COLOR_SHADER)
            .setTransparencyState(net.minecraft.client.renderer.RenderStateShard.TRANSLUCENT_TRANSPARENCY)
            .setDepthTestState(net.minecraft.client.renderer.RenderStateShard.LEQUAL_DEPTH_TEST)
            .setCullState(net.minecraft.client.renderer.RenderStateShard.NO_CULL)
            .setWriteMaskState(net.minecraft.client.renderer.RenderStateShard.COLOR_WRITE)
            .createCompositeState(false));

    /** 未確定 (1 点目だけ) のときの色。 */
    private static final float[] COLOR_PENDING = {1.0F, 0.8F, 0.2F};
    /** 確定後の色。 */
    private static final float[] COLOR_FIXED = {0.3F, 0.8F, 1.0F};

    public EditorEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(EditorEntity entity) {
        return ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    }

    @Override
    public boolean shouldRender(EditorEntity entity, net.minecraft.client.renderer.culling.Frustum frustum,
                                double camX, double camY, double camZ) {
        // 範囲が広いとエンティティ本体は視界外でも枠は見えるべきなので、常に描く判定にする
        return true;
    }

    @Override
    public void render(EditorEntity entity, float yaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        // ★描画は SelectionRenderer (レベル描画) が行う。
        // エンティティ経路だと視界外セクションのとき枠ごと消えるため
        // (「前を向くと選択が見えない」)。ここでは何もしない。
    }

    /** LevelRenderer.renderLineBox に面版が無いので自前で 6 面張る。 */
    private static void renderFilled(PoseStack poseStack, VertexConsumer vc, AABB b,
                                     float r, float g, float bl, float a) {
        var m = poseStack.last().pose();
        float x0 = (float) b.minX, y0 = (float) b.minY, z0 = (float) b.minZ;
        float x1 = (float) b.maxX, y1 = (float) b.maxY, z1 = (float) b.maxZ;

        // 下面 / 上面
        quad(vc, m, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, r, g, bl, a);
        quad(vc, m, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, r, g, bl, a);
        // 北 / 南
        quad(vc, m, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, r, g, bl, a);
        quad(vc, m, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, r, g, bl, a);
        // 西 / 東
        quad(vc, m, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, r, g, bl, a);
        quad(vc, m, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, r, g, bl, a);
    }

    private static void quad(VertexConsumer vc, org.joml.Matrix4f m,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz,
                             float r, float g, float b, float a) {
        vc.addVertex(m, ax, ay, az).setColor(r, g, b, a);
        vc.addVertex(m, bx, by, bz).setColor(r, g, b, a);
        vc.addVertex(m, cx, cy, cz).setColor(r, g, b, a);
        vc.addVertex(m, dx, dy, dz).setColor(r, g, b, a);
    }
}
