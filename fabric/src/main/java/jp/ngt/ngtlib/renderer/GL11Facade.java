package jp.ngt.ngtlib.renderer;

/**
 * パックスクリプト向け GL11 互換ファサード。
 * ScriptUtil のプリリュードで `var GL11 = Java.type("jp.ngt.ngtlib.renderer.GL11Facade")`
 * とバインドされ、mozilla_compat の importPackage(org.lwjgl.opengl) より優先される。
 */
@SuppressWarnings("unused")
public final class GL11Facade {
    // よく使われる定数 (値は本物の GL11 と同一)
    public static final int GL_LIGHTING = 0x0B50;
    public static final int GL_BLEND = 0x0BE2;
    public static final int GL_TEXTURE_2D = 0x0DE1;
    public static final int GL_CULL_FACE = 0x0B44;
    public static final int GL_DEPTH_TEST = 0x0B71;
    public static final int GL_SRC_ALPHA = 0x0302;
    public static final int GL_ONE_MINUS_SRC_ALPHA = 0x0303;
    public static final int GL_ONE = 1;
    public static final int GL_ZERO = 0;

    public static final int GL_FRONT=1028, GL_BACK=1029, GL_FRONT_AND_BACK=1032;
    public static final int GL_ALPHA_TEST=0x0BC0, GL_FLAT=7424, GL_SMOOTH=7425;
    public static final int GL_NEVER=512, GL_LESS=513, GL_EQUAL=514, GL_LEQUAL=515,
            GL_GREATER=516, GL_NOTEQUAL=517, GL_GEQUAL=518, GL_ALWAYS=519;
    public static final int GL_CURRENT_BIT=1, GL_POINT_BIT=2, GL_LINE_BIT=4, GL_POLYGON_BIT=8, GL_LIGHTING_BIT=64,
            GL_DEPTH_BUFFER_BIT=256, GL_ENABLE_BIT=8192, GL_COLOR_BUFFER_BIT=16384,
            GL_TEXTURE_BIT=262144, GL_ALL_ATTRIB_BITS=0xFFFFF;

    public static final int GL_POINTS=0, GL_LINES=1, GL_LINE_LOOP=2, GL_LINE_STRIP=3,
            GL_TRIANGLES=4, GL_TRIANGLE_STRIP=5, GL_TRIANGLE_FAN=6, GL_QUADS=7,
            GL_QUAD_STRIP=8, GL_POLYGON=9, GL_FOG=0x0B60;

    public static final int GL_NO_ERROR=0,GL_INVALID_ENUM=0x0500,GL_INVALID_VALUE=0x0501,GL_INVALID_OPERATION=0x0502;
    /** Recorded commands have not reached the driver yet; query native errors only outside recording. */
    public static int glGetError() {
        if(GLRecorder.active()!=null)return GL_NO_ERROR;
        try {if(com.mojang.blaze3d.systems.RenderSystem.isOnRenderThread() && org.lwjgl.opengl.GL.getCapabilities()!=null)return org.lwjgl.opengl.GL11.glGetError();}
        catch(IllegalStateException noContext) { /* CPU-only script validation has no driver context. */ }
        return GL_NO_ERROR;
    }

