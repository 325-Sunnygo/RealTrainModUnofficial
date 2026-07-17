package com.myname.legacyloader.bridge.item;

import net.minecraft.world.item.ItemStack; // 霑ｽ蜉
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Tier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.crafting.Ingredient;

public class LegacyTier implements Tier {

    // 1.7.10 Item.ToolMaterial の標準定数 (harvestLevel, maxUses, efficiency, damage, enchantability)。
    // Mod が LegacyTier.IRON のように参照する (例: Bamboo の ItemKatana)。
    public static final LegacyTier WOOD = new LegacyTier(0, 59, 2.0F, 0.0F, 15);
    public static final LegacyTier STONE = new LegacyTier(1, 131, 4.0F, 1.0F, 5);
    public static final LegacyTier IRON = new LegacyTier(2, 250, 6.0F, 2.0F, 14);
    public static final LegacyTier EMERALD = new LegacyTier(3, 1561, 8.0F, 3.0F, 10); // 1.7.10 のダイヤ相当
    public static final LegacyTier DIAMOND = EMERALD;
    public static final LegacyTier GOLD = new LegacyTier(0, 32, 12.0F, 0.0F, 22);

    private final int level;
    private final int uses;
    private final float speed;
    private final float damage;
    private final int enchantmentValue;
    // 笘・ｿｮ豁｣: 蠕後°繧牙､画峩縺吶ｋ縺溘ａ final 繧貞､悶☆
    private Ingredient repairIngredient;

    public LegacyTier(int level, int uses, float speed, float damage, int enchantmentValue) {
        this.level = level;
        this.uses = uses;
        this.speed = speed;
        this.damage = damage;
        this.enchantmentValue = enchantmentValue;
        this.repairIngredient = Ingredient.EMPTY;
    }

    // 1.20.1 Tier 縺ｮ螳溯｣・
    @Override public int getUses() { return uses; }
    @Override public float getSpeed() { return speed; }
    @Override public float getAttackDamageBonus() { return damage; }
    @Override public TagKey<Block> getIncorrectBlocksForDrops() { return BlockTags.INCORRECT_FOR_WOODEN_TOOL; }
    @Override public int getEnchantmentValue() { return enchantmentValue; }
    @Override public Ingredient getRepairIngredient() { return repairIngredient; }

    // 1.7.10 Mod莠呈鋤繝｡繧ｽ繝・ラ
    public int getHarvestLevel() { return level; }
    public int getMaxUses() { return uses; }
    public float getEfficiencyOnProperMaterial() { return speed; }
    public float getDamageVsEntity() { return damage; }
    public int getEnchantability() { return enchantmentValue; }

    // 笘・ｿｽ蜉: 菫ｮ逅・ｴ譚舌ｒ險ｭ螳壹☆繧九Γ繧ｽ繝・ラ (莉雁屓縺ｮ繧ｨ繝ｩ繝ｼ菫ｮ豁｣)
    public LegacyTier setRepairItem(ItemStack stack) {
        if (stack != null) {
            this.repairIngredient = Ingredient.of(stack);
        }
        return this;
    }

    // 逍台ｼｼEnum繝｡繧ｽ繝・ラ
    public String name() { return "LEGACY_TIER"; }
    public int ordinal() { return 0; }
}