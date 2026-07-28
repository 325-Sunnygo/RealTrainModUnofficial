package jp.kaiz.atsassistmod.client;

import jp.kaiz.atsassistmod.block.GroundUnitType;
import jp.kaiz.atsassistmod.block.tileentity.GroundUnitBlockEntity;
import jp.kaiz.atsassistmod.controller.trainprotection.TrainProtectionType;
import jp.kaiz.atsassistmod.network.GroundUnitConfigPayload;
import jp.ngt.rtm.entity.train.util.TrainState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * 本家 jp.kaiz.atsassistmod.gui.GUIGroundUnit の忠実移植 (地上子の設定画面)。
 * レイアウト・座標・ボタン配置は本家 1.7.10 のまま:
 *   種類 None → 機能選択メニュー (ATC/TASC/ATO/保安装置/その他 の見出し + 各機能ボタンのグリッド)
 *   各種類 → タイトル (w/4, 20)、右上「リセット」、パラメータ (テキスト欄は w/2, h/2-30+25n)、
 *           「レッドストーン連動」チェック (w/2+45, h/2-50)、下部「決定」「キャンセル」。
 *   保安装置強制変更はスライダー (本家 GuiOptionSliderTrainProtection)、
 *   列車データ変更は TrainState スライダー 12 本 (本家 GuiOptionSliderTrainState、i=3 は欠番)。
 */
public class GroundUnitScreen extends Screen {

    private final BlockPos pos;
    private CompoundTag config = new CompoundTag();
    private GroundUnitType type = GroundUnitType.None;

    private final List<EditBox> textFieldList = new ArrayList<>();
    private Checkbox linkRedStoneBox;
    private Checkbox checkBox0;     //autoBrake / lateCancel / trainDistance (種類による)
    private Checkbox checkBox1;     //trainDistance (ATC 予告の2個目)
    private TrainProtectionSlider tpSlider;
    private final List<TrainStateSlider> stateSliders = new ArrayList<>();

    public GroundUnitScreen(BlockPos pos) {
        super(Component.literal("地上子"));
        this.pos = pos;
    }

    @Override
    protected void init() {
        //現在の設定を BlockEntity から読む (クライアントへ同期済み)
        if (Minecraft.getInstance().level != null
                && Minecraft.getInstance().level.getBlockEntity(pos) instanceof GroundUnitBlockEntity be) {
            this.config = be.collectConfig();
        }
        this.type = GroundUnitType.getType(this.config.getInt("unitType"));
        rebuild();
    }

    private void rebuild() {
        clearWidgets();
        this.textFieldList.clear();
        this.stateSliders.clear();
        this.linkRedStoneBox = null;
        this.checkBox0 = null;
        this.checkBox1 = null;
        this.tpSlider = null;

        switch (this.type) {
            case None -> initMenu();
            case ATC_SpeedLimit_Notice -> {
                addDownCommon();
                addGuiTextField(0, String.valueOf(this.config.getInt("speedLimit")), 3);
                addGuiTextField(1, distanceText(), 5);
                this.checkBox0 = addCheck(this.width / 2 + 45, this.height / 2 + 25, this.config.getBoolean("autoBrake"));
                this.checkBox1 = addCheck(this.width / 2 + 45, this.height / 2 + 50, this.config.getBoolean("trainDistance"));
            }
            case ATC_SpeedLimit_Cancel -> {
                addDownCommon();
                this.checkBox0 = addCheck(this.width / 2 + 45, this.height / 2 - 25, this.config.getBoolean("lateCancel"));
            }
            case TASC_StopPotion_Notice, TASC_StopPotion_Correction -> {
                addDownCommon();
                addGuiTextField(0, distanceText(), 5);
                this.checkBox0 = addCheck(this.width / 2 + 45, this.height / 2, this.config.getBoolean("trainDistance"));
            }
            case ATO_Departure_Signal, ATO_Change_Speed -> {
                addDownCommon();
                addGuiTextField(0, String.valueOf(this.config.getInt("speedLimit")), 3);
            }
            case CHANGE_TP -> {
                addDownCommon();
                this.tpSlider = addRenderableWidget(new TrainProtectionSlider(
                        this.width / 2 - 75, this.height / 2 - 25,
                        TrainProtectionType.getType(this.config.getInt("tpType"))));
            }
            case TrainState_Set -> initTrainStateSet();
            //ATC_SpeedLimit_Reset / TASC_Cancel / TASC_StopPotion / ATO_Cancel / ATACS_Disable
            default -> addDownCommon();
        }
    }

