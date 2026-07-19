package jp.ngt.rtm.rail.util;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * 本家 jp.ngt.rtm.rail.util.SwitchType (KaizPatchX) の忠実移植。
 * 1.21 適合: World→Level, Entity 座標アクセサのみ変更。ロジックは本家のまま。
 */
public abstract class SwitchType {
    public final byte id;
    protected RailMapSwitch[] railMaps;
    protected Point[] points;
    // see RailMapBasic#fixRTMRailMapVersion
    public final int fixRTMRailMapVersion;

    @Deprecated
    protected List<RailMapSwitch> activeRails = new ArrayList<>();

    /**
     * @deprecated use {@link #SwitchType(int, int)}
     */
    @Deprecated
    protected SwitchType(int par1) {
        this(par1, 0);
    }

    protected SwitchType(int par1, int fixRTMRailMapVersion) {
        this.id = (byte) par1;
        this.fixRTMRailMapVersion = fixRTMRailMapVersion;
    }

    /**
     * 初期化
     */
    public abstract boolean init(List<RailPosition> switchList, List<RailPosition> normalList);

    public abstract String getName();

    /**
     * ブロック更新時に呼ばれる
     */
    public void onBlockChanged(Level world) {
    }

    /**
     * TileEntity.updateEntity()のタイミングで呼ばれる
     */
    public void onUpdate(Level world) {
        //軽量化: 全分岐レールコアが両サイド毎tick通る。Arrays.stream は毎tick Stream+ラムダを
        //アロケートするので素の for に (挙動は完全に同じ、GC 圧を下げるだけ)。
        for (Point point : this.points) {
            point.onUpdate(world);
        }
    }

    public abstract RailMap getRailMap(Entity entity);

    public RailMapSwitch[] getAllRailMap() {
        return this.railMaps;
    }

    @Deprecated
    public List<RailMapSwitch> getActiveRailMap() {
        return this.activeRails;
    }

    public Point[] getPoints() {
        return this.points;
    }

    public Point getNearestPoint(Entity entity) {
        Point point = null;
        double distance = Double.MAX_VALUE;
        for (Point p0 : this.getPoints()) {
            double d0 = entity.distanceToSqr(p0.rpRoot.posX, 0.0D, p0.rpRoot.posZ);
            if (d0 <= distance) {
                point = p0;
                distance = d0;
            }
        }
        return point;
    }

    // ================= 以下は Remaster 暫定互換 API (Phase 1 の BE 差し替え完了後に削除予定) =================

    /**
     * @deprecated Remaster 独自 API。本家に存在しない。
     */
    @Deprecated
    public List<RailMap> getOpenRailMaps() {
        if (this.id == 3) {
            return List.of(this.railMaps);
        }
        List<RailMap> open = new ArrayList<>(this.railMaps.length);
        for (RailMapSwitch railMap : this.railMaps) {
            if (railMap != null && railMap.isOpen()) {
                open.add(railMap);
            }
        }
        return open.isEmpty() ? List.of(this.railMaps) : open;
    }

    /**
     * @deprecated Remaster 独自 API。本家に存在しない。
     */
    @Deprecated
    public int firstOpenRailIndex() {
        if (this.id == 3) {
            return 0;
        }
        for (int i = 0; i < this.railMaps.length; i++) {
            if (this.railMaps[i] != null && this.railMaps[i].isOpen()) {
                return i;
            }
        }
        return 0;
    }

    /**
     * @deprecated Remaster 独自 API。本家に存在しない。
     */
    @Deprecated
    public int[] getOpenRailIndices() {
        if (this.id == 3) {
            int[] indices = new int[this.railMaps.length];
            for (int i = 0; i < this.railMaps.length; i++) {
                indices[i] = i;
            }
            return indices;
        }
        int count = 0;
        for (RailMapSwitch railMap : this.railMaps) {
            if (railMap != null && railMap.isOpen()) {
                count++;
            }
        }
        if (count == 0) {
            return new int[]{0};
        }
        int[] indices = new int[count];
        int write = 0;
        for (int i = 0; i < this.railMaps.length; i++) {
            if (this.railMaps[i] != null && this.railMaps[i].isOpen()) {
                indices[write++] = i;
            }
        }
        return indices;
    }

    /****************************************************************************************************/

    //Y字
    public static class SwitchBasic extends SwitchType {
        /**
         * @deprecated use {@link #SwitchBasic(int)}
         */
        @Deprecated
        public SwitchBasic() {
            this(0);
        }

