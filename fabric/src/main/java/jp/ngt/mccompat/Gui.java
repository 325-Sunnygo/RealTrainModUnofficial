package jp.ngt.mccompat;

import jp.ngt.ngtlib.renderer.GL11Facade;

/** The instantiable 1.7.10 Gui drawing helper, distinct from the modern HUD. */
public class Gui {
    public float zLevel;

    public void drawTexturedModalRect(int x, int y, int u, int v, int width, int height) {
        func_73729_b(x, y, u, v, width, height);
    }

    public void func_73729_b(int x, int y, int u, int v, int width, int height) {
        GL11Facade.glBegin(GL11Facade.GL_QUADS);
        GL11Facade.glTexCoord2f(u / 256.0, (v + height) / 256.0);
        GL11Facade.glVertex3f(x, y + height, zLevel);
        GL11Facade.glTexCoord2f((u + width) / 256.0, (v + height) / 256.0);
        GL11Facade.glVertex3f(x + width, y + height, zLevel);
        GL11Facade.glTexCoord2f((u + width) / 256.0, v / 256.0);
        GL11Facade.glVertex3f(x + width, y, zLevel);
        GL11Facade.glTexCoord2f(u / 256.0, v / 256.0);
        GL11Facade.glVertex3f(x, y, zLevel);
        GL11Facade.glEnd();
    }
}
