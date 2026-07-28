package com.portofino.realtrainmodunofficial.rail;

import net.minecraft.world.phys.Vec3;

import java.util.Map;

public class RailDefinition {
    private final String id;
    private final String displayName;
    private final String packName;
    private final String packResourcePath;
    private final String modelFile;
    private final String scriptPath;
    private final String buttonTexture;
    private final Map<String, String> textureOverrides;
    private final Vec3 modelOffset;
    private final float modelScale;
    private final int ballastWidth;
    /** 道床に敷くブロックID (例: "minecraft:gravel")。空なら道床無し。 */
    private final String ballastBlockId;

    public RailDefinition(String id, String displayName, String packName, String packResourcePath,
                          String modelFile, String scriptPath, String buttonTexture, Map<String, String> textureOverrides,
                          Vec3 modelOffset, float modelScale, int ballastWidth) {
        this(id, displayName, packName, packResourcePath, modelFile, scriptPath, buttonTexture,
            textureOverrides, modelOffset, modelScale, ballastWidth, "");
    }

    public RailDefinition(String id, String displayName, String packName, String packResourcePath,
                          String modelFile, String scriptPath, String buttonTexture, Map<String, String> textureOverrides,
                          Vec3 modelOffset, float modelScale, int ballastWidth, String ballastBlockId) {
        this.id = id;
        this.displayName = displayName;
        this.packName = packName;
        this.packResourcePath = packResourcePath;
        this.modelFile = modelFile;
        this.scriptPath = scriptPath;
        this.buttonTexture = buttonTexture == null ? "" : buttonTexture;
        this.textureOverrides = textureOverrides == null ? Map.of() : Map.copyOf(textureOverrides);
        this.modelOffset = modelOffset == null ? Vec3.ZERO : modelOffset;
        this.modelScale = modelScale <= 0 ? 1.0F : modelScale;
        this.ballastWidth = Math.max(0, ballastWidth);
        this.ballastBlockId = ballastBlockId == null ? "" : ballastBlockId;
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getPackName() { return packName; }
    public String getPackResourcePath() { return packResourcePath; }
    public String getModelFile() { return modelFile; }
    public String getScriptPath() { return scriptPath; }
    public String getButtonTexture() { return buttonTexture; }
    public Map<String, String> getTextureOverrides() { return textureOverrides; }
    public Vec3 getModelOffset() { return modelOffset; }
    public float getModelScale() { return modelScale; }
    public int getBallastWidth() { return ballastWidth; }
    public String getBallastBlockId() { return ballastBlockId; }
}
