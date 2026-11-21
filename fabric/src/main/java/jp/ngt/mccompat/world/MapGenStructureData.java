package jp.ngt.mccompat.world;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.saveddata.SavedData;
/** The original concrete Features NBT container, backed by per-dimension SavedData. */
public final class MapGenStructureData extends SavedData {
    private final jp.ngt.mccompat.nbt.NBTTagCompound features;
    public MapGenStructureData(String name) {this(new CompoundTag());}
    private MapGenStructureData(CompoundTag features) {this.features=new jp.ngt.mccompat.nbt.NBTTagCompound(features);this.features.watch(this::setDirty);}
    public static Factory<MapGenStructureData> factory(String name) {return new Factory<>(()->new MapGenStructureData(name),(nbt,registries)->new MapGenStructureData(nbt.getCompound("Features")),null);}
    public jp.ngt.mccompat.nbt.NBTTagCompound func_143041_a() {return features;}
    public void func_76185_a() {setDirty();}
    @Override public CompoundTag save(CompoundTag nbt,HolderLookup.Provider registries) {nbt.put("Features",features.tag.copy());return nbt;}
}
