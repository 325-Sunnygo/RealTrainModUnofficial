package jp.ngt.mccompat.input;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * LWJGL2 org.lwjgl.input.Keyboard のスクリプト互換 (GLFW ベース)。
 * SRB3/NGTO Builder が isKeyDown + KEY_* 定数を使用する。
 */
public final class Keyboard {
    private Keyboard() {
    }

    // === LWJGL2 キーコード定数 (スクリプトが参照する分 + 主要キー) ===
    public static final int KEY_ESCAPE = 1;
    public static final int KEY_1 = 2;
    public static final int KEY_2 = 3;
    public static final int KEY_3 = 4;
    public static final int KEY_4 = 5;
    public static final int KEY_5 = 6;
    public static final int KEY_6 = 7;
    public static final int KEY_7 = 8;
    public static final int KEY_8 = 9;
    public static final int KEY_9 = 10;
    public static final int KEY_0 = 11;
    public static final int KEY_MINUS = 12;
    public static final int KEY_EQUALS = 13;
    public static final int KEY_BACK = 14;
    public static final int KEY_TAB = 15;
    public static final int KEY_Q = 16;
    public static final int KEY_W = 17;
    public static final int KEY_E = 18;
    public static final int KEY_R = 19;
    public static final int KEY_T = 20;
    public static final int KEY_Y = 21;
    public static final int KEY_U = 22;
    public static final int KEY_I = 23;
    public static final int KEY_O = 24;
    public static final int KEY_P = 25;
    public static final int KEY_RETURN = 28;
    /**
     * KEY_NONE = 0。本家 LWJGL2 の「キー未割り当て」。
     * NGTO Builder 2 の InputManager が修飾キーの初期値に使う。
     */
    public static final int KEY_NONE = 0;

    public static final int KEY_LCONTROL = 29;
    public static final int KEY_A = 30;
    public static final int KEY_S = 31;
    public static final int KEY_D = 32;
    public static final int KEY_F = 33;
    public static final int KEY_G = 34;
    public static final int KEY_H = 35;
    public static final int KEY_J = 36;
    public static final int KEY_K = 37;
    public static final int KEY_L = 38;
    public static final int KEY_LSHIFT = 42;
    public static final int KEY_Z = 44;
    public static final int KEY_X = 45;
    public static final int KEY_C = 46;
    public static final int KEY_V = 47;
    public static final int KEY_B = 48;
    public static final int KEY_N = 49;
    public static final int KEY_M = 50;
    public static final int KEY_SPACE = 57;
    public static final int KEY_UP = 200;
    public static final int KEY_LEFT = 203;
    public static final int KEY_RIGHT = 205;
    public static final int KEY_DOWN = 208;
    public static final int KEY_DELETE = 211;
    public static final int KEY_RSHIFT = 54;
    public static final int KEY_RCONTROL = 157;
    public static final int KEY_LMENU = 56;
    public static final int KEY_RMENU = 184;

