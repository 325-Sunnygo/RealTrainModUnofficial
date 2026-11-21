package com.portofino.realtrainmodunofficial;

import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * DataComponentsを追加するクラス
 * DataComponentsは、1.20.5よりNBTタグの代替としてItemStackに導入された状態管理手段。
 * 今後のアップデートでアイテムだけでなくNBTを使用するあらゆる要素に拡大していくと予測されており、
 * これからはNBTタグではなくこちらを利用することが推奨されている。
 */
public class RealTrainModUnofficialComponents {
    public static final DeferredRegister.DataComponents REGISTRAR = DeferredRegister.createDataComponents(
        Registries.DATA_COMPONENT_TYPE,
        RealTrainModUnofficial.MODID
    );

    /** 列車・レールアイテムで選択中のモデルID */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> SELECTED_MODEL_ID
        = REGISTRAR.registerComponentType(
        "selected_model_id",
        builder -> builder.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8)
    );

    /** モデル選択画面で指定した datamap 引数 */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> SELECTED_MODEL_DATA_MAP
        = REGISTRAR.registerComponentType(
        "selected_model_data_map",
        builder -> builder.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8)
    );

    /**
     * レールアイテムに焼き込まれた道床ブロック ("minecraft:gravel" など)。
     *
     * <p>本家と同じで、アイテムごとに道床が決まっている。
     * 敷いたあとレールをいじっても道床は変わらない。
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> SELECTED_BALLAST
        = REGISTRAR.registerComponentType(
        "selected_ballast",
        builder -> builder.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8)
    );

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CompoundTag>> RAIL_PREVIEW_START
        = REGISTRAR.registerComponentType(
        "rail_preview_start",
        builder -> builder.persistent(CompoundTag.CODEC).networkSynchronized(ByteBufCodecs.COMPOUND_TAG)
    );

    /**
     * 1.20.5+ DataComponent: TRAIN_FORMATION
     * Stores train formation data including vehicle IDs and formation name
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CompoundTag>> TRAIN_FORMATION
        = REGISTRAR.registerComponentType(
        "train_formation",
        builder -> builder.persistent(CompoundTag.CODEC).networkSynchronized(ByteBufCodecs.COMPOUND_TAG)
    );

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CompoundTag>> WIRE_PLACEMENT_START
        = REGISTRAR.registerComponentType(
        "wire_placement_start",
        builder -> builder.persistent(CompoundTag.CODEC).networkSynchronized(ByteBufCodecs.COMPOUND_TAG)
    );

    /**
     * 弾薬 / 紙幣の種別。
     *
     * <p>本家 (1.7.10/1.12.2) はアイテムの<b>メタ</b>で種類を分けているが 1.21 にメタが無い。
     * 値は本家のメタと同じ (弾薬なら {@code BulletType.id * 4 + 0..2}、紙幣なら {@code MoneyType.id})
     * にしてあるので、本家の計算式をそのまま移植できる。
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> ITEM_VARIANT
        = REGISTRAR.registerComponentType(
        "item_variant",
        builder -> builder.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT)
    );
    /**
     * 液体入りバケツの中身 (本家の NBT {@code type} / {@code temperture} をそのまま持つ)。
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CompoundTag>> FLUID_DATA
        = REGISTRAR.registerComponentType(
        "fluid_data",
        builder -> builder.persistent(CompoundTag.CODEC).networkSynchronized(ByteBufCodecs.COMPOUND_TAG)
    );
    /** 貨物アイテムが持つ中身 (選んだモデル id / コンテナの中身 / 装填状態)。 */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CompoundTag>> CARGO_DATA
        = REGISTRAR.registerComponentType(
        "cargo_data",
        builder -> builder.persistent(CompoundTag.CODEC).networkSynchronized(ByteBufCodecs.COMPOUND_TAG)
    );
    /** 装飾ブロックアイテムが持つモデル名 (本家は NBT "ModelName")。 */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> DECORATION_MODEL
        = REGISTRAR.registerComponentType(
        "decoration_model",
        builder -> builder.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8)
    );
}