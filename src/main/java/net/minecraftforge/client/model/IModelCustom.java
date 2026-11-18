package net.minecraftforge.client.model;

/** 1.7.10 Forge の net.minecraftforge.client.model.IM… */
public interface IModelCustom {
    String getType();

    void renderAll();

    void renderOnly(String... groupNames);

    void renderPart(String partName);

    void renderAllExcept(String... excludedGroupNames);
}
