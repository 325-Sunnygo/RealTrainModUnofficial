package net.minecraftforge.client.model;

/**
 * 1.7.10 Forge の net.minecraftforge.client.model.IModelCustom スタブ。
 * Forge の OBJ/TCN カスタムモデル用インターフェース。
 */
public interface IModelCustom {
    String getType();

    void renderAll();

    void renderOnly(String... groupNames);

    void renderPart(String partName);

    void renderAllExcept(String... excludedGroupNames);
}
