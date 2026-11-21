package com.portofino.realtrainmodunofficial.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import jp.ngt.ngtlib.renderer.LegacyPointDraw;
import jp.ngt.ngtlib.renderer.LegacyPointVertices;
import net.minecraft.client.renderer.RenderType;
import org.lwjgl.system.MemoryUtil;

/** Uses a normal queued RenderType, then submits one native point for each buffered center. */
final class LegacyPointRenderType extends RenderType {
    private final float pointSize;
    private static PointBuffer pointBuffer;
    LegacyPointRenderType(RenderType pipeline,float pointSize) {
        super("rtmu_legacy_points",pipeline.format(),pipeline.mode(),pipeline.bufferSize(),false,false,
                pipeline::setupRenderState,pipeline::clearRenderState);
        this.pointSize=pointSize;
    }
    static void reset() { if(pointBuffer!=null) { pointBuffer.close(); pointBuffer=null; } }
    private static final class PointBuffer extends VertexBuffer {
        int points; float size;
        PointBuffer() { super(Usage.DYNAMIC); }
        @Override public void draw() { LegacyPointDraw.draw(points,size); }
    }
    @Override public void draw(MeshData mesh) {
        RenderSystem.assertOnRenderThread();
        try(mesh) {
            MeshData.DrawState original=mesh.drawState();
            if(original.vertexCount()%4!=0) throw new IllegalArgumentException("Point centers must contain four repeated vertices");
            int points=original.vertexCount()/4;
            if(points==0) return;
            int bytes=Math.multiplyExact(points,original.format().getVertexSize());
            try(ByteBufferBuilder storage=new ByteBufferBuilder(bytes)) {
                LegacyPointVertices.compact(mesh.vertexBuffer(),MemoryUtil.memByteBuffer(storage.reserve(bytes),bytes),original.format().getVertexSize(),points);
                MeshData.DrawState drawState=new MeshData.DrawState(original.format(),points,points,
                        VertexFormat.Mode.DEBUG_LINE_STRIP,VertexFormat.IndexType.least(points));
                // Keep Minecraft's shader defaults, textures and vertex layout; only the final primitive differs.
                BufferUploader.reset();
                if(pointBuffer==null) pointBuffer=new PointBuffer();
                setupRenderState();
                try {
                    pointBuffer.bind();
                    pointBuffer.points=points; pointBuffer.size=pointSize;
                    pointBuffer.upload(new MeshData(storage.build(),drawState));
                    pointBuffer.drawWithShader(RenderSystem.getModelViewMatrix(),RenderSystem.getProjectionMatrix(),RenderSystem.getShader());
                } finally { VertexBuffer.unbind(); BufferUploader.invalidate(); clearRenderState(); }
            }
        }
    }
}
