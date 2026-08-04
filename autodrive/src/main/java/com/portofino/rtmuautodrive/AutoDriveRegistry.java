package com.portofino.rtmuautodrive;

import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.function.Supplier;

/**
 * ローダーごとに違う登録の結果を、共通コードから引くための受け口。
 *
 * <p>★値ではなく <b>Supplier</b> を預かる。NeoForge の DeferredHolder は登録が終わるまで
 * get できないので、値で受け取ると mod の生成時点ではまだ null になる。
 */
public final class AutoDriveRegistry {

    private static Supplier<BlockEntityType<TrainDispatcherBlockEntity>> dispatcherBe;
    private static Supplier<MenuType<DispatcherMenu>> dispatcherMenu;
    private static Supplier<BlockEntityType<StationStopBlockEntity>> stationBe;

    private AutoDriveRegistry() {
    }

    public static BlockEntityType<TrainDispatcherBlockEntity> dispatcherBlockEntityType() {
        return dispatcherBe == null ? null : dispatcherBe.get();
    }

    public static MenuType<DispatcherMenu> dispatcherMenuType() {
        return dispatcherMenu == null ? null : dispatcherMenu.get();
    }

    public static BlockEntityType<StationStopBlockEntity> stationBlockEntityType() {
        return stationBe == null ? null : stationBe.get();
    }

    public static void setStationBlockEntityType(Supplier<BlockEntityType<StationStopBlockEntity>> supplier) {
        stationBe = supplier;
    }

    public static void setDispatcherBlockEntityType(Supplier<BlockEntityType<TrainDispatcherBlockEntity>> supplier) {
        dispatcherBe = supplier;
    }

    public static void setDispatcherMenuType(Supplier<MenuType<DispatcherMenu>> supplier) {
        dispatcherMenu = supplier;
    }
}
