package jp.ngt.ngtlib.renderer;
public final class GL13Facade {
    public static final int GL_TEXTURE0=33984,GL_TEXTURE1=33985,GL_ACTIVE_TEXTURE=34016;
    public static void glActiveTexture(int unit) {GLRecorder r=GLRecorder.active();if(r!=null)r.matrices.activeTexture=unit;}
    public static void glMultiTexCoord2f(int unit,float u,float v) {glMultiTexCoord4f(unit,u,v,0,1);}
    public static void glMultiTexCoord4f(int unit,float u,float v,float r,float q) {GLRecorder recorder=GLRecorder.active();if(recorder!=null)recorder.matrices.textureCoords.put(unit,new float[]{u,v,r,q});if(unit==GL_TEXTURE0)GL11Facade.glTexCoord4f(u,v,r,q);}
}
