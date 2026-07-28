package com.portofino.realtrainmodunofficial.entity;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficialEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * エディタの選択範囲を持つエンティティ (neo mcte)。本家 MCTE {@code EntityEditor} の移植。
 *
 * <p>本家と同じく<b>選択範囲をエンティティとして世界に置く</b>方式。プレイヤーごとに 1 個で、
 * 範囲は {@code START_POS} / {@code END_POS} として同期される。エンティティにしてあるおかげで
 * 範囲の描画・保存・マルチプレイ同期をバニラの仕組みにそのまま乗せられる。
 *
 * <p>本家は当たり判定も動きも持たない不可視エンティティで、これも同じにしてある。
 */
public class EditorEntity extends Entity implements net.minecraft.world.Container {

    private static final EntityDataAccessor<String> OWNER =
        SynchedEntityData.defineId(EditorEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<BlockPos> START_POS =
        SynchedEntityData.defineId(EditorEntity.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<BlockPos> END_POS =
        SynchedEntityData.defineId(EditorEntity.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<BlockPos> PASTE_POS =
        SynchedEntityData.defineId(EditorEntity.class, EntityDataSerializers.BLOCK_POS);
    /** 本家 editMode: 0/1 = 選択中の点、2/3 = 表示のみ。 */
    private static final EntityDataAccessor<Byte> EDIT_MODE =
        SynchedEntityData.defineId(EditorEntity.class, EntityDataSerializers.BYTE);
    /** 範囲が確定しているか (2 点とも打たれたか)。 */
    private static final EntityDataAccessor<Boolean> HAS_END =
        SynchedEntityData.defineId(EditorEntity.class, EntityDataSerializers.BOOLEAN);

    /**
     * 埋めるブロック / 置換先ブロックのスロット (本家 {@code EntityEditor implements IInventory})。
     * <p>0 = Fill、1 = Replace。本家の {@code ContainerEditor} が 72,152 と 72,172 に置くもの。
     */
    private final net.minecraft.core.NonNullList<net.minecraft.world.item.ItemStack> items =
        net.minecraft.core.NonNullList.withSize(2, net.minecraft.world.item.ItemStack.EMPTY);

    public EditorEntity(EntityType<? extends EditorEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setInvulnerable(true);
    }

    public EditorEntity(Level level) {
        this(RealTrainModUnofficialEntities.EDITOR.get(), level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(OWNER, "");
        builder.define(START_POS, BlockPos.ZERO);
        builder.define(END_POS, BlockPos.ZERO);
        builder.define(PASTE_POS, BlockPos.ZERO);
        builder.define(EDIT_MODE, (byte) 0);
        builder.define(HAS_END, false);
    }

    // ---- 所有者 ----

    public void setOwner(Player player) {
        this.entityData.set(OWNER, player == null ? "" : player.getStringUUID());
    }

    public boolean isOwner(Player player) {
        String o = this.entityData.get(OWNER);
        return player != null && !o.isEmpty() && o.equals(player.getStringUUID());
    }

    // ---- 選択範囲 ----

    public BlockPos getStart() {
        return this.entityData.get(START_POS);
    }

    public BlockPos getEnd() {
        return this.entityData.get(END_POS);
    }

    public boolean hasEnd() {
        return this.entityData.get(HAS_END);
    }

    /** 1 点目を打つ。範囲は未確定に戻る。 */
    public void setStart(BlockPos pos) {
        this.entityData.set(START_POS, pos);
        this.entityData.set(HAS_END, false);
        snapTo(pos);
    }

    /**
     * 始点だけ動かす (終点と確定状態は保つ)。
     * <p>編集モード中に視点で範囲を伸ばすときに使う。{@link #setStart} だと
     * 動かすたびに範囲が未確定へ戻ってしまう。
     */
    public void setStartKeepEnd(BlockPos pos) {
        this.entityData.set(START_POS, pos);
        snapTo(pos);
    }

    /** 2 点目を打つ。範囲が確定する。 */
    public void setEnd(BlockPos pos) {
        this.entityData.set(END_POS, pos);
        this.entityData.set(HAS_END, true);
    }

    public BlockPos getPastePos() {
        return this.entityData.get(PASTE_POS);
    }

    public void setPastePos(BlockPos pos) {
        this.entityData.set(PASTE_POS, pos);
    }

    public byte getEditMode() {
        return this.entityData.get(EDIT_MODE);
    }

    public void setEditMode(byte mode) {
        this.entityData.set(EDIT_MODE, mode);
    }

    /** 選択範囲 (両端を含む)。未確定なら 1 点目だけの 1 ブロック。 */
    public AABB getSelectionBox() {
        BlockPos a = getStart();
        BlockPos b = hasEnd() ? getEnd() : a;
        return new AABB(
            Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()),
            Math.max(a.getX(), b.getX()) + 1.0D, Math.max(a.getY(), b.getY()) + 1.0D, Math.max(a.getZ(), b.getZ()) + 1.0D);
    }

    /** 選択範囲のブロック数。フィルタの負荷判定に使う。 */
    public long getVolume() {
        AABB box = getSelectionBox();
        return (long) (box.getXsize() * box.getYsize() * box.getZsize());
    }

    /**
     * 描画・当たり判定のためにエンティティ本体を範囲の近くへ置く。
     * <p>選択範囲から離れた場所にエンティティがいると、範囲だけ画面外扱いになって描画が消える。
     */
    private void snapTo(BlockPos pos) {
        this.setPos(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
    }

    // ---- Entity の作法 ----

    @Override
    public void tick() {
        //本家と同じく自前では動かない。所有者が居なくなったら消える。
        if (!level().isClientSide) {
            String owner = this.entityData.get(OWNER);
            if (owner.isEmpty()) {
                discard();
                return;
            }
            if (this.tickCount % 40 == 0 && level().getPlayerByUUID(java.util.UUID.fromString(owner)) == null) {
                discard();
            }
        }
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        //選択範囲は広いことがあるので、遠くても描く
        return distance < 256.0D * 256.0D;
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        //範囲全体を包む。これが無いと端に立ったとき枠が消える。
        return getSelectionBox().inflate(1.0D);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.entityData.set(OWNER, tag.getString("Owner"));
        this.entityData.set(START_POS, readPos(tag, "Start"));
        this.entityData.set(END_POS, readPos(tag, "End"));
        this.entityData.set(PASTE_POS, readPos(tag, "Paste"));
        this.entityData.set(EDIT_MODE, tag.getByte("EditMode"));
        this.entityData.set(HAS_END, tag.getBoolean("HasEnd"));
        if (tag.contains("Slots")) {
            net.minecraft.world.ContainerHelper.loadAllItems(
                tag.getCompound("Slots"), items, level().registryAccess());
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putString("Owner", this.entityData.get(OWNER));
        writePos(tag, "Start", getStart());
        writePos(tag, "End", getEnd());
        writePos(tag, "Paste", getPastePos());
        tag.putByte("EditMode", getEditMode());
        tag.putBoolean("HasEnd", hasEnd());
        net.minecraft.core.HolderLookup.Provider reg = level().registryAccess();
        CompoundTag slots = new CompoundTag();
        net.minecraft.world.ContainerHelper.saveAllItems(slots, items, reg);
        tag.put("Slots", slots);
    }

    private static BlockPos readPos(CompoundTag tag, String key) {
        if (!tag.contains(key)) {
            return BlockPos.ZERO;
        }
        int[] a = tag.getIntArray(key);
        return a.length == 3 ? new BlockPos(a[0], a[1], a[2]) : BlockPos.ZERO;
    }

    private static void writePos(CompoundTag tag, String key, BlockPos pos) {
        tag.putIntArray(key, new int[]{pos.getX(), pos.getY(), pos.getZ()});
    }

    // ---- Container (本家 EntityEditor の IInventory 実装に対応) ----

    public static final int SLOT_FILL = 0;
    public static final int SLOT_REPLACE = 1;

    @Override
    public int getContainerSize() {
        return items.size();
    }

    @Override
    public boolean isEmpty() {
        return items.stream().allMatch(net.minecraft.world.item.ItemStack::isEmpty);
    }

    @Override
    public net.minecraft.world.item.ItemStack getItem(int slot) {
        return slot >= 0 && slot < items.size() ? items.get(slot) : net.minecraft.world.item.ItemStack.EMPTY;
    }

    @Override
    public net.minecraft.world.item.ItemStack removeItem(int slot, int amount) {
        return net.minecraft.world.ContainerHelper.removeItem(items, slot, amount);
    }

    @Override
    public net.minecraft.world.item.ItemStack removeItemNoUpdate(int slot) {
        return net.minecraft.world.ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, net.minecraft.world.item.ItemStack stack) {
        if (slot >= 0 && slot < items.size()) {
            items.set(slot, stack);
        }
    }

    @Override
    public void setChanged() {
    }

    @Override
    public boolean stillValid(Player player) {
        //本家 ContainerEditor.canInteractWith: エディタから 64 以内
        return isOwner(player) && player.distanceToSqr(this) <= 64.0D * 64.0D;
    }

    @Override
    public void clearContent() {
        items.clear();
    }

    /** スロットに入っているブロック。無ければ null。 */
    public net.minecraft.world.level.block.state.BlockState slotBlock(int slot) {
        net.minecraft.world.item.ItemStack stack = getItem(slot);
        if (stack.getItem() instanceof net.minecraft.world.item.BlockItem bi) {
            return bi.getBlock().defaultBlockState();
        }
        return null;
    }
}
