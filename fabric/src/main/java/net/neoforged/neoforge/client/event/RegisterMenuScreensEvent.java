package net.neoforged.neoforge.client.event;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
public class RegisterMenuScreensEvent extends net.neoforged.bus.api.Event {
 public <M extends AbstractContainerMenu,U extends Screen & MenuAccess<M>> void register(MenuType<M> type,MenuScreens.ScreenConstructor<M,U> factory){MenuScreens.register(type,factory);}
}
