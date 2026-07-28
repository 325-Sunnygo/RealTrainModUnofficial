package jp.ngt.rtm.rail.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * KaizPatchX (fixRTM) の jp.kaiz.kaizpatch.fixrtm.rtm.rail.util.SwitchTypeSingleCrossFixRTMV1 (Kotlin) の Java 移植。
 * fixRTMRailMapVersion >= 1 の N字分岐 (渡り線) 生成を改良したもの。
 * 幾何判定 (crossLineSegments) も本家 Geometrix.kt をそのまま移植 (dot 積判定も本家準拠)。
 */
public final class SwitchTypeSingleCrossFixRTMV1 extends SwitchType.SwitchSingleCross {

    public SwitchTypeSingleCrossFixRTMV1(int fixRTMRailMapVersion) {
        super(fixRTMRailMapVersion);
        // this is only for version 1...
        assert fixRTMRailMapVersion >= 1;
    }

    @Override
    public boolean init(List<RailPosition> switchList, List<RailPosition> normalList) {
        if (switchList.size() != 2 || normalList.size() != 2) {
            throw new IllegalStateException("SwitchTypeSingleCrossFixRTMV1 requires 2 switch + 2 normal RPs");
        }

        RailPosition switch0 = switchList.get(0);
        RailPosition switch1 = switchList.get(1);
        RailPosition normal0 = normalList.get(0);
        RailPosition normal1 = normalList.get(1);

        List<RailPosition[]> normalPairs = new ArrayList<>();
        normalPairs.add(new RailPosition[]{normal0, normal1});
        normalPairs.add(new RailPosition[]{normal1, normal0});

        // first, check the lines will never cross
        // in the case the lines cross, switch - switch line is a edge (expected to be diagonal)
        // so return false
        for (RailPosition[] pair : normalPairs) {
            if (crossLineSegments(
                    switch0.posX, switch0.posZ, pair[0].posX, pair[0].posZ,
                    switch1.posX, switch1.posZ, pair[1].posX, pair[1].posZ)) {
                return false;
            }
        }

        // find better switch (スコア降順で最良ペアを先頭に)
        normalPairs.sort(Comparator.comparingDouble(
                (RailPosition[] pair) -> lineScore(switch0, switch1, pair[0], pair[1])).reversed());

        RailPosition normalA = normalPairs.get(0)[0];
        RailPosition normalB = normalPairs.get(0)[1];

        RailDir directionLine0 = switch0.getDir(switch1, normalA);
        RailMapSwitch rmsLine0 = new RailMapSwitch(switch0, normalA, directionLine0.invert(), RailDir.NONE, 1);

        RailDir directionLine1 = switch1.getDir(switch0, normalB);
        RailMapSwitch rmsLine1 = new RailMapSwitch(switch1, normalB, directionLine1.invert(), RailDir.NONE, 1);

        // assertion: directionLine0 or directionLine1 cannot be NONE
        if (directionLine0 == RailDir.NONE || directionLine1 == RailDir.NONE) {
            return false;
        }

        RailMapSwitch rmsSlashLine = new RailMapSwitch(switch0, switch1, directionLine0, directionLine1, 1);

        this.railMaps = new RailMapSwitch[]{rmsLine0, rmsLine1, rmsSlashLine};
        this.points = new Point[]{
                new Point(switchList.get(0), rmsLine0, rmsSlashLine),
                new Point(switchList.get(1), rmsLine1, rmsSlashLine),
                new Point(rmsLine0.getEndRP(), rmsLine0),
                new Point(rmsLine1.getEndRP(), rmsLine1),
        };
        return true;
    }

    private static double lineScore(RailPosition switch0, RailPosition switch1, RailPosition normalA, RailPosition normalB) {
        double score = 0.0D;
        // initial score: sum of distance
        score += distance(switch0.posX, switch0.posZ, normalA.posX, normalA.posZ);
        score += distance(switch1.posX, switch1.posZ, normalB.posX, normalB.posZ);
        // if line maker is opposite direction, that can be better. add 10
        if (Math.abs(switch0.direction - normalA.direction) == 4) {
            score += 10;
        }
        if (Math.abs(switch1.direction - normalB.direction) == 4) {
            score += 10;
        }
        return score;
    }

    private static double distance(double x1, double y1, double x2, double y2) {
        double dx = x1 - x2;
        double dy = y1 - y2;
        return Math.sqrt(dx * dx + dy * dy);
    }

    // ===== fixRTM Geometrix.kt の移植 =====

    private static boolean crossStraightLineAndLineSegments(
            double s1x, double s1y, double s2x, double s2y,
            double g1x, double g1y, double g2x, double g2y) {
        double svx = s1x - s2x;
        double svy = s1y - s2y;
        double d1 = svx * (g1x - s2x) + svy * (g1y - s2y);
        double d2 = svx * (g2x - s2x) + svy * (g2y - s2y);
        return d1 * d2 < 0;
    }

    private static boolean crossLineSegments(
            double l11x, double l11y, double l12x, double l12y,
            double l21x, double l21y, double l22x, double l22y) {
        return crossStraightLineAndLineSegments(l11x, l11y, l12x, l12y, l21x, l21y, l22x, l22y)
                && crossStraightLineAndLineSegments(l21x, l21y, l22x, l22y, l11x, l11y, l12x, l12y);
    }

    @Override
    public String getName() {
        return "CrossoverFixRTMv1";
    }
}
