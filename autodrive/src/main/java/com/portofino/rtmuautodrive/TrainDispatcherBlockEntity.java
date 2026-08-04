package com.portofino.rtmuautodrive;

import com.portofino.realtrainmodunofficial.formation.FormationSpawner;
import com.portofino.realtrainmodunofficial.formation.TrainFormation;
import com.portofino.realtrainmodunofficial.formation.TrainFormationData;
import jp.ngt.rtm.entity.train.EntityTrainBase;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * 列車運転スポナー。
 *
 * <p>編成アイテムを 1 つ入れておき、自動運転装置の一覧から「発車」を押すと、
 * 近くのレールにその編成をスポーンさせて自動運転を始める。
 */
public class TrainDispatcherBlockEntity extends BlockEntity implements Container, net.minecraft.world.MenuProvider {

    /** レールを探す範囲 (ブロック)。ユーザー指定で 5。 */
    public static final int RAIL_SEARCH = 5;

    /** 表示名 (自動運転装置の一覧に出る)。 */
    private String dispatcherName = "";
    /** 編成アイテム 1 つ分。 */
    private final NonNullList<ItemStack> items = NonNullList.withSize(1, ItemStack.EMPTY);
    /** 設置したときのプレイヤーの向き。発車する向きを決めるのに使う。 */
    private float launchYaw;
    /**
     * 駅ごとの停車 (true) / 通過 (false)。載っていない駅は<b>停車</b>扱い。
     * キーは駅列車ブロックの座標。
     */
    private final java.util.Map<BlockPos, Boolean> stopping = new java.util.LinkedHashMap<>();
    /**
     * 駅ごとのドアの開く側。0=両側 / 1=左 / 2=右。載っていない駅は両側。
     * <b>駅ではなく運転 (スポナー) 側に持つ</b>ので、同じホームでも運転ごとに変えられる。
     */
    private final java.util.Map<BlockPos, Integer> doors = new java.util.LinkedHashMap<>();
    /** 方向幕の番号 (RTM の State_Destination)。0 = 既定。 */
    private int rollsign;
    /** 各駅での停車時間 (秒)。 */
    private int dwellSeconds = 10;

    public TrainDispatcherBlockEntity(BlockPos pos, BlockState state) {
        super(AutoDriveRegistry.dispatcherBlockEntityType(), pos, state);
    }

    // ---- 名前 ----

    public String getDispatcherName() {
        return this.dispatcherName;
    }

    public void setDispatcherName(String name) {
        this.dispatcherName = name == null ? "" : name;
        this.setChanged();
        if (this.level instanceof ServerLevel server) {
            DispatcherRegistry.get(server).setName(this.worldPosition, this.dispatcherName);
            server.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }

    public float getLaunchYaw() {
        return this.launchYaw;
    }

    public void setLaunchYaw(float yaw) {
        this.launchYaw = yaw;
        this.setChanged();
    }

    /** 一覧に出す名前。未設定なら座標で代用する。 */
    public String displayName() {
        return this.dispatcherName.isBlank()
                ? this.worldPosition.getX() + ", " + this.worldPosition.getY() + ", " + this.worldPosition.getZ()
                : this.dispatcherName;
    }

    public int getRollsign() {
        return this.rollsign;
    }

    public int getDwellSeconds() {
        return this.dwellSeconds;
    }

    /** 方向幕番号と停車時間をまとめて設定する。 */
    public void setConfig(int rollsign, int dwellSeconds) {
        this.rollsign = Math.max(0, Math.min(127, rollsign));
        this.dwellSeconds = Math.max(0, Math.min(600, dwellSeconds));
        this.setChanged();
        if (this.level instanceof ServerLevel server) {
            server.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }

    // ---- 停車 / 通過 ----

    /** その駅に停まるか。設定していなければ停車。 */
    public boolean isStopping(BlockPos station) {
        return this.stopping.getOrDefault(station, Boolean.TRUE);
    }

    public void setStopping(BlockPos station, boolean stop) {
        this.stopping.put(station.immutable(), stop);
        this.setChanged();
    }

    /** その駅でのドアの開く側 (0=両側 / 1=左 / 2=右)。 */
    public int doorSide(BlockPos station) {
        return this.doors.getOrDefault(station, 0);
    }

    public void setDoorSide(BlockPos station, int side) {
        this.doors.put(station.immutable(), side < 0 || side > 2 ? 0 : side);
        this.setChanged();
    }

    /** レールで繋がっている駅を順番に返す (繋がっていない駅は出さない)。 */
    public java.util.List<RailRoute.Stop> route(ServerLevel level) {
        BlockPos rail = findRail(level, this.worldPosition);
        return rail == null ? java.util.List.of() : RailRoute.scan(level, rail, this.launchYaw);
    }

    /** 実際に停まる駅だけを順番に並べたもの (自動運転へ渡す)。ドア設定込み。 */
    public java.util.List<AutoDriveState.RouteStop> stopPositions(ServerLevel level) {
        java.util.List<AutoDriveState.RouteStop> list = new java.util.ArrayList<>();
        for (RailRoute.Stop stop : this.route(level)) {
            if (this.isStopping(stop.pos())) {
                list.add(new AutoDriveState.RouteStop(stop.pos(), this.doorSide(stop.pos())));
            }
        }
        return list;
    }

    // ---- 発車 ----

    /** 発車できる状態か (編成アイテムが入っていて、近くにレールがある)。 */
    public boolean canLaunch() {
        return TrainFormationData.hasFormation(this.items.get(0))
                && this.level != null && findRail(this.level, this.worldPosition) != null;
    }

    /**
     * 発車させる。
     *
     * @return 失敗した理由 (成功なら {@link FormationSpawner.Result#OK})
     */
    public FormationSpawner.Result launch(ServerLevel level) {
        ItemStack stack = this.items.get(0);
        TrainFormation formation = TrainFormationData.getFormation(stack);
        if (formation == null || formation.isEmpty()) {
            return FormationSpawner.Result.EMPTY;
        }
        BlockPos rail = findRail(level, this.worldPosition);
        if (rail == null) {
            return FormationSpawner.Result.NO_RAIL;
        }
        List<String> ids = formation.getAllVehicles();
        FormationSpawner.Result result = FormationSpawner.spawn(level, rail, this.launchYaw, ids);
        if (result != FormationSpawner.Result.OK) {
            return result;
        }
        //置いたばかりの編成を拾って自動運転に入れる
        AABB area = new AABB(rail).inflate(16.0D, 6.0D, 16.0D);
        EntityTrainBase lead = null;
        double best = Double.MAX_VALUE;
        for (EntityTrainBase train : level.getEntitiesOfClass(EntityTrainBase.class, area)) {
            double d = train.distanceToSqr(rail.getX() + 0.5D, rail.getY() + 0.5D, rail.getZ() + 0.5D);
            if (d < best) {
                best = d;
                lead = train;
            }
        }
        if (lead != null) {
            AutoDriveState.get(level).enable(level, lead, this.stopPositions(level),
                    this.dwellSeconds * 20, this.rollsign);
        }
        return FormationSpawner.Result.OK;
    }

    /**
     * まわりのレールを探す。ユーザー指定で <b>5 ブロック以内</b>。
     * 近い順に見て、最初に「レールとして引ける」座標を返す。
     */
    public static BlockPos findRail(Level level, BlockPos origin) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int dx = -RAIL_SEARCH; dx <= RAIL_SEARCH; dx++) {
            for (int dz = -RAIL_SEARCH; dz <= RAIL_SEARCH; dz++) {
                for (int dy = -RAIL_SEARCH; dy <= RAIL_SEARCH; dy++) {
                    BlockPos pos = origin.offset(dx, dy, dz);
                    if (jp.ngt.rtm.rail.TileEntityLargeRailBase.getRailMapFromCoordinates(
                            level, null, pos.getX(), pos.getY(), pos.getZ()) == null) {
                        continue;
                    }
                    double d = origin.distSqr(pos);
                    if (d < bestDistance) {
                        bestDistance = d;
                        best = pos;
                    }
                }
            }
        }
        return best;
    }

