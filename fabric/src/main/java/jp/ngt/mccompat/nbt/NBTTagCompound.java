package jp.ngt.mccompat.nbt;

import net.minecraft.nbt.CompoundTag;

/** 1.7.10/1.12 NBTTagCompound のスクリプト互換ラッパー (SRG メソッド名)。 */
public final class NBTTagCompound {
    public final CompoundTag tag;
    private final java.util.List<Runnable> observers=new java.util.ArrayList<>();
    public void watch(Runnable observer) { observers.add(observer); }
    private void changed() { for(Runnable observer:java.util.List.copyOf(observers)) observer.run(); }

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
        this.tag.putFloat(key, value); changed();
    }

    /** func_74764_b = hasKey */
    public boolean func_74764_b(String key) {
        return tag.contains(key);
    }

    /** func_74775_l = getCompoundTag */
    public NBTTagCompound func_74775_l(String key) {
        var child=new NBTTagCompound(tag.getCompound(key)); child.watch(this::changed); return child;
    }

    /** func_74782_a = setTag */
    public void func_74782_a(String key, Object value) {
        if (value instanceof NBTTagCompound c) {
            tag.put(key, c.tag); changed(); c.watch(this::changed);
        } else if (value instanceof NBTTagList l) {
            tag.put(key, l.list); changed(); l.watch(this::changed);
        } else if (value instanceof net.minecraft.nbt.Tag t) {
            tag.put(key, t); changed();
        }
    }

    /** func_74778_a = setString */
    public void func_74778_a(String key, String value) {
        tag.putString(key, value); changed();
    }

    /** func_74779_i = getString */
    public String func_74779_i(String key) {
        return tag.getString(key);
    }

    /** func_74768_a = setInteger */
    public void func_74768_a(String key, int value) {
        tag.putInt(key, value); changed();
    }

    /** func_74762_e = getInteger */
    public int func_74762_e(String key) {
        return tag.getInt(key);
    }

    /** func_74757_a = setBoolean */
    public void func_74757_a(String key, boolean value) {
        tag.putBoolean(key, value); changed();
    }

    /** func_74767_n = getBoolean */
    public boolean func_74767_n(String key) {
        return tag.getBoolean(key);
    }

    /** func_74780_a = setDouble */
    public void func_74780_a(String key, double value) {
        tag.putDouble(key, value); changed();
    }

    /** func_74769_h = getDouble */
    public double func_74769_h(String key) {
        return tag.getDouble(key);
    }

    /** func_150295_c = getTagList(key, type) */
    public NBTTagList func_150295_c(String key, int type) {
        var child=new NBTTagList(tag.getList(key, type)); child.watch(this::changed); return child;
    }

    public Object getTag(String key) {
        var value=tag.get(key);
        if(value instanceof net.minecraft.nbt.CompoundTag) return getCompoundTag(key);
        if(value instanceof net.minecraft.nbt.ListTag list) return getTagList(key,list.getElementType());
        return value;
    }
    public Object func_74781_a(String key) { return getTag(key); }
    public byte getTagId(String key) { return tag.getTagType(key); }
    public byte func_150299_b(String key) { return getTagId(key); }
    public byte getId() { return 10; }
    public byte func_74732_a() { return getId(); }
    public boolean hasNoTags() { return tag.isEmpty(); }
    public void merge(NBTTagCompound other) { tag.merge(other.tag); changed(); }
    public void func_179237_a(NBTTagCompound other) { merge(other); }
    public void setByte(String key,byte value) { tag.putByte(key,value); changed(); }
    public byte getByte(String key) { return tag.getByte(key); }
    public void setShort(String key,short value) { tag.putShort(key,value); changed(); }
    public short getShort(String key) { return tag.getShort(key); }
    public void setLong(String key,long value) { tag.putLong(key,value); changed(); }
    public long getLong(String key) { return tag.getLong(key); }
    public void setByteArray(String key,byte[] value) { tag.putByteArray(key,value); changed(); }
    public byte[] getByteArray(String key) { return tag.getByteArray(key); }
    public void setIntArray(String key,int[] value) { tag.putIntArray(key,value); changed(); }
    public int[] getIntArray(String key) { return tag.getIntArray(key); }
    public void removeTag(String key) { tag.remove(key); changed(); }
    public boolean hasKey(String key,int type) { return tag.contains(key,type); }
    public boolean hasKey(String key) { return func_74764_b(key); }
    public java.util.Set<String> func_150296_c() { return tag.getAllKeys(); }
    public void func_82580_o(String key) { removeTag(key); }
    public void func_74774_a(String key,byte value) { setByte(key,value); }
    public byte func_74771_c(String key) { return getByte(key); }
    public void func_74777_a(String key,short value) { setShort(key,value); }
    public short func_74765_d(String key) { return getShort(key); }
    public void func_74772_a(String key,long value) { setLong(key,value); }
    public long func_74763_f(String key) { return getLong(key); }
    public void func_74773_a(String key,byte[] value) { setByteArray(key,value); }
    public byte[] func_74770_j(String key) { return getByteArray(key); }
    public void func_74783_a(String key,int[] value) { setIntArray(key,value); }
    public int[] func_74759_k(String key) { return getIntArray(key); }
    public boolean func_150297_b(String key,int type) { return hasKey(key,type); }
    public boolean func_82582_d() { return tag.isEmpty(); }
    public NBTTagCompound copy() { return func_74737_b(); }
    public NBTTagCompound getCompoundTag(String key) { return func_74775_l(key); }
    public NBTTagList getTagList(String key,int type) { return func_150295_c(key,type); }
    public void setTag(String key,Object value) { func_74782_a(key,value); }
    public void setFloat(String key,float value) { func_74776_a(key,value); }
    public float getFloat(String key) { return func_74760_g(key); }
    public void setInteger(String key,int value) { func_74768_a(key,value); }
    public int getInteger(String key) { return func_74762_e(key); }
    public void setString(String key,String value) { func_74778_a(key,value); }
    public String getString(String key) { return func_74779_i(key); }
    public void setBoolean(String key,boolean value) { func_74757_a(key,value); }
    public boolean getBoolean(String key) { return func_74767_n(key); }
    public void setDouble(String key,double value) { func_74780_a(key,value); }
    public double getDouble(String key) { return func_74769_h(key); }

    @Override
    public String toString() {
        return tag.toString();
    }
}
