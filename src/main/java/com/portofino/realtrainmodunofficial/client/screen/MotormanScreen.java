package com.portofino.realtrainmodunofficial.client.screen;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import com.portofino.realtrainmodunofficial.client.renderer.MotormanSkinLoader;
import com.portofino.realtrainmodunofficial.network.MotormanMacroPayload;
import com.portofino.realtrainmodunofficial.network.MotormanSkinPayload;
import jp.ngt.rtm.entity.npc.EntityMotorman;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.network.PacketDistributor;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 本家 GuiMotorman の移植 + スキン選択。運転士を素手で右クリックで開く。
 * スキン: 既定(季節)/標準/サンタ/獅子舞 + config/realtrainmodunofficial/npc_skins/*.png
 * マクロ: config/realtrainmodunofficial/macro/*.txt を選んで実行
 * (1 行 = 時刻 コマンド:引数。例: 0 Notch:5 / 300 Door:Door_OpenLeft)
 */
public class MotormanScreen extends Screen {

    private final int entityId;
    private List<Path> macros = List.of();
    private int page;
    private static final int PER_PAGE = 6;

    // スキン: 内部値と表示名 (index を合わせる)
    private final List<String> skinValues = new ArrayList<>();
    private final List<String> skinNames = new ArrayList<>();
    private int skinIndex;
    private Button skinButton;

    public MotormanScreen(int entityId) {
        super(Component.literal("運転士"));
        this.entityId = entityId;
    }

    public static Path macroFolder() {
        return FMLPaths.GAMEDIR.get().resolve("config").resolve("realtrainmodunofficial").resolve("macro");
    }

    @Override
    protected void init() {
        // --- スキン一覧 (同梱 + npc_skins/) ---
        skinValues.clear();
        skinNames.clear();
        skinValues.add("");        skinNames.add("既定 (季節)");
        skinValues.add("default"); skinNames.add("標準");
        skinValues.add("santa");   skinNames.add("サンタ");
        skinValues.add("shishi");  skinNames.add("獅子舞");
        for (String custom : MotormanSkinLoader.listSkins()) {
            skinValues.add(custom);
            skinNames.add(custom);
        }
        // 現在のスキンを初期選択にする
        skinIndex = 0;
        if (Minecraft.getInstance().level != null
                && Minecraft.getInstance().level.getEntity(entityId) instanceof EntityMotorman motorman) {
            int idx = skinValues.indexOf(motorman.getSkin());
            if (idx >= 0) {
                skinIndex = idx;
            }
        }

        // --- マクロ一覧 ---
        List<Path> found = new ArrayList<>();
        try {
            Path folder = macroFolder();
            Files.createDirectories(folder);
            try (var stream = Files.list(folder)) {
                stream.filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".txt"))
                        .sorted()
                        .forEach(found::add);
            }
        } catch (Exception ignored) {
        }
        this.macros = found;
        rebuild();
    }

    private void rebuild() {
        clearWidgets();

        // スキン切替 (◀ 名前 ▶) — 押した瞬間にサーバーへ送って見た目に反映
        int sy = 34;
        addRenderableWidget(Button.builder(Component.literal("◀"), b -> cycleSkin(-1))
                .bounds(this.width / 2 - 130, sy, 20, 20).build());
        this.skinButton = addRenderableWidget(Button.builder(skinLabel(), b -> cycleSkin(1))
                .bounds(this.width / 2 - 106, sy, 212, 20).build());
        addRenderableWidget(Button.builder(Component.literal("▶"), b -> cycleSkin(1))
                .bounds(this.width / 2 + 110, sy, 20, 20).build());

        // マクロリスト
        int y = sy + 32;
        int from = page * PER_PAGE;
        int to = Math.min(macros.size(), from + PER_PAGE);
        for (int i = from; i < to; i++) {
            Path file = macros.get(i);
            addRenderableWidget(Button.builder(Component.literal(file.getFileName().toString()),
                            b -> selectMacro(file))
                    .bounds(this.width / 2 - 130, y, 260, 20).build());
            y += 24;
        }
        if (macros.size() > PER_PAGE) {
            addRenderableWidget(Button.builder(Component.literal("<"), b -> {
                page = Math.max(0, page - 1);
                rebuild();
            }).bounds(this.width / 2 - 130, y, 40, 20).build());
            addRenderableWidget(Button.builder(Component.literal(">"), b -> {
                if ((page + 1) * PER_PAGE < macros.size()) {
                    page++;
                }
                rebuild();
            }).bounds(this.width / 2 + 90, y, 40, 20).build());
        }
        addRenderableWidget(Button.builder(Component.literal("閉じる"), b -> onClose())
                .bounds(this.width / 2 - 75, this.height - 30, 150, 20).build());
    }

    private Component skinLabel() {
        return Component.literal("スキン: " + skinNames.get(skinIndex));
    }

    private void cycleSkin(int dir) {
        skinIndex = Math.floorMod(skinIndex + dir, skinValues.size());
        if (skinButton != null) {
            skinButton.setMessage(skinLabel());
        }
        PacketDistributor.sendToServer(new MotormanSkinPayload(this.entityId, skinValues.get(skinIndex)));
    }

    private void selectMacro(Path file) {
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            PacketDistributor.sendToServer(new MotormanMacroPayload(this.entityId, String.join("\n", lines)));
        } catch (Exception e) {
            RealTrainModUnofficial.LOGGER.warn("[Motorman] macro read failed: {}", e.toString());
        }
        onClose();
    }

    /** ブラー無効化 (既定の renderBackground はぼかしを掛けて文字まで読めなくなる)。 */
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, 0xC0101010);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(this.font, this.title.getString(), this.width / 2, 14, 0xFFFFFF);
        g.drawString(this.font, "マクロ (config/realtrainmodunofficial/macro/*.txt):",
                this.width / 2 - 130, 58, 0xAAAAAA, false);
        if (this.macros.isEmpty()) {
            g.drawCenteredString(this.font, "マクロがありません", this.width / 2, this.height / 2,
                    0xFF8888);
            g.drawCenteredString(this.font, "書式: 「時刻 コマンド:引数」 例) 0 Notch:5 / 200 Notch:-8 / 300 Door:Door_OpenLeft",
                    this.width / 2, this.height / 2 + 16, 0x888888);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
