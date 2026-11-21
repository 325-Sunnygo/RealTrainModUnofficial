package jp.kaiz.kaizpatch.rtm.rail.util;
import jp.ngt.rtm.rail.util.*;
/** Restricts the original curve's parameter domain without rebuilding its control points. */
public final class RailMapSection extends RailMap {
    public final RailMapBasic source;
    public final double startRatio,endRatio;
    private final RailPosition start,end;
    public RailMapSection(RailMapBasic source,RailPosition start,RailPosition end,double from,double to) {this.source=source;this.start=start;this.end=end;startRatio=from;endRatio=to;}
    public RailPosition getStartRP() {return start;}
    public RailPosition getEndRP() {return end;}
    public double getLength() {return source.getLength()*Math.max(0,endRatio-startRatio);}
    public double sourceRatio(double local) {return startRatio+Math.max(0,endRatio-startRatio)*Math.max(0,Math.min(1,local));}
    private double ratio(int split,int index) {return sourceRatio(split<=0?0:(double)index/split);}
    public double[] getRailPos(int split,int index) {return source.getRailPos(ratio(split,index));}
    public double getRailHeight(int split,int index) {return source.getRailHeight(ratio(split,index));}
    public float getRailYaw(int split,int index) {return source.getRailYaw(ratio(split,index));}
    public float getRailPitch(int split,int index) {return source.getRailPitch(ratio(split,index));}
    public float getRailRoll(int split,int index) {return source.getRailRoll(ratio(split,index));}
    public int getNearlestPoint(int split,double x,double z) {
        int nearest=0;double best=Double.MAX_VALUE;
        for(int i=0;i<=split;i++) {double[] p=getRailPos(split,i);double dx=x-p[1],dz=z-p[0],d=dx*dx+dz*dz;if(d<best) {best=d;nearest=i;}}
        return nearest;
    }
}
