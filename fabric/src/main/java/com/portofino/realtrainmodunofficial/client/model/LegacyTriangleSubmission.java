package com.portofino.realtrainmodunofficial.client.model;

/** Reconstructs the original triangle stream from the internal four-corner storage. */
public final class LegacyTriangleSubmission {
    private LegacyTriangleSubmission() {}
    public static int[] indices(float[] data) {return indices(data,new java.util.BitSet());}
    public static int[] indices(float[] data,java.util.BitSet originalQuads) {
        if(data.length%32!=0) throw new IllegalArgumentException("Incomplete stored face");
        int count=0;
        for(int q=0;q<data.length/8;q+=4) count+=!originalQuads.get(q/4)&&same(data,q+2,q+3)?3:6;
        int[] out=new int[count];int at=0;
        for(int q=0;q<data.length/8;q+=4) {
            out[at++]=q;out[at++]=q+1;out[at++]=q+2;
            if(originalQuads.get(q/4)||!same(data,q+2,q+3)) { out[at++]=q;out[at++]=q+2;out[at++]=q+3; }
        }
        return out;
    }
    private static boolean same(float[] data,int a,int b) {
        for(int i=0;i<8;i++) if(data[a*8+i]!=data[b*8+i]) return false;
        return true;
    }
}
