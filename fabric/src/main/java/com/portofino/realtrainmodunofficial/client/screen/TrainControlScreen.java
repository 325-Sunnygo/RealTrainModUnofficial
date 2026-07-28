package com.portofino.realtrainmodunofficial.client.screen;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import com.portofino.realtrainmodunofficial.RealTrainModUnofficialItems;
import com.portofino.realtrainmodunofficial.client.sound.LegacyScriptSoundManager;
import com.portofino.realtrainmodunofficial.entity.TrainEntity;
import com.portofino.realtrainmodunofficial.network.TrainControlPayload;
import com.portofino.realtrainmodunofficial.vehicle.VehicleDefinition;
import com.portofino.realtrainmodunofficial.vehicle.VehicleRegistry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

public class TrainControlScreen extends Screen {
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

    private final TrainEntity train;
    private ControlTab selectedTab = ControlTab.SETTING;

    public TrainControlScreen(TrainEntity train) {
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
            addButton(left + 90, top + 28, 27, "前", "set_reverser", 1).active = train.getReverser() != 1;
            addButton(left + 117, top + 28, 28, "中", "set_reverser", 0).active = train.getReverser() != 0;
            addButton(left + 145, top + 28, 27, "後", "set_reverser", -1).active = train.getReverser() != -1;
            addArrowButton(left + 4, top + 52, "<", "noop");
            addButton(left + 28, top + 52, 120, "チャンクロード", "noop", 0);
            addArrowButton(left + 152, top + 52, ">", "noop");
            //方向幕: 持っていないパックでは null と出して押せなくする
            if (rollsignCount() == 0) {
                addArrowButton(left + 4, top + 76, "<", "noop");
                addButton(left + 28, top + 76, 120, "方向幕 null", "noop", 0);
                addArrowButton(left + 152, top + 76, ">", "noop");
            } else {
                addArrowButton(left + 4, top + 76, "<", "prev_destination");
                addButton(left + 28, top + 76, 120, destinationLabel(), "next_destination", 0);
                addArrowButton(left + 152, top + 76, ">", "next_destination");
            }
            //アナウンスも方向幕と同じ回し方にする (最後まで行ったら最初に戻る)。
            //アナウンスを持たないパックでは null と出して、押せなくする。
            if (announcementCount() == 0) {
                addArrowButton(left + 4, top + 100, "<", "noop");
                addButton(left + 28, top + 100, 120, "アナウンス null", "noop", 0);
                addArrowButton(left + 152, top + 100, ">", "noop");
            } else {
                addArrowButton(left + 4, top + 100, "<", "prev_sound");
                addButton(left + 28, top + 100, 120, announcementLabel(), "next_sound", 0);
                addArrowButton(left + 152, top + 100, ">", "next_sound");
            }
        } else if (selectedTab == ControlTab.FUNCTION) {
            VehicleDefinition definition = VehicleRegistry.getById(train.getVehicleId());
            List<List<String>> options = resolveCustomButtonOptions(definition);
            List<String> labels = resolveCustomButtonLabels(definition, options);
            for (int i = 0; i < Math.min(18, labels.size()); i++) {
                int x = left + 4 + (i % 3) * 56;
                int y = top + 4 + (i / 3) * 24;
                int index = i;
                int value = train.getCustomButtonValue(i);
                String text = resolveCustomButtonDisplayText(labels.get(i), options, i, value);
                int packed = (index << 8) | (value & 0xFF);
                addRenderableWidget(Button.builder(Component.literal(text), b -> send("cycle_custom_button", packed))
                    .bounds(x, y, 52, 22).build());
            }
        }
        addDoorButton(left + PANEL_W + 20, top + 20, false);
        addDoorButton(left - 84, top + 20, true);
    }

    private Button addButton(int x, int y, int w, String label, String action, int value) {
        Button button = Button.builder(Component.literal(label), b -> send(action, value)).bounds(x, y, w, 20).build();
        if ("noop".equals(action)) {
            button.active = false;
        }
        return addRenderableWidget(button);
    }

    private void addArrowButton(int x, int y, String label, String action) {
        Button button = Button.builder(Component.literal(label), b -> send(action, 0)).bounds(x, y, 20, 20).build();
        if ("noop".equals(action)) {
            button.active = false;
        }
        addRenderableWidget(button);
    }

    private void addDoorButton(int x, int y, boolean leftDoor) {
        addRenderableWidget(new DoorButton(x, y, leftDoor));
    }

    private List<String> resolveCustomButtonLabels(VehicleDefinition def, List<List<String>> options) {
        List<String> labels = new ArrayList<>();
        if (options != null && !options.isEmpty()) {
            for (List<String> optionList : options) {
                labels.add(optionList.isEmpty() ? "" : optionList.get(0));
            }
        }
        if (def != null) {
            if (labels.isEmpty()) {
                labels.addAll(def.getCustomButtonNames());
            }
        }
        if (labels.isEmpty()) {
            for (int i = 0; i < 16; i++) {
                String value = firstNonBlank(
                    train.getScriptDataValue("ButtonName" + i),
                    train.getScriptDataValue("buttonName" + i),
                    train.getScriptDataValue("customButtonName" + i),
                    train.getScriptDataValue("CustomButtonName" + i)
                );
                if (!value.isBlank()) {
                    labels.add(value);
                }
            }
        }
        if (labels.isEmpty()) {
            int count = 0;
            for (int i = 0; i < 31; i++) {
                if (train.getCustomButtonValue(i) != 0 || !train.getScriptDataValue("Button" + i).isBlank()) {
                    count = i + 1;
                }
            }
            for (int i = 0; i < count; i++) {
                labels.add("カスタム" + (i + 1));
            }
        }
        return labels;
    }

    private List<List<String>> resolveCustomButtonOptions(VehicleDefinition def) {
        if (def != null && !def.getCustomButtonOptions().isEmpty()) {
            return def.getCustomButtonOptions();
        }
        return List.of();
    }

    private String resolveCustomButtonDisplayText(String fallbackLabel, List<List<String>> options, int index, int value) {
        if (options != null && index >= 0 && index < options.size()) {
            List<String> optionList = options.get(index);
            if (!optionList.isEmpty()) {
                int current = Math.floorMod(value, optionList.size());
                return optionList.get(current);
            }
        }
        return fallbackLabel + (value != 0 ? " ON" : " OFF");
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private String lightLabel() {
        return switch (train.getLightMode()) {
            case 1 -> "前照灯";
            case 3 -> "前照灯・尾灯";
            default -> "消灯";
        };
    }

    private String interiorLightLabel() {
        return train.isInteriorLightOn() ? "室内灯 ON" : "室内灯 OFF";
    }

    private int nextLightMode() {
        return switch (train.getLightMode()) {
            case 0 -> 1;
            case 1 -> 3;
            default -> 0;
        };
    }

    private String pantographLabel() {
        return train.isPantographUp() ? "パンタ 上" : "パンタ 下";
    }

    /**
     * このパックが持つアナウンスの数。0 ならアナウンス機能なし。
     * <p>
     * 表示名 (sound_Announcement の 1 列目) は省略されることがあるので、数は<b>音声の数</b>で
     * 数える。名前だけで数えると、名前が無いアナウンスを取りこぼす。
     */
    private int announcementCount() {
        VehicleDefinition definition = VehicleRegistry.getById(train.getVehicleId());
        return definition == null ? 0 : definition.getAnnouncementSounds().size();
    }

    /**
     * 本家 sound_Announcement は [[表示名, 音声パス], ...] なので、指定された表示名を出す。
     * 名前が未指定 / パック未登録のときだけ従来の「アナウンス N」にフォールバックする。
     */
    private String announcementLabel() {
        int count = announcementCount();
        if (count == 0) {
            return "アナウンス null";
        }
        int index = Math.floorMod(train.getSoundIndex(), count);
        VehicleDefinition definition = VehicleRegistry.getById(train.getVehicleId());
        List<String> names = definition != null ? definition.getAnnouncementNames() : List.of();
        if (index >= 0 && index < names.size()) {
            String name = names.get(index);
            if (name != null && !name.isBlank()) {
                return name;
            }
        }
        return "アナウンス " + (index + 1);
    }

    /** このパックが持つ方向幕のコマ数。0 なら方向幕なし。 */
    private int rollsignCount() {
        return train.getResourceState().getResourceSet().getConfig().rollsignNames.length;
    }

    private String destinationLabel() {
        int count = rollsignCount();
        if (count == 0) {
            return "方向幕 null";
        }
        String[] names = train.getResourceState().getResourceSet().getConfig().rollsignNames;
        return "方向幕 " + names[Math.floorMod(train.getDestinationIndex(), count)];
    }

    private void send(String action, int value) {
        if ("noop".equals(action)) {
            return;
        }
        if (shouldPlayLeverClick(action)) {
            LegacyScriptSoundManager.playLeverClick();
        }
        applyLocal(action, value);
        PacketDistributor.sendToServer(new TrainControlPayload(train.getId(), action, value));
        rebuildTabWidgets();
    }

    private static boolean shouldPlayLeverClick(String action) {
        return action != null && (action.startsWith("mascon_") || "set_reverser".equals(action));
    }

    private void applyLocal(String action, int value) {
        switch (action) {
            case "set_light_mode" -> train.setLightMode(value);
            case "toggle_interior_light" -> train.setInteriorLightOn(!train.isInteriorLightOn());
            case "toggle_door" -> train.setDoorOpen(!train.isDoorOpen());
            case "toggle_door_left" -> train.setDoorLeftOpen(!train.isDoorLeftOpen());
            case "toggle_door_right" -> train.setDoorRightOpen(!train.isDoorRightOpen());
            case "toggle_pantograph" -> train.setPantographUp(!train.isPantographUp());
            case "set_reverser" -> train.setReverser(value);
            case "mascon_neutral" -> train.setNotch(0);
            case "mascon_power" -> train.stepMascon(1);
            case "mascon_brake" -> train.stepMascon(-1);
            case "next_destination" -> {
                int count = Math.max(1, train.getResourceState().getResourceSet().getConfig().rollsignNames.length);
                train.setDestinationIndex((train.getDestinationIndex() + 1) % count);
            }
            case "prev_destination" -> {
                int count = Math.max(1, train.getResourceState().getResourceSet().getConfig().rollsignNames.length);
                train.setDestinationIndex(Math.floorMod(train.getDestinationIndex() - 1, count));
            }
            case "next_sound" -> train.setSoundIndex(resolveNextSoundIndex(1));
            case "prev_sound" -> train.setSoundIndex(resolveNextSoundIndex(-1));
            case "toggle_custom_button" -> train.toggleCustomButton(value);
            case "cycle_custom_button" -> {
                int index = (value >>> 8) & 0xFF;
                int currentValue = value & 0xFF;
                VehicleDefinition definition = VehicleRegistry.getById(train.getVehicleId());
                List<List<String>> options = resolveCustomButtonOptions(definition);
                int nextValue;
                if (index >= 0 && index < options.size() && !options.get(index).isEmpty()) {
                    nextValue = (currentValue + 1) % options.get(index).size();
                } else {
                    nextValue = currentValue == 0 ? 1 : 0;
                }
                train.setCustomButtonValue(index, nextValue);
            }
            default -> {
            }
        }
    }

    private int resolveNextSoundIndex(int delta) {
        int size = announcementCount();
        if (size <= 0) {
            //アナウンスが無いパックで番号だけが際限なく増えないようにする
            return 0;
        }
        return Math.floorMod(train.getSoundIndex() + delta, size);
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
            int y = tab.isTop ? top - 28 : top + PANEL_H - 4;
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
        if (selectedTab != ControlTab.FORMATION) {
            return;
        }
        List<TrainEntity> trains = train.getFormationTrainsForDisplay();
        int count = Math.max(1, trains.size());
        graphics.drawString(font, Component.literal(count + "両編成"), left + 8, top + 10, 0x404040, false);
        for (int i = 0; i < Math.min(6, count); i++) {
            TrainEntity entry = i < trains.size() ? trains.get(i) : train;
            VehicleDefinition def = VehicleRegistry.getById(entry.getVehicleId());
            String name = def == null ? entry.getVehicleId() : def.getDisplayName();
            int y = top + 30 + i * 18;
            graphics.renderFakeItem(new ItemStack(RealTrainModUnofficialItems.TRAIN_ITEM.get()), left + 10, y - 4);
            graphics.drawString(font, Component.literal((i + 1) + "  " + name), left + 30, y, 0x404040, false);
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
            int y = tab.isTop ? top - 28 : top + PANEL_H - 4;
            int bg = 0xFF707070;
            int light = 0xFF9A9A9A;
            int shadow = 0xFF4A4A4A;
            graphics.fill(x, y, x + TAB_W, y + TAB_H, 0xFFFFFFFF);
            graphics.fill(x + 2, y + 2, x + TAB_W - 2, y + TAB_H - 2, bg);
            graphics.fill(x + 2, y + 2, x + TAB_W - 2, y + 3, light);
            graphics.fill(x + 2, y + 2, x + 3, y + TAB_H - 2, light);
            graphics.fill(x + 2, y + TAB_H - 3, x + TAB_W - 2, y + TAB_H - 2, shadow);
            graphics.fill(x + TAB_W - 3, y + 2, x + TAB_W - 2, y + TAB_H - 2, shadow);
            graphics.renderFakeItem(tab.icon, x + 6, y + (tab.isTop ? 8 : 7));
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

    private enum ControlTab {
        SETTING("Setting", TAB_SETTING_TEXTURE, new ItemStack(RealTrainModUnofficialItems.WRENCH_ITEM.get()), true),
        FUNCTION("Function", TAB_SETTING_TEXTURE, new ItemStack(RealTrainModUnofficialItems.CROWBAR_ITEM.get()), true),
        FORMATION("Formation", TAB_FORMATION_TEXTURE, new ItemStack(RealTrainModUnofficialItems.TRAIN_ITEM.get()), true),
        INVENTORY("Player Inventory", TAB_INVENTORY_TEXTURE, new ItemStack(Blocks.CHEST), true);

        final String label;
        final ResourceLocation background;
        final ItemStack icon;
        final boolean isTop;

        ControlTab(String label, ResourceLocation background, ItemStack icon, boolean isTop) {
            this.label = label;
            this.background = background;
            this.icon = icon;
            this.isTop = isTop;
        }
    }

    private class DoorButton extends Button {
        private final boolean leftDoor;

        DoorButton(int x, int y, boolean leftDoor) {
            super(x, y, 64, 80, Component.empty(), b -> send(leftDoor ? "toggle_door_left" : "toggle_door_right", 0), DEFAULT_NARRATION);
            this.leftDoor = leftDoor;
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            boolean opened = leftDoor ? train.isDoorLeftOpen() : train.isDoorRightOpen();
            int sliderOffset = opened ? -10 : -4;
            graphics.blit(TAB_INVENTORY_TEXTURE, getX() + 25, getY() + sliderOffset, 242, 80, 14, 100, 256, 256);
            graphics.blit(TAB_INVENTORY_TEXTURE, getX(), getY(), 192, 0, 64, 80, 256, 256);
            graphics.blit(TAB_INVENTORY_TEXTURE, getX() + 44, getY() + 48, 224, opened ? 80 : 88, 8, 8, 256, 256);
        }
    }
}