    public static boolean isKeyDown(int lwjgl2Key) {
        if (lwjgl2Key == KEY_NONE) {
            return false;
        }
        int glfwKey = toGlfw(lwjgl2Key);
        if (glfwKey < 0) {
            return false;
        }
        try {
            long window = Minecraft.getInstance().getWindow().getWindow();
            return GLFW.glfwGetKey(window, glfwKey) == GLFW.GLFW_PRESS;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 本家 Keyboard.getKeyName。操作説明の表示に使う (NGTO Builder 2 のヘルプ表示)。
     * GLFW のキー名を返し、取れないものは LWJGL2 のコード番号を文字列で返す。
     */
    public static String getKeyName(int lwjgl2Key) {
        if (lwjgl2Key == KEY_NONE) {
            return "NONE";
        }
        int glfwKey = toGlfw(lwjgl2Key);
        if (glfwKey >= 0) {
            try {
                String name = GLFW.glfwGetKeyName(glfwKey, 0);
                if (name != null && !name.isBlank()) {
                    return name.toUpperCase(java.util.Locale.ROOT);
                }
            } catch (Throwable ignored) {
                // 名前を持たないキー (ENTER 等) はここに来る
            }
            String fixed = fixedKeyName(glfwKey);
            if (fixed != null) {
                return fixed;
            }
        }
        return String.valueOf(lwjgl2Key);
    }

    /** glfwGetKeyName が null を返す特殊キーの表示名。 */
    private static String fixedKeyName(int glfwKey) {
        return switch (glfwKey) {
            case GLFW.GLFW_KEY_ENTER -> "ENTER";
            case GLFW.GLFW_KEY_SPACE -> "SPACE";
            case GLFW.GLFW_KEY_TAB -> "TAB";
            case GLFW.GLFW_KEY_ESCAPE -> "ESCAPE";
            case GLFW.GLFW_KEY_BACKSPACE -> "BACKSPACE";
            case GLFW.GLFW_KEY_LEFT_CONTROL -> "LCONTROL";
            case GLFW.GLFW_KEY_RIGHT_CONTROL -> "RCONTROL";
            case GLFW.GLFW_KEY_LEFT_SHIFT -> "LSHIFT";
            case GLFW.GLFW_KEY_RIGHT_SHIFT -> "RSHIFT";
            case GLFW.GLFW_KEY_LEFT_ALT -> "LMENU";
            case GLFW.GLFW_KEY_RIGHT_ALT -> "RMENU";
            case GLFW.GLFW_KEY_UP -> "UP";
            case GLFW.GLFW_KEY_DOWN -> "DOWN";
            case GLFW.GLFW_KEY_LEFT -> "LEFT";
            case GLFW.GLFW_KEY_RIGHT -> "RIGHT";
            default -> null;
        };
    }

    /** 本家 Keyboard.isCreated 互換 */
    public static boolean isCreated() {
        return true;
    }

    private static int toGlfw(int key) {
        return switch (key) {
            case KEY_ESCAPE -> GLFW.GLFW_KEY_ESCAPE;
            case KEY_1 -> GLFW.GLFW_KEY_1;
            case KEY_2 -> GLFW.GLFW_KEY_2;
            case KEY_3 -> GLFW.GLFW_KEY_3;
            case KEY_4 -> GLFW.GLFW_KEY_4;
            case KEY_5 -> GLFW.GLFW_KEY_5;
            case KEY_6 -> GLFW.GLFW_KEY_6;
            case KEY_7 -> GLFW.GLFW_KEY_7;
            case KEY_8 -> GLFW.GLFW_KEY_8;
            case KEY_9 -> GLFW.GLFW_KEY_9;
            case KEY_0 -> GLFW.GLFW_KEY_0;
            case KEY_MINUS -> GLFW.GLFW_KEY_MINUS;
            case KEY_EQUALS -> GLFW.GLFW_KEY_EQUAL;
            case KEY_BACK -> GLFW.GLFW_KEY_BACKSPACE;
            case KEY_TAB -> GLFW.GLFW_KEY_TAB;
            case KEY_Q -> GLFW.GLFW_KEY_Q;
            case KEY_W -> GLFW.GLFW_KEY_W;
            case KEY_E -> GLFW.GLFW_KEY_E;
            case KEY_R -> GLFW.GLFW_KEY_R;
            case KEY_T -> GLFW.GLFW_KEY_T;
            case KEY_Y -> GLFW.GLFW_KEY_Y;
            case KEY_U -> GLFW.GLFW_KEY_U;
            case KEY_I -> GLFW.GLFW_KEY_I;
            case KEY_O -> GLFW.GLFW_KEY_O;
            case KEY_P -> GLFW.GLFW_KEY_P;
            case KEY_RETURN -> GLFW.GLFW_KEY_ENTER;
            case KEY_LCONTROL -> GLFW.GLFW_KEY_LEFT_CONTROL;
            case KEY_A -> GLFW.GLFW_KEY_A;
            case KEY_S -> GLFW.GLFW_KEY_S;
            case KEY_D -> GLFW.GLFW_KEY_D;
            case KEY_F -> GLFW.GLFW_KEY_F;
            case KEY_G -> GLFW.GLFW_KEY_G;
            case KEY_H -> GLFW.GLFW_KEY_H;
            case KEY_J -> GLFW.GLFW_KEY_J;
            case KEY_K -> GLFW.GLFW_KEY_K;
            case KEY_L -> GLFW.GLFW_KEY_L;
            case KEY_LSHIFT -> GLFW.GLFW_KEY_LEFT_SHIFT;
            case KEY_Z -> GLFW.GLFW_KEY_Z;
            case KEY_X -> GLFW.GLFW_KEY_X;
            case KEY_C -> GLFW.GLFW_KEY_C;
            case KEY_V -> GLFW.GLFW_KEY_V;
            case KEY_B -> GLFW.GLFW_KEY_B;
            case KEY_N -> GLFW.GLFW_KEY_N;
            case KEY_M -> GLFW.GLFW_KEY_M;
            case KEY_SPACE -> GLFW.GLFW_KEY_SPACE;
            case KEY_UP -> GLFW.GLFW_KEY_UP;
            case KEY_LEFT -> GLFW.GLFW_KEY_LEFT;
            case KEY_RIGHT -> GLFW.GLFW_KEY_RIGHT;
            case KEY_DOWN -> GLFW.GLFW_KEY_DOWN;
            case KEY_DELETE -> GLFW.GLFW_KEY_DELETE;
            case KEY_RSHIFT -> GLFW.GLFW_KEY_RIGHT_SHIFT;
            case KEY_RCONTROL -> GLFW.GLFW_KEY_RIGHT_CONTROL;
            case KEY_LMENU -> GLFW.GLFW_KEY_LEFT_ALT;
            case KEY_RMENU -> GLFW.GLFW_KEY_RIGHT_ALT;
            default -> -1;
        };
    }
}
