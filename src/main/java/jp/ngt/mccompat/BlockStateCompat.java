package jp.ngt.mccompat;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * {@code IBlockState} 互換シム。
 *
 * <p>信号機のブロック検知スクリプトが、色ガラス等の判定にこの形を使う:
 * <pre>
 * // 600VsSignalpack_v2.2.zip!assets/minecraft/scripts/MSS_SigBlock/renderMSS_SigCol_glass.js:247-255
 * var iBlockState = world.func_180495_p(searchBlockPos);
 * var block     = iBlockState.func_177230_c();                        // getBlock()
 * var metaArray = iBlockState.func_177228_b().values().toArray();     // getProperties()
 * var meta      = metaArray[0].func_176765_a();                       // → メタ値
 * </pre>
 *
 * <p>1.21 にメタは無く色ごとに別ブロックなので、既存の
 * {@link jp.ngt.mccompat.init.Blocks#canonical}（色別 → 白色版へ正規化）と
 * {@link jp.ngt.mccompat.init.Blocks#colorMeta}（色番号 0-15）へ委譲して
 * 1.7.10 の「1 ブロック + メタで 16 色」を再現する。
 * これは {@code func_147439_a} / {@code func_72805_g} が既に採っている方式と同じで、
 * 1.12 形式の getBlockState 経路だけが繋がっていなかった。
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
     * 本家は {@code ImmutableMap<IProperty, Comparable>} を返し、スクリプトは
     * {@code .values().toArray()[0].func_176765_a()} で先頭プロパティのメタを読む。
     * ここでは 1.7.10 のメタに相当する値 1 個だけを持つマップを返す。
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

    /** プロパティ値 1 個ぶん。{@code func_176765_a} でメタ (0-15) を返す。 */
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