    /** 本家 initGui (None): 機能選択メニュー。座標は本家のまま。 */
    private void initMenu() {
        int cx = this.width / 2;
        int cy = this.height / 2;
        //ATC
        addTypeButton(GroundUnitType.ATC_SpeedLimit_Notice, "速度制限予告", cx - 170, cy - 75);
        addTypeButton(GroundUnitType.ATC_SpeedLimit_Cancel, "速度制限解除", cx - 50, cy - 75);
        addTypeButton(GroundUnitType.ATC_SpeedLimit_Reset, "速度制限一斉解除", cx + 70, cy - 75);
        //TASC
        addTypeButton(GroundUnitType.TASC_StopPotion_Notice, "停車位置予告", cx - 170, cy - 35);
        addTypeButton(GroundUnitType.TASC_Cancel, "制御終了", cx - 50, cy - 35);
        addTypeButton(GroundUnitType.TASC_StopPotion_Correction, "停車距離補正", cx - 170, cy - 10);
        addTypeButton(GroundUnitType.TASC_StopPotion, "停車検知", cx - 50, cy - 10);
        //ATO
        addTypeButton(GroundUnitType.ATO_Departure_Signal, "出発信号", cx - 170, cy + 30);
        addTypeButton(GroundUnitType.ATO_Cancel, "制御終了", cx - 50, cy + 30);
        addTypeButton(GroundUnitType.ATO_Change_Speed, "目標速度変更", cx + 70, cy + 30);
        //保安装置 / その他
        addTypeButton(GroundUnitType.CHANGE_TP, "強制変更", cx - 170, cy + 70);
        addTypeButton(GroundUnitType.TrainState_Set, "列車データ変更", cx + 70, cy + 70);
        //キャンセル (id 20)
        addRenderableWidget(Button.builder(Component.literal("キャンセル"), b -> onClose())
                .bounds(cx - 50, this.height - 25, 100, 20).build());
    }

    private void addTypeButton(GroundUnitType unitType, String label, int x, int y) {
        addRenderableWidget(Button.builder(Component.literal(label), b -> selectType(unitType))
                .bounds(x, y, 100, 20).build());
    }

