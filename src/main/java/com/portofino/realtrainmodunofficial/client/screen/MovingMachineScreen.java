package com.portofino.realtrainmodunofficial.client.screen;

import com.portofino.realtrainmodunofficial.network.MovingMachinePayload;
import jp.ngt.rtm.block.tileentity.TileEntityMovingMachine;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** 移動装置の設定。本家 {@code GuiMovingMachine} 相当 (大きさ・ずらし・速さ)。 */
public class MovingMachineScreen extends Screen {
    private final BlockPos pos;
    private final int[] init = new int[6];
    private float initSpeed = 0.0625F;
    private boolean guide = true;
    private final EditBox[] boxes = new EditBox[6];
    private EditBox speedBox;
    /** 乗り物生成器のときだけ出す種類ボタン (0=車/1=船/2=飛行機)。 */
    private int vehicleType;
    private boolean generator;

    private static final String[] LABELS = {"width", "height", "depth", "offsetX", "offsetY", "offsetZ"};

    public MovingMachineScreen(BlockPos pos, int[] values, float speed, boolean guide,
                               boolean generator, int vehicleType) {
        this(pos, values, speed, guide);
        this.generator = generator;
        this.vehicleType = vehicleType;
    }

    public MovingMachineScreen(BlockPos pos, int[] values, float speed, boolean guide) {
        super(Component.translatable("screen.realtrainmodunofficial.moving_machine"));
        this.pos = pos;
        System.arraycopy(values, 0, this.init, 0, Math.min(values.length, 6));
        this.initSpeed = speed;
        this.guide = guide;
    }

    @Override
    protected void init() {
        int hw = this.width / 2;
        for (int i = 0; i < 6; ++i) {
            EditBox box = new EditBox(this.font, hw + 10, 40 + i * 22, 60, 18,
                Component.literal(LABELS[i]));
            box.setValue(Integer.toString(this.init[i]));
            this.boxes[i] = box;
            this.addRenderableWidget(box);
        }
        this.speedBox = new EditBox(this.font, hw + 10, 40 + 6 * 22, 60, 18, Component.literal("speed"));
        this.speedBox.setValue(Float.toString(this.initSpeed));
        this.addRenderableWidget(this.speedBox);

        if (this.generator) {
            String[] names = {"Car", "Ship", "Plane"};
            this.addRenderableWidget(Button.builder(Component.literal(names[this.vehicleType % 3]), b -> {
                this.vehicleType = (this.vehicleType + 1) % 3;
                b.setMessage(Component.literal(names[this.vehicleType]));
            }).bounds(hw + 80, 40, 60, 18).build());
        }

        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> {
            int[] v = new int[6];
            for (int i = 0; i < 6; ++i) {
                v[i] = parseInt(this.boxes[i].getValue());
            }
            float speed = parseFloat(this.speedBox.getValue(), this.initSpeed);
            PacketDistributor.sendToServer(new MovingMachinePayload(this.pos,
                v[0], v[1], v[2], v[3], v[4], v[5], speed, this.guide, this.vehicleType));
            this.onClose();
        }).bounds(hw - 155, this.height - 28, 150, 20).build());

        this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> this.onClose())
            .bounds(hw + 5, this.height - 28, 150, 20).build());
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private static float parseFloat(String s, float fallback) {
        try {
            return Float.parseFloat(s.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int hw = this.width / 2;
        for (int i = 0; i < 6; ++i) {
            graphics.drawString(this.font,
                Component.translatable("screen.realtrainmodunofficial.mm_" + LABELS[i]),
                hw - 80, 45 + i * 22, 0xFFFFFF);
        }
        graphics.drawString(this.font,
            Component.translatable("screen.realtrainmodunofficial.mm_speed"),
            hw - 80, 45 + 6 * 22, 0xFFFFFF);
        graphics.drawCenteredString(this.font, this.title, hw, 18, 0xFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public static void open(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !(mc.level.getBlockEntity(pos) instanceof TileEntityMovingMachine tile)) {
            return;
        }
        boolean generator = tile.getBlockState().getBlock()
            instanceof jp.ngt.rtm.block.BlockMovingMachine mm && mm.generator;
        mc.setScreen(new MovingMachineScreen(pos,
            new int[]{tile.width, tile.height, tile.depth, tile.offsetX, tile.offsetY, tile.offsetZ},
            tile.speed, tile.guideVisibility, generator, tile.vehicleType));
    }

    /** 1.21 のメニューぼかしを無効化 (本家 1.7.10 の GUI にぼかしは無い)。 */
    @Override
    protected void renderBlurredBackground(float partialTick) {
    }
}
