package com.myname.legacyloader.bridge.client.model;

/**
 * 1.7.10 の {@code net.minecraftforge.client.model.IModelCustom} の代役。
 * TESR が {@code model.renderAll()} を呼ぶと OBJ 三角形が現行バッファへ描かれる
 * (実装は {@link LegacyWavefrontObject})。
 */
public interface LegacyModelCustom {

    String getType();

    void renderAll();

    void renderOnly(String... groupNames);

    void renderPart(String partName);

    // SRG/難読名エイリアス (念のため)
    default void func_78229_a() {
        renderAll();
    }
}
