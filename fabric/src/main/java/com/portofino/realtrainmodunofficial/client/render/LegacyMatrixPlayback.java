package com.portofino.realtrainmodunofficial.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix4f;

/** Flush at projection boundaries so deferred world vertices cannot use the GUI projection. */
final class LegacyMatrixPlayback implements AutoCloseable {
    private final PoseStack pose;
    private final MultiBufferSource buffer;
    private final Matrix4f basePose,projection;
    private final com.mojang.blaze3d.vertex.VertexSorting sorting;
    private boolean screen,maskChanged;
    private boolean[] originalMask;
    LegacyMatrixPlayback(PoseStack pose,MultiBufferSource buffer) {this.pose=pose;this.buffer=buffer;this.basePose=new Matrix4f(pose.last().pose());this.projection=new Matrix4f(RenderSystem.getProjectionMatrix());this.sorting=RenderSystem.getVertexSorting();}
    private void flush() {if(LegacyColorBuffer.root(buffer) instanceof MultiBufferSource.BufferSource source)source.endBatch();}
    void projection(float[] values) {
        flush();Matrix4f matrix=new Matrix4f().set(values);boolean restore=matrix.equals(new Matrix4f());
        if(!screen&&!restore){RenderSystem.getModelViewStack().pushMatrix().identity();RenderSystem.applyModelViewMatrix();}
        if(screen&&restore){pose.last().pose().set(new Matrix4f(basePose).mul(pose.last().pose()));pose.last().normal().set(pose.last().pose()).invert().transpose();RenderSystem.getModelViewStack().popMatrix();RenderSystem.applyModelViewMatrix();}
        screen=!restore;RenderSystem.setProjectionMatrix(restore?projection:matrix,restore?sorting:com.mojang.blaze3d.vertex.VertexSorting.ORTHOGRAPHIC_Z);
    }
    void colorMask(boolean r,boolean g,boolean b,boolean a) {
        flush();if(!maskChanged){var values=org.lwjgl.BufferUtils.createByteBuffer(4);org.lwjgl.opengl.GL11.glGetBooleanv(3107,values);originalMask=new boolean[]{values.get(0)!=0,values.get(1)!=0,values.get(2)!=0,values.get(3)!=0};maskChanged=true;}
        RenderSystem.colorMask(r,g,b,a);
    }
    void load(float[] values) {pose.last().pose().set(screen?new Matrix4f():basePose).mul(new Matrix4f().set(values));pose.last().normal().set(pose.last().pose()).invert().transpose();}
    @Override public void close() {if(maskChanged){flush();RenderSystem.colorMask(originalMask[0],originalMask[1],originalMask[2],originalMask[3]);}if(screen){flush();pose.last().pose().set(basePose);pose.last().normal().set(basePose).invert().transpose();RenderSystem.getModelViewStack().popMatrix();RenderSystem.applyModelViewMatrix();RenderSystem.setProjectionMatrix(projection,sorting);screen=false;}}
}
