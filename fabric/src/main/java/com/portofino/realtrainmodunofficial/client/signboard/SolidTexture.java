package com.portofino.realtrainmodunofficial.client.signboard;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * 1x1 の白テクスチャ。
 * 本家の看板は板の側面と (backTexture==2 の) 裏面を「テクスチャ無しの単色」で塗っていた
 * (glDisable(GL_TEXTURE_2D) + 頂点カラー)。
 */
public final class SolidTexture {
    private static ResourceLocation white;

    private SolidTexture() {
    }

    public static ResourceLocation white() {
        if (white == null) {
            NativeImage img = new NativeImage(NativeImage.Format.RGBA, 1, 1, false);
            img.setPixelRGBA(0, 0, 0xFFFFFFFF);
            white = Minecraft.getInstance().getTextureManager()
                    .register("rtmu_signboard_white", new DynamicTexture(img));
        }
        return white;
    }
}
