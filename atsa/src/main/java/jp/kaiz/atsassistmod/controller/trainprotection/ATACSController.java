package jp.kaiz.atsassistmod.controller.trainprotection;

import jp.ngt.rtm.entity.train.EntityBogie;
import jp.ngt.rtm.entity.train.EntityTrainBase;
import jp.ngt.rtm.rail.TileEntityLargeRailBase;
import jp.ngt.rtm.rail.TileEntityLargeRailCore;
import jp.ngt.rtm.rail.TileEntityLargeRailSwitchCore;
import jp.ngt.rtm.rail.util.Point;
import jp.ngt.rtm.rail.util.RailMap;
import jp.ngt.rtm.rail.util.RailPosition;
import jp.ngt.rtm.rail.util.RailProperty;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Arrays;
import java.util.List;

/**
 * 本家 jp.kaiz.atsassistmod.controller.trainprotection.ATACSController の完全移植
 * (無線式列車制御 — レールを先へ辿って先行列車までの距離を測り、ブレーキパターンを生成する)。
 *
 * <ul>
 *   <li>speed[0] = 表示速度 (距離-120m のパターン)</li>
 *   <li>speed[1] = パターン速度 (距離-110m。超過で B7)</li>
 *   <li>speed[2] = 非常パターン (距離-100m。超過で非常ブレーキ)</li>
 * </ul>
 * 先行列車がいなければ制限なし。100m 以内はパターン 0 km/h (B5 で停止保持)。
 */
public class ATACSController extends TrainProtection {
    private int count;

    private TileEntityLargeRailCore savedRail;
    private RailPosition nowRP;
    private RailMap nowRM;

    private double movedDistance;

    private double otherTrainDistance;

