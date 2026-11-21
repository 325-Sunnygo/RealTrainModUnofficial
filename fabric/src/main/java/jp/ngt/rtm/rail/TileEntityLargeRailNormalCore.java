package jp.ngt.rtm.rail;

import jp.ngt.rtm.rail.util.RailMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** 本家 jp.ngt.rtm.rail.TileEntityLargeRailNormalCore の忠実移植。 */
public class TileEntityLargeRailNormalCore extends TileEntityLargeRailCore {

    public TileEntityLargeRailNormalCore(BlockPos pos, BlockState state) {
        super(RTMRailBlockEntities.LARGE_RAIL_NORMAL_CORE.get(), pos, state);
    }

    private java.util.UUID railGroupId;
    private jp.ngt.rtm.rail.util.RailPosition[] logicalRailPositions;
    private double sectionStartRatio,sectionEndRatio=1;
    private java.util.List<BlockPos> groupPositions=java.util.List.of();
    public boolean isRailSection() {return railGroupId!=null && logicalRailPositions!=null && logicalRailPositions.length==2;}
    public void configureRailSection(java.util.UUID group,jp.ngt.rtm.rail.util.RailPosition[] logical,jp.ngt.rtm.rail.util.RailPosition[] positions,double from,double to,Object cores) {
        railGroupId=group;logicalRailPositions=copy(logical);setRailPositions(copy(positions));sectionStartRatio=from;sectionEndRatio=to;var converted=new java.util.ArrayList<BlockPos>();
        if(cores instanceof Iterable<?> iterable)for(var core:iterable) {
            if(core instanceof BlockPos pos)converted.add(pos);
            else if(core instanceof int[] pos)converted.add(new BlockPos(pos[0],pos[1],pos[2]));
        }
        groupPositions=java.util.List.copyOf(converted);railmap=null;
    }
    private static jp.ngt.rtm.rail.util.RailPosition[] copy(jp.ngt.rtm.rail.util.RailPosition[] positions) {
        return java.util.Arrays.stream(positions).map(r -> jp.ngt.rtm.rail.util.RailPosition.readFromNBT(r.writeToNBT())).toArray(jp.ngt.rtm.rail.util.RailPosition[]::new);
    }
    public java.util.UUID getRailGroupId() {return railGroupId;}
    public java.util.List<int[]> getRailGroupCorePositions() {return groupPositions.stream().map(pos->new int[]{pos.getX(),pos.getY(),pos.getZ()}).toList();}
    @Override public boolean isSameLogicalRail(TileEntityLargeRailCore other) {
        return this==other || isRailSection() && other instanceof TileEntityLargeRailNormalCore normal && normal.isRailSection()
            && railGroupId.equals(normal.railGroupId) && groupPositions.contains(other.getBlockPos()) && normal.groupPositions.contains(getBlockPos());
    }
    @Override public jp.ngt.rtm.rail.util.RailPosition[] getLogicalRailPositions() {return isRailSection()?copy(logicalRailPositions):super.getLogicalRailPositions();}
    public TileEntityLargeRailNormalCore adjacentSection(boolean end) {
        if(level==null)return null;int index=groupPositions.indexOf(getBlockPos())+(end?1:-1);
        if(index<0 || index>=groupPositions.size())return null;
        BlockPos position=groupPositions.get(index);level.getChunk(position.getX()>>4,position.getZ()>>4);
        return level.getBlockEntity(position) instanceof TileEntityLargeRailNormalCore normal && isSameLogicalRail(normal)?normal:null;
    }
    @Override public void createRailMap() {
        if(!isRailSection()) {super.createRailMap();return;}
        if(!isLoaded())return;
        var source=new jp.ngt.rtm.rail.util.RailMapBasic(logicalRailPositions[0],logicalRailPositions[1],getFixRTMRailMapVersion());
        var positions=getRailPositions();railmap=new jp.kaiz.kaizpatch.rtm.rail.util.RailMapSection(source,positions[0],positions[1],sectionStartRatio,sectionEndRatio);invalidateCollisionCache();
    }
    @Override protected void writeRailData(net.minecraft.nbt.CompoundTag nbt) {super.writeRailData(nbt);writeSectionData(nbt);}
    public void writeSectionData(Object nbt) {writeSectionData(java.util.Objects.requireNonNull(jp.ngt.mccompat.nbt.NBTTagCompound.unwrap(nbt)));}
    public void writeSectionData(net.minecraft.nbt.CompoundTag nbt) {
        if(!isRailSection())return;
        var section=new net.minecraft.nbt.CompoundTag();section.putLong("GroupMost",railGroupId.getMostSignificantBits());section.putLong("GroupLeast",railGroupId.getLeastSignificantBits());
        section.putDouble("StartRatio",sectionStartRatio);section.putDouble("EndRatio",sectionEndRatio);
        section.put("LogicalStartRP",logicalRailPositions[0].writeToNBT());section.put("LogicalEndRP",logicalRailPositions[1].writeToNBT());
        var cores=new net.minecraft.nbt.ListTag();for(var pos:groupPositions) {var tag=new net.minecraft.nbt.CompoundTag();tag.putInt("X",pos.getX());tag.putInt("Y",pos.getY());tag.putInt("Z",pos.getZ());cores.add(tag);}
        section.put("CorePositions",cores);nbt.put("RailSection",section);
    }
    @Override protected void readRailData(net.minecraft.nbt.CompoundTag nbt) {super.readRailData(nbt);readSectionData(nbt);}
    public void readSectionData(Object nbt) {readSectionData(java.util.Objects.requireNonNull(jp.ngt.mccompat.nbt.NBTTagCompound.unwrap(nbt)));}
    public void readSectionData(net.minecraft.nbt.CompoundTag nbt) {
        railGroupId=null;logicalRailPositions=null;groupPositions=java.util.List.of();sectionStartRatio=0;sectionEndRatio=1;railmap=null;
        if(!nbt.contains("RailSection"))return;var section=nbt.getCompound("RailSection");
        for(String key:new String[]{"GroupMost","GroupLeast","StartRatio","EndRatio","LogicalStartRP","LogicalEndRP","CorePositions"})if(!section.contains(key))return;
        railGroupId=new java.util.UUID(section.getLong("GroupMost"),section.getLong("GroupLeast"));
        logicalRailPositions=new jp.ngt.rtm.rail.util.RailPosition[]{jp.ngt.rtm.rail.util.RailPosition.readFromNBT(section.getCompound("LogicalStartRP")),jp.ngt.rtm.rail.util.RailPosition.readFromNBT(section.getCompound("LogicalEndRP"))};
        sectionStartRatio=section.getDouble("StartRatio");sectionEndRatio=section.getDouble("EndRatio");
        var cores=section.getList("CorePositions",10);var positions=new java.util.ArrayList<BlockPos>();for(int i=0;i<cores.size();i++) {var tag=cores.getCompound(i);positions.add(new BlockPos(tag.getInt("X"),tag.getInt("Y"),tag.getInt("Z")));}groupPositions=java.util.List.copyOf(positions);
    }
    private java.util.List<TileEntityLargeRailNormalCore> groupCores() {
        if(level==null || !isRailSection())return java.util.List.of(this);
        var cores=new java.util.ArrayList<TileEntityLargeRailNormalCore>();
        for(var position:groupPositions) {level.getChunk(position.getX()>>4,position.getZ()>>4);if(level.getBlockEntity(position) instanceof TileEntityLargeRailNormalCore core && isSameLogicalRail(core))cores.add(core);}
        if(!cores.contains(this))cores.add(this);return cores;
    }
    @Override public void replaceRail(jp.ngt.rtm.rail.util.RailProperty property) {for(var core:groupCores())core.replaceLocal(property);}
    private void replaceLocal(jp.ngt.rtm.rail.util.RailProperty property) {super.replaceRail(property);}
    @Override public void addSubRail(jp.ngt.rtm.rail.util.RailProperty property) {for(var core:groupCores())core.addSubLocal(property);}
    private void addSubLocal(jp.ngt.rtm.rail.util.RailProperty property) {super.addSubRail(property);}
    @Override public void setSignal(int value) {for(var core:groupCores())core.setSignalLocal(value);}
    private void setSignalLocal(int value) {super.setSignal(value);}
    @Override public boolean isLogicalRailOccupied() {for(var core:groupCores())if(core.isCollidedTrain())return true;return false;}
    @Override public void breakLogicalRail() {
        if(!isRailSection() || level==null) {super.breakLogicalRail();return;}
        if(breaking)return;var cores=groupCores();for(var core:cores)core.breaking=true;
        var candidates=new java.util.LinkedHashSet<BlockPos>(groupPositions);
        var full=new jp.ngt.rtm.rail.util.RailMapBasic(logicalRailPositions[0],logicalRailPositions[1],getFixRTMRailMapVersion());
        for(var point:full.getRailBlockList(getProperty()))candidates.add(new BlockPos(point[0],point[1],point[2]));
        for(var core:cores) {var map=core.getRailMap(null);if(map!=null)for(var point:map.getRailBlockList(core.getProperty()))candidates.add(new BlockPos(point[0],point[1],point[2]));}
        var targets=new java.util.ArrayList<BlockPos>();
        for(var position:candidates)if(level.getBlockEntity(position) instanceof TileEntityLargeRailBase base) {var owner=base.getRailCore();if(owner!=null && isSameLogicalRail(owner))targets.add(position);}
        for(var position:targets)level.removeBlock(position,false);
    }

