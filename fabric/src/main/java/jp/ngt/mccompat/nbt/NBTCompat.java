package jp.ngt.mccompat.nbt;

import net.minecraft.nbt.CompoundTag;

/**
 * {@link NBTTagCompound} と同じ SRG メソッドの静的版。レシーバを引数で受ける。
 *
 * <p>スクリプトが触る NBT は、いつもシムとは限らない。
 * 例えば {@code jp.ngt.ngtlib.block.BlockSet.nbt} は Java 側の都合で実バニラの
 * {@link CompoundTag} で、これには 1.7.10 の SRG 名が無い。
 * 実バニラ型は継承もラップもできないので、
 * {@code PackScriptSource} が {@code <式>.func_74737_b(...)} を
 * {@code NBTCompat.func_74737_b(<式>, ...)} へ書き換えて、ここへ流す。
 *
 * <p>これが無いと NGTO Builder2 の setTileEntityData が
 * 「nbt.func_74737_b is not a function」で落ち、
 * パック側が例外を捨てるため、ポールだけ置かれて架線のモデルも接続先も書かれなかった。
 */
public final class NBTCompat {

    private NBTCompat() {
    }

    /** シムでも実バニラでも受ける。null は空タグ扱い (スクリプト側の null 判定は呼ぶ前に済んでいる)。 */
    private static NBTTagCompound of(Object nbt) {
        if (nbt instanceof NBTTagCompound c) {
            return c;
        }
        return new NBTTagCompound(NBTTagCompound.unwrap(nbt));
    }

    /** func_74737_b = copy。戻り値はシムなので、書き換えが届かない呼び出しでも動く。 */
    public static NBTTagCompound func_74737_b(Object nbt) {
        return of(nbt).func_74737_b();
    }

    /** func_74760_g = getFloat */
    public static float func_74760_g(Object nbt, String key) {
        return of(nbt).func_74760_g(key);
    }

    /** func_74776_a = setFloat */
    public static void func_74776_a(Object nbt, String key, float value) {
        of(nbt).func_74776_a(key, value);
    }

    /** func_74764_b = hasKey */
    public static boolean func_74764_b(Object nbt, String key) {
        return of(nbt).func_74764_b(key);
    }

    /** func_74775_l = getCompoundTag */
    public static NBTTagCompound func_74775_l(Object nbt, String key) {
        return of(nbt).func_74775_l(key);
    }

    /** func_74782_a = setTag */
    public static void func_74782_a(Object nbt, String key, Object value) {
        of(nbt).func_74782_a(key, value);
    }

    /** func_74778_a = setString */
    public static void func_74778_a(Object nbt, String key, String value) {
        of(nbt).func_74778_a(key, value);
    }

    /** func_74779_i = getString */
    public static String func_74779_i(Object nbt, String key) {
        return of(nbt).func_74779_i(key);
    }

    /** func_74768_a = setInteger */
    public static void func_74768_a(Object nbt, String key, int value) {
        of(nbt).func_74768_a(key, value);
    }

    /** func_74762_e = getInteger */
    public static int func_74762_e(Object nbt, String key) {
        return of(nbt).func_74762_e(key);
    }

    /** func_74757_a = setBoolean */
    public static void func_74757_a(Object nbt, String key, boolean value) {
        of(nbt).func_74757_a(key, value);
    }

    /** func_74767_n = getBoolean */
    public static boolean func_74767_n(Object nbt, String key) {
        return of(nbt).func_74767_n(key);
    }

    /** func_74780_a = setDouble */
    public static void func_74780_a(Object nbt, String key, double value) {
        of(nbt).func_74780_a(key, value);
    }

    /** func_74769_h = getDouble */
    public static double func_74769_h(Object nbt, String key) {
        return of(nbt).func_74769_h(key);
    }

    /** func_150295_c = getTagList(key, type) */
    public static NBTTagList func_150295_c(Object nbt, String key, int type) {
        return of(nbt).func_150295_c(key, type);
    }
}
