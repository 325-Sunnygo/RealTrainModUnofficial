package com.portofino.realtrainmodunofficial.cargo;

import net.minecraft.world.phys.Vec3;

import java.util.Map;

/**
 * 貨物 (コンテナ / 火砲) のパック定義。
 *
 * <p>本家 {@code ContainerConfig} / {@code FirearmConfig} の必要な所だけを 1 つにまとめたもの。
 * どちらも {@code ModelContainer_*.json} / {@code ModelFirearm_*.json} から読む。
 */
public class CargoDefinition {

    public enum Kind {
        /** 本家 RTMResource.CONTAINER (item_cargo メタ 0)。 */
        CONTAINER,
        /** 本家 RTMResource.FIREARM (item_cargo メタ 1)。 */
        FIREARM
    }

    private final String id;
    private final String displayName;
    private final String packName;
    private final Kind kind;
    private final String modelFile;
    private final Map<String, String> textureOverrides;
    private final String buttonTexture;
    private final boolean doCulling;

    // --- コンテナ ---
    private final float containerWidth;
    private final float containerHeight;
    private final float containerLength;

    // --- 火砲 ---
    private final Vec3 muzzlePos;
    private final Vec3 playerPos;
    private final float[] yawRange;
    private final float[] pitchRange;
    private final String bulletType;
    /** 本家 modelPartsN/Y/X/Barrel の pos。砲口の位置を出すのに要る。 */
    private Vec3 partsPosN = Vec3.ZERO;
    private Vec3 partsPosY = Vec3.ZERO;
    private Vec3 partsPosX = Vec3.ZERO;
    private Vec3 partsPosBarrel = Vec3.ZERO;
    private int magazineSize = 1;

    public CargoDefinition(String id, String displayName, String packName, Kind kind,
                           String modelFile, Map<String, String> textureOverrides,
                           String buttonTexture, boolean doCulling,
                           float containerWidth, float containerHeight, float containerLength,
                           Vec3 muzzlePos, Vec3 playerPos, float[] yawRange, float[] pitchRange,
                           String bulletType) {
        this.id = id;
        this.displayName = displayName;
        this.packName = packName;
        this.kind = kind;
        this.modelFile = modelFile;
        this.textureOverrides = textureOverrides == null ? Map.of() : Map.copyOf(textureOverrides);
        this.buttonTexture = buttonTexture == null ? "" : buttonTexture;
        this.doCulling = doCulling;
        this.containerWidth = containerWidth;
        this.containerHeight = containerHeight;
        this.containerLength = containerLength;
        this.muzzlePos = muzzlePos == null ? Vec3.ZERO : muzzlePos;
        this.playerPos = playerPos == null ? Vec3.ZERO : playerPos;
        this.yawRange = yawRange == null ? new float[]{-180.0F, 180.0F} : yawRange;
        this.pitchRange = pitchRange == null ? new float[]{-30.0F, 60.0F} : pitchRange;
        this.bulletType = bulletType == null ? "" : bulletType;
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getPackName() { return packName; }
    public Kind getKind() { return kind; }
    public String getModelFile() { return modelFile; }
    public Map<String, String> getTextureOverrides() { return textureOverrides; }
    public String getButtonTexture() { return buttonTexture; }
    public boolean isDoCulling() { return doCulling; }
    public float getContainerWidth() { return containerWidth; }
    public float getContainerHeight() { return containerHeight; }
    public float getContainerLength() { return containerLength; }
    public Vec3 getMuzzlePos() { return muzzlePos; }
    public Vec3 getPlayerPos() { return playerPos; }
    public float[] getYawRange() { return yawRange; }
    public float[] getPitchRange() { return pitchRange; }
    public String getBulletType() { return bulletType; }
    public Vec3 getPartsPosN() { return partsPosN; }
    public Vec3 getPartsPosY() { return partsPosY; }
    public Vec3 getPartsPosX() { return partsPosX; }
    public Vec3 getPartsPosBarrel() { return partsPosBarrel; }
    public int getMagazineSize() { return magazineSize; }

    public void setFirearmParts(Vec3 n, Vec3 y, Vec3 x, Vec3 barrel, int magazineSize) {
        this.partsPosN = n == null ? Vec3.ZERO : n;
        this.partsPosY = y == null ? Vec3.ZERO : y;
        this.partsPosX = x == null ? Vec3.ZERO : x;
        this.partsPosBarrel = barrel == null ? Vec3.ZERO : barrel;
        this.magazineSize = Math.max(1, magazineSize);
    }
}
