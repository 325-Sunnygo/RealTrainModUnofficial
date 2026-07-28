package jp.ngt.rtm;

import jp.ngt.rtm.rail.RTMRailBlocks;
import net.minecraft.world.level.block.Block;

/**
 * 本家 jp.ngt.rtm.RTMRail のスクリプト互換 (SRB3 等がレール生成に使用)。
 * フィールド名は KaizPatchX (1.7.10) の "0" 付き。
 */
@SuppressWarnings("unused")
public final class RTMRail {
    private RTMRail() {
    }

    public static Block largeRailBase0;
    public static Block largeRailCore0;
    public static Block largeRailSwitchBase0;
    public static Block largeRailSwitchCore0;
    public static Block largeRailSlopeBase0;
    public static Block largeRailSlopeCore0;
    // 1.12 名 (isOldVer=false 分岐用の保険)
    public static Block largeRailBase;
    public static Block largeRailCore;
    public static Block largeRailSwitchBase;
    public static Block largeRailSwitchCore;
    public static Block largeRailSlopeBase;
    public static Block largeRailSlopeCore;
    public static Block TURNTABLE_CORE;

    static {
        try {
            largeRailBase0 = largeRailBase = RTMRailBlocks.LARGE_RAIL_BASE.get();
            largeRailCore0 = largeRailCore = RTMRailBlocks.LARGE_RAIL_NORMAL_CORE.get();
            largeRailSwitchBase0 = largeRailSwitchBase = RTMRailBlocks.LARGE_RAIL_SWITCH_BASE.get();
            largeRailSwitchCore0 = largeRailSwitchCore = RTMRailBlocks.LARGE_RAIL_SWITCH_CORE.get();
            largeRailSlopeBase0 = largeRailSlopeBase = RTMRailBlocks.LARGE_RAIL_SLOPE_BASE.get();
            largeRailSlopeCore0 = largeRailSlopeCore = RTMRailBlocks.LARGE_RAIL_SLOPE_CORE.get();
            TURNTABLE_CORE = RTMRailBlocks.TURNTABLE_CORE.get();
        } catch (Throwable t) {
            jp.ngt.ngtlib.io.NGTLog.debug("[RTMRail] block statics not ready: " + t);
        }
    }

    /** 本家 getProperty: レールブロックのプロパティ (レール種別の判定に使う)。 */
    public static Object getProperty(Block block) {
        return block == null ? null : block.defaultBlockState();
    }
}
