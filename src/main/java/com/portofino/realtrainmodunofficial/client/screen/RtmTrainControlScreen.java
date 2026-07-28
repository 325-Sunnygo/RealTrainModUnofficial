package com.portofino.realtrainmodunofficial.client.screen;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import com.portofino.realtrainmodunofficial.RealTrainModUnofficialItems;
import com.portofino.realtrainmodunofficial.network.TrainControlPayload;
import com.portofino.realtrainmodunofficial.vehicle.VehicleDefinition;
import com.portofino.realtrainmodunofficial.vehicle.VehicleRegistry;
import jp.ngt.rtm.entity.train.EntityTrainBase;
import jp.ngt.rtm.entity.train.util.TrainState.TrainStateType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * jp.ngt 列車の運転台メニュー — 本家 GuiTrainCtrl 相当 (RTM 風テクスチャ付き)。
 * 旧 TrainControlScreen のレイアウト/テクスチャを EntityTrainBase (TrainState) 向けに移植。
 */
public class RtmTrainControlScreen extends Screen {
    private static final ResourceLocation TAB_INVENTORY_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(RealTrainModUnofficial.MODID, "textures/gui/tab_inventory.png");
    private static final ResourceLocation TAB_SETTING_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(RealTrainModUnofficial.MODID, "textures/gui/tab_setting.png");
    private static final ResourceLocation TAB_FORMATION_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(RealTrainModUnofficial.MODID, "textures/gui/tab_formation.png");

    private static final int PANEL_W = 176;
    private static final int PANEL_H = 166;
    private static final int TAB_W = 28;
    private static final int TAB_H = 32;

    private final EntityTrainBase train;
    private ControlTab selectedTab = ControlTab.SETTING;
    private int refreshTimer;

    public RtmTrainControlScreen(EntityTrainBase train) {
        super(Component.literal("Train Control Panel"));
        this.train = train;
    }

    @Override
    protected void init() {
        rebuildTabWidgets();
    }

