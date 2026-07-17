package com.myname.legacyloader.bridge.item;

import com.myname.legacyloader.LegacyLoaderMod;
import com.myname.legacyloader.bridge.init.LegacyItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;

public class LegacyItemStackHelper {

    public static final String METADATA_TAG = "LegacyMeta";
    public static final String CUSTOM_MODEL_DATA_TAG = "CustomModelData";

    public static ItemStack create(Object obj) {
        return createInternal(obj, 1, 0);
    }

    public static ItemStack create(Object obj, int count) {
        return createInternal(obj, count, 0);
    }

    public static ItemStack create(Object obj, int count, int meta) {
        return createInternal(obj, count, meta);
    }

    private static ItemStack createInternal(Object obj, int count, int meta) {
        if (obj == null) {
            return ItemStack.EMPTY;
        }

        try {
            Item item = null;

            if (obj instanceof ItemStack) {
                ItemStack stack = (ItemStack) obj;
                if (stack.isEmpty()) return ItemStack.EMPTY;
                ItemStack result = stack.copy();
                result.setCount(Math.max(1, count));
                if (meta != 0) {
                    setMetadata(result, meta);
                }
                return result;
            }

            if (obj instanceof Item) {
                item = (Item) obj;
            } else if (obj instanceof Block) {
                Block block = (Block) obj;
                item = block.asItem();
            } else if (obj instanceof ItemLike) {
                item = ((ItemLike) obj).asItem();
            }

            if (item == null || item == Items.AIR) {
                return ItemStack.EMPTY;
            }

            if (item == LegacyItems.field_151100_aR) {
                item = LegacyItems.dyeFromLegacyMetadata(meta);
                meta = 0;
            }

            ItemStack stack = new ItemStack(item, Math.max(1, count));

            if (meta != 0) {
                setMetadata(stack, meta);
            }

            return stack;

        } catch (Exception e) {
            System.err.println("LegacyItemStackHelper: Error creating ItemStack: " + e.getMessage());
            return ItemStack.EMPTY;
        }
    }

    /**
     * 笘・㍾隕・ 繝｡繧ｿ繝・・繧ｿ繧貞叙蠕・
     */
    public static int getMetadata(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }

        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null) {
            CompoundTag tag = customData.copyTag();
            // LegacyMeta繧ｿ繧ｰ繧偵メ繧ｧ繝・け
            if (tag.contains(METADATA_TAG)) {
                return tag.getInt(METADATA_TAG);
            }
            // CustomModelData繧ｿ繧ｰ繧偵メ繧ｧ繝・け・医ヵ繧ｩ繝ｼ繝ｫ繝舌ャ繧ｯ・・
            if (tag.contains(CUSTOM_MODEL_DATA_TAG)) {
                return tag.getInt(CUSTOM_MODEL_DATA_TAG);
            }
        }

        // 繝繝｡繝ｼ繧ｸ蛟､繧偵メ繧ｧ繝・け
        return stack.getDamageValue();
    }

    /**
     * 笘・㍾隕・ 繝｡繧ｿ繝・・繧ｿ繧定ｨｭ螳・
     */
    public static void setMetadata(ItemStack stack, int metadata) {
        if (stack == null || stack.isEmpty()) {
            return;
        }

        CustomData existing = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag tag = existing != null ? existing.copyTag() : new CompoundTag();

        // LegacyMeta繧ｿ繧ｰ縺ｫ菫晏ｭ・
        tag.putInt(METADATA_TAG, metadata);

        // 笘・㍾隕・ CustomModelData縺ｫ繧ゆｿ晏ｭ假ｼ医Δ繝・Ν蛻・ｊ譖ｿ縺育畑・・
        tag.putInt(CUSTOM_MODEL_DATA_TAG, metadata);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(metadata));

        // 繝繝｡繝ｼ繧ｸ蛟､縺ｫ繧りｨｭ螳夲ｼ井ｺ呈鋤諤ｧ縺ｮ縺溘ａ・・
        try {
            stack.setDamageValue(metadata);
        } catch (Exception e) {
            // 辟｡隕・
        }

        LegacyLoaderMod.LOGGER.debug("LegacyItemStackHelper.setMetadata: meta=" + metadata +
                ", tag=" + tag.toString());
    }

    public static int getItemDamage(ItemStack stack) {
        return getMetadata(stack);
    }

    public static void setItemDamage(ItemStack stack, int damage) {
        setMetadata(stack, damage);
    }

    public static void setCount(ItemStack stack, int count) {
        if (stack != null) stack.setCount(Math.max(0, count));
    }

    // 1.7.10 SRG蜷阪・繝・ヴ繝ｳ繧ｰ・・lassTransformer縺九ｉ繝ｪ繝繧､繝ｬ繧ｯ繝茨ｼ・
    public static Item func_77973_b(ItemStack stack) {
        return (stack == null || stack.isEmpty()) ? Items.AIR : stack.getItem();
    }

    public static int func_190916_E(ItemStack stack) {
        return stack == null ? 0 : stack.getCount();
    }

    public static int func_77960_j(ItemStack stack) {
        return stack == null ? 0 : getMetadata(stack);
    }

    /** 1.7.10 ItemStack.func_77964_b(int) = setItemDamage / setDamageValue。 */
    public static void func_77964_b(ItemStack stack, int damage) {
        if (stack != null && !stack.isEmpty()) {
            stack.setDamageValue(damage);
        }
    }

    public static boolean func_190926_b(ItemStack stack) {
        return stack == null || stack.isEmpty();
    }

    // func_77982_d = setTagCompound(CompoundTag) — removed in 1.21.1, use data components
    public static void func_77982_d(ItemStack stack, CompoundTag tag) {
        if (stack == null || stack.isEmpty()) return;
        if (tag == null || tag.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
    }

    // func_77978_p = getTagCompound() — removed in 1.21.1, use data components
    public static CompoundTag func_77978_p(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        return customData != null ? customData.copyTag() : null;
    }

    /**
     * 1.7.10 ItemStack#isItemEqual(ItemStack) / func_77969_a.
     *
     * Important: this is an instance method in 1.7.10 with one ItemStack parameter,
     * so the transformer must redirect it to this static method with TWO ItemStack
     * arguments: the receiver and the compared stack.  A previous one-argument
     * implementation left the compared stack on the JVM operand stack and caused
     * VerifyError / broken stack-map frames in BuildCraft coolant and pipe code.
     */
    public static boolean func_77969_a(ItemStack self, ItemStack other) {
        return isItemEqual(self, other);
    }

    public static boolean isItemEqual(ItemStack self, ItemStack other) {
        if (self == null || other == null || self.isEmpty() || other.isEmpty()) return false;
        if (self.getItem() != other.getItem()) return false;

        int a = getMetadata(self);
        int b = getMetadata(other);
        // 1.7.10 OreDictionary.WILDCARD_VALUE compatibility.
        return a == b || a == 32767 || b == 32767;
    }

    // func_77942_o = hasTagCompound() in 1.7.10.
    public static boolean func_77942_o(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return stack.has(DataComponents.CUSTOM_DATA);
    }

    // Human-readable alias used by some transformed/deobfuscated mods.
    public static boolean hasTagCompound(ItemStack stack) {
        return func_77942_o(stack);
    }
}
