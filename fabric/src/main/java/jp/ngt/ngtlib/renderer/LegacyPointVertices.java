package jp.ngt.ngtlib.renderer;

import java.nio.ByteBuffer;

/** Convert the four repeated center vertices required by BufferBuilder into native points. */
public final class LegacyPointVertices {
    private LegacyPointVertices() {}
    public static void compact(ByteBuffer source,ByteBuffer destination,int stride,int points) {
        if(stride<=0 || points<0) throw new IllegalArgumentException("Invalid point layout");
        int length=Math.multiplyExact(points,stride);
        if(source.remaining()<Math.multiplyExact(length,4) || destination.remaining()<length)
            throw new IllegalArgumentException("Incomplete point vertex buffer");
        int start=source.position();
        for(int point=0;point<points;point++) {
            ByteBuffer vertex=source.duplicate();
            vertex.position(start+point*4*stride).limit(start+point*4*stride+stride);
            destination.put(vertex);
        }
    }
}
