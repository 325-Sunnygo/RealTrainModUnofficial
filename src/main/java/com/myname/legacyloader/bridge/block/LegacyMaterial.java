package com.myname.legacyloader.bridge.block;

import net.minecraft.world.level.material.MapColor;

public class LegacyMaterial {
    private final MapColor color;
    private boolean canBurn;
    private boolean replaceable;
    private boolean translucent;
    private boolean requiresTool;
    private boolean adventureModeExempt;
    private int mobilityFlag;

    public LegacyMaterial(MapColor color) {
        this.color = color;
    }

    public LegacyMaterial(LegacyMapColor color) {
        this(MapColor.STONE);
    }

    public LegacyMaterial(Object color) {
        this(MapColor.STONE);
    }

    public LegacyMaterial() {
        this(MapColor.STONE);
    }

    public MapColor getColor() {
        return this.color;
    }

    public MapColor getMaterialMapColor() {
        return this.color;
    }

    public boolean isLiquid() {
        return this == WATER || this == LAVA;
    }

    public boolean isSolid() {
        return !isLiquid() && this != AIR && this != PLANTS && this != VINE && this != FIRE && this != CIRCUITS;
    }

    public boolean getCanBlockGrass() {
        return !this.translucent && isSolid();
    }

    public boolean blocksMovement() {
        return isSolid() && this != PORTAL;
    }

    protected LegacyMaterial setRequiresTool() {
        this.requiresTool = true;
        return this;
    }

    protected LegacyMaterial setBurning() {
        this.canBurn = true;
        return this;
    }

    public boolean getCanBurn() {
        return this.canBurn;
    }

    public LegacyMaterial setReplaceable() {
        this.replaceable = true;
        return this;
    }

    public boolean isReplaceable() {
        return this.replaceable;
    }

    protected LegacyMaterial setTranslucent() {
        this.translucent = true;
        return this;
    }

    public boolean isOpaque() {
        return !this.translucent && getCanBlockGrass();
    }

    public boolean isToolNotRequired() {
        return !this.requiresTool;
    }

    public int getMaterialMobility() {
        return this.mobilityFlag;
    }

    protected LegacyMaterial setNoPushMobility() {
        this.mobilityFlag = 1;
        return this;
    }

    protected LegacyMaterial setImmovableMobility() {
        this.mobilityFlag = 2;
        return this;
    }

    // --- 1.7.10 SRG 名エイリアス (Material のビルダー系。Mod がサブクラス構築時に呼ぶ) ---
    // 例: MaterialBamboo の <init> が func_76219_n() (setNoPushMobility) をチェーン呼び出し。
    public LegacyMaterial func_76219_n() { return setNoPushMobility(); }
    public LegacyMaterial func_76222_j() { return setImmovableMobility(); }

    protected LegacyMaterial setAdventureModeExempt() {
        this.adventureModeExempt = true;
        return this;
    }

    public boolean isAdventureModeExempt() {
        return this.adventureModeExempt;
    }

    // === 荳ｻ隕√↑繝槭ユ繝ｪ繧｢繝ｫ螳夂ｾｩ ===
    public static final LegacyMaterial AIR = new LegacyMaterial(MapColor.NONE);
    public static final LegacyMaterial GRASS = new LegacyMaterial(MapColor.GRASS);
    public static final LegacyMaterial GROUND = new LegacyMaterial(MapColor.DIRT);
    public static final LegacyMaterial WOOD = new LegacyMaterial(MapColor.WOOD);
    public static final LegacyMaterial ROCK = new LegacyMaterial(MapColor.STONE);
    public static final LegacyMaterial IRON = new LegacyMaterial(MapColor.METAL);
    public static final LegacyMaterial ANVIL = new LegacyMaterial(MapColor.METAL);
    public static final LegacyMaterial WATER = new LegacyMaterial(MapColor.WATER);
    public static final LegacyMaterial LAVA = new LegacyMaterial(MapColor.FIRE);
    public static final LegacyMaterial LEAVES = new LegacyMaterial(MapColor.PLANT);
    public static final LegacyMaterial PLANTS = new LegacyMaterial(MapColor.PLANT);
    public static final LegacyMaterial VINE = new LegacyMaterial(MapColor.PLANT);
    public static final LegacyMaterial SPONGE = new LegacyMaterial(MapColor.COLOR_YELLOW);
    public static final LegacyMaterial CLOTH = new LegacyMaterial(MapColor.WOOL);
    public static final LegacyMaterial FIRE = new LegacyMaterial(MapColor.FIRE);
    public static final LegacyMaterial SAND = new LegacyMaterial(MapColor.SAND);
    public static final LegacyMaterial CIRCUITS = new LegacyMaterial(MapColor.NONE);
    public static final LegacyMaterial CARPET = new LegacyMaterial(MapColor.WOOL);
    public static final LegacyMaterial GLASS = new LegacyMaterial(MapColor.NONE);
    public static final LegacyMaterial REDSTONE_LIGHT = new LegacyMaterial(MapColor.NONE);
    public static final LegacyMaterial TNT = new LegacyMaterial(MapColor.FIRE);
    public static final LegacyMaterial CORAL = new LegacyMaterial(MapColor.PLANT);
    public static final LegacyMaterial ICE = new LegacyMaterial(MapColor.ICE);
    public static final LegacyMaterial PACKED_ICE = new LegacyMaterial(MapColor.ICE);
    public static final LegacyMaterial SNOW = new LegacyMaterial(MapColor.SNOW);
    public static final LegacyMaterial CRAFTED_SNOW = new LegacyMaterial(MapColor.SNOW);
    public static final LegacyMaterial CACTUS = new LegacyMaterial(MapColor.PLANT);
    public static final LegacyMaterial CLAY = new LegacyMaterial(MapColor.CLAY);
    public static final LegacyMaterial GOURD = new LegacyMaterial(MapColor.PLANT);
    public static final LegacyMaterial DRAGON_EGG = new LegacyMaterial(MapColor.PLANT);
    public static final LegacyMaterial PORTAL = new LegacyMaterial(MapColor.NONE);
    public static final LegacyMaterial CAKE = new LegacyMaterial(MapColor.NONE);
    public static final LegacyMaterial WEB = new LegacyMaterial(MapColor.WOOL);
    public static final LegacyMaterial PISTON = new LegacyMaterial(MapColor.STONE);