    public static final int GL_MODELVIEW=5888,GL_PROJECTION=5889,GL_TEXTURE=5890,
        GL_MODELVIEW_STACK_DEPTH=2979,GL_PROJECTION_STACK_DEPTH=2980,GL_TEXTURE_STACK_DEPTH=2981,GL_ATTRIB_STACK_DEPTH=2992,
        GL_MATRIX_MODE=2976,GL_MODELVIEW_MATRIX=2982,GL_PROJECTION_MATRIX=2983,GL_TEXTURE_MATRIX=2984,
        GL_CURRENT_COLOR=2816,GL_CURRENT_TEXTURE_COORDS=2819,GL_COLOR_WRITEMASK=3107,
        GL_ALPHA_TEST_FUNC=3009,GL_ALPHA_TEST_REF=3010,GL_DEPTH_FUNC=2932,GL_DEPTH_WRITEMASK=2930,
        GL_TEXTURE_BINDING_2D=32873,GL_TEXTURE_ENV=8960,GL_TEXTURE_ENV_MODE=8704,GL_MODULATE=8448;
    private static LegacyMatrixState matrices() {GLRecorder r=rec();return r==null?MATRICES.get():r.matrices;}
    private static final ThreadLocal<LegacyMatrixState> MATRICES=ThreadLocal.withInitial(()->new GLRecorder().matrices);
    public static void glMatrixMode(int mode) {if(mode!=GL_MODELVIEW&&mode!=GL_PROJECTION&&mode!=GL_TEXTURE)throw new IllegalArgumentException("Invalid matrix mode");matrices().mode=mode;}
    public static void glLoadIdentity() {matrices().load(new org.joml.Matrix4f());}
    public static void glLoadMatrix(java.nio.FloatBuffer values) {float[] matrix=new float[16];for(int i=0;i<16;i++)matrix[i]=values.get(values.position()+i);matrices().load(new org.joml.Matrix4f().set(matrix));}
    public static void glLoadMatrixf(java.nio.FloatBuffer values) {glLoadMatrix(values);}
    public static void glOrtho(double left,double right,double bottom,double top,double near,double far) {matrices().multiply(new org.joml.Matrix4f().setOrtho((float)left,(float)right,(float)bottom,(float)top,(float)near,(float)far));}
    public static void glColorMask(boolean r,boolean g,boolean b,boolean a) {matrices().colorMask=new boolean[]{r,g,b,a};if(rec()!=null)rec().colorMask(r,g,b,a);}
    public static boolean glGetBoolean(int query) {if(query==GL_DEPTH_WRITEMASK){Boolean value=LegacyRenderState.current().snapshot().depthWrite();return value==null||value;}return glIsEnabled(query);}
    public static void glGetBoolean(int query,java.nio.ByteBuffer output) {boolean[] values=query==GL_COLOR_WRITEMASK?matrices().colorMask:new boolean[]{glGetBoolean(query)};for(int i=0;i<values.length;i++)output.put(output.position()+i,(byte)(values[i]?1:0));}
    public static int glGetInteger(int query) {
        var s=LegacyRenderState.current().snapshot();var m=matrices();
        return switch(query) {case GL_MODELVIEW_STACK_DEPTH->m.depth(GL_MODELVIEW);case GL_PROJECTION_STACK_DEPTH->m.depth(GL_PROJECTION);case GL_TEXTURE_STACK_DEPTH->m.depth(GL_TEXTURE);case GL_ATTRIB_STACK_DEPTH->LegacyRenderState.current().attributeDepth();case GL_MATRIX_MODE->m.mode;case 34016->m.activeTexture;
            case GL_TEXTURE_BINDING_2D->m.textureBindings.getOrDefault(m.activeTexture,0);
            case GL_ALPHA_TEST_FUNC->s.alphaFunction()==null?GL_GREATER:s.alphaFunction();
            case GL_DEPTH_FUNC->s.depthFunction()==null?GL_LEQUAL:s.depthFunction();
            case 32969->s.sourceBlend()==null?GL_ONE:s.sourceBlend();case 32968->s.destinationBlend()==null?GL_ZERO:s.destinationBlend();
            case 32971->s.sourceAlphaBlend()==null?GL_ONE:s.sourceAlphaBlend();case 32970->s.destinationAlphaBlend()==null?GL_ZERO:s.destinationAlphaBlend();
            default->throw new IllegalArgumentException("Unsupported integer GL query: "+query);};
    }
    public static float glGetFloat(int query) {if(query==GL_ALPHA_TEST_REF){Float value=LegacyRenderState.current().snapshot().alphaReference();return value==null?0.1F:value;}return glGetInteger(query);}
    public static void glGetFloat(int query,java.nio.FloatBuffer output) {
        float[] values;
        if(query==GL_CURRENT_COLOR)values=LegacyRenderState.current().currentColor();
        else if(query==GL_CURRENT_TEXTURE_COORDS)values=matrices().textureCoords.getOrDefault(matrices().activeTexture,new float[]{0,0,0,1});
        else if(query==GL_MODELVIEW_MATRIX||query==GL_PROJECTION_MATRIX||query==GL_TEXTURE_MATRIX)values=matrices().get(query);
        else values=new float[]{glGetFloat(query)};
        for(int i=0;i<values.length;i++)output.put(output.position()+i,values[i]);
    }
    public static void glTexEnvi(int target,int query,int value) {matrices().textureEnvironments.put(matrices().activeTexture,value);}
    public static int glGetTexEnvi(int target,int query) {return matrices().textureEnvironments.getOrDefault(matrices().activeTexture,GL_MODULATE);}
    public static void glTexCoord4f(double u,double v,double r,double q) {matrices().textureCoords.put(33984,new float[]{(float)u,(float)v,(float)r,(float)q});glTexCoord2f(u/q,v/q);}

