package jp.ngt.mccompat;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biomes;

/**
 * 1.7.10 {@code net.minecraft.world.biome.BiomeGenBase} のスクリプト互換。
 * NGTO Builder2 の Snowfall ブラシが
 * {@code BiomeGenBase.field_76774_n.field_76756_M} (= icePlains.biomeID) と
 * {@code BiomeGenBase.field_76772_c.field_76756_M} (= plains.biomeID) を読む。
 *
 * <p>1.21 のバイオームは int ID を持たないため、1.7.10 の固定 ID
 * (plains=1, icePlains=12) を返す。ChunkCompat のバイオーム配列もこの ID を使う。
 */
public final class BiomeGenBase {
    private BiomeGenBase() {
    }

    /** plains (1.7.10 biomeID=1) */
    public static final LegacyBiome field_76772_c = new LegacyBiome(1);
    /** icePlains (1.7.10 biomeID=12) */
    public static final LegacyBiome field_76774_n = new LegacyBiome(12);

    /** 1.7.10 BiomeGenBase のスクリプト互換 (field_76756_M = biomeID)。 */
    public static final class LegacyBiome {
        /** biomeID */
        public final int field_76756_M;

        LegacyBiome(int id) {
            this.field_76756_M = id;
        }
    }

    /** 1.21 のバイオーム → 1.7.10 の biomeID。未知は plains(1)。 */
    public static int idOf(Holder<net.minecraft.world.level.biome.Biome> biome) {
        if (biome == null) {
            return 1;
        }
        ResourceKey<net.minecraft.world.level.biome.Biome> key = biome.unwrapKey().orElse(null);
        if (Biomes.SNOWY_PLAINS.equals(key)) {
            return 12;
        }
        return 1;
    }

    /** 1.7.10 の biomeID → 1.21 のバイオーム。未知は plains。 */
    public static ResourceKey<net.minecraft.world.level.biome.Biome> keyOf(int id) {
        return id == 12 ? Biomes.SNOWY_PLAINS : Biomes.PLAINS;
    }
}
