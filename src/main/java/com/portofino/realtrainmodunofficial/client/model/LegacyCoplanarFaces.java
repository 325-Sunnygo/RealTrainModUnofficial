package com.portofino.realtrainmodunofficial.client.model;

import java.util.*;

/** Reject only repeated, identically shaded/UV-mapped triangles of the same material batch. */
public final class LegacyCoplanarFaces {
    private LegacyCoplanarFaces() {}
    private record Vertex(int x,int y,int z,int nx,int ny,int nz,int u,int v) {}
    private record Triangle(Vertex a,Vertex b,Vertex c) {
        @Override public int hashCode() { return a.hashCode()+b.hashCode()+c.hashCode(); }
        @Override public boolean equals(Object value) {
            if(!(value instanceof Triangle t)) return false;
            return a.equals(t.a)&&b.equals(t.b)&&c.equals(t.c)
                    || a.equals(t.b)&&b.equals(t.c)&&c.equals(t.a)
                    || a.equals(t.c)&&b.equals(t.a)&&c.equals(t.b);
        }
    }
    private static int bits(float v) { return Float.floatToIntBits(v==0?0:v); }
    private static Vertex vertex(float[] d,int i) {
        int o=i*8;return new Vertex(bits(d[o]),bits(d[o+1]),bits(d[o+2]),bits(d[o+3]),bits(d[o+4]),bits(d[o+5]),bits(d[o+6]),bits(d[o+7]));
    }
    public static int[] unique(float[] data,int[] triangles) {
        Set<Triangle> seen=new HashSet<>();int[] out=new int[triangles.length];int count=0;
        for(int i=0;i<triangles.length;i+=3) {
            int a=triangles[i],b=triangles[i+1],c=triangles[i+2];
            if(seen.add(new Triangle(vertex(data,a),vertex(data,b),vertex(data,c)))) {
                out[count++]=a;out[count++]=b;out[count++]=c;
            }
        }
        return count==triangles.length?triangles:Arrays.copyOf(out,count);
    }
}
