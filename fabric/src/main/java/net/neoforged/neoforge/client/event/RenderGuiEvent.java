package net.neoforged.neoforge.client.event;

import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.bus.api.Event;

public abstract class RenderGuiEvent extends Event {
    private final GuiGraphics guiGraphics;
    private final float partialTick;

    protected RenderGuiEvent(GuiGraphics guiGraphics, float partialTick) {
        this.guiGraphics = guiGraphics;
        this.partialTick = partialTick;
    }

    public GuiGraphics getGuiGraphics() {
        return guiGraphics;
    }

    public float getPartialTick() {
        return partialTick;
    }

    public static class Pre extends RenderGuiEvent {
        public Pre(GuiGraphics guiGraphics, float partialTick) {
            super(guiGraphics, partialTick);
        }
    }

    public static class Post extends RenderGuiEvent {
        public Post(GuiGraphics guiGraphics, float partialTick) {
            super(guiGraphics, partialTick);
        }
    }
}
