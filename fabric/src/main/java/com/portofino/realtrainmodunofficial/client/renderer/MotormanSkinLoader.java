package com.portofino.realtrainmodunofficial.client.renderer;

import com.mojang.blaze3d.platform.NativeImage;
import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.fml.loading.FMLPaths;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 運転士のカスタムスキン ({@code config/realtrainmodunofficial/npc_skins/*.png}) の読み込み。
 * プレイヤースキン形式 (64x64)。旧 64x32 形式は読み込み時に 64x64 へ変換する。
 * 読み込んだテクスチャは名前でキャッシュ (1 ファイル 1 回だけロード = 軽量)。
 */
@OnlyIn(Dist.CLIENT)
public final class MotormanSkinLoader {

    private static final Map<String, ResourceLocation> CACHE = new ConcurrentHashMap<>();
    private static final ResourceLocation MISSING = ResourceLocation.fromNamespaceAndPath("rtm", "textures/motorman.png");

    private MotormanSkinLoader() {
    }

    public static Path skinFolder() {
        return FMLPaths.GAMEDIR.get().resolve("config").resolve("realtrainmodunofficial").resolve("npc_skins");
    }

    /** 選択可能なカスタムスキンのファイル名一覧。 */
    public static List<String> listSkins() {
        List<String> names = new ArrayList<>();
        try {
            Path folder = skinFolder();
            Files.createDirectories(folder);
            try (var stream = Files.list(folder)) {
                stream.filter(Files::isRegularFile)
                        .map(p -> p.getFileName().toString())
                        .filter(n -> n.toLowerCase(Locale.ROOT).endsWith(".png"))
                        .sorted()
                        .forEach(names::add);
            }
        } catch (Exception ignored) {
        }
        return names;
    }

    /** ファイル名 → 登録済みテクスチャ。読めなければ既定スキン。 */
    public static ResourceLocation getOrLoad(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return MISSING;
        }
        return CACHE.computeIfAbsent(fileName, MotormanSkinLoader::load);
    }

    private static ResourceLocation load(String fileName) {
        try {
            Path file = skinFolder().resolve(fileName);
            if (!Files.isRegularFile(file)) {
                return MISSING;
            }
            NativeImage img;
            try (InputStream in = Files.newInputStream(file)) {
                img = NativeImage.read(in);
            }
            //旧 64x32 スキンは 64x64 キャンバスへ載せ替え (旧 img は close、新 img は
            //DynamicTexture が所有して close する)
            if (img.getHeight() == img.getWidth() / 2) {
                NativeImage expanded = new NativeImage(img.getWidth(), img.getWidth(), true);
                for (int x = 0; x < img.getWidth(); x++) {
                    for (int y = 0; y < img.getHeight(); y++) {
                        expanded.setPixelRGBA(x, y, img.getPixelRGBA(x, y));
                    }
                }
                img.close();
                img = expanded;
            }
            //旧レイアウト補正: 左脚/左腕領域が空なら右のミラーコピーで埋める
            //(RTM 時代のスキンは 64x64 でも下半分が透明。プレイヤーモデルは左手足を
            // (16,48)/(32,48) から読むため、空のままだと左腕・左脚が消える)
            int s = img.getWidth() / 64;
            if (regionEmpty(img, 16, 48, 16, 16, s)) {
                mirrorCopy(img, 0, 16, 16, 48, 16, 16, s);   //右脚 → 左脚位置 (左右反転)
            }
            if (regionEmpty(img, 32, 48, 16, 16, s)) {
                mirrorCopy(img, 40, 16, 32, 48, 16, 16, s);  //右腕 → 左腕位置 (左右反転)
            }
            String key = "motorman_skin/" + fileName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
            ResourceLocation rl = ResourceLocation.fromNamespaceAndPath(RealTrainModUnofficial.MODID, key);
            Minecraft.getInstance().getTextureManager().register(rl, new DynamicTexture(img));
            return rl;
        } catch (Exception e) {
            RealTrainModUnofficial.LOGGER.warn("[Motorman] skin load failed {}: {}", fileName, e.toString());
            return MISSING;
        }
    }

    /** 領域が完全に透明か (アルファ 0 のみ)。 */
    private static boolean regionEmpty(NativeImage img, int x0, int y0, int w, int h, int scale) {
        for (int x = 0; x < w * scale; x++) {
            for (int y = 0; y < h * scale; y++) {
                if ((img.getPixelRGBA(x0 * scale + x, y0 * scale + y) >>> 24) != 0) {
                    return false;
                }
            }
        }
        return true;
    }

    /** 左右反転してコピー (旧モデルの mirror サンプリングと同じ見た目になる)。 */
    private static void mirrorCopy(NativeImage img, int sx, int sy, int dx, int dy, int w, int h, int scale) {
        int ws = w * scale;
        for (int x = 0; x < ws; x++) {
            for (int y = 0; y < h * scale; y++) {
                img.setPixelRGBA(dx * scale + x, dy * scale + y,
                        img.getPixelRGBA(sx * scale + (ws - 1 - x), sy * scale + y));
            }
        }
    }
}
