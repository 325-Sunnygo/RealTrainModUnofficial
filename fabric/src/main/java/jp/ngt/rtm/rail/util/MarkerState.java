package jp.ngt.rtm.rail.util;

/**
 * 本家 jp.ngt.rtm.rail.util.MarkerState の忠実移植。
 */
public enum MarkerState {
    FIT_NEIGHBOR, DISTANCE, GRID, LINE1, LINE2, ANCHOR21;

    private int bitMask() {
        return 1 << ordinal();
    }

    public boolean get(int data) {
        return (data & bitMask()) > 0;
    }

    public int set(int data, boolean state) {
        int mask = bitMask();
        if (state) {
            return data | mask;
        }
        return (data | mask) - mask;
    }

    public int flip(int data) {
        int mask = bitMask();
        return data ^ mask;
    }
}
