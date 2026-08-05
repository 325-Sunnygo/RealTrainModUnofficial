package com.portofino.realtrainmodunofficial.client.screen;

import com.portofino.realtrainmodunofficial.network.SignalConverterPayload;
import jp.ngt.rtm.electric.SignalConverterType;
import jp.ngt.rtm.electric.TileEntitySignalConverter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 信号変換機の設定。本家 {@code jp.ngt.rtm.gui.GuiSignalConverter} の移植。
 *
 * <p>本家と同じ配置 (比較演算子ボタン 30x20、値の欄 40x20 を y=60 と y=100)、
 * 同じラベル、同じ値域:
 * <ul>
 *   <li>RSIn … "Output signal level" / "RS_ON"(0〜127) / "RS_OFF"(0〜127)</li>
 *   <li>RSOut … "Input signal level" と比較演算子 (欄は 1 つ、0〜127)</li>
 *   <li>Wireless … "Channel"(0〜32767) / "Chunk Load Range"(0〜25)</li>
 *   <li>Increment/Decrement … 本家は<b>画面を開かない</b> (設定を持たない)</li>
 * </ul>
 */
public class SignalConverterScreen extends Screen {
    private final BlockPos pos;
    private final SignalConverterType type;
    private final int[] initial;
    private int comparatorIndex;
    private EditBox valueTrue;
    private EditBox valueFalse;

    public SignalConverterScreen(BlockPos pos, SignalConverterType type, int[] signalLevel, int comparator) {
        super(Component.translatable("screen.realtrainmodunofficial.signal_converter"));
        this.pos = pos;
        this.type = type;
        this.initial = signalLevel;
        this.comparatorIndex = comparator;
    }

    @Override
    protected void init() {
        int hw = this.width / 2;

        //本家: 完了 / キャンセル
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> {
            this.sendPacket();
            this.onClose();
        }).bounds(hw - 155, this.height - 28, 150, 20).build());
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> this.onClose())
            .bounds(hw + 5, this.height - 28, 150, 20).build());

        int i0 = 0;
        if (this.type == SignalConverterType.RSOut) {
            //本家: 比較演算子ボタン (== / > / >= / < / <= / !=) を x = w/2-36, y = 60, 30x20
            this.addRenderableWidget(Button.builder(
                Component.literal(TileEntitySignalConverter.ComparatorType.getType(this.comparatorIndex).operator),
                b -> {
                    this.comparatorIndex = (this.comparatorIndex + 1)
                        % TileEntitySignalConverter.ComparatorType.values().length;
                    b.setMessage(Component.literal(
                        TileEntitySignalConverter.ComparatorType.getType(this.comparatorIndex).operator));
                }).bounds(hw - 36, 60, 30, 20).build());
            i0 = 16;
        }

        this.valueTrue = new EditBox(this.font, hw - 20 + i0, 60, 40, 20, Component.literal("signal0"));
        this.valueTrue.setMaxLength(5);
        this.valueTrue.setValue(Integer.toString(this.initial[0]));
        this.addRenderableWidget(this.valueTrue);

        //本家: 2 つ目の欄が出るのは RSIn と Wireless だけ
        if (this.type == SignalConverterType.RSIn || this.type == SignalConverterType.Wireless) {
            this.valueFalse = new EditBox(this.font, hw - 20 + i0, 100, 40, 20, Component.literal("signal1"));
            this.valueFalse.setMaxLength(5);
            this.valueFalse.setValue(Integer.toString(this.initial[1]));
            this.addRenderableWidget(this.valueFalse);
        }
    }

    /** 本家 formatSignalLevel: 欄の値を丸めて欄にも書き戻す。 */
    private int[] formatSignalLevel() {
        int[] ia = new int[2];
        EditBox[] fields = {this.valueTrue, this.valueFalse};
        for (int i = 0; i < fields.length; ++i) {
            if (fields[i] == null) {
                continue;
            }
            //本家: 無線はチャンネル 0〜32767 / チャンク範囲 0〜25、それ以外は 0〜127
            int max = 127;
            if (this.type == SignalConverterType.Wireless) {
                max = i == 1 ? 25 : 32767;
            }
            int value = clampFromString(fields[i].getValue(), 0, max);
            fields[i].setValue(String.valueOf(value));
            ia[i] = value;
        }
        return ia;
    }

    /** 本家 NGTMath.getIntFromString(s, min, max, def)。 */
    private static int clampFromString(String s, int min, int max) {
        int value;
        try {
            value = Integer.parseInt(s.trim());
        } catch (Exception e) {
            value = 0;
        }
        return Math.max(min, Math.min(max, value));
    }

    /** 本家 sendPacket: RSIn / Wireless 以外は 2 つ目の値を 0 にして送る。 */
    private void sendPacket() {
        int[] ia = this.formatSignalLevel();
        if (this.type != SignalConverterType.RSIn && this.type != SignalConverterType.Wireless) {
            ia[1] = 0;
        }
        PacketDistributor.sendToServer(new SignalConverterPayload(this.pos, ia[0], ia[1], this.comparatorIndex));
    }

    /** 本家 keyTyped: Enter で値を丸め直す。 */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER
                || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
            this.formatSignalLevel();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int hw = this.width / 2;
        //本家 drawScreen のラベル (位置も本家のまま)
        if (this.type == SignalConverterType.RSIn) {
            graphics.drawCenteredString(this.font, "Output signal level", hw, 30, 0xFFFFFF);
            graphics.drawCenteredString(this.font, "RS_ON", hw, 45, 0xFFFFFF);
            graphics.drawCenteredString(this.font, "RS_OFF", hw, 85, 0xFFFFFF);
        } else if (this.type == SignalConverterType.RSOut) {
            graphics.drawCenteredString(this.font, "Input signal level", hw, 40, 0xFFFFFF);
        } else if (this.type == SignalConverterType.Wireless) {
            graphics.drawCenteredString(this.font, "Channel", hw, 45, 0xFFFFFF);
            graphics.drawCenteredString(this.font, "Chunk Load Range", hw, 85, 0xFFFFFF);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public static void open(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null
            || !(mc.level.getBlockEntity(pos) instanceof TileEntitySignalConverter converter)) {
            return;
        }
        SignalConverterType type = converter.getConverterType();
        //本家 BlockSignalConverter.onBlockActivated: Increment/Decrement は画面を開かない
        if (type == SignalConverterType.Increment || type == SignalConverterType.Decrement) {
            return;
        }
        mc.setScreen(new SignalConverterScreen(pos, type,
            converter.getSignalLevel(), converter.getComparator().id));
    }

    /** 1.21 のメニューぼかしを無効化 (本家 1.7.10 の GUI にぼかしは無い)。 */
    @Override
    protected void renderBlurredBackground(float partialTick) {
    }
}
