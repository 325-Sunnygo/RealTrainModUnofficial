package net.minecraftforge.client.model;

import net.minecraft.resources.ResourceLocation;

/**
 * 1.7.10 Forge の net.minecraftforge.client.model.AdvancedModelLoader スタブ。
 * 本家は OBJ/TCN モデルを読み込んで IModelCustom を返す。
 */
public final class AdvancedModelLoader {

    private AdvancedModelLoader() {
    }

    public static IModelCustom loadModel(ResourceLocation resource) {
        return NOOP;
    }

    public static void registerModelHandler(Object modelHandler) {
        // 本家は拡張子ごとの IModelCustomLoader を登録する。1.21 では不要。
    }

    private static final IModelCustom NOOP = new IModelCustom() {
        @Override public String getType() { return "stub"; }
        @Override public void renderAll() { }
        @Override public void renderOnly(String... groupNames) { }
        @Override public void renderPart(String partName) { }
        @Override public void renderAllExcept(String... excludedGroupNames) { }
    };
}
