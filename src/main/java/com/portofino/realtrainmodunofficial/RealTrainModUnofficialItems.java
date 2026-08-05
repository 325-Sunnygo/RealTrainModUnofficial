package com.portofino.realtrainmodunofficial;

import com.portofino.realtrainmodunofficial.item.CarItem;
import com.portofino.realtrainmodunofficial.item.CrowbarItem;
import com.portofino.realtrainmodunofficial.item.IcCardItem;
import com.portofino.realtrainmodunofficial.item.MiniatureItem;
import com.portofino.realtrainmodunofficial.item.MarkerItem;
import com.portofino.realtrainmodunofficial.item.RailItem;
import com.portofino.realtrainmodunofficial.item.TrainItem;
import com.portofino.realtrainmodunofficial.item.InstalledObjectItem;
import com.portofino.realtrainmodunofficial.item.TrainVehicleItem;
import com.portofino.realtrainmodunofficial.item.WireItem;
import com.portofino.realtrainmodunofficial.item.WrenchItem;
import com.portofino.realtrainmodunofficial.item.RtmWrenchItem;
import com.portofino.realtrainmodunofficial.installedobject.InstalledObjectCategory;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class RealTrainModUnofficialItems {

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(RealTrainModUnofficial.MODID);

    public static final DeferredItem<InstalledObjectItem> CROSSING_GATE_ITEM = ITEMS.register(
        "crossing_gate", () -> new InstalledObjectItem(InstalledObjectCategory.CROSSING)
    );
    // 以下のアイテムはユーザー要望により削除:
    // 受信機(signal_receiver) / 受信機シグナル値(signal_value_receiver) / 電車検知ブロック(train_detector)
    // 状態ブロック(signal_state) / スクリプトブロック(script_block) / 通信機(signal_communicator)
    // 道床(ballast)アイテムも廃止済み。ブロック自体は残るがアイテム(入手手段)は登録しない。
    public static final DeferredItem<MarkerItem> MARKER_ITEM = ITEMS.register(
        "marker", () -> new MarkerItem(jp.ngt.rtm.rail.RTMRailBlocks.MARKER.get(), false)
    );
    public static final DeferredItem<MarkerItem> MARKER_DIAGONAL_ITEM = ITEMS.register(
        "marker_diagonal", () -> new MarkerItem(jp.ngt.rtm.rail.RTMRailBlocks.MARKER.get(), true)
    );
    public static final DeferredItem<MarkerItem> MARKER_SWITCH_ITEM = ITEMS.register(
        "marker_switch", () -> new MarkerItem(jp.ngt.rtm.rail.RTMRailBlocks.MARKER_SWITCH.get(), false)
    );
    public static final DeferredItem<MarkerItem> MARKER_SWITCH_DIAGONAL_ITEM = ITEMS.register(
        "marker_switch_diagonal", () -> new MarkerItem(jp.ngt.rtm.rail.RTMRailBlocks.MARKER_SWITCH.get(), true)
    );
    public static final DeferredItem<RailItem> RAIL_ITEM = ITEMS.register(
        "rail", RailItem::new
    );
    /**
     * 本家 ItemTrain のメタ 0-3/127 を、1.21 ではアイテム 5 つに分ける。
     * ★{@code train} (電車) の ID は変えないこと。変えると既存ワールドの持ち物が消える。
     */
    public static final DeferredItem<TrainItem> TRAIN_DIESEL_ITEM = ITEMS.register(
        "diesel_train", () -> new TrainItem(TrainItem.Category.DIESEL)
    );
    public static final DeferredItem<TrainItem> TRAIN_FREIGHT_ITEM = ITEMS.register(
        "freight_train", () -> new TrainItem(TrainItem.Category.FREIGHT)
    );
    public static final DeferredItem<TrainItem> TRAIN_TANKER_ITEM = ITEMS.register(
        "tanker_train", () -> new TrainItem(TrainItem.Category.TANKER)
    );
    public static final DeferredItem<TrainItem> TRAIN_TEST_ITEM = ITEMS.register(
        "test_train", () -> new TrainItem(TrainItem.Category.TEST)
    );
    public static final DeferredItem<TrainItem> TRAIN_ITEM = ITEMS.register(
        "train", () -> new TrainItem(TrainItem.Category.ELECTRIC)
    );
    // 試験用車両(test_train)は本家 ItemTrain のメタ127として復活 (ユーザー確認済み)。
    public static final DeferredItem<TrainVehicleItem> TRAIN_VEHICLE_ITEM = ITEMS.register(
        "train_vehicle", TrainVehicleItem::new
    );
    public static final DeferredItem<CarItem> CAR_ITEM = ITEMS.register(
        "car", CarItem::new
    );
    /**
     * 編成アイテム。右クリックで編成を組み、線路へ右クリックで連結した状態のまま設置する。
     * 1 両ずつ置く TRAIN_ITEM とは別物 (あちらはそのまま)。
     */
    public static final DeferredItem<com.portofino.realtrainmodunofficial.item.FormationItem> FORMATION_ITEM =
        ITEMS.register("formation", com.portofino.realtrainmodunofficial.item.FormationItem::new);

    /** 編成バール。殴った車両の編成をまるごと消す (1 両ずつ壊す CROWBAR_ITEM とは別)。 */
    public static final DeferredItem<com.portofino.realtrainmodunofficial.item.FormationCrowbarItem> FORMATION_CROWBAR_ITEM =
        ITEMS.register("formation_crowbar", com.portofino.realtrainmodunofficial.item.FormationCrowbarItem::new);
    // 本家 itemMotorman (運転士)。列車に使うと運転台に乗り、信号/ダイヤ/マクロで自動運転する
    public static final DeferredItem<com.portofino.realtrainmodunofficial.item.MotormanItem> MOTORMAN_ITEM = ITEMS.register(
        "motorman", com.portofino.realtrainmodunofficial.item.MotormanItem::new
    );
    public static final DeferredItem<IcCardItem> IC_CARD_ITEM = ITEMS.register(
        "ic_card", IcCardItem::new
    );
    // MCTE 互換ミニチュア (最低限: ブロック範囲キャプチャ。NGTO Builder が使用)
    /** ペインター (neo mcte)。 */
    public static final DeferredItem<com.portofino.realtrainmodunofficial.item.PainterItem> PAINTER_ITEM = ITEMS.register(
        "painter", com.portofino.realtrainmodunofficial.item.PainterItem::new
    );

    /** エディタ (neo mcte)。 */
    public static final DeferredItem<com.portofino.realtrainmodunofficial.item.EditorItem> EDITOR_ITEM = ITEMS.register(
        "editor", com.portofino.realtrainmodunofficial.item.EditorItem::new
    );

    public static final DeferredItem<MiniatureItem> MINIATURE_ITEM = ITEMS.register(
        "miniature", MiniatureItem::new
    );
    /** 背景パネル。設定 (画像・大きさ) はブロックエンティティが持つ。 */
    public static final DeferredItem<net.minecraft.world.item.BlockItem> BACKGROUND_PANEL_ITEM = ITEMS.register(
        "background_panel", () -> new net.minecraft.world.item.BlockItem(
            RealTrainModUnofficialBlocks.BACKGROUND_PANEL.get(), new net.minecraft.world.item.Item.Properties())
    );
    public static final DeferredItem<CrowbarItem> CROWBAR_ITEM = ITEMS.register(
        "crowbar", CrowbarItem::new
    );
    // 本家 ItemWrench 忠実移植版 (旧 WrenchItem はレガシー系の参照維持のため残置)
    public static final DeferredItem<RtmWrenchItem> WRENCH_ITEM = ITEMS.register(
        "wrench", RtmWrenchItem::new
    );
    public static final DeferredItem<WireItem> WIRE_ITEM = ITEMS.register(
        "wire", WireItem::new
    );
    // 照明(light): 本家RTM の照明アイテム。外部パックのモデルを使用し、レッドストーン
    // 信号を受けると点灯する(InstalledObjectBlock 側で発光処理)。
    public static final DeferredItem<InstalledObjectItem> LIGHT_ITEM = ITEMS.register(
        "light", () -> new InstalledObjectItem(InstalledObjectCategory.LIGHT)
    );
    // 看板(signboard): 本家RTM の看板アイテム。パックの SignBoard_*.json からテクスチャと
    // 板のサイズ(width/height/depth)を読み、文字(SignboardText)を貼り付けられる。
    public static final DeferredItem<InstalledObjectItem> SIGNBOARD_ITEM = ITEMS.register(
        "signboard", () -> new InstalledObjectItem(InstalledObjectCategory.SIGNBOARD)
    );
    public static final DeferredItem<InstalledObjectItem> PIPE_ITEM = ITEMS.register(
        "pipe", () -> new InstalledObjectItem(InstalledObjectCategory.PIPE)
    );
    public static final DeferredItem<InstalledObjectItem> INSULATOR_ITEM = ITEMS.register(
        "insulator", () -> new InstalledObjectItem(InstalledObjectCategory.INSULATOR)
    );
    public static final DeferredItem<InstalledObjectItem> SIGNAL_ITEM = ITEMS.register(
        "signal", () -> new InstalledObjectItem(InstalledObjectCategory.SIGNAL)
    );
    // 列車検知器(train_detector): 本家 RTM の列車検知器 (EntityTrainDetector)。
    // レールの上に置くと、真下のレールに列車が乗っているかを見る。
    public static final DeferredItem<InstalledObjectItem> TRAIN_DETECTOR_ITEM = ITEMS.register(
        "train_detector", () -> new InstalledObjectItem(InstalledObjectCategory.TRAIN_DETECTOR)
    );
    public static final DeferredItem<InstalledObjectItem> ATC_ITEM = ITEMS.register(
        "atc", () -> new InstalledObjectItem(InstalledObjectCategory.ATC)
    );
    public static final DeferredItem<InstalledObjectItem> OVERHEAD_LINE_POLE_ITEM = ITEMS.register(
        "overhead_line_pole", () -> new InstalledObjectItem(InstalledObjectCategory.OVERHEAD_LINE_POLE)
    );
    public static final DeferredItem<InstalledObjectItem> TICKET_GATE_ITEM = ITEMS.register(
        "ticket_gate", () -> new InstalledObjectItem(InstalledObjectCategory.TICKET_GATE)
    );
    public static final DeferredItem<InstalledObjectItem> SPEAKER_ITEM = ITEMS.register(
        "speaker", () -> new InstalledObjectItem(InstalledObjectCategory.SPEAKER)
    );
    // 本家 electric: 入力/出力コネクタ (配線網⇔レッドストーン)
    public static final DeferredItem<InstalledObjectItem> CONNECTOR_INPUT_ITEM = ITEMS.register(
        "connector_input", () -> new InstalledObjectItem(InstalledObjectCategory.CONNECTOR_INPUT)
    );
    public static final DeferredItem<InstalledObjectItem> CONNECTOR_OUTPUT_ITEM = ITEMS.register(
        "connector_output", () -> new InstalledObjectItem(InstalledObjectCategory.CONNECTOR_OUTPUT)
    );
    // 本家 electric: 信号変換器
    /**
     * 本家 signalConverter メタ 0 (RS入力)。★id は既存互換のため signal_converter のまま。
     */
    public static final DeferredItem<com.portofino.realtrainmodunofficial.item.SignalConverterItem> SIGNAL_CONVERTER_ITEM = ITEMS.register(
        "signal_converter", () -> new com.portofino.realtrainmodunofficial.item.SignalConverterItem(0)
    );
    /** 本家 signalConverter メタ 1 (RS出力)。 */
    public static final DeferredItem<com.portofino.realtrainmodunofficial.item.SignalConverterItem> SIGNAL_CONVERTER_RS_ITEM = ITEMS.register(
        "signal_converter_rs", () -> new com.portofino.realtrainmodunofficial.item.SignalConverterItem(1)
    );
    /** 本家 signalConverter メタ 2 (インクリメント)。 */
    public static final DeferredItem<com.portofino.realtrainmodunofficial.item.SignalConverterItem> SIGNAL_CONVERTER_INC_ITEM = ITEMS.register(
        "signal_converter_increment", () -> new com.portofino.realtrainmodunofficial.item.SignalConverterItem(2)
    );
    /** 本家 signalConverter メタ 3 (デクリメント)。 */
    public static final DeferredItem<com.portofino.realtrainmodunofficial.item.SignalConverterItem> SIGNAL_CONVERTER_DEC_ITEM = ITEMS.register(
        "signal_converter_decrement", () -> new com.portofino.realtrainmodunofficial.item.SignalConverterItem(3)
    );

    /** 本家 signalConverter メタ 4 (無線)。チャンネル一致の変換機同士で信号を飛ばす。 */
    public static final DeferredItem<com.portofino.realtrainmodunofficial.item.SignalConverterItem> SIGNAL_CONVERTER_WIRELESS_ITEM = ITEMS.register(
        "signal_converter_wireless", () -> new com.portofino.realtrainmodunofficial.item.SignalConverterItem(4)
    );

    // ---- 本家 ItemInstalledObject のうち未移植だった設置物 ----
    // 本家 installed_object meta 0: ガラスの蛍光灯 (BlockFluorescent)。置くだけで光源 15。
    public static final DeferredItem<InstalledObjectItem> FLUORESCENT_ITEM = ITEMS.register(
        "fluorescent", () -> new InstalledObjectItem(InstalledObjectCategory.FLUORESCENT)
    );
    // 本家 installed_object meta 6: 標識 (BlockRailroadSign)。モデルでなくテクスチャを選ぶ。
    public static final DeferredItem<InstalledObjectItem> RAILROAD_SIGN_ITEM = ITEMS.register(
        "railroad_sign", () -> new InstalledObjectItem(InstalledObjectCategory.RAILROAD_SIGN)
    );
    // 本家 installed_object meta 13: 車止め (EntityBumpingPost)。レールに吸着し列車を止める。
    public static final DeferredItem<InstalledObjectItem> BUMPING_POST_ITEM = ITEMS.register(
        "bumping_post", () -> new InstalledObjectItem(InstalledObjectCategory.BUMPING_POST)
    );
    // 本家 installed_object meta 16: 転轍機 (BlockPoint)。右クリックで切り替わるレッドストーン源。
    public static final DeferredItem<InstalledObjectItem> POINT_MACHINE_ITEM = ITEMS.register(
        "point_machine", () -> new InstalledObjectItem(InstalledObjectCategory.POINT)
    );
    // 本家 installed_object meta 18: 券売機 (BlockTicketVendor)。切符/回数券を発券する。
    public static final DeferredItem<InstalledObjectItem> TICKET_VENDOR_ITEM = ITEMS.register(
        "ticket_vendor", () -> new InstalledObjectItem(InstalledObjectCategory.TICKET_VENDOR)
    );

    /**
     * カテゴリに対応する設置物アイテム。中ボタン(ピックブロック)で設置済みの照明/信号/碍子/架線柱などを
     * コピーする用。WIRE 等 InstalledObjectItem を持たないカテゴリは null。
     */
    public static net.minecraft.world.item.Item getInstalledObjectItem(InstalledObjectCategory category) {
        if (category == null) {
            return null;
        }
        return switch (category) {
            case CROSSING -> CROSSING_GATE_ITEM.get();
            case LIGHT -> LIGHT_ITEM.get();
            case SIGNBOARD -> SIGNBOARD_ITEM.get();
            case PIPE -> PIPE_ITEM.get();
            case PLANT -> PLANT_ITEM.get();
            case STAIR -> STAIR_ITEM.get();
            case SCAFFOLD -> SCAFFOLD_ITEM.get();
            case FLAG -> FLAG_ITEM.get();
            case MECHANISM -> MECHANISM_ITEM.get();
            case INSULATOR -> INSULATOR_ITEM.get();
            case SIGNAL -> SIGNAL_ITEM.get();
            case TRAIN_DETECTOR -> TRAIN_DETECTOR_ITEM.get();
            case ATC -> ATC_ITEM.get();
            case OVERHEAD_LINE_POLE -> OVERHEAD_LINE_POLE_ITEM.get();
            case TICKET_GATE -> TICKET_GATE_ITEM.get();
            case SPEAKER -> SPEAKER_ITEM.get();
            case CONNECTOR_INPUT -> CONNECTOR_INPUT_ITEM.get();
            case CONNECTOR_OUTPUT -> CONNECTOR_OUTPUT_ITEM.get();
            case FLUORESCENT -> FLUORESCENT_ITEM.get();
            case RAILROAD_SIGN -> RAILROAD_SIGN_ITEM.get();
            case BUMPING_POST -> BUMPING_POST_ITEM.get();
            case POINT -> POINT_MACHINE_ITEM.get();
            case TICKET_VENDOR -> TICKET_VENDOR_ITEM.get();
            default -> null;
        };
    }
    // 本家 ItemCamera: 撮り鉄用カメラ (望遠 / 被写界深度 / 流し撮り / 列車追尾AF)
    public static final DeferredItem<com.portofino.realtrainmodunofficial.item.CameraItem> CAMERA_ITEM = ITEMS.register(
        "camera", com.portofino.realtrainmodunofficial.item.CameraItem::new
    );
    // リモコン: 2つのブロックを無線レッドストーンでペアリング (シフト+右クリック)
    public static final DeferredItem<com.portofino.realtrainmodunofficial.item.RemoteItem> REMOTE_ITEM = ITEMS.register(
        "remote", com.portofino.realtrainmodunofficial.item.RemoteItem::new
    );
    // 本家 ItemTicket: 券売機が発券し改札が消費する。切符=1回, 回数券=11回。
    public static final DeferredItem<com.portofino.realtrainmodunofficial.item.TicketItem> TICKET_ITEM = ITEMS.register(
        "ticket", () -> new com.portofino.realtrainmodunofficial.item.TicketItem(1)
    );
    public static final DeferredItem<com.portofino.realtrainmodunofficial.item.TicketItem> TICKET_BOOK_ITEM = ITEMS.register(
        "ticket_book", () -> new com.portofino.realtrainmodunofficial.item.TicketItem(11)
    );

    // SignalControllerMod (masa300) 移植: 信号制御器 + 位置設定ツール×2
    public static final DeferredItem<net.minecraft.world.item.BlockItem> SIGNAL_CONTROLLER_ITEM = ITEMS.register(
        "signal_controller", () -> new net.minecraft.world.item.BlockItem(RealTrainModUnofficialBlocks.SIGNAL_CONTROLLER.get(), new net.minecraft.world.item.Item.Properties())
    );
    public static final DeferredItem<jp.masa.signalcontrollermod.ItemPosSettingTool> POS_SETTING_TOOL_0 = ITEMS.register(
        "pos_setting_tool_0", () -> new jp.masa.signalcontrollermod.ItemPosSettingTool(0)
    );
    public static final DeferredItem<jp.masa.signalcontrollermod.ItemPosSettingTool> POS_SETTING_TOOL_1 = ITEMS.register(
        "pos_setting_tool_1", () -> new jp.masa.signalcontrollermod.ItemPosSettingTool(1)
    );

    // ───────────────────────────────────────────────────────────────
    // 本家 RTM_道具 タブのアイテム (RTMItem.init と同じ並び)
    // ───────────────────────────────────────────────────────────────

    /** 本家 nvd: 暗視装置 (頭防具)。 */
    public static final DeferredItem<jp.ngt.rtm.item.ItemNVD> NVD_ITEM = ITEMS.register(
        "nvd", jp.ngt.rtm.item.ItemNVD::new
    );
    public static final DeferredItem<jp.ngt.rtm.item.ItemGun> HANDGUN_ITEM = ITEMS.register(
        "handgun", () -> new jp.ngt.rtm.item.ItemGun(jp.ngt.rtm.item.ItemGun.GunType.handgun)
    );
    public static final DeferredItem<jp.ngt.rtm.item.ItemGun> RIFLE_ITEM = ITEMS.register(
        "rifle", () -> new jp.ngt.rtm.item.ItemGun(jp.ngt.rtm.item.ItemGun.GunType.rifle)
    );
    public static final DeferredItem<jp.ngt.rtm.item.ItemGun> AUTOLOADING_RIFLE_ITEM = ITEMS.register(
        "autoloading_rifle", () -> new jp.ngt.rtm.item.ItemGun(jp.ngt.rtm.item.ItemGun.GunType.autoloading_rifle)
    );
    public static final DeferredItem<jp.ngt.rtm.item.ItemGun> SNIPER_RIFLE_ITEM = ITEMS.register(
        "sniper_rifle", () -> new jp.ngt.rtm.item.ItemGun(jp.ngt.rtm.item.ItemGun.GunType.sniper_rifle)
    );
    public static final DeferredItem<jp.ngt.rtm.item.ItemGun> SMG_ITEM = ITEMS.register(
        "smg", () -> new jp.ngt.rtm.item.ItemGun(jp.ngt.rtm.item.ItemGun.GunType.smg)
    );
    public static final DeferredItem<jp.ngt.rtm.item.ItemGun> AMR_ITEM = ITEMS.register(
        "amr", () -> new jp.ngt.rtm.item.ItemGun(jp.ngt.rtm.item.ItemGun.GunType.amr)
    );
    public static final DeferredItem<jp.ngt.rtm.item.ItemGun> RAZER_GUN_ITEM = ITEMS.register(
        "razer_gun", () -> new jp.ngt.rtm.item.ItemGun(jp.ngt.rtm.item.ItemGun.GunType.razer_gun)
    );
    /**
     * 本家 money: メタ 0〜8 の 9 種。
     * ★1 アイテム + variant の overrides はアイコンの解決が壊れやすいので、
     * material と同じく<b>アイテムを分ける</b> (アイコンは通常のモデル解決)。
     */
    public static final java.util.Map<Integer, DeferredItem<jp.ngt.rtm.item.ItemMoney>> MONEY_ITEMS =
        new java.util.LinkedHashMap<>();
    static {
        for (int i = 0; i <= 8; ++i) {
            final int id = i;
            MONEY_ITEMS.put(i, ITEMS.register("money_" + i, () -> new jp.ngt.rtm.item.ItemMoney(id)));
        }
    }
    public static final DeferredItem<jp.ngt.rtm.item.ItemMagazine> MAGAZINE_HANDGUN_ITEM = ITEMS.register(
        "magazine_handgun", () -> new jp.ngt.rtm.item.ItemMagazine(jp.ngt.rtm.item.ItemGun.GunType.handgun)
    );
    public static final DeferredItem<jp.ngt.rtm.item.ItemMagazine> MAGAZINE_RIFLE_ITEM = ITEMS.register(
        "magazine_rifle", () -> new jp.ngt.rtm.item.ItemMagazine(jp.ngt.rtm.item.ItemGun.GunType.rifle)
    );
    public static final DeferredItem<jp.ngt.rtm.item.ItemMagazine> MAGAZINE_ALR_ITEM = ITEMS.register(
        "magazine_alr", () -> new jp.ngt.rtm.item.ItemMagazine(jp.ngt.rtm.item.ItemGun.GunType.autoloading_rifle)
    );
    public static final DeferredItem<jp.ngt.rtm.item.ItemMagazine> MAGAZINE_SR_ITEM = ITEMS.register(
        "magazine_sr", () -> new jp.ngt.rtm.item.ItemMagazine(jp.ngt.rtm.item.ItemGun.GunType.sniper_rifle)
    );
    public static final DeferredItem<jp.ngt.rtm.item.ItemMagazine> MAGAZINE_SMG_ITEM = ITEMS.register(
        "magazine_smg", () -> new jp.ngt.rtm.item.ItemMagazine(jp.ngt.rtm.item.ItemGun.GunType.smg)
    );
    public static final DeferredItem<jp.ngt.rtm.item.ItemMagazine> MAGAZINE_AMR_ITEM = ITEMS.register(
        "magazine_amr", () -> new jp.ngt.rtm.item.ItemMagazine(jp.ngt.rtm.item.ItemGun.GunType.amr)
    );
    /** 本家 bullet: 弾種 × (弾薬/弾/薬莢) を 1 アイテム + variant で出す。 */
    public static final DeferredItem<jp.ngt.rtm.item.ItemAmmunition> BULLET_ITEM = ITEMS.register(
        "bullet", jp.ngt.rtm.item.ItemAmmunition::new
    );
    // ───────────────────────────────────────────────────────────────
    // 本家 RTM_工業 タブのアイテム (RTMItem.init と同じ並び)
    // ───────────────────────────────────────────────────────────────

    public static final DeferredItem<jp.ngt.rtm.item.ItemBucketLiquid> BUCKET_LIQUID_ITEM = ITEMS.register(
        "bucket_liquid", jp.ngt.rtm.item.ItemBucketLiquid::new
    );
    public static final DeferredItem<jp.ngt.rtm.item.ItemHacksaw> IRON_HACKSAW_ITEM = ITEMS.register(
        "iron_hacksaw", jp.ngt.rtm.item.ItemHacksaw::new
    );
    public static final DeferredItem<jp.ngt.rtm.item.ItemPaddle> PADDLE_ITEM = ITEMS.register(
        "paddle", jp.ngt.rtm.item.ItemPaddle::new
    );
    public static final DeferredItem<jp.ngt.rtm.item.ItemBellows> BELLOWS_ITEM = ITEMS.register(
        "bellows", jp.ngt.rtm.item.ItemBellows::new
    );
    /** 本家 ingot_steel: ただの素材アイテム。 */
    public static final DeferredItem<net.minecraft.world.item.Item> INGOT_STEEL_ITEM = ITEMS.register(
        "ingot_steel", () -> new net.minecraft.world.item.Item(new net.minecraft.world.item.Item.Properties())
    );
    public static final DeferredItem<jp.ngt.rtm.item.ItemCoke> COKE_ITEM = ITEMS.register(
        "coke", jp.ngt.rtm.item.ItemCoke::new
    );
    public static final DeferredItem<net.minecraft.world.item.BlockItem> STEEL_MATERIAL_ITEM = ITEMS.register(
        "steel_material", () -> new net.minecraft.world.item.BlockItem(
            RealTrainModUnofficialBlocks.STEEL_MATERIAL.get(), new net.minecraft.world.item.Item.Properties())
    );
    public static final DeferredItem<net.minecraft.world.item.BlockItem> SLOT_ITEM = ITEMS.register(
        "slot", () -> new net.minecraft.world.item.BlockItem(
            RealTrainModUnofficialBlocks.SLOT.get(), new net.minecraft.world.item.Item.Properties())
    );
    public static final DeferredItem<net.minecraft.world.item.BlockItem> FIRE_BRICK_ITEM = ITEMS.register(
        "fire_brick", () -> new net.minecraft.world.item.BlockItem(
            RealTrainModUnofficialBlocks.FIRE_BRICK.get(), new net.minecraft.world.item.Item.Properties())
    );
    public static final DeferredItem<net.minecraft.world.item.BlockItem> HOT_STOVE_BRICK_ITEM = ITEMS.register(
        "hot_stove_brick", () -> new net.minecraft.world.item.BlockItem(
            RealTrainModUnofficialBlocks.HOT_STOVE_BRICK.get(), new net.minecraft.world.item.Item.Properties())
    );
    /** 本家もタブには出さない (溶けた金属が冷えて生まれるブロック)。 */
    public static final DeferredItem<net.minecraft.world.item.BlockItem> STEEL_SLAB_ITEM = ITEMS.register(
        "steel_slab", () -> new net.minecraft.world.item.BlockItem(
            RealTrainModUnofficialBlocks.STEEL_SLAB.get(), new net.minecraft.world.item.Item.Properties())
    );
    // ───────────────────────────────────────────────────────────────
    // 本家 RTM_鉄道 タブの未移植分 (第1回)
    // ───────────────────────────────────────────────────────────────

    /**
     * 本家 material: メタ 0/1/2/3/4/8 の部品 6 種。
     * ★<b>1 アイテム + variant にしてはいけない</b>。1.21 のレシピの材料は
     * アイテムでしか一致を見ないので、鉄板を要求する所に車軸が入ってしまう。
     */
    public static final java.util.Map<Integer, DeferredItem<jp.ngt.rtm.item.ItemMaterial>> MATERIAL_ITEMS =
        new java.util.LinkedHashMap<>();
    static {
        MATERIAL_ITEMS.put(0, ITEMS.register("material_shaft", () -> new jp.ngt.rtm.item.ItemMaterial(0)));
        MATERIAL_ITEMS.put(1, ITEMS.register("material_wheel", () -> new jp.ngt.rtm.item.ItemMaterial(1)));
        MATERIAL_ITEMS.put(2, ITEMS.register("material_engine", () -> new jp.ngt.rtm.item.ItemMaterial(2)));
        MATERIAL_ITEMS.put(3, ITEMS.register("material_motor", () -> new jp.ngt.rtm.item.ItemMaterial(3)));
        MATERIAL_ITEMS.put(4, ITEMS.register("material_powder", () -> new jp.ngt.rtm.item.ItemMaterial(4)));
        MATERIAL_ITEMS.put(8, ITEMS.register("material_sheet_steel", () -> new jp.ngt.rtm.item.ItemMaterial(8)));
    }
    /** 本家 bogie: 台車 (クラフト材料)。 */
    public static final DeferredItem<jp.ngt.rtm.item.ItemBogie> BOGIE_ITEM = ITEMS.register(
        "bogie", jp.ngt.rtm.item.ItemBogie::new
    );
    /** 本家 iron_pillar: 鉄柱 (登れる)。 */
    public static final DeferredItem<net.minecraft.world.item.BlockItem> IRON_PILLAR_ITEM = ITEMS.register(
        "iron_pillar", () -> new net.minecraft.world.item.BlockItem(
            RealTrainModUnofficialBlocks.IRON_PILLAR.get(), new net.minecraft.world.item.Item.Properties())
    );
    /** 本家 installedObject の PLANT(1)。 */
    public static final DeferredItem<InstalledObjectItem> PLANT_ITEM = ITEMS.register(
        "plant", () -> new InstalledObjectItem(InstalledObjectCategory.PLANT)
    );
    /** 本家 installedObject の STAIR(21)。 */
    public static final DeferredItem<InstalledObjectItem> STAIR_ITEM = ITEMS.register(
        "stair", () -> new InstalledObjectItem(InstalledObjectCategory.STAIR)
    );
    /** 本家 installedObject の SCAFFOLD(22)。 */
    public static final DeferredItem<InstalledObjectItem> SCAFFOLD_ITEM = ITEMS.register(
        "scaffold", () -> new InstalledObjectItem(InstalledObjectCategory.SCAFFOLD)
    );
    /** 本家 installedObject の FLAG(20)。 */
    public static final DeferredItem<InstalledObjectItem> FLAG_ITEM = ITEMS.register(
        "flag", () -> new InstalledObjectItem(InstalledObjectCategory.FLAG)
    );
    /** 本家 installedObject の MECHANISM(24)。 */
    public static final DeferredItem<InstalledObjectItem> MECHANISM_ITEM = ITEMS.register(
        "mechanism", () -> new InstalledObjectItem(InstalledObjectCategory.MECHANISM)
    );
    /** 本家 station_core: 駅コア。 */
    public static final DeferredItem<net.minecraft.world.item.BlockItem> STATION_CORE_ITEM = ITEMS.register(
        "station_core", () -> new net.minecraft.world.item.BlockItem(
            RealTrainModUnofficialBlocks.STATION_CORE.get(), new net.minecraft.world.item.Item.Properties())
    );
    /** 本家 item_cargo: メタ 0=コンテナ / 1=火砲 / 2=貨物用枕木。 */
    public static final DeferredItem<jp.ngt.rtm.item.ItemCargo> ITEM_CARGO = ITEMS.register(
        "item_cargo", jp.ngt.rtm.item.ItemCargo::new
    );
    /** 本家 framework: 鉄骨 (IronFrame01 を置く。本家 tab=null・レシピで入手)。 */
    public static final DeferredItem<com.portofino.realtrainmodunofficial.item.FrameworkItem> FRAMEWORK_ITEM = ITEMS.register(
        "framework", com.portofino.realtrainmodunofficial.item.FrameworkItem::new
    );
    /** 本家 item_decoration: 装飾ブロック (registerItemModel は "decoration")。 */
    public static final DeferredItem<jp.ngt.rtm.item.ItemDecoration> ITEM_DECORATION = ITEMS.register(
        "item_decoration", jp.ngt.rtm.item.ItemDecoration::new
    );
    public static final DeferredItem<net.minecraft.world.item.BlockItem> TRAIN_WORKBENCH_ITEM = ITEMS.register(
        "train_workbench", () -> new net.minecraft.world.item.BlockItem(
            RealTrainModUnofficialBlocks.TRAIN_WORKBENCH.get(), new net.minecraft.world.item.Item.Properties())
    );
    public static final DeferredItem<net.minecraft.world.item.BlockItem> RAIL_WORKBENCH_ITEM = ITEMS.register(
        "rail_workbench", () -> new net.minecraft.world.item.BlockItem(
            RealTrainModUnofficialBlocks.RAIL_WORKBENCH.get(), new net.minecraft.world.item.Item.Properties())
    );
    public static final DeferredItem<net.minecraft.world.item.BlockItem> MOVING_MACHINE_ITEM = ITEMS.register(
        "moving_machine", () -> new net.minecraft.world.item.BlockItem(
            RealTrainModUnofficialBlocks.MOVING_MACHINE.get(), new net.minecraft.world.item.Item.Properties())
    );
    public static final DeferredItem<net.minecraft.world.item.BlockItem> BRICK_SLAB_ITEM = ITEMS.register(
        "brick_slab", () -> new net.minecraft.world.item.BlockItem(
            RealTrainModUnofficialBlocks.BRICK_SLAB.get(), new net.minecraft.world.item.Item.Properties())
    );
    /** 本家 item_motorman メタ 1: モデル付き NPC (運転士はメタ 0 = MOTORMAN_ITEM)。 */
    public static final DeferredItem<com.portofino.realtrainmodunofficial.item.NpcItem> NPC_ITEM = ITEMS.register(
        "npc", com.portofino.realtrainmodunofficial.item.NpcItem::new
    );
    public static final DeferredItem<net.minecraft.world.item.BlockItem> VEHICLE_GENERATOR_ITEM = ITEMS.register(
        "vehicle_generator", () -> new net.minecraft.world.item.BlockItem(
            RealTrainModUnofficialBlocks.VEHICLE_GENERATOR.get(), new net.minecraft.world.item.Item.Properties())
    );
}