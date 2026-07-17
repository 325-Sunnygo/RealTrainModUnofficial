package com.myname.legacyloader.bridge.client.renderer;

import java.nio.Buffer;
import java.util.ArrayDeque;
import java.util.Deque;
import org.joml.Matrix4d;
import org.joml.Vector4d;

public final class LegacyGL11 {
    public static final int GL_QUADS = 7;
    public static final int GL_TEXTURE_2D = 3553;
    public static final int GL_BLEND = 3042;
    public static final int GL_ALPHA_TEST = 3008;
    public static final int GL_CULL_FACE = 2884;
    public static final int GL_LIGHTING = 2896;
    public static final int GL_SRC_ALPHA = 770;
    public static final int GL_ONE_MINUS_SRC_ALPHA = 771;
    private static final ThreadLocal<Deque<Matrix4d>> MATRIX_STACK =
            ThreadLocal.withInitial(() -> {
                Deque<Matrix4d> stack = new ArrayDeque<>();
                stack.push(new Matrix4d());
                return stack;
            });

    private LegacyGL11() {
    }

    public static void glPushMatrix() {
        Deque<Matrix4d> stack = MATRIX_STACK.get();
        stack.push(new Matrix4d(stack.peek()));
    }

    public static void glPopMatrix() {
        Deque<Matrix4d> stack = MATRIX_STACK.get();
        if (stack.size() > 1) stack.pop();
    }

    public static void glTranslatef(float x, float y, float z) { glTranslated(x, y, z); }
    public static void glTranslated(double x, double y, double z) { MATRIX_STACK.get().peek().translate(x, y, z); }
    public static void glRotatef(float angle, float x, float y, float z) { glRotated(angle, x, y, z); }
    public static void glRotated(double angle, double x, double y, double z) {
        double length = Math.sqrt(x * x + y * y + z * z);
        if (length <= 1.0E-8D) return;
        MATRIX_STACK.get().peek().rotate(Math.toRadians(angle), x / length, y / length, z / length);
    }
    public static void glScalef(float x, float y, float z) { glScaled(x, y, z); }
    public static void glScaled(double x, double y, double z) { MATRIX_STACK.get().peek().scale(x, y, z); }
    public static void glColor3f(float r, float g, float b) { LegacyTessellator.setCurrentColor(r, g, b); }
    public static void glColor4f(float r, float g, float b, float a) { LegacyTessellator.setCurrentColor(r, g, b); }
    public static void glColor3ub(byte r, byte g, byte b) {
        LegacyTessellator.setCurrentColor((r & 255) / 255.0F, (g & 255) / 255.0F, (b & 255) / 255.0F);
    }
    public static void glColor4ub(byte r, byte g, byte b, byte a) { glColor3ub(r, g, b); }
    public static void glPushAttrib(int mask) {}
    public static void glPopAttrib() {}
    public static void glEnable(int target) {}
    public static void glDisable(int target) {}
    public static void glBlendFunc(int src, int dst) {}
    public static void glBindTexture(int target, int texture) {}
    public static void glTexCoordPointer(int size, int stride, Buffer buffer) {}
    public static void glTexCoordPointer(int size, int type, int stride, Buffer buffer) {}
    public static void glColorPointer(int size, boolean unsigned, int stride, Buffer buffer) {}
    public static void glColorPointer(int size, int type, int stride, Buffer buffer) {}
    public static void glNormalPointer(int stride, Buffer buffer) {}
    public static void glNormalPointer(int type, int stride, Buffer buffer) {}
    public static void glVertexPointer(int size, int stride, Buffer buffer) {}
    public static void glVertexPointer(int size, int type, int stride, Buffer buffer) {}
    public static void glEnableClientState(int cap) {}
    public static void glDisableClientState(int cap) {}
    public static void glDrawArrays(int mode, int first, int count) {}

    static double[] transform(double x, double y, double z) {
        Vector4d vec = MATRIX_STACK.get().peek().transform(new Vector4d(x, y, z, 1.0D));
        return new double[]{vec.x, vec.y, vec.z};
    }

    /** 現在の GL 行列 (TESR 内の glTranslatef/glRotatef 累積)。OBJ 描画時の頂点変換に使う。 */
    public static Matrix4d currentMatrix() {
        return new Matrix4d(MATRIX_STACK.get().peek());
    }

    public static void resetMatrixStack() {
        Deque<Matrix4d> stack = MATRIX_STACK.get();
        stack.clear();
        stack.push(new Matrix4d());
    }
}
