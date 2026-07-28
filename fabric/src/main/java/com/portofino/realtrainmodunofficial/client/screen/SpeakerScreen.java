package com.portofino.realtrainmodunofficial.client.screen;

import com.portofino.realtrainmodunofficial.blockentity.InstalledObjectBlockEntity;
import com.portofino.realtrainmodunofficial.installedobject.SpeakerSoundConfig;
import com.portofino.realtrainmodunofficial.network.ConfigureSpeakerPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * スピーカー設定GUI（本家 RTM GuiSpeaker 風のスロット選択方式）。
 * <ul>
 *   <li>左: レッドストーン信号強度 1〜15 のスロット一覧（縦スクロール可）。各行に「select」ボタン。</li>
 *   <li>select を押すと右にサウンド一覧（検索付き・スクロール可）が出て、クリックでそのスロットに割り当てる。</li>
 *   <li>上に可聴範囲(ブロック)。</li>
 * </ul>
 * バックエンド（ConfigureSpeakerPayload / InstalledObjectBlockEntity の per-block 音・範囲）は従来通り。
 */
public class SpeakerScreen extends Screen {
    private static final int SLOT_COUNT = 15;
    private static final int SLOT_ROW_H = 16;
    private static final int SOUND_ROW_H = 14;

    private final BlockPos pos;
    private EditBox rangeBox;
    private EditBox searchBox;
    private int speakerRange = 32;

    private int selectedSlot = -1;           // select 中のスロット (信号強度)。-1 = 未選択
    private int slotScroll = 0;              // スロット一覧のスクロール位置
    private int soundScroll = 0;             // サウンド一覧のスクロール位置

    private int slotTop, slotRowsVisible, slotLabelX, slotSelectX, slotSelectW;
    private int soundListX, soundListTop, soundListRows, soundListW;

    private final List<Button> slotSelectButtons = new ArrayList<>();
    private final List<ResourceLocation> filteredSounds = new ArrayList<>();
    private final List<Button> soundButtons = new ArrayList<>();

    public SpeakerScreen(BlockPos pos) {
        super(Component.literal("スピーカー設定"));
        this.pos = pos.immutable();
    }

    @Override
    protected void init() {
        readState();

        int margin = 12;
        int top = 24;

        // 可聴範囲 (上部)
        rangeBox = new EditBox(font, margin, top, 70, 18, Component.literal("可聴範囲(ブロック)"));
        rangeBox.setMaxLength(4);
        rangeBox.setValue(Integer.toString(speakerRange));
        addRenderableWidget(rangeBox);
        addRenderableWidget(Button.builder(Component.literal("範囲を設定"), b -> submitRange())
            .bounds(margin + 74, top, 96, 18).build());

        // 左: スロット一覧レイアウト (縦スクロール可)
        slotTop = top + 28;
        int slotLabelW = 150;
        slotLabelX = margin;
        slotSelectX = margin + slotLabelW;
        slotSelectW = 54;
        int bottomLimit = height - 30;                     // 完了ボタンの上まで
        slotRowsVisible = Math.max(1, Math.min(SLOT_COUNT, (bottomLimit - slotTop) / SLOT_ROW_H));

        // 右: サウンド一覧レイアウト
        soundListX = slotSelectX + slotSelectW + 16;
        soundListW = Math.max(140, width - soundListX - margin);
        searchBox = new EditBox(font, soundListX, top, Math.min(soundListW, 220), 18, Component.literal("音を検索"));
        searchBox.setMaxLength(64);
        searchBox.setResponder(s -> { soundScroll = 0; rebuildSoundList(); });
        addRenderableWidget(searchBox);
        soundListTop = top + 24;
        soundListRows = Math.max(4, (bottomLimit - soundListTop) / SOUND_ROW_H);

        // 完了
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
            .bounds(margin, height - 26, 100, 20).build());