    private final int[] speed = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE};

    @Override
    public void onTick(EntityTrainBase train, double distance) throws Exception {
        super.onTick(train, distance);
        double trainX = train.getX();
        double trainY = train.getY();
        double trainZ = train.getZ();
        TileEntityLargeRailBase nowRailBase =
                TileEntityLargeRailBase.getRailFromCoordinates(train.level(), trainX, trainY, trainZ);
        if (nowRailBase != null) {
            TileEntityLargeRailCore nowRailCore = nowRailBase.getRailCore();
            if (nowRailCore != null) {
                if (this.savedRail == null) {
                    this.savedRail = nowRailCore;
                    return;
                } else if (this.savedRail != nowRailCore) {
                    this.savedRail = nowRailCore;
                    this.movedDistance = 0d;
                    this.nowRM = this.getNearRailMap(nowRailCore);
                    this.nowRP = this.getNearRailPoint(this.nowRM);
                } else if (this.nowRP == null) {
                    return;
                }
                if (this.nowRM == null) {
                    return;
                }
                this.movedDistance = this.movedDistance + distance;

                if (this.count > 0) {
                    double nowRailLength = this.nowRM.getLength();
                    double trainDistance = this.otherTrainDistance + nowRailLength - this.movedDistance;

                    if (this.setPatternSpeed(trainDistance, 0)) {
                        //先行列車の直前 — パターン維持
                    } else {
                        this.count--;
                    }
                } else {
                    this.count = 20;
                }

                double necessaryDistance = this.getBreakingDistance(train.getSpeed());
                double trainDistance = this.otherTrainDistance = this.getAnotherTrainDistance(necessaryDistance + 100d);
                if (trainDistance == -1d) {
                    this.speed[0] = Integer.MAX_VALUE;
                    this.speed[1] = Integer.MAX_VALUE;
                    this.speed[2] = Integer.MAX_VALUE;
                } else {
                    double nowRailLength = this.nowRM.getLength();
                    trainDistance = trainDistance + nowRailLength - this.movedDistance;

                    if (this.setPatternSpeed(trainDistance, nowRailLength - this.movedDistance)) {
                        this.count = 0;
                    }
                }
            }
        }
    }

    private boolean setPatternSpeed(double trainDistance, double d0) {
        if (trainDistance - d0 < 1d) {
            return true;
        }

        if (trainDistance > 100d) {
            this.speed[0] = (int) this.getPattern(trainDistance - 120d);
            this.speed[1] = (int) this.getPattern(trainDistance - 110d);
            this.speed[2] = (int) this.getPattern(trainDistance - 100d);
        } else {
            this.speed[0] = 0;
            this.speed[1] = 0;
            this.speed[2] = 0;
        }
        return false;
    }

    @Override
    public int getDisplaySpeed() {
        return this.speed[0];
    }

    public int getPatternSpeed() {
        return this.speed[1];
    }

    public int getEmergencySpeed() {
        return this.speed[2];
    }

    @Override
    public int getNotch(float speedH) {
        if (speedH > this.getEmergencySpeed()) {
            return -8;
        } else if (speedH > this.getPatternSpeed()) {
            return -7;
        } else if (this.getDisplaySpeed() == 0) {
            return -5;
        } else {
            return 1;
        }
    }

    @Override
    public TrainProtectionType getType() {
        return TrainProtectionType.ATACS;
    }

    //列車から
    private RailMap getNearRailMap(TileEntityLargeRailCore core) {
        RailMap railMap;
        if (core instanceof TileEntityLargeRailSwitchCore switchObj) {
            Point point = switchObj.getSwitch().getNearestPoint(this.train.getBogie(train.getTrainDirection()));
            railMap = point == null ? null : point.getActiveRailMap(this.train.level());
        } else {
            railMap = core.getRailMap(this.train.getBogie(train.getTrainDirection()));
        }
        return railMap;
    }

    //レールから
    private RailMap getNearRailMap(TileEntityLargeRailCore core, TileEntityLargeRailBase base) {
        RailMap railMap;
        if (core instanceof TileEntityLargeRailSwitchCore switchObj) {
            Point point = this.getNearestPoint(base, switchObj.getSwitch().getPoints());
            railMap = point == null ? null : point.getActiveRailMap(base.getLevel());
        } else {
            railMap = core.getRailMap(null);
        }
        return railMap;
    }

    public Point getNearestPoint(TileEntityLargeRailBase entity, Point[] points) {
        Point point = null;
        double distance = Double.MAX_VALUE;
        BlockPos bePos = entity.getBlockPos();
        for (Point p0 : points) {
            double dx = bePos.getX() - p0.rpRoot.posX;
            double dz = bePos.getZ() - p0.rpRoot.posZ;
            double d0 = Math.sqrt(dx * dx + dz * dz);
            if (d0 <= distance) {
                point = p0;
                distance = d0;
            }
        }
        return point;
    }

    /** レールを進行方向へ辿り、先行列車までの距離を返す (居なければ -1)。 */
    private double getAnotherTrainDistance(double searchDistance) {
        double distance = 0d;
        RailPosition tempRailPosition = this.getRailPositionDestination(this.nowRM);
        TileEntityLargeRailCore tempRail = this.savedRail;
        while (true) {
            if (distance >= searchDistance) {
                return -1d;
            }
            TileEntityLargeRailBase railBase = this.getNextRailBase(tempRail.getLevel(), tempRailPosition);
            if (railBase == null) {
                return -1d;
            }
            tempRail = railBase.getRailCore();
            if (tempRail == null) {
                return -1d;
            }
            if (tempRail.isTrainOnRail()) {
                EntityBogie bogie0 = this.train.getBogie(0);
                EntityBogie bogie1 = this.train.getBogie(1);
                if (bogie0 == null || bogie1 == null) {
                    break;
                }

                int x0 = Mth.floor(Math.min(bogie0.getX(), bogie1.getX()));
                int y0 = Mth.floor(Math.min(bogie0.getY(), bogie1.getY()));
                int z0 = Mth.floor(Math.min(bogie0.getZ(), bogie1.getZ()));
                int x1 = Mth.floor(Math.max(bogie0.getX(), bogie1.getX()));
                int y1 = Mth.floor(Math.max(bogie0.getY(), bogie1.getY()));
                int z1 = Mth.floor(Math.max(bogie0.getZ(), bogie1.getZ()));

                RailProperty rp = tempRail.getProperty();

                //踏んでいるレールが自編成のものなら除外 (本家のまま)
                if (Arrays.stream(tempRail.getAllRailMaps())
                        .map(railMap -> railMap.getRailBlockList(rp))
                        .flatMap(List::stream)
                        .noneMatch(pos -> pos[0] >= x0 && pos[0] <= x1
                                && pos[1] >= y0 - 2 && pos[1] <= y1 + 1
                                && pos[2] >= z0 && pos[2] <= z1)) {
                    break;
                }
            }
            RailMap tempMap = this.getNearRailMap(tempRail, railBase);
            if (tempMap == null) {
                return -1d;
            }
            tempRailPosition = this.getFarRailPotion(tempRailPosition, tempMap);
            distance = distance + tempMap.getLength();
        }
        return distance - train.getConfig().trainDistance;
    }

    private double getPattern(double distance) {
        return Math.sqrt((1.4f * 3.6f) * 7.2f * (distance));
    }

    private double getBreakingDistance(float trainSpeedT) {
        float trainSpeedH = trainSpeedT * 72f + 20f;
        return Math.pow(trainSpeedH, 2) / ((0.8f * 3.6f) * 7.2f);
    }

    private RailPosition getRailPositionDestination(RailMap railMap) {
        if (railMap.getStartRP() == this.nowRP) {
            return railMap.getEndRP();
        } else {
            return railMap.getStartRP();
        }
    }

    private TileEntityLargeRailBase getNextRailBase(Level level, RailPosition railPosition) {
        if (level == null || railPosition == null) {
            return null;
        }
        BlockEntity tile = level.getBlockEntity(new BlockPos(
                Mth.floor(railPosition.posX + RailPosition.REVISION[railPosition.direction][0]),
                railPosition.blockY,
                Mth.floor(railPosition.posZ + RailPosition.REVISION[railPosition.direction][1])));
        return tile instanceof TileEntityLargeRailBase base ? base : null;
    }

    private RailPosition getNearRailPoint(RailMap railMap) {
        if (railMap == null) {
            return null;
        }
        RailPosition rp0 = railMap.getStartRP();
        RailPosition rp1 = railMap.getEndRP();
        double distance0 = train.distanceToSqr(rp0.blockX, rp0.blockY, rp0.blockZ);
        double distance1 = train.distanceToSqr(rp1.blockX, rp1.blockY, rp1.blockZ);
        return distance0 < distance1 ? rp0 : rp1;
    }

    private RailPosition getFarRailPotion(RailPosition railPosition0, RailMap railMap) {
        RailPosition rp0 = railMap.getStartRP();
        RailPosition rp1 = railMap.getEndRP();
        double distance0 = this.getRPToRP(railPosition0, rp0);
        double distance1 = this.getRPToRP(railPosition0, rp1);
        return distance0 < distance1 ? rp1 : rp0;
    }

    private double getRPToRP(RailPosition railPosition1, RailPosition railPosition2) {
        double rightSquaredValue = 0;
        rightSquaredValue += Math.pow(railPosition1.blockX - railPosition2.blockX, 2);
        rightSquaredValue += Math.pow(railPosition1.blockY - railPosition2.blockY, 2);
        rightSquaredValue += Math.pow(railPosition1.blockZ - railPosition2.blockZ, 2);
        return Math.sqrt(rightSquaredValue);
    }
}
