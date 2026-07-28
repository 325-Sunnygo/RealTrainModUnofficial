package com.portofino.realtrainmodunofficial.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * 選択範囲の描画 (neo mcte)。MCTEU {@code BlockSelectionRenderer} と同じ<b>レベル描画</b>方式。
 *
 * <p>★エンティティレンダラで描いてはいけない。1.21 は<b>見えているチャンクセクションに
 * 属するエンティティしか集めない</b>ので、選択範囲の起点が視界外のセクションにあると
 * 枠ごと消える (「前を向くと選択が見えない、後ろを向くと見える」)。
 * レベル描画なら視界に依らず必ず出る。
 */
@EventBusSubscriber(modid = RealTrainModUnofficial.MODID, value = Dist.CLIENT)
public final class SelectionRenderer {

    /** 未確定 (1 点目だけ) のときの色。 */
    private static final float[] COLOR_PENDING = {1.0F, 0.8F, 0.2F};
    /** 確定後の色。 */
    private static final float[] COLOR_FIXED = {0.3F, 0.8F, 1.0F};

    /** MCTEU {@code mcte_selection_faces} 相当。QUADS でないと三角に化ける。 */
    private static final RenderType FACES = RenderType.create(
        "rtmu_selection_faces", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS,
        1536, false, true,
        RenderType.CompositeState.builder()
            .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
            .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
            .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
            .setCullState(RenderStateShard.NO_CULL)
            .setWriteMaskState(RenderStateShard.COLOR_WRITE)
            .createCompositeState(false));

    /** クローンの下見 (MCTEU {@code CopyPreviewRenderer} 相当)。 */
    private static final float[] COLOR_CLONE = {0.4F, 1.0F, 0.4F};
    private static int cloneDx;
    private static int cloneDy;
    private static int cloneDz;
    private static int cloneRepeat;

    /**
     * ★最後に見えていた選択範囲。
     * <p>エディタは Entity なので、遠くへ飛ぶ・上に上がるとクライアント側で追跡が切れて
     * {@code find} が null になり、選択範囲が消えていた。表示用にここへ控えておき、
     * 追跡が切れても描き続ける。解除 (N キー) で捨てる。
     */
    private static AABB cachedBox;
    private static boolean cachedHasEnd;

    /** 画面のクローン欄が変わったら呼ぶ。 */
    public static void setClonePreview(int dx, int dy, int dz, int repeat) {
        cloneDx = dx;
        cloneDy = dy;
        cloneDz = dz;
        cloneRepeat = Math.max(0, Math.min(64, repeat));
    }

    /** 選択解除時に控えも捨てる。 */
    public static void forget() {
        cachedBox = null;
        cachedHasEnd = false;
        cloneRepeat = 0;
    }

    private SelectionRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        //★持っているアイテムで出し分けない。
        //MCTEU も pos1/pos2 があれば常に描く (BlockSelectionRenderer:36-41)。
        //エディタを持っているときだけにすると、埋めるブロックへ持ち替えた瞬間に
        //選択が消えたように見える。解除するまで出したままにする。
        //★選択はクライアントの静的データ。ワールドに実体が無いので消えない。
        AABB box = com.portofino.realtrainmodunofficial.client.ClientSelection.box();
        if (box == null) {
            return;
        }
        boolean hasEnd = com.portofino.realtrainmodunofficial.client.ClientSelection.hasEnd();

        if (!hasEnd) {
            //1 点目だけのときは、見ている先を仮の 2 点目として箱を出す
            //(本家 MCTE の「選択を始めると箱が視点についてくる」挙動)
            if (mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
                BlockPos a = com.portofino.realtrainmodunofficial.client.ClientSelection.pos1();
                BlockPos b = hit.getBlockPos();
                box = new AABB(
                    Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()),
                    Math.max(a.getX(), b.getX()) + 1.0D,
                    Math.max(a.getY(), b.getY()) + 1.0D,
                    Math.max(a.getZ(), b.getZ()) + 1.0D);
            }
        }
        float[] c = hasEnd ? COLOR_FIXED : COLOR_PENDING;

        Vec3 cam = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-cam.x, -cam.y, -cam.z);

        MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();
        drawFaces(poseStack, buffer.getBuffer(FACES), box, c[0], c[1], c[2], 0.16F);
        buffer.endBatch(FACES);

        VertexConsumer lines = buffer.getBuffer(RenderType.lines());
        LevelRenderer.renderLineBox(poseStack, lines, box, c[0], c[1], c[2], 1.0F);

        //クローンの下見: ずらした先を枠だけで出す (どこにいくつ落ちるかを見せる)
        if (hasEnd && cloneRepeat > 0 && (cloneDx != 0 || cloneDy != 0 || cloneDz != 0)) {
            for (int r = 1; r <= cloneRepeat; r++) {
                AABB p = box.move(cloneDx * r, cloneDy * r, cloneDz * r);
                LevelRenderer.renderLineBox(poseStack, lines, p,
                    COLOR_CLONE[0], COLOR_CLONE[1], COLOR_CLONE[2], 0.9F);
            }
        }
        buffer.endBatch(RenderType.lines());

        poseStack.popPose();
    }

    private static void drawFaces(PoseStack poseStack, VertexConsumer vc, AABB b,
                                  float r, float g, float bl, float a) {
        var m = poseStack.last().pose();
        float x0 = (float) b.minX, y0 = (float) b.minY, z0 = (float) b.minZ;
        float x1 = (float) b.maxX, y1 = (float) b.maxY, z1 = (float) b.maxZ;
        quad(vc, m, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, r, g, bl, a);
        quad(vc, m, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, r, g, bl, a);
        quad(vc, m, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, r, g, bl, a);
        quad(vc, m, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, r, g, bl, a);
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
