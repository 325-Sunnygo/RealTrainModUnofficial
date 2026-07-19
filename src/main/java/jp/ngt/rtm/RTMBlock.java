package jp.ngt.rtm;

import net.minecraft.world.level.block.Block;

/**
 * 本家 jp.ngt.rtm.RTMBlock のスクリプト互換 (マーカー参照等)。
 */
@SuppressWarnings("unused")
public final class RTMBlock {
    private RTMBlock() {
    }

    public static Block marker;
    public static Block markerSwitch;
    // NGTO Builder の Wire ツールが setBlock(..., RTMBlock.insulator, ...) で碍子を置く。
    // RTMU では碍子は汎用 INSTALLED_OBJECT ブロック (定義=INSULATOR カテゴリ) なのでそれを指す。
    public static Block insulator;

    static {
        try {
            marker = jp.ngt.rtm.rail.RTMRailBlocks.MARKER.get();
            markerSwitch = jp.ngt.rtm.rail.RTMRailBlocks.MARKER_SWITCH.get();
            insulator = com.portofino.realtrainmodunofficial.RealTrainModUnofficialBlocks.INSTALLED_OBJECT.get();
        } catch (Throwable t) {
            jp.ngt.ngtlib.io.NGTLog.debug("[RTMBlock] block statics not ready: " + t);
        }
    }
}