    private void rebuildTabWidgets() {
        clearWidgets();
        int left = leftPos();
        int top = topPos();
        if (selectedTab == ControlTab.SETTING) {
            addButton(left + 4, top + 4, 82, interiorLightLabel(), "toggle_interior_light", 0);
            addButton(left + 90, top + 4, 82, lightLabel(), "set_light_mode", nextLightMode());
            addButton(left + 4, top + 28, 82, pantographLabel(), "toggle_pantograph", 0);
            int dir = train.getTrainStateData(TrainStateType.State_Direction.id);
            addButton(left + 90, top + 28, 27, "前", "set_direction", 0).active = dir != 0;
            addButton(left + 117, top + 28, 28, "中", "set_direction", 1).active = dir != 1;
            addButton(left + 145, top + 28, 27, "後", "set_direction", 2).active = dir != 2;
            boolean loaderOn = train.getTrainStateData(TrainStateType.State_ChunkLoader.id) != 0;
            addArrowButton(left + 4, top + 52, "<", "toggle_chunk_loader", 0);
            addButton(left + 28, top + 52, 120, "チャンクロード" + (loaderOn ? " ON" : " OFF"), "toggle_chunk_loader", 0);
            addArrowButton(left + 152, top + 52, ">", "toggle_chunk_loader", 0);
            // 方向幕/種別/アナウンスの3行は、チャンクロードの下で行間を詰める (高さ18)。
            // 行を1つ増やした (種別) ぶん下がホットバー/インベントリに被らないように上へ寄せる。
            final int rowH = 18;
            // 方向幕: 持っていないパックでは null と出して押せなくする
            int destCount = rollsignNames().length;
            if (destCount == 0) {
                addArrowButton(left + 4, top + 73, rowH, "<", "noop", 0);
                addButton(left + 28, top + 73, 120, rowH, "方向幕 null", "noop", 0);
                addArrowButton(left + 152, top + 73, rowH, ">", "noop", 0);
            } else {
                int dest = Math.floorMod(
                        train.getTrainStateData(TrainStateType.State_Destination.id), destCount);
                addArrowButton(left + 4, top + 73, rowH, "<", "set_destination",
                        Math.floorMod(dest - 1, destCount));
                addButton(left + 28, top + 73, 120, rowH, destinationLabel(), "set_destination",
                        Math.floorMod(dest + 1, destCount));
                addArrowButton(left + 152, top + 73, rowH, ">", "set_destination",
                        Math.floorMod(dest + 1, destCount));
            }
            // RTMU 追加: 種別幕。方向幕のすぐ下に同じ操作感で並べる。持たないパックは null で押せなくする。
            int typeCount = typeSignNames().length;
            if (typeCount == 0) {
                addArrowButton(left + 4, top + 92, rowH, "<", "noop", 0);
                addButton(left + 28, top + 92, 120, rowH, "種別 null", "noop", 0);
                addArrowButton(left + 152, top + 92, rowH, ">", "noop", 0);
            } else {
                int type = Math.floorMod(
                        train.getTrainStateData(TrainStateType.State_Type.id), typeCount);
                addArrowButton(left + 4, top + 92, rowH, "<", "set_type",
                        Math.floorMod(type - 1, typeCount));
                addButton(left + 28, top + 92, 120, rowH, typeLabel(), "set_type",
                        Math.floorMod(type + 1, typeCount));
                addArrowButton(left + 152, top + 92, rowH, ">", "set_type",
                        Math.floorMod(type + 1, typeCount));
            }
            // アナウンスも方向幕と同じ回し方にする。
            // 以前は上限も循環も無く、パックのアナウンス数を超えて増え続けていた。
            int announceCount = announcementCount();
            if (announceCount == 0) {
                // アナウンスを持たないパック: 空欄ではなく null と出して、押せなくする
                addArrowButton(left + 4, top + 111, rowH, "<", "noop", 0);
                addButton(left + 28, top + 111, 120, rowH, "アナウンス null", "noop", 0);
                addArrowButton(left + 152, top + 111, rowH, ">", "noop", 0);
            } else {
                int announce = Math.floorMod(
                        train.getTrainStateData(TrainStateType.State_Announcement.id), announceCount);
                addArrowButton(left + 4, top + 111, rowH, "<", "set_announcement",
                        Math.floorMod(announce - 1, announceCount));
                addButton(left + 28, top + 111, 120, rowH, announcementLabel(announce), "set_announcement",
                        Math.floorMod(announce + 1, announceCount));
                addArrowButton(left + 152, top + 111, rowH, ">", "set_announcement",
                        Math.floorMod(announce + 1, announceCount));
            }
        } else if (selectedTab == ControlTab.FUNCTION) {
            VehicleDefinition definition = VehicleRegistry.getById(train.getModelName());
            List<List<String>> options = definition != null ? definition.getCustomButtonOptions() : List.of();
            for (int i = 0; i < Math.min(18, options.size()); i++) {
                int x = left + 4 + (i % 3) * 56;
                int y = top + 4 + (i / 3) * 24;
                int index = i;
                int value = train.getResourceState().getDataMap().getInt("Button" + i);
                List<String> optionList = options.get(i);
                // スライダー型: customButtons のエントリを ["slider:名前"] と書くと
                // 0-100% のスライダーになる (値は DataMap Button{i}、0=未設定は 100% 扱い)
                if (optionList.size() == 1 && optionList.get(0).startsWith("slider:")) {
                    String label = optionList.get(0).substring("slider:".length());
                    int shown = value <= 0 ? 100 : Math.min(100, value);
                    addRenderableWidget(new CustomSliderButton(left + 4, y, 164, 22, label, index, shown));
                    continue;
                }
                String text = optionList.isEmpty() ? ("カスタム" + (i + 1))
                        : optionList.get(Math.floorMod(value, optionList.size()));
                int packed = (index << 8) | (value & 0xFF);
                addRenderableWidget(Button.builder(Component.literal(text), b -> send("cycle_custom_button", packed))
                        .bounds(x, y, 52, 22).build());
            }
        } else if (selectedTab == ControlTab.DEDICATED) {
            // 速度設定タブ: 最高速度と加速度を車両ごとに決める。
            // JSON (maxSpeed / acceleration) は読まなくなったので、性能はここで指定する。
            int maxSpeed = train.getConfiguredMaxSpeedKmh();
            int accelCenti = train.getConfiguredAccelCentiKmhS();

            graphics_maxSpeedLabel = maxSpeed > 0 ? String.valueOf(maxSpeed) : "既定";
            // 単位はラベル側に出さない (数値だけ)。"6.00 km/h/s" は 60px あり、
            // 左右のボタンに挟まれた 60px の隙間で右の +10 と重なる。
            graphics_accelLabel = accelCenti > 0
                    ? String.format(java.util.Locale.ROOT, "%.2f", accelCenti / 100.0F) : "既定";

            // --- 最高速度 ---
            addButton(left + 6, top + 22, 26, "-10", "set_max_speed", Math.max(0, effMaxSpeed(maxSpeed) - 10));
            addButton(left + 34, top + 22, 24, "-1", "set_max_speed", Math.max(0, effMaxSpeed(maxSpeed) - 1));
            addButton(left + 106, top + 22, 24, "+1", "set_max_speed", Math.min(1000, effMaxSpeed(maxSpeed) + 1));
            addButton(left + 132, top + 22, 26, "+10", "set_max_speed", Math.min(1000, effMaxSpeed(maxSpeed) + 10));

            // --- 加速度 (km/h/s ×100 で保持) ---
            addButton(left + 6, top + 62, 26, "-50", "set_acceleration", Math.max(0, effAccel(accelCenti) - 50));
            addButton(left + 34, top + 62, 24, "-10", "set_acceleration", Math.max(0, effAccel(accelCenti) - 10));
            addButton(left + 106, top + 62, 24, "+10", "set_acceleration", Math.min(10000, effAccel(accelCenti) + 10));
            addButton(left + 132, top + 62, 26, "+50", "set_acceleration", Math.min(10000, effAccel(accelCenti) + 50));

            // --- それぞれを既定値へ戻す ---
            addButton(left + 6, top + 96, PANEL_W - 12, 20, "速度を戻す", "set_max_speed", 0);
            addRenderableWidget(Button.builder(Component.literal("加速度を戻す"),
                    b -> send("set_acceleration", 0))
                    .bounds(left + 6, top + 120, PANEL_W - 12, 20).build());
        }
        addRenderableWidget(new DoorButton(left + PANEL_W + 20, top + 20, false));
        addRenderableWidget(new DoorButton(left - 84, top + 20, true));
    }

