package com.portofino.realtrainmodunofficial.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** RTM 車両のクライアント描画設定。 RTM.cfg とは別ファイル (`config/realt… */
public final class VehicleVisualSettings {
    // ---- detail toggles ----
    public static boolean renderInterior = true;
    public static boolean renderLights = true;
    public static boolean renderRollsigns = true;
    public static boolean renderBogies = true;
    public static boolean renderScripts = true;
    public static boolean renderShadows = true;
    public static boolean renderAlpha = true;
    public static boolean renderSeats = true;
    public static boolean renderParticles = true;
    public static boolean renderRailDetails = true;
    public static boolean renderWireDetails = true;
    public static boolean renderInstalledObjectDetails = true;
    public static boolean renderMarkerDetails = true;

    // ---- distances ----
    public static int vehicleRenderDistance = 192;
    public static int vehicleInteriorDistance = 64;
    public static int vehicleLightDistance = 96;
    public static int vehicleRollsignDistance = 64;
    public static int vehicleBogieDistance = 128;

    // ---- rain ----
    public static boolean rainEnabled = true;
    public static int rainDensity = 60;
    public static float rainSize = 0.08F;
    public static int rainDistance = 64;
    public static float rainOpacity = 0.8F;
    public static float rainSpeed = 1.0F;
    public static int rainMaxDrops = 300;
    public static float rainMinLevel = 0.01F;
    public static float rainRoofRatio = 0.35F;
    public static float rainSideRatio = 0.65F;
    public static float rainHighlight = 0.35F;
    public static float rainStreak = 1.4F;
    public static float rainWind = 0.0F;
    public static float rainColorR = 0.85F;
    public static float rainColorG = 0.95F;
    public static float rainColorB = 1.0F;
    /** 0: 自動 / 1: 常に自前レイヤー / 2: シェーダー使用時だけ自前レイヤー */
    public static boolean rainSplash = true;
    public static boolean rainDoubleSided = true;
    public static boolean rainHideInsideVehicle = true;
    public static float rainReflection = 0.45F;
    public static float rainRefraction = 0.25F;
    public static float rainGloss = 0.8F;
    public static float rainNormal = 0.5F;
    public static boolean rainBloom = true;
    public static boolean rainHdr = true;
    public static boolean rainSoftParticles = true;
    public static float rainDepthFade = 0.25F;
    public static float rainNearFade = 0.0F;
    public static float rainFarFade = 0.0F;
    public static boolean rainTemporalSmoothing = true;
    public static boolean rainScreenSpace = true;
    public static int rainTextureResolution = 512;
    public static float rainMipBias = 0.0F;
    public static float rainAnisotropy = 4.0F;
    public static int rainSplashCount = 3;
    public static float rainTurbulence = 0.5F;
    public static boolean rainCameraMotion = true;
    public static float rainLightResponse = 0.6F;
    // ---- follow / lod ----
    public static int smoothFollowDelayMs = 100;
    public static int modelLod = 1;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static boolean loaded;

    private VehicleVisualSettings() {
    }

    private static Path path() {
        return FMLPaths.CONFIGDIR.get().resolve("realtrainmodunofficial").resolve("vehicle_visual.json");
    }

    public static synchronized void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        Path path = path();
        if (!Files.isRegularFile(path)) {
            save();
            return;
        }
        try {
            Data data = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), Data.class);
            if (data != null) {
                data.apply();
            }
        } catch (IOException | JsonSyntaxException ignored) {
        }
    }

    public static synchronized void save() {
        Path path = path();
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(new Data()), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    public static void reset() {
        Data data = new Data();
        data.apply();
        save();
    }

    /** JSON 用の入れ物。フィールド名をそのまま設定キーにする。 */
    public static final class Data {
        public boolean renderInterior = true;
        public boolean renderLights = true;
        public boolean renderRollsigns = true;
        public boolean renderBogies = true;
        public boolean renderScripts = true;
        public boolean renderShadows = true;
        public boolean renderAlpha = true;
        public boolean renderSeats = true;
        public boolean renderParticles = true;
        public boolean renderRailDetails = true;
        public boolean renderWireDetails = true;
        public boolean renderInstalledObjectDetails = true;
        public boolean renderMarkerDetails = true;
        public int vehicleRenderDistance = 192;
        public int vehicleInteriorDistance = 64;
        public int vehicleLightDistance = 96;
        public int vehicleRollsignDistance = 64;
        public int vehicleBogieDistance = 128;
        public boolean rainEnabled = true;
        public int rainDensity = 60;
        public float rainSize = 0.08F;
        public int rainDistance = 64;
        public float rainOpacity = 0.8F;
        public float rainSpeed = 1.0F;
        public int rainMaxDrops = 300;
        public float rainMinLevel = 0.01F;
        public float rainRoofRatio = 0.35F;
        public float rainSideRatio = 0.65F;
        public float rainHighlight = 0.35F;
        public float rainStreak = 1.4F;
        public float rainWind = 0.0F;
        public float rainColorR = 0.85F;
        public float rainColorG = 0.95F;
        public float rainColorB = 1.0F;
        public boolean rainSplash = true;
        public boolean rainDoubleSided = true;
        public boolean rainHideInsideVehicle = true;
        public float rainReflection = 0.45F;
        public float rainRefraction = 0.25F;
        public float rainGloss = 0.8F;
        public float rainNormal = 0.5F;
        public boolean rainBloom = true;
        public boolean rainHdr = true;
        public boolean rainSoftParticles = true;
        public float rainDepthFade = 0.25F;
        public float rainNearFade = 0.0F;
        public float rainFarFade = 0.0F;
        public boolean rainTemporalSmoothing = true;
        public boolean rainScreenSpace = true;
        public int rainTextureResolution = 512;
        public float rainMipBias = 0.0F;
        public float rainAnisotropy = 4.0F;
        public int rainSplashCount = 3;
        public float rainTurbulence = 0.5F;
        public boolean rainCameraMotion = true;
        public float rainLightResponse = 0.6F;
        public int smoothFollowDelayMs = 100;
        public int modelLod = 1;

        void apply() {
            renderInterior = this.renderInterior;
            renderLights = this.renderLights;
            renderRollsigns = this.renderRollsigns;
            renderBogies = this.renderBogies;
            renderScripts = this.renderScripts;
            renderShadows = this.renderShadows;
            renderAlpha = this.renderAlpha;
            renderSeats = this.renderSeats;
            renderParticles = this.renderParticles;
            renderRailDetails = this.renderRailDetails;
            renderWireDetails = this.renderWireDetails;
            renderInstalledObjectDetails = this.renderInstalledObjectDetails;
            renderMarkerDetails = this.renderMarkerDetails;
            vehicleRenderDistance = this.vehicleRenderDistance;
            vehicleInteriorDistance = this.vehicleInteriorDistance;
            vehicleLightDistance = this.vehicleLightDistance;
            vehicleRollsignDistance = this.vehicleRollsignDistance;
            vehicleBogieDistance = this.vehicleBogieDistance;
            rainEnabled = this.rainEnabled;
            rainDensity = this.rainDensity;
            rainSize = this.rainSize;
            rainDistance = this.rainDistance;
            rainOpacity = this.rainOpacity;
            rainSpeed = this.rainSpeed;
            rainMaxDrops = this.rainMaxDrops;
            rainMinLevel = this.rainMinLevel;
            rainRoofRatio = this.rainRoofRatio;
            rainSideRatio = this.rainSideRatio;
            rainHighlight = this.rainHighlight;
            rainStreak = this.rainStreak;
            rainWind = this.rainWind;
            rainColorR = this.rainColorR;
            rainColorG = this.rainColorG;
            rainColorB = this.rainColorB;
            rainSplash = this.rainSplash;
            rainDoubleSided = this.rainDoubleSided;
            rainHideInsideVehicle = this.rainHideInsideVehicle;
            rainReflection = this.rainReflection;
            rainRefraction = this.rainRefraction;
            rainGloss = this.rainGloss;
            rainNormal = this.rainNormal;
            rainBloom = this.rainBloom;
            rainHdr = this.rainHdr;
            rainSoftParticles = this.rainSoftParticles;
            rainDepthFade = this.rainDepthFade;
            rainNearFade = this.rainNearFade;
            rainFarFade = this.rainFarFade;
            rainTemporalSmoothing = this.rainTemporalSmoothing;
            rainScreenSpace = this.rainScreenSpace;
            rainTextureResolution = this.rainTextureResolution;
            rainMipBias = this.rainMipBias;
            rainAnisotropy = this.rainAnisotropy;
            rainSplashCount = this.rainSplashCount;
            rainTurbulence = this.rainTurbulence;
            rainCameraMotion = this.rainCameraMotion;
            rainLightResponse = this.rainLightResponse;
            smoothFollowDelayMs = this.smoothFollowDelayMs;
            modelLod = this.modelLod;
        }
    }
}