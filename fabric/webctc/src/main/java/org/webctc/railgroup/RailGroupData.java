package org.webctc.railgroup;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jp.ngt.rtm.entity.train.EntityBogie;
import jp.ngt.rtm.entity.train.EntityTrainBase;
import jp.ngt.rtm.rail.TileEntityLargeRailCore;
import jp.ngt.rtm.rail.TileEntityLargeRailSwitchCore;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.webctc.WebCTCSavedData;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * 本家 org.webctc.railgroup.RailGroupData / RailGroupUtils の移植 (連動装置の心臓部)。
 * RailGroup = レール集合 + RS出力 + 表示信号 + 転てつ設定。進路 (RailGroupChain) の
 * 予約 (reserve) / 解放 (release)、鎖錠 (Lock, frozenTime)、在線検知を毎 tick 処理する。
 * 本家の @JvmStatic ブリッジと同名メソッドを提供 (スクリプト互換)。
 * 永続化は WebCTCSavedData に JSON で保存 (形状は本家 kotlinx と同じフィールド名)。
 */
public final class RailGroupData {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    public static final CopyOnWriteArrayList<RailGroup> railGroupList = new CopyOnWriteArrayList<>();
    public static final CopyOnWriteArrayList<RailGroupFolder> folderList = new CopyOnWriteArrayList<>();

    private static final Map<String, Lock> lockList = new ConcurrentHashMap<>();
    private static final Set<RailGroupChain> rgcc = ConcurrentHashMap.newKeySet();

    private static MinecraftServer server;

    private RailGroupData() {
    }

    //------------------------------------------------------------ データ型 (本家 common/types/railgroup)

    public static final class Pos {
        public int x, y, z;

        public Pos() {
        }

        public Pos(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public BlockPos toBlockPos() {
            return new BlockPos(x, y, z);
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Pos p && p.x == x && p.y == y && p.z == z;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, y, z);
        }
    }

    /** 本家 PosIntWithKey。 */
    public static final class PosWithKey {
        public int x, y, z;
        public String key = "";
    }

    /** 本家 SettingEntry。 */
    public static final class SettingEntry {
        public String key = "";
        public boolean value;
    }

    /** 本家 SwitchSetting。 */
    public static final class SwitchSetting {
        public String name = "Default Name";
        public List<Pos> switchRsPos = new ArrayList<>();
        public List<SettingEntry> settingMap = new ArrayList<>();
    }

    /** 本家 RailGroup。 */
    public static final class RailGroup {
        public String uuid = UUID.randomUUID().toString();
        public String name = "Default Name";
        public List<Pos> railPosList = new ArrayList<>();
        public List<PosWithKey> rsPosList = new ArrayList<>();
        public List<String> nextRailGroupList = new ArrayList<>();
        public List<Pos> displayPosList = new ArrayList<>();
        public List<SwitchSetting> switchSettings = new ArrayList<>();
        public int signalLevel;
        public String folderUuid;

        public void updateBy(RailGroup other) {
            this.name = other.name;
            this.railPosList = other.railPosList;
            this.rsPosList = other.rsPosList;
            this.nextRailGroupList = other.nextRailGroupList;
            this.displayPosList = other.displayPosList;
            this.switchSettings = other.switchSettings;
            this.folderUuid = other.folderUuid;
        }
    }

    /** 本家 RailGroupFolder。 */
    public static final class RailGroupFolder {
        public String uuid = UUID.randomUUID().toString();
        public String name = "New Folder";
        public String parentUuid;
    }

    /** 本家 Lock。 */
    private static final class Lock {
        final String key;
        int frozenTime;
        boolean releaseFlag;

        Lock(String key, int frozenTime) {
            this.key = key;
            this.frozenTime = frozenTime;
        }
    }

    /** 本家 RailGroupChain。 */
    private record RailGroupChain(LinkedHashSet<String> chain, String key) {
    }

    //------------------------------------------------------------ ライフサイクル / 永続化

