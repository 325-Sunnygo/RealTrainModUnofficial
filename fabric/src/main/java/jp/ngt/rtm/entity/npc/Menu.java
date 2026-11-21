package jp.ngt.rtm.entity.npc;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * 商売メニュー。本家 {@code Menu} の移植 (NBT 文字列 {list:[{item,price}...]})。
 * 空のときは本家と同じ既定メニュー (クッキー/焼き鮭)。
 */
public class Menu {
    private final List<MenuEntry> menuList = new ArrayList<>();

    public Menu(String s, EntityNPC.Role role, HolderLookup.Provider provider) {
        this.init(s, role, provider);
    }

    public boolean add(MenuEntry entry) {
        if (entry == null || entry.item.isEmpty()) {
            return false;
        }
        this.menuList.removeIf(e -> ItemStack.isSameItemSameComponents(e.item, entry.item)
            && e.item.getCount() == entry.item.getCount());
        return this.menuList.add(entry);
    }

    public void remove(int index) {
        if (index >= 0 && index < this.menuList.size()) {
            this.menuList.remove(index);
        }
    }

    public MenuEntry get(int index) {
        return index >= 0 && index < this.menuList.size() ? this.menuList.get(index) : null;
    }

    public List<MenuEntry> getList() {
        return this.menuList;
    }

    public boolean init(String s, EntityNPC.Role role, HolderLookup.Provider provider) {
        this.menuList.clear();
        if (s != null && !s.isEmpty()) {
            try {
                CompoundTag nbt0 = TagParser.parseTag(s);
                ListTag list = nbt0.getList("list", 10);
                for (int i = 0; i < list.size(); ++i) {
                    MenuEntry entry = MenuEntry.readFromNBT(list.getCompound(i), provider);
                    if (entry != null) {
                        this.add(entry);
                    }
                }
                if (!this.menuList.isEmpty()) {
                    return true;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        //本家の既定メニュー
        if (role == EntityNPC.Role.SALESPERSON) {
            this.add(new MenuEntry(new ItemStack(Items.COOKIE, 10), 200));
            this.add(new MenuEntry(new ItemStack(Items.COOKED_SALMON, 5), 500));
        } else if (role == EntityNPC.Role.BUYER) {
            this.add(new MenuEntry(new ItemStack(Items.COOKIE, 1), 20));
            this.add(new MenuEntry(new ItemStack(Items.COOKED_SALMON, 1), 100));
        }
        return false;
    }

    public String toNbtString(HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        for (MenuEntry entry : this.menuList) {
            list.add(entry.writeToNBT(provider));
        }
        CompoundTag nbt = new CompoundTag();
        nbt.put("list", list);
        return nbt.toString();
    }
}
