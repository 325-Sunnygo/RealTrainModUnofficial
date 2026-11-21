package net.neoforged.neoforge.client.gui;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModContainer;

/** シム: NeoForge 自動生成コンフィグ画面の代わりに親へ戻るだけの空画面。 */
public class ConfigurationScreen extends Screen {
    private final Screen parent;

    public ConfigurationScreen(ModContainer container, Screen parent) {
        super(Component.literal("Config"));
        this.parent = parent;
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
}
