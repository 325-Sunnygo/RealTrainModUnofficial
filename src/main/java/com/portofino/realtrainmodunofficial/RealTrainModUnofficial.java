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
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(RealTrainModUnofficial.MODID)
public class RealTrainModUnofficial {
    public static final String MODID = "realtrainmodunofficial";
    public static final Logger LOGGER = LogUtils.getLogger();

    /**
     * ビルド刻印。
     * サーバー/クライアントにどのビルドが入っているかはこれでしか判別できない。
     */
    public static final String BUILD_TAG = "1.0.17 (Fabric 移植: キー衝突・描画段階・intermediary 名・設置物の当たり判定・可変光源; MOD 自身の規約は非表示)";

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    /** 本家 RTM にあるものを本家の並び順で出すタブ。 */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN_TAB =
        CREATIVE_MODE_TABS.register("main_tab", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.realtrainmodunofficial"))
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .icon(() -> RealTrainModUnofficialItems.RAIL_ITEM.get().getDefaultInstance())
            .displayItems((parameters, output) -> acceptMainTab(output)).build());

    /** 本家に無い RTMU 独自のものだけを出すタブ。 */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> RTMU_TAB =
        CREATIVE_MODE_TABS.register("rtmu_tab", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.realtrainmodunofficial.rtmu"))
            .withTabsAfter(MAIN_TAB.getKey())
            .icon(() -> RealTrainModUnofficialItems.FORMATION_ITEM.get().getDefaultInstance())
            .displayItems((parameters, output) -> acceptRtmuTab(output)).build());

    /** 信号制御器と位置設定ツール専用のタブ (ユーザー指定で独立させたもの)。 */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> SIGNAL_CONTROLLER_TAB =
        CREATIVE_MODE_TABS.register("signal_controller_tab", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.realtrainmodunofficial.signal_controller"))
            .withTabsAfter(RTMU_TAB.getKey())
            .icon(() -> RealTrainModUnofficialItems.SIGNAL_CONTROLLER_ITEM.get().getDefaultInstance())
            .displayItems((parameters, output) -> acceptSignalControllerTab(output)).build());

    /** シグナルコントローラー (SignalControllerMod 移植) 一式。 */
    private static void acceptSignalControllerTab(net.minecraft.world.item.CreativeModeTab.Output output) {
        output.accept(RealTrainModUnofficialItems.SIGNAL_CONTROLLER_ITEM.get());
        output.accept(RealTrainModUnofficialItems.POS_SETTING_TOOL_0.get());
        output.accept(RealTrainModUnofficialItems.POS_SETTING_TOOL_1.get());
    }

