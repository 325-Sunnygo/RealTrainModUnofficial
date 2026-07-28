package jp.ngt.rtm.entity.train.util;

import jp.ngt.ngtlib.util.NGTUtil;
import jp.ngt.rtm.entity.train.EntityBogie;
import jp.ngt.rtm.entity.train.EntityTrainBase;
import jp.ngt.rtm.entity.train.util.TrainState.TrainStateType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * 本家 jp.ngt.rtm.entity.train.util.Formation (KaizPatchX) の忠実移植。
 * 編成の管理, ServerOnly。
 * TODO: PacketFormation 相当のクライアント同期 (sendPacket は現状 no-op)。
 */
public class Formation {
    public final long id;
    public FormationEntry[] entries;

    final boolean isRemote;
    private EntityTrainBase controlCar;
    private byte direction;
    private float speed;

    /**
     * @param par1 ID
     * @param par2 両数
     */
    public Formation(long par1, int par2) {
        this(par1, par2, false);
    }

    public Formation(long par1, int par2, boolean remote) {
        this.id = par1;
        this.entries = new FormationEntry[par2];
        this.isRemote = remote;
        FormationManager.getInstance(remote).setFormation(par1, this);
    }

    public static Formation readFromNBT(CompoundTag nbt, Level world, boolean withEntries) {
        long fid = nbt.getLong("FormationId");
        int num = nbt.getInt("Size");
        Formation formation = new Formation(fid, num, world.isClientSide);
        formation.direction = nbt.getByte("Direction");

        if (withEntries) {
            ListTag tagList = nbt.getList("Entries", 10);
            IntStream.range(0, tagList.size()).forEach(i -> {
                CompoundTag tag = tagList.getCompound(i);
                FormationEntry entry = FormationEntry.readFromNBT(tag, world);
                if (entry != null) {
                    formation.setEntry(entry, i);
                    entry.train.setFormation(formation);
                }
            });
        }

        return formation;
    }

    public void writeToNBT(CompoundTag nbt, boolean withEntries) {
        nbt.putLong("FormationId", this.id);
        nbt.putInt("Size", this.entries.length);
        nbt.putByte("Direction", this.direction);

        if (withEntries) {
            ListTag tagList = new ListTag();
            this.getFormationEntryStream().forEach(entry -> {
                CompoundTag tag = new CompoundTag();
                entry.writeToNBT(tag);
                tagList.add(tag);
            });
            nbt.put("Entries", tagList);
        }
    }

    /**
     * @return 両数
     */
    public int size() {
        return this.entries.length;
    }

    public FormationEntry get(int par1) {
        return this.entries[par1];
    }

    public Stream<EntityTrainBase> getTrainStream() {
        return this.getFormationEntryStream().map(entry -> entry.train);
    }

    public Stream<FormationEntry> getFormationEntryStream() {
        return Arrays.stream(this.entries).filter(Objects::nonNull);
    }

    private void setEntry(FormationEntry entry, int par2) {
        this.entries[par2] = entry;
    }

    public FormationEntry getEntry(EntityTrainBase par1) {
        return this.getFormationEntryStream().filter(entry -> par1.equals(entry.train)).findFirst().orElse(null);
    }

    /**
     * 編成に車両を登録
     *
     * @param par3 車両の位置
     * @param par5 向き
     */
    public void setTrain(EntityTrainBase par1, int par3, int par5) {
        this.setEntry(new FormationEntry(par1, par3, par5), par3);
        this.sendPacket();
    }

    public void setFormationData(EntityTrainBase par1, byte par3, byte par5) {
        FormationEntry entry = this.getEntry(par1);
        if (entry == null) {
            this.setTrain(par1, par3, par5);
        } else {
            entry.entryId = par3;
            entry.dir = par5;
        }
    }

    /**
     * 車両番号再振り分け
     */
    private void reallocation() {
        int i = 0;
        for (FormationEntry entry : this.entries) {
            if (entry != null) {
                entry.updateFormationData(this, i);
            }
            ++i;
        }
        this.sendPacket();
    }

