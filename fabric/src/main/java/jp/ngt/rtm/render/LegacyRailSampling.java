package jp.ngt.rtm.render;

/** Shared reference rules: float length*2, logical sample count, and end cross sections. */
public final class LegacyRailSampling {
    private LegacyRailSampling() {}
    public static int split(double length,int minimum) { return Math.max(Math.max(0,(int)((float)length*2.0F)),minimum); }
    public static int lastIndex(int split,int endOffset) { return Math.max(0,split-endOffset); }
    public static boolean crossSection(String group,int index,int count,boolean start,boolean end) {
        return !group.startsWith("side") || (index==0 && start) || (index==count-1 && end);
    }
}
