package jp.ngt.mccompat.input;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * LWJGL2 org.lwjgl.input.Mouse のスクリプト互換 (GLFW ベース)。
 */
public final class Mouse {
    private Mouse() {
    }

    /** button: 0=左, 1=右, 2=中 (LWJGL2 と GLFW で同一) */
    public static boolean isButtonDown(int button) {
        try {
            long window = Minecraft.getInstance().getWindow().getWindow();
            return GLFW.glfwGetMouseButton(window, button) == GLFW.GLFW_PRESS;
        } catch (Throwable t) {
            return false;
        }
    }

    public static int getDWheel() {
        return 0;
    }
}