    public static void onServerStarted(MinecraftServer mcServer) {
        server = mcServer;
        lockList.clear();
        rgcc.clear();
        load();
    }

    public static void onServerStopping() {
        server = null;
        railGroupList.clear();
        folderList.clear();
        lockList.clear();
        rgcc.clear();
    }

    private static void load() {
        railGroupList.clear();
        folderList.clear();
        try {
            WebCTCSavedData data = WebCTCSavedData.get(server.overworld());
            JsonArray groups = JsonParser.parseString(data.get("railgroups")).getAsJsonArray();
            for (var el : groups) {
                RailGroup rg = GSON.fromJson(el, RailGroup.class);
                if (rg != null && rg.uuid != null) {
                    railGroupList.add(rg);
                }
            }
            JsonArray folders = JsonParser.parseString(data.get("railgroupfolders")).getAsJsonArray();
            for (var el : folders) {
                RailGroupFolder folder = GSON.fromJson(el, RailGroupFolder.class);
                if (folder != null && folder.uuid != null) {
                    folderList.add(folder);
                }
            }
        } catch (Exception e) {
            org.webctc.WebCTCCore.LOGGER.warn("Failed to load rail groups", e);
        }
    }

    public static void markDirty() {
        if (server == null) {
            return;
        }
        WebCTCSavedData data = WebCTCSavedData.get(server.overworld());
        data.set("railgroups", GSON.toJson(railGroupList));
        data.set("railgroupfolders", GSON.toJson(folderList));
    }

    public static String railGroupsJson() {
        return GSON.toJson(railGroupList);
    }

    public static String foldersJson() {
        return GSON.toJson(folderList);
    }

    public static String toJson(Object o) {
        return GSON.toJson(o);
    }

    public static RailGroup parseRailGroup(String json) {
        return GSON.fromJson(json, RailGroup.class);
    }

    public static RailGroupFolder parseFolder(String json) {
        return GSON.fromJson(json, RailGroupFolder.class);
    }

    public static RailGroup findRailGroup(String uuid) {
        return railGroupList.stream().filter(rg -> rg.uuid.equals(uuid)).findFirst().orElse(null);
    }

    public static RailGroupFolder findFolder(String uuid) {
        return folderList.stream().filter(f -> f.uuid.equals(uuid)).findFirst().orElse(null);
    }

    //------------------------------------------------------------ 在線 / 状態

    private static ServerLevel level() {
        return server != null ? server.overworld() : null;
    }

    /** 本家 RailGroup.isTrainOnRail()。 */
    public static boolean isTrainOnRail(RailGroup rg) {
        ServerLevel level = level();
        if (level == null) {
            return false;
        }
        for (Pos pos : rg.railPosList) {
            BlockEntity be = level.getBlockEntity(pos.toBlockPos());
            if (be instanceof TileEntityLargeRailCore core && core.isTrainOnRail()) {
                return true;
            }
        }
        return false;
    }

    /** 本家 @JvmStatic isTrainOnRail(uuid)。 */
    public static boolean isTrainOnRail(String uuid) {
        RailGroup rg = findRailGroup(uuid);
        return rg != null && isTrainOnRail(rg);
    }

    /** 本家 RailGroup.getTrainName() — 在線編成の制御車の車両名。 */
    public static String getTrainName(RailGroup rg) {
        ServerLevel level = level();
        if (level == null) {
            return "?";
        }
        Set<Pos> railPos = Set.copyOf(rg.railPosList);
        for (EntityTrainBase train : allTrains(level)) {
            if (!onRailGroup(train, railPos)) {
                continue;
            }
            if (train.getFormation() != null) {
                var control = train.getFormation().getTrainStream()
                        .filter(Objects::nonNull)
                        .filter(EntityTrainBase::isControlCar)
                        .findFirst().orElse(train);
                var state = control.getResourceState();
                return state != null ? state.getName() : "?";
            }
            var state = train.getResourceState();
            return state != null ? state.getName() : "?";
        }
        return "?";
    }

