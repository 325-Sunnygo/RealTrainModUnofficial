package com.portofino.realtrainmodunofficial.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** 動的テクスチャ (NativeImage から作る DynamicTexture) の登録を 1… */
public final class DynamicTextureRegistry {

    /** 登録済み loc。二重登録 (= TextureManager による先行 close) を防ぐ。 */
    private static final Set<ResourceLocation> REGISTERED = ConcurrentHashMap.newKeySet();

    private DynamicTextureRegistry() {
    }

    /** NativeImage から DynamicTexture を作って登録する。 */
    private static final java.util.Map<ResourceLocation,LegacyTextureSampling> SAMPLING = new ConcurrentHashMap<>();
    public static LegacyTextureSampling sampling(ResourceLocation location) {return SAMPLING.getOrDefault(location,LegacyTextureSampling.DEFAULT);}
    public static boolean blur(ResourceLocation location) { return location!=null && SAMPLING.getOrDefault(location,LegacyTextureSampling.DEFAULT).blur(); }
    public static DynamicTexture register(ResourceLocation loc, NativeImage image) {
        return register(loc,image,LegacyTextureSampling.DEFAULT);
    }
    public static DynamicTexture register(ResourceLocation loc, NativeImage image, LegacyTextureSampling sampling) {
        if (loc == null || image == null) {
            return null;
        }
        if (!REGISTERED.add(loc)) {
            // ★既に登録済み。ここで DynamicTexture を作ると、その初期化コールバックが キューに…
            closeQuietly(image);
            return null;
        }
        SAMPLING.put(loc,sampling);
        DynamicTexture tex = new DynamicTexture(image);
        if (com.mojang.blaze3d.systems.RenderSystem.isOnRenderThread()) {
            sampling.apply(tex);
            Minecraft.getInstance().getTextureManager().register(loc, tex);
        } else {
            com.mojang.blaze3d.systems.RenderSystem.recordRenderCall(
                () -> { sampling.apply(tex); Minecraft.getInstance().getTextureManager().register(loc, tex); });
        }
        return tex;
    }

    /** 破棄 (リロード等で登録が消えたときの後始末用)。 */
    public static void forget(ResourceLocation loc) {
        if (loc != null) {
            IrisTexturePasses.forget(loc);
            LegacyRenderTypes.forgetTexture(loc);
            REGISTERED.remove(loc);
            SAMPLING.remove(loc);
        }
    }

    private static void closeQuietly(NativeImage image) {
        try {
            image.close();
        } catch (Throwable t) {
            RealTrainModUnofficial.LOGGER.debug("[RTMU] 重複画像の破棄に失敗: {}", t.toString());
        }
    }
}
