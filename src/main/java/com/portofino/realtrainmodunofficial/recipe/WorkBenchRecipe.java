package com.portofino.realtrainmodunofficial.recipe;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.level.Level;

/**
 * RTM 専用作業台のレシピ。本家 {@code ShapedRecipes55} 相当で、<b>最大 5x5</b>。
 *
 * <p>3x3 に収まる本家レシピは、本家と同じくバニラのレシピ ({@code crafting_shaped}) として
 * 出してあるので普通の作業台でも作れる。ここは 4x4 以上のぶんだけを持つ。
 */
public record WorkBenchRecipe(String group, CraftingBookCategory category,
                              ShapedRecipePattern pattern, ItemStack result) implements CraftingRecipe {

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return this.pattern.matches(input);
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        return this.result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width >= this.pattern.width() && height >= this.pattern.height();
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return this.result;
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        return this.pattern.ingredients();
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return RealTrainModUnofficialRecipes.WORK_BENCH_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return RealTrainModUnofficialRecipes.WORK_BENCH_TYPE.get();
    }

    @Override
    public CraftingBookCategory category() {
        return this.category;
    }

    @Override
    public String getGroup() {
        return this.group;
    }

    public static class Serializer implements RecipeSerializer<WorkBenchRecipe> {
        private static final MapCodec<WorkBenchRecipe> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                com.mojang.serialization.Codec.STRING.optionalFieldOf("group", "")
                    .forGetter(WorkBenchRecipe::group),
                CraftingBookCategory.CODEC.optionalFieldOf("category", CraftingBookCategory.MISC)
                    .forGetter(WorkBenchRecipe::category),
                ShapedRecipePattern.MAP_CODEC.forGetter(WorkBenchRecipe::pattern),
                ItemStack.STRICT_CODEC.fieldOf("result").forGetter(WorkBenchRecipe::result)
            ).apply(instance, WorkBenchRecipe::new));

        private static final StreamCodec<RegistryFriendlyByteBuf, WorkBenchRecipe> STREAM_CODEC =
            StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, WorkBenchRecipe::group,
                CraftingBookCategory.STREAM_CODEC, WorkBenchRecipe::category,
                ShapedRecipePattern.STREAM_CODEC, WorkBenchRecipe::pattern,
                ItemStack.STREAM_CODEC, WorkBenchRecipe::result,
                WorkBenchRecipe::new);

        @Override
        public MapCodec<WorkBenchRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, WorkBenchRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
