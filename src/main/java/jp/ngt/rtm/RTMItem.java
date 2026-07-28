package jp.ngt.rtm;

import net.minecraft.world.item.Item;

/**
 * 本家 jp.ngt.rtm.RTMItem のスクリプト互換。
 * SRB3 の getPlayerRail が item.func_77973_b === RTMItem.itemLargeRail で手持ちレールを判定し、
 * NGTO Builder の Wire ツールが item.func_77973_b === RTMItem.itemWire で手持ちワイヤーを判定する。
 */
@SuppressWarnings("unused")
public final class RTMItem {
    private RTMItem() {
    }

    public static Item itemLargeRail;
    public static Item itemWire;
    // NGTO Builder の Wire ツールが ignoreItemList (碍子を持ったままの右クリックを無視) に使う。
    public static Item installedObject;
    // hi03CatenaryPack の render_CatenaryConnector が ignoreItemList に使う。
    public static Item crowbar;

    // 以下は本家と一対一で対応する RTMU のアイテム
    public static Item itemtrain;
    public static Item itemVehicle;
    public static Item itemSignal;
    public static Item itemRailroadSign;
    public static Item itemLinePole;
    public static Item itemPipe;
    public static Item icCard;
    public static Item wrench;
    public static Item ticket;
    public static Item signboard;
    public static Item speaker;
    public static Item turnstile;
    public static Item fluorescent;
    public static Item light;
    public static Item crossingGate;
    public static Item point;
    public static Item connector;
    public static Item bumpingPost;
    public static Item miniature;

    static {
        try {
            itemLargeRail = com.portofino.realtrainmodunofficial.RealTrainModUnofficialItems.RAIL_ITEM.get();
            itemWire = com.portofino.realtrainmodunofficial.RealTrainModUnofficialItems.WIRE_ITEM.get();
            installedObject = com.portofino.realtrainmodunofficial.RealTrainModUnofficialItems.INSULATOR_ITEM.get();
            crowbar = com.portofino.realtrainmodunofficial.RealTrainModUnofficialItems.CROWBAR_ITEM.get();

            itemtrain = com.portofino.realtrainmodunofficial.RealTrainModUnofficialItems.TRAIN_ITEM.get();
            itemVehicle = com.portofino.realtrainmodunofficial.RealTrainModUnofficialItems.TRAIN_VEHICLE_ITEM.get();
            itemSignal = com.portofino.realtrainmodunofficial.RealTrainModUnofficialItems.SIGNAL_ITEM.get();
            itemRailroadSign = com.portofino.realtrainmodunofficial.RealTrainModUnofficialItems.RAILROAD_SIGN_ITEM.get();
            itemLinePole = com.portofino.realtrainmodunofficial.RealTrainModUnofficialItems.OVERHEAD_LINE_POLE_ITEM.get();
            itemPipe = com.portofino.realtrainmodunofficial.RealTrainModUnofficialItems.PIPE_ITEM.get();
            icCard = com.portofino.realtrainmodunofficial.RealTrainModUnofficialItems.IC_CARD_ITEM.get();
            wrench = com.portofino.realtrainmodunofficial.RealTrainModUnofficialItems.WRENCH_ITEM.get();
            ticket = com.portofino.realtrainmodunofficial.RealTrainModUnofficialItems.TICKET_VENDOR_ITEM.get();
            signboard = com.portofino.realtrainmodunofficial.RealTrainModUnofficialItems.SIGNBOARD_ITEM.get();
            speaker = com.portofino.realtrainmodunofficial.RealTrainModUnofficialItems.SPEAKER_ITEM.get();
            turnstile = com.portofino.realtrainmodunofficial.RealTrainModUnofficialItems.TICKET_GATE_ITEM.get();
            fluorescent = com.portofino.realtrainmodunofficial.RealTrainModUnofficialItems.FLUORESCENT_ITEM.get();
            light = com.portofino.realtrainmodunofficial.RealTrainModUnofficialItems.LIGHT_ITEM.get();
            crossingGate = com.portofino.realtrainmodunofficial.RealTrainModUnofficialItems.CROSSING_GATE_ITEM.get();
            point = com.portofino.realtrainmodunofficial.RealTrainModUnofficialItems.POINT_MACHINE_ITEM.get();
            connector = com.portofino.realtrainmodunofficial.RealTrainModUnofficialItems.CONNECTOR_INPUT_ITEM.get();
            bumpingPost = com.portofino.realtrainmodunofficial.RealTrainModUnofficialItems.BUMPING_POST_ITEM.get();
            miniature = com.portofino.realtrainmodunofficial.RealTrainModUnofficialItems.MINIATURE_ITEM.get();
        } catch (Throwable t) {
            jp.ngt.ngtlib.io.NGTLog.debug("[RTMItem] item statics not ready: " + t);
        }
    }
}
