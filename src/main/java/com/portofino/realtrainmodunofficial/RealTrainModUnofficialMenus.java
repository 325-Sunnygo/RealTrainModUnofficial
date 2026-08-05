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

    /** RTM 専用作業台 (5x5)。本家 ContainerRTMWorkBench 相当。 */
    public static final DeferredHolder<MenuType<?>, MenuType<com.portofino.realtrainmodunofficial.menu.WorkBenchMenu>> WORK_BENCH =
        MENUS.register("work_bench", () -> IMenuTypeExtension.create(
            (id, inv, buf) -> new com.portofino.realtrainmodunofficial.menu.WorkBenchMenu(id, inv)));

    /** NPC の装備 (本家 ContainerNPC 相当)。 */
    public static final DeferredHolder<MenuType<?>, MenuType<com.portofino.realtrainmodunofficial.menu.NpcMenu>> NPC =
        MENUS.register("npc", () -> IMenuTypeExtension.create(
            (id, inv, buf) -> new com.portofino.realtrainmodunofficial.menu.NpcMenu(id, inv)));

    private RealTrainModUnofficialMenus() {
    }
}
