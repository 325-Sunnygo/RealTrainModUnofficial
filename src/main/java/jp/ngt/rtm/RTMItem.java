package jp.ngt.rtm;

import net.minecraft.world.item.Item;

/**
 * 本家 jp.ngt.rtm.RTMItem のスクリプト互換。
 * SRB3 の getPlayerRail が item.func_77973_b() === RTMItem.itemLargeRail で手持ちレールを判定し、
 * NGTO Builder の Wire ツールが item.func_77973_b() === RTMItem.itemWire で手持ちワイヤーを判定する。
 * これらの static が未設定だと「手に持っていても認識されず無言で置けない」。
 */
@SuppressWarnings("unused")
public final class RTMItem {
    private RTMItem() {
    }

    public static Item itemLargeRail;
    public static Item itemWire;
    //NGTO Builder の Wire ツールが ignoreItemList (碍子を持ったままの右クリックを無視) に使う。
    public static Item installedObject;
    //hi03CatenaryPack の render_CatenaryConnector が ignoreItemList に使う。
    public static Item crowbar;

    static {
        try {
            itemLargeRail = com.portofino.realtrainmodunofficial.RealTrainModUnofficialItems.RAIL_ITEM.get();
            itemWire = com.portofino.realtrainmodunofficial.RealTrainModUnofficialItems.WIRE_ITEM.get();
            installedObject = com.portofino.realtrainmodunofficial.RealTrainModUnofficialItems.INSULATOR_ITEM.get();
            crowbar = com.portofino.realtrainmodunofficial.RealTrainModUnofficialItems.CROWBAR_ITEM.get();
        } catch (Throwable t) {
            jp.ngt.ngtlib.io.NGTLog.debug("[RTMItem] item statics not ready: " + t);
        }
    }
}
