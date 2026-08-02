package com.portofino.rtmupassenger.client;

import com.portofino.realtrainmodunofficial.network.SetStationTagsPayload;
import com.portofino.rtmupassenger.station.StationRegistry;
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
 * さらに<b>この駅から出る人数</b>を駅ごとに決められる (以前は全駅一律 6 だった)。
 */
public class StationScreen extends Screen {

    private final BlockPos pos;
    /** 編集中のタグビット (1 << StationTag.ordinal の OR)。 */
    private int bits;
    /** 編集中の出る人数 (この駅で待たせる乗客の上限)。0 なら湧かせない。 */
    private int capacity;

    /** 出る人数の表示位置 (−/+ ボタンの間)。render で使う。 */
    private int capacityLabelX;
    private int capacityLabelY;
    /** タイトル行の Y。中身の高さに合わせて動かす。 */
    private int headerY;

    public StationScreen(BlockPos pos, int bits, int capacity) {
        super(Component.translatable("gui.realtrainmodunofficial.station.title"));
        this.pos = pos;
        this.bits = bits;
        this.capacity = StationRegistry.clampCapacity(capacity);
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int w = 200;
        // 出る人数の行が増えたぶん縦に伸びた。height/4 固定だと GUI スケールが大きいときに
        // 「完了」が画面外へ落ちるので、中身の高さから逆算して置く。
        int contentH = StationTag.values().length * 22 + 6 + 20 + 6 + 20;
        int y = Math.max(28, (this.height - contentH) / 2 + 6);
        this.headerY = y - 26;

        for (StationTag tag : StationTag.values()) {
            final int bit = 1 << tag.ordinal();
            addRenderableWidget(Button.builder(label(tag, bit), b -> {
                this.bits ^= bit;             //トグル
                b.setMessage(label(tag, bit));
            }).bounds(cx - w / 2, y, w, 20).build());
            y += 22;
        }

        // 出る人数: [−] 数 [+]。Shift 押しで 5 ずつ動く。
        y += 6;
        this.capacityLabelX = cx;
        this.capacityLabelY = y + 6;
        addRenderableWidget(Button.builder(Component.literal("−"), b -> addCapacity(-step()))
            .bounds(cx - w / 2, y, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> addCapacity(step()))
            .bounds(cx + w / 2 - 20, y, 20, 20).build());

        y += 26;
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
            .bounds(cx - w / 2, Math.min(y, this.height - 24), w, 20).build());
    }

    private static int step() {
        return hasShiftDown() ? 5 : 1;
    }

    private void addCapacity(int delta) {
        this.capacity = StationRegistry.clampCapacity(this.capacity + delta);
    }

    private Component label(StationTag tag, int bit) {
        boolean on = (this.bits & bit) != 0;
        return Component.literal((on ? "§a☑ " : "§7☐ ") + tag.displayName());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, this.headerY, 0xFFFFFF);
        graphics.drawCenteredString(this.font,
            Component.translatable("gui.realtrainmodunofficial.station.hint"),
            this.width / 2, this.headerY + 12, 0xFFA0A0A0);

        // 0 は「湧かせない」= 行き先専用の駅。分かるように文言を変える。
        Component value = this.capacity <= 0
            ? Component.translatable("gui.realtrainmodunofficial.station.spawn_count_off")
            : Component.translatable("gui.realtrainmodunofficial.station.spawn_count", this.capacity);
        graphics.drawCenteredString(this.font, value, this.capacityLabelX, this.capacityLabelY, 0xFFFFFF);
    }

    @Override
    public void onClose() {
        // 選択したタグと出る人数をサーバーへ送って保存 (駅の登録も兼ねる)。
        if (this.minecraft != null && this.minecraft.getConnection() != null) {
            PacketDistributor.sendToServer(new SetStationTagsPayload(this.pos, this.bits, this.capacity));
        }
        super.onClose();
    }
}
