package net.neoforged.neoforge.common.extensions;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;

/**
 * シム: NeoForge の「追加データ付き MenuType」を作るためのファクトリ。
 * NeoForge は IMenuTypeExtension.create((id, inv, buf) -> ...) で
 * 開くときに任意のデータを積める MenuType を作れる。
 */
public interface IMenuTypeExtension<T extends AbstractContainerMenu> {

    @FunctionalInterface
    interface MenuSupplier<T extends AbstractContainerMenu> {
        T create(int windowId, Inventory inv, RegistryFriendlyByteBuf data);
    }

    static <T extends AbstractContainerMenu> MenuType<T> create(MenuSupplier<T> supplier) {
        // 追加データはバニラの MenuType では渡らないので null を入れて呼ぶ。
        // RTMU はメニューを開いた直後に専用ペイロードで内容を送るため、これで足りる。
        return new MenuType<>((windowId, inv) -> supplier.create(windowId, inv, null),
            FeatureFlags.VANILLA_SET);
    }
}
