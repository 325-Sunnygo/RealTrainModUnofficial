package com.portofino.realtrainmodunofficial.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import jp.ngt.ngtlib.renderer.LegacyRenderState;

/** Bake legacy UV offsets into vertices when Iris owns the vertex shader. */
public final class LegacyShaderAttributes {
    private LegacyShaderAttributes() {}
    public static VertexConsumer wrap(VertexConsumer source) {
        var state=LegacyRenderState.playback();
        if(!com.portofino.realtrainmodunofficial.client.ShaderCompat.active()||state==null||state.textureU()==0&&state.textureV()==0)return source;
        float offsetU=state.textureU(),offsetV=state.textureV();
        return new VertexConsumer() {
            public VertexConsumer addVertex(float x,float y,float z){source.addVertex(x,y,z);return this;}
            public VertexConsumer setColor(int r,int g,int b,int a){source.setColor(r,g,b,a);return this;}
            public VertexConsumer setUv(float u,float v){source.setUv(u+offsetU,v+offsetV);return this;}
            public VertexConsumer setUv1(int u,int v){source.setUv1(u,v);return this;}
            public VertexConsumer setUv2(int u,int v){source.setUv2(u,v);return this;}
            public VertexConsumer setNormal(float x,float y,float z){source.setNormal(x,y,z);return this;}
        };
    }
}