    private GL11Facade() {
    }

    private static final ThreadLocal<Boolean> IMMEDIATE=ThreadLocal.withInitial(()->false);
    static void resetImmediate() { if(IMMEDIATE.get()) NGTTessellator.instance.abortDrawing(); IMMEDIATE.remove(); }
    public static void glBegin(int mode) {
        if(IMMEDIATE.get()) throw new IllegalStateException("Nested glBegin");
        var state=LegacyRenderState.current();
        float[] color=state.currentColor(), normal=state.currentNormal(), uv=state.currentUV();
        var tess=NGTTessellator.instance;
        tess.startDrawing(mode); tess.setColorRGBA_F(color[0],color[1],color[2],color[3]);
        tess.setNormal(normal[0],normal[1],normal[2]); tess.setTextureUV(uv[0],uv[1]);
        IMMEDIATE.set(true);
    }
    public static void glEnd() {
        if(!IMMEDIATE.get()) throw new IllegalStateException("glEnd without glBegin");
        try { NGTTessellator.instance.draw(); } finally { IMMEDIATE.remove(); }
    }
    public static void glVertex3f(double x,double y,double z) {
        if(!IMMEDIATE.get()) throw new IllegalStateException("glVertex outside glBegin");
        NGTTessellator.instance.addVertex(x,y,z);
    }
    public static void glVertex3d(double x,double y,double z) { glVertex3f(x,y,z); }
    public static void glVertex2f(double x,double y) { glVertex3f(x,y,0); }
    public static void glVertex2d(double x,double y) { glVertex3f(x,y,0); }
    public static void glTexCoord2f(double u,double v) {
        matrices().textureCoords.put(33984,new float[]{(float)u,(float)v,0,1});
        LegacyRenderState.current().texCoord((float)u,(float)v);
        NGTTessellator.instance.setTextureUV(u,v);
    }
    public static void glTexCoord2d(double u,double v) { glTexCoord2f(u,v); }
    public static void glColor4d(double r,double g,double b,double a) { glColor4f(r,g,b,a); }
    public static void glColor3d(double r,double g,double b) { glColor4f(r,g,b,1); }
    public static void glNormal3d(double x,double y,double z) { glNormal3f(x,y,z); }

    private static GLRecorder rec() {
        return GLRecorder.active();
    }

    /**
     * 記録 (GLRecorder) を通さずに描く経路のための、実際の行列スタック。
     *
     * <p>★これが無いと、記録に何も出さないスクリプト (焼き込みモデルが本体で、
     * スクリプトは向きだけ変えるもの) の {@code glRotatef} 等が<b>黙って捨てられる</b>。
     * ミラーボール・赤色灯が通電しても回らなかったのはこれ。
     * 型は Object で持つ (専用サーバーで client 型を触らないため)。
     */
    private static final ThreadLocal<Object> FALLBACK_POSE = new ThreadLocal<>();
    /** フォールバック側で積んだ push の数。スクリプトが pop し忘れても戻せるように数える。 */
    private static final ThreadLocal<int[]> FALLBACK_DEPTH = ThreadLocal.withInitial(() -> new int[1]);

