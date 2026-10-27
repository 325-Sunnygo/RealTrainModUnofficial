package jp.kaiz.kaizpatch.rtm.rail.util;

import jp.ngt.rtm.rail.*;
import jp.ngt.rtm.rail.util.*;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import java.util.*;

/** Port of KaizPatchX's endpoint-directed rail transition and bounded nearest search. */
public final class RailTransitionResolver {
    private RailTransitionResolver() {}
    public static int findNearestPointAround(RailMap map,int split,int previous,double x,double z,float movement) {
        if(split<=0)return 0;
        if(previous<0 || previous>split)return map.getNearlestPoint(split,x,z);
        int margin=Math.max(1,(int)Math.ceil((Math.abs(movement)+0.25)*360));
        int nearest=previous;double best=Double.MAX_VALUE;
        for(int i=Math.max(0,previous-margin);i<=Math.min(split,previous+margin);i++) {
            double[] point=map.getRailPos(split,i);double dx=x-point[1],dz=z-point[0],d=dx*dx+dz*dz;
            if(d<best) {best=d;nearest=i;}
        }
        return nearest;
    }
    public static TileEntityLargeRailCore keepCurrentSectionCore(TileEntityLargeRailCore current,TileEntityLargeRailCore located) {
        return current!=null && current!=located && current.isSameLogicalRail(located)?current:located;
    }
    public static int findConnectedEntryIndex(RailMap previous,RailMap next,int split) {
        if(previous==null || split<=0)return -1;
        double best=Double.MAX_VALUE;int endpoint=-1;
        for(var p:new RailPosition[]{previous.getStartRP(),previous.getEndRP()}) {
            var ends=new RailPosition[]{next.getStartRP(),next.getEndRP()};
            for(int i=0;i<2;i++) {double dx=p.posX-ends[i].posX,dz=p.posZ-ends[i].posZ,d=dx*dx+dz*dz;if(d<best) {best=d;endpoint=i;}}
        }
        return best>1E-6?-1:endpoint==0?0:split;
    }
    public static float outwardYaw(RailMap map,int split,boolean end) {
        return Mth.wrapDegrees(map.getRailYaw(split,end?Math.max(0,split-1):Math.min(1,split))+(end?0:180));
    }
    public static List<Boolean> selectExitDirections(RailMap map,int split,int previous,double movement,float yaw) {
        int margin=(int)Math.ceil((movement+0.5)*360);List<Boolean> exits=new ArrayList<>();
        if(previous<=margin)exits.add(false);if(split-previous<=margin)exits.add(true);
        exits.sort(Comparator.comparingDouble(end -> Math.abs(Mth.wrapDegrees(yaw-outwardYaw(map,split,end)))));
        exits.removeIf(end -> Math.abs(Mth.wrapDegrees(yaw-outwardYaw(map,split,end)))>90);
        return exits;
    }
    public static List<BlockPos> forwardSearchPositions(RailPosition endpoint,float yaw) {
        int[] n=endpoint.getNeighborPos();Set<BlockPos> positions=new LinkedHashSet<>();positions.add(new BlockPos(n[0],0,n[2]));
        double radians=Math.toRadians(yaw);
        for(int i=0;i<8;i++) {double d=Math.min((i+1)*0.25,2-1E-7);positions.add(new BlockPos(Mth.floor(endpoint.posX+Math.sin(radians)*d),0,Mth.floor(endpoint.posZ+Math.cos(radians)*d)));}
        return List.copyOf(positions);
    }
    public static TileEntityLargeRailCore findConnectedCore(Level world,TileEntityLargeRailCore core,RailMap map,int split,int previous,double x,double z,double targetX,double targetZ,float movingYaw) {
        return find(world,core,map,split,previous,x,z,targetX,targetZ,movingYaw,false);
    }
    public static TileEntityLargeRailCore findCrossedConnectedCore(Level world,TileEntityLargeRailCore core,RailMap map,int split,int previous,double x,double z,double targetX,double targetZ,float movingYaw) {
        return find(world,core,map,split,previous,x,z,targetX,targetZ,movingYaw,true);
    }
    private static TileEntityLargeRailCore find(Level world,TileEntityLargeRailCore core,RailMap map,int split,int previous,double x,double z,double tx,double tz,float yaw,boolean crossed) {
        if(core==null || map==null || split<=0 || previous<0)return null;
        double movement=Math.hypot(tx-x,tz-z);if(movement>1E-7)yaw=(float)Math.toDegrees(Math.atan2(tx-x,tz-z));
        for(boolean end:selectExitDirections(map,split,previous,movement,yaw)) {
            RailPosition endpoint=end?map.getEndRP():map.getStartRP();float exit=outwardYaw(map,split,end);double radians=Math.toRadians(exit);
            if(crossed && (tx-endpoint.posX)*Math.sin(radians)+(tz-endpoint.posZ)*Math.cos(radians)<=1E-7)continue;
            if(crossed && core instanceof TileEntityLargeRailNormalCore normal && normal.isRailSection()) {
                var adjacent=normal.adjacentSection(end);if(adjacent!=null)return adjacent;
            }
            Set<Integer> heights=new LinkedHashSet<>();heights.add(endpoint.getNeighborPos()[1]);
            for(int offset:new int[]{0,1,-1,2,-2,3,-3})heights.add(Mth.floor(endpoint.posY)+offset);
            for(BlockPos pos:forwardSearchPositions(endpoint,exit)) {
                world.getChunk(pos.getX()>>4,pos.getZ()>>4);
                for(int y:heights)if(world.getBlockEntity(new BlockPos(pos.getX(),y,pos.getZ())) instanceof TileEntityLargeRailBase base) {
                    TileEntityLargeRailCore candidate=base.getRailCore();if(candidate==null || candidate==core)continue;
                    if(core.isSameLogicalRail(candidate))return candidate;
                    RailMap[] maps=candidate.getAllRailMaps();if(maps!=null)for(RailMap next:maps)if(map.canConnect(next))return candidate;
                }
            }
        }
        return null;
    }
}
