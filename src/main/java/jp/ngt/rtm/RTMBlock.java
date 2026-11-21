package jp.ngt.rtm;

import net.minecraft.world.level.block.Block;

/** 本家 jp.ngt.rtm.RTMBlock のスクリプト互換 (マーカー参照等)。 */
@SuppressWarnings("unused")
public final class RTMBlock {
    private RTMBlock() {
    }

    public static Block marker;
    public static Block markerSwitch;
    public static Block markerStraight;
    // NGTO Builder の Wire ツールが setBlock(..., RTMBlock.i…
    public static Block insulator;

    // 本家の renderId* は 1.7.10 の ISimpleBlockRenderingHan…
    public static int renderIdBlockRail;
    public static int renderIdLiquid = 1;
    public static int renderIdScaffold = 2;
    public static int renderIdScaffoldStairs = 3;
    public static int renderIdSignalBase = 4;
    public static int renderIdVariableBlock = 5;

    static {
        try {
            marker = jp.ngt.rtm.rail.RTMRailBlocks.MARKER.get();
            markerSwitch = jp.ngt.rtm.rail.RTMRailBlocks.MARKER_SWITCH.get();
            markerStraight = marker;
            insulator = com.portofino.realtrainmodunofficial.RealTrainModUnofficialBlocks.INSTALLED_OBJECT.get();
        } catch (Throwable t) {
            jp.ngt.ngtlib.io.NGTLog.debug("[RTMBlock] block statics not ready: " + t);
        }
    }

    // 本家の signal / speaker / turnstile / fluorescent 等の…
}
