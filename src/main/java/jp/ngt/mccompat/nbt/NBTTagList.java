package jp.ngt.mccompat.nbt;

import net.minecraft.nbt.ListTag;

/** 1.7.10/1.12 NBTTagList のスクリプト互換ラッパー。 */
public final class NBTTagList {
    public final ListTag list;
    private final java.util.List<Runnable> observers=new java.util.ArrayList<>();
    public void watch(Runnable observer) { observers.add(observer); }
    private void changed() { for(Runnable observer:java.util.List.copyOf(observers)) observer.run(); }

    public NBTTagList() {
        this(new ListTag());
    }

    public NBTTagList(ListTag list) {
        this.list = list == null ? new ListTag() : list;
    }

    /** func_74745_c = tagCount */
    public int func_74745_c() {
        return list.size();
    }

    /** func_150305_b = getCompoundTagAt */
    public NBTTagCompound func_150305_b(int index) {
        var child=new NBTTagCompound(list.getCompound(index)); child.watch(this::changed); return child;
    }

    /** func_74742_a = appendTag */
    public void func_74742_a(Object value) {
        if (value instanceof NBTTagCompound c) {
            list.add(c.tag); c.watch(this::changed); changed();
        } else if(value instanceof NBTTagList nested) {
            list.add(nested.list); nested.watch(this::changed); changed();
        } else if (value instanceof net.minecraft.nbt.Tag t) {
            list.add(t); changed();
        }
    }
    public byte getId() { return 9; }
    public byte func_74732_a() { return getId(); }
    public int getTagType() { return list.getElementType(); }
    public int func_150303_d() { return getTagType(); }
    public double getDoubleAt(int index) { return list.getDouble(index); }
    public double func_150309_d(int index) { return getDoubleAt(index); }
    public float getFloatAt(int index) { return list.getFloat(index); }
    public float func_150308_e(int index) { return getFloatAt(index); }
    public NBTTagList copy() { return new NBTTagList(list.copy()); }
    public NBTTagList func_74737_b() { return copy(); }
    public void set(int index,Object value) {
        net.minecraft.nbt.Tag nativeTag;
        if(value instanceof NBTTagCompound compound) { nativeTag=compound.tag; compound.watch(this::changed); }
        else if(value instanceof NBTTagList nested) { nativeTag=nested.list; nested.watch(this::changed); }
        else if(value instanceof net.minecraft.nbt.Tag tag) nativeTag=tag;
        else throw new IllegalArgumentException("Not an NBT tag");
        list.set(index,nativeTag); changed();
    }
    public void func_150304_a(int index,Object value) { set(index,value); }
    public int tagCount() { return func_74745_c(); }
    public NBTTagCompound getCompoundTagAt(int index) { return func_150305_b(index); }
    public void appendTag(Object tag) { func_74742_a(tag); }
    public String getStringTagAt(int index) { return list.getString(index); }
    public String func_150307_f(int index) { return getStringTagAt(index); }
    public net.minecraft.nbt.Tag removeTag(int index) { var tag=list.remove(index); changed(); return tag; }
    public net.minecraft.nbt.Tag func_74744_a(int index) { return removeTag(index); }
}