    private static boolean onRailGroup(EntityTrainBase train, Set<Pos> railPos) {
        for (int i = 0; i < 2; i++) {
            EntityBogie bogie = train.getBogie(i);
            if (bogie == null) {
                continue;
            }
            TileEntityLargeRailCore core = bogie.getCurrentRailObj();
            if (core != null) {
                BlockPos pos = core.getBlockPos();
                if (railPos.contains(new Pos(pos.getX(), pos.getY(), pos.getZ()))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static List<EntityTrainBase> allTrains(ServerLevel level) {
        return StreamSupport.stream(level.getEntities().getAll().spliterator(), false)
                .filter(EntityTrainBase.class::isInstance)
                .map(EntityTrainBase.class::cast)
                .collect(Collectors.toList());
    }

    /** 本家 RailGroupState を JSON で返す。 */
    public static String stateJson(List<String> uuids) {
        StringBuilder out = new StringBuilder("[");
        boolean first = true;
        for (String uuid : uuids) {
            RailGroup rg = findRailGroup(uuid);
            if (rg == null) {
                continue;
            }
            boolean trainOnRail = isTrainOnRail(rg);
            String trainName = trainOnRail ? getTrainName(rg) : null;
            if (!first) {
                out.append(',');
            }
            first = false;
            out.append("{\"uuid\":\"").append(rg.uuid).append('"')
                    .append(",\"locked\":").append(isLocked(rg.uuid))
                    .append(",\"reserved\":").append(isReserved(rg.uuid))
                    .append(",\"trainOnRail\":").append(trainOnRail)
                    .append(",\"trainName\":")
                    .append(trainName == null ? "null" : GSON.toJson(trainName))
                    .append('}');
        }
        return out.append(']').toString();
    }

    //------------------------------------------------------------ 予約 / ロック (本家ロジック)

    public static boolean hasReleaseFlag(String uuid) {
        Lock lock = lockList.get(uuid);
        return lock != null && lock.releaseFlag;
    }

    /** 進路 (uuid 列) を key で予約する。全区間が確保できた時のみ成功 (本家 reserve)。 */
    public static boolean reserve(String[] uuids, String key) {
        RailGroupChain railGroupChain = new RailGroupChain(new LinkedHashSet<>(List.of(uuids)), key);
        if (!canLock(railGroupChain, key)) {
            return false;
        }
        lock(railGroupChain);
        rgcc.add(railGroupChain);
        return true;
    }

    /** 進路の解放。在線中の区間以降は releaseFlag を立てて追い抜き解放 (本家 release)。 */
    public static void release(String[] uuids, String key) {
        RailGroupChain railGroupChain = new RailGroupChain(new LinkedHashSet<>(List.of(uuids)), key);
        if (rgcc.remove(railGroupChain)) {
            releaseChain(railGroupChain);
        }
    }

    public static void unsafeRelease(String uuid) {
        lockList.remove(uuid);
    }

    public static void unsafeRelease(String[] uuids) {
        for (String uuid : uuids) {
            lockList.remove(uuid);
        }
    }

    public static boolean isLocked(String uuid) {
        return lockList.containsKey(uuid);
    }

    public static boolean isLocked(String uuid, String key) {
        Lock lock = lockList.get(uuid);
        return lock != null && lock.key.equals(key);
    }

    public static boolean isLocked(String[] uuids, String key) {
        for (String uuid : uuids) {
            if (!isLocked(uuid, key)) {
                return false;
            }
        }
        return uuids.length > 0;
    }

    public static boolean isReserved(String uuid) {
        Lock lock = lockList.get(uuid);
        return lock != null && lock.frozenTime == 0 && !isTurning(uuid);
    }

    public static boolean isReserved(String uuid, String key) {
        Lock lock = lockList.get(uuid);
        return lock != null && lock.key.equals(key) && lock.frozenTime == 0 && !isTurning(uuid);
    }

    public static boolean isReserved(String[] uuids, String key) {
        for (String uuid : uuids) {
            if (!isReserved(uuid, key)) {
                return false;
            }
        }
        return uuids.length > 0;
    }

    /** RTMU に転換中フラグがないため常に false (本家 isTurning 相当)。 */
    public static boolean isTurning(String uuid) {
        return false;
    }

    public static String getReservedKey(String uuid) {
        Lock lock = lockList.get(uuid);
        return lock != null ? lock.key : null;
    }

    private static boolean canLock(RailGroupChain chain, String key) {
        return chain.chain().stream()
                .map(RailGroupData::findRailGroup)
                .filter(Objects::nonNull)
                .allMatch(rg -> canLock(rg, key));
    }

    private static boolean canLock(RailGroup rg, String key) {
        boolean trainOnRail = isTrainOnRail(rg);
        Lock own = lockList.get(rg.uuid);
        boolean reservedByOther = own != null && !own.key.equals(key);
        //他 key でロック中の RailGroup とレールを共有していないか (本家 isLocked 判定)
        Set<Pos> myRails = Set.copyOf(rg.railPosList);
        boolean lockedShared = lockList.entrySet().stream()
                .filter(e -> !e.getValue().key.equals(key))
                .map(e -> findRailGroup(e.getKey()))
                .filter(Objects::nonNull)
                .anyMatch(other -> other.railPosList.stream().anyMatch(myRails::contains));
        return !(trainOnRail || reservedByOther || lockedShared);
    }

    private static void lock(RailGroupChain chain) {
        for (String uuid : chain.chain()) {
            RailGroup rg = findRailGroup(uuid);
            if (rg == null) {
                continue;
            }
            boolean hasSwitch = hasSwitch(rg);
            Lock old = lockList.get(uuid);
            boolean keyEquals = old != null && old.key.equals(chain.key());
            //転てつ器を含む区間は 20 tick の転換鎖錠 (本家仕様)
            int frozenTime = hasSwitch ? (keyEquals ? old.frozenTime : 20) : 0;
            lockList.put(uuid, new Lock(chain.key(), frozenTime));
        }
    }

    private static void releaseChain(RailGroupChain chain) {
        List<RailGroup> railGroups = chain.chain().stream()
                .map(RailGroupData::findRailGroup)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        int firstOnRail = -1;
        for (int i = 0; i < railGroups.size(); i++) {
            if (isTrainOnRail(railGroups.get(i))) {
                firstOnRail = i;
                break;
            }
        }
        if (firstOnRail == -1) {
            railGroups.forEach(rg -> lockList.remove(rg.uuid));
        } else {
            for (int i = 0; i < firstOnRail; i++) {
                lockList.remove(railGroups.get(i).uuid);
            }
            for (int i = firstOnRail; i < railGroups.size(); i++) {
                Lock lock = lockList.get(railGroups.get(i).uuid);
                if (lock != null) {
                    lock.releaseFlag = true;
                }
            }
        }
    }

    /** 本家 hasSwitch — 転てつレール (SwitchCore) を含むか。 */
    private static boolean hasSwitch(RailGroup rg) {
        ServerLevel level = level();
        if (level == null) {
            return false;
        }
        for (Pos pos : rg.railPosList) {
            if (level.getBlockEntity(pos.toBlockPos()) instanceof TileEntityLargeRailSwitchCore) {
                return true;
            }
        }
        return false;
    }

    //------------------------------------------------------------ tick (本家 WebCTCEventHandler)

    private static int tickCount;

    /** 毎 tick 呼ぶ。20 tick ごとに update()、毎 tick releaseFlag / frozenTime 処理。 */
    public static void tick() {
        if (server == null) {
            return;
        }
        tickCount++;
        if (tickCount >= 20) {
            tickCount = 0;
            for (RailGroup rg : railGroupList) {
                try {
                    update(rg);
                } catch (Exception ignored) {
                }
            }
        }
        for (RailGroup rg : railGroupList) {
            //releaseFlag 付きロックは列車が到達したら解放 (本家 RailGroup.tick)
            if (hasReleaseFlag(rg.uuid) && isTrainOnRail(rg)) {
                unsafeRelease(rg.uuid);
            }
        }
        //frozenTime カウントダウン (本家 updateLocks)
        lockList.values().forEach(lock -> {
            if (lock.frozenTime > 0) {
                lock.frozenTime--;
            }
        });
    }

    /** 本家 RailGroup.update() — 信号段数計算 + 表示信号 + RS/転てつ出力。 */
    private static void update(RailGroup rg) {
        ServerLevel level = level();
        if (level == null) {
            return;
        }
        boolean trainOnRail = isTrainOnRail(rg);

        //閉塞信号の段数: 在線なら 0、そうでなければ次区間の最小 signalLevel。+1 して最大 6
        int nextMin = rg.nextRailGroupList.stream()
                .map(RailGroupData::findRailGroup)
                .filter(Objects::nonNull)
                .mapToInt(next -> next.signalLevel)
                .min().orElse(0);
        rg.signalLevel = Math.min((trainOnRail ? 0 : nextMin) + 1, 6);

        //表示信号: displayPos の信号機へ直接反映 (本家 TileEntitySignal.setElectricity 相当)
        for (Pos pos : rg.displayPosList) {
            BlockEntity be = level.getBlockEntity(pos.toBlockPos());
            if (be instanceof com.portofino.realtrainmodunofficial.blockentity.InstalledObjectBlockEntity io
                    && io.isSignal() && io.getSignal() != rg.signalLevel) {
                io.setElectricity(rg.signalLevel);
            }
        }

        //転てつ設定: 予約 key に対応する設定でレバー RS を出す (本家仕様)
        String reservedKey = getReservedKey(rg.uuid);
        for (SwitchSetting setting : rg.switchSettings) {
            SettingEntry match = null;
            if (reservedKey != null) {
                for (SettingEntry entry : setting.settingMap) {
                    if (reservedKey.equals(entry.key)) {
                        match = entry;
                        break;
                    }
                }
            }
            if (match != null) {
                var block = match.value ? Blocks.REDSTONE_BLOCK : Blocks.RED_STAINED_GLASS;
                for (Pos pos : setting.switchRsPos) {
                    level.setBlock(pos.toBlockPos(), block.defaultBlockState(), 3);
                }
            }
        }

        //在線 RS 出力: 在線でレッドストーンブロック、それ以外は赤色ガラス (本家仕様)
        var onRailBlock = trainOnRail ? Blocks.REDSTONE_BLOCK : Blocks.RED_STAINED_GLASS;
        for (PosWithKey pos : rg.rsPosList) {
            var block = onRailBlock;
            if (pos.key != null && !pos.key.isEmpty()) {
                //key 付きは「その key で予約中」を出力
                block = isReserved(rg.uuid, pos.key) ? onRailBlock : Blocks.REDSTONE_BLOCK;
            }
            level.setBlock(new BlockPos(pos.x, pos.y, pos.z), block.defaultBlockState(), 3);
        }
    }

    //------------------------------------------------------------ レール保護 (本家 onBreakBlock)

    /** このレール座標がいずれかの RailGroup に管理されているか。 */
    public static List<String> managedBy(BlockPos pos) {
        Pos p = new Pos(pos.getX(), pos.getY(), pos.getZ());
        return railGroupList.stream()
                .filter(rg -> rg.railPosList.contains(p))
                .map(rg -> rg.uuid)
                .collect(Collectors.toList());
    }

    //------------------------------------------------------------ スクリプト互換ブリッジ (本家 @JvmStatic)

    public static void setSignal(String uuid, int signal) {
        RailGroup rg = findRailGroup(uuid);
        ServerLevel level = level();
        if (rg == null || level == null) {
            return;
        }
        for (Pos pos : rg.railPosList) {
            BlockEntity be = level.getBlockEntity(pos.toBlockPos());
            if (be instanceof TileEntityLargeRailCore core) {
                core.setSignal(signal);
            }
        }
    }
}