    /**
     * 編成を反転
     */
    private void reverse() {
        NGTUtil.reverse(this.entries);
        //向きを反転
        this.getFormationEntryStream().forEach(entry -> entry.dir ^= 1);
    }

    private void addAll(FormationEntry[] par1) {
        List<FormationEntry> list = new ArrayList<>();
        NGTUtil.addArray(list, this.entries);
        NGTUtil.addArray(list, par1);
        this.entries = list.toArray(new FormationEntry[0]);
    }

    private void trim(int start, int end) {
        FormationEntry[] array = new FormationEntry[end - start + 1];
        if (end + 1 - start >= 0) System.arraycopy(this.entries, start, array, 0, end + 1 - start);
        this.entries = array;
    }

    /**
     * @param par1 連結される車両
     * @param par2 連結対象の車両
     * @param par3 連結される車両の向き
     * @param par4 連結対象の車両の向き
     * @param par5 連結対象の編成
     */
    public void connectTrain(EntityTrainBase par1, EntityTrainBase par2, int par3, int par4, Formation par5) {
        FormationEntry entry = this.getEntry(par1);
        if (entry == null) {
            return;
        }

        boolean flag0 = (par3 == entry.dir);
        if (flag0) {
            this.reverse();
        }

        entry = par5.getEntry(par2);
        if (entry == null) {
            return;
        }

        flag0 = (par4 == entry.dir);
        if (!flag0) {
            par5.reverse();
        }

        this.addAll(par5.entries);
        this.reallocation();

        this.setSpeed(0.0f);

        this.getTrainStream().forEach(train -> {
            train.setEBNotch();
            train.setSpeed(0.0F);
            train.setTrainStateData(TrainStateType.State_Direction.id, TrainState.Direction_Center.data);
        });

        FormationManager.getInstance(this.isRemote).removeFormation(par5.id);
    }

    /**
     * 車両を編成から除去<br>
     * ※Server Only
     */
    public void onRemovedTrain(EntityTrainBase par1) {
        //1両編成の時
        if (this.entries.length <= 1) {
            FormationManager.getInstance(this.isRemote).removeFormation(this.id);
            return;
        }

        FormationEntry entry = this.getEntry(par1);
        if (entry == null) {
            return;
        }

        if (entry.entryId == 0) {
            this.trim(1, this.entries.length - 1);
        } else if (entry.entryId == this.entries.length - 1) {
            this.trim(0, this.entries.length - 2);
        } else {
            int size = this.entries.length - entry.entryId - 1;
            Formation formation = new Formation(FormationManager.getInstance(this.isRemote).getNewFormationId(), size, this.isRemote);

            IntStream.range(entry.entryId + 1, this.entries.length).forEach(i -> formation.setEntry(this.entries[i], i - (entry.entryId + 1)));

            this.trim(0, entry.entryId - 1);
            formation.reallocation();
        }

        this.reallocation();
    }

    /**
     * バール右クリックで連結解除時
     */
    public void onDisconnectedTrain(EntityTrainBase par1, int par2) {
        FormationEntry entry = this.getEntry(par1);
        if (entry == null) {
            return;
        }

        boolean b0 = (par2 == entry.dir);//true:切る向きが前
        int i0 = b0 ? entry.entryId : entry.entryId + 1;
        int size = this.entries.length - i0;
        Formation formation = new Formation(FormationManager.getInstance(this.isRemote).getNewFormationId(), size, this.isRemote);

        IntStream.range(i0, this.entries.length).forEach(i -> formation.setEntry(this.entries[i], i - i0));
        formation.reallocation();

        this.trim(0, i0 - 1);
        this.reallocation();
    }

    private EntityTrainBase getControlCar() {
        if (this.controlCar == null || !this.controlCar.isControlCar()) {
            this.controlCar = this.getTrainStream().filter(EntityTrainBase::isControlCar).findFirst().orElse(this.controlCar);
        }
        return this.controlCar;
    }

    public int getNotch() {
        return this.getControlCar() == null ? 0 : this.getControlCar().getNotch();
    }

