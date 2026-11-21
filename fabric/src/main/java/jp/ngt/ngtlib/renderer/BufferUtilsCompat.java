package jp.ngt.ngtlib.renderer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * LWJGL2 の org.lwjgl.BufferUtils 互換。
 * スクリプトが BufferUtils.createFloatBuffer(16) で行列バッファを作り
 * GL11.glMultMatrix(buf) へ渡す (NGTO Builder 2)。
 */
public final class BufferUtilsCompat {

    private BufferUtilsCompat() {
    }

    public static FloatBuffer createFloatBuffer(int size) {
        return ByteBuffer.allocateDirect(Math.max(0, size) * Float.BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer();
    }

    public static ByteBuffer createByteBuffer(int size) {
        return ByteBuffer.allocateDirect(Math.max(0, size)).order(ByteOrder.nativeOrder());
    }
}
