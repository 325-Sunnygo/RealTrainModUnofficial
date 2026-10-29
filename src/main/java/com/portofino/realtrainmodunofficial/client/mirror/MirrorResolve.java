package com.portofino.realtrainmodunofficial.client.mirror;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;

/** Resolve reflected geometry over the legacy sky without copying the main camera's image. */
public final class MirrorResolve {
    private static ShaderInstance shader;
    private MirrorResolve(){}
    public static void register(net.neoforged.neoforge.client.event.RegisterShadersEvent event) throws java.io.IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(),ResourceLocation.fromNamespaceAndPath("realtrainmodunofficial","mirror_resolve"),DefaultVertexFormat.POSITION_TEX),s->shader=s);
    }
    public static void draw(RenderTarget sky,RenderTarget world,int depth,RenderTarget destination) {
        if(shader==null)throw new IllegalStateException("Mirror resolve shader is not loaded");
        boolean blend=org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_BLEND),cull=org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_CULL_FACE),test=org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_DEPTH_TEST);
        boolean mask=org.lwjgl.opengl.GL11.glGetBoolean(org.lwjgl.opengl.GL11.GL_DEPTH_WRITEMASK);
        try {
            destination.bindWrite(true);RenderSystem.disableBlend();RenderSystem.disableCull();RenderSystem.disableDepthTest();RenderSystem.depthMask(false);
            RenderSystem.setShader(()->shader);RenderSystem.setShaderTexture(0,sky.getColorTextureId());RenderSystem.setShaderTexture(1,world.getColorTextureId());RenderSystem.setShaderTexture(2,depth);
            var vertices=Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX);
            vertices.addVertex(-1,-1,0).setUv(0,0);vertices.addVertex(1,-1,0).setUv(1,0);vertices.addVertex(1,1,0).setUv(1,1);vertices.addVertex(-1,1,0).setUv(0,1);
            BufferUploader.drawWithShader(vertices.buildOrThrow());
        }finally{if(blend)RenderSystem.enableBlend();if(cull)RenderSystem.enableCull();if(test)RenderSystem.enableDepthTest();RenderSystem.depthMask(mask);}
    }
}
