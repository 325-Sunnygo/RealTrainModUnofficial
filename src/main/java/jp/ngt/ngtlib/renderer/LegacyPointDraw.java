package jp.ngt.ngtlib.renderer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL32.GL_PROGRAM_POINT_SIZE;

/** Draw actual GL_POINTS and restore both fixed/programmed point-size state. */
public final class LegacyPointDraw {
    private LegacyPointDraw() {}
    public static void draw(int count,float size) {
        if(count<0 || !(size>0) || !Float.isFinite(size)) throw new IllegalArgumentException("Invalid legacy point draw");
        boolean programmable=glIsEnabled(GL_PROGRAM_POINT_SIZE);
        float previous=glGetFloat(GL_POINT_SIZE);
        try {
            glDisable(GL_PROGRAM_POINT_SIZE);
            glPointSize(size);
            glDrawArrays(GL_POINTS,0,count);
        } finally {
            glPointSize(previous);
            if(programmable) glEnable(GL_PROGRAM_POINT_SIZE); else glDisable(GL_PROGRAM_POINT_SIZE);
        }
    }
}
