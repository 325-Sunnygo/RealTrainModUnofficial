package jp.kaiz.atsassistmod.client;

import jp.kaiz.atsassistmod.controller.trainprotection.TrainProtectionType;
import jp.kaiz.atsassistmod.network.AtsaTrainControlPayload;
import jp.ngt.rtm.entity.train.EntityTrainBase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * 本家 jp.kaiz.atsassistmod.gui.GUITrainProtectionSelector の忠実移植。
 * レイアウト・座標は本家のまま:
 *   左: 「運転切替」「運転モード: (手動/TASC/TASC・ATO)」+ 手動運転固定 / HUD非表示 のチェックボックス
 *   右: 「保安装置切替」開放・構内 (中列) と搭載保安装置 (右列)。各行の横の 20x20 ボタンに
 *       現在選択中なら "X" が出て、X の付いていないボタンを押すと切替 (本家仕様)。
 * 搭載保安装置は DataMap "ATSAssist_TP" で制限 (未設定なら ATACS/ATS-Ps/R-ATS/Rn-ATS、順番固定)。
 */
public class TrainProtectionSelectorScreen extends Screen {

    private final List<TrainProtectionType> validTPList = new ArrayList<>();
    private EntityTrainBase train;

    //本家 id 20/21/30..33 のボタン (displayString "X" を毎フレーム更新)
    private final List<Button> tpButtons = new ArrayList<>();
    private final List<TrainProtectionType> tpButtonTypes = new ArrayList<>();

    public TrainProtectionSelectorScreen() {
        super(Component.literal("保安装置切替"));
    }

