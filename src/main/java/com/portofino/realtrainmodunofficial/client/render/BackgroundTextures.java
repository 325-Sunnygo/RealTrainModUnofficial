package com.portofino.realtrainmodunofficial.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 背景パネルの画像。<b>クライアント専用</b>。
 *
 * <p>ブロックが持っている PNG のバイト列をテクスチャにする。
 * <b>中身のハッシュで覚える</b>ので、同じ画像を何枚並べてもテクスチャは 1 つで済み、
 * 画像を差し替えたときは自動で作り直される。
 */
public final class BackgroundTextures {

    public record Loaded(ResourceLocation location, int width, int height) {
    }

    private static final Map<Integer, Loaded> READY = new HashMap<>();
    private static final Set<Integer> FAILED = new HashSet<>();

    private BackgroundTextures() {
    }

    /** バイト列からテクスチャを得る。読めなければ null。 */
    public static Loaded get(byte[] data) {
        if (data == null || data.length == 0) {
            return null;
        }
        int key = java.util.Arrays.hashCode(data);
        Loaded ready = READY.get(key);
        if (ready != null) {
            return ready;
        }
        if (FAILED.contains(key)) {
            return null;
        }
        try {
            NativeImage image = NativeImage.read(new java.io.ByteArrayInputStream(data));
            ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(
                RealTrainModUnofficial.MODID, "background/" + Integer.toHexString(key));
            DynamicTexture tex = new DynamicTexture(image);
            Minecraft.getInstance().getTextureManager().register(loc, tex);
            //写真なので拡大時ににじませた方が自然
            tex.setFilter(true, false);
            Loaded l = new Loaded(loc, image.getWidth(), image.getHeight());
            READY.put(key, l);
            return l;
        } catch (Exception e) {
            FAILED.add(key);
            //★黙って諦めない。「保存したのに出ない」の原因がここでしか分からない
            RealTrainModUnofficial.LOGGER.warn("[背景パネル] 画像を読めません ({} バイト): {}",
                data.length, e.toString());
            return null;
        }
    }

    public static void clear() {
        for (Loaded l : READY.values()) {
            Minecraft.getInstance().getTextureManager().release(l.location());
        }
        READY.clear();
        FAILED.clear();
    }
}
