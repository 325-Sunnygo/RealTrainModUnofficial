package jp.ngt.mccompat;

import net.minecraft.world.item.ItemStack;

/**
 * 1.7.10 ItemStack のスクリプト互換ラッパー。
 * SRB3/NGTO は item.func_77973_b() (getItem) を RTMItem.itemLargeRail 等と
 * 同一性比較し、func_77978_p() (NBT) をミニチュア読取に使う。
 */
public final class ItemStackCompat {
    public final ItemStack stack;

    public ItemStackCompat(ItemStack stack) {
        this.stack = stack;
    }

    public static ItemStack unwrap(Object obj) {
        if (obj instanceof ItemStackCompat c) {
            return c.stack;
        }
        if (obj instanceof ItemStack s) {
            return s;
        }
        return null;
    }

    /** func_77973_b = getItem */
    public net.minecraft.world.item.Item func_77973_b() {
        return stack.getItem();
    }

    /** func_77960_j = getItemDamage (1.21 にメタは無い) */
    public int func_77960_j() {
        return 0;
    }

    /** func_77942_o = hasTagCompound */
    public boolean func_77942_o() {
        return getTagCompat() != null;
    }

    /** func_77978_p = getTagCompound */
    public jp.ngt.mccompat.nbt.NBTTagCompound func_77978_p() {
        // MCTEU (com.mattya.mcteunofficial) のミニチュアは、NGTO Builder が読めるよう中身を
        // NGTObject 形式 ("BlocksData" を含む) に変換して見せる (ソフト連携)。MCTEU 未導入や
        // データ未解決なら null が返り、従来どおり実 NBT にフォールバックする。
        net.minecraft.nbt.CompoundTag mcteu = jp.ngt.mcte.McteuMiniatureBridge.getBlocksDataTag(stack);
        if (mcteu != null) {
            return new jp.ngt.mccompat.nbt.NBTTagCompound(mcteu);
        }
        return getTagCompat();
    }

    /** func_77982_d = setTagCompound */
    public void func_77982_d(Object tag) {
        net.minecraft.nbt.CompoundTag real = jp.ngt.mccompat.nbt.NBTTagCompound.unwrap(tag);
        if (real != null) {
            stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                    net.minecraft.world.item.component.CustomData.of(real));
        }
    }

    /** func_190916_E = getCount (1.12) */
    public int func_190916_E() {
        return stack.getCount();
    }

    private jp.ngt.mccompat.nbt.NBTTagCompound getTagCompat() {
        net.minecraft.world.item.component.CustomData data =
                stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        if (data == null || data.isEmpty()) {
            return null;
        }
        return new jp.ngt.mccompat.nbt.NBTTagCompound(data.copyTag());
    }

    @Override
    public String toString() {
        return stack.toString();
    }
}