        public SwitchBasic(int fixRTMRailMapVersion) {
            super(0, fixRTMRailMapVersion);
        }

        @Override
        public boolean init(List<RailPosition> switchList, List<RailPosition> normalList) {
            RailMapSwitch[] rails = new RailMapSwitch[2];
            RailPosition rpRoot = switchList.get(0);
            RailPosition rpBranch1 = normalList.get(0);
            RailPosition rpBranch2 = normalList.get(1);
            RailDir dir = rpRoot.getDir(rpBranch1, rpBranch2);
            rails[0] = new RailMapSwitch(rpRoot, rpBranch1, dir, RailDir.NONE, this.fixRTMRailMapVersion);
            rails[1] = new RailMapSwitch(rpRoot, rpBranch2, dir.invert(), RailDir.NONE, this.fixRTMRailMapVersion);
            this.railMaps = rails;
            this.activeRails.add(this.railMaps[0]);

            this.points = new Point[3];
            this.points[0] = new Point(rpRoot, rails[0], rails[1]);
            this.points[1] = new Point(rpBranch1, rails[0]);
            this.points[2] = new Point(rpBranch2, rails[1]);
            return true;
        }

        @Override
        public void onBlockChanged(Level world) {
            super.onBlockChanged(world);

            this.activeRails.clear();
            this.railMaps[0].setState(false);
            this.railMaps[1].setState(false);
            RailMapSwitch activeRM = (RailMapSwitch) this.points[0].getActiveRailMap(world);
            this.activeRails.add(activeRM.setState(true));
        }

        @Override
        public RailMap getRailMap(Entity entity) {
            return this.points[0].getActiveRailMap(entity.level());
        }

        @Override
        public String getName() {
            return "Simple";
        }
    }

    /****************************************************************************************************/

    //N字
    public static class SwitchSingleCross extends SwitchType {
        /**
         * @deprecated use {@link #SwitchSingleCross(int)}
         */
        @Deprecated
        public SwitchSingleCross() {
            this(0);
        }

        public SwitchSingleCross(int fixRTMRailMapVersion) {
            super(1, fixRTMRailMapVersion);
        }

        @Override
        public boolean init(List<RailPosition> switchList, List<RailPosition> normalList) {
            RailMapSwitch[] rails = new RailMapSwitch[3];
            RailPosition rpRoot1 = switchList.get(0);
            RailPosition rpRoot2 = switchList.get(1);
            RailDir b0 = RailDir.NONE;
            RailDir b1 = RailDir.NONE;
            int rmsCount = 0;

            for (RailPosition rpA : switchList) {
                for (RailPosition rpB : normalList) {
                    if (rpA.direction != rpB.direction) {
                        boolean flag = (rpA == rpRoot1);
                        RailPosition rp2 = flag ? rpRoot2 : rpRoot1;//sw-sw側のend
                        RailDir b2 = rpA.getDir(rp2, rpB);
                        if (flag) {
                            b0 = b2;
                        } else {
                            b1 = b2;
                        }
                        rails[rmsCount] = new RailMapSwitch(rpA, rpB, b2.invert(), RailDir.NONE, this.fixRTMRailMapVersion);
                    }
                }
                ++rmsCount;
            }
            rails[2] = new RailMapSwitch(rpRoot1, rpRoot2, b0, b1, this.fixRTMRailMapVersion);//渡り部分
            this.railMaps = rails;
            this.activeRails.add(this.railMaps[0]);
            this.activeRails.add(this.railMaps[1]);

            this.points = new Point[4];
            this.points[0] = new Point(rpRoot1, rails[0], rails[2]);
            this.points[1] = new Point(rpRoot2, rails[1], rails[2]);
            this.points[2] = new Point((rpRoot1 == rails[0].getStartRP() ? rails[0].getEndRP() : rails[0].getStartRP()), rails[0]);
            this.points[3] = new Point((rpRoot2 == rails[1].getStartRP() ? rails[1].getEndRP() : rails[1].getStartRP()), rails[1]);

            return true;
        }

        @Override
        public void onBlockChanged(Level world) {
            super.onBlockChanged(world);

            this.activeRails.clear();
            if (this.railMaps[2].isGettingPowered(world)) {
                this.railMaps[0].setState(false);
                this.railMaps[1].setState(false);
                this.railMaps[2].setState(true);
                this.activeRails.add(this.railMaps[2]);
            } else {
                this.railMaps[0].setState(true);
                this.railMaps[1].setState(true);
                this.railMaps[2].setState(false);
                this.activeRails.add(this.railMaps[0]);
                this.activeRails.add(this.railMaps[1]);
            }
        }

