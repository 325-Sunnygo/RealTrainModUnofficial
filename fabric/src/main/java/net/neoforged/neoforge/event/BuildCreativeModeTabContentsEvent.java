package net.neoforged.neoforge.event;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.neoforged.bus.api.Event;

import java.util.ArrayList;
import java.util.List;

/** シム: 追加分をエントリポイントが ItemGroupEvents へ回す。 */
public class BuildCreativeModeTabContentsEvent extends Event {
    private final ResourceKey<CreativeModeTab> tabKey;
    private final List<ItemStack> accepted = new ArrayList<>();

    public BuildCreativeModeTabContentsEvent(ResourceKey<CreativeModeTab> tabKey) {
        this.tabKey = tabKey;
    }

    public ResourceKey<CreativeModeTab> getTabKey() {
        return tabKey;
    }

    public void accept(ItemLike item) {
        accepted.add(new ItemStack(item));
    }

    public void accept(ItemStack stack) {
        accepted.add(stack);
    }

    public List<ItemStack> getAccepted() {
        return accepted;
    }
}
