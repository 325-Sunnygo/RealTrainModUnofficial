package jp.kaiz.atsassistmod.block.tileentity;

import jp.kaiz.atsassistmod.ATSAssistCore;
import jp.kaiz.atsassistmod.ifttt.IFTTTContainer;
import jp.ngt.rtm.entity.train.EntityTrainBase;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * 本家 jp.kaiz.atsassistmod.block.tileentity.TileEntityIFTTT の移植。
 * 毎 tick ブロック上方 (x-1,y,z-1)〜(x+2,y+4,z+2) の列車を検知し、
 * This 条件 (allMatch / anyMatch) が成立したら That を実行、
 * 不成立へ変わったら finish + RS出力 0 (本家仕様)。
 */
public class IFTTTBlockEntity extends BlockEntity {

    public static final int MAX_LIST = 6;

    private final List<IFTTTContainer> thisList = new ArrayList<>();
    private final List<IFTTTContainer> thatList = new ArrayList<>();
    private boolean anyMatch;

    /** 前 tick で条件成立していたか (本家 notFirst)。保存しない。 */
    private boolean notFirst;
    private int redStoneOutput;

    public IFTTTBlockEntity(BlockPos pos, BlockState state) {
        super(ATSAssistCore.IFTTT_BE.get(), pos, state);
    }

    //------------------------------------------------------------ tick (本家 updateEntity)

    public static void serverTick(Level level, BlockPos pos, BlockState state, IFTTTBlockEntity tile) {
        //検知範囲は本家準拠: (x-1, y, z-1) → (x+2, y+4, z+2)
        AABB box = new AABB(
                pos.getX() - 1, pos.getY(), pos.getZ() - 1,
                pos.getX() + 2, pos.getY() + 4, pos.getZ() + 2);
        List<EntityTrainBase> trains = level.getEntitiesOfClass(EntityTrainBase.class, box);
        EntityTrainBase train = trains.isEmpty() ? null : trains.get(0);

        boolean match = tile.anyMatch
                ? tile.thisList.stream().anyMatch(c -> ((IFTTTContainer.This) c).isCondition(tile, train))
                : tile.thisList.stream().allMatch(c -> ((IFTTTContainer.This) c).isCondition(tile, train));

        if (match) {
            boolean first = !tile.notFirst;
            tile.thatList.forEach(c -> ((IFTTTContainer.That) c).doThat(tile, train, first));
            tile.notFirst = true;
        } else if (tile.notFirst) {
            tile.thatList.forEach(c -> ((IFTTTContainer.That) c).finish(tile, train));
            tile.setRedStoneOutput(0);
            tile.notFirst = false;
        }
    }

    //------------------------------------------------------------ リスト管理 (本家 add/set/removeIFTTT)

    public List<IFTTTContainer> getThisList() {
        return this.thisList;
    }

    public List<IFTTTContainer> getThatList() {
        return this.thatList;
    }

    public void addIFTTT(IFTTTContainer container) {
        if (container == null) {
            return;
        }
        List<IFTTTContainer> list = container instanceof IFTTTContainer.This ? this.thisList : this.thatList;
        if (list.size() < MAX_LIST) {
            list.add(container);
        }
    }

    public void setIFTTT(IFTTTContainer container, int index) {
        if (container == null) {
            return;
        }
        List<IFTTTContainer> list = container instanceof IFTTTContainer.This ? this.thisList : this.thatList;
        if (index >= 0 && index < list.size()) {
            list.set(index, container);
        } else {
            this.addIFTTT(container);
        }
    }

    public void removeIFTTT(IFTTTContainer container, int index) {
        if (container == null) {
            return;
        }
        List<IFTTTContainer> list = container instanceof IFTTTContainer.This ? this.thisList : this.thatList;
        if (index >= 0 && index < list.size()) {
            list.remove(index);
        }
    }

    public boolean isAnyMatch() {
        return this.anyMatch;
    }

    public void setAnyMatch(boolean anyMatch) {
        this.anyMatch = anyMatch;
    }

    //------------------------------------------------------------ RS出力

    public int getRedStoneOutput() {
        return this.redStoneOutput;
    }

    public void setRedStoneOutput(int value) {
        if (this.redStoneOutput != value) {
            this.redStoneOutput = value;
            if (this.level != null) {
                //コンパレータ・隣接ブロックへ通知 (本家 setRedStoneOutput)
                this.level.updateNeighbourForOutputSignal(this.worldPosition, this.getBlockState().getBlock());
                this.level.updateNeighborsAt(this.worldPosition, this.getBlockState().getBlock());
            }
        }
    }

    /** 変更をディスクとクライアント両方に反映する。 */
    public void setChangedAndSync() {
        this.setChanged();
        if (this.level != null) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }

    //------------------------------------------------------------ NBT

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.anyMatch = tag.getBoolean("AnyMatch");
        this.thisList.clear();
        this.thatList.clear();
        readList(tag.getList("ThisList", Tag.TAG_COMPOUND), this.thisList);
        readList(tag.getList("ThatList", Tag.TAG_COMPOUND), this.thatList);
    }

    private static void readList(ListTag listTag, List<IFTTTContainer> out) {
        for (int i = 0; i < listTag.size() && out.size() < MAX_LIST; i++) {
            IFTTTContainer container = IFTTTContainer.fromNbt(listTag.getCompound(i));
            if (container != null) {
                out.add(container);
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("AnyMatch", this.anyMatch);
        tag.put("ThisList", writeList(this.thisList));
        tag.put("ThatList", writeList(this.thatList));
    }

    private static ListTag writeList(List<IFTTTContainer> list) {
        ListTag listTag = new ListTag();
        for (IFTTTContainer container : list) {
            listTag.add(container.toNbt());
        }
        return listTag;
    }

    //------------------------------------------------------------ クライアント同期 (GUI 表示用)

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveWithoutMetadata(registries);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
