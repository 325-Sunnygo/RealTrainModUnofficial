package jp.ngt.rtm.entity.vehicle;

import jp.ngt.ngtlib.block.NGTObject;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

/**
 * ブロックから作った乗り物のモデル。本家 {@code jp.ngt.rtm.entity.vehicle.VehicleNGTO} の移植。
 */
public final class VehicleNGTO {
    /** 移動装置での一時保存時は null。 */
    @Nullable
    public final NGTObject ngto;
    public final float scale;
    public final float offsetX;
    public final float offsetY;
    public final float offsetZ;
    public float riderPosX;
    public float riderPosY;
    public float riderPosZ;
    /** 0=車 / 1=船 / 2=飛行機。 */
    public int type;

    public VehicleNGTO(@Nullable NGTObject par1, float par2, float par3, float par4, float par5) {
        this.ngto = par1;
        this.offsetX = par2;
        this.offsetY = par3;
        this.offsetZ = par4;
        this.scale = par5;
    }

    public CompoundTag writeToNBT() {
        CompoundTag nbt = new CompoundTag();
        nbt.putFloat("OffsetX", this.offsetX);
        nbt.putFloat("OffsetY", this.offsetY);
        nbt.putFloat("OffsetZ", this.offsetZ);
        nbt.putFloat("RiderPosX", this.riderPosX);
        nbt.putFloat("RiderPosY", this.riderPosY);
        nbt.putFloat("RiderPosZ", this.riderPosZ);
        nbt.putFloat("Scale", this.scale);
        nbt.putInt("Type", this.type);
        if (this.ngto != null) {
            nbt.put("NGTO", this.ngto.writeToNBT());
        }
        return nbt;
    }

    @Nullable
    public static VehicleNGTO readFromNBT(CompoundTag nbt, boolean allowNullNGTO) {
        float ox = nbt.getFloat("OffsetX");
        float oy = nbt.getFloat("OffsetY");
        float oz = nbt.getFloat("OffsetZ");
        float sc = nbt.getFloat("Scale");
        NGTObject ngto = null;
        if (nbt.contains("NGTO")) {
            ngto = NGTObject.readFromNBT(nbt.getCompound("NGTO"));
        }
        VehicleNGTO obj = new VehicleNGTO(ngto, ox, oy, oz, sc <= 0.0F ? 1.0F : sc);
        obj.riderPosX = nbt.getFloat("RiderPosX");
        obj.riderPosY = nbt.getFloat("RiderPosY");
        obj.riderPosZ = nbt.getFloat("RiderPosZ");
        obj.type = nbt.getInt("Type");
        return (allowNullNGTO || ngto != null) ? obj : null;
    }
}
