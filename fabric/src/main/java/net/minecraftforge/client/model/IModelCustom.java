package net.minecraftforge.client.model;

/**
 * 1.7.10 Forge の {@code net.minecraftforge.client.model.IModelCustom} スタブ。
 *
 * <p>Forge の OBJ/TCN カスタムモデル用インターフェース。1.21 には対応する即時描画系が無いので
 * no-op スタブとして提供する (AsphaltMod の道路標識等が使うが、描画されなくてもブロック登録・
 * 単純テクスチャ描画には影響しない)。{@code net.minecraftforge.*} は 1.21 に存在しないパッケージ
 * なので、ソースとして追加してもモジュール衝突は起きない。
 */
public interface IModelCustom {
    String getType();

    void renderAll();

    void renderOnly(String... groupNames);

    void renderPart(String partName);

    void renderAllExcept(String... excludedGroupNames);
}
