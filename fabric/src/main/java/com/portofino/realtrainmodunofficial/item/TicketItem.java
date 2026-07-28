package com.portofino.realtrainmodunofficial.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;

import java.util.List;

/**
 * 本家 jp.ngt.rtm.item.ItemTicket の移植。券売機 (TICKET_VENDOR) が発券し、
 * 改札 (TICKET_GATE) が消費する。
 *
 * <p>本家は残り回数をメタデータ (damage) に、入場済みフラグを NBT "Entered" に持っていた。
 * 1.21 にメタデータは無いので、どちらも CUSTOM_DATA の中に入れる。
 *
 * <p>本家 ItemTicket.consumeTicket の挙動:
 * <ul>
 *   <li>未入場で残り &gt; 0 → 残りを 1 減らして「入場済み」印を付ける (入場)</li>
 *   <li>入場済みで残り &gt; 0 → 「入場済み」印を外して返す (出場。次also使える)</li>
 *   <li>入場済みで残り 0    → 券は回収される (出場して使い切り)</li>
 * </ul>
 * つまり切符 (残り 1) は片道1往復で無くなり、回数券 (残り 11) は 11 往復できる。
 */
public class TicketItem extends Item {

    private static final String KEY_RIDES = "Rides";
    private static final String KEY_ENTERED = "Entered";

    /** 発券時の残り回数。本家: 切符=1, 回数券=11。 */
    private final int defaultRides;

    public TicketItem(int defaultRides) {
        super(new Properties().stacksTo(1));
        this.defaultRides = defaultRides;
    }

    /** 券売機が発券する新品。 */
    public ItemStack createIssued() {
        ItemStack stack = new ItemStack(this);
        write(stack, defaultRides, false);
        return stack;
    }

    /**
     * 改札を通ったときの処理。
     *
     * @return 手に残るスタック (使い切ったら {@link ItemStack#EMPTY})
     */
    public ItemStack consume(ItemStack stack) {
        CompoundTag tag = read(stack);
        int rides = tag.contains(KEY_RIDES) ? tag.getInt(KEY_RIDES) : defaultRides;
        boolean entered = tag.getBoolean(KEY_ENTERED);

        if (entered) {
            //出場。残っていれば入場済み印を外して返す。使い切っていたら回収。
            if (rides <= 0) {
                return ItemStack.EMPTY;
            }
            ItemStack out = new ItemStack(this);
            write(out, rides, false);
            return out;
        }
        //入場。残り 0 の券では入れない (通常は起こらない)。
        if (rides <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack out = new ItemStack(this);
        write(out, rides - 1, true);
        return out;
    }

    public boolean isEntered(ItemStack stack) {
        return read(stack).getBoolean(KEY_ENTERED);
    }

    public int getRides(ItemStack stack) {
        CompoundTag tag = read(stack);
        return tag.contains(KEY_RIDES) ? tag.getInt(KEY_RIDES) : defaultRides;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        //本家: 回数券だけ残り回数を出す。
        if (defaultRides > 1) {
            tooltip.add(Component.translatable("tooltip.realtrainmodunofficial.ticket.remaining", getRides(stack))
                .withStyle(ChatFormatting.GRAY));
        }
        if (isEntered(stack)) {
            tooltip.add(Component.translatable("tooltip.realtrainmodunofficial.ticket.entered")
                .withStyle(ChatFormatting.GRAY));
        }
    }

    private static CompoundTag read(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? new CompoundTag() : data.copyTag();
    }

    private static void write(ItemStack stack, int rides, boolean entered) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(KEY_RIDES, Math.max(0, rides));
        tag.putBoolean(KEY_ENTERED, entered);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }
}
