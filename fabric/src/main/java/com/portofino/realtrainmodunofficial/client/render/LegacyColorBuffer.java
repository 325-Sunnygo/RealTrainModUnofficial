package com.portofino.realtrainmodunofficial.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;

/** Bake the legacy current color into vertices before deferred batches are submitted. */
public final class LegacyColorBuffer {
    private LegacyColorBuffer() {}
    public static MultiBufferSource tint(MultiBufferSource source,float red,float green,float blue,float alpha) {
        if(red==1 && green==1 && blue==1 && alpha==1) return source;
        return new Tinted(source,red,green,blue,alpha);
    }
    private record Tinted(MultiBufferSource source,float red,float green,float blue,float alpha) implements MultiBufferSource {
        public VertexConsumer getBuffer(net.minecraft.client.renderer.RenderType type) {return new VertexConsumer() {
            final VertexConsumer delegate=source.getBuffer(type);
            public VertexConsumer addVertex(float x,float y,float z) { delegate.addVertex(x,y,z); return this; }
            public VertexConsumer setColor(int r,int g,int b,int a) {
                delegate.setColor(channel(r,red),channel(g,green),channel(b,blue),channel(a,alpha)); return this;
            }
            public VertexConsumer setUv(float u,float v) { delegate.setUv(u,v); return this; }
            public VertexConsumer setUv1(int u,int v) { delegate.setUv1(u,v); return this; }
            public VertexConsumer setUv2(int u,int v) { delegate.setUv2(u,v); return this; }
            public VertexConsumer setNormal(float x,float y,float z) { delegate.setNormal(x,y,z); return this; }
        };}
    }
    public static int alpha(MultiBufferSource source,int value) {
        return source instanceof Tinted tint?alpha(tint.source,channel(value,tint.alpha)):value;
    }
    public static MultiBufferSource root(MultiBufferSource source) {return source instanceof Tinted tint?root(tint.source):source;}
    private static int channel(int value,float multiplier) { return Math.max(0,Math.min(255,Math.round(value*multiplier))); }
}
