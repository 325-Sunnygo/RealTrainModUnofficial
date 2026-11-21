package net.neoforged.neoforge.client.event;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

public abstract class ScreenEvent extends Event {
    private final Screen screen;

    protected ScreenEvent(Screen screen) {
        this.screen = screen;
    }

    public Screen getScreen() {
        return screen;
    }

    /** 画面が開く直前。setNewScreen で差し替え可能。 */
    public static class Opening extends ScreenEvent implements ICancellableEvent {
        private final Screen currentScreen;
        private Screen newScreen;

        public Opening(Screen currentScreen, Screen newScreen) {
            super(newScreen);
            this.currentScreen = currentScreen;
            this.newScreen = newScreen;
        }

        public Screen getCurrentScreen() {
            return currentScreen;
        }

        public Screen getNewScreen() {
            return newScreen;
        }

        public void setNewScreen(Screen screen) {
            this.newScreen = screen;
        }
    }

    public abstract static class Init extends ScreenEvent {

        /** この画面へ足したいウィジェット。エントリポイント側が画面へ差し込む。 */
        private final java.util.List<net.minecraft.client.gui.components.events.GuiEventListener> added =
            new java.util.ArrayList<>();

        protected Init(Screen screen) {
            super(screen);
        }

        /** NeoForge の addListener 相当。 */
        public void addListener(net.minecraft.client.gui.components.events.GuiEventListener listener) {
            this.added.add(listener);
        }

        public java.util.List<net.minecraft.client.gui.components.events.GuiEventListener> getAddedListeners() {
            return this.added;
        }

        public static class Post extends Init {
            public Post(Screen screen) {
                super(screen);
            }
        }
    }

    public abstract static class Render extends ScreenEvent {
        private final GuiGraphics guiGraphics;
        private final int mouseX;
        private final int mouseY;
        private final float partialTick;

        protected Render(Screen screen, GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            super(screen);
            this.guiGraphics = guiGraphics;
            this.mouseX = mouseX;
            this.mouseY = mouseY;
            this.partialTick = partialTick;
        }

        public GuiGraphics getGuiGraphics() {
            return guiGraphics;
        }

        public int getMouseX() {
            return mouseX;
        }

        public int getMouseY() {
            return mouseY;
        }

        public float getPartialTick() {
            return partialTick;
        }

        public static class Post extends Render {
            public Post(Screen screen, GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
                super(screen, guiGraphics, mouseX, mouseY, partialTick);
            }
        }
    }
}