        rebuildSlots();
        rebuildSoundList();
    }

    private void readState() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.getBlockEntity(pos) instanceof InstalledObjectBlockEntity be) {
            speakerRange = be.getSpeakerRange();
        }
    }

    private int maxSlotScroll() {
        return Math.max(0, SLOT_COUNT - slotRowsVisible);
    }

    /** 表示中のスロット窓だけ select ボタンを作る (縦スクロール分だけずらす)。 */
    private void rebuildSlots() {
        for (Button b : slotSelectButtons) {
            removeWidget(b);
        }
        slotSelectButtons.clear();
        slotScroll = Math.max(0, Math.min(maxSlotScroll(), slotScroll));
        for (int row = 0; row < slotRowsVisible; row++) {
            final int level = slotScroll + row + 1;   // 1..15
            if (level > SLOT_COUNT) {
                break;
            }
            int y = slotTop + row * SLOT_ROW_H;
            Button sel = Button.builder(Component.literal("select"), b -> selectSlot(level))
                .bounds(slotSelectX, y, slotSelectW, SLOT_ROW_H - 2).build();
            addRenderableWidget(sel);
            slotSelectButtons.add(sel);
        }
    }

    private void selectSlot(int level) {
        selectedSlot = level;
        soundScroll = 0;
        rebuildSoundList();
    }

    /** 検索語でフィルタしたサウンド一覧を、選択スロットがあるときだけ表示する。 */
    private void rebuildSoundList() {
        for (Button b : soundButtons) {
            removeWidget(b);
        }
        soundButtons.clear();
        filteredSounds.clear();
        if (selectedSlot < 0) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.getSoundManager() == null) {
            return;
        }
        String query = searchBox == null ? "" : searchBox.getValue().trim().toLowerCase(Locale.ROOT);
        List<ResourceLocation> all = new ArrayList<>(mc.getSoundManager().getAvailableSounds());
        all.sort((a, b) -> a.toString().compareTo(b.toString()));
        for (ResourceLocation id : all) {
            if (query.isEmpty() || id.toString().toLowerCase(Locale.ROOT).contains(query)) {
                filteredSounds.add(id);
            }
        }

        int shown = 0;
        // 先頭に「（なし / 割り当て解除）」
        Button clear = Button.builder(Component.literal("（なし / 解除）"), b -> assignSound(""))
            .bounds(soundListX, soundListTop, soundListW, SOUND_ROW_H - 1).build();
        addRenderableWidget(clear);
        soundButtons.add(clear);
        shown++;

        soundScroll = Math.max(0, Math.min(Math.max(0, filteredSounds.size() - 1), soundScroll));
        for (int i = soundScroll; i < filteredSounds.size() && shown < soundListRows; i++) {
            final String chosen = filteredSounds.get(i).toString();
            Button b = Button.builder(Component.literal(chosen), btn -> assignSound(chosen))
                .bounds(soundListX, soundListTop + shown * SOUND_ROW_H, soundListW, SOUND_ROW_H - 1)
                .build();
            addRenderableWidget(b);
            soundButtons.add(b);
            shown++;
        }
    }

    private void assignSound(String sound) {
        if (selectedSlot < 1 || selectedSlot > 15) {
            return;
        }
        PacketDistributor.sendToServer(new ConfigureSpeakerPayload(pos, selectedSlot, sound, 0));
        toast(sound.isEmpty()
            ? ("信号強度 " + selectedSlot + " の割り当てを解除しました")
            : ("信号強度 " + selectedSlot + " → " + sound));
        selectedSlot = -1;
        rebuildSoundList();
    }

    private void submitRange() {
        try {
            int range = Integer.parseInt(rangeBox.getValue().trim());
            speakerRange = Math.max(1, range);
            PacketDistributor.sendToServer(new ConfigureSpeakerPayload(pos, 0, "", speakerRange));
            toast("可聴範囲を " + speakerRange + " ブロックに設定しました");
        } catch (NumberFormatException ignored) {
            toast("数字で入力してください");
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int dir = -(int) Math.signum(scrollY);
        // 右のサウンド一覧の上 → サウンドをスクロール
        if (selectedSlot >= 0 && mouseX >= soundListX) {
            int max = Math.max(0, filteredSounds.size() - 1);
            soundScroll = Math.max(0, Math.min(max, soundScroll + dir));
            rebuildSoundList();
            return true;
        }
        // 左のスロット一覧の上 → スロットをスクロール
        if (mouseX < soundListX && maxSlotScroll() > 0) {
            slotScroll = Math.max(0, Math.min(maxSlotScroll(), slotScroll + dir));
            rebuildSlots();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private String currentSound(int level) {
        InstalledObjectBlockEntity be = speakerBe();
        String s = be != null ? be.getSpeakerSound(level) : SpeakerSoundConfig.getSound(level);
        return s == null ? "" : s;
    }

    private InstalledObjectBlockEntity speakerBe() {
        return minecraft != null && minecraft.level != null
            && minecraft.level.getBlockEntity(pos) instanceof InstalledObjectBlockEntity b ? b : null;
    }

    private void toast(String msg) {
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(Component.literal(msg), true);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 8, 0xFFFFFF);

        // 左: 表示中スロットの「N : 現在の音」ラベル
        for (int row = 0; row < slotRowsVisible; row++) {
            int level = slotScroll + row + 1;
            if (level > SLOT_COUNT) {
                break;
            }
            int y = slotTop + row * SLOT_ROW_H + 3;
            String snd = currentSound(level);
            String shown = snd.isEmpty() ? "null" : (snd.length() > 24 ? snd.substring(0, 23) + "…" : snd);
            int color = (level == selectedSlot) ? 0xFFFF66 : (snd.isEmpty() ? 0x888888 : 0xFFFFFF);
            graphics.drawString(font, Component.literal(level + " : " + shown), slotLabelX, y, color, false);
        }
        // スクロール可能なことを示す控えめな表示
        if (maxSlotScroll() > 0) {
            graphics.drawString(font, Component.literal("[" + (slotScroll + 1) + "-"
                + Math.min(SLOT_COUNT, slotScroll + slotRowsVisible) + "/" + SLOT_COUNT + " ホイールで移動]"),
                slotLabelX, slotTop - 11, 0xAAAAAA, false);
        }
        // 右: 見出し
        if (selectedSlot >= 0) {
            graphics.drawString(font, Component.literal("信号強度 " + selectedSlot + " に割り当てる音"),
                soundListX, soundListTop - 11, 0xFFFF66, false);
        } else {
            graphics.drawString(font, Component.literal("← select で信号強度を選ぶ"),
                soundListX, soundListTop, 0xAAAAAA, false);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
