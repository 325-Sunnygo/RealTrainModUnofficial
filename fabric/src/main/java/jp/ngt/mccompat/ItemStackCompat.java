package jp.ngt.mccompat;

import net.minecraft.world.item.ItemStack;

/**
 * 1.7.10 ItemStack のスクリプト互換ラッパー。
 * SRB3/NGTO は item.func_77973_b (getItem) を RTMItem.itemLargeRail 等と
 * 同一性比較し、func_77978_p (NBT) をミニチュア読取に使う。
 */
public final class ItemStackCompat {
    public final ItemStack stack;
    private record TagView(jp.ngt.mccompat.nbt.NBTTagCompound tag,
                           java.util.concurrent.atomic.AtomicReference<net.minecraft.world.item.component.CustomData> data) {}
    private static final java.util.Map<ItemStack,java.lang.ref.WeakReference<TagView>> TAG_VIEWS=
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

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

    /**
     * func_77973_b = getItem。
     * 1.7.10 に 1 個しか無かったアイテムが RTMU で 2 個に分かれている場合は、
     * 本家側の 1 個へ正規化して返す。
     */
    public net.minecraft.world.item.Item func_77973_b() {
        net.minecraft.world.item.Item item = stack.getItem();
        if (item instanceof jp.ngt.rtm.item.ItemRail && jp.ngt.rtm.RTMItem.itemLargeRail != null) {
            return jp.ngt.rtm.RTMItem.itemLargeRail;
        }
        return item;
    }

    /** 正規化していない実アイテム (Java 側から使う)。 */
    public net.minecraft.world.item.Item rawItem() {
        return stack.getItem();
    }

    /** Item damage uses the native damage component. */
    public int func_77960_j() {
        return stack.getDamageValue();
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
        if(real==null) { stack.remove(net.minecraft.core.component.DataComponents.CUSTOM_DATA); TAG_VIEWS.remove(stack); return; }
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.of(real));
        if(tag instanceof jp.ngt.mccompat.nbt.NBTTagCompound wrapper) observe(wrapper);
    }

    /** func_190916_E = getCount (1.12) */
    public int func_190916_E() {
        return stack.getCount();
    }

    private jp.ngt.mccompat.nbt.NBTTagCompound getTagCompat() {
        net.minecraft.world.item.component.CustomData data =
                stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        if (data == null) {
            return null;
        }
        var reference=TAG_VIEWS.get(stack); var current=reference==null ? null : reference.get();
        if(current!=null && current.data().get()==data) return current.tag();
        var wrapper=new jp.ngt.mccompat.nbt.NBTTagCompound(data.copyTag());
        observe(wrapper); return wrapper;
    }

    private void observe(jp.ngt.mccompat.nbt.NBTTagCompound wrapper) {
        var reference=new java.lang.ref.WeakReference<ItemStack>(stack);
        var view=new TagView(wrapper,new java.util.concurrent.atomic.AtomicReference<>(stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA)));
        TAG_VIEWS.put(stack,new java.lang.ref.WeakReference<>(view));
        wrapper.watch(()->{
            var current=reference.get();
            // Replacing or removing the tag detaches old references, just as setTagCompound does.
            if(current==null || current.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA)!=view.data().get()) return;
            var data=net.minecraft.world.item.component.CustomData.of(wrapper.tag);
            view.data().set(data); current.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,data);
        });
    }
    public int getItemDamage() { return func_77960_j(); }
    public void setItemDamage(int damage) { stack.setDamageValue(damage); }
    public void func_77964_b(int damage) { setItemDamage(damage); }
    public int getMaxDamage() { return stack.getMaxDamage(); }
    public int func_77958_k() { return getMaxDamage(); }
    public boolean isItemDamaged() { return stack.isDamaged(); }
    public boolean func_77951_h() { return isItemDamaged(); }
    public boolean isItemStackDamageable() { return stack.isDamageableItem(); }
    public boolean func_77984_f() { return isItemStackDamageable(); }
    public int getStackSize() { return stack.getCount(); }
    public void setStackSize(int count) { stack.setCount(count); }
    public int getField_77994_a() { return getStackSize(); }
    public void setField_77994_a(int count) { setStackSize(count); }
    public int getCount() { return stack.getCount(); }
    public void setCount(int count) { stack.setCount(count); }
    public ItemStackCompat copy() { return new ItemStackCompat(stack.copy()); }
    public ItemStackCompat func_77946_l() { return copy(); }
    public ItemStackCompat splitStack(int count) { return new ItemStackCompat(stack.split(count)); }
    public ItemStackCompat func_77979_a(int count) { return splitStack(count); }
    public net.minecraft.world.item.Item getItem() { return func_77973_b(); }
    public int getMaxStackSize() { return stack.getMaxStackSize(); }
    public int func_77976_d() { return getMaxStackSize(); }
    public boolean hasTagCompound() { return func_77942_o(); }
    public jp.ngt.mccompat.nbt.NBTTagCompound getTagCompound() { return func_77978_p(); }
    public void setTagCompound(Object tag) { func_77982_d(tag); }

    @Override
    public String toString() {
        return stack.toString();
    }
}
