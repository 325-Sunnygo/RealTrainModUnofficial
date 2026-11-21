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
    // NGTO Builder の Wire ツールが setBlock(..., RTMBlock.insulator, ...) で碍子を置く。
    // RTMU では碍子は汎用 INSTALLED_OBJECT ブロック (定義=INSULATOR カテゴリ) なのでそれを指す。
    public static Block insulator;

    // 本家の renderId* は 1.7.10 の ISimpleBlockRenderingHandler 用 ID。
    // 1.21 に相当物は無いが、スクリプトが数値比較に使うことがあるので本家の並びで持つ。
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

    // 本家の signal / speaker / turnstile / fluorescent 等の個別ブロックは、RTMU では
    // すべて単一の INSTALLED_OBJECT ブロック + カテゴリで表現される。
    // INSTALLED_OBJECT を割り当てると world.getBlock(...) == RTMBlock.signal が
    // 「あらゆる設置物」に対して true になり誤判定を招くため、あえて未定義のままにする。
}