    public static final LegacyMaterial air = AIR;
    public static final LegacyMaterial grass = GRASS;
    public static final LegacyMaterial ground = GROUND;
    public static final LegacyMaterial wood = WOOD;
    public static final LegacyMaterial rock = ROCK;
    public static final LegacyMaterial iron = IRON;
    public static final LegacyMaterial anvil = ANVIL;
    public static final LegacyMaterial water = WATER;
    public static final LegacyMaterial lava = LAVA;
    public static final LegacyMaterial leaves = LEAVES;
    public static final LegacyMaterial plants = PLANTS;
    public static final LegacyMaterial vine = VINE;
    public static final LegacyMaterial sponge = SPONGE;
    public static final LegacyMaterial cloth = CLOTH;
    public static final LegacyMaterial fire = FIRE;
    public static final LegacyMaterial sand = SAND;
    public static final LegacyMaterial circuits = CIRCUITS;
    public static final LegacyMaterial carpet = CARPET;
    public static final LegacyMaterial glass = GLASS;
    public static final LegacyMaterial redstoneLight = REDSTONE_LIGHT;
    public static final LegacyMaterial tnt = TNT;
    public static final LegacyMaterial coral = CORAL;
    public static final LegacyMaterial ice = ICE;
    public static final LegacyMaterial packedIce = PACKED_ICE;
    public static final LegacyMaterial snow = SNOW;
    public static final LegacyMaterial craftedSnow = CRAFTED_SNOW;
    public static final LegacyMaterial cactus = CACTUS;
    public static final LegacyMaterial clay = CLAY;
    public static final LegacyMaterial gourd = GOURD;
    public static final LegacyMaterial dragonEgg = DRAGON_EGG;
    public static final LegacyMaterial portal = PORTAL;
    public static final LegacyMaterial cake = CAKE;
    public static final LegacyMaterial web = WEB;
    public static final LegacyMaterial piston = PISTON;

    // === 笘・・笘・SRG蜷阪お繧､繝ｪ繧｢繧ｹ・亥ｿ・茨ｼ俄・笘・・ ===
    public static final LegacyMaterial field_151579_a = AIR;
    public static final LegacyMaterial field_151577_b = GRASS;
    public static final LegacyMaterial field_151578_c = GROUND;
    public static final LegacyMaterial field_151575_d = WOOD;
    public static final LegacyMaterial field_151576_e = ROCK;       // 笘・％繧後′驥崎ｦ・
    //AsphaltMod は一部を別 SRG マッピング (field_76xxx) でビルドしており、標準の field_151xxx に
    //無い名前で Material を参照する。BlockGuardRail 等が使う field_76233_E を金属(IRON)にマップ。
    public static final LegacyMaterial field_76233_E = IRON;
    public static final LegacyMaterial field_151573_f = IRON;
    public static final LegacyMaterial field_151574_g = ANVIL;
    public static final LegacyMaterial field_151586_h = WATER;
    public static final LegacyMaterial field_151587_i = LAVA;
    public static final LegacyMaterial field_151584_j = LEAVES;
    public static final LegacyMaterial field_151585_k = PLANTS;
    public static final LegacyMaterial field_151582_l = VINE;
    public static final LegacyMaterial field_151583_m = SPONGE;
    public static final LegacyMaterial field_151580_n = CLOTH;
    public static final LegacyMaterial field_151581_o = FIRE;
    public static final LegacyMaterial field_151595_p = SAND;
    public static final LegacyMaterial field_151594_q = CIRCUITS;
    public static final LegacyMaterial field_151568_F = CARPET;
    public static final LegacyMaterial field_151592_s = GLASS;
    public static final LegacyMaterial field_151590_u = REDSTONE_LIGHT;
    public static final LegacyMaterial field_151591_v = TNT;
    public static final LegacyMaterial field_151566_D = CLAY;
    public static final LegacyMaterial field_151598_x = ICE;
    public static final LegacyMaterial field_151596_z = PACKED_ICE;
    public static final LegacyMaterial field_151588_w = SNOW;
    public static final LegacyMaterial field_151589_v = CRAFTED_SNOW;
    public static final LegacyMaterial field_151597_y = CACTUS;
    public static final LegacyMaterial field_151593_t = GOURD;
    public static final LegacyMaterial field_151565_H = DRAGON_EGG;
    public static final LegacyMaterial field_151567_E = PORTAL;
    public static final LegacyMaterial field_151570_A = CAKE;
    public static final LegacyMaterial field_151571_B = WEB;
    public static final LegacyMaterial field_151572_C = PISTON;
}