    /** スクリプト実行の前後で呼ぶ。記録が無いときの変換先を差し替える。 */
    public static void setFallbackPose(Object poseStack) {
        FALLBACK_POSE.set(poseStack);
        FALLBACK_DEPTH.get()[0] = 0;
    }

    /** 差し替えを解除する。スクリプトが積みっぱなしにした push はここで戻す。 */
    public static void clearFallbackPose() {
        com.mojang.blaze3d.vertex.PoseStack ps = fallback();
        int[] depth = FALLBACK_DEPTH.get();
        if (ps != null) {
            while (depth[0] > 0) {
                ps.popPose();
                depth[0]--;
            }
        }
        depth[0] = 0;
        FALLBACK_POSE.remove();
    }

    private static com.mojang.blaze3d.vertex.PoseStack fallback() {
        Object o = FALLBACK_POSE.get();
        return o instanceof com.mojang.blaze3d.vertex.PoseStack ps ? ps : null;
    }

    /**
     * glPushAttrib / glPopAttrib はスクリプトの描画状態を属性マスクごとに保存・復帰する。
     */
    public static void glPushAttrib(int mask) {
        LegacyRenderState.current().push(mask);
    }

    public static void glPopAttrib() {
        LegacyRenderState.current().pop();
    }

    /** glIsEnabled: このスクリプトで設定した状態と描画パスの初期状態を返す。 */
    public static boolean glIsEnabled(int cap) {
        return cap==GL_TEXTURE_2D?matrices().textureEnabled.getOrDefault(matrices().activeTexture,LegacyRenderState.current().enabled(cap)):LegacyRenderState.current().enabled(cap);
    }

    /** glMultMatrix(FloatBuffer): 現在の行列に掛ける。 */
    public static void glMultMatrix(java.nio.FloatBuffer matrix) {
        GLRecorder r = rec();
        if (r == null || matrix == null) {
            return;
        }
        float[] m = new float[16];
        int pos = matrix.position();
        for (int i = 0; i < 16 && matrix.remaining() > i; i++) {
            m[i] = matrix.get(pos + i);
        }
        if(matrices().mode==GL_MODELVIEW){matrices().current().mul(new org.joml.Matrix4f().set(m));r.multMatrix(m);}else matrices().multiply(new org.joml.Matrix4f().set(m));
    }

    public static void glMultMatrixf(java.nio.FloatBuffer matrix) {
        glMultMatrix(matrix);
    }

    public static void glPushMatrix() {
        GLRecorder r = rec();
        if (r != null) {
            matrices().push();
            return;
        }
        com.mojang.blaze3d.vertex.PoseStack ps = fallback();
        if (ps != null) {
            ps.pushPose();
            FALLBACK_DEPTH.get()[0]++;
        }
    }

    public static void glPopMatrix() {
        GLRecorder r = rec();
        if (r != null) {
            matrices().pop();
            return;
        }
        com.mojang.blaze3d.vertex.PoseStack ps = fallback();
        int[] depth = FALLBACK_DEPTH.get();
        if (ps != null && depth[0] > 0) {
            ps.popPose();
            depth[0]--;
        }
    }

    public static void glTranslatef(double x, double y, double z) {
        GLRecorder r = rec();
        if (r != null) {
            if(matrices().mode==GL_MODELVIEW){matrices().current().translate((float)x,(float)y,(float)z);r.translate((float)x,(float)y,(float)z);}else matrices().multiply(new org.joml.Matrix4f().translation((float)x,(float)y,(float)z));
            return;
        }
        com.mojang.blaze3d.vertex.PoseStack ps = fallback();
        if (ps != null) {
            ps.translate(x, y, z);
        }
    }

    public static void glTranslated(double x, double y, double z) {
        glTranslatef(x, y, z);
    }

