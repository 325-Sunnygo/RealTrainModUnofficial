package jp.ngt.ngtlib.renderer;

import com.mojang.blaze3d.systems.RenderSystem;

/** 1.7.10/1.12 の net.minecraft.client.renderer.OpenG… */
public final class OpenGlHelperCompat {

    private OpenGlHelperCompat() {
    }

    /** func_148821_a = glBlendFunc(srcRGB, dstRGB, srcAl… */
    public static void func_148821_a(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
        LegacyRenderState.current().blendSeparate(srcRgb,dstRgb,srcAlpha,dstAlpha);
    }

    /** func_148824_a = glBlendFunc の後始末 (既定へ戻す)。 */
    public static void func_148824_a() {
        LegacyRenderState.current().blendSeparate(770,771,1,0);
    }
}
