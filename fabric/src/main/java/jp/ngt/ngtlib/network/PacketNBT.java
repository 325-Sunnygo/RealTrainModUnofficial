package jp.ngt.ngtlib.network;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.nbt.CompoundTag;
/** Legacy server-to-client tile snapshot, copied when constructed. */
public final class PacketNBT {
    public final BlockEntity tile;
    public final CompoundTag data;
    public final boolean toClient;
    public PacketNBT(Object tile,Object nbt,boolean toClient) {
        if(!(tile instanceof BlockEntity entity))throw new IllegalArgumentException("Expected block entity");
        CompoundTag tag=jp.ngt.mccompat.nbt.NBTTagCompound.unwrap(nbt);
        if(tag==null)throw new IllegalArgumentException("Expected compound NBT");
        this.tile=entity;this.data=tag.copy();this.toClient=toClient;
    }
}
