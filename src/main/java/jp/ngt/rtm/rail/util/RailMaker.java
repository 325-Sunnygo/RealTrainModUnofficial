package jp.ngt.rtm.rail.util;

import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 本家 jp.ngt.rtm.rail.util.RailMaker (KaizPatchX) の忠実移植。
 * fixRTMRailMapVersion >= 1 の N字分岐は SwitchTypeSingleCrossFixRTMV1 を使用 (KaizPatchX 準拠)。
 */
public final class RailMaker {
    // see RailMapBasic.fixRTMRailMapVersion
    public final int fixRTMRailMapVersion;
    private final Level worldObj;
    private final List<RailPosition> rpList;

    /** @deprecated use #RailMaker(Level, List, int) */
    @Deprecated
    public RailMaker(Level world, List<RailPosition> par2) {
        this(world, par2, 0);
    }

    public RailMaker(Level world, List<RailPosition> par2, int fixRTMRailMapVersion) {
        this.worldObj = world;
        this.rpList = par2;
        this.fixRTMRailMapVersion = fixRTMRailMapVersion;
    }

    /** @deprecated use #RailMaker(Level, RailPosition[], int) */
    @Deprecated
    public RailMaker(Level world, RailPosition[] par2) {
        this(world, par2, 0);
    }

    public RailMaker(Level world, RailPosition[] par2, int fixRTMRailMapVersion) {
        this(world, new ArrayList<>(Arrays.asList(par2)), fixRTMRailMapVersion);
    }

    /** スクリプト互換: WorldCompat + List/配列 (SRB3 等が entity.field_70170_p を渡す)。 */
    public RailMaker(Object world, Object positions, int fixRTMRailMapVersion) {
        this.worldObj = jp.ngt.ngtlib.block.BlockUtil.toLevel(world);
        List<RailPosition> list = new ArrayList<>();
        collectRailPositions(positions, list, 0);
        if (list.isEmpty()) {
            // ここが空だと getSwitch は必ず失敗する。
            // 「An error occurred while generating rails」とだけ出すため、
            // 元のレールを消したあとに生成だけ失敗して線路ごと消える。
            jp.ngt.ngtlib.io.NGTLog.debug("[RailMaker] レール位置が 1 つも取れなかった: %s",
                positions == null ? "null" : positions.getClass().getName());
        }
        this.rpList = list;
        this.fixRTMRailMapVersion = fixRTMRailMapVersion;
    }

    /**
     * スクリプトから渡された「レール位置の集まり」を取り出す。
     * ★JS 配列に対応することが要点。
     */
    private static void collectRailPositions(Object src, List<RailPosition> out, int depth) {
        if (src == null || depth > 4) {
            return;
        }
        if (src instanceof RailPosition rp) {
            out.add(rp);
        } else if (src instanceof RailPosition[] arr) {
            out.addAll(Arrays.asList(arr));
        } else if (src instanceof Object[] arr) {
            for (Object o : arr) {
                collectRailPositions(o, out, depth + 1);
            }
        } else if (src instanceof Iterable<?> it) {
            for (Object o : it) {
                collectRailPositions(o, out, depth + 1);
            }
        } else if (src instanceof java.util.Map<?, ?> m) {
            // Nashorn の JS 配列/オブジェクトはここに来る (ScriptObject implements Map)。
            // JS 配列の要素は values に添字順で並ぶ。
            for (Object o : m.values()) {
                collectRailPositions(o, out, depth + 1);
            }
        }
    }

    // ===== Remaster 暫定互換コンストラクタ (旧コードは world を渡さない。Phase 1 の BE 差し替え後に削除予定) =====

    /** @deprecated Remaster 独自。本家に存在しない。 */
    @Deprecated
    public RailMaker(List<RailPosition> positions) {
        this(null, new ArrayList<>(positions), 0);
    }

    /** @deprecated Remaster 独自。本家に存在しない。 */
    @Deprecated
    public RailMaker(RailPosition[] positions) {
        this(null, positions, 0);
    }

    private SwitchType getSwitchType() {
        if (this.rpList.size() == 3) {
            int i0 = this.rpList.stream().mapToInt(rp -> (rp.switchType == 1) ? 1 : 0).sum();

            if (i0 == 1) {
                return new SwitchType.SwitchBasic(fixRTMRailMapVersion);
            }
        } else if (this.rpList.size() == 4) {
            int i0 = this.rpList.stream().mapToInt(rp -> (rp.switchType == 1) ? 1 : 0).sum();

            if (i0 == 2) {
                if (fixRTMRailMapVersion >= 1) {
                    return new SwitchTypeSingleCrossFixRTMV1(fixRTMRailMapVersion);
                } else {
                    return new SwitchType.SwitchSingleCross(fixRTMRailMapVersion);
                }
            } else if (i0 == 4) {
                for (int i = 0; i < this.rpList.size(); ++i) {
                    for (int j = i + 1; j < this.rpList.size(); ++j)//全組み合わせ(重複なし)
                    {
                        if (this.rpList.get(i).direction == this.rpList.get(j).direction) {
                            return new SwitchType.SwitchScissorsCross(fixRTMRailMapVersion);
                        }
                    }
                }
                return new SwitchType.SwitchDiamondCross(fixRTMRailMapVersion);
            }
        }

        return null;
    }

    public SwitchType getSwitch() {
        // ★SuperRailBuilder3 は分岐生成をこの経路で行い、例外を JS 側で握りつぶして
        // 「An error occurred while generating rails」とだけ表示する。
        // その結果、元のレールを消したあとに生成が失敗して線路ごと消えるのに
        // ログには何も残らなかった。ここで必ず記録してから投げ直す。
        try {
            SwitchType type = this.getSwitchType();
            if (type == null) {
                jp.ngt.ngtlib.io.NGTLog.debug(
                    "[RailMaker] getSwitch: 分岐種別が決まらない (rp=%d, switchType=1 の数=%d)",
                    this.rpList.size(),
                    this.rpList.stream().mapToInt(rp -> (rp.switchType == 1) ? 1 : 0).sum());
                return null;
            }
            List<RailPosition> switchList = new ArrayList<>();//分岐あり
            List<RailPosition> normalList = new ArrayList<>();//分岐なし
            this.rpList.forEach(rp -> (rp.switchType == 1 ? switchList : normalList).add(rp));

            if (type.init(switchList, normalList)) {
                return type;
            }
            jp.ngt.ngtlib.io.NGTLog.debug(
                "[RailMaker] getSwitch: %s.init が false (分岐あり=%d 分岐なし=%d)",
                type.getClass().getSimpleName(), switchList.size(), normalList.size());
            return null;
        } catch (Throwable t) {
            com.portofino.realtrainmodunofficial.RealTrainModUnofficial.LOGGER.error(
                "[RTMU] 分岐生成で例外 (SRB3 はこれを握りつぶすためログに出なかった)。rp={}",
                this.rpList.size(), t);
            throw t;
        }
    }
}
