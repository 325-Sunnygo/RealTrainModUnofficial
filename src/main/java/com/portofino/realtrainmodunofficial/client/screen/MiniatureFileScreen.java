package com.portofino.realtrainmodunofficial.client.screen;

import com.portofino.realtrainmodunofficial.network.MiniatureFiles;
import com.portofino.realtrainmodunofficial.network.MiniatureLoadPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.network.PacketDistributor;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * NGTO ファイルの選択 / 書き出し画面 (neo mcte)。
 * 本家 MCTE の「選択」「書き出し」ボタンから開く画面に相当する。
 */
public class MiniatureFileScreen extends Screen {

    private final MiniatureSettingsScreen parent;
    private final boolean exporting;

    private FileList list;
    private EditBox nameBox;
    private Button confirm;

    public MiniatureFileScreen(MiniatureSettingsScreen parent, boolean exporting) {
        super(Component.translatable(exporting
            ? "gui.realtrainmodunofficial.miniature.export"
            : "gui.realtrainmodunofficial.miniature.select"));
        this.parent = parent;
        this.exporting = exporting;
    }

    @Override
    protected void init() {
        int hw = this.width / 2;

        list = new FileList(this.minecraft, this.width, this.height - 96, 32, 20);
        addRenderableWidget(list);

        if (exporting) {
            nameBox = new EditBox(this.font, hw - 150, this.height - 60, 300, 20, Component.empty());
            nameBox.setMaxLength(64);
            nameBox.setHint(Component.translatable("gui.realtrainmodunofficial.miniature.file_name"));
            addRenderableWidget(nameBox);
        }

        confirm = addRenderableWidget(Button.builder(
            Component.translatable(exporting ? "gui.realtrainmodunofficial.miniature.save" : "gui.realtrainmodunofficial.miniature.load"),
            b -> onConfirm()).bounds(hw - 155, this.height - 28, 150, 20).build());
        confirm.active = exporting;

        addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose())
            .bounds(hw + 5, this.height - 28, 150, 20).build());
    }

    private void onConfirm() {
        if (exporting) {
            String name = nameBox.getValue().trim();
            if (name.isEmpty() || name.contains("/") || name.contains("\\") || name.contains("..")) {
                return;
            }
            if (!name.endsWith(MiniatureFiles.EXTENSION)) {
                name = name + MiniatureFiles.EXTENSION;
            }
            if (parent.ngto() == null) {
                return;
            }
            try {
                parent.ngto().exportToFile(MiniatureFiles.dir().resolve(name).toFile());
                this.minecraft.gui.setOverlayMessage(
                    Component.literal("ミニチュア: " + name + " へ書き出しました"), false);
            } catch (Exception e) {
                this.minecraft.gui.setOverlayMessage(
                    Component.literal("ミニチュア: 書き出しに失敗しました"), false);
            }
        } else {
            FileEntry sel = list.getSelectedFile();
            if (sel == null) {
                return;
            }
            PacketDistributor.sendToServer(new MiniatureLoadPayload(
                parent.hand() == InteractionHand.OFF_HAND, sel.name()));
        }
        onClose();
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(this.font, this.title, this.width / 2, 14, 0xFFFFFF);
        g.drawCenteredString(this.font,
            Component.literal(MiniatureFiles.dir().toString()), this.width / 2, 24, 0x808080);
    }

    private record FileEntry(String name, long size) {
    }

    private class FileList extends ObjectSelectionList<FileRow> {
        FileList(net.minecraft.client.Minecraft mc, int width, int height, int top, int itemHeight) {
            super(mc, width, height, top, itemHeight);
            File[] files = MiniatureFiles.dir().toFile()
                .listFiles(f -> f.isFile() && f.getName().endsWith(MiniatureFiles.EXTENSION));
            List<File> sorted = files == null ? new ArrayList<>() : new ArrayList<>(Arrays.asList(files));
            sorted.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
            for (File f : sorted) {
                addEntry(new FileRow(new FileEntry(f.getName(), f.length())));
            }
        }

        FileEntry getSelectedFile() {
            FileRow e = getSelected();
            return e == null ? null : e.file;
        }
    }

    private class FileRow extends ObjectSelectionList.Entry<FileRow> {
        private final FileEntry file;

        FileRow(FileEntry file) {
            this.file = file;
        }

        @Override
        public void render(GuiGraphics g, int index, int top, int left, int width, int height,
                           int mouseX, int mouseY, boolean hovered, float partialTick) {
            g.drawString(MiniatureFileScreen.this.font, file.name(), left + 4, top + 4, 0xFFFFFF, false);
            String size = String.format("%.1f KB", file.size() / 1024.0);
            g.drawString(MiniatureFileScreen.this.font, size,
                left + width - MiniatureFileScreen.this.font.width(size) - 6, top + 4, 0x808080, false);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            list.setSelected(this);
            if (confirm != null) {
                confirm.active = true;
            }
            return true;
        }

        @Override
        public Component getNarration() {
            return Component.literal(file.name());
        }
    }
}
