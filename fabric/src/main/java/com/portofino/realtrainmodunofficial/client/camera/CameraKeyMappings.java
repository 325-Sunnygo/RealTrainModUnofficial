package com.portofino.realtrainmodunofficial.client.camera;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/** Original CameraKey bindings, including the depth-debug key. */
public final class CameraKeyMappings {
    public static final int ZOOM_OUT=GLFW.GLFW_KEY_Z,ZOOM_IN=GLFW.GLFW_KEY_X;
    public static final int SENSIT_DOWN=GLFW.GLFW_KEY_C,SENSIT_UP=GLFW.GLFW_KEY_V;
    public static final int FOCUS_OUT=GLFW.GLFW_KEY_B,FOCUS_IN=GLFW.GLFW_KEY_N;
    public static final int FOCUS_MODE=GLFW.GLFW_KEY_M,DEBUG=GLFW.GLFW_KEY_K;
    private CameraKeyMappings() {}
    public static boolean isDown(int key) {
        var mc=Minecraft.getInstance();
        return mc!=null && mc.getWindow()!=null && InputConstants.isKeyDown(mc.getWindow().getWindow(),key);
    }
}
