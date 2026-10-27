package com.portofino.realtrainmodunofficial.client.screen;

import com.portofino.realtrainmodunofficial.network.NpcTradePayload;
import jp.ngt.rtm.entity.npc.EntityNPC;
import jp.ngt.rtm.entity.npc.Menu;
import jp.ngt.rtm.entity.npc.MenuEntry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * NPC の商売画面。本家 {@code GuiSalesperson} の移植。
 *
 * <p>本家はお金をスロットに入れる方式だが、RTMU は所持金を自動精算する
 * (サーバーがインベントリの紙幣を数え、釣り銭は紙幣で返す)。
 * メニューは 7 行 x ページ、+/- で個数、店主 (飼い主) は登録/削除ができる。
 */
public class NpcTradeScreen extends Screen {

    private static final int ROWS = 7;

    private final EntityNPC npc;
    private final boolean isOwner;
    private Menu menu;

    private int pageIndex;
    private int selectedRow = -1;
    private int count = 1;
    private EditBox priceField;
    private String message = "";

    public NpcTradeScreen(EntityNPC npc) {
        super(npc.getName());
        this.npc = npc;
        this.isOwner = net.minecraft.client.Minecraft.getInstance().player != null
            && net.minecraft.client.Minecraft.getInstance().player.equals(npc.getOwner());
    }

    private void reloadMenu() {
        this.menu = new Menu(this.npc.getMenu(), this.npc.getRole(),
            this.minecraft.level.registryAccess());
    }

    @Override
    protected void init() {
        this.reloadMenu();
        this.clearWidgets();
        int hw = this.width / 2;
        int top = 30;
        int rowH = 24;

        List<MenuEntry> list = this.menu.getList();
        int maxPage = Math.max(0, (list.size() - 1) / ROWS);
        this.pageIndex = Math.min(this.pageIndex, maxPage);

        //ページ送り
        this.addRenderableWidget(Button.builder(Component.literal("<"), b -> {
            if (this.pageIndex > 0) {
                --this.pageIndex;
                this.rebuild();
            }
        }).bounds(hw - 120, 5, 20, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal(">"), b -> {
            if (this.pageIndex < maxPage) {
                ++this.pageIndex;
                this.rebuild();
            }
        }).bounds(hw + 100, 5, 20, 20).build());

        for (int i = 0; i < ROWS; ++i) {
            int menuIndex = this.pageIndex * ROWS + i;
            if (menuIndex >= list.size()) {
                break;
            }
            int y = top + i * rowH;
            int row = i;
            //行選択
            this.addRenderableWidget(Button.builder(Component.literal("選択"), b -> {
                this.selectedRow = row;
                this.count = 1;
            }).bounds(hw + 40, y, 34, 20).build());
            if (this.isOwner) {
                this.addRenderableWidget(Button.builder(Component.literal("X"), b -> {
                    net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                        new NpcTradePayload(this.npc.getId(), 3, this.pageIndex * ROWS + row, 0, 0));
                    //エンティティデータの同期を待ってから再読込
                    this.minecraft.execute(this::rebuild);
                }).bounds(hw + 78, y, 20, 20).build());
            }
        }

        //個数 +/-
        this.addRenderableWidget(Button.builder(Component.literal("-"), b -> {
            if (this.count > 1) {
                --this.count;
            }
        }).bounds(hw - 120, this.height - 56, 20, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("+"), b -> {
            MenuEntry entry = this.selectedEntry();
            if (entry != null && this.count < entry.maxCount) {
                ++this.count;
            }
        }).bounds(hw - 70, this.height - 56, 20, 20).build());

        //買う / 売る
        String actionKey = this.npc.getRole() == EntityNPC.Role.BUYER ? "gui.npc.sell" : "gui.npc.buy";
        this.addRenderableWidget(Button.builder(Component.translatable(actionKey), b -> {
            int index = this.selectedIndex();
            if (index < 0) {
                return;
            }
            int action = this.npc.getRole() == EntityNPC.Role.BUYER ? 1 : 0;
            net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                new NpcTradePayload(this.npc.getId(), action, index, this.count, 0));
        }).bounds(hw - 40, this.height - 56, 60, 20).build());

        //登録 (店主のみ): メインハンドの品を価格つきで
        if (this.isOwner) {
            this.priceField = new EditBox(this.font, hw + 30, this.height - 56, 60, 20, Component.empty());
            this.priceField.setValue("100");
            this.addRenderableWidget(this.priceField);
            this.addRenderableWidget(Button.builder(Component.translatable("gui.npc.register"), b -> {
                int price;
                try {
                    price = Integer.parseInt(this.priceField.getValue().trim());
                } catch (NumberFormatException e) {
                    return;
                }
                net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                    new NpcTradePayload(this.npc.getId(), 2, 0, 0, price));
                this.minecraft.execute(this::rebuild);
            }).bounds(hw + 94, this.height - 56, 60, 20).build());
        }

        this.addRenderableWidget(Button.builder(Component.translatable("gui.npc.close"), b -> this.onClose())
            .bounds(hw - 30, this.height - 28, 60, 20).build());
    }

    private void rebuild() {
        this.init(this.minecraft, this.width, this.height);
    }

    private int selectedIndex() {
        int index = this.pageIndex * ROWS + this.selectedRow;
        return this.selectedRow >= 0 && index < this.menu.getList().size() ? index : -1;
    }

    private MenuEntry selectedEntry() {
        int index = this.selectedIndex();
        return index < 0 ? null : this.menu.get(index);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int hw = this.width / 2;
        int top = 30;
        int rowH = 24;

        graphics.drawCenteredString(this.font, this.title, hw, 11, 0xFFFFFF);

        List<MenuEntry> list = this.menu.getList();
        for (int i = 0; i < ROWS; ++i) {
            int menuIndex = this.pageIndex * ROWS + i;
            if (menuIndex >= list.size()) {
                break;
            }
            MenuEntry entry = list.get(menuIndex);
            int y = top + i * rowH;
            if (this.selectedRow == i) {
                graphics.fill(hw - 122, y - 2, hw + 102, y + 20, 0x60FFFFFF);
            }
            graphics.renderItem(entry.item, hw - 118, y + 2);
            graphics.drawString(this.font,
                entry.item.getHoverName().getString() + " x" + entry.item.getCount(),
                hw - 96, y + 6, 0xFFFFFF);
            graphics.drawString(this.font, String.format("￥%d", entry.price), hw - 4, y + 6, 0xFFFF80);
        }

        //個数と合計
        MenuEntry entry = this.selectedEntry();
        graphics.drawCenteredString(this.font, String.valueOf(this.count), hw - 95, this.height - 50, 0xFFFFFF);
        if (entry != null) {
            graphics.drawString(this.font, String.format("￥%d", entry.price * this.count),
                hw + 24, this.height - 76, 0xFFFF80);
        }
        //所持金
        int money = NpcTradePayload.countMoney(this.minecraft.player);
        graphics.drawString(this.font,
            Component.translatable("gui.npc.have_money").getString() + String.format(" ￥%d", money),
            hw - 120, this.height - 76, 0xA0FFA0);
        if (!this.message.isEmpty()) {
            graphics.drawCenteredString(this.font, this.message, hw, this.height - 90, 0xFF4040);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** 1.21 のメニューぼかしを無効化 (本家 1.7.10 の GUI にぼかしは無い)。 */
    @Override
    protected void renderBlurredBackground(float partialTick) {
    }
}