    // 速度設定タブの表示ラベル (init で組み立て、render で描く)
    private String graphics_maxSpeedLabel = "";
    private String graphics_accelLabel = "";

    /** 未設定(0)のときは既定値を初期値として扱う (増減の起点)。 */
    private int effMaxSpeed(int configured) {
        if (configured > 0) {
            return configured;
        }
        float[] ms = train.getConfig().maxSpeed;
        return ms != null && ms.length > 0 ? Math.round(ms[ms.length - 1] * 72.0F) : 130;
    }

    /** 加速度の起点 (km/h/s ×100)。 */
    private int effAccel(int configured) {
        if (configured > 0) {
            return configured;
        }
        return Math.round(train.getConfig().accelerateion * 72.0F * 20.0F * 100.0F);
    }

    private String[] rollsignNames() {
        return train.getConfig().rollsignNames;
    }

    private Button addButton(int x, int y, int w, String label, String action, int value) {
        return addButton(x, y, w, 20, label, action, value);
    }

    private Button addButton(int x, int y, int w, int h, String label, String action, int value) {
        Button button = Button.builder(Component.literal(label), b -> send(action, value)).bounds(x, y, w, h).build();
        if ("noop".equals(action)) {
            button.active = false;
        }
        return addRenderableWidget(button);
    }

    private void addArrowButton(int x, int y, String label, String action, int value) {
        addArrowButton(x, y, 20, label, action, value);
    }

    private void addArrowButton(int x, int y, int h, String label, String action, int value) {
        Button button = Button.builder(Component.literal(label), b -> send(action, value)).bounds(x, y, 20, h).build();
        if ("noop".equals(action)) {
            button.active = false;
        }
        addRenderableWidget(button);
    }

    private String lightLabel() {
        return switch (train.getTrainStateData(TrainStateType.State_Light.id)) {
            case 1 -> "前照灯";
            case 2 -> "前照灯・尾灯";
            default -> "消灯";
        };
    }

    private int nextLightMode() {
        return switch (train.getTrainStateData(TrainStateType.State_Light.id)) {
            case 0 -> 1;
            case 1 -> 2;
            default -> 0;
        };
    }

    private String interiorLightLabel() {
        return train.getTrainStateData(TrainStateType.State_InteriorLight.id) != 0 ? "室内灯 ON" : "室内灯 OFF";
    }

    private String pantographLabel() {
        return train.getTrainStateData(TrainStateType.State_Pantograph.id) != 0 ? "パンタ 上" : "パンタ 下";
    }

