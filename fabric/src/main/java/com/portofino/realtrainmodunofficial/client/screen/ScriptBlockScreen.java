package com.portofino.realtrainmodunofficial.client.screen;

import com.portofino.realtrainmodunofficial.blockentity.ScriptBlockEntity;
import com.portofino.realtrainmodunofficial.network.UpdateScriptBlockPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

public class ScriptBlockScreen extends Screen {
    private static final int LINE_COUNT = 12;
    private static final int LINE_LENGTH = 120;

    private final BlockPos pos;
    private final List<EditBox> lines = new ArrayList<>();
    private boolean runOnRedstone = true;
    private String lastError = "";

    public ScriptBlockScreen(BlockPos pos) {
        super(Component.literal("scriptブロック"));
        this.pos = pos.immutable();
    }

    @Override
    protected void init() {
        readState();
        int boxWidth = Math.min(420, width - 40);
        int x = (width - boxWidth) / 2;
        int startY = 38;
        List<String> splitLines = splitScript(readScript());
        for (int i = 0; i < LINE_COUNT; i++) {
            EditBox line = new EditBox(font, x, startY + i * 18, boxWidth, 16, Component.literal("line" + i));
            line.setMaxLength(LINE_LENGTH);
            line.setValue(i < splitLines.size() ? splitLines.get(i) : "");
            addRenderableWidget(line);
            lines.add(line);
        }
        if (!lines.isEmpty()) {
            setInitialFocus(lines.get(0));
        }

        addRenderableWidget(CycleButton.onOffBuilder(runOnRedstone)
            .create(x, startY + LINE_COUNT * 18 + 8, 120, 20, Component.literal("赤石実行"), (button, value) -> runOnRedstone = value));
        addRenderableWidget(Button.builder(Component.literal("貼り付け"), button -> pasteClipboard())
            .bounds(x + 128, startY + LINE_COUNT * 18 + 8, 70, 20)
            .build());
        addRenderableWidget(Button.builder(Component.literal("保存"), button -> submit(false))
            .bounds(x + 206, startY + LINE_COUNT * 18 + 8, 70, 20)
            .build());
        addRenderableWidget(Button.builder(Component.literal("保存して実行"), button -> submit(true))
            .bounds(x + 284, startY + LINE_COUNT * 18 + 8, 110, 20)
            .build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose())
            .bounds(x + boxWidth - 70, startY + LINE_COUNT * 18 + 32, 70, 20)
            .build());
    }

    private String readScript() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null && minecraft.level.getBlockEntity(pos) instanceof ScriptBlockEntity blockEntity) {
            runOnRedstone = blockEntity.isRunOnRedstone();
            lastError = blockEntity.getLastError();
            return blockEntity.getScript();
        }
        return "";
    }

    private void readState() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null && minecraft.level.getBlockEntity(pos) instanceof ScriptBlockEntity blockEntity) {
            runOnRedstone = blockEntity.isRunOnRedstone();
            lastError = blockEntity.getLastError();
        }
    }

    private List<String> splitScript(String script) {
        List<String> result = new ArrayList<>();
        if (script == null || script.isEmpty()) {
            return result;
        }
        String[] rawLines = script.replace("\r", "").split("\n", -1);
        for (int i = 0; i < rawLines.length && result.size() < LINE_COUNT; i++) {
            String current = rawLines[i];
            while (current.length() > LINE_LENGTH && result.size() < LINE_COUNT) {
                result.add(current.substring(0, LINE_LENGTH));
                current = current.substring(LINE_LENGTH);
            }
            if (result.size() < LINE_COUNT) {
                result.add(current);
            }
        }
        return result;
    }

    private void pasteClipboard() {
        List<String> splitLines = splitScript(Minecraft.getInstance().keyboardHandler.getClipboard());
        for (int i = 0; i < lines.size(); i++) {
            lines.get(i).setValue(i < splitLines.size() ? splitLines.get(i) : "");
        }
    }

    private void submit(boolean executeNow) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            String value = lines.get(i).getValue();
            if (value.isEmpty() && builder.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(value);
        }
        PacketDistributor.sendToServer(new UpdateScriptBlockPayload(pos, builder.toString(), runOnRedstone, executeNow));
        onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        int x = width / 2;
        graphics.drawCenteredString(font, title, x, 12, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.literal("level / world / pos / x / y / z / powered / redstone / train を使えます"), x, 24, 0xAAAAAA);
        if (!lastError.isBlank()) {
            graphics.drawCenteredString(font, Component.literal("前回: " + lastError), x, height - 14, 0xFF7777);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
