package jp.ngt.rtm.rail.util;

/** Simplified ResourceStateRail stand-in: ballast width from rail pack (legacy RailConfig#ballastWidth). */
public class RailProperties {
    /** Same semantics as legacy: full width is2*halfWidth + center; 0 = center column only. */
    public int ballastWidth;
    public float blockHeight;

    public static RailProperties createDefault() {
        RailProperties p = new RailProperties();
        p.ballastWidth = 0;
        p.blockHeight = 0.0625F;
        return p;
    }
}