    public void setSpeed(float par1) {
        if (par1 == this.speed) {
            return;
        }

        if (this.entries != null) {
            this.getTrainStream().forEach(train -> train.setSpeed_NoSync(par1));
        }
        this.speed = par1;
    }

    public void setTrainDirection(byte par1, EntityTrainBase par2) {
        if (par2.getTrainDirection() != par1) {
            this.setSpeed(-par2.getSpeed());
        }

        FormationEntry entry = this.getEntry(par2);
        if (entry == null) {
            return;
        }

        this.direction = (byte) (par1 ^ entry.dir);//編成としての向き,XOR

        this.getFormationEntryStream().forEach(entry2 -> {
            byte b0 = (byte) (this.direction ^ entry2.dir);
            entry2.train.setTrainDirection_NoSync(b0);
        });
    }

    public void setTrainStateData(int id, byte data, EntityTrainBase par2) {
        if (id == TrainStateType.State_Direction.id)//向き
        {
            if (data == TrainState.Direction_Front.data || data == TrainState.Direction_Back.data) {
                this.controlCar = par2;
                par2.setTrainStateData_NoSync(id, (par2.getCabDirection() == par2.getTrainDirection()) ? data : (byte) (data ^ 2));
                par2.setTrainDirection(par2.getCabDirection());
            }

            this.getTrainStream().forEach(train -> {
                if (par2.equals(train)) {
                    train.setTrainStateData_NoSync(id, data);
                } else if (train.getTrainStateData(TrainStateType.State_Direction.id) != TrainState.Direction_Center.data) {
                    train.setTrainStateData_NoSync(id, TrainState.Direction_Center.data);
                }
            });
        } else if (id == TrainStateType.State_Door.id)//ドア
        {
            int stateR = data & 1;
            int stateL = (data >> 1) & 1;
            //data は「操作した車両 (par2) 自身の物理左右」で来る。編成内の物理向き
            //(FormationEntry.dir) が par2 と違う車両だけ左右を反転させれば、編成全体で
            //同じ物理側のドアが開く (本家は getTrainDirection を基準に同じ事をしている)。
            //旧実装は dir==0 を基準にしていたため、逆向き車 (dir==1) に乗ると
            //par2 自身の値まで反転されて保存され、次のトグルで別のビットが立って
            //「閉めたのに反対側が開く (両開き)」になっていた。
            FormationEntry control = this.getEntry(par2);
            int refDir = (control == null) ? 0 : control.dir;
            for (FormationEntry e : this.entries) {
                if (e == null || e.train == null) {
                    continue;
                }
                int data2 = (e.dir == refDir) ? (stateL << 1 | stateR) : (stateR << 1 | stateL);
                e.train.setTrainStateData_NoSync(id, (byte) data2);
            }
        } else {
            this.getTrainStream().forEach(train -> train.setTrainStateData_NoSync(id, data));
        }
    }

    public boolean containBogie(EntityBogie bogie) {
        return this.getTrainStream().anyMatch(train -> train.getBogie(0) == bogie || train.getBogie(1) == bogie);
    }

    public void sendPacket() {
        //TODO PacketFormation 移植 (クライアントへの編成同期)
    }

    public boolean isFrontCar(EntityTrainBase train) {
        EntityTrainBase front = null;
        FormationEntry fe;
        if (this.direction == 0) {
            if ((fe = this.entries[0]) != null) {
                front = fe.train;
            }
        } else {
            if ((fe = this.entries[this.entries.length - 1]) != null) {
                front = fe.train;
            }
        }
        return train.equals(front);
    }

    public void updateTrainMovement() {
        EntityTrainBase prevTrain = null;
        for (int i = 0; i < this.entries.length; i++) {
            int index = (this.direction == 0) ? i : (this.entries.length - i - 1);
            if (this.entries[index] != null) {
                EntityTrainBase train = (this.entries[index]).train;
                if (train.existBogies()) {
                    train.bogieController.moveTrainWithBogie(train, prevTrain, this.speed, false);
                }
                prevTrain = train;
            }
        }
    }
}