        @Override
        public RailMap getRailMap(Entity entity) {
            RailMap map1 = this.points[0].getActiveRailMap(entity.level());
            RailMap map2 = this.points[1].getActiveRailMap(entity.level());
            if (map1 == map2) {
                return map1;
            } else {
                int n1 = map1.getNearlestPoint(16, entity.getX(), entity.getZ());
                int n2 = map2.getNearlestPoint(16, entity.getX(), entity.getZ());
                double[] pos1 = map1.getRailPos(16, n1);
                double[] pos2 = map1.getRailPos(16, n2);//本家のまま (map1)
                double d1 = entity.distanceToSqr(pos1[1], 0.0D, pos1[0]);
                double d2 = entity.distanceToSqr(pos2[1], 0.0D, pos2[0]);
                return d1 < d2 ? map1 : map2;
            }
        }

        @Override
        public String getName() {
            return "Crossover";
        }
    }

    /****************************************************************************************************/

    //シーサスクロッシング
    public static class SwitchScissorsCross extends SwitchType {
        /**
         * @deprecated use {@link #SwitchScissorsCross(int)}
         */
        @Deprecated
        public SwitchScissorsCross() {
            this(0);
        }

        public SwitchScissorsCross(int fixRTMRailMapVersion) {
            super(2, fixRTMRailMapVersion);
        }

        @Override
        public boolean init(List<RailPosition> switchList, List<RailPosition> normalList) {
            RailMapSwitch[] rails = new RailMapSwitch[4];
            //RailMapSwitchになる頂点2つを格納
            RailPosition[][] rps = new RailPosition[4][2];
            //頂点同士の組み合わせ調査
            int rpsCount = 0;
            for (int i = 0; i < 4; ++i) {
                for (int j = i + 1; j < 4; ++j) {
                    int dirDif = Math.abs(switchList.get(i).direction - switchList.get(j).direction);
                    if (dirDif > 4) {
                        dirDif = 8 - dirDif;
                    }

                    //角度差>45なら追加
                    if (dirDif > 2 && rpsCount < 4) {
                        rps[rpsCount] = new RailPosition[]{switchList.get(i), switchList.get(j)};
                        ++rpsCount;
                    }
                }
            }

            if (rpsCount == 4) {
                //RailMap生成
                for (int i = 0; i < 4; ++i) {
                    RailDir dir0 = RailDir.NONE;
                    RailDir dir1 = RailDir.NONE;

                    for (int j = 0; j < 4; ++j) {
                        if (i == j) {
                            continue;
                        }

                        if (rps[i][0] == rps[j][0]) {
                            dir0 = rps[i][0].getDir(rps[i][1], rps[j][1]);
                        } else if (rps[i][0] == rps[j][1]) {
                            dir0 = rps[i][0].getDir(rps[i][1], rps[j][0]);
                        } else if (rps[i][1] == rps[j][0]) {
                            dir1 = rps[i][1].getDir(rps[i][0], rps[j][1]);
                        } else if (rps[i][1] == rps[j][1]) {
                            dir1 = rps[i][1].getDir(rps[i][0], rps[j][0]);
                        }
                    }
                    rails[i] = new RailMapSwitch(rps[i][0], rps[i][1], dir0, dir1, this.fixRTMRailMapVersion);
                }

                this.railMaps = rails;
                this.activeRails.add(this.railMaps[0]);

                //Point生成
                this.points = new Point[4];
                for (int i = 0; i < 4; ++i) {
                    RailPosition rp = switchList.get(i);
                    RailMapSwitch rms1 = null;
                    RailMapSwitch rms2 = null;

                    //当該頂点を含むRailMapを2つ探す
                    for (int j = 0; j < 4; ++j) {
                        if (rails[j].getStartRP() == rp || rails[j].getEndRP() == rp) {
                            if (rms1 == null) {
                                rms1 = rails[j];
                            } else {
                                rms2 = rails[j];
                                break;
                            }
                        }
                    }

                    this.points[i] = new Point(rp, rms1, rms2);
                }

                return true;
            }
            return false;
        }