    // ---- Container (編成アイテム 1 枠) ----

    @Override
    public int getContainerSize() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        return this.items.get(0).isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        return this.items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        ItemStack stack = ContainerHelper.removeItem(this.items, slot, count);
        this.setChanged();
        return stack;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(this.items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        this.items.set(slot, stack);
        this.setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return this.level != null
                && this.level.getBlockEntity(this.worldPosition) == this
                && player.distanceToSqr(this.worldPosition.getCenter()) <= 64.0D;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return stack.getItem() instanceof com.portofino.realtrainmodunofficial.item.FormationItem;
    }

    @Override
    public void clearContent() {
        this.items.clear();
    }

    // ---- MenuProvider ----

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.rtmuautodrive.train_dispatcher");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new DispatcherMenu(id, inventory, this);
    }

    // ---- 保存 ----

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        this.dispatcherName = tag.getString("Name");
        this.launchYaw = tag.getFloat("LaunchYaw");
        this.rollsign = tag.getInt("Rollsign");
        this.dwellSeconds = tag.contains("Dwell") ? tag.getInt("Dwell") : 10;
        this.stopping.clear();
        net.minecraft.nbt.ListTag stops = tag.getList("Stops", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = 0; i < stops.size(); i++) {
            CompoundTag c = stops.getCompound(i);
            BlockPos sp = BlockPos.of(c.getLong("Pos"));
            this.stopping.put(sp, c.getBoolean("Stop"));
            this.doors.put(sp, c.getInt("Door"));
        }
        this.items.clear();
        ContainerHelper.loadAllItems(tag, this.items, provider);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        tag.putString("Name", this.dispatcherName);
        tag.putFloat("LaunchYaw", this.launchYaw);
        tag.putInt("Rollsign", this.rollsign);
        tag.putInt("Dwell", this.dwellSeconds);
        net.minecraft.nbt.ListTag stops = new net.minecraft.nbt.ListTag();
        this.stopping.forEach((pos, stop) -> {
            CompoundTag c = new CompoundTag();
            c.putLong("Pos", pos.asLong());
            c.putBoolean("Stop", stop);
            c.putInt("Door", this.doorSide(pos));
            stops.add(c);
        });
        tag.put("Stops", stops);
        ContainerHelper.saveAllItems(tag, this.items, provider);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        this.saveAdditional(tag, provider);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** ワールドへ読み込まれたら一覧へ登録し直す (保険)。 */
    public void registerSelf() {
        if (this.level instanceof ServerLevel server) {
            DispatcherRegistry.get(server).setName(this.worldPosition, this.dispatcherName);
        }
    }
}