    /**
     * クリエイティブタブの並び順。<b>本家 RTM (1.7.10) と同じ順番</b>にしてある。
     *
     * <p>1.7.10 のクリエイティブタブは「アイテムの登録順」そのままで、
     * {@code RTMCore} が {@code RTMBlock.init()} → {@code RTMItem.init()} の順に呼ぶ。
     * つまり<b>ブロック (マーカー等) が先、アイテムが後</b>。だからここもマーカーから始まる。
     *
     * <p>本家はタブが 3 つ (tabRailway / tabIndustry / tabRTMTools) に分かれているが、
     * RTMU はタブ 1 つなので {@code CreativeTabRTM} の宣言順どおり
     * 鉄道 → 工業 → 道具 の順につなげる。
     *
     * <p>RTMU にしか無いもの (エディタ・リモコン・信号制御器・乗客シミュ) は、
     * 本家に対応する位置が無いので最後にまとめる。
     */
    private static void acceptMainTab(net.minecraft.world.item.CreativeModeTab.Output output) {
        // ===== 本家 tabRailway =====
        // --- RTMBlock.init() の登録順 ---
        // 本家: marker → markerSwitch → markerStraight → markerVoid → ironPillar
        //       → signalConverter → trainWorkBench → movingMachine
        // markerStraight/markerVoid/鉄柱/作業台/移動機 は RTMU に無い。
        // 信号変換器はユーザー要望でタブから外してある (登録は残置)。
        output.accept(RealTrainModUnofficialItems.MARKER_ITEM.get());
        output.accept(RealTrainModUnofficialItems.MARKER_SWITCH_ITEM.get());
        // マーカー(斜め)/分岐マーカー(斜め) はユーザー要望で削除
        // (本家は通常マーカーが8方位対応のため斜めバリアント不要)

        // --- RTMItem.init() の登録順 ---
        // 本家 bogie (台車) は RTMU に無い。
        // 本家 installedObject は 1 アイテムで、クリエイティブには
        // ItemInstalledObject.IstlObjType の宣言順にサブアイテムが並ぶ。
        // RTMU はこれを設置物ごとの別アイテムに割ってあるので、同じ順で並べる。
        output.accept(RealTrainModUnofficialItems.FLUORESCENT_ITEM.get());    //FLUORESCENT(0)
        // PLANT(1) は RTMU に無い
        output.accept(RealTrainModUnofficialItems.INSULATOR_ITEM.get());      //INSULATOR(3)
        output.accept(RealTrainModUnofficialItems.PIPE_ITEM.get());           //PIPE(4)
        output.accept(RealTrainModUnofficialItems.CROSSING_GATE_ITEM.get());  //CROSSING(5)
        // CONNECTOR_IN(8)/CONNECTOR_OUT(9) はユーザー要望で削除 (登録は残置)
        output.accept(RealTrainModUnofficialItems.ATC_ITEM.get());            //ATC(10)
        output.accept(RealTrainModUnofficialItems.TRAIN_DETECTOR_ITEM.get()); //TRAIN_DETECTOR(11)
        output.accept(RealTrainModUnofficialItems.TICKET_GATE_ITEM.get());    //TURNSTILE(12) = 改札機
        output.accept(RealTrainModUnofficialItems.BUMPING_POST_ITEM.get());   //BUMPING_POST(13)
        // LINEPOLE(14) は本家に itemLinePole という専用アイテムもある。
        // RTMU は 1 つしか無いので、専用アイテム側 (下の itemLinePole の位置) に出す。
        output.accept(RealTrainModUnofficialItems.POINT_MACHINE_ITEM.get());  //POINT(16)
        output.accept(RealTrainModUnofficialItems.SIGNBOARD_ITEM.get());      //SIGNBOARD(17)
        output.accept(RealTrainModUnofficialItems.TICKET_VENDOR_ITEM.get());  //TICKET_VENDOR(18)
        output.accept(RealTrainModUnofficialItems.LIGHT_ITEM.get());          //LIGHT(19)
        // FLAG(20)/STAIR(21)/SCAFFOLD(22) は RTMU に無い
        output.accept(RealTrainModUnofficialItems.SPEAKER_ITEM.get());        //SPEAKER(23)

        // 本家 material (部品) は RTMU に無い
        output.accept(RealTrainModUnofficialItems.TRAIN_ITEM.get());          //itemtrain
        // 本家 itemMotorman (運転士): 列車に使うと乗車して信号/ダイヤ/マクロで自動運転
        output.accept(RealTrainModUnofficialItems.MOTORMAN_ITEM.get());       //itemMotorman
        // 本家 itemCargo (コンテナ) は RTMU に無い
        output.accept(RealTrainModUnofficialItems.RAILROAD_SIGN_ITEM.get());  //itemRailroadSign
        output.accept(RealTrainModUnofficialItems.TICKET_ITEM.get());         //ticket
        output.accept(RealTrainModUnofficialItems.TICKET_BOOK_ITEM.get());    //ticketBook
        output.accept(RealTrainModUnofficialItems.IC_CARD_ITEM.get());        //icCard
        output.accept(RealTrainModUnofficialItems.RAIL_ITEM.get());           //itemLargeRail
        acceptRailVariants(output);                                           //本家 ItemRail.getSubItems 相当
        output.accept(RealTrainModUnofficialItems.SIGNAL_ITEM.get());         //itemSignal
        // 架線柱: 本家モデル (LinePole01/02/Frame01/SignalPole01) を同梱したので再追加。
        output.accept(RealTrainModUnofficialItems.OVERHEAD_LINE_POLE_ITEM.get()); //itemLinePole
        output.accept(RealTrainModUnofficialItems.CAR_ITEM.get());            //itemVehicle
        output.accept(RealTrainModUnofficialItems.WIRE_ITEM.get());           //itemWire

        // ===== 本家 tabIndustry =====
        // 製鉄設備・足場・鋼材は RTMU に無い (itemPipe は上の PIPE と同じ物なので出さない)

        // ===== 本家 tabRTMTools =====
        output.accept(RealTrainModUnofficialItems.CROWBAR_ITEM.get());        //crowbar
        // 本家 money は RTMU に無い
        output.accept(RealTrainModUnofficialItems.WRENCH_ITEM.get());         //wrench
        // 銃器・金鋸・櫂・ふいご は RTMU に無い
        output.accept(RealTrainModUnofficialItems.CAMERA_ITEM.get());         //camera

        // ★SignalControllerMod (masa300) 移植は専用タブ (SIGNAL_CONTROLLER_TAB) へ移した。
        // 位置設定ツール 2 つと必ずセットで使うので、まとめて 1 タブにしてある (ユーザー指定)。
    }

