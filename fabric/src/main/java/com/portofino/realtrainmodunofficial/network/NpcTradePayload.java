package com.portofino.realtrainmodunofficial.network;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import com.portofino.realtrainmodunofficial.RealTrainModUnofficialItems;
import jp.ngt.rtm.entity.npc.EntityNPC;
import jp.ngt.rtm.entity.npc.Menu;
import jp.ngt.rtm.entity.npc.MenuEntry;
import jp.ngt.rtm.item.ItemMoney;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Map;
import java.util.TreeMap;

/**
 * NPC の商売 (C2S)。本家 GuiSalesperson の buy/sell/register/delete。
 *
 * <p>本家はお金をスロットへ入れてクライアント側で判定するが、RTMU は
 * <b>サーバーがインベントリの紙幣を数えて精算する</b> (釣り銭は紙幣で返す)。
 * action: 0=買う(salesperson) 1=売る(buyer) 2=登録(owner) 3=削除(owner)
 */
public record NpcTradePayload(int entityId, int action, int menuIndex, int count, int price)
        implements CustomPacketPayload {

    public static final Type<NpcTradePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RealTrainModUnofficial.MODID, "npc_trade"));

    public static final StreamCodec<RegistryFriendlyByteBuf, NpcTradePayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.entityId());
                buf.writeVarInt(p.action());
                buf.writeVarInt(p.menuIndex());
                buf.writeVarInt(p.count());
                buf.writeVarInt(p.price());
            },
            buf -> new NpcTradePayload(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                buf.readVarInt(), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnServer(NpcTradePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (!(player.level().getEntity(payload.entityId()) instanceof EntityNPC npc)) {
                return;
            }
            EntityNPC.Role role = npc.getRole();
            Menu menu = new Menu(npc.getMenu(), role, player.registryAccess());
            switch (payload.action()) {
                case 0 -> {   //買う (salesperson が売る)
                    MenuEntry entry = menu.get(payload.menuIndex());
                    int count = Math.max(1, payload.count());
                    if (role != EntityNPC.Role.SALESPERSON || entry == null || count > entry.maxCount) {
                        return;
                    }
                    int total = entry.price * count;
                    if (pay(player, total)) {
                        ItemStack out = entry.item.copy();
                        out.setCount(entry.item.getCount() * count);
                        give(player, out);
                    } else {
                        player.displayClientMessage(
                            net.minecraft.network.chat.Component.translatable("gui.npc.can_not_buy"), true);
                    }
                }
                case 1 -> {   //売る (buyer が買い取る)
                    MenuEntry entry = menu.get(payload.menuIndex());
                    int count = Math.max(1, payload.count());
                    if (role != EntityNPC.Role.BUYER || entry == null || count > entry.maxCount) {
                        return;
                    }
                    int need = entry.item.getCount() * count;
                    if (removeItems(player, entry.item, need)) {
                        payOut(player, entry.price * count);
                    } else {
                        player.displayClientMessage(
                            net.minecraft.network.chat.Component.translatable("gui.npc.can_not_sell"), true);
                    }
                }
                case 2 -> {   //登録 (メインハンドの品を price で)
                    if (!player.equals(npc.getOwner())) {
                        return;
                    }
                    ItemStack held = player.getMainHandItem();
                    if (held.isEmpty() || payload.price() <= 0) {
                        return;
                    }
                    menu.add(new MenuEntry(held.copy(), payload.price()));
                    npc.setMenu(menu.toNbtString(player.registryAccess()));
                }
                case 3 -> {   //削除
                    if (!player.equals(npc.getOwner())) {
                        return;
                    }
                    menu.remove(payload.menuIndex());
                    npc.setMenu(menu.toNbtString(player.registryAccess()));
                }
                default -> {
                }
            }
        });
    }

    /** プレイヤーの所持金合計。 */
    public static int countMoney(net.minecraft.world.entity.player.Player player) {
        int total = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); ++i) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() instanceof ItemMoney money) {
                total += ItemMoney.MoneyType.getPrice(money.variant) * stack.getCount();
            }
        }
        return total;
    }

    /** price 円をインベントリの紙幣から支払う。釣り銭は紙幣で返す。 */
    private static boolean pay(ServerPlayer player, int price) {
        int total = countMoney(player);
        if (total < price) {
            return false;
        }
        //全部回収して釣りを返す (額面の組合せ問題を避ける)
        int collected = 0;
        for (int i = 0; i < player.getInventory().getContainerSize() && collected < price; ++i) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() instanceof ItemMoney money) {
                collected += ItemMoney.MoneyType.getPrice(money.variant) * stack.getCount();
                player.getInventory().setItem(i, ItemStack.EMPTY);
            }
        }
        if (collected > price) {
            payOut(player, collected - price);
        }
        return true;
    }

    /** amount 円を紙幣で渡す (大きい額面から)。 */
    private static void payOut(ServerPlayer player, int amount) {
        TreeMap<Integer, Item> denominations = new TreeMap<>();
        for (var entry : RealTrainModUnofficialItems.MONEY_ITEMS.entrySet()) {
            if (entry.getValue().get() instanceof ItemMoney money) {
                denominations.put((int) ItemMoney.MoneyType.getPrice(money.variant), money);
            }
        }
        for (Map.Entry<Integer, Item> entry : denominations.descendingMap().entrySet()) {
            int value = entry.getKey();
            if (value <= 0) {
                continue;
            }
            int count = amount / value;
            amount -= count * value;
            while (count > 0) {
                int stackCount = Math.min(count, 64);
                give(player, new ItemStack(entry.getValue(), stackCount));
                count -= stackCount;
            }
        }
    }

    /** entry.item と同じ物を need 個回収。足りなければ何もしないで false。 */
    private static boolean removeItems(ServerPlayer player, ItemStack sample, int need) {
        int have = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); ++i) {
            ItemStack stack = player.getInventory().getItem(i);
            if (ItemStack.isSameItemSameComponents(stack, sample)) {
                have += stack.getCount();
            }
        }
        if (have < need) {
            return false;
        }
        int remaining = need;
        for (int i = 0; i < player.getInventory().getContainerSize() && remaining > 0; ++i) {
            ItemStack stack = player.getInventory().getItem(i);
            if (ItemStack.isSameItemSameComponents(stack, sample)) {
                int take = Math.min(remaining, stack.getCount());
                stack.shrink(take);
                remaining -= take;
                if (stack.isEmpty()) {
                    player.getInventory().setItem(i, ItemStack.EMPTY);
                }
            }
        }
        return true;
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }
}
