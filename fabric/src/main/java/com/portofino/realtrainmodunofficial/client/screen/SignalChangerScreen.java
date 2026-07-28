package com.portofino.realtrainmodunofficial.client.screen;

import com.portofino.realtrainmodunofficial.network.SetSignalAspectPayload;
import com.portofino.realtrainmodunofficial.signal.SignalAspect;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

public class SignalChangerScreen extends Screen {
    private static final int TITLE_TOP = 18;
    private static final int TITLE_BUTTON_GAP = 18;
    private static final int BOTTOM_MARGIN = 20;

    private final BlockPos pos;
    private int titleY;

    public SignalChangerScreen(BlockPos pos) {
        super(Component.literal("変更機"));
        this.pos = pos.immutable();
    }

    @Override
    protected void init() {
        SignalAspect[] aspects = SignalAspect.values();
        int count = aspects.length;
        int widthButton = Math.min(220, width - 40);
        int availableHeight = Math.max(140, height - TITLE_TOP - TITLE_BUTTON_GAP - BOTTOM_MARGIN);
        int gap = Mth.clamp((availableHeight - count * 20) / Math.max(1, count - 1), 6, 10);
        int heightButton = Mth.clamp((availableHeight - gap * Math.max(0, count - 1)) / count, 20, 24);
        int totalHeight = count * heightButton + Math.max(0, count - 1) * gap;
        int startX = (width - widthButton) / 2;
        int startY = Math.max(TITLE_TOP + TITLE_BUTTON_GAP, (height - totalHeight) / 2);
        titleY = Math.max(8, startY - TITLE_BUTTON_GAP);
        int index = 0;
        for (SignalAspect aspect : aspects) {
            int y = startY + index++ * (heightButton + gap);
            addRenderableWidget(Button.builder(Component.literal(aspect.getLabel()), button -> {
                PacketDistributor.sendToServer(new SetSignalAspectPayload(pos, aspect.getId()));
                onClose();
            }).bounds(startX, y, widthButton, heightButton).build());
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, titleY, 0xFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
