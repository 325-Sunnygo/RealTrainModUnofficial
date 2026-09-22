package com.portofino.realtrainmodunofficial.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 動的テクスチャ (NativeImage から作る DynamicTexture) の登録を 1 箇所に集約する。
 *
 * <p><b>なぜ必要か</b>: {@code TextureManager.register} は<b>同じ ResourceLocation を二重登録すると
 * 先に登録されていたテクスチャを close する</b>。{@code DynamicTexture} のコンストラクタは
 * 初期化 ({@code TextureUtil.prepareImage} + {@code upload}) を {@code RenderSystem.recordRenderCall}
 * で描画スレッドへ回すため、そのコールバックが走る前に close されると
 * {@code this.pixels.getWidth()} が NPE になり、クライアントがクラッシュする。
 *
 * <p>★重要: 重複を検出して「作ってから閉じる」のでは遅い。コンストラクタの時点で
 * 初期化コールバックがキューに積まれており、閉じるとそのコールバックが NPE で落ちる。
 * したがって <b>登録済みかどうかを DynamicTexture を作る前に判定</b>し、
 * 重複なら NativeImage を閉じて何も作らない。
 *
 * <p>本家 (KaizPatchX) も動的テクスチャは 1 回だけ登録して以後は再利用するので、
 * これが本家と同じ仕様。
 */
public final class DynamicTextureRegistry {

    /** 登録済み loc。二重登録 (= TextureManager による先行 close) を防ぐ。 */
    private static final Set<ResourceLocation> REGISTERED = ConcurrentHashMap.newKeySet();

    private DynamicTextureRegistry() {
    }

    /**
     * NativeImage から DynamicTexture を作って登録する。
     *
     * @return 登録したテクスチャ (フィルタ設定などに使う)。既に登録済みなら null を返し、
     *         渡された NativeImage はここで閉じる (呼び出し側は何もしなくてよい)。
     */
    public static DynamicTexture register(ResourceLocation loc, NativeImage image) {
        if (loc == null || image == null) {
            return null;
        }
        if (!REGISTERED.add(loc)) {
            // ★既に登録済み。ここで DynamicTexture を作ると、その初期化コールバックが
            //   キューに積まれたあと TextureManager が先行テクスチャを close するため、
            //   コールバックが pixels=null で NPE になる。作らずに画像だけ閉じる。
            closeQuietly(image);
            return null;
        }
        DynamicTexture tex = new DynamicTexture(image);
        if (com.mojang.blaze3d.systems.RenderSystem.isOnRenderThread()) {
            Minecraft.getInstance().getTextureManager().register(loc, tex);
        } else {
            com.mojang.blaze3d.systems.RenderSystem.recordRenderCall(
                () -> Minecraft.getInstance().getTextureManager().register(loc, tex));
        }
        return tex;
    }

    /** 破棄 (リロード等で登録が消えたときの後始末用)。 */
    public static void forget(ResourceLocation loc) {
        if (loc != null) {
            REGISTERED.remove(loc);
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
