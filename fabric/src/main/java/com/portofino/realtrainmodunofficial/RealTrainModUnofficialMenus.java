package com.portofino.realtrainmodunofficial;

import com.portofino.realtrainmodunofficial.menu.EditorMenu;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** メニュー (コンテナ画面) の登録。 */
public final class RealTrainModUnofficialMenus {

    public static final DeferredRegister<MenuType<?>> MENUS =
        DeferredRegister.create(BuiltInRegistries.MENU, RealTrainModUnofficial.MODID);

    /** エディタ (neo mcte)。本家 ContainerEditor 相当。 */
    public static final DeferredHolder<MenuType<?>, MenuType<EditorMenu>> EDITOR =
        MENUS.register("editor", () -> IMenuTypeExtension.create(EditorMenu::new));

    private RealTrainModUnofficialMenus() {
    }
}