    /** 本家: メニューのボタン → PacketGroundUnitTileInit + setBlock(meta)。種類を確定してその設定ページへ。 */
    private void selectType(GroundUnitType unitType) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("unitType", unitType.id);
        PacketDistributor.sendToServer(new GroundUnitConfigPayload(this.pos, tag));
        this.config = tag;
        this.type = unitType;
        rebuild();
    }

    /** 本家 TrainState_Set ページ (12 スライダー、i=3 は欠番)。 */
    private void initTrainStateSet() {
        //id 100: チェックボックス = 逆転ハンドル前以外でも有効 (linkRedStone 相当)
        this.linkRedStoneBox = addCheck(this.width / 2 + 145, this.height / 2 - 45, this.config.getBoolean("linkRedStone"));

        addResetDecideCancel();

        byte[] states = this.config.getByteArray("state");
        for (int i = 0; i < 12; i++) {
            if (i == 3) {
                continue;
            }
            TrainState.TrainStateType stateType = TrainState.getStateType(i);
            byte value = states.length == 12 ? states[i] : (byte) (stateType.min - 1);
            TrainStateSlider slider = new TrainStateSlider(
                    this.width / 2 - 160 + 170 * (i % 2),
                    this.height / 2 - 75 + 25 * (i / 2),
                    i, stateType, value);
            this.stateSliders.add(slider);
            addRenderableWidget(slider);
        }
    }

    /** 本家 addDownCommon: RS連動チェック + リセット/決定/キャンセル。 */
    private void addDownCommon() {
        this.linkRedStoneBox = addCheck(this.width / 2 + 45, this.height / 2 - 50, this.config.getBoolean("linkRedStone"));
        addResetDecideCancel();
    }

    private void addResetDecideCancel() {
        //id 0: リセット (種類を None に戻す)
        addRenderableWidget(Button.builder(Component.literal("リセット"), b -> selectType(GroundUnitType.None))
                .bounds(this.width / 4 + 160, 15, 50, 20).build());
        //id 21: 決定
        addRenderableWidget(Button.builder(Component.literal("決定"), b -> save())
                .bounds(this.width / 2 - 110, this.height - 30, 100, 20).build());
        //id 20: キャンセル
        addRenderableWidget(Button.builder(Component.literal("キャンセル"), b -> onClose())
                .bounds(this.width / 2 + 10, this.height - 30, 100, 20).build());
    }

    private Checkbox addCheck(int x, int y, boolean selected) {
        //本家 GuiCheckBox は 11x11。バニラは少し大きいので中心を合わせる
        return addRenderableWidget(Checkbox.builder(Component.empty(), this.font)
                .pos(x, y - 3).selected(selected).build());
    }

    /** 本家 addGuiTextField: (w/2, h/2-30+25n) 100x20。 */
    private void addGuiTextField(int number, String str, int maxLength) {
        EditBox text = new EditBox(this.font, this.width / 2, this.height / 2 - 30 + (25 * number), 100, 20, Component.empty());
        text.setMaxLength(maxLength);
        text.setValue(str);
        //本家 keyTyped: 数字のみ (maxLength=5 の距離欄だけ小数点も可)
        if (maxLength == 5) {
            text.setFilter(s -> s.isEmpty() || s.matches("\\d*(\\.\\d*)?"));
        } else {
            text.setFilter(s -> s.isEmpty() || s.matches("\\d*"));
        }
        this.textFieldList.add(text);
        addRenderableWidget(text);
    }

    private String distanceText() {
        double d = this.config.getDouble("distance");
        return d == Math.floor(d) ? String.valueOf((long) d) : String.valueOf(d);
    }

    private int getIntField(int number) {
        try {
            return Integer.parseInt(this.textFieldList.get(number).getValue().trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private double getDoubleField(int number) {
        try {
            return Double.parseDouble(this.textFieldList.get(number).getValue().trim());
        } catch (Exception e) {
            return 0.0D;
        }
    }

    /** 本家 actionPerformed id=21 (決定): 種類ごとのパラメータをサーバーへ。 */
    private void save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("unitType", this.type.id);
        tag.putBoolean("linkRedStone", this.linkRedStoneBox != null && this.linkRedStoneBox.selected());
        switch (this.type) {
            case ATC_SpeedLimit_Notice -> {
                tag.putInt("speedLimit", getIntField(0));
                tag.putDouble("distance", getDoubleField(1));
                tag.putBoolean("autoBrake", this.checkBox0 != null && this.checkBox0.selected());
                tag.putBoolean("trainDistance", this.checkBox1 != null && this.checkBox1.selected());
            }
            case ATC_SpeedLimit_Cancel ->
                tag.putBoolean("lateCancel", this.checkBox0 != null && this.checkBox0.selected());
            case TASC_StopPotion_Notice, TASC_StopPotion_Correction -> {
                tag.putDouble("distance", getDoubleField(0));
                tag.putBoolean("trainDistance", this.checkBox0 != null && this.checkBox0.selected());
            }
            case ATO_Departure_Signal, ATO_Change_Speed ->
                tag.putInt("speedLimit", getIntField(0));
            case CHANGE_TP ->
                tag.putInt("tpType", this.tpSlider != null ? this.tpSlider.nowValue.id : 0);
            case TrainState_Set -> {
                byte[] states = new byte[12];
                for (int i = 0; i < 12; i++) {
                    states[i] = (byte) (TrainState.getStateType(i).min - 1);
                }
                for (TrainStateSlider slider : this.stateSliders) {
                    states[slider.stateId] = slider.nowValue;
                }
                tag.putByteArray("state", states);
            }
            default -> {
            }
        }
        PacketDistributor.sendToServer(new GroundUnitConfigPayload(this.pos, tag));
        onClose();
    }

    //------------------------------------------------------------ 描画 (本家 drawScreen)

    /** ブラー無効化 (本家 drawDefaultBackground 相当)。 */
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fillGradient(0, 0, this.width, this.height, 0xC0101010, 0xD0101010);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int cx = this.width / 2;
        int cy = this.height / 2;

        switch (this.type) {
            case None -> {
                g.drawString(this.font, "地上子機能選択メニュー", this.width / 4, 20, 0xFFFFFF, false);
                g.drawString(this.font, "ATC", cx - 170, cy - 90, 0xFFFFFF, false);
                g.drawString(this.font, "TASC", cx - 170, cy - 50, 0xFFFFFF, false);
                g.drawString(this.font, "ATO", cx - 170, cy + 15, 0xFFFFFF, false);
                g.drawString(this.font, "保安装置", cx - 170, cy + 55, 0xFFFFFF, false);
                g.drawString(this.font, "その他", cx + 70, cy + 55, 0xFFFFFF, false);
                return;
            }
            case ATC_SpeedLimit_Notice -> {
                g.drawString(this.font, "地上子機能 : ATC(ATO) : 速度制限予告", this.width / 4, 20, 0xFFFFFF, false);
                g.drawString(this.font, "速度制限    (km/h)", cx - 100, cy - 25, 0xFFFFFF, false);
                g.drawString(this.font, "制限開始の距離 (m)", cx - 100, cy, 0xFFFFFF, false);
                g.drawString(this.font, "自動的に減速する", cx - 100, cy + 25, 0xFFFFFF, false);
                g.drawString(this.font, "距離基準を車両先頭に", cx - 100, cy + 50, 0xFFFFFF, false);
            }
            case ATC_SpeedLimit_Cancel -> {
                g.drawString(this.font, "地上子機能 : ATC(ATO) : 速度制限解除", this.width / 4, 20, 0xFFFFFF, false);
                g.drawString(this.font, "編成最後尾で解除", cx - 100, cy - 25, 0xFFFFFF, false);
            }
            case ATC_SpeedLimit_Reset ->
                g.drawString(this.font, "地上子機能 : ATC(ATO) : 速度制限クリア", this.width / 4, 20, 0xFFFFFF, false);
            case TASC_StopPotion_Notice -> {
                g.drawString(this.font, "地上子機能 : TASC : 距離設定 制御開始", this.width / 4, 20, 0xFFFFFF, false);
                g.drawString(this.font, "停車位置までの距離", cx - 100, cy - 25, 0xFFFFFF, false);
                g.drawString(this.font, "距離基準を車両先頭に", cx - 100, cy, 0xFFFFFF, false);
            }
            case TASC_Cancel ->
                g.drawString(this.font, "地上子機能 : TASC : 制御終了", this.width / 4, 20, 0xFFFFFF, false);
            case TASC_StopPotion_Correction -> {
                g.drawString(this.font, "地上子機能 : TASC : 距離補正", this.width / 4, 20, 0xFFFFFF, false);
                g.drawString(this.font, "停車位置までの距離", cx - 100, cy - 25, 0xFFFFFF, false);
                g.drawString(this.font, "距離基準を車両先頭に", cx - 100, cy, 0xFFFFFF, false);
            }
            case TASC_StopPotion -> {
                g.drawString(this.font, "地上子機能 : 停車検知", this.width / 4, 20, 0xFFFFFF, false);
                g.drawString(this.font, "逆転ハンドル前以外でも検知", cx - 100, cy - 50, 0xFFFFFF, false);
                return;
            }
            case ATO_Departure_Signal -> {
                g.drawString(this.font, "地上子機能 : ATO : 出発信号 制御開始", this.width / 4, 20, 0xFFFFFF, false);
                g.drawString(this.font, "目標速度", cx - 100, cy - 25, 0xFFFFFF, false);
            }
            case ATO_Cancel ->
                g.drawString(this.font, "地上子機能 : ATO : 制御終了", this.width / 4, 20, 0xFFFFFF, false);
            case ATO_Change_Speed -> {
                g.drawString(this.font, "地上子機能 : ATO : 目標速度変更", this.width / 4, 20, 0xFFFFFF, false);
                g.drawString(this.font, "目標速度", cx - 100, cy - 25, 0xFFFFFF, false);
            }
            case TrainState_Set -> {
                g.drawString(this.font, "地上子機能 : TrainState : 車両状態変更", this.width / 4, 20, 0xFFFFFF, false);
                g.drawString(this.font, "逆転ハンドル前以外でも有効", cx + 10, cy - 45, 0xFFFFFF, false);
                return;
            }
            case CHANGE_TP ->
                g.drawString(this.font, "地上子機能 : 保安装置 : 強制変更", this.width / 4, 20, 0xFFFFFF, false);
            case ATACS_Disable ->
                g.drawString(this.font, "地上子機能 : ATACS : 制御終了", this.width / 4, 20, 0xFFFFFF, false);
            default -> {
            }
        }

        //本家: 各設定ページ共通の「レッドストーン連動」ラベル
        g.drawString(this.font, "レッドストーン連動", cx - 100, cy - 50, 0xFFFFFF, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    //------------------------------------------------------------ スライダー (本家 GuiOptionSlider*)

    /** 本家 GuiOptionSliderTrainProtection: 保安装置を選ぶスライダー (150x20)。 */
    private static class TrainProtectionSlider extends AbstractSliderButton {
        TrainProtectionType nowValue;

        TrainProtectionSlider(int x, int y, TrainProtectionType current) {
            super(x, y, 150, 20, Component.empty(), 0);
            this.nowValue = current;
            TrainProtectionType[] values = TrainProtectionType.values();
            int index = 0;
            for (int i = 0; i < values.length; i++) {
                if (values[i] == current) {
                    index = i;
                    break;
                }
            }
            this.value = values.length <= 1 ? 0 : (double) index / (values.length - 1);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(this.nowValue.getDisplayName());
        }

        @Override
        protected void applyValue() {
            TrainProtectionType[] values = TrainProtectionType.values();
            int index = Mth.clamp((int) Math.round(this.value * (values.length - 1)), 0, values.length - 1);
            this.nowValue = values[index];
        }
    }

    /** 本家 GuiOptionSliderTrainState: TrainState を選ぶスライダー (150x20、min-1 = 変更なし)。 */
    private static class TrainStateSlider extends AbstractSliderButton {
        final int stateId;
        final TrainState.TrainStateType type;
        byte nowValue;

        TrainStateSlider(int x, int y, int stateId, TrainState.TrainStateType type, byte current) {
            super(x, y, 150, 20, Component.empty(), 0);
            this.stateId = stateId;
            this.type = type;
            this.nowValue = current;
            int range = this.type.max - (this.type.min - 1);
            this.value = range <= 0 ? 0 : (double) (current - (this.type.min - 1)) / range;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(getNormalizedValue()));
        }

        @Override
        protected void applyValue() {
            int min = this.type.min - 1;
            this.nowValue = (byte) Mth.clamp(
                    (int) Math.round(min + this.value * (this.type.max - min)), min, this.type.max);
        }

        /** 本家 getNormalizedValue: "項目:値" 表示 (min 未満は 変更なし)。 */
        private String getNormalizedValue() {
            String state;
            String data = String.valueOf(this.nowValue);
            switch (this.stateId) {
                case 0 -> state = "方向";
                case 1 -> state = "ノッチ";
                case 2 -> state = "信号";
                case 4 -> {
                    state = "ドア";
                    switch (this.nowValue) {
                        case 0 -> data = "両側 閉";
                        case 1 -> data = "右側 開";
                        case 2 -> data = "左側 開";
                        case 3 -> data = "両側 開";
                        default -> {
                        }
                    }
                }
                case 5 -> {
                    state = "前照灯";
                    switch (this.nowValue) {
                        case 0 -> data = "オフ";
                        case 1 -> data = "オン";
                        case 2 -> {
                            state = "前照灯・尾灯";
                            data = "オン";
                        }
                        default -> {
                        }
                    }
                }
                case 6 -> {
                    state = "パンタグラフ";
                    switch (this.nowValue) {
                        case 0 -> data = "下げ";
                        case 1 -> data = "上げ";
                        default -> {
                        }
                    }
                }
                case 7 -> state = "チャンクローダー";
                case 8 -> state = "方向幕";
                case 9 -> state = "車内放送";
                case 10 -> {
                    state = "逆転ハンドル";
                    switch (this.nowValue) {
                        case 0 -> data = "前";
                        case 1 -> data = "切";
                        case 2 -> data = "後";
                        default -> {
                        }
                    }
                }
                case 11 -> {
                    state = "車内灯";
                    switch (this.nowValue) {
                        case 0 -> data = "OFF";
                        case 1 -> data = "ON";
                        case 2 -> data = "Rainbow";
                        default -> {
                        }
                    }
                }
                default -> state = "";
            }
            if (this.nowValue < this.type.min) {
                data = "変更なし";
            }
            return state + ":" + data;
        }
    }
}
