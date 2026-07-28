package jp.ngt.mcte.item;

import jp.ngt.ngtlib.block.NGTObject;
import net.minecraft.nbt.CompoundTag;

/**
 * MCTE {@code jp.ngt.mcte.item.ItemMiniature} の static API 移植 (neo mcte)。
 *
 * <p>ミニチュアの中身と設定を格納/取得する<b>唯一の窓口</b>。実アイテムは
 * {@code com.portofino.realtrainmodunofficial.item.MiniatureItem}、
 * 設置済みは {@code MiniatureBlockEntity} が、どちらもここを通す。
 *
 * <h2>★データは ItemStack 自身が持つ (最重要)</h2>
 * MCTEU は中身を {@code miniatureId} という文字列だけスタックに持たせ、実体は
 * その ID をキーにした外部テーブル ({@code PlacedMiniatureManager} /
 * {@code MiniatureWorldData}) に置いていた。そのうえで ID の再割り当てを
 * <pre>if (!newMiniatureId.isEmpty() &amp;&amp; !tag.contains("miniatureId")) { ... }</pre>
 * (MCTEU {@code MiniatureNetwork.java:206} / {@code MiniatureSettingsScreen.java:135})
 * と「既に ID があれば振り直さない」ようにしていたため、NGTO を選び直すと
 * <b>ID は据え置きのままテーブル側が上書き</b>され、同じ ID を共有する
 * インベントリ内の全ミニチュアの見た目が一緒に変わっていた。
 *
 * <p>neo mcte は MCTE 本家と同じく {@code BlocksData} を<b>スタックの NBT に直接</b>持つ。
 * スタックごとに独立した実体を持つので、この波及は<b>原理的に起こらない</b>。
 *
 * <h2>NBT 契約 (本家 MCTE と同一)</h2>
 * <ul>
 *   <li>{@code BlocksData} — {@link NGTObject} 本体</li>
 *   <li>{@code Scale} (float) — 縮尺。旧 {@code MinimizeRate} (int) も読む</li>
 *   <li>{@code OffsetX/OffsetY/OffsetZ} (float) — 表示位置の微調整</li>
 *   <li>{@code Mode} (byte) — {@link MiniatureMode}</li>
 *   <li>{@code MBState} — 設置時のブロック状態 (明るさ等)。旧 {@code LightValue} も読む</li>
 * </ul>
 */
public final class ItemMiniature {

    public static final String KEY_BLOCKS = "BlocksData";
    public static final String KEY_SCALE = "Scale";
    public static final String KEY_SCALE_LEGACY = "MinimizeRate";
    public static final String KEY_OFFSET_X = "OffsetX";
    public static final String KEY_OFFSET_Y = "OffsetY";
    public static final String KEY_OFFSET_Z = "OffsetZ";
    public static final String KEY_MODE = "Mode";
    public static final String KEY_MB_STATE = "MBState";
    public static final String KEY_LIGHT_LEGACY = "LightValue";

    private ItemMiniature() {
    }

    /**
     * 本家 {@code MiniatureMode}。設置したときの振る舞い。
     * <p>宣言順が id になる (本家は {@code nextId++})。NBT に byte で入るので順序を変えないこと。
     */
    public enum MiniatureMode {
        /** 縮小した模型として 1 ブロックに収める。 */
        MINIATURE,
        /** 模型だが原寸。彫刻扱い。 */
        SCULPTURE,
        /** 実ブロックとして展開する (ミニチュアではなくなる)。 */
        ORIGINAL;

        public int id() {
            return ordinal();
        }

        public static MiniatureMode byId(int id) {
            MiniatureMode[] v = values();
            return id >= 0 && id < v.length ? v[id] : MINIATURE;
        }
    }

    // ---- BlocksData ----

    /**
     * NBT から {@link NGTObject} を復元。無ければ null。
     * <p>ラッパー NBT ({@code jp.ngt.mccompat.nbt.NBTTagCompound}) と実 {@link CompoundTag} の両対応。
     * NGTO Builder 等のパックスクリプトは {@code item.func_77978_p()} をそのまま渡してくる。
     */
    public static NGTObject getNGTObject(Object nbtLike) {
        CompoundTag tag = unwrap(nbtLike);
        if (tag == null) {
            return null;
        }
        //旧 RTMU 実装は BlocksData の中身をタグ直下へ展開していた。両方受ける。
        CompoundTag data = tag.contains(KEY_BLOCKS) ? tag.getCompound(KEY_BLOCKS) : tag;
        if (!data.contains("Blocks") && !data.contains("SizeX")) {
            return null;
        }
        try {
            return NGTObject.readFromNBT(data);
        } catch (Exception e) {
            jp.ngt.ngtlib.io.NGTLog.debug("[ItemMiniature] NGTObject の読み込みに失敗: " + e);
            return null;
        }
    }

