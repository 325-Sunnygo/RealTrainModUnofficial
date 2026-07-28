package jp.ngt.mccompat.nbt;

import net.minecraft.nbt.ListTag;

/**
 * 1.7.10/1.12 NBTTagList のスクリプト互換ラッパー。
 */
public final class NBTTagList {
    public final ListTag list;

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
        return new NBTTagCompound(list.getCompound(index));
    }

    /** func_74742_a = appendTag */
    public void func_74742_a(Object value) {
        if (value instanceof NBTTagCompound c) {
            list.add(c.tag);
        } else if (value instanceof net.minecraft.nbt.Tag t) {
            list.add(t);
        }
    }
}
