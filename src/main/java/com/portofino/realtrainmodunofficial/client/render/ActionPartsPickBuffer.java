package com.portofino.realtrainmodunofficial.client.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.portofino.realtrainmodunofficial.client.ActionPartsHost;
import jp.ngt.ngtlib.renderer.model.GroupObject;
import jp.ngt.ngtlib.renderer.model.PolygonModel;
import jp.ngt.rtm.render.ActionParts;
import jp.ngt.rtm.render.Parts;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * ActionParts の当たり判定用 色ピッキング FBO。
 *
 * <p>本家は PICK パスで ActionParts だけを {@code glLoadName(id)} 付きで描き、
 * GL_SELECT の選択バッファからパーツを決めていた。GL_SELECT は modern OpenGL core で
 * 削除されているため、その代替として <b>ActionParts だけ</b>をパーツ ID 色で
 * オフスクリーン (TextureTarget) に描き、画面中央ピクセルを読んでパーツを決める。
 *
 * <p>ピッキング頂点は専用の {@link MultiBufferSource.BufferSource} へ流すため、
 * 呼び出し側のバッファ種別 (車両描画の buffer) に依存しない。
 */
public final class ActionPartsPickBuffer {

    /**
     * ピッキング描画用 RenderType。色をそのまま出力する必要があるため
     * POSITION_COLOR (position_color シェーダ) を使い、テクスチャ/ライトマップ/
     * ブレンドを一切適用しない。
     */
    public static final RenderType PICK_RT = RenderType.create(
        "rtmu_actionparts_pick",
        com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR,
        com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,
        256,
        false,
        true,
        RenderType.CompositeState.builder()
            .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
            .setTextureState(RenderStateShard.NO_TEXTURE)
            .setTransparencyState(RenderStateShard.NO_TRANSPARENCY)
            .setCullState(RenderStateShard.CULL)
            .setLightmapState(RenderStateShard.NO_LIGHTMAP)
            .setOverlayState(RenderStateShard.NO_OVERLAY)
            .setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE)
            .createCompositeState(false));

    private static TextureTarget target;
    private static MultiBufferSource.BufferSource pickBuffer;
    private static final ByteBuffer PIXEL = ByteBuffer.allocateDirect(4);

    private static boolean active;
    private static ActionPartsHost host;

    private ActionPartsPickBuffer() {
    }

    public static boolean isActive() {
        return active;
    }

    /** ピッキング描画を開始する。以降の emitGroups は専用バッファへ色付きで流れる。 */
    public static void begin(ActionPartsHost partsHost) {
        host = partsHost;
        active = partsHost != null && !partsHost.getTargetsList().isEmpty();
        if (active) {
            pickBuffer = MultiBufferSource.immediate(new ByteBufferBuilder(1536));
        } else {
            pickBuffer = null;
        }
    }

    /** 専用バッファをオフスクリーンへ流し、画面中央ピクセルからパーツ ID を返す。 */
    public static int finish() {
        MultiBufferSource.BufferSource bs = pickBuffer;
        active = false;
        pickBuffer = null;
        host = null;
        if (bs == null) {
            return -1;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() == null) {
            return -1;
        }
        int w = mc.getWindow().getWidth();
        int h = mc.getWindow().getHeight();
        if (target == null) {
            target = new TextureTarget(w, h, true, Minecraft.ON_OSX);
        } else if (target.width != w || target.height != h) {
            target.resize(w, h, Minecraft.ON_OSX);
        }
        RenderTarget main = mc.getMainRenderTarget();
        target.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
        target.clear(Minecraft.ON_OSX);
        target.bindWrite(true);
        // 画面中央 = 照準位置。ここに向けて描いた色付き頂点を flush する。
        bs.endBatch(PICK_RT);
        // ★main に戻す前に読む (戻すと読み取り先が main になる)。
        PIXEL.clear();
        GlStateManager._readPixels(w / 2, h / 2, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, PIXEL);
        main.bindWrite(true);

        int r = PIXEL.get(0) & 0xFF;
        int g = PIXEL.get(1) & 0xFF;
        int b = PIXEL.get(2) & 0xFF;
        return (r << 16) | (g << 8) | b;
    }

    /** ピッキングパーツ ID に対応する色で、指定グループを頂点として流す。 */
    public static void emitGroups(Set<String> normalizedNames, PoseStack poseStack, int light, int overlay) {
        if (!active || host == null || pickBuffer == null || normalizedNames == null) {
            return;
        }
        PolygonModel model = host.getPolygonModel();
        if (model == null) {
            return;
        }
        List<Parts> targets = host.getTargetsList();
        VertexConsumer vc = pickBuffer.getBuffer(PICK_RT);
        Matrix4f mat = poseStack.last().pose();
        for (Parts p : targets) {
            if (!(p instanceof ActionParts ap)) {
                continue;
            }
            float cr = ((ap.id >> 16) & 0xFF) / 255.0F;
            float cg = ((ap.id >> 8) & 0xFF) / 255.0F;
            float cb = (ap.id & 0xFF) / 255.0F;
            String[] names = ap.getNames();
            if (names == null) {
                continue;
            }
            for (String rawName : names) {
                if (rawName == null) {
                    continue;
                }
                String key = rawName.trim().toLowerCase(Locale.ROOT);
                if (!normalizedNames.contains(key)) {
                    continue;
                }
                GroupObject group = findGroup(model, key);
                if (group == null) {
                    continue;
                }
                float[] tris = ActionParts.buildOutlineTriangles(group, cr, cg, cb);
                int count = tris.length / 9;
                for (int t = 0; t + 2 < count; t += 3) {
                    for (int k = 0; k < 3; k++) {
                        int o = (t + k) * 9;
                        // POSITION_COLOR 形式なので position と color だけ書く。
                        VertexWriter.addVertex(vc, mat, tris[o], tris[o + 1], tris[o + 2])
                            .setColor(cr, cg, cb, 1.0F);
                    }
                }
            }
        }
    }

    private static GroupObject findGroup(PolygonModel model, String normalizedName) {
        for (GroupObject g : model.groupObjects) {
            if (g.name != null && g.name.trim().toLowerCase(Locale.ROOT).equals(normalizedName)) {
                return g;
            }
        }
        return null;
    }
}