    /**
     * このパックが持つアナウンスの数。0 ならアナウンス機能なし。
     * 表示名 (sound_Announcement の 1 列目) は省略されることがあるので、
     * 数は音声の数で数える。名前だけで数えると、名前が無いアナウンスを取りこぼす。
     */
    private int announcementCount() {
        VehicleDefinition definition = VehicleRegistry.getById(train.getModelName());
        return definition == null ? 0 : definition.getAnnouncementSounds().size();
    }

    /**
     * 本家 sound_Announcement は [[表示名, 音声パス], ...] なので、指定された表示名を出す。
     * 名前が未指定 / パック未登録のときだけ従来の「アナウンス N」にフォールバックする。
     */
    private String announcementLabel(int announce) {
        VehicleDefinition definition = VehicleRegistry.getById(train.getModelName());
        List<String> names = definition != null ? definition.getAnnouncementNames() : List.of();
        if (announce >= 0 && announce < names.size()) {
            String name = names.get(announce);
            if (name != null && !name.isBlank()) {
                return name;
            }
        }
        return "アナウンス " + (announce + 1);
    }

    private String destinationLabel() {
        String[] names = rollsignNames();
        if (names.length == 0) {
            return "方向幕 null";
        }
        int dest = Math.floorMod(train.getTrainStateData(TrainStateType.State_Destination.id), names.length);
        return "方向幕 " + names[dest];
    }

    // RTMU 追加: 種別幕 (typeSignNames)。方向幕とは別の State_Type で選ぶ。
    private String[] typeSignNames() {
        VehicleDefinition def = VehicleRegistry.getById(train.getModelName());
        return def == null ? new String[0] : def.getTypeSignNames().toArray(new String[0]);
    }

    private String typeLabel() {
        String[] names = typeSignNames();
        if (names.length == 0) {
            return "種別 null";
        }
        int type = Math.floorMod(train.getTrainStateData(TrainStateType.State_Type.id), names.length);
        return "種別 " + names[type];
    }

    private void send(String action, int value) {
        if ("noop".equals(action)) {
            return;
        }
        // カスタムボタンは描画がクライアント DataMap を読むためローカルにも反映
        if ("cycle_custom_button".equals(action)) {
            int index = (value >>> 8) & 0xFF;
            int current = value & 0xFF;
            VehicleDefinition definition = VehicleRegistry.getById(train.getModelName());
            List<List<String>> options = definition != null ? definition.getCustomButtonOptions() : List.of();
            int next = (index < options.size() && !options.get(index).isEmpty())
                    ? (current + 1) % options.get(index).size()
                    : (current == 0 ? 1 : 0);
            train.getResourceState().getDataMap().setInt("Button" + index, next, 0);
        } else if ("set_custom_button".equals(action)) {
            train.getResourceState().getDataMap().setInt("Button" + ((value >>> 8) & 0xFF), value & 0xFF, 0);
        }
        PacketDistributor.sendToServer(new TrainControlPayload(train.getId(), action, value));
        // スライダーはドラッグ中に毎回 applyValue が呼ばれる — 再構築すると
        // ウィジェットが差し替わってドラッグが切れ、値が動かなくなる
        if (!"set_custom_button".equals(action)) {
            rebuildTabWidgets();
        }
    }

    /** スライダー型カスタムボタン (0-100%)。ドラッグ確定ごとに DataMap Button{i} を送信。 */
    private class CustomSliderButton extends net.minecraft.client.gui.components.AbstractSliderButton {
        private final String label;
        private final int index;

        CustomSliderButton(int x, int y, int w, int h, String label, int index, int initialPercent) {
            super(x, y, w, h, Component.literal(label + " " + initialPercent + "%"), initialPercent / 100.0D);
            this.label = label;
            this.index = index;
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(this.label + " " + currentPercent() + "%"));
        }

        @Override
        protected void applyValue() {
            // 0 は「未設定=100%」の予約値なので最低 1
            int percent = Math.max(1, currentPercent());
            train.getResourceState().getDataMap().setInt("Button" + this.index, percent, 0);
            PacketDistributor.sendToServer(new TrainControlPayload(
                    train.getId(), "set_custom_button", (this.index << 8) | percent));
        }

