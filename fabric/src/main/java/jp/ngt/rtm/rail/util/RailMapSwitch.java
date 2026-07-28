package jp.ngt.rtm.rail.util;

/**
 * 本家 jp.ngt.rtm.rail.util.RailMapSwitch の忠実移植。
 */
public class RailMapSwitch extends RailMapBasic {
    public final RailDir startDir, endDir;
    private boolean isOpen;

    /**
     * @deprecated use {@link #RailMapSwitch(RailPosition, RailPosition, RailDir, RailDir, int)}
     */
    @Deprecated
    public RailMapSwitch(RailPosition par1, RailPosition par2, RailDir sDir, RailDir eDir) {
        this(par1, par2, sDir, eDir, 0);
    }

    public RailMapSwitch(RailPosition par1, RailPosition par2, RailDir sDir, RailDir eDir, int version) {
        super(par1, par2, version);
        this.startDir = sDir;
        this.endDir = eDir;
    }

    public RailMapSwitch setState(boolean par1) {
        this.isOpen = par1;
        return this;
    }

    public boolean isOpen() {
        return this.isOpen;
    }
}
