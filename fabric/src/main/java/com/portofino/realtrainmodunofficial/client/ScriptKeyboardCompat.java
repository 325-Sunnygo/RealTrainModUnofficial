package com.portofino.realtrainmodunofficial.client;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * 1.12.2 RTM スクリプト(SuperRailBuilder3 等)が使う LWJGL2 の {@code Keyboard.isKeyDown(code)} を、
 * 1.21.1 の GLFW 実キー状態に橋渡しするクライアント専用ヘルパー。
 *
 * <p>SRB の GUI はキー駆動(KeyMaps: LCONTROL/UP/DOWN/F/LEFT/RIGHT/RETURN/C/P/O/I/DELETE/H/Q 等)で、
 * 旧 RTMU の Keyboard スタブは常に false を返していたため操作が一切効かなかった。ここで実入力に繋ぐ。</p>
 */
public final class ScriptKeyboardCompat {

    /** LWJGL2 のキーコードを受け取り、現在押下中かを返す(GLFW へ変換して読む)。 */
    public boolean isKeyDown(int lwjgl2Code) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.getWindow() == null) {
                return false;
            }
            int glfw = toGlfw(lwjgl2Code);
            if (glfw < 0) {
                return false;
            }
            long window = mc.getWindow().getWindow();
            return GLFW.glfwGetKey(window, glfw) == GLFW.GLFW_PRESS;
        } catch (Throwable t) {
            return false;
        }
    }

    /** LWJGL2 キーコード → GLFW キーコード。SRB が使うキーを網羅。 */
    private static int toGlfw(int code) {
        switch (code) {
            case 1:   return GLFW.GLFW_KEY_ESCAPE;
            case 14:  return GLFW.GLFW_KEY_BACKSPACE;
            case 15:  return GLFW.GLFW_KEY_TAB;
            case 16:  return GLFW.GLFW_KEY_Q;
            case 17:  return GLFW.GLFW_KEY_W;
            case 18:  return GLFW.GLFW_KEY_E;
            case 19:  return GLFW.GLFW_KEY_R;
            case 20:  return GLFW.GLFW_KEY_T;
            case 21:  return GLFW.GLFW_KEY_Y;
            case 22:  return GLFW.GLFW_KEY_U;
            case 23:  return GLFW.GLFW_KEY_I;
            case 24:  return GLFW.GLFW_KEY_O;
            case 25:  return GLFW.GLFW_KEY_P;
            case 26:  return GLFW.GLFW_KEY_LEFT_BRACKET;
            case 27:  return GLFW.GLFW_KEY_RIGHT_BRACKET;
            case 28:  return GLFW.GLFW_KEY_ENTER;
            case 29:  return GLFW.GLFW_KEY_LEFT_CONTROL;
            case 30:  return GLFW.GLFW_KEY_A;
            case 31:  return GLFW.GLFW_KEY_S;
            case 32:  return GLFW.GLFW_KEY_D;
            case 33:  return GLFW.GLFW_KEY_F;
            case 34:  return GLFW.GLFW_KEY_G;
            case 35:  return GLFW.GLFW_KEY_H;
            case 36:  return GLFW.GLFW_KEY_J;
            case 37:  return GLFW.GLFW_KEY_K;
            case 38:  return GLFW.GLFW_KEY_L;
            case 42:  return GLFW.GLFW_KEY_LEFT_SHIFT;
            case 44:  return GLFW.GLFW_KEY_Z;
            case 45:  return GLFW.GLFW_KEY_X;
            case 46:  return GLFW.GLFW_KEY_C;
            case 47:  return GLFW.GLFW_KEY_V;
            case 48:  return GLFW.GLFW_KEY_B;
            case 49:  return GLFW.GLFW_KEY_N;
            case 50:  return GLFW.GLFW_KEY_M;
            case 57:  return GLFW.GLFW_KEY_SPACE;
            case 157: return GLFW.GLFW_KEY_RIGHT_CONTROL;
            case 184: return GLFW.GLFW_KEY_RIGHT_ALT;
            case 199: return GLFW.GLFW_KEY_HOME;
            case 200: return GLFW.GLFW_KEY_UP;
            case 203: return GLFW.GLFW_KEY_LEFT;
            case 205: return GLFW.GLFW_KEY_RIGHT;
            case 207: return GLFW.GLFW_KEY_END;
            case 208: return GLFW.GLFW_KEY_DOWN;
            case 210: return GLFW.GLFW_KEY_INSERT;
            case 211: return GLFW.GLFW_KEY_DELETE;
            default:  return -1;
        }
    }
}
