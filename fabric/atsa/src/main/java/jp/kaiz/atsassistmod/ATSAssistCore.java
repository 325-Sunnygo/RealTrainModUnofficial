package jp.kaiz.atsassistmod;

import com.mojang.brigadier.arguments.StringArgumentType;
import jp.kaiz.atsassistmod.block.GroundUnitBlock;
import jp.kaiz.atsassistmod.block.tileentity.GroundUnitBlockEntity;
import jp.kaiz.atsassistmod.controller.TrainControllerManager;
import jp.kaiz.atsassistmod.controller.trainprotection.TrainProtectionType;
import jp.kaiz.atsassistmod.network.GroundUnitConfigPayload;
import jp.ngt.rtm.entity.train.EntityTrainBase;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * RTMU-ATSAssistMod_1.21.1 — 本家 ATSAssistMod (jp.kaiz.atsassistmod / Kaiz_JP / GPL-3.0) の
 * 1.21.1 移植。RTMU 本体とは別 jar。
 *
 * <p>移植済み:
 * <ul>
 *   <li>地上子 (GroundUnit) ブロック — ATC 速度制限 (予告/解除/全解除)、TASC (予告/補正/停止位置/解除)、
 *       ATO (出発/速度変更/解除)、列車状態設定、保安装置切替。右クリックで設定 GUI。</li>
 *   <li>車上装置 (TrainController) — 速度制限/ATO/TASC/保安装置をまとめて照査、自動ブレーキ。</li>
 *   <li>保安装置 — 開放/構内/Rn-ATS/R-ATS/ATS-Ps/ATACS(枠)。{@code /atsa tp <名前>} でも切替可。</li>
 * </ul>
 */
@Mod(ATSAssistCore.MODID)
public class ATSAssistCore {

    public static final String MODID = "atsassistmod";
    public static final Logger LOGGER = LoggerFactory.getLogger("ATSAssistMod");

