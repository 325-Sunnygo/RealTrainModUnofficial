package com.portofino.realtrainmodunofficial;

import net.neoforged.api.distmarker.Dist;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(RealTrainModUnofficial.MODID)
public class RealTrainModUnofficial {
    public static final String MODID = "realtrainmodunofficial";
    public static final Logger LOGGER = LogUtils.getLogger();

    /**
     * ビルド刻印。同じバージョン番号を再利用してビルドし直しているため、
     * サーバー/クライアントに<b>どのビルド</b>が入っているかはこれでしか判別できない。
     * 起動ログに出る。ビルドを更新するたびに日付+連番を上げること。
     * <p>
     * <b>ここのバージョンは gradle.properties の mod_version と必ず合わせること。</b>
     * 報告者から送られるログの判別材料がこれなので、古い番号が残っていると
     * 「どのビルドで起きたか」を取り違える。
     */
    public static final String BUILD_TAG = "1.0.11 build 2026-07-20d (train-render-perf+replay-culling-fix)";

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN_TAB =
        CREATIVE_MODE_TABS.register("main_tab", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.realtrainmodunofficial"))
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .icon(() -> RealTrainModUnofficialItems.RAIL_ITEM.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(RealTrainModUnofficialItems.TRAIN_ITEM.get());
                output.accept(RealTrainModUnofficialItems.CAR_ITEM.get());
                //本家 itemMotorman (運転士): 列車に使うと乗車して信号/ダイヤ/マクロで自動運転
                output.accept(RealTrainModUnofficialItems.MOTORMAN_ITEM.get());
                output.accept(RealTrainModUnofficialItems.IC_CARD_ITEM.get());
                output.accept(RealTrainModUnofficialItems.RAIL_ITEM.get());
                output.accept(RealTrainModUnofficialItems.WIRE_ITEM.get());
                output.accept(RealTrainModUnofficialItems.CROWBAR_ITEM.get());
                output.accept(RealTrainModUnofficialItems.WRENCH_ITEM.get());
                output.accept(RealTrainModUnofficialItems.CROSSING_GATE_ITEM.get());
                output.accept(RealTrainModUnofficialItems.MARKER_ITEM.get());
                //マーカー(斜め)/分岐マーカー(斜め)/スピーカー はユーザー要望で削除
                //(本家は通常マーカーが8方位対応のため斜めバリアント不要)
                output.accept(RealTrainModUnofficialItems.MARKER_SWITCH_ITEM.get());
                output.accept(RealTrainModUnofficialItems.LIGHT_ITEM.get());
                output.accept(RealTrainModUnofficialItems.SIGNBOARD_ITEM.get());
                output.accept(RealTrainModUnofficialItems.INSULATOR_ITEM.get());
                output.accept(RealTrainModUnofficialItems.PIPE_ITEM.get());
                output.accept(RealTrainModUnofficialItems.SIGNAL_ITEM.get());
                output.accept(RealTrainModUnofficialItems.TRAIN_DETECTOR_ITEM.get());
                //架線柱: 本家モデル (LinePole01/02/Frame01/SignalPole01) を同梱したので再追加。
                //以前は選択できるモデルが1つも無かったためタブから外していた。
                output.accept(RealTrainModUnofficialItems.OVERHEAD_LINE_POLE_ITEM.get());
                output.accept(RealTrainModUnofficialItems.TICKET_GATE_ITEM.get());
                //入力コネクタ/出力コネクタ/信号変換器 はユーザー要望で削除
                //(信号機とワイヤーのみ残す。登録は既存ワールド互換のため残置)
                //ミニチュアもユーザー要望でタブから削除 (登録は残置 — NGTO Builder は
                //コマンド入手や既存アイテムで引き続き使用可能)
                //SignalControllerMod (masa300) 移植
                output.accept(RealTrainModUnofficialItems.SIGNAL_CONTROLLER_ITEM.get());
                output.accept(RealTrainModUnofficialItems.POS_SETTING_TOOL_0.get());
                output.accept(RealTrainModUnofficialItems.POS_SETTING_TOOL_1.get());
                //スピーカー: 本家仕様化 (スピーカーごとの音登録+可聴範囲) に伴い再追加
                output.accept(RealTrainModUnofficialItems.SPEAKER_ITEM.get());
                //本家 ItemInstalledObject から移植した設置物
                output.accept(RealTrainModUnofficialItems.FLUORESCENT_ITEM.get());
                output.accept(RealTrainModUnofficialItems.RAILROAD_SIGN_ITEM.get());
                output.accept(RealTrainModUnofficialItems.BUMPING_POST_ITEM.get());
                output.accept(RealTrainModUnofficialItems.POINT_MACHINE_ITEM.get());
                output.accept(RealTrainModUnofficialItems.TICKET_VENDOR_ITEM.get());
                output.accept(RealTrainModUnofficialItems.TICKET_ITEM.get());
                output.accept(RealTrainModUnofficialItems.TICKET_BOOK_ITEM.get());
                output.accept(RealTrainModUnofficialItems.CAMERA_ITEM.get());
                //交換レンズ (広角→標準→望遠→超望遠→サンニッパ→撒き餌) とテレコン
                output.accept(RealTrainModUnofficialItems.LENS_WIDE.get());
                output.accept(RealTrainModUnofficialItems.LENS_STANDARD.get());
                output.accept(RealTrainModUnofficialItems.LENS_TELE.get());
                output.accept(RealTrainModUnofficialItems.LENS_SUPER_TELE.get());
                output.accept(RealTrainModUnofficialItems.LENS_SANNI.get());
                output.accept(RealTrainModUnofficialItems.LENS_NIFTY.get());
                output.accept(RealTrainModUnofficialItems.TELECONVERTER_14.get());
                output.accept(RealTrainModUnofficialItems.TELECONVERTER_20.get());
                //リモコン: ブロック2つを無線レッドストーンでペアリング
                output.accept(RealTrainModUnofficialItems.REMOTE_ITEM.get());
                //乗客シミュレーション (統合): 駅ブロック / 停止位置目標
                output.accept(com.portofino.rtmupassenger.PassengerMod.STATION_ITEM.get());
                output.accept(com.portofino.rtmupassenger.PassengerMod.STOP_TARGET_ITEM.get());
            }).build());

    /**
     * mods フォルダの 1.7.10 建材 mod からかき集めたブロックを全部並べる専用タブ。
     * 中身は {@link com.portofino.realtrainmodunofficial.building.ExternalBuildingBlocks#TAB_ITEMS}
     * (コンストラクタの init() で構築)。空でもタブ自体は出す (レンガアイコン)。
     */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> EXTERNAL_BUILDING_TAB =
        CREATIVE_MODE_TABS.register("external_building_tab", () -> CreativeModeTab.builder()
            .title(Component.literal("外部建材 (1.7.10)"))
            .withTabsAfter(MAIN_TAB.getKey())
            .icon(() -> com.portofino.realtrainmodunofficial.building.ExternalBuildingBlocks.TAB_ITEMS.isEmpty()
                ? new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BRICKS)
                : com.portofino.realtrainmodunofficial.building.ExternalBuildingBlocks.TAB_ITEMS.get(0).get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                for (var item : com.portofino.realtrainmodunofficial.building.ExternalBuildingBlocks.TAB_ITEMS) {
                    output.accept(item.get());
                }
            }).build());

    public RealTrainModUnofficial(IEventBus modEventBus, ModContainer modContainer, Dist dist) {
        //どのビルドが動いているかの唯一の証拠 (バージョン番号は再利用しているため)。
        LOGGER.info("[RTMU] {}", BUILD_TAG);
        // 軽量化: 既定のログレベルは INFO に固定する(描画には無関係)。
        // 以前はバグ追跡のため DEBUG を強制していたが、毎tick/毎フレームの DEBUG ログが
        // 文字列整形・I/O コストになり負荷源になるため INFO に下げる(調査時は手動で DEBUG に上げる)。
        try {
            org.apache.logging.log4j.core.config.Configurator.setLevel(
                "com.portofino.realtrainmodunofficial",
                org.apache.logging.log4j.Level.INFO
            );
        } catch (Throwable t) {
            LOGGER.warn("Failed to set log level for rtmu: {}", t.toString());
        }

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::registerNetwork);
        modEventBus.addListener(this::buildCreativeTabContents);
        //本家 RTM のチャンクローダー (列車の State_ChunkLoader) 用チケットコントローラ
        modEventBus.addListener((net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent event) ->
            event.register(com.portofino.realtrainmodunofficial.world.TrainChunkLoader.CONTROLLER));

        RealTrainModUnofficialBlocks.BLOCKS.register(modEventBus);
        //mods フォルダの 1.7.10 建材 mod をスキャンし、ブロックテクスチャをフルキューブブロックとして
        //登録する (レジストリ凍結前に走らせる必要があるのでここで呼ぶ)。
        com.portofino.realtrainmodunofficial.building.ExternalBuildingBlocks.init(modEventBus);
        // jp.ngt.rtm.rail: 本家忠実移植のレール/マーカー (Phase 1)
        jp.ngt.rtm.rail.RTMRailBlocks.REGISTER.register(modEventBus);
        jp.ngt.rtm.rail.RTMRailBlockEntities.REGISTER.register(modEventBus);
        jp.ngt.rtm.item.RTMItems.REGISTER.register(modEventBus);
        // jp.ngt.rtm.entity: 本家忠実移植の列車/台車 (Phase 2)
        jp.ngt.rtm.entity.RTMEntities.REGISTER.register(modEventBus);
        RealTrainModUnofficialItems.ITEMS.register(modEventBus);
        RealTrainModUnofficialEntities.ENTITIES.register(modEventBus);
        com.portofino.realtrainmodunofficial.registry.RealTrainModUnofficialEntities.ENTITY_TYPES.register(modEventBus);
        RealTrainModUnofficialBlockEntities.BLOCK_ENTITY_TYPES.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
        RealTrainModUnofficialComponents.REGISTRAR.register(modEventBus);
        //乗客シミュレーション (旧・別 jar rtmupassenger を統合)。本体の名前空間で登録する。
        com.portofino.rtmupassenger.PassengerMod.register(modEventBus);
        //WebCTC は別 mod (RTMU-WebCTC_1.21.1, webctc サブプロジェクト) へ分離した。
        // スピーカー音源マッピングをサーバー起動時にロードし、プレイヤー接続時に同期する。
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
            (net.neoforged.neoforge.event.server.ServerStartingEvent e) ->
                com.portofino.realtrainmodunofficial.installedobject.SpeakerSoundConfig.load());
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
            (net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent e) -> {
                if (e.getEntity() instanceof net.minecraft.server.level.ServerPlayer sp) {
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(sp,
                        new com.portofino.realtrainmodunofficial.network.SyncSpeakerSoundsPayload(
                            java.util.Arrays.asList(
                                com.portofino.realtrainmodunofficial.installedobject.SpeakerSoundConfig.snapshot())));
                }
            });

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            BundledPackInstaller.installDefaultPacks();
            com.portofino.realtrainmodunofficial.rail.RailPackLoader.load();
            com.portofino.realtrainmodunofficial.vehicle.VehiclePackLoader.load();
            com.portofino.realtrainmodunofficial.installedobject.InstalledObjectPackLoader.load();
            com.portofino.realtrainmodunofficial.script.TrainScriptSystem.getInstance().initialize();
        });
    }

    private void registerNetwork(RegisterPayloadHandlersEvent event) {
        com.portofino.realtrainmodunofficial.network.RealTrainModUnofficialNetwork.registerPayloadHandlers(event);
    }

    private void buildCreativeTabContents(BuildCreativeModeTabContentsEvent event) {
        if (CreativeModeTabs.REDSTONE_BLOCKS.equals(event.getTabKey())) {
            event.accept(RealTrainModUnofficialItems.CROSSING_GATE_ITEM.get());
            event.accept(RealTrainModUnofficialItems.SIGNAL_ITEM.get());
            event.accept(RealTrainModUnofficialItems.SPEAKER_ITEM.get());
        }
    }
}
