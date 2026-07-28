package com.portofino.realtrainmodunofficial.client.camera;

import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

/**
 * カメラモードのキー。既定は本家 jp.ngt.rtm.gui.camera.CameraKey に合わせてある
 * (ズーム Z/X、シャッター C/V、ピント B/N、モード M)。撮り鉄向けに増やしたぶんは空きキーへ。
 */
public final class CameraKeyMappings {
    private static final String CATEGORY = "key.categories.realtrainmodunofficial.camera";

    /** 本家 ZOOM_OUT / ZOOM_IN */
    public static final KeyMapping ZOOM_OUT = key("camera_zoom_out", GLFW.GLFW_KEY_Z);
    public static final KeyMapping ZOOM_IN = key("camera_zoom_in", GLFW.GLFW_KEY_X);
    /** 本家 SENSIT_DOWN / SENSIT_UP をシャッター速度に置き換え */
    public static final KeyMapping SHUTTER_SLOWER = key("camera_shutter_slower", GLFW.GLFW_KEY_C);
    public static final KeyMapping SHUTTER_FASTER = key("camera_shutter_faster", GLFW.GLFW_KEY_V);
    /** 本家 FOCUS_OUT / FOCUS_IN (MF のピント送り) */
    public static final KeyMapping FOCUS_NEAR = key("camera_focus_near", GLFW.GLFW_KEY_B);
    public static final KeyMapping FOCUS_FAR = key("camera_focus_far", GLFW.GLFW_KEY_N);
    /** 本家 FOCUS_MODE */
    public static final KeyMapping FOCUS_MODE = key("camera_focus_mode", GLFW.GLFW_KEY_M);
    /** 絞り (F値) */
    public static final KeyMapping APERTURE_OPEN = key("camera_aperture_open", GLFW.GLFW_KEY_F);
    public static final KeyMapping APERTURE_CLOSE = key("camera_aperture_close", GLFW.GLFW_KEY_G);
    /** ファインダー */
    public static final KeyMapping CYCLE_GRID = key("camera_cycle_grid", GLFW.GLFW_KEY_H);
    public static final KeyMapping CYCLE_ASPECT = key("camera_cycle_aspect", GLFW.GLFW_KEY_J);
    public static final KeyMapping TOGGLE_LEVEL = key("camera_toggle_level", GLFW.GLFW_KEY_K);
    // レンズ / テレコンはアイテムを持って右クリックで装着するのでキーは無い。
    /** 撮影 */
    public static final KeyMapping SHOOT = key("camera_shoot", GLFW.GLFW_KEY_ENTER);

    private CameraKeyMappings() {
    }

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(ZOOM_OUT);
        event.register(ZOOM_IN);
        event.register(SHUTTER_SLOWER);
        event.register(SHUTTER_FASTER);
        event.register(FOCUS_NEAR);
        event.register(FOCUS_FAR);
        event.register(FOCUS_MODE);
        event.register(APERTURE_OPEN);
        event.register(APERTURE_CLOSE);
        event.register(CYCLE_GRID);
        event.register(CYCLE_ASPECT);
        event.register(TOGGLE_LEVEL);
        event.register(SHOOT);
    }

    private static KeyMapping key(String name, int defaultKey) {
        return new KeyMapping("key.realtrainmodunofficial." + name, defaultKey, CATEGORY);
    }
}
