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

    /** 「敷設」セクション見出しの y。render() でラベルを描くために保持。 */
    private int layingHeaderY;
    /** 「軽量化」セクション見出しの y。 */
    private int perfHeaderY;
    /** 「乗客」セクション見出しの y。 */
    private int passengerHeaderY;

    @Override
    protected void init() {
        int cx = this.width / 2;
        int w = 220;
        //上寄せ。項目が増えたので固定の開始位置から 24px 刻みで積む。
        int y = 36;

        // ===== 敷設 =====
        this.layingHeaderY = y;
        y += 14;

        addRenderableWidget(CycleButton.onOffBuilder(RtmuSettings.autoCant)
            .create(cx - w / 2, y, w, 20,
                Component.literal("カーブ自動カント"),
                (btn, value) -> {
                    RtmuSettings.autoCant = value;
                    RtmuSettings.save();
                    sync();
                }));

        y += 24;
        //レール自動高さ: 0=OFF、1〜16=高さ 0〜15 (1/16ブロック)。任意の高さ (9,10 等) を指定できる。
        addRenderableWidget(new AutoHeightSlider(cx - w / 2, y, w, 20));

        // ===== 軽量化 =====
        y += 30;
        this.perfHeaderY = y;
        y += 14;

        //レール描画距離: 64〜512 ブロック。遠くのレールが消えるときはこれを上げる。
        addRenderableWidget(new RailDistanceSlider(cx - w / 2, y, w, 20));

        y += 24;
        //車両描画距離: 0=無制限、32〜256。遠方車両を丸ごと省略して毎フレームのスクリプト実行を削る。
        addRenderableWidget(new VehicleDistanceSlider(cx - w / 2, y, w, 20));


        // ===== 乗客 =====
        y += 30;
        this.passengerHeaderY = y;
        y += 14;
        //乗客の最大数: 0(湧かない)〜100、その先は無制限。
        addRenderableWidget(new PassengerCapSlider(cx - w / 2, y, w, 20));

        y += 30;
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
            .bounds(cx - 100, y, 200, 20).build());
    }

    /** 乗客の最大数スライダー (0〜100、右端で無制限)。 */
    private class PassengerCapSlider extends net.minecraft.client.gui.components.AbstractSliderButton {
        private static final int MAX = RtmuSettings.MAX_PASSENGERS_UNLIMITED; //101 = 無制限

        PassengerCapSlider(int x, int y, int w, int h) {
            super(x, y, w, h, Component.empty(),
                RtmuSettings.clampMaxPassengers(RtmuSettings.maxPassengers) / (double) MAX);
            updateMessage();
        }

        private int currentCap() {
            return RtmuSettings.clampMaxPassengers((int) Math.round(this.value * MAX));
        }

        @Override
        protected void updateMessage() {
            int c = currentCap();
            String s = c >= RtmuSettings.MAX_PASSENGERS_UNLIMITED ? "無制限"
                : (c == 0 ? "0人 (湧かない)" : c + "人");
            setMessage(Component.literal("乗客の最大数: " + s));
        }

        @Override
        protected void applyValue() {
            RtmuSettings.maxPassengers = currentCap();
            RtmuSettings.save();
            sync();
        }
    }

    private static int clampThrottle(int v) {
        return Math.max(0, Math.min(2, v));
    }

    private static Component throttleLabel(int v) {
        return Component.literal(switch (v) {
            case 1 -> "省エネ";
            case 2 -> "積極";
            default -> "標準";
        });
    }

    /** レール自動高さスライダー (0=OFF、1〜16 → 高さ 0〜15)。 */
    private class AutoHeightSlider extends net.minecraft.client.gui.components.AbstractSliderButton {
        AutoHeightSlider(int x, int y, int w, int h) {
            super(x, y, w, h, Component.empty(),
                RtmuSettings.clampLevel(RtmuSettings.autoHeightLevel) / 16.0D);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            int level = (int) Math.round(this.value * 16.0D);
            setMessage(Component.literal("レール自動高さ: "
                + (level <= 0 ? "OFF" : (level - 1) + "/16")));
        }

        @Override
        protected void applyValue() {
            RtmuSettings.autoHeightLevel = (int) Math.round(this.value * 16.0D);
            RtmuSettings.save();
            sync();
        }
    }

    /** レール描画距離スライダー (64〜512 ブロック、16刻み)。 */
    private class RailDistanceSlider extends net.minecraft.client.gui.components.AbstractSliderButton {
        private static final int MIN = 64;
        private static final int MAX = 512;

        RailDistanceSlider(int x, int y, int w, int h) {
            super(x, y, w, h, Component.empty(),
                (RtmuSettings.clampRailRenderDistance(RtmuSettings.railRenderDistance) - MIN) / (double) (MAX - MIN));
            updateMessage();
        }

        private int currentDistance() {
            int raw = (int) Math.round(MIN + this.value * (MAX - MIN));
            return Math.max(MIN, Math.min(MAX, (raw / 16) * 16));  //16刻み
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal("レール描画距離: " + currentDistance() + "m"));
        }

        @Override
        protected void applyValue() {
            RtmuSettings.railRenderDistance = currentDistance();
            RtmuSettings.save();
            //描画距離はクライアントのみ (サーバー同期不要)
        }
    }

    /** 車両描画距離スライダー (0=無制限、32〜256、16刻み)。 */
    private static class VehicleDistanceSlider extends net.minecraft.client.gui.components.AbstractSliderButton {
        //0(無制限) を左端、32〜256 を連続に並べる。内部 value 0.0 = 無制限。
        private static final int MIN = 32;
        private static final int MAX = 256;

        VehicleDistanceSlider(int x, int y, int w, int h) {
            super(x, y, w, h, Component.empty(), initialValue());
            updateMessage();
        }

        private static double initialValue() {
            int d = RtmuSettings.clampVehicleRenderDistance(RtmuSettings.vehicleRenderDistance);
            if (d <= 0) {
                return 0.0D;
            }
            //無制限のぶん左端に幅を持たせる (value 0〜0.08 = 無制限帯)。
            return 0.08D + (1.0D - 0.08D) * (d - MIN) / (double) (MAX - MIN);
        }

        private int currentDistance() {
            if (this.value < 0.08D) {
                return 0;  //無制限
            }
            double t = (this.value - 0.08D) / (1.0D - 0.08D);
            int raw = (int) Math.round(MIN + t * (MAX - MIN));
            return Math.max(MIN, Math.min(MAX, (raw / 16) * 16));
        }

        @Override
        protected void updateMessage() {
            int d = currentDistance();
            setMessage(Component.literal("車両描画距離: " + (d <= 0 ? "無制限" : d + "m")));
        }

        @Override
        protected void applyValue() {
            RtmuSettings.vehicleRenderDistance = currentDistance();
            RtmuSettings.save();
        }
    }

    private void sync() {
        if (minecraft != null && minecraft.getConnection() != null) {
            PacketDistributor.sendToServer(
                new RtmuSettingsPayload(RtmuSettings.autoCant, RtmuSettings.autoHeightLevel,
                    RtmuSettings.maxPassengers));
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 16, 0xFFFFFF);
        //セクション見出し (左寄せ、少しくすんだ色)
        int cx = this.width / 2;
        int left = cx - 110;
        graphics.drawString(this.font, Component.literal("§l敷設"), left, this.layingHeaderY, 0xFFD0A040, false);
        graphics.drawString(this.font, Component.literal("§l軽量化"), left, this.perfHeaderY, 0xFF60C0FF, false);
        graphics.drawString(this.font,
            Component.literal("§7遠い車両/レールを間引いて FPS を稼ぐ (既定=無制限)"),
            left + 44, this.perfHeaderY, 0xFF808080, false);
        graphics.drawString(this.font, Component.literal("§l乗客"), left, this.passengerHeaderY, 0xFF80E080, false);
        graphics.drawString(this.font,
            Component.literal("§7駅に湧く乗客 NPC の上限 (自分から見える数)"),
            left + 44, this.passengerHeaderY, 0xFF808080, false);
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