    /** 本家 {@code setNGTObject}。{@code BlocksData} として<b>そのスタックの NBT に</b>書く。 */
    public static void setNGTObject(NGTObject obj, Object nbtLike) {
        CompoundTag tag = unwrap(nbtLike);
        if (tag == null || obj == null) {
            return;
        }
        tag.put(KEY_BLOCKS, obj.writeToNBT());
    }

    public static boolean hasNGTObject(Object nbtLike) {
        CompoundTag tag = unwrap(nbtLike);
        if (tag == null) {
            return false;
        }
        return tag.contains(KEY_BLOCKS) || tag.contains("SizeX");
    }

    // ---- Scale ----

    /** 本家 {@code getScale}。旧 {@code MinimizeRate}(整数の分母) も読む。 */
    public static float getScale(Object nbtLike) {
        CompoundTag tag = unwrap(nbtLike);
        if (tag == null) {
            return 1.0F;
        }
        if (tag.contains(KEY_SCALE)) {
            return tag.getFloat(KEY_SCALE);
        }
        if (tag.contains(KEY_SCALE_LEGACY)) {
            int i = tag.getInt(KEY_SCALE_LEGACY);
            return 1.0F / (float) Math.max(1, i);
        }
        return 1.0F;
    }

    public static void setScale(float scale, Object nbtLike) {
        CompoundTag tag = unwrap(nbtLike);
        if (tag != null) {
            tag.putFloat(KEY_SCALE, scale);
        }
    }

    // ---- Offset ----

    /**
     * 本家 {@code getOffset}。
     * <p>★本家は {@code private static float[] offset} を使い回して返していたが、
     * 呼び出し側が保持すると別のミニチュアの値に書き換わる。ここでは毎回新しい配列を返す。
     */
    public static float[] getOffset(Object nbtLike) {
        CompoundTag tag = unwrap(nbtLike);
        if (tag == null) {
            return new float[3];
        }
        return new float[]{
            tag.getFloat(KEY_OFFSET_X),
            tag.getFloat(KEY_OFFSET_Y),
            tag.getFloat(KEY_OFFSET_Z)
        };
    }

    public static void setOffset(Object nbtLike, float x, float y, float z) {
        CompoundTag tag = unwrap(nbtLike);
        if (tag != null) {
            tag.putFloat(KEY_OFFSET_X, x);
            tag.putFloat(KEY_OFFSET_Y, y);
            tag.putFloat(KEY_OFFSET_Z, z);
        }
    }

    // ---- Mode ----

    public static MiniatureMode getMode(Object nbtLike) {
        CompoundTag tag = unwrap(nbtLike);
        return tag == null ? MiniatureMode.MINIATURE : MiniatureMode.byId(tag.getByte(KEY_MODE));
    }

    public static void setMode(Object nbtLike, MiniatureMode mode) {
        CompoundTag tag = unwrap(nbtLike);
        if (tag != null && mode != null) {
            tag.putByte(KEY_MODE, (byte) mode.id());
        }
    }

    // ---- 設置時のブロック状態 ----

    /** 本家 {@code MBState.lightValue} 相当。旧 {@code LightValue} も読む。 */
    public static int getLightValue(Object nbtLike) {
        CompoundTag tag = unwrap(nbtLike);
        if (tag == null) {
            return 0;
        }
        if (tag.contains(KEY_MB_STATE)) {
            return tag.getCompound(KEY_MB_STATE).getByte(KEY_LIGHT_LEGACY);
        }
        return tag.getByte(KEY_LIGHT_LEGACY);
    }

    public static void setLightValue(Object nbtLike, int light) {
        CompoundTag tag = unwrap(nbtLike);
        if (tag == null) {
            return;
        }
        CompoundTag mb = tag.contains(KEY_MB_STATE) ? tag.getCompound(KEY_MB_STATE) : new CompoundTag();
        mb.putByte(KEY_LIGHT_LEGACY, (byte) Math.max(0, Math.min(15, light)));
        tag.put(KEY_MB_STATE, mb);
    }

    private static CompoundTag unwrap(Object nbtLike) {
        if (nbtLike instanceof CompoundTag tag) {
            return tag;
        }
        return jp.ngt.mccompat.nbt.NBTTagCompound.unwrap(nbtLike);
    }
}
