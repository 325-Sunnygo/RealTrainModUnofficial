package jp.ngt.rtm.electric;

/**
 * 本家 jp.ngt.rtm.electric.Connection の移植。
 *
 * <p>電線 1 本の「接続先」を表す。NGTO Builder の Wire ツールが
 * {@code tileEntity.setConnectionTo(x, y, z, Connection.ConnectionType.WIRE, resourceState)} と
 * 種別を渡す。ConnectionType の顔ぶれと id は本家に合わせてある
 * (以前は RTMU 独自の WIRE2/RELAY/DUMMY だったため、本家スクリプトが参照する
 * DIRECT / TO_ENTITY / TO_PLAYER が解決できなかった)。
 */
@SuppressWarnings("unused")
public class Connection {
    /** 接続元側か (張った側が root)。 */
    public final boolean isRoot;
    public int x;
    public int y;
    public int z;
    public final ConnectionType type;
    /** 電線モデル名 (ModelWire)。 */
    public final String wireName;

    private Object connectedObject;

    public Connection(boolean isRoot, int x, int y, int z, ConnectionType type, String wireName) {
        this.isRoot = isRoot;
        this.x = x;
        this.y = y;
        this.z = z;
        this.type = type == null ? ConnectionType.NONE : type;
        this.wireName = wireName == null ? "" : wireName;
    }

    /** 本家 isVisible: この接続が描画対象か。 */
    public boolean isVisible() {
        return this.type.isVisible;
    }

    /** 本家 getElectricalWiring: 接続先の配線タイルエンティティ。 */
    public Object getElectricalWiring(Object world) {
        if (this.type != ConnectionType.WIRE && this.type != ConnectionType.TO_ENTITY) {
            return null;
        }
        net.minecraft.world.level.Level level = unwrapLevel(world);
        if (level == null) {
            return null;
        }
        this.connectedObject = level.getBlockEntity(new net.minecraft.core.BlockPos(this.x, this.y, this.z));
        return this.connectedObject;
    }

    /** 本家 getIProvideElectricity: 直結先の給電元。 */
    public IProvideElectricity getIProvideElectricity(Object world) {
        if (this.type != ConnectionType.DIRECT) {
            return null;
        }
        net.minecraft.world.level.Level level = unwrapLevel(world);
        if (level == null) {
            return null;
        }
        Object be = level.getBlockEntity(new net.minecraft.core.BlockPos(this.x, this.y, this.z));
        return be instanceof IProvideElectricity p ? p : null;
    }

    /** 本家 getPlayer: プレイヤーへ張った電線の相手。 */
    public net.minecraft.world.entity.player.Player getPlayer(Object world) {
        if (this.type != ConnectionType.TO_PLAYER) {
            return null;
        }
        net.minecraft.world.level.Level level = unwrapLevel(world);
        if (level == null) {
            return null;
        }
        //TO_PLAYER は x にエンティティ ID を入れる (本家の {eID, -1, 0} 形式)
        net.minecraft.world.entity.Entity e = level.getEntity(this.x);
        return e instanceof net.minecraft.world.entity.player.Player p ? p : null;
    }

    /** 本家 isAvailable: 接続先が今も存在するか。 */
    public boolean isAvailable(Object world) {
        return switch (this.type) {
            case WIRE, TO_ENTITY -> this.getElectricalWiring(world) != null;
            case DIRECT -> this.getIProvideElectricity(world) != null;
            case TO_PLAYER -> this.getPlayer(world) != null;
            case NONE -> false;
        };
    }

    private static net.minecraft.world.level.Level unwrapLevel(Object world) {
        if (world instanceof net.minecraft.world.level.Level level) {
            return level;
        }
        if (world instanceof jp.ngt.mccompat.WorldCompat compat) {
            return compat.getLevel();
        }
        return null;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Connection c
                && this.x == c.x && this.y == c.y && this.z == c.z && this.type == c.type;
    }

    @Override
    public int hashCode() {
        return (this.x * 31 + this.y) * 31 + this.z;
    }

    public void writeToNBT(net.minecraft.nbt.CompoundTag nbt) {
        nbt.putBoolean("IsRoot", this.isRoot);
        nbt.putInt("x", this.x);
        nbt.putInt("y", this.y);
        nbt.putInt("z", this.z);
        nbt.putInt("type", this.type.id);
        nbt.putString("ModelName", this.wireName);
    }

    public static Connection readFromNBT(net.minecraft.nbt.CompoundTag nbt) {
        //IsRoot が無いのは v34 以前のデータ。当時は張った側しか保存しなかった
        boolean isRoot = !nbt.contains("IsRoot") || nbt.getBoolean("IsRoot");
        int type = nbt.getInt("type");
        String name = nbt.getString("ModelName");
        if (name.isEmpty()) {
            switch (type) {
                case 1, 50 -> name = "BasicWireBlack";
                case 2 -> {
                    name = "SimpleCatenary";
                    type = 1;
                }
                case 51 -> {
                    name = "SimpleCatenary";
                    type = 50;
                }
                default -> {
                }
            }
        }
        return new Connection(isRoot, nbt.getInt("x"), nbt.getInt("y"), nbt.getInt("z"),
                ConnectionType.getType(type), name);
    }

    public static java.util.List<Connection> readListFromNBT(net.minecraft.nbt.CompoundTag nbt) {
        java.util.List<Connection> out = new java.util.ArrayList<>();
        net.minecraft.nbt.ListTag list = nbt.getList("connections", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            out.add(readFromNBT(list.getCompound(i)));
        }
        return out;
    }

    public static void writeListToNBT(net.minecraft.nbt.CompoundTag nbt, java.util.List<Connection> list) {
        net.minecraft.nbt.ListTag tagList = new net.minecraft.nbt.ListTag();
        for (Connection c : list) {
            net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
            c.writeToNBT(tag);
            tagList.add(tag);
        }
        nbt.put("connections", tagList);
    }

    public enum ConnectionType {
        NONE(0, false),
        WIRE(1, true),
        DIRECT(3, false),
        TO_ENTITY(4, true),
        TO_PLAYER(50, true);

        public final byte id;
        /** 描画対象か。 */
        public final boolean isVisible;

        ConnectionType(int id, boolean isVisible) {
            this.id = (byte) id;
            this.isVisible = isVisible;
        }

        public static ConnectionType getType(int id) {
            for (ConnectionType t : values()) {
                if (t.id == id) {
                    return t;
                }
            }
            return NONE;
        }
    }
}
