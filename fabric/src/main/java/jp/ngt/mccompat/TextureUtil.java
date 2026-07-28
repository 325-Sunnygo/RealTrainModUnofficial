package jp.ngt.mccompat;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;

import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * パックスクリプト互換: 1.7.10 の net.minecraft.client.renderer.texture.TextureUtil。
 * func_110996_a (glGenTextures) は疑似ハンドルを払い出し、
 * func_110987_a (uploadTextureImageAllocate) は BufferedImage を DynamicTexture として
 * TextureManager に登録する。GL11Facade.glBindTexture がハンドル→ResourceLocation を解決する。
 */
public final class TextureUtil {
    private static final AtomicInteger NEXT_ID = new AtomicInteger(1);
    private static final Map<Integer, net.minecraft.resources.ResourceLocation> HANDLE_TO_RL = new ConcurrentHashMap<>();

    private TextureUtil() {
    }

    /**
     * 1.7.10 SRG: glGenTextures 相当 (疑似ハンドル)
     */
    public static int func_110996_a() {
        return NEXT_ID.getAndIncrement();
    }

    /**
     * 1.7.10 SRG: uploadTextureImageAllocate
     */
    public static int func_110987_a(int handle, BufferedImage image) {
        if (image == null) {
            return handle;
        }
        NativeImage img = toNativeImage(image);
        net.minecraft.resources.ResourceLocation rl = Minecraft.getInstance().getTextureManager()
                .register("rtmu_script_tex", new DynamicTexture(img));
        HANDLE_TO_RL.put(handle, rl);
        return handle;
    }

    public static net.minecraft.resources.ResourceLocation getTexture(int handle) {
        return HANDLE_TO_RL.get(handle);
    }

    private static NativeImage toNativeImage(BufferedImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        NativeImage img = new NativeImage(NativeImage.Format.RGBA, w, h, false);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = image.getRGB(x, y);
                int a = (argb >>> 24) & 0xFF;
                int r = (argb >>> 16) & 0xFF;
                int g = (argb >>> 8) & 0xFF;
                int b = argb & 0xFF;
                img.setPixelRGBA(x, y, (a << 24) | (b << 16) | (g << 8) | r);
            }
        }
        return img;
    }
}