    @Override
    protected void init() {
        this.clearWidgets();
        this.tpButtons.clear();
        this.tpButtonTypes.clear();
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.player != null && mc.player.getVehicle() instanceof EntityTrainBase riding)) {
            onClose();
            return;
        }
        this.train = riding;

        //本家: DataMap "ATSAssist_TP" で搭載保安装置を制限 (順番考えてるから values にするな)
        validTPList.clear();
        String tpList = train.getResourceState().getDataMap().getString("ATSAssist_TP");
        if (tpList.isEmpty()) {
            validTPList.add(TrainProtectionType.ATACS);
            validTPList.add(TrainProtectionType.ATSPs);
            validTPList.add(TrainProtectionType.RATS);
            validTPList.add(TrainProtectionType.RnATS);
        } else {
            if (tpList.contains("ATACS")) validTPList.add(TrainProtectionType.ATACS);
            if (tpList.contains("ATS-Ps")) validTPList.add(TrainProtectionType.ATSPs);
            if (tpList.contains("R-ATS")) validTPList.add(TrainProtectionType.RATS);
            if (tpList.contains("Rn-ATS")) validTPList.add(TrainProtectionType.RnATS);
        }

        //本家 initGui の座標
        int heightBase = this.height / 2 - 55;
        int widthBaseL = this.width / 2 - 80;
        int widthBaseR0 = this.width / 2 + 40;
        int widthBaseR1 = this.width / 2 + 130;

        //id 101: 手動運転固定 (本家 PacketManualDrive)
        addRenderableWidget(Checkbox.builder(Component.empty(), this.font)
                .pos(widthBaseL + 3, heightBase + 28 - 3)
                .selected(isManualDrive())
                .onValueChange((box, value) ->
                        PacketDistributor.sendToServer(new AtsaTrainControlPayload("manual", value ? 1 : 0)))
                .build());

        //id 100: HUD非表示 (クライアント設定)
        addRenderableWidget(Checkbox.builder(Component.empty(), this.font)
                .pos(widthBaseL + 3, heightBase + 103 - 3)
                .selected(AtsaHudRenderer.isNotShowHud())
                .onValueChange((box, value) -> AtsaHudRenderer.setNotShowHud(value))
                .build());

        //id 20/21: 開放・構内 (中列)
        addTpButton(TrainProtectionType.NONE, widthBaseR0, heightBase);
        addTpButton(TrainProtectionType.STATION_PREMISES, widthBaseR0, heightBase + 25);

        //id 30..: 搭載保安装置 (右列)
        int y = heightBase;
        for (TrainProtectionType type : validTPList) {
            addTpButton(type, widthBaseR1, y);
            y += 25;
        }
    }

    /** 本家: 20x20 ボタン。選択中は "X" 表示、X が付いている間は押しても無反応。 */
    private void addTpButton(TrainProtectionType type, int x, int y) {
        Button button = Button.builder(Component.empty(), b -> {
            if (currentTpType() != type) {
                PacketDistributor.sendToServer(new AtsaTrainControlPayload("set_tp", type.id));
            }
        }).bounds(x, y, 20, 20).build();
        this.tpButtons.add(button);
        this.tpButtonTypes.add(type);
        addRenderableWidget(button);
    }

    private TrainProtectionType currentTpType() {
        return TrainProtectionType.getType(train.getResourceState().getDataMap().getInt("ATSAssist_TPType"));
    }

    private String[] hudParts() {
        String hud = train.getResourceState().getDataMap().getString("ATSAssist_HUD");
        return hud.split("\\|");
    }

    private boolean isManualDrive() {
        String[] parts = hudParts();
        return parts.length >= 6 && "1".equals(parts[5]);
    }

    /** ブラー無効化 (本家 drawDefaultBackground 相当)。 */
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fillGradient(0, 0, this.width, this.height, 0xC0101010, 0xD0101010);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        //ボタンの "X" 表示を毎フレーム更新 (本家 drawScreen)
        TrainProtectionType current = currentTpType();
        for (int i = 0; i < tpButtons.size(); i++) {
            tpButtons.get(i).setMessage(Component.literal(current == tpButtonTypes.get(i) ? "X" : ""));
        }

        super.render(g, mouseX, mouseY, partialTick);

        //本家 drawScreen の座標
        int heightBase = this.height / 2 - 50;
        int widthBaseL = this.width / 2 - 135;
        int widthBaseR0 = this.width / 2 - 10;
        int widthBaseR1 = this.width / 2 + 80;

        //運転切替
        g.drawString(this.font, "運転切替", widthBaseL + 20, heightBase - 25, 0xFFFFFF, true);
        g.drawString(this.font, "運転モード:", widthBaseL, heightBase, 0xFFFFFF, true);
        g.drawString(this.font, currentDriveMode(), widthBaseL + 55, heightBase, 0xFFFFFF, true);
        g.drawString(this.font, "手動運転固定", widthBaseL, heightBase + 25, 0xFFFFFF, true);
        g.drawString(this.font, "HUD非表示", widthBaseL, heightBase + 100, 0xFFFFFF, true);

        //保安装置切替
        g.drawString(this.font, "保安装置切替", widthBaseR0 + 50, heightBase - 25, 0xFFFFFF, true);
        g.drawString(this.font, TrainProtectionType.NONE.getDisplayName().getString(),
                widthBaseR0, heightBase + 1, 0xFFFFFF, true);
        g.drawString(this.font, TrainProtectionType.STATION_PREMISES.getDisplayName().getString(),
                widthBaseR0, heightBase + 26, 0xFFFFFF, true);
        int y = heightBase;
        for (TrainProtectionType type : validTPList) {
            g.drawString(this.font, type.getDisplayName().getString(), widthBaseR1, y + 1, 0xFFFFFF, true);
            y += 25;
        }
    }

    /** 本家: 運転モード表示 (手動 / TASC / TASC・ATO) — HUD 同期文字列から判定。 */
    private String currentDriveMode() {
        String[] parts = hudParts();
        if (parts.length < 2) {
            return "手動";
        }
        boolean ato = !"off".equals(parts[0]) && !parts[0].isEmpty();
        boolean tasc = !"off".equals(parts[1]) && !parts[1].isEmpty();
        return ato ? "TASC/ATO" : tasc ? "TASC" : "手動";
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