    /**
     * RTMU 独自タブ。本家 RTM に無いものだけ。
     * 本家にある物と混ざると「どれが本家の挙動か」が分からなくなるので分けてある。
     */
    private static void acceptRtmuTab(net.minecraft.world.item.CreativeModeTab.Output output) {
        // neo mcte: タブに出すのは Neo Editor だけ。
        // ミニチュアは MCTEU のものを使うため、ペインターは Neo Editor と役割が重なるため外した。
        // ★アイテムの登録自体は残してある (機能は消さない)。
        output.accept(RealTrainModUnofficialItems.EDITOR_ITEM.get());
        // 編成アイテム: 複数両をまとめて連結設置する
        output.accept(RealTrainModUnofficialItems.FORMATION_ITEM.get());
        // 編成バール: 殴った車両の編成をまるごと消す
        output.accept(RealTrainModUnofficialItems.FORMATION_CROWBAR_ITEM.get());
        // リモコン: ブロック2つを無線レッドストーンでペアリング
        output.accept(RealTrainModUnofficialItems.REMOTE_ITEM.get());
        // 乗客シミュレーション (統合): 駅ブロック / 停止位置目標
        output.accept(com.portofino.rtmupassenger.PassengerMod.STATION_ITEM.get());
        output.accept(com.portofino.rtmupassenger.PassengerMod.STOP_TARGET_ITEM.get());
        // 背景パネル: 模型やジオラマの背景に写真を立てる
        output.accept(RealTrainModUnofficialItems.BACKGROUND_PANEL_ITEM.get());
    }