        private int currentPercent() {
            return (int) Math.round(this.value * 100.0D);
        }
    }

    /** TrainState はサーバー→クライアント同期のため、少し遅れてラベルへ反映する */
    @Override
    public void tick() {
        super.tick();
        // スライダードラッグ中に再構築するとドラッグ状態が失われる
        if (this.isDragging()) {
            return;
        }
        if (++refreshTimer >= 5) {
            refreshTimer = 0;
            rebuildTabWidgets();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            ControlTab tab = tabAt(mouseX, mouseY);
            if (tab != null) {
                selectedTab = tab;
                rebuildTabWidgets();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private ControlTab tabAt(double mouseX, double mouseY) {
        int left = leftPos();
        int top = topPos();
        for (ControlTab tab : ControlTab.values()) {
            int x = tabX(left, tab);
            int y = top - 28;
            if (mouseX >= x && mouseX < x + TAB_W && mouseY >= y && mouseY < y + TAB_H) {
                return tab;
            }
        }
        return null;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int left = leftPos();
        int top = topPos();
        renderTabs(graphics, left, top, false);
        graphics.blit(selectedTab.background, left - 1, top - 1, 0, 0, PANEL_W, PANEL_H, 256, 256);
        renderTabs(graphics, left, top, true);
        for (Renderable renderable : renderables) {
            renderable.render(graphics, mouseX, mouseY, partialTick);
        }
        renderTabContents(graphics, left, top);
        renderPlayerInventory(graphics, left, top);
    }

    private void renderTabContents(GuiGraphics graphics, int left, int top) {
        if (selectedTab == ControlTab.DEDICATED) {
            // 速度設定タブ: 見出しと現在値
            graphics.drawString(this.font, Component.literal("最高速度 (km/h)"), left + 8, top + 8, 0xFF404040, false);
            graphics.drawString(this.font, Component.literal(graphics_maxSpeedLabel),
                    left + 62, top + 28, 0xFF202020, false);
            graphics.drawString(this.font, Component.literal("加速度 (km/h/s)"), left + 8, top + 48, 0xFF404040, false);
            graphics.drawString(this.font, Component.literal(graphics_accelLabel),
                    left + 62, top + 68, 0xFF202020, false);
            return;
        }
        if (selectedTab != ControlTab.FORMATION) {
            return;
        }
        // 本家 GuiVehicleControlPanel の編成タブ移植: 連結中の各車両を tab_formation.png の
        // 車両スプライトで並べる。u=制御車で右へ+32、v=先頭(0)/中間(1)/最後尾(2)、5両で改行。
        jp.ngt.rtm.entity.train.util.Formation formation = train.getFormation();
        int size = formation == null ? 0 : formation.size();
        if (size <= 0) {
            graphics.drawString(font, Component.literal("編成情報なし"), left + 8, top + 10, 0x404040, false);
            return;
        }
        graphics.drawString(font, Component.literal(size + "両編成"), left + 8, top + 8, 0x404040, false);
        for (int i = 0; i < size; i++) {
            jp.ngt.rtm.entity.train.util.FormationEntry entry = formation.get(i);
            if (entry == null || entry.train == null) {
                continue;
            }
            int u = entry.train.isControlCar() ? 1 : 0;
            int v = (i == 0) ? 0 : (i == size - 1 ? 2 : 1);
            int x = left + 8 + (i % 5) * 32;
            int y = top + 25 + (i / 5) * 32;
            graphics.blit(TAB_FORMATION_TEXTURE, x, y, 192 + u * 32, v * 16, 32, 16, 256, 256);
            // 自分が乗っている(=このメニューを開いた)車両に「ここにいる」マーカー。
            if (entry.train == train) {
                graphics.blit(TAB_FORMATION_TEXTURE, x + 12, y - 16, 180, 0, 10, 16, 256, 256);
            }
            // 号車番号を中央に。
            String num = String.valueOf(entry.entryId + 1);
            graphics.drawString(font, num, x + 16 - font.width(num) / 2, y + 4, 0x000000, false);
        }
    }

    private void renderPlayerInventory(GuiGraphics graphics, int left, int top) {
        if (minecraft == null || minecraft.player == null) {
            return;
        }
        List<ItemStack> items = minecraft.player.getInventory().items;
        if (selectedTab == ControlTab.INVENTORY) {
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 9; col++) {
                    int index = 9 + row * 9 + col;
                    renderInventoryItem(graphics, items, index, left + 8 + col * 18, top + 84 + row * 18);
                }
            }
        }
        for (int col = 0; col < 9; col++) {
            renderInventoryItem(graphics, items, col, left + 8 + col * 18, top + 142);
        }
    }

    private void renderInventoryItem(GuiGraphics graphics, List<ItemStack> items, int index, int x, int y) {
        if (index < 0 || index >= items.size()) {
            return;
        }
        ItemStack stack = items.get(index);
        if (stack.isEmpty()) {
            return;
        }
        graphics.renderItem(stack, x, y);
        graphics.renderItemDecorations(font, stack, x, y);
    }

    private void renderTabs(GuiGraphics graphics, int left, int top, boolean selectedOnly) {
        for (ControlTab tab : ControlTab.values()) {
            if ((tab == selectedTab) != selectedOnly) {
                continue;
            }
            int x = tabX(left, tab);
            int y = top - 28;
            int bg = 0xFF707070;
            int light = 0xFF9A9A9A;
            int shadow = 0xFF4A4A4A;
            graphics.fill(x, y, x + TAB_W, y + TAB_H, 0xFFFFFFFF);
            graphics.fill(x + 2, y + 2, x + TAB_W - 2, y + TAB_H - 2, bg);
            graphics.fill(x + 2, y + 2, x + TAB_W - 2, y + 3, light);
            graphics.fill(x + 2, y + 2, x + 3, y + TAB_H - 2, light);
            graphics.fill(x + 2, y + TAB_H - 3, x + TAB_W - 2, y + TAB_H - 2, shadow);
            graphics.fill(x + TAB_W - 3, y + 2, x + TAB_W - 2, y + TAB_H - 2, shadow);
            graphics.renderFakeItem(tab.icon, x + 6, y + 8);
        }
    }

    private int tabX(int left, ControlTab tab) {
        int column = tab.ordinal() % 6;
        if (column == 5) {
            return left + PANEL_W - TAB_W;
        }
        return left + 28 * column + (column > 0 ? column : 0);
    }

    private int leftPos() {
        return (width - PANEL_W) / 2;
    }

    private int topPos() {
        return (height - PANEL_H) / 2;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.minecraft != null && this.minecraft.options.keyInventory.matches(keyCode, scanCode)) {
            this.onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // タブのアイコン (左から順に並ぶ)。
    // 触らない (入れ替えるとインベントリ内の全アイテムの見た目が変わってしまう)。
    private enum ControlTab {
        INVENTORY(TAB_INVENTORY_TEXTURE, new ItemStack(Blocks.CHEST)),
        SETTING(TAB_SETTING_TEXTURE, new ItemStack(RealTrainModUnofficialItems.CROWBAR_ITEM.get())),
        FUNCTION(TAB_SETTING_TEXTURE, new ItemStack(RealTrainModUnofficialItems.WRENCH_ITEM.get())),
        FORMATION(TAB_FORMATION_TEXTURE, new ItemStack(RealTrainModUnofficialItems.TRAIN_ITEM.get())),
        // 専用タブ: ドアカット・連結解除など運転士の追加操作。アイコンは鉄ドア (ドアカット由来)。
        DEDICATED(TAB_SETTING_TEXTURE, new ItemStack(net.minecraft.world.item.Items.IRON_DOOR));

        final ResourceLocation background;
        final ItemStack icon;

        ControlTab(ResourceLocation background, ItemStack icon) {
            this.background = background;
            this.icon = icon;
        }
    }

    private class DoorButton extends Button {
        private final boolean leftDoor;

        DoorButton(int x, int y, boolean leftDoor) {
            super(x, y, 64, 80, Component.empty(),
                    b -> RtmTrainControlScreen.this.send(leftDoor ? "toggle_door_left" : "toggle_door_right", 0),
                    DEFAULT_NARRATION);
            this.leftDoor = leftDoor;
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int doorState = train.getTrainStateData(TrainStateType.State_Door.id);
            // 車両自身のドア byte は常に「その車両の物理左右」(Formation が entry.dir で
            // 再配布済み)。運転士から見た左右は運転台向き (cabDir) だけで決まる。
            boolean dir = (train.getCabDirection() & 1) == 0;
            int bit = leftDoor ? (dir ? 1 : 2) : (dir ? 2 : 1);
            boolean opened = (doorState & bit) == bit;
            int sliderOffset = opened ? -10 : -4;
            graphics.blit(TAB_INVENTORY_TEXTURE, getX() + 25, getY() + sliderOffset, 242, 80, 14, 100, 256, 256);
            graphics.blit(TAB_INVENTORY_TEXTURE, getX(), getY(), 192, 0, 64, 80, 256, 256);
            graphics.blit(TAB_INVENTORY_TEXTURE, getX() + 44, getY() + 48, 224, opened ? 80 : 88, 8, 8, 256, 256);
        }
    }
}
