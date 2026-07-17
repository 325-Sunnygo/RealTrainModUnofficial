package com.portofino.realtrainmodunofficial.client.screen;

import com.portofino.realtrainmodunofficial.RtmuSettings;
import com.portofino.realtrainmodunofficial.network.RtmuSettingsPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * RTMU 設定画面 (ポーズメニューの「RTMU設定」ボタンから開く)。
 * <ul>
 *   <li>カーブ自動カント: ON / OFF</li>
 *   <li>レール自動高さ: 1〜5 (レンチ高さ単位)</li>
 * </ul>
 * 変更は即クライアントへ保存し、サーバーへ同期する。
 */
public class RtmuSettingsScreen extends Screen {

    private final Screen parent;

    public RtmuSettingsScreen(Screen parent) {
        super(Component.literal("RTMU設定"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int y = this.height / 4 + 20;
        int w = 220;

        addRenderableWidget(CycleButton.onOffBuilder(RtmuSettings.autoCant)
            .create(cx - w / 2, y, w, 20,
                Component.literal("カーブ自動カント"),
                (btn, value) -> {
                    RtmuSettings.autoCant = value;
                    RtmuSettings.save();
                    sync();
                }));

        y += 24;
        addRenderableWidget(CycleButton.<Integer>builder(v -> Component.literal("レベル " + v))
            .withValues(List.of(1, 2, 3, 4, 5))
            .withInitialValue(RtmuSettings.clampLevel(RtmuSettings.autoHeightLevel))
            .create(cx - w / 2, y, w, 20,
                Component.literal("レール自動高さ"),
                (btn, value) -> {
                    RtmuSettings.autoHeightLevel = value;
                    RtmuSettings.save();
                    sync();
                }));

        y += 36;
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
            .bounds(cx - 100, y, 200, 20).build());
    }

    private void sync() {
        if (minecraft != null && minecraft.getConnection() != null) {
            PacketDistributor.sendToServer(
                new RtmuSettingsPayload(RtmuSettings.autoCant, RtmuSettings.autoHeightLevel));
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, this.height / 4 - 8, 0xFFFFFF);
        graphics.drawCenteredString(this.font,
            Component.literal("カーブに自動でカントを付け、レールを指定高さに揃えます"),
            this.width / 2, this.height / 4 + 6, 0xA0A0A0);
    }

    @Override
    public void onClose() {
        //閉じるときにも念のため同期
        sync();
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }
}
