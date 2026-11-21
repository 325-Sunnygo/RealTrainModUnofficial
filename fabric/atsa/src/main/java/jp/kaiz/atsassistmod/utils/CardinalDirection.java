package jp.kaiz.atsassistmod.utils;

import jp.ngt.rtm.entity.train.EntityBogie;
import jp.ngt.rtm.entity.train.EntityTrainBase;

/** 本家 jp.kaiz.atsassistmod.utils.CardinalDirection の移植 (列車の進行方角判定)。 */
public enum CardinalDirection {
    NORTH("NORTH", false, Axis.Z),
    EAST("EAST", true, Axis.X),
    SOUTH("SOUTH", true, Axis.Z),
    WEST("WEST", false, Axis.X);

    private final String name;
    private final boolean positive;
    private final Axis axis;

    CardinalDirection(String name, boolean positive, Axis axis) {
        this.name = name;
        this.positive = positive;
        this.axis = axis;
    }

    public String getName() {
        return name;
    }

    public boolean isInDirection(EntityTrainBase entity) {
        int dir = entity.getTrainDirection();
        EntityBogie front = entity.getBogie(dir == 0 ? 0 : 1);
        EntityBogie back = entity.getBogie(dir == 0 ? 1 : 0);
        if (front == null || back == null) {
            return false;
        }
        switch (axis) {
            case X:
                return positive == front.getX() > back.getX();
            case Z:
                return positive == front.getZ() > back.getZ();
            default:
                return false;
        }
    }

    public static CardinalDirection getDirection(String name) {
        try {
            return CardinalDirection.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return CardinalDirection.NORTH;
        }
    }

    private enum Axis {
        X, Z
    }
}