    public static void glRotatef(double deg, double x, double y, double z) {
        GLRecorder r = rec();
        if (r != null) {
            if(x*x+y*y+z*z>0){var matrix=new org.joml.Matrix4f().rotation((float)Math.toRadians(deg),new org.joml.Vector3f((float)x,(float)y,(float)z).normalize());if(matrices().mode==GL_MODELVIEW){matrices().current().mul(matrix);r.rotate((float)deg,(float)x,(float)y,(float)z);}else matrices().multiply(matrix);}
            return;
        }
        com.mojang.blaze3d.vertex.PoseStack ps = fallback();
        if (ps != null && deg != 0.0D) {
            ps.mulPose(new org.joml.Quaternionf().rotateAxis(
                (float) Math.toRadians(deg),
                (float) x, (float) y, (float) z));
        }
    }

    public static void glRotated(double deg, double x, double y, double z) {
        glRotatef(deg, x, y, z);
    }

    public static void glScalef(double x, double y, double z) {
        GLRecorder r = rec();
        if (r != null) {
            if(matrices().mode==GL_MODELVIEW){matrices().current().scale((float)x,(float)y,(float)z);r.scale((float)x,(float)y,(float)z);}else matrices().multiply(new org.joml.Matrix4f().scaling((float)x,(float)y,(float)z));
            return;
        }
        com.mojang.blaze3d.vertex.PoseStack ps = fallback();
        if (ps != null) {
            ps.scale((float) x, (float) y, (float) z);
        }
    }

    public static void glScaled(double x, double y, double z) {
        glScalef(x, y, z);
    }

    public static void glColor4f(double r0, double g, double b, double a) {
        GLRecorder r = rec();
        if (r != null) r.color((float) r0, (float) g, (float) b, (float) a);
        else LegacyRenderState.current().color((float)r0,(float)g,(float)b,(float)a);
        if(IMMEDIATE.get()) NGTTessellator.instance.setColorRGBA_F((float)r0,(float)g,(float)b,(float)a);
    }

    public static void glColor3f(double r0, double g, double b) {
        glColor4f(r0, g, b, 1.0D);
    }

    // 状態は各描画コマンドに保存し、再生時に対応する RenderType を選ぶ。
    public static void glEnable(int cap) {
        if(cap==GL_TEXTURE_2D)matrices().textureEnabled.put(matrices().activeTexture,true);
        if(cap!=GL_TEXTURE_2D || matrices().activeTexture==33984)LegacyRenderState.current().enable(cap, true);
    }

    public static void glDisable(int cap) {
        if(cap==GL_TEXTURE_2D)matrices().textureEnabled.put(matrices().activeTexture,false);
        if(cap!=GL_TEXTURE_2D || matrices().activeTexture==33984)LegacyRenderState.current().enable(cap, false);
    }

    public static void glBlendFunc(int src, int dst) {
        LegacyRenderState.current().blend(src, dst);
    }

    public static void glBindTexture(int target, int tex) {
        matrices().textureBindings.put(matrices().activeTexture,tex);
        if(matrices().activeTexture!=33984)return;
        // mccompat.TextureUtil の疑似ハンドル → 動的テクスチャ RL に解決して記録
        GLRecorder r = rec();
        if (r != null) {
            r.bindTexture(jp.ngt.mccompat.TextureUtil.getTexture(tex));
        }
    }

    public static void glLoadName(int name) { GLRecorder recorder=rec(); if(recorder!=null) recorder.loadName(name); }

    public static void glNormal3f(double x, double y, double z) {
        LegacyRenderState.current().normal((float)x,(float)y,(float)z);
        NGTTessellator.instance.setNormal((float) x, (float) y, (float) z);
    }

    public static void glPointSize(double size) { LegacyRenderState.current().pointSize((float)size); }

    public static void glLineWidth(double w) {
        LegacyRenderState.current().lineWidth((float) w);
    }

    public static void glShadeModel(int mode) {
        LegacyRenderState.current().shade(mode);
    }

    public static void glDepthFunc(int function) { LegacyRenderState.current().depthFunction(function); }
    public static void glCullFace(int mode) { LegacyRenderState.current().cullFace(mode); }

    public static void glDepthMask(boolean flag) {
        LegacyRenderState.current().depthWrite(flag);
    }

    public static void glAlphaFunc(int func, double ref) {
        LegacyRenderState.current().alpha(func, (float) ref);
    }
}