    /**
     * mods フォルダの 1.7.10 建材 mod からかき集めたブロックを全部並べる専用タブ。
     * 中身は com.portofino.realtrainmodunofficial.building.ExternalBuildingBlocks#TAB_ITEMS
     * (コンストラクタの init で構築)。空でもタブ自体は出す (レンガアイコン)。
     */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> EXTERNAL_BUILDING_TAB =
        CREATIVE_MODE_TABS.register("external_building_tab", () -> CreativeModeTab.builder()
            .title(Component.literal("外部建材 (1.7.10)"))
            .withTabsAfter(SIGNAL_CONTROLLER_TAB.getKey())
            .icon(() -> com.portofino.realtrainmodunofficial.building.ExternalBuildingBlocks.TAB_ITEMS.isEmpty()
                ? new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BRICKS)
                : com.portofino.realtrainmodunofficial.building.ExternalBuildingBlocks.TAB_ITEMS.get(0).get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                for (var item : com.portofino.realtrainmodunofficial.building.ExternalBuildingBlocks.TAB_ITEMS) {
                    output.accept(item.get());
                }
            }).build());

    public RealTrainModUnofficial(IEventBus modEventBus, ModContainer modContainer, Dist dist) {
        // どのビルドが動いているかの唯一の証拠 (バージョン番号は再利用しているため)。
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
        // 本家 RTM のチャンクローダー (列車の State_ChunkLoader) 用チケットコントローラ
        modEventBus.addListener((net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent event) ->
            event.register(com.portofino.realtrainmodunofficial.world.TrainChunkLoader.CONTROLLER));

        RealTrainModUnofficialBlocks.BLOCKS.register(modEventBus);
        RealTrainModUnofficialMenus.MENUS.register(modEventBus);
        // mods フォルダの 1.7.10 建材 mod をスキャンし、ブロックテクスチャをフルキューブブロックとして
        // 登録する (レジストリ凍結前に走らせる必要があるのでここで呼ぶ)。
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
        // 乗客シミュレーション (旧・別 jar rtmupassenger を統合)。本体の名前空間で登録する。
        com.portofino.rtmupassenger.PassengerMod.register(modEventBus);
        // WebCTC は別 mod (RTMU-WebCTC_1.21.1, webctc サブプロジェクト) へ分離した。
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
            // 本家同様、前提パックが足りなければここで落とす (黙って崩れた見た目で動かさない)。
            // 全パックのロード後に検証する — スクリプトは全パック横断の索引まで見て解決するため。
            com.portofino.realtrainmodunofficial.pack.PackPrerequisiteCheck.verify();
            com.portofino.realtrainmodunofficial.script.TrainScriptSystem.getInstance().initialize();
        });
    }

    private void registerNetwork(RegisterPayloadHandlersEvent event) {
        com.portofino.realtrainmodunofficial.network.RealTrainModUnofficialNetwork.registerPayloadHandlers(event);
    }


    /**
     * レールを<b>種類ごと</b>にクリエイティブへ並べる (本家 ItemRail.getSubItems と同じ)。
     *
     * <p>★出すのは通常の {@code RAIL_ITEM}。
     * {@code jp.ngt.rtm.item.ItemRail} (ITEM_LARGE_RAIL) は<b>コピー&ペースト用の別アイテム</b>で、
     * こちらで並べると「コピーしたレール」が大量に出てしまう。
     *
     * <p>道床 (砂利・土など) をアイテムごとに選ぶ本家の並びは、
     * まだ選択を持ち回る仕組みが無いので種類だけにしてある。
     */
    private static void acceptRailVariants(net.minecraft.world.item.CreativeModeTab.Output output) {
        try {
            for (com.portofino.realtrainmodunofficial.rail.RailDefinition def
                    : com.portofino.realtrainmodunofficial.rail.RailRegistry.getAll()) {
                if (def.getId() == null || def.getId().isBlank()) {
                    continue;
                }
                //本家 ItemRail.getSubItems と同じ: defaultBallast に書いてある数だけ出す。
                //  1067mm_Wood なら [gravel, snow] の 2 つ、桁レールなら [air] の 1 つ。
                //  ★defaultBallast を持たないレール (Advanced Rails 等) は本家ではタブに出ない。
                //   RTMU は出すが、道床は付けない (null = レール定義に無い = 空気)。
                java.util.List<com.portofino.realtrainmodunofficial.rail.RailDefinition.Ballast> sets =
                    def.getBallastSets();
                if (sets.isEmpty()) {
                    output.accept(railStack(def.getId(), null));
                    continue;
                }
                for (com.portofino.realtrainmodunofficial.rail.RailDefinition.Ballast set : sets) {
                    output.accept(railStack(def.getId(), set.blockId()));
                }
            }
        } catch (Throwable t) {
            //パックが読めていない段階でも、素のレールアイテムだけは出るようにする
            LOGGER.warn("レールの種類別アイテムを並べられませんでした", t);
        }
    }

    /** 種類と道床を焼き込んだレールアイテム 1 つ。 */
    private static net.minecraft.world.item.ItemStack railStack(String railId, String ballastId) {
        net.minecraft.world.item.ItemStack stack =
            new net.minecraft.world.item.ItemStack(RealTrainModUnofficialItems.RAIL_ITEM.get());
        stack.set(RealTrainModUnofficialComponents.SELECTED_MODEL_ID.get(), railId);
        if (ballastId != null && !ballastId.isBlank()) {
            stack.set(RealTrainModUnofficialComponents.SELECTED_BALLAST.get(), ballastId);
        }
        return stack;
    }

}
