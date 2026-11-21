package jp.ngt.rtm.rail.util;

public enum RailDir {
    RIGHT(-1),
    LEFT(1),
    NONE(0);

    public final byte id;

    RailDir(int id) {
        this.id = (byte) id;
    }

    public RailDir invert() {
        return this == RIGHT ? LEFT : this == LEFT ? RIGHT : NONE;
    }
}
