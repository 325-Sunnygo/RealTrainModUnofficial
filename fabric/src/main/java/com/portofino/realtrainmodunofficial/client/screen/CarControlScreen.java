package com.portofino.realtrainmodunofficial.client.screen;

import com.portofino.realtrainmodunofficial.entity.CarEntity;
import com.portofino.realtrainmodunofficial.vehicle.VehicleDefinition;
import com.portofino.realtrainmodunofficial.vehicle.VehicleRegistry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 自動車 (ModelVehicle) のカスタムボタン (customButtons) 操作画面。
 *
 * <p>本家 {@code GuiVehicleControl} 相当。RTMU は操作画面が<b>列車専用</b>で、
 * ModelVehicle の車両 (シールドマシン 等) は {@code Button0} を立てられず、
 * サーバースクリプトが動かなかった (ShieldMachine.js は
 * {@code Button0 != 0 && speed > 0} が条件)。
 *
 * <p>ボタンを押すと {@code dataMap.setInt("Button<i>", value, 1)} = 同期フラグ付きで書き、
 * 既存のクライアント→サーバー同期 (CarScriptDataPayload) でサーバーの
 * {@code onUpdate(entity, scriptExecuter)} へ届く。
 */
public class CarControlScreen extends Screen {
    private final CarEntity car;

    public CarControlScreen(CarEntity car) {
        super(Component.literal("Vehicle Control"));
        this.car = car;
    }

    @Override
    protected void init() {
        List<String> names = List.of();
        List<List<String>> options = List.of();
        VehicleDefinition def = VehicleRegistry.getById(this.car.getVehicleId());
        if (def != null) {
            names = def.getCustomButtonNames();
            options = def.getCustomButtonOptions();
        }
        int y = 40;
        for (int i = 0; i < names.size(); i++) {
            final int index = i;
            final List<String> opts = i < options.size() ? options.get(i) : List.of();
            String raw = names.get(i) == null ? "" : names.get(i);
            boolean slider = raw.startsWith("slider:");
            String fallback = slider ? raw.substring("slider:".length()) : raw;

            this.addRenderableWidget(Button.builder(buttonLabel(index, opts, fallback), b -> {
                if (slider) {
                    int v = current(index);
                    v = (v + 10) % 110; // 0..100 を 10 刻みで巡回
                    set(index, v);
                } else if (!opts.isEmpty()) {
                    // 本家 GUI と同じく選択肢を順に切り替える (STOP→DRIVING 等)
                    set(index, (current(index) + 1) % opts.size());
                } else {
                    set(index, current(index) == 0 ? 1 : 0);
                }
                b.setMessage(buttonLabel(index, opts, fallback));
            }).bounds(this.width / 2 - 100, y, 200, 20).build());
            y += 24;
        }
        this.addRenderableWidget(Button.builder(Component.literal("閉じる"), b -> this.onClose())
            .bounds(this.width / 2 - 100, Math.min(this.height - 28, y + 8), 200, 20).build());
    }

    /** 本家 GUI と同じく「現在の値に対応する選択肢名」を出す (例: "DRIVING")。 */
    private Component buttonLabel(int index, List<String> opts, String fallback) {
        int v = current(index);
        if (!opts.isEmpty()) {
            int idx = Math.max(0, Math.min(v, opts.size() - 1));
            String opt = opts.get(idx);
            return Component.literal(opt == null ? String.valueOf(v) : opt);
        }
        return Component.literal(fallback + ": " + v);
    }

    private int current(int index) {
        return this.car.getResourceState().getDataMap().getInt("Button" + index);
    }

    /** 値の書き込み (syncFlag=1 → サーバーへ同期)。 */
    private void set(int index, int value) {
        this.car.getResourceState().getDataMap().setInt("Button" + index, value, 1);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(this.font, this.title, this.width / 2, 16, 0xFFFFFF);
    }
}
