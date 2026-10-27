package com.portofino.realtrainmodunofficial.client.render;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL11;

/** Exercises actual RenderType state setup and BufferUploader, without starting Minecraft. */
public final class LegacyProductionBlendRegression {
    private static Object allocate(Class<?> type) throws Exception {
        var f=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);
        return ((sun.misc.Unsafe)f.get(null)).allocateInstance(type);
    }
    private static void set(Class<?> type,Object target,String name,Object value) throws Exception {
        var f=type.getDeclaredField(name);f.setAccessible(true);f.set(target,value);
    }
    public static void run(ShaderInstance shader) throws Exception {
        var mc=(Minecraft)allocate(Minecraft.class);
        var manager=new TextureManager(null);
        var renderer=(GameRenderer)allocate(GameRenderer.class);
        set(Minecraft.class,mc,"textureManager",manager);
        set(Minecraft.class,mc,"gameRenderer",renderer);
        set(Minecraft.class,null,"instance",mc);
        var light=new LightTexture(renderer,mc);
        var overlay=new OverlayTexture();
        set(GameRenderer.class,renderer,"lightTexture",light);
        set(GameRenderer.class,renderer,"overlayTexture",overlay);
        set(LegacyRenderTypes.class,null,"shader",shader);
        TextureTarget target=new TextureTarget(16,16,true,Minecraft.ON_OSX);
        var loc=ResourceLocation.fromNamespaceAndPath("regression","alpha");
        RenderSystem.getModelViewStack().identity();RenderSystem.applyModelViewMatrix();
        RenderSystem.setProjectionMatrix(new org.joml.Matrix4f(),VertexSorting.ORTHOGRAPHIC_Z);
        RenderSystem.setShaderColor(1,1,1,1);
        RenderSystem.setShaderLights(new org.joml.Vector3f(0,0,1),new org.joml.Vector3f(0,0,1));
        var state=new jp.ngt.ngtlib.renderer.LegacyRenderState();state.enable(0x0B50,false);state.enable(0x0B60,false);
        int checks=0;
        try(var ignored=jp.ngt.ngtlib.renderer.LegacyRenderState.use(state.snapshot())) {
            var shaderActive=com.portofino.realtrainmodunofficial.client.ShaderCompat.class.getDeclaredField("cachedActive");shaderActive.setAccessible(true);
            for(boolean shaderPack:new boolean[]{false,true}) {
            shaderActive.setBoolean(null,shaderPack);
            if(shaderPack) {
                var posed=new PoseStack();posed.translate(3,4,5);
                LocalSpaceBatch.run(posed,null,(actual,source)->{if(actual!=posed||!actual.last().pose().equals(posed.last().pose()))throw new AssertionError("Shader path erased entity transforms");return true;});
            }
            for(int vertexAlpha:new int[]{255,128}) {
            for(int alpha:new int[]{0,14,44,75,106,138,168,200,231,255}) {
                var image=new NativeImage(1,1,false);image.setPixelRGBA(0,0,alpha<<24);
                IrisTexturePasses.forget(loc);LegacyRenderTypes.forgetTexture(loc);
                var texture=new DynamicTexture(image);manager.register(loc,texture);
                for(int pass=0;pass<2;pass++) {
                    target.setClearColor(0,1,0,1);target.clear(Minecraft.ON_OSX);target.bindWrite(true);
                    var type=LegacyRenderTypes.vehicle(RenderType.entityTranslucent(loc),loc,pass==1,true,true,VertexFormat.Mode.TRIANGLES,vertexAlpha/255F);
                    var builder=Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES,DefaultVertexFormat.NEW_ENTITY);
                    for(float[] v:new float[][]{{-1,-1},{3,-1},{-1,3}}) builder.addVertex(v[0],v[1],0).setColor(255,255,255,vertexAlpha).setUv(0.5F,0.5F).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0,0,1);
                    type.draw(builder.buildOrThrow());
                    var pixel=org.lwjgl.BufferUtils.createByteBuffer(4);GL11.glReadPixels(8,8,1,1,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixel);
                    int actual=pixel.get(1)&255;
                    int expected=pass==0?(alpha==255&&vertexAlpha==255?0:255):(alpha==255&&vertexAlpha==255?255:255-Math.round(alpha*vertexAlpha/255F));
                    if(Math.abs(actual-expected)>1) throw new AssertionError("Production blend alpha="+alpha+" pass="+pass+" expected green="+expected+" actual="+actual+" blend="+GL11.glIsEnabled(GL11.GL_BLEND));
                    var depth=org.lwjgl.BufferUtils.createFloatBuffer(1);
                    GL11.glReadPixels(8,8,1,1,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,depth);
                    float expectedDepth=pass==0 && alpha==255&&vertexAlpha==255?0.5F:1F;
                    if(Math.abs(depth.get(0)-expectedDepth)>0.00001F) throw new AssertionError("Forge pass inherited depth mask: alpha="+alpha+" pass="+pass+" expected depth="+expectedDepth+" actual="+depth.get(0));
                    checks+=2;
                }
            }
            }
            }
            shaderActive.setBoolean(null,true);
            if (LegacyRenderTypes.shaderPackProgram(false,true,false) != LegacyRenderTypes.shaderPackProgram(false,true,true)
                    || LegacyRenderTypes.shaderPackProgram(false,true,false) == LegacyRenderTypes.shaderPackProgram(true,false,true))
                throw new AssertionError("Lighting-disabled legacy geometry must use the ordinary cutout entity program, not Iris eyes");
            var transformed=new PoseStack();transformed.translate(3,4,5);
            var view=new org.joml.Matrix4f(RenderSystem.getModelViewMatrix());
            LocalSpaceBatch.run(transformed,null,(actual,source)->{if(actual!=transformed)throw new AssertionError("Iris entity pose was moved into the global camera matrix");return true;});
            if(!RenderSystem.getModelViewMatrix().equals(view))throw new AssertionError("Iris fallback changed the camera matrix");
            var mapField=LightTexture.class.getDeclaredField("lightTexture");mapField.setAccessible(true);var map=(DynamicTexture)mapField.get(light);
            map.getPixels().fillRect(0,0,map.getPixels().getWidth(),map.getPixels().getHeight(),-1);map.upload();
            IrisTexturePasses.forget(loc);LegacyRenderTypes.forgetTexture(loc);var white=new NativeImage(1,1,false);white.setPixelRGBA(0,0,-1);manager.register(loc,new DynamicTexture(white));
            var smooth=new jp.ngt.ngtlib.renderer.LegacyRenderState();smooth.enable(0x0B50,true);smooth.enable(0x0B60,false);
            try(var smoothState=jp.ngt.ngtlib.renderer.LegacyRenderState.use(smooth.snapshot())) {
                target.setClearColor(0,0,0,1);target.clear(Minecraft.ON_OSX);target.bindWrite(true);
                var geometry=com.portofino.realtrainmodunofficial.client.model.LegacyIrisGeometry.class;
                if (!com.portofino.realtrainmodunofficial.client.model.LegacyIrisGeometry.useTriangles(true,true)
                    || com.portofino.realtrainmodunofficial.client.model.LegacyIrisGeometry.useTriangles(true,false))
                    throw new AssertionError("Smooth and flat shader primitives were not selected separately");
                var type=LegacyRenderTypes.vehicle(RenderType.entityCutout(loc),loc,false,false,true,VertexFormat.Mode.TRIANGLES);
                var builder=Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES,DefaultVertexFormat.NEW_ENTITY);
                float[][] vertices={{-1,-1,1,0,0},{1,-1,0,0,1},{1,1,0,0,1},{-1,1,1,0,0}};
                for(int index:new int[]{0,1,2,0,2,3}) {float[] v=vertices[index];builder.addVertex(v[0],v[1],0).setColor(255,255,255,255).setUv(.5F,.5F).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(v[2],v[3],v[4]);}
                type.draw(builder.buildOrThrow());var left=org.lwjgl.BufferUtils.createByteBuffer(4);var right=org.lwjgl.BufferUtils.createByteBuffer(4);
                GL11.glReadPixels(2,8,1,1,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,left);GL11.glReadPixels(13,8,1,1,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,right);
                if((right.get(0)&255)-(left.get(0)&255)<30)throw new AssertionError("Shader fallback triangle lost per-corner smooth normals: "+(left.get(0)&255)+" / "+(right.get(0)&255));
            }
            shaderActive.setBoolean(null,false);
            System.out.println("Shader fallback smooth triangles / ordinary unlit entity / camera isolation: 5 passed");
            System.out.println("Actual normal/Iris-fallback RenderType/BufferUploader alpha and depth: "+checks+" passed");
            LegacyE259ScriptRegression.run();
        } finally {target.destroyBuffers();light.close();overlay.close();set(Minecraft.class,null,"instance",null);}
    }
}
