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

/** Original eight depth bands and ten-frame sensitivity snapshot, on core-profile FBOs. */
public final class CameraPostProcessor {
    private static final org.slf4j.Logger LOGGER=org.slf4j.LoggerFactory.getLogger(CameraPostProcessor.class);
    private static ShaderInstance dofShader,accumShader,blitShader;
    private static RenderTarget blurA,blurB,accum,delayed;
    private static int lastW=-1,lastH=-1,frames,depthTexture=-1;
    private static boolean snapshotValid,depthScreenshot;
    private static java.nio.FloatBuffer rawDepth;
    private static java.nio.ByteBuffer depthColors;
    private CameraPostProcessor() {}
    public static void registerShaders(RegisterShadersEvent event) throws java.io.IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(),ResourceLocation.fromNamespaceAndPath(
                RealTrainModUnofficial.MODID,"rtm_dof"),DefaultVertexFormat.POSITION_TEX),s->dofShader=s);
        event.registerShader(new ShaderInstance(event.getResourceProvider(),ResourceLocation.fromNamespaceAndPath(
                RealTrainModUnofficial.MODID,"rtm_accum"),DefaultVertexFormat.POSITION_TEX),s->accumShader=s);
        event.registerShader(new ShaderInstance(event.getResourceProvider(),ResourceLocation.fromNamespaceAndPath(
                RealTrainModUnofficial.MODID,"rtm_blit"),DefaultVertexFormat.POSITION_TEX),s->blitShader=s);
    }
    public static void reset() { frames=0;snapshotValid=false;depthScreenshot=false; }
    public static void requestDepthScreenshot() { depthScreenshot=true; }
    public static void process(Minecraft mc,CameraState state) {
        if(ShaderCompat.isShaderPackInUse()) return;
        var path=mc.gameDirectory.toPath().resolve("mods/ss_depth.png");boolean requested=depthScreenshot;
        processTarget(mc.getMainRenderTarget(),state,mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight(),path);
        if(requested && !depthScreenshot && mc.player!=null) mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal("save:"+path),false);
    }
    static void processTarget(RenderTarget main,CameraState state,int guiWidth,int guiHeight,java.nio.file.Path screenshotPath) {
        if(dofShader==null || accumShader==null || blitShader==null) return;
        if(state.sensitivity()>=1 && state.focusMode()==0) return;
        int w=main.width,h=main.height;
        if(w<=0 || h<=0) return;
        ensureTargets(w,h);
        Matrix4f savedProjection=new Matrix4f(RenderSystem.getProjectionMatrix());
        var savedSort=RenderSystem.getVertexSorting();var mv=RenderSystem.getModelViewStack();mv.pushMatrix();mv.identity();
        RenderSystem.applyModelViewMatrix();RenderSystem.setProjectionMatrix(new Matrix4f(),com.mojang.blaze3d.vertex.VertexSorting.ORTHOGRAPHIC_Z);
        RenderSystem.disableDepthTest();RenderSystem.depthMask(false);RenderSystem.disableBlend();
        try {
            int scene=main.getColorTextureId(),result=scene,base=scene;
            if(state.sensitivity()<1) {
                if(frames>=10) { accum.bindWrite(false);blitPass(scene);snapshotValid=true;frames=0; }
                frames++;
                if(snapshotValid) { delayed.bindWrite(false);accumPass(scene,accum.getColorTextureId(),state.delayBlend());base=delayed.getColorTextureId();result=base; }
            }
            if(state.focusMode()>0) {
                readDepth(main,state,w,h,screenshotPath);
                blurA.setClearColor(0,0,0,0);blurB.setClearColor(0,0,0,0);
                blurA.clear(Minecraft.ON_OSX);blurB.clear(Minecraft.ON_OSX);
                RenderSystem.enableBlend();RenderSystem.blendFunc(770,771);
                for(int band=8;band>0;band--) {
                    float threshold=band/8F;
                    float[] weights=CameraState.gaussian(CameraState.blurRange(threshold,state.focus(),w));
                    blurA.bindWrite(false);dofPass(scene,0,threshold,weights,guiWidth,guiHeight);
                    blurB.bindWrite(false);dofPass(blurA.getColorTextureId(),1,threshold,weights,guiWidth,guiHeight);
                }
                result=blurB.getColorTextureId();
            }
            main.bindWrite(false);
            RenderSystem.disableBlend();
            if(base!=scene) blitPass(base);
            if(result!=base) { RenderSystem.enableBlend();RenderSystem.blendFunc(770,771);blitPass(result); }
        } catch(Throwable failure) {
            LOGGER.warn("Original camera post-processing failed",failure);
            reset();
        } finally {
            RenderSystem.disableBlend();RenderSystem.depthMask(true);RenderSystem.enableDepthTest();
            RenderSystem.setProjectionMatrix(savedProjection,savedSort);mv.popMatrix();RenderSystem.applyModelViewMatrix();main.bindWrite(true);
        }
    }
    private static void readDepth(RenderTarget main,CameraState state,int w,int h,java.nio.file.Path screenshotPath) throws java.io.IOException {
        int previous=org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER_BINDING);
        org.lwjgl.opengl.GL30.glBindFramebuffer(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER,main.frameBufferId);
        rawDepth.clear();
        try { org.lwjgl.opengl.GL11.glReadPixels(0,0,w,h,org.lwjgl.opengl.GL11.GL_DEPTH_COMPONENT,org.lwjgl.opengl.GL11.GL_FLOAT,rawDepth); }
        finally { org.lwjgl.opengl.GL30.glBindFramebuffer(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER,previous); }
        depthColors.clear();
        for(int i=0;i<w*h;i++) depthColors.put((byte)CameraState.depthLevel(rawDepth.get(i)));
        depthColors.flip();state.autoFocus((depthColors.get((h/2)*w+w/2)&255)/255F);
        RenderSystem.bindTexture(depthTexture);
        int alignment=org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_UNPACK_ALIGNMENT);
        org.lwjgl.opengl.GL11.glPixelStorei(org.lwjgl.opengl.GL11.GL_UNPACK_ALIGNMENT,1);
        org.lwjgl.opengl.GL11.glTexSubImage2D(org.lwjgl.opengl.GL11.GL_TEXTURE_2D,0,0,0,w,h,org.lwjgl.opengl.GL11.GL_RED,org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE,depthColors);
        org.lwjgl.opengl.GL11.glPixelStorei(org.lwjgl.opengl.GL11.GL_UNPACK_ALIGNMENT,alignment);
        if(depthScreenshot) {
            depthScreenshot=false;
            java.awt.image.BufferedImage image=new java.awt.image.BufferedImage(w,h,java.awt.image.BufferedImage.TYPE_INT_RGB);
            for(int y=0;y<h;y++) for(int x=0;x<w;x++) { int c=depthColors.get(y*w+x)&255;image.setRGB(x,y,(c<<16)|(c<<8)|c); }
            if(screenshotPath!=null) {
                java.nio.file.Files.createDirectories(screenshotPath.getParent());
                javax.imageio.ImageIO.write(image,"png",screenshotPath.toFile());
            }
        }
    }
    private static void dofPass(int color,int pass,float threshold,float[] weights,int guiWidth,int guiHeight) {
        RenderSystem.setShader(()->dofShader);RenderSystem.setShaderTexture(0,color);RenderSystem.setShaderTexture(1,depthTexture);
        dofShader.safeGetUniform("InSize").set((float)guiWidth,(float)guiHeight);
        dofShader.safeGetUniform("Pass").set(pass);dofShader.safeGetUniform("Threshold").set(threshold);
        dofShader.safeGetUniform("Weights").set(weights);drawFullscreenQuad();
    }
    private static void accumPass(int current,int previous,float blend) {
        RenderSystem.disableBlend();RenderSystem.setShader(()->accumShader);RenderSystem.setShaderTexture(0,current);RenderSystem.setShaderTexture(1,previous);
        accumShader.safeGetUniform("Blend").set(blend);drawFullscreenQuad();
    }
    private static void blitPass(int texture) { RenderSystem.setShader(()->blitShader);RenderSystem.setShaderTexture(0,texture);drawFullscreenQuad(); }
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
            // These shaders consume clip-space positions and explicit uniforms. Avoid
            // the world renderer's window-dependent default uniform setup.
            var shader = RenderSystem.getShader();
            if (shader.MODEL_VIEW_MATRIX != null) shader.MODEL_VIEW_MATRIX.set(new org.joml.Matrix4f());
            if (shader.PROJECTION_MATRIX != null) shader.PROJECTION_MATRIX.set(new org.joml.Matrix4f());
            for (int i = 0; i < 2; i++) shader.setSampler("Sampler" + i, RenderSystem.getShaderTexture(i));
            try (var vertices = new com.mojang.blaze3d.vertex.VertexBuffer(com.mojang.blaze3d.vertex.VertexBuffer.Usage.DYNAMIC)) {
                vertices.bind(); vertices.upload(mesh);
                shader.apply(); vertices.draw(); shader.clear();
            } finally { com.mojang.blaze3d.vertex.VertexBuffer.unbind(); }

        }
    }

    private static void ensureTargets(int w,int h) {
        if(blurA!=null && lastW==w && lastH==h) return;
        for(RenderTarget target:new RenderTarget[]{blurA,blurB,accum,delayed}) if(target!=null) target.destroyBuffers();
        if(depthTexture>=0) com.mojang.blaze3d.platform.TextureUtil.releaseTextureId(depthTexture);
        blurA=new TextureTarget(w,h,false,Minecraft.ON_OSX);blurB=new TextureTarget(w,h,false,Minecraft.ON_OSX);accum=new TextureTarget(w,h,false,Minecraft.ON_OSX);delayed=new TextureTarget(w,h,false,Minecraft.ON_OSX);
        for(RenderTarget target:new RenderTarget[]{blurA,blurB,accum,delayed}) target.setFilterMode(org.lwjgl.opengl.GL11.GL_NEAREST);
        rawDepth=org.lwjgl.BufferUtils.createFloatBuffer(w*h);depthColors=org.lwjgl.BufferUtils.createByteBuffer(w*h);
        depthTexture=com.mojang.blaze3d.platform.TextureUtil.generateTextureId();RenderSystem.bindTexture(depthTexture);
        org.lwjgl.opengl.GL11.glTexImage2D(org.lwjgl.opengl.GL11.GL_TEXTURE_2D,0,org.lwjgl.opengl.GL30.GL_R8,w,h,0,org.lwjgl.opengl.GL11.GL_RED,org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE,(java.nio.ByteBuffer)null);
        org.lwjgl.opengl.GL11.glTexParameteri(org.lwjgl.opengl.GL11.GL_TEXTURE_2D,org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER,org.lwjgl.opengl.GL11.GL_NEAREST);
        org.lwjgl.opengl.GL11.glTexParameteri(org.lwjgl.opengl.GL11.GL_TEXTURE_2D,org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER,org.lwjgl.opengl.GL11.GL_NEAREST);
        for (int texture : new int[]{blurA.getColorTextureId(),blurB.getColorTextureId(),accum.getColorTextureId(),delayed.getColorTextureId(),depthTexture}) {
            RenderSystem.bindTexture(texture);
            org.lwjgl.opengl.GL11.glTexParameteri(org.lwjgl.opengl.GL11.GL_TEXTURE_2D,org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_S,org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE);
            org.lwjgl.opengl.GL11.glTexParameteri(org.lwjgl.opengl.GL11.GL_TEXTURE_2D,org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_T,org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE);
        }
        lastW=w;lastH=h;boolean pending=depthScreenshot;reset();depthScreenshot=pending;
    }
}
