package jp.ngt.ngtlib.renderer;
public final class GL14Facade {
    public static final int GL_BLEND_SRC_RGB=32969,GL_BLEND_DST_RGB=32968,GL_BLEND_SRC_ALPHA=32971,GL_BLEND_DST_ALPHA=32970;
    public static void glBlendFuncSeparate(int sr,int dr,int sa,int da) {LegacyRenderState.current().blendSeparate(sr,dr,sa,da);}
}
