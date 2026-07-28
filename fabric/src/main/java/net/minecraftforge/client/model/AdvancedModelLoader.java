package net.minecraftforge.client.model;

import net.minecraft.resources.ResourceLocation;

/**
 * 1.7.10 Forge の {@code net.minecraftforge.client.model.AdvancedModelLoader} スタブ。
 *
 * <p>本家は OBJ/TCN モデルを読み込んで {@link IModelCustom} を返す。1.21 には無いので、
 * 何も描かない no-op の {@link IModelCustom} を返す。これが無いと (合成される空クラスに
 * loadModel が無く) AsphaltMod の ClientProxy.preInit が {@code NoSuchMethodError} で落ち、
 * <b>ブロック登録に到達せずクライアントで 0 ブロック</b>になる。単純テクスチャの建材ブロックは
 * この no-op でも問題なく登録・描画される。
 */
public final class AdvancedModelLoader {

    private AdvancedModelLoader() {
    }

    public static IModelCustom loadModel(ResourceLocation resource) {
        return NOOP;
    }

    public static void registerModelHandler(Object modelHandler) {
        //本家は拡張子ごとの IModelCustomLoader を登録する。1.21 では不要。
    }

    private static final IModelCustom NOOP = new IModelCustom() {
        @Override public String getType() { return "stub"; }
        @Override public void renderAll() { }
        @Override public void renderOnly(String... groupNames) { }
        @Override public void renderPart(String partName) { }
        @Override public void renderAllExcept(String... excludedGroupNames) { }
    };
}
