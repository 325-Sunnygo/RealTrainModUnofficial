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
    //   受信機(signal_receiver) / 受信機シグナル値(signal_value_receiver) / 電車検知ブロック(train_detector)
    //   状態ブロック(signal_state) / スクリプトブロック(script_block) / 通信機(signal_communicator)
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
    public static final DeferredItem<TrainItem> TRAIN_ITEM = ITEMS.register(
        "train", () -> new TrainItem(TrainItem.Category.ELECTRIC)
    );
    // 試験用車両(test_train)はユーザー要望により削除。
    public static final DeferredItem<TrainVehicleItem> TRAIN_VEHICLE_ITEM = ITEMS.register(
        "train_vehicle", TrainVehicleItem::new
    );
    public static final DeferredItem<CarItem> CAR_ITEM = ITEMS.register(
        "car", CarItem::new
    );
    //本家 itemMotorman (運転士)。列車に使うと運転台に乗り、信号/ダイヤ/マクロで自動運転する
    public static final DeferredItem<com.portofino.realtrainmodunofficial.item.MotormanItem> MOTORMAN_ITEM = ITEMS.register(
        "motorman", com.portofino.realtrainmodunofficial.item.MotormanItem::new
    );
    public static final DeferredItem<IcCardItem> IC_CARD_ITEM = ITEMS.register(
        "ic_card", IcCardItem::new
    );
    //MCTE 互換ミニチュア (最低限: ブロック範囲キャプチャ。NGTO Builder が使用)
    public static final DeferredItem<MiniatureItem> MINIATURE_ITEM = ITEMS.register(
        "miniature", MiniatureItem::new
    );
    public static final DeferredItem<CrowbarItem> CROWBAR_ITEM = ITEMS.register(
        "crowbar", CrowbarItem::new
    );
    //本家 ItemWrench 忠実移植版 (旧 WrenchItem はレガシー系の参照維持のため残置)
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
    // レールの上に置くと、真下のレールに列車が乗っているかを見る。検知したら
    // 指定座標のレッドストーンブロックを置く/消す (座標と動作は右クリックの GUI で設定)。
    // ※旧「電車検知ブロック(TrainDetectorBlock)」とは別物。あちらは範囲内の列車を
    //   AABB で探す独自実装で、アイテムは削除済み。
    public static final DeferredItem<InstalledObjectItem> TRAIN_DETECTOR_ITEM = ITEMS.register(
        "train_detector", () -> new InstalledObjectItem(InstalledObjectCategory.TRAIN_DETECTOR)
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
    //本家 electric: 入力/出力コネクタ (配線網⇔レッドストーン)
    public static final DeferredItem<InstalledObjectItem> CONNECTOR_INPUT_ITEM = ITEMS.register(
        "connector_input", () -> new InstalledObjectItem(InstalledObjectCategory.CONNECTOR_INPUT)
    );
    public static final DeferredItem<InstalledObjectItem> CONNECTOR_OUTPUT_ITEM = ITEMS.register(
        "connector_output", () -> new InstalledObjectItem(InstalledObjectCategory.CONNECTOR_OUTPUT)
    );
    //本家 electric: 信号変換器
    public static final DeferredItem<net.minecraft.world.item.BlockItem> SIGNAL_CONVERTER_ITEM = ITEMS.register(
        "signal_converter", () -> new net.minecraft.world.item.BlockItem(RealTrainModUnofficialBlocks.SIGNAL_CONVERTER.get(), new net.minecraft.world.item.Item.Properties())
    );

    // ---- 本家 ItemInstalledObject のうち未移植だった設置物 ----
    //本家 installed_object meta 0: ガラスの蛍光灯 (BlockFluorescent)。置くだけで光源 15。
    public static final DeferredItem<InstalledObjectItem> FLUORESCENT_ITEM = ITEMS.register(
        "fluorescent", () -> new InstalledObjectItem(InstalledObjectCategory.FLUORESCENT)
    );
    //本家 installed_object meta 6: 標識 (BlockRailroadSign)。モデルでなくテクスチャを選ぶ。
    public static final DeferredItem<InstalledObjectItem> RAILROAD_SIGN_ITEM = ITEMS.register(
        "railroad_sign", () -> new InstalledObjectItem(InstalledObjectCategory.RAILROAD_SIGN)
    );
    //本家 installed_object meta 13: 車止め (EntityBumpingPost)。レールに吸着し列車を止める。
    public static final DeferredItem<InstalledObjectItem> BUMPING_POST_ITEM = ITEMS.register(
        "bumping_post", () -> new InstalledObjectItem(InstalledObjectCategory.BUMPING_POST)
    );
    //本家 installed_object meta 16: 転轍機 (BlockPoint)。右クリックで切り替わるレッドストーン源。
    public static final DeferredItem<InstalledObjectItem> POINT_MACHINE_ITEM = ITEMS.register(
        "point_machine", () -> new InstalledObjectItem(InstalledObjectCategory.POINT)
    );
    //本家 installed_object meta 18: 券売機 (BlockTicketVendor)。切符/回数券を発券する。
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
            case INSULATOR -> INSULATOR_ITEM.get();
            case SIGNAL -> SIGNAL_ITEM.get();
            case TRAIN_DETECTOR -> TRAIN_DETECTOR_ITEM.get();
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
    //本家 ItemCamera: 撮り鉄用カメラ (望遠 / 被写界深度 / 流し撮り / 列車追尾AF / レンズ交換式)
    public static final DeferredItem<com.portofino.realtrainmodunofficial.item.CameraItem> CAMERA_ITEM = ITEMS.register(
        "camera", com.portofino.realtrainmodunofficial.item.CameraItem::new
    );
    // ---- 交換レンズ (カメラ構え中に L キーで装着) ----
    public static final DeferredItem<com.portofino.realtrainmodunofficial.item.LensItem> LENS_WIDE = ITEMS.register(
        "lens_wide", () -> new com.portofino.realtrainmodunofficial.item.LensItem(com.portofino.realtrainmodunofficial.client.camera.CameraLens.WIDE)
    );
    public static final DeferredItem<com.portofino.realtrainmodunofficial.item.LensItem> LENS_STANDARD = ITEMS.register(
        "lens_standard", () -> new com.portofino.realtrainmodunofficial.item.LensItem(com.portofino.realtrainmodunofficial.client.camera.CameraLens.STANDARD)
    );
    public static final DeferredItem<com.portofino.realtrainmodunofficial.item.LensItem> LENS_TELE = ITEMS.register(
        "lens_tele", () -> new com.portofino.realtrainmodunofficial.item.LensItem(com.portofino.realtrainmodunofficial.client.camera.CameraLens.TELE)
    );
    public static final DeferredItem<com.portofino.realtrainmodunofficial.item.LensItem> LENS_SUPER_TELE = ITEMS.register(
        "lens_super_tele", () -> new com.portofino.realtrainmodunofficial.item.LensItem(com.portofino.realtrainmodunofficial.client.camera.CameraLens.SUPER_TELE)
    );
    public static final DeferredItem<com.portofino.realtrainmodunofficial.item.LensItem> LENS_SANNI = ITEMS.register(
        "lens_sanni", () -> new com.portofino.realtrainmodunofficial.item.LensItem(com.portofino.realtrainmodunofficial.client.camera.CameraLens.SANNI)
    );
    public static final DeferredItem<com.portofino.realtrainmodunofficial.item.LensItem> LENS_NIFTY = ITEMS.register(
        "lens_nifty", () -> new com.portofino.realtrainmodunofficial.item.LensItem(com.portofino.realtrainmodunofficial.client.camera.CameraLens.NIFTY)
    );
    // ---- テレコンバーター (カメラ構え中に ; キーで着脱) ----
    public static final DeferredItem<com.portofino.realtrainmodunofficial.item.TeleconverterItem> TELECONVERTER_14 = ITEMS.register(
        "teleconverter_14", () -> new com.portofino.realtrainmodunofficial.item.TeleconverterItem(com.portofino.realtrainmodunofficial.client.camera.Teleconverter.TC14)
    );
    public static final DeferredItem<com.portofino.realtrainmodunofficial.item.TeleconverterItem> TELECONVERTER_20 = ITEMS.register(
        "teleconverter_20", () -> new com.portofino.realtrainmodunofficial.item.TeleconverterItem(com.portofino.realtrainmodunofficial.client.camera.Teleconverter.TC20)
    );
    //リモコン: 2つのブロックを無線レッドストーンでペアリング (シフト+右クリック)
    public static final DeferredItem<com.portofino.realtrainmodunofficial.item.RemoteItem> REMOTE_ITEM = ITEMS.register(
        "remote", com.portofino.realtrainmodunofficial.item.RemoteItem::new
    );
    //本家 ItemTicket: 券売機が発券し改札が消費する。切符=1回, 回数券=11回。
    public static final DeferredItem<com.portofino.realtrainmodunofficial.item.TicketItem> TICKET_ITEM = ITEMS.register(
        "ticket", () -> new com.portofino.realtrainmodunofficial.item.TicketItem(1)
    );
    public static final DeferredItem<com.portofino.realtrainmodunofficial.item.TicketItem> TICKET_BOOK_ITEM = ITEMS.register(
        "ticket_book", () -> new com.portofino.realtrainmodunofficial.item.TicketItem(11)
    );

    //SignalControllerMod (masa300) 移植: 信号制御器 + 位置設定ツール×2
    public static final DeferredItem<net.minecraft.world.item.BlockItem> SIGNAL_CONTROLLER_ITEM = ITEMS.register(
        "signal_controller", () -> new net.minecraft.world.item.BlockItem(RealTrainModUnofficialBlocks.SIGNAL_CONTROLLER.get(), new net.minecraft.world.item.Item.Properties())
    );
    public static final DeferredItem<jp.masa.signalcontrollermod.ItemPosSettingTool> POS_SETTING_TOOL_0 = ITEMS.register(
        "pos_setting_tool_0", () -> new jp.masa.signalcontrollermod.ItemPosSettingTool(0)
    );
    public static final DeferredItem<jp.masa.signalcontrollermod.ItemPosSettingTool> POS_SETTING_TOOL_1 = ITEMS.register(
        "pos_setting_tool_1", () -> new jp.masa.signalcontrollermod.ItemPosSettingTool(1)
    );
}