    @Override public int getRailRenderMinimumSplit() {return isRailSection()?1:0;}
    @Override public int getRailRenderEndOffset() {return isRailSection() && sectionEndRatio<1-1E-7?1:0;}
    @Override public boolean shouldRenderRailStartCap() {return !isRailSection() || sectionStartRatio<=1E-7;}
    @Override public boolean shouldRenderRailEndCap() {return !isRailSection() || sectionEndRatio>=1-1E-7;}

    @Override
    public String getRailShapeName() {
        RailMap map = this.getRailMap(null);
        if (map == null || map.getStartRP() == null || map.getEndRP() == null) {
            // ★レールを置いた直後は RailMap がまだ無い。
            // WAILA 系 MOD は見ているブロックへ毎tick getCloneItemStack を呼ぶので、
            // ここで落ちるとレール設置の瞬間にクライアントごと落ちる (実測)。
            return "Type:Normal";
        }
        if(isRailSection()) {
            var logical=getLogicalRailPositions();return "Type:Normal, X:"+(logical[1].blockX-logical[0].blockX)+", Y:"+(logical[1].blockY-logical[0].blockY)+", Z:"+(logical[1].blockZ-logical[0].blockZ);
        }
        return "Type:Normal, " +
                "X:" + (map.getEndRP().blockX - map.getStartRP().blockX) + ", " +
                "Y:" + (map.getEndRP().blockY - map.getStartRP().blockY) + ", " +
                "Z:" + (map.getEndRP().blockZ - map.getStartRP().blockZ);
    }
}
