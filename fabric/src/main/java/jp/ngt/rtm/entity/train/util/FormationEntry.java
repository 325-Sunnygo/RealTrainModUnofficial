package jp.ngt.rtm.entity.train.util;

import jp.ngt.rtm.entity.train.EntityTrainBase;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/**
 * 本家 jp.ngt.rtm.entity.train.util.FormationEntry (KaizPatchX) の忠実移植。
 */
public class FormationEntry implements Comparable<FormationEntry> {
    public final EntityTrainBase train;
    public byte entryId;
    public byte dir;

    /**
     * @param par1 車両
     * @param par2 位置
     * @param par3 向き
     */
    public FormationEntry(EntityTrainBase par1, int par2, int par3) {
        this.train = par1;
        this.entryId = (byte) par2;
        this.dir = (byte) par3;
    }

    public static FormationEntry readFromNBT(CompoundTag nbt, Level world) {
        int trainId = nbt.getInt("TrainId");
        byte pos = nbt.getByte("EntryPos");
        byte dir = nbt.getByte("EntryDir");
        Entity entity = world.getEntity(trainId);
        if (!(entity instanceof EntityTrainBase train)) {
            return null;
        }
        return new FormationEntry(train, pos, dir);
    }

    public void writeToNBT(CompoundTag nbt) {
        nbt.putInt("TrainId", this.train.getId());
        nbt.putByte("EntryPos", this.entryId);
        nbt.putByte("EntryDir", this.dir);
    }

    /**
     * 編成データ更新(Entity側も)
     */
    public void updateFormationData(Formation par1, int i) {
        this.entryId = (byte) i;
        this.train.setFormation(par1);
    }

    @Override
    public int compareTo(FormationEntry obj) {
        return this.entryId - obj.entryId;
    }
}
