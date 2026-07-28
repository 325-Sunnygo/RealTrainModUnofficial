package jp.ngt.mccompat.nbt;

import net.minecraft.nbt.CompoundTag;

/** 1.7.10/1.12 NBTTagCompound のスクリプト互換ラッパー (SRG メソッド名)。 */
public final class NBTTagCompound {
    public final CompoundTag tag;

    public NBTTagCompound() {
        this(new CompoundTag());
    }

    public NBTTagCompound(CompoundTag tag) {
        this.tag = tag == null ? new CompoundTag() : tag;
    }

    public static CompoundTag unwrap(Object obj) {
        if (obj instanceof NBTTagCompound c) {
            return c.tag;
        }
        if (obj instanceof CompoundTag t) {
            return t;
        }
        return null;
    }

    /** func_74737_b = copy。NGTO Builder が編集前の NBT を退避するのに使う。 */
    public NBTTagCompound func_74737_b() {
        return new NBTTagCompound(this.tag.copy());
    }

    /** func_74760_g = getFloat */
    public float func_74760_g(String key) {
        return this.tag.getFloat(key);
    }

    /** func_74776_a = setFloat */
    public void func_74776_a(String key, float value) {
        this.tag.putFloat(key, value);
    }

    /** func_74764_b = hasKey */
    public boolean func_74764_b(String key) {
        return tag.contains(key);
    }

    /** func_74775_l = getCompoundTag */
    public NBTTagCompound func_74775_l(String key) {
        return new NBTTagCompound(tag.getCompound(key));
    }

    /** func_74782_a = setTag */
    public void func_74782_a(String key, Object value) {
        if (value instanceof NBTTagCompound c) {
            tag.put(key, c.tag);
        } else if (value instanceof NBTTagList l) {
            tag.put(key, l.list);
        } else if (value instanceof net.minecraft.nbt.Tag t) {
            tag.put(key, t);
        }
    }

    /** func_74778_a = setString */
    public void func_74778_a(String key, String value) {
        tag.putString(key, value);
    }

    /** func_74779_i = getString */
    public String func_74779_i(String key) {
        return tag.getString(key);
    }

    /** func_74768_a = setInteger */
    public void func_74768_a(String key, int value) {
        tag.putInt(key, value);
    }

    /** func_74762_e = getInteger */
    public int func_74762_e(String key) {
        return tag.getInt(key);
    }

    /** func_74757_a = setBoolean */
    public void func_74757_a(String key, boolean value) {
        tag.putBoolean(key, value);
    }

    /** func_74767_n = getBoolean */
    public boolean func_74767_n(String key) {
        return tag.getBoolean(key);
    }

    /** func_74780_a = setDouble */
    public void func_74780_a(String key, double value) {
        tag.putDouble(key, value);
    }

    /** func_74769_h = getDouble */
    public double func_74769_h(String key) {
        return tag.getDouble(key);
    }

    /** func_150295_c = getTagList(key, type) */
    public NBTTagList func_150295_c(String key, int type) {
        return new NBTTagList(tag.getList(key, type));
    }

    @Override
    public String toString() {
        return tag.toString();
    }
}
