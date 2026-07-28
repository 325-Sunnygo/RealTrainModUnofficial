package com.portofino.realtrainmodunofficial.client.camera;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import com.portofino.realtrainmodunofficial.client.ShaderCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

/**
 * カメラのポストエフェクト。被写界深度 (ボケ) と 流し撮り (残像) を掛ける。
 * 本家 jp.ngt.rtm.gui.camera.Camera が生の GL (ARB シェーダー + FBO) でやっていたことを、
 * 1.21 のコアシェーダー (ShaderInstance) と RenderTarget で書き直したもの。
 */
public final class CameraPostProcessor {

    /** 最大ぼかし半径 (px, 1080p 基準)。これ以上はいくら絞りを開けてもぼけない。 */
    private static final float MAX_BLUR_PX = 22.0F;

    private static ShaderInstance dofShader;
    private static ShaderInstance accumShader;
    private static ShaderInstance blitShader;

    private static RenderTarget blurA;
    private static RenderTarget blurB;
    /** 流し撮り用の蓄積バッファ (前フレームの結果) */
    private static RenderTarget accum;
    private static boolean accumValid;
    private static int lastW = -1;
    private static int lastH = -1;

    private CameraPostProcessor() {
    }

    public static void registerShaders(RegisterShadersEvent event) throws java.io.IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
            ResourceLocation.fromNamespaceAndPath(RealTrainModUnofficial.MODID, "rtm_dof"),
            DefaultVertexFormat.POSITION_TEX), s -> dofShader = s);
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
            ResourceLocation.fromNamespaceAndPath(RealTrainModUnofficial.MODID, "rtm_accum"),
            DefaultVertexFormat.POSITION_TEX), s -> accumShader = s);
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
            ResourceLocation.fromNamespaceAndPath(RealTrainModUnofficial.MODID, "rtm_blit"),
            DefaultVertexFormat.POSITION_TEX), s -> blitShader = s);
    }

    /** カメラを閉じたら蓄積を捨てる (次に開いたとき前の絵が残らないように)。 */
    public static void reset() {
        accumValid = false;
    }

    /** ワールド描画後・GUI 描画前に呼ぶ。 */
    public static void process(Minecraft mc, CameraState state, float focusDistance) {
        if (dofShader == null || accumShader == null || blitShader == null) {
            return;
        }
        // Iris/Oculus のシェーダーパックが有効なときはコアシェーダーが差し替わっており、
        // こちらのパスを重ねると壊れる。ボケはシェーダーパック側に任せる。
        if (ShaderCompat.isShaderPackInUse()) {
            return;
        }
        float bokeh = state.getBokehStrength();
        float motion = state.getMotionBlend();
        if (bokeh <= 0.01F && motion <= 0.01F) {
            accumValid = false;
            return;
        }

        RenderTarget main = mc.getMainRenderTarget();
        int w = main.width;
        int h = main.height;
        if (w <= 0 || h <= 0) {
            return;
        }
        ensureTargets(w, h);

        // 描画状態を退避 (ここは GUI 描画の直前なので、戻さないとバニラの GUI が壊れる)
        Matrix4f savedProj = RenderSystem.getProjectionMatrix();
        com.mojang.blaze3d.vertex.VertexSorting savedSort = RenderSystem.getVertexSorting();
        Matrix4fStack mv = RenderSystem.getModelViewStack();
        mv.pushMatrix();
        mv.identity();
        RenderSystem.applyModelViewMatrix();
        // クリップ空間へ直接頂点を出すので投影は単位行列
        RenderSystem.setProjectionMatrix(new Matrix4f(), com.mojang.blaze3d.vertex.VertexSorting.ORTHOGRAPHIC_Z);
        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);

        try {
            int sceneTex = main.getColorTextureId();
            int depthTex = main.getDepthTextureId();
            // MC の投影は near=0.05 固定。far はレンダー距離から決まる。
            float near = 0.05F;
            float far = Math.max(near + 1.0F, mc.gameRenderer.getDepthFar());
            // 画面が大きいほどぼかし半径も比例させる (720p と 4K で見た目を揃える)
            float maxRadius = MAX_BLUR_PX * (h / 1080.0F);

            int result = sceneTex;

            if (bokeh > 0.01F) {
                // 横ぼかし: main.color + main.depth → blurA
                blurA.bindWrite(false);
                dofPass(sceneTex, depthTex, w, h, 1.0F, 0.0F, focusDistance, bokeh, near, far, maxRadius);
                // 縦ぼかし: blurA + main.depth → blurB
                blurB.bindWrite(false);
                dofPass(blurA.getColorTextureId(), depthTex, w, h, 0.0F, 1.0F, focusDistance, bokeh, near, far, maxRadius);
                result = blurB.getColorTextureId();
            }

            if (motion > 0.01F) {
                // 前フレームと合成。accum は前回の結果を持っているので、
                // 読みながら書けないよう blurA を作業用に使い回す。
                blurA.bindWrite(false);
                if (accumValid) {
                    accumPass(result, accum.getColorTextureId(), motion);
                } else {
                    blitPass(result);
                }
                // 結果を accum へ写して次フレームに備える
                accum.bindWrite(false);
                blitPass(blurA.getColorTextureId());
                accumValid = true;
                result = accum.getColorTextureId();
            } else {
                accumValid = false;
            }

            // メインターゲットへ書き戻す
            main.bindWrite(false);
            if (result != sceneTex) {
                blitPass(result);
            }
        } catch (Throwable t) {
            RealTrainModUnofficial.LOGGER.warn("Camera post-processing failed; disabling effects", t);
            dofShader = null;
        } finally {
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.enableBlend();
            RenderSystem.setProjectionMatrix(savedProj, savedSort);
            mv.popMatrix();
            RenderSystem.applyModelViewMatrix();
            main.bindWrite(true);
        }
    }

    private static void dofPass(int colorTex, int depthTex, int w, int h,
                                float dirX, float dirY, float focus, float bokeh,
                                float near, float far, float maxRadius) {
        RenderSystem.setShader(() -> dofShader);
        RenderSystem.setShaderTexture(0, colorTex);
        RenderSystem.setShaderTexture(1, depthTex);
        setUniform(dofShader, "InSize", (float) w, (float) h);
        setUniform(dofShader, "BlurDir", dirX, dirY);
        setUniform(dofShader, "FocusDepth", focus);
        setUniform(dofShader, "Bokeh", bokeh);
        setUniform(dofShader, "NearPlane", near);
        setUniform(dofShader, "FarPlane", far);
        setUniform(dofShader, "MaxRadius", maxRadius);
        drawFullscreenQuad();
    }

    private static void accumPass(int currentTex, int prevTex, float blend) {
        RenderSystem.setShader(() -> accumShader);
        RenderSystem.setShaderTexture(0, currentTex);
        RenderSystem.setShaderTexture(1, prevTex);
        setUniform(accumShader, "Blend", blend);
        drawFullscreenQuad();
    }

    private static void blitPass(int tex) {
        RenderSystem.setShader(() -> blitShader);
        RenderSystem.setShaderTexture(0, tex);
        drawFullscreenQuad();
    }

    private static void setUniform(ShaderInstance shader, String name, float... values) {
        com.mojang.blaze3d.shaders.Uniform u = shader.getUniform(name);
        if (u == null) {
            return;
        }
        switch (values.length) {
            case 1 -> u.set(values[0]);
            case 2 -> u.set(values[0], values[1]);
            default -> {
            }
        }
    }

    /** クリップ空間 (-1..1) に直接四角形を出す。投影/モデルビューは単位行列にしてある。 */
    private static void drawFullscreenQuad() {
        BufferBuilder buffer = Tesselator.getInstance()
            .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        buffer.addVertex(-1.0F, -1.0F, 0.0F).setUv(0.0F, 0.0F);
        buffer.addVertex(1.0F, -1.0F, 0.0F).setUv(1.0F, 0.0F);
        buffer.addVertex(1.0F, 1.0F, 0.0F).setUv(1.0F, 1.0F);
        buffer.addVertex(-1.0F, 1.0F, 0.0F).setUv(0.0F, 1.0F);
        MeshData mesh = buffer.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }
    }

    private static void ensureTargets(int w, int h) {
        if (blurA != null && lastW == w && lastH == h) {
            return;
        }
        destroy();
        // 深度は不要 (メインターゲットのものを読む)
        blurA = new TextureTarget(w, h, false, Minecraft.ON_OSX);
        blurB = new TextureTarget(w, h, false, Minecraft.ON_OSX);
        accum = new TextureTarget(w, h, false, Minecraft.ON_OSX);
        for (RenderTarget t : new RenderTarget[]{blurA, blurB, accum}) {
            t.setFilterMode(com.mojang.blaze3d.platform.GlConst.GL_LINEAR);
            t.setClearColor(0.0F, 0.0F, 0.0F, 1.0F);
        }
        lastW = w;
        lastH = h;
        accumValid = false;
    }

    private static void destroy() {
        for (RenderTarget t : new RenderTarget[]{blurA, blurB, accum}) {
            if (t != null) {
                t.destroyBuffers();
            }
        }
        blurA = null;
        blurB = null;
        accum = null;
        accumValid = false;
    }
}
