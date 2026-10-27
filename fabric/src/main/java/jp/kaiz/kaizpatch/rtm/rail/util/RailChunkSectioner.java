package jp.kaiz.kaizpatch.rtm.rail.util;
import jp.ngt.rtm.rail.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import java.util.*;
/** KaizPatchX chunk-boundary sampling, bisection and unique core placement. */
public final class RailChunkSectioner {
    private static final double EPS=1E-7;
    private RailChunkSectioner() {}
    public record RailSection(double startRatio,double endRatio,RailPosition startRP,RailPosition endRP) {
        public double getStartRatio() {return startRatio;}
        public double getEndRatio() {return endRatio;}
        public RailPosition getStartRP() {return startRP;}
        public RailPosition getEndRP() {return endRP;}
    }
    private record Start(double ratio,RailPosition rp) {}
    private record Chunk(int x,int z) {}
    private static RailPosition copy(RailPosition rp) {return RailPosition.readFromNBT(rp.writeToNBT());}
    private static Chunk chunk(RailMapBasic source,double ratio) {double[] p=source.getRailPos(ratio);return new Chunk(Mth.floor(p[1])>>4,Mth.floor(p[0])>>4);}
    public static List<RailSection> split(RailMapBasic source) {
        List<Double> boundaries=boundaries(source);List<Start> starts=new ArrayList<>();starts.add(new Start(0,copy(source.getStartRP())));
        Set<BlockPos> occupied=new LinkedHashSet<>();var initial=source.getStartRP();occupied.add(new BlockPos(initial.blockX,initial.blockY,initial.blockZ));
        for(int i=0;i<boundaries.size();i++) {
            double from=boundaries.get(i),to=i+1<boundaries.size()?boundaries.get(i+1):1,length=to-from;
            if(length<=EPS)continue;
            Chunk target=chunk(source,Math.min(to,from+Math.max(EPS*8,length*1E-4)));BlockPos core=null;
            for(int j=0;j<=32;j++) {
                double ratio=Math.min(to,from+length*(j==0?1E-4:(double)j/32));double[] point=source.getRailPos(ratio);
                BlockPos candidate=new BlockPos(Mth.floor(point[1]),Mth.floor(source.getRailHeight(ratio)),Mth.floor(point[0]));
                if(new Chunk(candidate.getX()>>4,candidate.getZ()>>4).equals(target) && !occupied.contains(candidate)) {core=candidate;break;}
            }
            if(core==null)continue;
            float yaw=source.getRailYaw(from),normalized=(yaw%360+360)%360;double[] point=source.getRailPos(from);
            RailPosition rp=new RailPosition(core.getX(),core.getY(),core.getZ(),(byte)(Mth.floor(normalized/45+0.5)&7));
            rp.anchorYaw=Mth.wrapDegrees(yaw);rp.anchorPitch=Mth.wrapDegrees(source.getRailPitch(from));rp.anchorLengthHorizontal=0;rp.anchorLengthVertical=0;
            rp.cantEdge=source.getRailRoll(from);rp.cantCenter=initial.cantCenter;rp.cantRandom=initial.cantRandom;
            rp.constLimitHP=initial.constLimitHP;rp.constLimitHN=initial.constLimitHN;rp.constLimitWP=initial.constLimitWP;rp.constLimitWN=initial.constLimitWN;
            rp.setPosition(point[1],source.getRailHeight(from),point[0]);starts.add(new Start(from,rp));occupied.add(core);
        }
        List<RailSection> result=new ArrayList<>();
        for(int i=0;i<starts.size();i++) {
            double to=i+1<starts.size()?starts.get(i+1).ratio:1;
            RailPosition end=copy(i+1<starts.size()?starts.get(i+1).rp:source.getEndRP());
            if(i+1<starts.size()) {double x=end.posX,y=end.posY,z=end.posZ;end.direction=(byte)((end.direction+4)&7);end.anchorYaw=Mth.wrapDegrees(end.anchorYaw+180);end.setPosition(x,y,z);}
            if(to-starts.get(i).ratio>EPS)result.add(new RailSection(starts.get(i).ratio,to,copy(starts.get(i).rp),end));
        }
        return result;
    }
    private static List<Double> boundaries(RailMapBasic source) {
        if(source.getLength()<=0)return List.of();
        int samples=Math.max(1,(int)Math.ceil(source.getLength()*4));List<Double> result=new ArrayList<>();double previous=0;Chunk previousChunk=chunk(source,0);
        for(int i=1;i<=samples;i++) {
            double current=(double)i/samples,search=previous;Chunk currentChunk=chunk(source,current),searchChunk=previousChunk;int guard=0;
            while(!searchChunk.equals(currentChunk) && guard++<4) {
                double low=search,high=current;
                while(high-low>EPS) {double mid=(low+high)*0.5;if(chunk(source,mid).equals(searchChunk))low=mid;else high=mid;}
                double boundary=high;
                if(boundary>EPS && boundary<1-EPS && (result.isEmpty() || boundary-result.get(result.size()-1)>EPS))result.add(boundary);
                double after=Math.min(current,boundary+Math.max(EPS*4,(current-previous)*1E-5));if(after<=search+EPS)break;
                search=after;searchChunk=chunk(source,search);currentChunk=chunk(source,current);
            }
            previous=current;previousChunk=currentChunk;
        }
        result.removeIf(r -> chunk(source,Math.max(0,r-1E-6)).equals(chunk(source,Math.min(1,r+1E-6))));return result;
    }
}
