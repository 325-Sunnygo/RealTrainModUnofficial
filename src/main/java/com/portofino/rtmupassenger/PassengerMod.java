package com.portofino.rtmupassenger;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import com.portofino.rtmupassenger.entity.PassengerEntity;
import com.portofino.rtmupassenger.station.StationBlock;
import com.portofino.rtmupassenger.station.StationBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 乗客シミュレーション (旧・別 jar rtmupassenger を RTMU 本体へ統合したもの)。
 *
 * <p>もう独立した mod ではない。ブロック/アイテム/エンティティは<b>すべて RTMU 本体
 * (realtrainmodunofficial 名前空間)</b> に登録される。本体の起動時に {@link #register(IEventBus)} を
 * 呼ぶことで初期化される。
 *
 * <ul>
 *   <li>駅ブロック: 右クリックで駅名・タグ (住宅街/オフィス街/工業地帯/…) を GUI で設定</li>
 *   <li>乗客: 需要 (時間帯 × タグ) に応じて駅で待ち、停車してドアが開いた列車に自動乗車、目的駅で降車</li>
 *   <li>列車が壊されたら乗車中の乗客は列車ごと消える</li>
 * </ul>
 */
public final class PassengerMod {

    /** 統合後は RTMU 本体と同じ名前空間。 */
    public static final String MODID = RealTrainModUnofficial.MODID;
    public static final Logger LOGGER = LoggerFactory.getLogger("RTMU-PassengerSim");

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, MODID);

    public static final DeferredBlock<StationBlock> STATION = BLOCKS.register(
            "station", () -> new StationBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_BLUE).strength(1.0F).noOcclusion()));

    public static final DeferredItem<BlockItem> STATION_ITEM = ITEMS.register(
            "station", () -> new BlockItem(STATION.get(), new Item.Properties()));

    //停止位置目標 (透明・当たり判定なしのマーカー)。乗客がここで列車を待つ。
    public static final DeferredBlock<com.portofino.rtmupassenger.station.StopTargetBlock> STOP_TARGET = BLOCKS.register(
            "stop_target", () -> new com.portofino.rtmupassenger.station.StopTargetBlock(
                    BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(0.5F)
                            .noOcclusion().noCollission().noLootTable().instabreak()));

    public static final DeferredItem<BlockItem> STOP_TARGET_ITEM = ITEMS.register(
            "stop_target", () -> new BlockItem(STOP_TARGET.get(), new Item.Properties()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StationBlockEntity>> STATION_BE =
            BLOCK_ENTITIES.register("station", () -> BlockEntityType.Builder.of(
                    StationBlockEntity::new, STATION.get()).build(null));

    public static final DeferredHolder<EntityType<?>, EntityType<PassengerEntity>> PASSENGER =
            ENTITIES.register("passenger", () -> EntityType.Builder.of(PassengerEntity::new, MobCategory.CREATURE)
                    .sized(0.6F, 1.8F)
                    .clientTrackingRange(10)
                    .build("passenger"));

    private PassengerMod() {
    }

    /** RTMU 本体のコンストラクタから呼ぶ。全レジストリと属性リスナーを本体のイベントバスへ載せる。 */
    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        ENTITIES.register(modEventBus);
        modEventBus.addListener(PassengerMod::registerAttributes);
    }

    private static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(PASSENGER.get(), PassengerEntity.createAttributes().build());
    }
}