        @Override
        public void onBlockChanged(Level world) {
            super.onBlockChanged(world);

            this.activeRails.clear();
            RailMapSwitch openRMS = null;
            for (int j = 0; j < 2; ++j) {
                for (int i = 0; i < 4; ++i) {
                    RailMapSwitch rms = this.railMaps[i];
                    if (rms.startDir == rms.endDir)//渡り部分
                    {
                        if (j == 0) {
                            if (rms.isGettingPowered(world)) {
                                openRMS = rms;
                                break;
                            }
                        } else {
                            if (rms == openRMS) {
                                rms.setState(true);
                                this.activeRails.add(rms);
                            } else {
                                rms.setState(false);
                            }
                        }
                    } else//直線部分
                    {
                        if (j == 1) {
                            if (openRMS == null)//渡り部分未開通
                            {
                                rms.setState(true);
                                this.activeRails.add(rms);
                            } else {
                                rms.setState(false);
                            }
                        }
                    }
                }
            }
        }

        @Override
        public RailMap getRailMap(Entity entity) {
            RailMap map = null;
            double distance = Double.MAX_VALUE;
            for (Point point : this.getPoints()) {
                RailMap map1 = point.getActiveRailMap(entity.level());
                if (map1 == map) {
                    continue;
                }

                int n1 = map1.getNearlestPoint(16, entity.getX(), entity.getZ());
                double[] pos1 = map1.getRailPos(16, n1);
                double d1 = entity.distanceToSqr(pos1[1], 0.0D, pos1[0]);
                if (d1 < distance) {
                    distance = d1;
                    map = map1;
                }
            }
            return map;
        }

        @Override
        public String getName() {
            return "Scissors Crossing";
        }
    }

    /****************************************************************************************************/

    //ダイヤモンドクロス
    public static class SwitchDiamondCross extends SwitchType {
        /**
         * @deprecated use {@link #SwitchDiamondCross(int)}
         */
        @Deprecated
        public SwitchDiamondCross() {
            this(0);
        }

        public SwitchDiamondCross(int fixRTMRailMapVersion) {
            super(3, fixRTMRailMapVersion);
        }

        @Override
        public boolean init(List<RailPosition> switchList, List<RailPosition> normalList) {
            List<RailPosition> rpList = new ArrayList<>();
            rpList.addAll(switchList);
            rpList.addAll(normalList);

            RailMapSwitch[] rails = new RailMapSwitch[2];
            int k = 0;
            for (int i = 0; i < 4; ++i) {
                for (int j = 0; j < 4; ++j) {
                    if (i < j && Math.abs(rpList.get(i).direction - rpList.get(j).direction) == 4) {
                        rails[k] = new RailMapSwitch(rpList.get(i), rpList.get(j), RailDir.NONE, RailDir.NONE, this.fixRTMRailMapVersion);
                        ++k;

                        if (k >= 2) {
                            this.railMaps = rails;
                            this.activeRails.add(this.railMaps[0].setState(true));
                            this.activeRails.add(this.railMaps[1].setState(true));

                            this.points = new Point[4];
                            this.points[0] = new Point(rails[0].getStartRP(), rails[0]);
                            this.points[1] = new Point(rails[0].getEndRP(), rails[0]);
                            this.points[2] = new Point(rails[1].getStartRP(), rails[1]);
                            this.points[3] = new Point(rails[1].getEndRP(), rails[1]);
                            return true;
                        }
                    }
                }
            }
            return false;
        }

        @Override
        public void onBlockChanged(Level world) {
            super.onBlockChanged(world);

            if (this.activeRails.isEmpty()) {
                this.activeRails.add(this.railMaps[0].setState(true));
                this.activeRails.add(this.railMaps[1].setState(true));
            }
        }

        @Override
        public RailMap getRailMap(Entity entity) {
            RailMap map1 = this.points[0].getActiveRailMap(entity.level());
            RailMap map2 = this.points[2].getActiveRailMap(entity.level());
            int n1 = map1.getNearlestPoint(16, entity.getX(), entity.getZ());
            int n2 = map2.getNearlestPoint(16, entity.getX(), entity.getZ());
            double[] pos1 = map1.getRailPos(16, n1);
            double[] pos2 = map1.getRailPos(16, n2);//本家のまま (map1)
            double d1 = entity.distanceToSqr(pos1[1], 0.0D, pos1[0]);
            double d2 = entity.distanceToSqr(pos2[1], 0.0D, pos2[0]);
            return d1 < d2 ? map1 : map2;
        }

        @Override
        public String getName() {
            return "Diamond Crossing";
        }
    }
}
