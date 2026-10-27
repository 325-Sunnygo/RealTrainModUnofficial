package com.portofino.realtrainmodunofficial.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;
import java.util.Random;

/** Shared-edge coordinates must be bit-identical across normal and light submissions. */
public final class LegacyVertexTransformRegression {
    private static final class Capture implements VertexConsumer {
        float x,y,z;
        public VertexConsumer addVertex(float a,float b,float c) { x=a;y=b;z=c;return this; }
        public VertexConsumer setColor(int r,int g,int b,int a) { return this; }
        public VertexConsumer setUv(float u,float v) { return this; }
        public VertexConsumer setUv1(int u,int v) { return this; }
        public VertexConsumer setUv2(int u,int v) { return this; }
        public VertexConsumer setNormal(float x,float y,float z) { return this; }
    }
    public static void main(String[] args) {
        Capture expected=new Capture(),actual=new Capture();Random random=new Random(7101211);
        Matrix4f[] matrices={new Matrix4f(),new Matrix4f().translation(123.456F,-61.0625F,987.654F),
                new Matrix4f().translation(-10000.125F,64.0625F,3000.75F).rotateXYZ(0.127F,1.32F,-0.029F),
                new Matrix4f().translation(0.001F,1,0.0625F).rotateY(3.14159F).scale(-1.0001F,0.9999F,0.01F)};
        int count=0;
        for(Matrix4f matrix:matrices) for(int sample=0;sample<1024;sample++) {
            float x=random.nextFloat()*4-2,y=random.nextFloat()*5-1,z=random.nextFloat()*22-11;
            expected.addVertex(matrix,x,y,z);VertexWriter.addVertex(actual,matrix,x,y,z);
            if(Float.floatToRawIntBits(expected.x)!=Float.floatToRawIntBits(actual.x)
                    || Float.floatToRawIntBits(expected.y)!=Float.floatToRawIntBits(actual.y)
                    || Float.floatToRawIntBits(expected.z)!=Float.floatToRawIntBits(actual.z))
                throw new AssertionError("Shared edge transform rounded differently");
            count++;
        }
        System.out.println("Legacy vertex transform regressions: "+count+" bit-identical positions");
    }
}
