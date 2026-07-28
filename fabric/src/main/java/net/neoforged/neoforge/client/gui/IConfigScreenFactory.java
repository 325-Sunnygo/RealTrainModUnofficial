package net.neoforged.neoforge.client.gui;

import net.minecraft.client.gui.screens.Screen;
import net.neoforged.fml.ModContainer;

@FunctionalInterface
public interface IConfigScreenFactory {
    Screen createScreen(ModContainer container, Screen parent);
}
