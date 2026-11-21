package jp.ngt.ngtlib.renderer;
public interface IRenderer {
    void startDrawing(int mode);
    int draw();
    void addVertexWithUV(float x,float y,float z,float u,float v);
    void setNormal(float x,float y,float z);
    void setBrightness(int packed);
}
