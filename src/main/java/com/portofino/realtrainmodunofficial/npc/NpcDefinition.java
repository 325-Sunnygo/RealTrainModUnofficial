package com.portofino.realtrainmodunofficial.npc;

import java.util.Map;

/**
 * NPC のパック定義 ({@code ModelNPC_*.json})。
 *
 * <p>本家には<b>2 つの書式</b>がある:
 * <ul>
 *   <li>{@code texture} だけ … バニラの人型モデルにその絵を貼る (PassengerNGT01 など)</li>
 *   <li>{@code model: { modelFile, textures }} … MQO モデル (CatMaid など)</li>
 * </ul>
 */
public class NpcDefinition {
    private final String id;
    private final String packName;
    private final String buttonTexture;
    private final String role;
    /** バニラ人型に貼る絵。MQO の場合は空。 */
    private final String skinTexture;
    /** MQO のモデル。バニラ人型の場合は空。 */
    private final String modelFile;
    private final Map<String, String> textureOverrides;

    public NpcDefinition(String id, String packName, String buttonTexture, String role,
                         String skinTexture, String modelFile, Map<String, String> textureOverrides) {
        this.id = id;
        this.packName = packName;
        this.buttonTexture = buttonTexture == null ? "" : buttonTexture;
        this.role = role == null ? "" : role;
        this.skinTexture = skinTexture == null ? "" : skinTexture;
        this.modelFile = modelFile == null ? "" : modelFile;
        this.textureOverrides = textureOverrides == null ? Map.of() : Map.copyOf(textureOverrides);
    }

    public String getId() { return id; }
    public String getPackName() { return packName; }
    public String getButtonTexture() { return buttonTexture; }
    public String getRole() { return role; }
    public String getSkinTexture() { return skinTexture; }
    public String getModelFile() { return modelFile; }
    public Map<String, String> getTextureOverrides() { return textureOverrides; }
    public boolean hasModel() { return !this.modelFile.isBlank(); }
}
