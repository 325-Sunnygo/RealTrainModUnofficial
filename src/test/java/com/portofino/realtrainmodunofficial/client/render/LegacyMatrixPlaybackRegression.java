package com.portofino.realtrainmodunofficial.client.render;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.MultiBufferSource;
public final class LegacyMatrixPlaybackRegression {
    public static void run() {
        var projection=new Matrix4f().perspective(1F,1.5F,.1F,200F);
        RenderSystem.setProjectionMatrix(projection,VertexSorting.DISTANCE_TO_ORIGIN);
        var inherited=new Matrix4f().translation(2,3,4);
        RenderSystem.getModelViewStack().set(inherited);RenderSystem.applyModelViewMatrix();
        var pose=new PoseStack();pose.translate(10,20,30);var base=new Matrix4f(pose.last().pose());
        var storage=new ByteBufferBuilder(4096);var buffer=MultiBufferSource.immediate(storage);
        try {
            try(var playback=new LegacyMatrixPlayback(pose,buffer)) {
                playback.projection(new Matrix4f().setOrtho(0,320,240,0,1000,3000).get(new float[16]));
                playback.load(new Matrix4f().translation(0,0,-1001).get(new float[16]));
                var clip=new Vector4f(160,120,0,1).mul(pose.last().pose()).mul(RenderSystem.getProjectionMatrix());
                if(Math.abs(clip.x)>1e-5||Math.abs(clip.y)>1e-5||clip.z < -1||clip.z>1)throw new AssertionError("HUD must use screen coordinates");
                if(!RenderSystem.getModelViewMatrix().equals(new Matrix4f()))throw new AssertionError("World view leaked into HUD");
                playback.load(new Matrix4f().get(new float[16]));playback.projection(new Matrix4f().get(new float[16]));
                if(!pose.last().pose().equals(base)||!RenderSystem.getProjectionMatrix().equals(projection)||!RenderSystem.getModelViewMatrix().equals(inherited))throw new AssertionError("World matrices were not restored");
                playback.projection(new Matrix4f().setOrtho(0,320,240,0,1000,3000).get(new float[16]));
            }
            if(!RenderSystem.getProjectionMatrix().equals(projection)||!RenderSystem.getModelViewMatrix().equals(inherited))throw new AssertionError("Exceptional HUD exit must restore GL matrices");
            if(org.lwjgl.opengl.GL11.glGetError()!=0)throw new AssertionError("Core-profile GL error");
            System.out.println("Actual core-profile GUI/world matrix transitions: 5 passed");
        }finally {storage.close();}
    }
}
