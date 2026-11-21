package net.neoforged.neoforge.client.event;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

public abstract class RenderGuiLayerEvent extends Event {
    private final GuiGraphics guiGraphics;
    private final ResourceLocation name;

    protected RenderGuiLayerEvent(GuiGraphics guiGraphics, ResourceLocation name) {
        this.guiGraphics = guiGraphics;
        this.name = name;
    }

    public GuiGraphics getGuiGraphics() {
        return guiGraphics;
    }

    public ResourceLocation getName() {
        return name;
    }

    public static class Pre extends RenderGuiLayerEvent implements ICancellableEvent {
        public Pre(GuiGraphics guiGraphics, ResourceLocation name) {
            super(guiGraphics, name);
        }
    }

    public static class Post extends RenderGuiLayerEvent {
        public Post(GuiGraphics guiGraphics, ResourceLocation name) {
            super(guiGraphics, name);
        }
    }
}
