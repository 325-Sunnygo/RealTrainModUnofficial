package jp.ngt.ngtlib.renderer;
/** Recorded equivalent of the original immediate-mode polygon renderer. */
public final class PolygonRenderer implements IRenderer {
    public static final PolygonRenderer INSTANCE=new PolygonRenderer();
    public static final float DIV_15=1.0F/15.0F;
    private PolygonRenderer() {}
    public void startDrawing(int mode) { NGTTessellator.instance.startDrawing(mode); }
    public int draw() { NGTTessellator.instance.draw(); return 0; }
    public void addVertexWithUV(float x,float y,float z,float u,float v) { NGTTessellator.instance.addVertexWithUV(x,y,z,u,v); }
    public void setNormal(float x,float y,float z) { GL11Facade.glNormal3f(x,y,z); }
    public void setBrightness(int value) { GLHelper.setBrightness(value); }
}
