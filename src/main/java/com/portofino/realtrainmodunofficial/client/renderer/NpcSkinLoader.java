package com.portofino.realtrainmodunofficial.client.renderer;

import com.mojang.blaze3d.platform.NativeImage;
import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import com.portofino.realtrainmodunofficial.client.PackButtonTextureCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.io.InputStream;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * NPC のスキンを 1.21 の人型モデルで使える形にして返す。
 *
 * <p>本家 (1.7.10/1.12) の {@code ModelBiped} は 64x32 の並びで、左右の腕・脚が
 * <b>同じテクスチャ領域</b>を共有していた。1.21 の人型モデルは 1.8 以降の 64x64 の並びで
 * 左腕・左脚に専用の領域を使うため、本家パックのスキンをそのまま貼ると
 * <b>片方の腕と脚が消える</b> (その領域が透明のまま)。
 *
 * <p>そこでバニラの旧スキン変換 ({@code SkinTextureDownloader.processLegacySkin}) と同じ
 * コピーを行い、右腕・右脚を左側の領域へ複製する。64x32 の物は 64x64 へ広げてから同じ処理。
 */
public final class NpcSkinLoader {

    private static final Map<String, ResourceLocation> CACHE = new ConcurrentHashMap<>();

    private NpcSkinLoader() {
    }

    /**
     * @param packName 定義が属するパック名 (同梱なら実質未使用)
     * @param path     "textures/npc/xxx.png" のような素のパス
     * @param fallback 変換できないときに返す location
     */
    public static ResourceLocation getOrConvert(String packName, String path, ResourceLocation fallback) {
        if (path == null || path.isBlank()) {
            return fallback;
        }
        String key = packName + "|" + path;
        ResourceLocation cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        ResourceLocation result = convert(packName, path, fallback);
        CACHE.put(key, result);
        return result;
    }

    private static ResourceLocation convert(String packName, String path, ResourceLocation fallback) {
        NativeImage image = readImage(packName, path);
        if (image == null) {
            return fallback;
        }
        try {
            if (image.getWidth() != 64 || (image.getHeight() != 32 && image.getHeight() != 64)) {
                //人型スキンの形をしていない (MQO 用のテクスチャ等) — そのまま使う
                image.close();
                return fallback;
            }
            NativeImage out = image;
            if (image.getHeight() == 32) {
                //バニラ processLegacySkin: 64x64 へ広げて下半分を空にする
                out = new NativeImage(64, 64, true);
                out.copyFrom(image);
                image.close();
                out.fillRect(0, 32, 64, 32, 0);
                copyLegacyLimbs(out);
            } else if (isLeftLimbsEmpty(image)) {
                //64x64 だが 1.8 以降の左腕・左脚が空 = 旧スキンを貼り替えただけの物
                copyLegacyLimbs(out);
            } else {
                out.close();
                return fallback;   //そのままで正しく描ける
            }
            ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(RealTrainModUnofficial.MODID,
                "dynamic/npc_skin/" + sanitize(packName) + "/" + sanitize(path));
            Minecraft.getInstance().getTextureManager().register(loc, new DynamicTexture(out));
            RealTrainModUnofficial.LOGGER.debug("[RTMU] NPC スキンを 1.8 形式へ変換: {}", path);
            return loc;
        } catch (Exception e) {
            RealTrainModUnofficial.LOGGER.warn("[RTMU] NPC スキンを変換できません: {}", path, e);
            return fallback;
        }
    }

    /**
     * バニラ processLegacySkin と同じコピー (右脚→左脚、右腕→左腕)。
     * ★<b>全 14 回</b>。前半 8 回が脚 (→x16-32,y48-64)、<b>後半 6 回が腕</b> (→x32-48,y48-64)。
     * 腕の 6 回を書き忘れると「片腕だけ消える」。
     */
    private static void copyLegacyLimbs(NativeImage image) {
        //脚: 右脚 (0-16,16-32) → 左脚 (16-32,48-64)
        image.copyRect(4, 16, 16, 32, 4, 4, true, false);
        image.copyRect(8, 16, 16, 32, 4, 4, true, false);
        image.copyRect(0, 20, 24, 32, 4, 12, true, false);
        image.copyRect(4, 20, 16, 32, 4, 12, true, false);
        image.copyRect(8, 20, 8, 32, 4, 12, true, false);
        image.copyRect(12, 20, 16, 32, 4, 12, true, false);
        image.copyRect(16, 20, 8, 32, 4, 12, true, false);
        image.copyRect(20, 20, 8, 32, 4, 12, true, false);
        //腕: 右腕 (40-56,16-32) → 左腕 (32-48,48-64)
        image.copyRect(44, 16, -8, 32, 4, 4, true, false);
        image.copyRect(48, 16, -8, 32, 4, 4, true, false);
        image.copyRect(40, 20, 0, 32, 4, 12, true, false);
        image.copyRect(44, 20, -8, 32, 4, 12, true, false);
        image.copyRect(48, 20, -16, 32, 4, 12, true, false);
        image.copyRect(52, 20, -8, 32, 4, 12, true, false);
    }

    /** 1.8 以降の左腕 (36,52) / 左脚 (20,52) の領域が全部透明か。 */
    private static boolean isLeftLimbsEmpty(NativeImage image) {
        return isTransparent(image, 20, 52, 4, 12) && isTransparent(image, 36, 52, 4, 12);
    }

    private static boolean isTransparent(NativeImage image, int x0, int y0, int w, int h) {
        for (int x = x0; x < x0 + w; ++x) {
            for (int y = y0; y < y0 + h; ++y) {
                if ((image.getPixelRGBA(x, y) >>> 24) != 0) {
                    return false;
                }
            }
        }
        return true;
    }

    private static NativeImage readImage(String packName, String path) {
        //同梱アセット
        try {
            ResourceLocation loc = ResourceLocation.withDefaultNamespace(path.toLowerCase(Locale.ROOT));
            var res = Minecraft.getInstance().getResourceManager().getResource(loc);
            if (res.isPresent()) {
                try (InputStream in = res.get().open()) {
                    return NativeImage.read(in);
                }
            }
        } catch (Exception ignored) {
        }
        //外部パック
        try {
            return PackButtonTextureCache.readImage(packName, path);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String sanitize(String s) {
        return s == null ? "none" : s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_./-]", "_");
    }
}
