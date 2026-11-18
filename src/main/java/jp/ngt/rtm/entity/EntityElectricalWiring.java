package jp.ngt.rtm.entity;

import jp.ngt.rtm.electric.Connection;
import jp.ngt.rtm.electric.Connection.ConnectionType;
import jp.ngt.rtm.electric.IProvideElectricity;
import jp.ngt.rtm.electric.WireManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;

/** 本家 jp.ngt.rtm.electric.EntityElectricalWiring の 1… */
public abstract class EntityElectricalWiring extends EntityInstalledObject implements IProvideElectricity {

    private static final EntityDataAccessor<String> DATA_CONNECTIONS =
            SynchedEntityData.defineId(EntityElectricalWiring.class, EntityDataSerializers.STRING);

    /** 本家 TileEntityElectricalWiring.connections。 */
    private final List<Connection> connections = new ArrayList<>();
    private boolean wiringRegistered;

    protected EntityElectricalWiring(EntityType<?> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_CONNECTIONS, "");
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (key == DATA_CONNECTIONS) {
            this.readSyncedConnections(this.entityData.get(DATA_CONNECTIONS));
        }
    }

    /** 本家 getConnectionList。描画・伝播の両方が使う。 */
    public List<Connection> getConnectionList() {
        return this.connections;
    }

    /** 本家 getConnection。 */
    public Connection getConnection(int x, int y, int z) {
        return this.connections.stream()
                .filter(c -> c.x == x && c.y == y && c.z == z)
                .findFirst().orElse(null);
    }

    /** 本家 setConnectionTo: 自分を root として接続を張る。 */
    public boolean setConnectionTo(int x, int y, int z, ConnectionType type, String wireName) {
        BlockPos target = new BlockPos(x, y, z);
        ConnectionType connectionType = type == null ? ConnectionType.NONE : type;
        this.connections.removeIf(c -> c.x == x && c.y == y && c.z == z);

        if (connectionType == ConnectionType.NONE) {
            this.removeReciprocal(target);
                this.syncConnections();
            if (!this.level().isClientSide()) {
                WireManager.unregister(this.level(), this.blockPosition(), target);
            }
            return true;
        }

        this.connections.add(new Connection(true, x, y, z, connectionType,
                wireName == null ? "" : wireName));
        this.addReciprocal(target, connectionType, wireName);
        this.syncConnections();
        if (!this.level().isClientSide()
                && (connectionType == ConnectionType.WIRE || connectionType == ConnectionType.TO_ENTITY)) {
            WireManager.register(this.level(), this.blockPosition(), target);
        }
        this.wiringRegistered = true;
        return true;
    }

    /** 本家 setConnectionFrom: 相手側 (root でない) の接続を足す。 */
    public void setConnectionFrom(int x, int y, int z, ConnectionType type, String wireName) {
        this.connections.removeIf(c -> c.x == x && c.y == y && c.z == z);
        this.connections.add(new Connection(false, x, y, z, type, wireName == null ? "" : wireName));
        this.syncConnections();
    }

    private void addReciprocal(BlockPos target, ConnectionType type, String wireName) {
        BlockEntity be = this.level().getBlockEntity(target);
        if (be instanceof com.portofino.realtrainmodunofficial.blockentity.InstalledObjectBlockEntity io) {
            io.setConnectionFrom(this.blockPosition().getX(), this.blockPosition().getY(),
                    this.blockPosition().getZ(), type, wireName);
            return;
        }
        EntityElectricalWiring other = find(this.level(), target);
        if (other != null) {
            other.setConnectionFrom(this.blockPosition().getX(), this.blockPosition().getY(),
                    this.blockPosition().getZ(), type, wireName);
        }
    }

    private void removeReciprocal(BlockPos target) {
        BlockEntity be = this.level().getBlockEntity(target);
        if (be instanceof com.portofino.realtrainmodunofficial.blockentity.InstalledObjectBlockEntity io) {
            io.removeConnectionAt(this.blockPosition().getX(), this.blockPosition().getY(),
                    this.blockPosition().getZ());
            return;
        }
        EntityElectricalWiring other = find(this.level(), target);
        if (other != null) {
            other.connections.removeIf(c -> c.x == this.blockPosition().getX()
                    && c.y == this.blockPosition().getY() && c.z == this.blockPosition().getZ());
            other.syncConnections();
        }
    }

    private void syncConnections() {
        if (this.level().isClientSide()) {
            return;
        }
        CompoundTag tag = new CompoundTag();
        Connection.writeListToNBT(tag, this.connections);
        this.entityData.set(DATA_CONNECTIONS, tag.toString());
    }

    private void readSyncedConnections(String text) {
        this.connections.clear();
        if (text == null || text.isBlank()) {
            return;
        }
        try {
            CompoundTag tag = TagParser.parseTag(text);
            this.connections.addAll(Connection.readListFromNBT(tag));
        } catch (Exception ignored) {
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide() && !this.wiringRegistered) {
            this.wiringRegistered = true;
            for (Connection connection : this.connections) {
                if (connection.isRoot
                        && (connection.type == ConnectionType.WIRE || connection.type == ConnectionType.TO_ENTITY)) {
                    WireManager.register(this.level(), this.blockPosition(),
                            new BlockPos(connection.x, connection.y, connection.z));
                }
            }
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!this.level().isClientSide()) {
            for (Connection connection : new ArrayList<>(this.connections)) {
                if (connection.isRoot
                        && (connection.type == ConnectionType.WIRE || connection.type == ConnectionType.TO_ENTITY)) {
                    WireManager.unregister(this.level(), this.blockPosition(),
                            new BlockPos(connection.x, connection.y, connection.z));
                }
            }
        }
        // 実際に破壊/除去されたときだけ相手側の逆接続も外す。
        if (reason == RemovalReason.KILLED || reason == RemovalReason.DISCARDED) {
            for (Connection connection : new ArrayList<>(this.connections)) {
                if (connection.isRoot && connection.type != ConnectionType.TO_PLAYER) {
                    this.removeReciprocal(new BlockPos(connection.x, connection.y, connection.z));
                }
            }
        }
        super.remove(reason);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        // spawn data には connections が無い。
        if (tag.contains("connections", net.minecraft.nbt.Tag.TAG_LIST)) {
            this.connections.clear();
            this.connections.addAll(Connection.readListFromNBT(tag));
            // 既存ワールドの接続を、エンティティのトラッキング開始時にクライアントへ送る。
            this.syncConnections();
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        Connection.writeListToNBT(tag, this.connections);
    }

    /** 本家 getElectricity: 配線網へ流す値。 */
    @Override
    public abstract int getElectricity();

    /** 本家 setElectricity(int): 配線網から受け取った値。 */
    public abstract void setElectricity(int value);

    /** 本家 IProvideElectricity.setElectricity(x,y,z,level… */
    @Override
    public void setElectricity(int x, int y, int z, int level) {
        this.setElectricity(level);
    }

    /** その座標に居る配線エンティティを返す (RTMU の配線はブロック座標ベースなので、 ATC/列車… */
    public static EntityElectricalWiring find(Level level, BlockPos pos) {
        if (level == null || pos == null) {
            return null;
        }
        for (net.minecraft.world.entity.Entity entity : level.getEntitiesOfClass(
                net.minecraft.world.entity.Entity.class,
                new net.minecraft.world.phys.AABB(pos).inflate(0.5D))) {
            if (entity instanceof EntityElectricalWiring wiring) {
                return wiring;
            }
        }
        return null;
    }

    /** 本家 EntityElectricalWiring.interactFirst: ワイヤーアイテム… */
    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (player == null) {
            return InteractionResult.PASS;
        }
        var stack = player.getItemInHand(hand);
        if (stack.getItem() instanceof com.portofino.realtrainmodunofficial.item.WireItem) {
            return com.portofino.realtrainmodunofficial.item.WireItem
                    .handleEntityConnection(this.level(), player, stack, this);
        }
        return super.interact(player, hand);
    }
}
