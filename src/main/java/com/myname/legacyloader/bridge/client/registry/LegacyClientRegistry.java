package com.myname.legacyloader.bridge.client.registry;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LegacyClientRegistry {
    private static final Map<Class<?>, Object> TILE_RENDERERS = new ConcurrentHashMap<>();

    public static void bindTileEntitySpecialRenderer(Class<?> tileEntityClass, Object renderer) {
        if (tileEntityClass != null && renderer != null) {
            TILE_RENDERERS.put(tileEntityClass, renderer);
        }
    }

    // Exact descriptor used by some transformed 1.7.10 mods. Keeping this overload avoids
    // NoSuchMethodError when a descriptor was not fully remapped by ASM.
    public static void bindTileEntitySpecialRenderer(Class<?> tileEntityClass,
            net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer renderer) {
        bindTileEntitySpecialRenderer(tileEntityClass, (Object) renderer);
    }

    // TileEntitySpecialRenderer をブリッジ型へ remap した後の厳密なシグネチャ。
    // これが無いと mod の bindTileEntitySpecialRenderer(Class, LegacyTileEntitySpecialRenderer)
    // 呼び出しが NoSuchMethodError になり preInit ごと失敗 → ブロック未登録になる。
    public static void bindTileEntitySpecialRenderer(Class<?> tileEntityClass,
            com.myname.legacyloader.bridge.client.renderer.tileentity.LegacyTileEntitySpecialRenderer renderer) {
        bindTileEntitySpecialRenderer(tileEntityClass, (Object) renderer);
    }

    public static Object getTileEntitySpecialRenderer(Class<?> tileEntityClass) {
        if (tileEntityClass == null) return null;
        Class<?> c = tileEntityClass;
        while (c != null) {
            Object renderer = TILE_RENDERERS.get(c);
            if (renderer != null) return renderer;
            c = c.getSuperclass();
        }
        return null;
    }

    public static void registerKeyBinding(Object keyBinding) {}

    //1.7.10 mod は KeyBinding 型で呼ぶ (bytecode の descriptor が Object 版と一致しないため
    //オーバーロードが必要)。1.21 のキー入力には接続しないが、登録が例外にならないようにする。
    public static void registerKeyBinding(net.minecraft.client.settings.KeyBinding keyBinding) {}
}
