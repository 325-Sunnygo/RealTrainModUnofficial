package jp.ngt.ngtlib.renderer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * LWJGL2 の {@code org.lwjgl.BufferUtils} 互換。
 *
 * <p>スクリプトが {@code BufferUtils.createFloatBuffer(16)} で行列バッファを作り
 * {@code GL11.glMultMatrix(buf)} へ渡す (NGTO Builder 2)。LWJGL3 にも同名クラスはあるが、
 * プレリュードで束縛していないと未定義で落ちるのでここで用意する。
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
