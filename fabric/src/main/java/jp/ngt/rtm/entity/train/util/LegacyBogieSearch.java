package jp.ngt.rtm.entity.train.util;

import jp.ngt.rtm.rail.util.RailMap;

/** Original inclusive trailing-bogie search, including the final rail endpoint. */
public final class LegacyBogieSearch {
    private LegacyBogieSearch() {}
    public static int trailingIndex(RailMap map,int split,int min,int max,double y,double[] front,double length) {
        int best=min;double error=Double.POSITIVE_INFINITY;
        for(int i=min;i<=max;i++) {
            double[] point=map.getRailPos(split,i);
            double dx=point[1]-front[0],dy=y-front[1],dz=point[0]-front[2];
            double candidate=Math.abs(dx*dx+dy*dy+dz*dz-length*length);
            if(candidate<error) {error=candidate;best=i;}
        }
        return best;
    }
}
