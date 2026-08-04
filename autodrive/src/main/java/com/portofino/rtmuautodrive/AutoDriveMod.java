package com.portofino.rtmuautodrive;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RTMU-AutoDrive_1.21.1 — RTMU 用の自動運転 mod (別 jar)。
 *
 * <p>使い方:
 * <ol>
 *   <li><b>列車運転スポナー</b>をレールから 5 ブロック以内に置く</li>
 *   <li>右クリックして<b>名前</b>を入れ、<b>編成アイテム</b>を枠に入れる</li>
 *   <li><b>自動運転装置</b>を持って右クリック → スポナーの一覧が出る</li>
 *   <li>名前の横の<b>発車</b>を押すと編成がスポーンし、駅ブロックまで自動運転で走る</li>
 * </ol>
 *
 * <p>ARAD のような路線図/ダイヤ画面は持たない (ユーザー指定)。運転の中身は全部この mod 側で、
 * RTMU 本体には最小限のフック (運転台への乗車・駅一覧) だけを足してある。
 */
@Mod(AutoDriveMod.MODID)
public class AutoDriveMod {

    public static final String MODID = "rtmuautodrive";
    public static final Logger LOGGER = LoggerFactory.getLogger("RTMU-AutoDrive");

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, MODID);
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final DeferredItem<Item> AUTO_DRIVER_ITEM =
            ITEMS.register("auto_driver", AutoDriverItem::new);

    public static final DeferredBlock<TrainDispatcherBlock> DISPATCHER = BLOCKS.register(
            "train_dispatcher", () -> new TrainDispatcherBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLUE).strength(1.5F).sound(SoundType.METAL)));

    public static final DeferredItem<BlockItem> DISPATCHER_ITEM =
            ITEMS.register("train_dispatcher", () -> new BlockItem(DISPATCHER.get(), new Item.Properties()));

    public static final DeferredBlock<StationStopBlock> STATION = BLOCKS.register(
            "station_stop", () -> new StationStopBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GREEN).strength(1.5F).sound(SoundType.METAL)));

    public static final DeferredItem<BlockItem> STATION_ITEM =
            ITEMS.register("station_stop", () -> new BlockItem(STATION.get(), new Item.Properties()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StationStopBlockEntity>> STATION_BE =
            BLOCK_ENTITIES.register("station_stop", () -> BlockEntityType.Builder.of(
                    StationStopBlockEntity::new, STATION.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TrainDispatcherBlockEntity>> DISPATCHER_BE =
            BLOCK_ENTITIES.register("train_dispatcher", () -> BlockEntityType.Builder.of(
                    TrainDispatcherBlockEntity::new, DISPATCHER.get()).build(null));

    public static final DeferredHolder<MenuType<?>, MenuType<DispatcherMenu>> DISPATCHER_MENU =
            MENUS.register("train_dispatcher", () -> IMenuTypeExtension.create(DispatcherMenu::new));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register(
            "autodrive", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.rtmuautodrive"))
                    .icon(() -> AUTO_DRIVER_ITEM.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(DISPATCHER_ITEM.get());
                        output.accept(STATION_ITEM.get());
                        output.accept(AUTO_DRIVER_ITEM.get());
                    })
                    .build());

    public AutoDriveMod(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        MENUS.register(modEventBus);
        TABS.register(modEventBus);
        modEventBus.addListener(AutoDriveMod::onRegisterPayloads);
        //共通コードから型を引けるようにする (Supplier なので登録前でも渡せる)
        AutoDriveRegistry.setDispatcherBlockEntityType(DISPATCHER_BE::get);
        AutoDriveRegistry.setStationBlockEntityType(STATION_BE::get);
        AutoDriveRegistry.setDispatcherMenuType(DISPATCHER_MENU::get);
        LOGGER.info("[RTMU-AutoDrive] 自動運転アドオンを読み込みました");
    }

    private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        AutoDriveNetwork.register(event.registrar("1"));
    }

    @EventBusSubscriber(modid = MODID)
    public static final class ServerEvents {

        /** サーバーの毎 tick。自動運転中の編成を進める。 */
        @SubscribeEvent
        public static void onLevelTick(LevelTickEvent.Post event) {
            if (event.getLevel() instanceof ServerLevel level) {
                AutoDriveState.get(level).tick(level);
            }
        }
    }
}
