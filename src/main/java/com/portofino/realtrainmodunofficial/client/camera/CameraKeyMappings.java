package com.portofino.realtrainmodunofficial.client.camera;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * カメラモードのキー。本家 jp.ngt.rtm.gui.camera.CameraKey に合わせた固定割り当て
 * (ズーム Z/X、シャッター C/V、ピント B/N、モード M)。増やしたぶんは空きキーへ。
 * ★キーコンフィグには出さない。変更不可なので KeyMapping を作らず GLFW を直接見る。
 */
public final class CameraKeyMappings {

    /** 本家 ZOOM_OUT / ZOOM_IN */
    public static final int ZOOM_OUT = GLFW.GLFW_KEY_Z;
    public static final int ZOOM_IN = GLFW.GLFW_KEY_X;
    /** 本家 SENSIT_DOWN / SENSIT_UP をシャッター速度に置き換え */
    public static final int SHUTTER_SLOWER = GLFW.GLFW_KEY_C;
    public static final int SHUTTER_FASTER = GLFW.GLFW_KEY_V;
    /** 本家 FOCUS_OUT / FOCUS_IN (MF のピント送り) */
    public static final int FOCUS_NEAR = GLFW.GLFW_KEY_B;
    public static final int FOCUS_FAR = GLFW.GLFW_KEY_N;
    /** 本家 FOCUS_MODE */
    public static final int FOCUS_MODE = GLFW.GLFW_KEY_M;
    /** 絞り (F値) */
    public static final int APERTURE_OPEN = GLFW.GLFW_KEY_F;
    public static final int APERTURE_CLOSE = GLFW.GLFW_KEY_G;
    /** ファインダー */
    public static final int CYCLE_GRID = GLFW.GLFW_KEY_H;
    public static final int CYCLE_ASPECT = GLFW.GLFW_KEY_J;
    public static final int TOGGLE_LEVEL = GLFW.GLFW_KEY_K;
    // レンズ / テレコンはアイテムを持って右クリックで装着するのでキーは無い。
    /** 撮影 */
    public static final int SHOOT = GLFW.GLFW_KEY_ENTER;

    private CameraKeyMappings() {
    }

    /**
     * いま押されているか。
     * 呼び出し側は「カメラ起動中かつ画面を開いていない」ときだけ呼ぶこと
     * (キーコンフィグを経由しないので、チャット入力中でも押下は取れてしまう)。
     */
    public static boolean isDown(int key) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) {
            return false;
        }
        return InputConstants.isKeyDown(mc.getWindow().getWindow(), key);
    }
}
