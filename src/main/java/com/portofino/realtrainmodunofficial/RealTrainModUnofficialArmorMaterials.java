package com.portofino.realtrainmodunofficial;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;
import java.util.List;

/** 防具の素材。1.21 では ArmorMaterial がレジストリ登録制になったので、 本家が… */
public final class RealTrainModUnofficialArmorMaterials {

    public static final DeferredRegister<ArmorMaterial> ARMOR_MATERIALS =
        DeferredRegister.create(Registries.ARMOR_MATERIAL, RealTrainModUnofficial.MODID);

    /** 暗視装置 (NVD)。本家は super(ArmorMaterial.IRON, 2, Entit… */
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> NVD =
        ARMOR_MATERIALS.register("nvd", () -> new ArmorMaterial(
            defense(2, 5, 6, 2),   // 鉄と同じ
            9,                     // 鉄と同じ enchantmentValue
            SoundEvents.ARMOR_EQUIP_IRON,
            () -> Ingredient.of(Items.IRON_INGOT),
            List.of(new ArmorMaterial.Layer(
                ResourceLocation.fromNamespaceAndPath(RealTrainModUnofficial.MODID, "nvd"))),
            0.0F,
            0.0F));


    /** net.minecraft.world.item.ArmorItem へ渡す Holder。 */
    public static Holder<ArmorMaterial> nvd() {
        return NVD;
    }

    /** 鉄の耐久倍率 (バニラ ArmorMaterials.IRON)。 */
    public static final int NVD_DURABILITY_MULTIPLIER = 15;

    private static EnumMap<ArmorItem.Type, Integer> defense(int helmet, int chestplate, int leggings, int boots) {
        EnumMap<ArmorItem.Type, Integer> map = new EnumMap<>(ArmorItem.Type.class);
        map.put(ArmorItem.Type.HELMET, helmet);
        map.put(ArmorItem.Type.CHESTPLATE, chestplate);
        map.put(ArmorItem.Type.LEGGINGS, leggings);
        map.put(ArmorItem.Type.BOOTS, boots);
        map.put(ArmorItem.Type.BODY, chestplate);
        return map;
    }

    private RealTrainModUnofficialArmorMaterials() {
    }
}
