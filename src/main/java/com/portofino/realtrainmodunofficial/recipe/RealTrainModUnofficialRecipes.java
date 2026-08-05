package com.portofino.realtrainmodunofficial.recipe;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** RTM 専用作業台 (5x5) のレシピ種別。 */
public final class RealTrainModUnofficialRecipes {

    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES =
        DeferredRegister.create(Registries.RECIPE_TYPE, RealTrainModUnofficial.MODID);
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
        DeferredRegister.create(Registries.RECIPE_SERIALIZER, RealTrainModUnofficial.MODID);

    public static final DeferredHolder<RecipeType<?>, RecipeType<WorkBenchRecipe>> WORK_BENCH_TYPE =
        //★RecipeType.simple(...) は NeoForge 追加で Fabric に無い。
        //  中身は toString だけ返す無名クラスなので、そのまま書く。
        RECIPE_TYPES.register("work_bench", () -> new RecipeType<WorkBenchRecipe>() {
            @Override
            public String toString() {
                return RealTrainModUnofficial.MODID + ":work_bench";
            }
        });

    public static final DeferredHolder<RecipeSerializer<?>, WorkBenchRecipe.Serializer> WORK_BENCH_SERIALIZER =
        RECIPE_SERIALIZERS.register("work_bench", WorkBenchRecipe.Serializer::new);

    private RealTrainModUnofficialRecipes() {
    }
}
