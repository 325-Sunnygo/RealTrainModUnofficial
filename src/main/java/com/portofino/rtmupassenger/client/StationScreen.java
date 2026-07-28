package com.portofino.rtmupassenger.client;

import com.portofino.realtrainmodunofficial.network.SetStationTagsPayload;
import com.portofino.rtmupassenger.station.StationTag;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 駅ブロックの設定画面。右クリックで開く (旧: Shift+右クリックでの 1 個ずつ切替を置き換え)。
 * 住宅街/オフィス街/工業地帯/… の各タグを ON/OFF でき、複数付けられる。
 */
public class StationScreen extends Screen {

    private final BlockPos pos;
    /** 編集中のタグビット (1 << StationTag.ordinal の OR)。 */
    private int bits;

    public StationScreen(BlockPos pos, int bits) {
        super(Component.translatable("gui.realtrainmodunofficial.station.title"));
        this.pos = pos;
        this.bits = bits;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int w = 200;
        int y = this.height / 4 + 4;

        for (StationTag tag : StationTag.values()) {
            final int bit = 1 << tag.ordinal();
            addRenderableWidget(Button.builder(label(tag, bit), b -> {
                this.bits ^= bit;             //トグル
                b.setMessage(label(tag, bit));
            }).bounds(cx - w / 2, y, w, 20).build());
            y += 22;
        }

        y += 6;
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
            .bounds(cx - w / 2, y, w, 20).build());
    }

    private Component label(StationTag tag, int bit) {
        boolean on = (this.bits & bit) != 0;
        return Component.literal((on ? "§a☑ " : "§7☐ ") + tag.displayName());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, this.height / 4 - 24, 0xFFFFFF);
        graphics.drawCenteredString(this.font,
            Component.translatable("gui.realtrainmodunofficial.station.hint"),
            this.width / 2, this.height / 4 - 12, 0xFFA0A0A0);
    }

    @Override
    public void onClose() {
        // 選択したタグをサーバーへ送って保存 (駅の登録も兼ねる)。
        if (this.minecraft != null && this.minecraft.getConnection() != null) {
            PacketDistributor.sendToServer(new SetStationTagsPayload(this.pos, this.bits));
        }
        super.onClose();
    }
}