    //--- 登録 ---
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final DeferredBlock<GroundUnitBlock> GROUND_UNIT = BLOCKS.register(
            "ground_unit", () -> new GroundUnitBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE).strength(1.0F).noOcclusion()));

    public static final DeferredItem<BlockItem> GROUND_UNIT_ITEM = ITEMS.register(
            "ground_unit", () -> new BlockItem(GROUND_UNIT.get(), new Item.Properties()));

    //本家 TrainProtectionSelector (列車に乗って右クリック → 運転/保安装置切替 GUI)
    public static final DeferredItem<jp.kaiz.atsassistmod.item.TrainProtectionSelectorItem> TP_SELECTOR_ITEM =
            ITEMS.register("train_protection_selector",
                    jp.kaiz.atsassistmod.item.TrainProtectionSelectorItem::new);

    //本家 DataMapEditor (列車の DataMap 閲覧)
    public static final DeferredItem<jp.kaiz.atsassistmod.item.DataMapEditorItem> DATA_MAP_EDITOR_ITEM =
            ITEMS.register("data_map_editor",
                    jp.kaiz.atsassistmod.item.DataMapEditorItem::new);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GroundUnitBlockEntity>> GROUND_UNIT_BE =
            BLOCK_ENTITIES.register("ground_unit", () -> BlockEntityType.Builder.of(
                    GroundUnitBlockEntity::new, GROUND_UNIT.get()).build(null));

    //本家 IFTTT ブロック (ゲーム内自動化: 条件 This → アクション That)
    public static final DeferredBlock<jp.kaiz.atsassistmod.block.IFTTTBlock> IFTTT = BLOCKS.register(
            "ifttt", () -> new jp.kaiz.atsassistmod.block.IFTTTBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE).strength(1.0F)));

    public static final DeferredItem<BlockItem> IFTTT_ITEM = ITEMS.register(
            "ifttt", () -> new BlockItem(IFTTT.get(), new Item.Properties()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<jp.kaiz.atsassistmod.block.tileentity.IFTTTBlockEntity>> IFTTT_BE =
            BLOCK_ENTITIES.register("ifttt", () -> BlockEntityType.Builder.of(
                    jp.kaiz.atsassistmod.block.tileentity.IFTTTBlockEntity::new, IFTTT.get()).build(null));

    //本家 CreativeTabATSAssist
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register(
            "atsassist", () -> CreativeModeTab.builder()
                    .title(Component.literal("ATSAssistMod"))
                    .icon(() -> GROUND_UNIT_ITEM.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(GROUND_UNIT_ITEM.get());
                        output.accept(IFTTT_ITEM.get());
                        output.accept(TP_SELECTOR_ITEM.get());
                        output.accept(DATA_MAP_EDITOR_ITEM.get());
                    })
                    .build());

    public ATSAssistCore(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        TABS.register(modEventBus);
        modEventBus.addListener(this::registerPayloads);

        //車上装置の tick (本家 TrainControllerManager.onTick)
        NeoForge.EVENT_BUS.addListener(TrainControllerManager::onServerTick);
        NeoForge.EVENT_BUS.addListener(this::registerCommands);
        LOGGER.info("RTMU-ATSAssistMod loaded (original ATSAssistMod by Kaiz_JP)");
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToServer(GroundUnitConfigPayload.TYPE, GroundUnitConfigPayload.STREAM_CODEC,
                GroundUnitConfigPayload::handleOnServer);
        registrar.playToServer(jp.kaiz.atsassistmod.network.AtsaTrainControlPayload.TYPE,
                jp.kaiz.atsassistmod.network.AtsaTrainControlPayload.STREAM_CODEC,
                jp.kaiz.atsassistmod.network.AtsaTrainControlPayload::handleOnServer);
        //IFTTT ブロック
        registrar.playToServer(jp.kaiz.atsassistmod.network.IFTTTUpdatePayload.TYPE,
                jp.kaiz.atsassistmod.network.IFTTTUpdatePayload.STREAM_CODEC,
                jp.kaiz.atsassistmod.network.IFTTTUpdatePayload::handleOnServer);
        registrar.playToClient(jp.kaiz.atsassistmod.network.IFTTTPlaySoundPayload.TYPE,
                jp.kaiz.atsassistmod.network.IFTTTPlaySoundPayload.STREAM_CODEC,
                jp.kaiz.atsassistmod.network.IFTTTPlaySoundPayload::handleOnClient);
    }

    private void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("atsa")
                .then(Commands.literal("tp")
                    //一覧表示
                    .executes(context -> {
                        String list = Arrays.stream(TrainProtectionType.values())
                                .map(Enum::name)
                                .collect(Collectors.joining(", "));
                        context.getSource().sendSuccess(
                                () -> Component.literal("保安装置: " + list + " — /atsa tp <名前> で設定"), false);
                        return 1;
                    })
                    //設定 (乗車中の列車)
                    .then(Commands.argument("type", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            for (TrainProtectionType type : TrainProtectionType.values()) {
                                builder.suggest(type.name());
                            }
                            return builder.buildFuture();
                        })
                        .executes(context -> {
                            var player = context.getSource().getPlayerOrException();
                            if (!(player.getVehicle() instanceof EntityTrainBase train)) {
                                context.getSource().sendFailure(Component.literal("列車に乗ってから実行してください"));
                                return 0;
                            }
                            String name = StringArgumentType.getString(context, "type");
                            TrainProtectionType type;
                            try {
                                type = TrainProtectionType.valueOf(name);
                            } catch (IllegalArgumentException e) {
                                context.getSource().sendFailure(Component.literal("不明な保安装置: " + name));
                                return 0;
                            }
                            TrainControllerManager.getTrainController(train).setTrainProtection(type);
                            context.getSource().sendSuccess(
                                    () -> Component.literal("保安装置を " + type.name() + " に設定しました"), false);
                            return 1;
                        })
                    )
                )
        );
    }
}
