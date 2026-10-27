package com.portofino.realtrainmodunofficial.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * RTM 専用作業台 (5x5) のレシピ形状。
 *
 * <p>★バニラの {@link net.minecraft.world.item.crafting.ShapedRecipePattern} は
 * <b>3x3 を超えると解析時に例外を投げる</b> ("Invalid pattern: too many rows, 3 is maximum")。
 * そのまま使うと 4x4 以上のレシピが 1 つも読み込まれず、
 * サーバー起動ログに大量の {@code Parsing error loading recipe} が出る。
 * ここは本家 {@code ShapedRecipes55} と同じ 5x5 まで許す独自実装。
 *
 * <p>json の書式はバニラと同じ ({@code pattern} + {@code key})。
 */
public record WorkBenchPattern(int width, int height, NonNullList<Ingredient> ingredients) {

    /** 本家 RTM の作業台は 5x5。 */
    public static final int MAX_WIDTH = 5;
    public static final int MAX_HEIGHT = 5;

    /** json の生データ (pattern の行と key の対応)。 */
    private record Raw(List<String> pattern, Map<String, Ingredient> key) {
        private static final MapCodec<Raw> MAP_CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                Codec.STRING.listOf().fieldOf("pattern").forGetter(Raw::pattern),
                Codec.unboundedMap(
                        Codec.STRING.comapFlatMap(
                            s -> s.length() != 1
                                ? DataResult.error(() -> "Invalid key entry: '" + s + "' is not a single character")
                                : DataResult.success(s),
                            s -> s),
                        Ingredient.CODEC_NONEMPTY)
                    .fieldOf("key").forGetter(Raw::key)
            ).apply(instance, Raw::new));
    }

    public static final MapCodec<WorkBenchPattern> MAP_CODEC =
        Raw.MAP_CODEC.flatXmap(WorkBenchPattern::unpack, WorkBenchPattern::pack);

    public static final StreamCodec<RegistryFriendlyByteBuf, WorkBenchPattern> STREAM_CODEC =
        StreamCodec.of(WorkBenchPattern::toNetwork, WorkBenchPattern::fromNetwork);

    private static DataResult<WorkBenchPattern> unpack(Raw raw) {
        List<String> rows = raw.pattern();
        if (rows.isEmpty()) {
            return DataResult.error(() -> "Invalid pattern: empty pattern not allowed");
        }
        if (rows.size() > MAX_HEIGHT) {
            return DataResult.error(() -> "Invalid pattern: too many rows, " + MAX_HEIGHT + " is maximum");
        }
        int width = rows.get(0).length();
        for (String row : rows) {
            if (row.length() > MAX_WIDTH) {
                return DataResult.error(() -> "Invalid pattern: too many columns, " + MAX_WIDTH + " is maximum");
            }
            if (row.length() != width) {
                return DataResult.error(() -> "Invalid pattern: each row must be the same width");
            }
        }
        int height = rows.size();
        NonNullList<Ingredient> ingredients = NonNullList.withSize(width * height, Ingredient.EMPTY);
        for (int y = 0; y < height; ++y) {
            String row = rows.get(y);
            for (int x = 0; x < width; ++x) {
                char c = row.charAt(x);
                if (c == ' ') {
                    continue;
                }
                Ingredient ingredient = raw.key().get(String.valueOf(c));
                if (ingredient == null) {
                    return DataResult.error(() -> "Pattern references symbol '" + c + "' but it's not defined in the key");
                }
                ingredients.set(x + y * width, ingredient);
            }
        }
        return DataResult.success(new WorkBenchPattern(width, height, ingredients));
    }

    /** データ生成用。RTMU は json を手で持っているので、書き出しは使わない。 */
    private static DataResult<Raw> pack(WorkBenchPattern pattern) {
        return DataResult.error(() -> "WorkBenchPattern の書き出しには未対応");
    }

    /**
     * 盤面に置かれた材料と一致するか。バニラの shaped と同じで<b>ずらして</b>照合し、
     * 左右反転も見る (本家 RTM の作業台も鏡像を許す)。
     */
    public boolean matches(CraftingInput input) {
        if (input.width() < this.width || input.height() < this.height) {
            return false;
        }
        for (int x = 0; x <= input.width() - this.width; ++x) {
            for (int y = 0; y <= input.height() - this.height; ++y) {
                if (this.matchesAt(input, x, y, true) || this.matchesAt(input, x, y, false)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean matchesAt(CraftingInput input, int offsetX, int offsetY, boolean mirrored) {
        for (int x = 0; x < input.width(); ++x) {
            for (int y = 0; y < input.height(); ++y) {
                int px = x - offsetX;
                int py = y - offsetY;
                Ingredient ingredient = Ingredient.EMPTY;
                if (px >= 0 && py >= 0 && px < this.width && py < this.height) {
                    int index = mirrored ? (this.width - px - 1) + py * this.width : px + py * this.width;
                    ingredient = this.ingredients.get(index);
                }
                if (!ingredient.test(input.getItem(x, y))) {
                    return false;
                }
            }
        }
        return true;
    }

    private static void toNetwork(RegistryFriendlyByteBuf buf, WorkBenchPattern pattern) {
        buf.writeVarInt(pattern.width);
        buf.writeVarInt(pattern.height);
        for (Ingredient ingredient : pattern.ingredients) {
            Ingredient.CONTENTS_STREAM_CODEC.encode(buf, ingredient);
        }
    }

    private static WorkBenchPattern fromNetwork(RegistryFriendlyByteBuf buf) {
        int width = buf.readVarInt();
        int height = buf.readVarInt();
        NonNullList<Ingredient> ingredients = NonNullList.withSize(width * height, Ingredient.EMPTY);
        ingredients.replaceAll(ignored -> Ingredient.CONTENTS_STREAM_CODEC.decode(buf));
        return new WorkBenchPattern(width, height, ingredients);
    }

    /** 空でない材料だけを取り出す (レシピ本 / JEI 用)。 */
    public List<Ingredient> nonEmptyIngredients() {
        List<Ingredient> out = new ArrayList<>();
        for (Ingredient ingredient : this.ingredients) {
            if (!ingredient.isEmpty()) {
                out.add(ingredient);
            }
        }
        return out;
    }

    public Optional<Ingredient> at(int x, int y) {
        if (x < 0 || y < 0 || x >= this.width || y >= this.height) {
            return Optional.empty();
        }
        return Optional.of(this.ingredients.get(x + y * this.width));
    }
}
