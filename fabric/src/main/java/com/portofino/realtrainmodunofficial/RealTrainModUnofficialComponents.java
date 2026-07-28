package com.portofino.realtrainmodunofficial;

import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * DataComponentsを追加するクラス<br>
 * DataComponentsは、1.20.5よりNBTタグの代替としてItemStackに導入された状態管理手段。<br>
 * 今後のアップデートでアイテムだけでなくNBTを使用するあらゆる要素に拡大していくと予測されており、<br>
 * これからはNBTタグではなくこちらを利用することが推奨されている。
 */
public class RealTrainModUnofficialComponents {
    public static final DeferredRegister.DataComponents REGISTRAR = DeferredRegister.createDataComponents(
        Registries.DATA_COMPONENT_TYPE,
        RealTrainModUnofficial.MODID
    );

    /**
     * 列車・レールアイテムで選択中のモデルID
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> SELECTED_MODEL_ID
        = REGISTRAR.registerComponentType(
        "selected_model_id",
        builder -> builder.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8)
    );

    /**
     * モデル選択画面で指定した datamap 引数
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> SELECTED_MODEL_DATA_MAP
        = REGISTRAR.registerComponentType(
        "selected_model_data_map",
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
}
