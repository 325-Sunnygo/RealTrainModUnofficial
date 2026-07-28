package jp.ngt.mccompat;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * IBlockState 互換シム。
 * 信号機のブロック検知スクリプトが、色ガラス等の判定にこの形を使う:
 */
public final class BlockStateCompat {

    private final BlockState state;

    public BlockStateCompat(BlockState state) {
        this.state = state;
    }

    /** 生の 1.21 BlockState (Java 側から使う)。 */
    public BlockState vanilla() {
        return this.state;
    }

    /** func_177230_c = getBlock。色別ブロックは白色版へ正規化する (base+meta 照合のため)。 */
    public Block func_177230_c() {
        return this.state == null ? null : jp.ngt.mccompat.init.Blocks.canonical(this.state.getBlock());
    }

    public Block getBlock() {
        return func_177230_c();
    }

    /**
     * func_177228_b = getProperties。
     * 本家は ImmutableMap<IProperty, Comparable> を返し、スクリプトは
     * .values.toArray[0].func_176765_a で先頭プロパティのメタを読む。
     */
    public Map<String, MetaValue> func_177228_b() {
        Map<String, MetaValue> map = new LinkedHashMap<>();
        map.put("meta", new MetaValue(
                this.state == null ? 0 : jp.ngt.mccompat.init.Blocks.colorMeta(this.state.getBlock())));
        return map;
    }

    public Map<String, MetaValue> getProperties() {
        return func_177228_b();
    }

    /** func_177230_c で取った Block と直接比較されることがあるため equals も通す。 */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o instanceof BlockStateCompat other) {
            return java.util.Objects.equals(this.state, other.state);
        }
        return java.util.Objects.equals(this.state, o);
    }

    @Override
    public int hashCode() {
        return this.state == null ? 0 : this.state.hashCode();
    }

    @Override
    public String toString() {
        return String.valueOf(this.state);
    }

    /** プロパティ値 1 個ぶん。func_176765_a でメタ (0-15) を返す。 */
    public static final class MetaValue implements Comparable<MetaValue> {
        private final int meta;

        MetaValue(int meta) {
            this.meta = meta;
        }

        /** func_176765_a = EnumDyeColor.getMetadata 相当。 */
        public int func_176765_a() {
            return this.meta;
        }

        public int getMetadata() {
            return this.meta;
        }

        @Override
        public int compareTo(MetaValue o) {
            return Integer.compare(this.meta, o.meta);
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof MetaValue v && v.meta == this.meta;
        }

        @Override
        public int hashCode() {
            return this.meta;
        }

        @Override
        public String toString() {
            return String.valueOf(this.meta);
        }
    }

    /** 値だけの Collection が欲しい経路向け。 */
    public Collection<MetaValue> values() {
        return func_177228_b().values();
    }
}
