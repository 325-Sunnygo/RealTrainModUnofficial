package com.portofino.realtrainmodunofficial.blockentity;

import com.portofino.realtrainmodunofficial.ClientHooks;
import com.portofino.realtrainmodunofficial.RealTrainModUnofficialBlockEntities;
import com.portofino.realtrainmodunofficial.installedobject.InstalledObjectDefinition;
import com.portofino.realtrainmodunofficial.installedobject.InstalledObjectCategory;
import com.portofino.realtrainmodunofficial.installedobject.InstalledObjectRegistry;
import com.portofino.realtrainmodunofficial.signal.SignalAspect;
import com.portofino.realtrainmodunofficial.signal.SignalNetworkSavedData;
import com.portofino.realtrainmodunofficial.signboard.SignboardText;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InstalledObjectBlockEntity extends BlockEntity
        implements jp.ngt.rtm.electric.TileEntityInsulator, jp.ngt.ngtlib.block.TileEntityPlaceable,
                   jp.ngt.rtm.modelpack.IModelSelector {
    /** 本家 BlockTurnstile.openGate: tile.setCount(30) — 開… */
    private static final int TICKET_GATE_OPEN_TICKS = 30;
    private static final int TICKET_GATE_MOVE_TICKS = 12;
    private String definitionId = "";
    /** 張ってある架線のモデル。碍子の定義とは別に持つ (本家は Connection がモデルを持ち、碍… */
    private String wireModel = "";
    private String category = InstalledObjectCategory.LIGHT.name();
    // ATS 地上子ビーコン (server_ATS_Beacon.js) が、連動信号機の現示を NG…
    @SuppressWarnings("unused")
    private int signalLevel;
    // --- 派生値キャッシュ (definitionId から一意に決まる不変値を毎tick/毎fra…
    private transient String crossingLogicKey;
    private transient boolean crossingLogicVal;
    private transient String brokenFluorescentKey;
    private transient boolean brokenFluorescentVal;
    private float yaw;
    // 壁(横面)挿し時に碍子を横倒しにするためのピッチ(度)。0=通常(縦置き)。
    private float mountPitch;
    // yaw の後に掛けるロール(度)。列車検知器をレールのカント(横傾き)に合わせるために使う。
    private float mountRoll;
    // 本家 meta 相当のクリック面 (0-5)。-1 = 旧方式
    private int mountFace = -1;
    // 本家 ResourceState.color 相当 (電線の頂点色)。
    private int wireColor = 0xFFFFFF;
    // 本家 TileEntityPlaceable の微調整 (GuiChangeOffset): 追加…
    private float adjustRoll;
    private float adjustPitch;
    private float adjustYaw;
    private float adjustScale = 1.0F;
    /** 本家 TileEntityElectricalWiring.connections の移植。 */
    private final java.util.List<jp.ngt.rtm.electric.Connection> connections =
        new java.util.ArrayList<>();

    private BlockPos wireStart;
    private BlockPos wireEnd;
    private boolean powered;
    private int barMoveCount;
    private int lightCount = -1;
    private int tickCountOnActive;
    private double offsetX;
    private double offsetY;
    private double offsetZ;
    /** 本家 TileEntitySignal.setOrigBlock 用: 信号を置くときに置き換えた… */
    private BlockState signalOrigBlock;
    /** 本家 TileEntitySignal.origTileEntity: 置き換えた元タイルの NB… */
    private CompoundTag signalOrigTileNbt;
    /** 本家 TileEntitySignal の「本体向き (rotation)」。 */
    private float signalBodyYaw;
    private int signalChannel = -1;
    private int signalAspect = SignalAspect.STOP.getId();
    // 本家 electric: コネクタの信号レベル (配線網)
    private int electricity;
    private int prevConnectorInput = -1;
    /** 本家 Connection(DIRECT): コネクタが取り付いている相手ブロック。 */
    private BlockPos directTarget;
    /** 本家 TileEntityConnector.prevOutputSignal。 */
    private int prevOutputSignal = Integer.MIN_VALUE;
    // スピーカー: 音が聞こえる範囲(ブロック)。GUIで可変。
    private int speakerRange = 32;
    private final Map<String, String> scriptData = new HashMap<>();

    /** DataMap (scriptData) の値設定。 */
    public void putScriptData(String key, String value) {
        if (key == null || key.isBlank()) {
            return;
        }
        this.scriptData.put(key, value == null ? "" : value);
        this.setChanged();
        if (this.level != null && !this.level.isClientSide()) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }

    public Map<String, String> copyScriptData() {
        return new HashMap<>(this.scriptData);
    }

    /** DataMapCompat と同じ「毎tick値 (powered 等) を除いた」生の保存値。 */
    public String getScriptDataValue(String key) {
        return this.scriptData.getOrDefault(key, "");
    }

    // ---- 蛍光灯 (本家 TileEntityFluorescent) ---- 本家 dirF:…
    private byte fluorescentDir;
    // 本家 BlockFluorescent.getLightValue: meta==2 (壊れた蛍光…
    private int fluorescentFlickerTick;
    private int fluorescentLight = 15;

    // ---- 転轍機 (本家 TileEntityPoint) ---- activated: レッド…
    private boolean pointActivated;
    // move: 本家既定 24.0。符号がレバー/本体の向き(左右どちら側に付くか)を決める。
    private float pointMove = 24.0F;

    // ---- 看板 (本家 TileEntitySignBoard / ResourceStateSi…
    /** 板に貼り付けた文字。看板エディタ (SignboardScreen) で編集する。 */
    private final List<SignboardText> signTexts = new ArrayList<>();
    /** 時刻表の参照設定。本家形式 "tt=<file>,station=<駅名>,track=<番線>"。 */
    private String signTtSetting = DEFAULT_TT_SETTING;
    /** 本家 direction (0-3)。設置時のプレイヤー向きで、板の面が向く方角を決める。 */
    private byte signDirection;
    /** クライアント: フレームアニメ用カウンタ (frame * animationCycle で1周)。 */
    private int signCounter;
    /** サーバー: lightValue == -16 (ランダム点滅) 用の現在の明るさ。 */
    private int signFlicker;
    /** サーバー: 最後にライトエンジンへ渡した明るさ。 */
    private int signLastLight = -1;

    // ---- 列車検知器 (本家 EntityTrainDetector) ----
    /** 検知したときにレッドストーンブロックを置く/消す座標。未設定なら null (何もしない)。 */
    private BlockPos detectorTarget;
    /** true = 列車を検知したら「置く」 (居なくなったら消す) false = 列車を検知したら「… */
    private boolean detectorPlaceOnDetect = true;
    /** 真下のレールに列車が乗っているか。クライアントにも同期して GUI に出す。 */
    private boolean detectorTrainOnRail;

    private static final String DEFAULT_TT_SETTING = "tt=tt_sample.csv,station=西京,track=-1";
    private static final RandomSource SIGN_RANDOM = RandomSource.create();
    /** 本家 EntityTrainDetector: 自分の位置から真下に最大 8 ブロックまでレールを… */
    private static final int DETECTOR_RAIL_SEARCH_DEPTH = 8;
    /** 検知の間隔(tick)。毎tickでなくても列車の検知には十分。 */
    private static final int DETECTOR_INTERVAL = 5;

    public InstalledObjectBlockEntity(BlockPos pos, BlockState blockState) {
        super(RealTrainModUnofficialBlockEntities.INSTALLED_OBJECT.get(), pos, blockState);
        // 本家スクリプト互換 (1.7.10 の TileEntity 座標フィールド)。
        this.field_145851_c = pos.getX();
        this.field_145848_d = pos.getY();
        this.field_145849_e = pos.getZ();
    }

    // ---- 本家スクリプト互換 (SRG 名) ---- 架線柱パックのスクリプトは RTMCore…

    /** 1.7.10 TileEntity.xCoord */
    public int field_145851_c;
    /** 1.7.10 TileEntity.yCoord */
    public int field_145848_d;
    /** 1.7.10 TileEntity.zCoord */
    public int field_145849_e;

    private jp.ngt.mccompat.WorldCompat worldCompat;

    /** func_145831_w = getWorldObj。 */
    public jp.ngt.mccompat.WorldCompat func_145831_w() {
        if (this.level == null) {
            return null;
        }
        if (this.worldCompat == null || this.worldCompat.level != this.level) {
            this.worldCompat = new jp.ngt.mccompat.WorldCompat(this.level);
        }
        return this.worldCompat;
    }

    // ---- 本家 EntityInstalledObject 互換 (サーバースクリプト用) ---…

    /** 1.7.10 Entity.worldObj */
    public jp.ngt.mccompat.WorldCompat field_70170_p;
    /** posX (ブロック中心) */
    public double field_70165_t;
    /** posY */
    public double field_70163_u;
    /** posZ (ブロック中心) */
    public double field_70161_v;
    /** rotationYaw。検知器は自分の向きと列車の進行方向を突き合わせて通過方向を判定する。 */
    public float field_70177_z;
    /** boundingBox。検知器はこれを上下に広げて中の列車を探す。設置物は 1 ブロック大。 */
    public jp.ngt.mccompat.AxisAlignedBB field_70121_D;

    private jp.ngt.rtm.modelpack.ScriptExecuter scriptExecuter;

    /** サーバースクリプトを呼ぶ直前に、上の SRG フィールドを実際の値に合わせる。 */
    public void refreshScriptFields() {
        this.field_70170_p = this.func_145831_w();
        this.field_70165_t = this.worldPosition.getX() + 0.5D;
        // ATC 地上子はレール吸着でレールの 1 つ上のブロックに置かれる。
        this.field_70163_u = getCategory() == InstalledObjectCategory.ATC
                ? this.worldPosition.getY() - 1.0D
                : this.worldPosition.getY();
        this.field_70161_v = this.worldPosition.getZ() + 0.5D;
        this.field_70177_z = this.yaw;
        if (this.field_70121_D == null) {
            this.field_70121_D = new jp.ngt.mccompat.AxisAlignedBB(
                    new net.minecraft.world.phys.AABB(this.worldPosition));
        }
    }

    /** サーバースクリプトの第 2 引数。設置物ごとに 1 個作って使い回す (count を貯めるため)。 */
    public jp.ngt.rtm.modelpack.ScriptExecuter getScriptExecuter(net.minecraft.server.level.ServerLevel serverLevel) {
        if (this.scriptExecuter == null) {
            this.scriptExecuter = new jp.ngt.rtm.modelpack.ScriptExecuter(
                    serverLevel,
                    new net.minecraft.world.phys.Vec3(this.worldPosition.getX() + 0.5D,
                            this.worldPosition.getY(), this.worldPosition.getZ() + 0.5D),
                    "RTM Script Executer");
        }
        return this.scriptExecuter;
    }

    /** 本家 TileEntityInsulator.wirePos (電線の取付点)。 */
    @Override
    public jp.ngt.ngtlib.math.Vec3 getWirePos() {
        // 碍子 (INSULATOR) も対象に含める。
        InstalledObjectCategory cat = getCategory();
        if (cat != InstalledObjectCategory.CONNECTOR_INPUT && cat != InstalledObjectCategory.CONNECTOR_OUTPUT
                && cat != InstalledObjectCategory.INSULATOR) {
            return null;
        }
        InstalledObjectDefinition def = InstalledObjectRegistry.getById(this.definitionId);
        if (def == null) {
            return null;
        }
        net.minecraft.world.phys.Vec3 wp = def.getWireAttachPos();
        // ★ここはモデル座標のままの生の取付点を返す。
        return wp == null ? null : new jp.ngt.ngtlib.math.Vec3(wp.x, wp.y, wp.z);
    }

    /** NGTO Builder の Wire ツール互換: スクリプトの setModelName (N… */
    /** 見つからなかったモデル名を 1 回だけ知らせる。 */
    private static final java.util.Set<String> WARNED_MODEL_NAME =
        java.util.concurrent.ConcurrentHashMap.newKeySet();

    public void applyScriptModelName(String bareName) {
        InstalledObjectDefinition def = InstalledObjectRegistry.getByBareName(bareName, InstalledObjectCategory.INSULATOR);
        if (def == null && bareName != null) {
            // 完全一致で見つからないときは末尾一致でも拾う ("pack:Name" 形式や接頭辞違いで来ること…
            String needle = bareName.substring(bareName.lastIndexOf(':') + 1);
            for (InstalledObjectDefinition d : InstalledObjectRegistry.getAll()) {
                if (d.getBareName().equalsIgnoreCase(needle)) {
                    def = d;
                    break;
                }
            }
        }
        if (def == null) {
            // ★黙って諦めない。ここで返すと「ブロックは置かれたのにモデルが付かない (＝何も見えない)」になり…
            if (WARNED_MODEL_NAME.size() < 32 && WARNED_MODEL_NAME.add(String.valueOf(bareName))) {
                com.portofino.realtrainmodunofficial.RealTrainModUnofficial.LOGGER.warn(
                    "[RTMU] スクリプトが指定したモデルが見つかりません: \"{}\" (碍子/設置物として解決できず)",
                    bareName);
            }
            return;
        }
        this.definitionId = def.getId();
        this.category = def.getCategory().name();
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    // ---- 1.7.10 srg 名のシム (NGTO Builder 等のパックスクリプトが直接呼…

    /** 本家 TileEntity.readFromNBT。 */
    public void func_145839_a(Object nbt) {
        net.minecraft.nbt.CompoundTag tag = jp.ngt.mccompat.nbt.NBTTagCompound.unwrap(nbt);
        if (tag == null || this.level == null) {
            return;
        }
        // ★パックの NBT は部分的 (ModelName/offset だけ等)。
        String prevDefinition = this.definitionId;
        String prevCategory = this.category;
        String prevWireModel = this.wireModel;
        int prevMountFace = this.mountFace;
        float prevYaw = this.yaw;
        net.minecraft.core.BlockPos prevWireStart = this.wireStart;
        net.minecraft.core.BlockPos prevWireEnd = this.wireEnd;
        java.util.List<jp.ngt.rtm.electric.Connection> prevConnections =
            new java.util.ArrayList<>(this.connections);
        this.loadAdditional(tag, this.level.registryAccess());
        if (!tag.contains("DefinitionId")) {
            this.definitionId = prevDefinition;
        }
        if (!tag.contains("Category")) {
            this.category = prevCategory;
        }
        if (!tag.contains("WireModelId")) {
            this.wireModel = prevWireModel;
        }
        if (!tag.contains("Yaw")) {
            this.yaw = prevYaw;
        }
        if (!tag.contains("MountFace")) {
            // 本家は取付面をブロックメタが持つので NBT 適用では変わらない。
            this.mountFace = prevMountFace >= 0 ? prevMountFace : 1;
        }
        if (!tag.contains("WireStartX")) {
            this.wireStart = prevWireStart;
        }
        if (!tag.contains("WireEndX")) {
            this.wireEnd = prevWireEnd;
        }
        if (!tag.contains("Connections")) {
            this.connections.clear();
            this.connections.addAll(prevConnections);
        }
        // 1.7.10 のパックが手で入れるキーも受ける (RTMU の保存形式には無い)
        if (tag.contains("ModelName")) {
            this.applyScriptModelName(tag.getString("ModelName"));
        }
        if (tag.contains("offsetX") || tag.contains("offsetY") || tag.contains("offsetZ")) {
            this.setRenderOffset(tag.getFloat("offsetX"), tag.getFloat("offsetY"), tag.getFloat("offsetZ"));
        }
        this.setChanged();
        if (!this.level.isClientSide) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }

    /** 本家 TileEntity.writeToNBT。 */
    public void func_145841_b(Object nbt) {
        net.minecraft.nbt.CompoundTag tag = jp.ngt.mccompat.nbt.NBTTagCompound.unwrap(nbt);
        if (tag == null || this.level == null) {
            return;
        }
        this.saveAdditional(tag, this.level.registryAccess());
        tag.putInt("x", this.worldPosition.getX());
        tag.putInt("y", this.worldPosition.getY());
        tag.putInt("z", this.worldPosition.getZ());
        // パックが読み書きする 1.7.10 のキーも一緒に出す
        tag.putString("ModelName", bareModelName(this.definitionId));
        net.minecraft.world.phys.Vec3 off = this.getRenderOffset();
        tag.putFloat("offsetX", (float) off.x);
        tag.putFloat("offsetY", (float) off.y);
        tag.putFloat("offsetZ", (float) off.z);
    }

    /** "insulator:pack:Name" → "Name" (パックが期待する素の名前)。 */
    private static String bareModelName(String id) {
        if (id == null || id.isEmpty()) {
            return "";
        }
        int i = id.lastIndexOf(':');
        return i >= 0 ? id.substring(i + 1) : id;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("DefinitionId", definitionId);
        tag.putString("WireModelId", wireModel);
        if (this.signalOrigBlock != null) {
            tag.put("SignalOrigBlock", net.minecraft.nbt.NbtUtils.writeBlockState(this.signalOrigBlock));
        }
        if (this.signalOrigTileNbt != null) {
            tag.put("SignalOrigTile", this.signalOrigTileNbt.copy());
        }
        tag.putFloat("SignalBodyYaw", this.signalBodyYaw);
        if (!this.connections.isEmpty()) {
            net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
            for (jp.ngt.rtm.electric.Connection c : this.connections) {
                CompoundTag e = new CompoundTag();
                e.putBoolean("Root", c.isRoot);
                e.putInt("X", c.x);
                e.putInt("Y", c.y);
                e.putInt("Z", c.z);
                e.putByte("Type", c.type.id);
                e.putString("Wire", c.wireName);
                list.add(e);
            }
            tag.put("Connections", list);
        }
        tag.putString("Category", category);
        tag.putFloat("Yaw", yaw);
        tag.putFloat("MountPitch", mountPitch);
        tag.putFloat("MountRoll", mountRoll);
        tag.putInt("MountFace", mountFace);
        if (directTarget != null) {
            // 本家 Connection(DIRECT) の相手座標
            tag.putInt("DirectX", directTarget.getX());
            tag.putInt("DirectY", directTarget.getY());
            tag.putInt("DirectZ", directTarget.getZ());
        }
        // 本家 ResourceState と同じキー名 (本家ワールドのデータをそのまま読める)。
        tag.putInt("Color", wireColor);
        tag.putFloat("AdjustRoll", adjustRoll);
        tag.putFloat("AdjustPitch", adjustPitch);
        tag.putFloat("AdjustYaw", adjustYaw);
        tag.putFloat("AdjustScale", adjustScale);
        if (wireStart != null) {
            tag.putInt("WireStartX", wireStart.getX());
            tag.putInt("WireStartY", wireStart.getY());
            tag.putInt("WireStartZ", wireStart.getZ());
        }
        if (wireEnd != null) {
            tag.putInt("WireEndX", wireEnd.getX());
            tag.putInt("WireEndY", wireEnd.getY());
            tag.putInt("WireEndZ", wireEnd.getZ());
        }
        tag.putBoolean("Powered", powered);
        tag.putInt("Electricity", electricity);
        tag.putInt("BarMoveCount", barMoveCount);
        tag.putInt("LightCount", lightCount);
        tag.putInt("TickCountOnActive", tickCountOnActive);
        // 本家 TileEntityPlaceable:33 と同じキー名・型 (小文字 offsetX /…
        tag.putFloat("offsetX", (float) offsetX);
        tag.putFloat("offsetY", (float) offsetY);
        tag.putFloat("offsetZ", (float) offsetZ);
        tag.putInt("SignalChannel", signalChannel);
        tag.putInt("SignalAspect", signalAspect);
        tag.putInt("SpeakerRange", speakerRange);
        // 蛍光灯 / 転轍機 (本家 TileEntityFluorescent.dir, TileEnti…
        tag.putByte("FluorescentDir", fluorescentDir);
        tag.putBoolean("PointActivated", pointActivated);
        tag.putFloat("PointMove", pointMove);
        if (!scriptData.isEmpty()) {
            CompoundTag scriptDataTag = new CompoundTag();
            scriptData.forEach(scriptDataTag::putString);
            tag.put("ScriptData", scriptDataTag);
        }
        // 看板: 本家 ResourceStateSignboard と同じキー名なので、本家ワールドのデー…
        if (!signTexts.isEmpty()) {
            ListTag list = new ListTag();
            for (SignboardText text : signTexts) {
                list.add(text.save());
            }
            tag.put("Texts", list);
        }
        tag.putString("TimeTableSetting", signTtSetting);
        tag.putByte("SignDir", signDirection);
        // 列車検知器
        if (detectorTarget != null) {
            tag.putInt("DetectorTargetX", detectorTarget.getX());
            tag.putInt("DetectorTargetY", detectorTarget.getY());
            tag.putInt("DetectorTargetZ", detectorTarget.getZ());
        }
        tag.putBoolean("DetectorPlaceOnDetect", detectorPlaceOnDetect);
        tag.putBoolean("DetectorTrainOnRail", detectorTrainOnRail);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        definitionId = tag.getString("DefinitionId");
        wireModel = tag.getString("WireModelId");
        if (tag.contains("SignalOrigBlock")) {
            this.signalOrigBlock = net.minecraft.nbt.NbtUtils.readBlockState(
                registries.lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK),
                tag.getCompound("SignalOrigBlock"));
        }
        if (tag.contains("SignalOrigTile")) {
            this.signalOrigTileNbt = tag.getCompound("SignalOrigTile").copy();
        }
        this.signalBodyYaw = tag.getFloat("SignalBodyYaw");
        this.connections.clear();
        net.minecraft.nbt.ListTag connList =
            tag.getList("Connections", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = 0; i < connList.size(); i++) {
            CompoundTag e = connList.getCompound(i);
            this.connections.add(new jp.ngt.rtm.electric.Connection(
                e.getBoolean("Root"), e.getInt("X"), e.getInt("Y"), e.getInt("Z"),
                jp.ngt.rtm.electric.Connection.ConnectionType.byId(e.getByte("Type")),
                e.getString("Wire")));
        }
        category = tag.contains("Category") ? tag.getString("Category") : InstalledObjectCategory.LIGHT.name();
        yaw = tag.getFloat("Yaw");
        mountPitch = tag.getFloat("MountPitch");
        mountRoll = tag.getFloat("MountRoll");
        mountFace = tag.contains("MountFace") ? tag.getInt("MountFace") : -1;
        // 未設定なら本家 ResourceState.color の既定 (0xFFFFFF = 白)。
        wireColor = tag.contains("Color") ? tag.getInt("Color") : 0xFFFFFF;
        adjustRoll = tag.getFloat("AdjustRoll");
        adjustPitch = tag.getFloat("AdjustPitch");
        adjustYaw = tag.getFloat("AdjustYaw");
        adjustScale = tag.contains("AdjustScale") ? tag.getFloat("AdjustScale") : 1.0F;
        wireStart = tag.contains("WireStartX") ? new BlockPos(tag.getInt("WireStartX"), tag.getInt("WireStartY"), tag.getInt("WireStartZ")) : null;
        wireEnd = tag.contains("WireEndX") ? new BlockPos(tag.getInt("WireEndX"), tag.getInt("WireEndY"), tag.getInt("WireEndZ")) : null;
        powered = tag.getBoolean("Powered");
        electricity = tag.getInt("Electricity");
        barMoveCount = tag.getInt("BarMoveCount");
        lightCount = tag.contains("LightCount") ? tag.getInt("LightCount") : -1;
        tickCountOnActive = tag.getInt("TickCountOnActive");
        // 本家キー (小文字 float) を優先し、旧 RTMU セーブ ("OffsetX" doubl…
        offsetX = tag.contains("offsetX") ? tag.getFloat("offsetX") : tag.getDouble("OffsetX");
        offsetY = tag.contains("offsetY") ? tag.getFloat("offsetY") : tag.getDouble("OffsetY");
        offsetZ = tag.contains("offsetZ") ? tag.getFloat("offsetZ") : tag.getDouble("OffsetZ");
        signalChannel = tag.contains("SignalChannel") ? tag.getInt("SignalChannel") : -1;
        signalAspect = tag.contains("SignalAspect") ? tag.getInt("SignalAspect") : SignalAspect.STOP.getId();
        // 旧セーブ互換: 信号機は現示 (SignalAspect) と electricity を単一状態…
        if (getCategory() == InstalledObjectCategory.SIGNAL && electricity == 0) {
            electricity = SignalAspect.byId(signalAspect).getLegacyValue();
        }
        speakerRange = tag.contains("SpeakerRange") ? tag.getInt("SpeakerRange") : 32;
        fluorescentDir = tag.getByte("FluorescentDir");
        pointActivated = tag.getBoolean("PointActivated");
        // 本家 TileEntityPoint の既定は 24.0。
        pointMove = tag.contains("PointMove") ? tag.getFloat("PointMove") : 24.0F;
        scriptData.clear();
        if (tag.contains("ScriptData")) {
            CompoundTag scriptDataTag = tag.getCompound("ScriptData");
            for (String key : scriptDataTag.getAllKeys()) {
                scriptData.put(key, scriptDataTag.getString(key));
            }
        }
        // 既設ワールドの設置物にも defaultValues を補完する (未設定キーのみ)
        this.applyDefaultScriptData();
        // 看板
        signTexts.clear();
        if (tag.contains("Texts")) {
            ListTag list = tag.getList("Texts", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                signTexts.add(SignboardText.load(list.getCompound(i)));
            }
        }
        signTtSetting = tag.contains("TimeTableSetting") ? tag.getString("TimeTableSetting") : DEFAULT_TT_SETTING;
        signDirection = tag.getByte("SignDir");
        // 列車検知器
        detectorTarget = tag.contains("DetectorTargetX")
                ? new BlockPos(tag.getInt("DetectorTargetX"), tag.getInt("DetectorTargetY"), tag.getInt("DetectorTargetZ"))
                : null;
        detectorPlaceOnDetect = !tag.contains("DetectorPlaceOnDetect") || tag.getBoolean("DetectorPlaceOnDetect");
        detectorTrainOnRail = tag.getBoolean("DetectorTrainOnRail");
        directTarget = tag.contains("DirectX")
                ? new BlockPos(tag.getInt("DirectX"), tag.getInt("DirectY"), tag.getInt("DirectZ"))
                : null;
    }

    // ───── 本家 TileEntityConnector (入出力コネクタ) ─────

    public void setDirectTarget(BlockPos pos) {
        this.directTarget = pos == null ? null : pos.immutable();
        this.setChanged();
    }

    /** 取り付け先ブロック。本家 ItemInstalledObject はコネクタを置いた瞬間に set… */
    public BlockPos getDirectTarget() {
        if (this.directTarget != null) {
            return this.directTarget;
        }
        int face = this.getMountFace();
        if (face < 0 || face >= net.minecraft.core.Direction.values().length) {
            return null;
        }
        // 取付面 = クリックした面。コネクタはその面の 1 つ手前に置かれるので、逆向きが相手
        return this.worldPosition.relative(net.minecraft.core.Direction.values()[face].getOpposite());
    }

    /** 取り付け先が持っている信号 (本家 Connection.getIProvideElectrici… */
    private int readAttachedElectricity() {
        BlockPos target = this.getDirectTarget();
        if (this.level == null || target == null) {
            return 0;
        }
        net.minecraft.world.level.block.entity.BlockEntity be = this.level.getBlockEntity(target);
        if (be instanceof jp.ngt.rtm.electric.IProvideElectricity provider) {
            return provider.getElectricity();
        }
        if (be instanceof InstalledObjectBlockEntity io) {
            return io.getElectricity();
        }
        // ★本家はエンティティ (EntityElectricalWiring: ATC/列車検知器/車止め…
        jp.ngt.rtm.entity.EntityElectricalWiring wiring =
            jp.ngt.rtm.entity.EntityElectricalWiring.find(this.level, target);
        if (wiring != null) {
            return wiring.getElectricity();
        }
        return 0;
    }

    /** 本家 TileEntityConnector.sendElectricity (INPUT かつ… */
    public void deliverToAttached(int levelValue) {
        BlockPos target = this.getDirectTarget();
        if (this.level == null || target == null) {
            return;
        }
        net.minecraft.world.level.block.entity.BlockEntity be = this.level.getBlockEntity(target);
        if (be instanceof jp.ngt.rtm.electric.IProvideElectricity provider) {
            provider.setElectricity(this.worldPosition.getX(), this.worldPosition.getY(),
                this.worldPosition.getZ(), levelValue);
        } else if (be instanceof InstalledObjectBlockEntity io) {
            io.setElectricity(levelValue);
        } else {
            // 本家は ATC/列車検知器/車止め (EntityElectricalWiring) も取り付け先…
            jp.ngt.rtm.entity.EntityElectricalWiring wiring =
                    jp.ngt.rtm.entity.EntityElectricalWiring.find(this.level, target);
            if (wiring != null) {
                wiring.setElectricity(levelValue);
            }
        }
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    public void setDefinition(String definitionId, InstalledObjectCategory category, float yaw) {
        this.definitionId = definitionId == null ? "" : definitionId;
        this.category = category == null ? InstalledObjectCategory.LIGHT.name() : category.name();
        this.yaw = yaw;
        if (category == InstalledObjectCategory.SIGNAL) {
            this.signalAspect = SignalAspect.STOP.getId();
        }
        this.applyDefaultScriptData();
        setChanged();
    }

    /** 本家 ModelConfig.defaultValues を DataMap (scriptDat… */
    private void applyDefaultScriptData() {
        InstalledObjectDefinition def = this.getDefinition();
        if (def == null) {
            return;
        }
        for (InstalledObjectDefinition.DefaultValue dv : def.getDefaultValues()) {
            if (this.scriptData.containsKey(dv.key())) {
                continue;
            }
            String stored = "List".equalsIgnoreCase(dv.type())
                ? String.join("|", dv.values())
                : dv.value();
            this.scriptData.put(dv.key(), stored);
        }
    }

    /** 張ってある架線のモデル ID。空なら未指定。 */
    public String getWireModelId() {
        // 旧ワールド互換: definitionId を書き換える方式で保存されたものはそれを使う
        if ((this.wireModel == null || this.wireModel.isEmpty())
                && getCategory() == InstalledObjectCategory.WIRE) {
            return this.definitionId;
        }
        return this.wireModel == null ? "" : this.wireModel;
    }

    public String getDefinitionId() {
        return definitionId;
    }

    public InstalledObjectCategory getCategory() {
        try {
            return InstalledObjectCategory.valueOf(category);
        } catch (Exception e) {
            return InstalledObjectCategory.LIGHT;
        }
    }

    public float getYaw() {
        return yaw;
    }

    public float getMountPitch() {
        return mountPitch;
    }

    public void setMountPitch(float mountPitch) {
        this.mountPitch = mountPitch;
        setChanged();
    }

    /** yaw の後に掛けるロール(度)。列車検知器のカント用。 */
    public float getMountRoll() {
        return mountRoll;
    }

    public void setMountRoll(float mountRoll) {
        this.mountRoll = mountRoll;
        setChanged();
    }

    /** 本家 ItemInstalledObject の meta (クリック面 0-5)。 */
    public int getMountFace() {
        return mountFace;
    }

    public void setMountFace(int face) {
        this.mountFace = face;
        setChanged();
    }

    // ===== 本家 GuiChangeOffset の微調整 (追加回転/スケール) =====

    public float getAdjustRoll() {
        return adjustRoll;
    }

    public float getAdjustPitch() {
        return adjustPitch;
    }

    public float getAdjustYaw() {
        return adjustYaw;
    }

    public float getAdjustScale() {
        return adjustScale;
    }

    public void setAdjustments(double offX, double offY, double offZ,
                               float roll, float pitch, float yawAdj, float scale) {
        this.offsetX = offX;
        this.offsetY = offY;
        this.offsetZ = offZ;
        this.adjustRoll = roll;
        this.adjustPitch = pitch;
        this.adjustYaw = yawAdj;
        this.adjustScale = Math.max(0.01F, Math.min(10.0F, scale));
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public void setRenderOffset(double offsetX, double offsetY, double offsetZ) {
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.offsetZ = offsetZ;
        setChanged();
    }

    public Vec3 getRenderOffset() {
        return new Vec3(offsetX, offsetY, offsetZ);
    }

    /** 本家 TileEntitySignal.renderBlock: 信号が置き換えた元ブロック。 */
    public void setSignalOrigBlock(BlockState state) {
        this.signalOrigBlock = state;
        setChanged();
    }

    public BlockState getSignalOrigBlock() {
        return this.signalOrigBlock;
    }

    /** 本家 TileEntitySignal.origTileEntity: 置き換えた元タイルの NB… */
    public void setSignalOrigTileNbt(CompoundTag nbt) {
        this.signalOrigTileNbt = nbt;
        setChanged();
    }

    public CompoundTag getSignalOrigTileNbt() {
        return this.signalOrigTileNbt;
    }

    /** 本家 TileEntitySignal.getRotation (= 本体向き)。 */
    public float getSignalBodyYaw() {
        return this.signalBodyYaw;
    }

    public void setSignalBodyYaw(float yaw) {
        this.signalBodyYaw = yaw % 360.0F;
        setChanged();
    }

    public void setWireEndpoints(BlockPos start, BlockPos end) {
        this.wireStart = start;
        this.wireEnd = end;
        setChanged();
        // 電気配線網に登録 (本家 WireManager)
        if (level != null && !level.isClientSide && start != null && end != null) {
            jp.ngt.rtm.electric.WireManager.register(level, start, end);
        }
        // 架線ジオメトリはパンタの上昇停止位置に使う。描画はクライアント側なので両サイドで登録する
        if (level != null && start != null && end != null) {
            jp.ngt.rtm.electric.WireManager.INSTANCE.addWire(level, start, end);
        }
    }

    /** 本家 electric: コネクタの信号レベル */
    public int getElectricity() {
        return electricity;
    }

    public void setElectricity(int levelValue) {
        // ★本家 TileEntitySignal.setElectricity: 現示はモデルが出せる上限…
        if (getCategory() == InstalledObjectCategory.SIGNAL) {
            com.portofino.realtrainmodunofficial.installedobject.InstalledObjectDefinition def =
                com.portofino.realtrainmodunofficial.installedobject.InstalledObjectRegistry
                    .getById(this.getDefinitionId());
            if (def != null) {
                int max = def.getMaxSignalLevel();
                if (max > 0 && levelValue > max) {
                    levelValue = max;
                }
            }
        }
        // 信号機は「電気値は同じだが現示 (aspect) がズレている」ことがある (ミラー導入前の設置物…
        boolean signalDesynced = getCategory() == InstalledObjectCategory.SIGNAL
                && getLegacySignalState() != levelValue;
        if (this.electricity != levelValue || signalDesynced) {
            this.electricity = levelValue;
            // 本家 TileEntitySpeaker.setElectricity: レベル 1-64 = そ…
            if (isSpeaker() && levelValue >= 1 && levelValue <= 64) {
                playSpeakerSound(levelValue);
            }
            // 本家 TileEntitySignal.setElectricity 同様、信号機は電気レベル=現…
            if (getCategory() == InstalledObjectCategory.SIGNAL) {
                this.signalAspect = com.portofino.realtrainmodunofficial.signal.SignalAspect
                        .byLegacyValue(levelValue).getId();
            }
            setChanged();
            if (level != null && !level.isClientSide) {
                if (getCategory() == InstalledObjectCategory.CONNECTOR_OUTPUT) {
                    // レッドストーン出力の更新
                    level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
                }
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }
    }

    public BlockPos getWireStart() {
        return wireStart;
    }

    public BlockPos getWireEnd() {
        return wireEnd;
    }

    public Vec3 getRenderCenter() {
        return Vec3.atCenterOf(getBlockPos());
    }

    public void setPowered(boolean powered) {
        if (this.powered != powered) {
            this.powered = powered;
            setChanged();
        }
    }

    /** 隣接レッドストーン入力があるか。 */
    private boolean redstoneOn(Level level, BlockPos pos) {
        return level.getBestNeighborSignal(pos) > 0;
    }

    public boolean isPowered() {
        return powered;
    }

    // ---- 看板 ----

    /** 板に貼られている文字。描画側から触るので変更不可で返す。 */
    public List<SignboardText> getSignTexts() {
        return Collections.unmodifiableList(signTexts);
    }

    public String getSignTtSetting() {
        return signTtSetting;
    }

    /** 看板エディタ (SignboardScreen) の保存。サーバー側で呼ばれる。 */
    public void setSignboardData(List<SignboardText> texts, String ttSetting) {
        signTexts.clear();
        if (texts != null) {
            signTexts.addAll(texts);
        }
        signTtSetting = ttSetting == null || ttSetting.isBlank() ? DEFAULT_TT_SETTING : ttSetting;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /** 本家 direction (0-3)。板が向く方角。 */
    public byte getSignDirection() {
        return signDirection;
    }

    public void setSignDirection(byte direction) {
        this.signDirection = (byte) (direction & 3);
        setChanged();
    }

    /** クライアント: フレームアニメの現在位置 (0 .. */
    public int getSignCounter() {
        return signCounter;
    }

    /** 描画結果を左右する状態のシグネチャ (スクリプト描画キャッシュ用)。 */
    public long renderStateSignature() {
        long h = 1125899906842597L;
        for (Map.Entry<String, String> e : scriptData.entrySet()) {
            h = 31L * h + e.getKey().hashCode();
            h = 31L * h + (e.getValue() == null ? 0 : e.getValue().hashCode());
        }
        h = 31L * h + getSignal();
        h = 31L * h + signCounter;
        h = 31L * h + signFlicker;
        h = 31L * h + Float.floatToIntBits(getRotation());
        return h;
    }

    /** 本家 BlockSignBoard.getLightValue の移植。 */
    public int getSignboardLightEmission() {
        InstalledObjectDefinition definition = getDefinition();
        if (definition == null) {
            return 0;
        }
        int value = definition.getLightValue();
        if (value >= 0) {
            return Math.min(15, value);
        }
        if (value == -16) {
            return signFlicker;
        }
        return powered ? Math.min(15, -value) : 0;
    }

    // ---- 列車検知器 ----

    /** 検知時にレッドストーンブロックを操作する座標。未設定なら null。 */
    @javax.annotation.Nullable
    public BlockPos getDetectorTarget() {
        return detectorTarget;
    }

    /** true = 検知したら「置く」 / false = 検知したら「消す」。 */
    public boolean isDetectorPlaceOnDetect() {
        return detectorPlaceOnDetect;
    }

    /** 真下のレールに列車が乗っているか (GUI 表示用)。 */
    public boolean isDetectorTrainOnRail() {
        return detectorTrainOnRail;
    }

    /** 検知器の設定 (GUI から)。サーバー側で呼ばれる。 */
    public void configureDetector(@javax.annotation.Nullable BlockPos target, boolean placeOnDetect) {
        this.detectorTarget = target == null ? null : target.immutable();
        this.detectorPlaceOnDetect = placeOnDetect;
        setChanged();
        if (level != null && !level.isClientSide) {
            // 設定を変えた瞬間に対象ブロックを今の在線状態に合わせる。
            applyDetectorOutput(level, detectorTrainOnRail);
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /** 本家 EntityTrainDetector 準拠: 自分の真下 8 ブロック以内で最初に見つかっ… */
    private static boolean detectTrainOnRailBelow(Level level, BlockPos pos) {
        for (int i = 0; i < DETECTOR_RAIL_SEARCH_DEPTH; i++) {
            BlockPos p = pos.below(i);
            if (!level.isLoaded(p)) {
                continue;
            }
            if (level.getBlockEntity(p) instanceof jp.ngt.rtm.rail.TileEntityLargeRailBase rail) {
                return rail.isTrainOnRail();
            }
        }
        return false;
    }

    /** 検知結果を対象座標のレッドストーンブロックに反映する。 */
    private void applyDetectorOutput(Level level, boolean detected) {
        BlockPos target = detectorTarget;
        if (target == null || level.isClientSide || !level.isLoaded(target)) {
            return;
        }
        // 置くモード: 検知中 = レッドストーンブロックあり / 消すモード: 検知中 = なし
        boolean wantRedstone = detected == detectorPlaceOnDetect;
        BlockState current = level.getBlockState(target);
        if (wantRedstone) {
            if (current.isAir()) {
                level.setBlockAndUpdate(target, Blocks.REDSTONE_BLOCK.defaultBlockState());
            }
        } else if (current.is(Blocks.REDSTONE_BLOCK)) {
            level.setBlockAndUpdate(target, Blocks.AIR.defaultBlockState());
        }
    }

    public int getBarMoveCount() {
        return barMoveCount;
    }

    public boolean isTicketGateOpen() {
        return getCategory() == InstalledObjectCategory.TICKET_GATE && powered;
    }

    public void activateTicketGate() {
        if (getCategory() != InstalledObjectCategory.TICKET_GATE) {
            return;
        }
        powered = true;
        tickCountOnActive = 0;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public int getLightCount() {
        if (isSignal()) {
            return getLegacySignalState();
        }
        return lightCount;
    }

    public boolean isSignal() {
        return getCategory() == InstalledObjectCategory.SIGNAL;
    }

    public boolean isSpeaker() {
        return getCategory() == InstalledObjectCategory.SPEAKER;
    }

    /** スピーカーが音を鳴らす範囲(ブロック)。 */
    /** 本家 TileEntitySpeaker.setSound/getSound 相当 (スピーカーご… */
    public void setSpeakerSound(int index, String sound) {
        scriptData.put("SpeakerSound" + index, sound == null ? "" : sound);
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public String getSpeakerSound(int index) {
        String own = scriptData.get("SpeakerSound" + index);
        if (own != null && !own.isBlank()) {
            return own;
        }
        return com.portofino.realtrainmodunofficial.installedobject.SpeakerSoundConfig.getSound(index);
    }

    /** 本家 TileEntitySpeaker.setElectricity 相当の発音 (index… */
    public void playSpeakerSound(int index) {
        if (level == null || level.isClientSide || !isSpeaker()) {
            return;
        }
        String sound = getSpeakerSound(index);
        if (sound == null || sound.isBlank()) {
            return;
        }
        double cx = worldPosition.getX() + 0.5D;
        double cy = worldPosition.getY() + 0.5D;
        double cz = worldPosition.getZ() + 0.5D;
        int range = getSpeakerRange();
        var payload = new com.portofino.realtrainmodunofficial.network.SpeakerPlayPayload(
                -1, cx, cy, cz, sound, -1.0F, 1.0F, range);
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            for (net.minecraft.server.level.ServerPlayer p : serverLevel.players()) {
                if (p.distanceToSqr(cx, cy, cz) <= 256.0D * 256.0D) {
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p, payload);
                }
            }
        }
    }

    public int getSpeakerRange() {
        return speakerRange;
    }

    public void setSpeakerRange(int range) {
        this.speakerRange = Math.max(1, Math.min(256, range));
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }

    /** Legacy RTM signal scripts read the current aspect… */
    public int getSignal() {
        return Math.max(0, getLegacySignalState());
    }

    /** Legacy RTM signal scripts query a resource-state… */
    public ResourceStateCompat getResourceState() {
        return new ResourceStateCompat(this);
    }

    /** Legacy 1.7-style scripts sometimes access modelSe… */
    public javax.script.ScriptEngine getServerScriptEngine() {
        return this.getModelSet().serverSE;
    }

    /** 本家 IModelSelector.getModelSet。 */
    public jp.ngt.rtm.modelpack.modelset.ModelSetBase<?> getModelSet() {
        InstalledObjectDefinition def = this.getDefinition();
        String key = "machine:" + (def == null ? "" : def.getId());
        float[] offset = null;
        float[] bodyPos = null;
        if (def != null) {
            var mo = def.getModelOffset();
            offset = new float[]{(float) mo.x, (float) mo.y, (float) mo.z};
            var bp = def.getScriptBodyPos();
            bodyPos = new float[]{(float) bp.x, (float) bp.y, (float) bp.z};
        }
        jp.ngt.rtm.modelpack.modelset.ModelSetBase<?> set =
                jp.ngt.rtm.modelpack.ModelPackManager.INSTANCE.getScriptOnlyModelSet(
                        key,
                        def == null ? "" : def.getBareName(),
                        "ModelMachine",
                        def == null ? null : def.getServerScriptPath(),
                        null, null, def == null ? null : def.getButtonTexture(),
                        offset, bodyPos);
        if (def != null && set instanceof jp.ngt.rtm.modelpack.modelset.ModelSetScriptOnly scriptOnly) {
            var config = scriptOnly.getConfig();
            config.machineType = def.getSubType();
            config.followRailAngle = def.isFollowRailAngle();
            config.smoothing = def.isSmoothing();
            config.doCulling = def.isDoCulling();
        }
        return set;
    }

    /** 本家 EntityInstalledObject.getModelType は設置物では常に "M… */
    @Override
    public String getModelType() {
        return "ModelMachine";
    }

    /** 本家 IModelSelector.setModelName: 設置物のモデルを差し替える。 */
    @Override
    public void setModelName(String name) {
        if (name == null || name.isBlank()) {
            return;
        }
        InstalledObjectDefinition def = InstalledObjectRegistry.getById(name);
        if (def == null) {
            def = InstalledObjectRegistry.getByBareName(name, this.getCategory());
        }
        if (def == null) {
            return;
        }
        this.setDefinition(def.getId(), def.getCategory(), this.yaw);
        if (this.level != null && !this.level.isClientSide()) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }

    /** The installed-object renderer already applies blo… */
    @Override
    public float getRotation() {
        // 本家 TileEntityPlaceable.getRotation。
        return this.yaw;
    }

    @Override
    public void setRotation(float rotation, boolean sync) {
        this.yaw = rotation % 360.0F;
        setChanged();
        if (sync && level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public float getOffsetX() {
        return (float) this.offsetX;
    }

    @Override
    public float getOffsetY() {
        return (float) this.offsetY;
    }

    @Override
    public float getOffsetZ() {
        return (float) this.offsetZ;
    }

    /** Slanted placement is not implemented for installe… */
    public float getBlockDirection() {
        return 0.0F;
    }

    public int getSignalChannel() {
        return signalChannel;
    }

    public void setSignalChannel(int signalChannel, boolean updateClient) {
        this.signalChannel = signalChannel;
        setChanged();
        if (updateClient && level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public SignalAspect getSignalAspect() {
        return SignalAspect.byId(signalAspect);
    }

    /** RTM signal scripts use sparse numeric states rath… */
    public int getLegacySignalState() {
        return getSignalAspect().getLegacyValue();
    }

    public void setSignalAspect(SignalAspect aspect, boolean updateClient) {
        SignalAspect a = aspect == null ? SignalAspect.STOP : aspect;
        this.signalAspect = a.getId();
        // electricity と双方向ミラー (setElectricity 側のコメント参照)
        this.electricity = a.getLegacyValue();
        setChanged();
        if (updateClient && level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public String getModelName() {
        int index = definitionId.lastIndexOf(':');
        return index >= 0 ? definitionId.substring(index + 1) : definitionId;
    }

    // ---- Legacy RTM script position/direction accesso…

    public int getX() { return worldPosition.getX(); }
    public int getY() { return worldPosition.getY(); }
    public int getZ() { return worldPosition.getZ(); }

    /** 0–3 facing direction derived from yaw (0=south,1=… */
    public int getDir() {
        if (getCategory() == InstalledObjectCategory.FLUORESCENT) {
            return fluorescentDir;
        }
        return Math.floorMod(Math.round(yaw / 90.0F), 4);
    }

    // ---- 蛍光灯 (本家 TileEntityFluorescent) ----

    public void setFluorescentDir(byte dir) {
        this.fluorescentDir = dir;
        setChanged();
    }

    /** 壊れた蛍光灯 (本家 meta==2 / モデル名 *Broken) か。 */
    public boolean isBrokenFluorescent() {
        if (!definitionId.equals(brokenFluorescentKey)) {
            brokenFluorescentKey = definitionId;
            brokenFluorescentVal = getCategory() == InstalledObjectCategory.FLUORESCENT
                && getModelName().toLowerCase(java.util.Locale.ROOT).contains("broken");
        }
        return brokenFluorescentVal;
    }

    /** 本家 BlockFluorescent.getLightValue: 壊れた蛍光灯は 0/4/8/… */
    public int getFluorescentLightValue() {
        return isBrokenFluorescent() ? fluorescentLight : 15;
    }

    /** 壊れた蛍光灯の明滅。3tick ごとに明るさを振り直して再ライティングを要求する。 */
    private static void tickFluorescentFlicker(Level level, BlockPos pos, InstalledObjectBlockEntity be) {
        if (!be.isBrokenFluorescent()) {
            return;
        }
        if (++be.fluorescentFlickerTick < 3) {
            return;
        }
        be.fluorescentFlickerTick = 0;
        int next = switch (level.random.nextInt(4)) {
            case 0 -> 0;
            case 1 -> 4;
            case 2 -> 8;
            default -> 12;
        };
        if (next != be.fluorescentLight) {
            be.fluorescentLight = next;
            // 本家 world.checkLight(pos) 相当。
            level.getLightEngine().checkBlock(pos);
        }
    }

    // ---- 転轍機 (本家 TileEntityPoint) ----

    /** 本家 (1.12 系) TileEntityPoint.isActivated。 */
    public boolean isActivated() {
        return this.isPointActivated();
    }

    public boolean isPointActivated() {
        return pointActivated;
    }

    public void setPointActivated(boolean activated) {
        this.pointActivated = activated;
        setChanged();
        sync();
    }

    public float getPointMove() {
        return pointMove;
    }

    /** バールでの右クリックで符号が反転し、転轍機の本体が線路の反対側に移る (本家 BlockPoint… */
    public void setPointMove(float move) {
        this.pointMove = move;
        setChanged();
        sync();
    }

    private void sync() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /** 指定方向 (RTM side 0-5) の隣に接続相手がいるか。 */
    public boolean isConnected(int side) {
        if (level == null || side < 0 || side > 5 || getCategory() != InstalledObjectCategory.PIPE) {
            return false;
        }
        net.minecraft.core.BlockPos neighbor = worldPosition.relative(net.minecraft.core.Direction.from3DDataValue(side));
        return level.getBlockEntity(neighbor) instanceof InstalledObjectBlockEntity io
            && io.getCategory() == InstalledObjectCategory.PIPE;
    }

    /** このブロックが貼り付いている面 (0=下,1=上,2-5=NSWE)。 */
    public int getAttachedSide() {
        if (getCategory() == InstalledObjectCategory.PIPE) {
            return mountFace >= 0 ? mountFace : 1;
        }
        return 1;
    }

    /** Random decorative scale factor (used by RenderPal… */
    public float getRandomScale() { return 1.0F; }

    public InstalledObjectDefinition getDefinition() {
        return InstalledObjectRegistry.getById(definitionId);
    }

    /** 本家 ResourceState.color 相当。 */
    public int getWireColor() {
        return wireColor;
    }

    public void setWireColor(int color) {
        this.wireColor = color;
        setChanged();
    }

    // --- NGTO Builder の Wire ツール互換 ---

    /** Wire ツールが碍子の微調整オフセットを設定する (本家 TileEntityConnector… */
    @Override
    public void setOffset(double x, double y, double z, boolean sync) {
        this.offsetX = x;
        this.offsetY = y;
        this.offsetZ = z;
        setChanged();
        if (sync && level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /** Wire ツールが「この碍子から (x,y,z) の碍子へワイヤーを張る」ときに呼ぶ (本家 se… */
    /** 本家 getConnectionList。描画側が root だけを拾う。 */
    public java.util.List<jp.ngt.rtm.electric.Connection> getConnectionList() {
        return this.connections;
    }

    /** 本家 setConnectionFrom: 張られた側 (root でない) の接続を足す。 */
    public void setConnectionFrom(int x, int y, int z, jp.ngt.rtm.electric.Connection.ConnectionType type,
                                  String wireName) {
        this.connections.removeIf(c -> c.x == x && c.y == y && c.z == z);
        this.connections.add(new jp.ngt.rtm.electric.Connection(false, x, y, z, type, wireName));
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /** 逆接続 (張られた側) を削除する。エンティティ側の接続解除から呼ばれる。 */
    public void removeConnectionAt(int x, int y, int z) {
        this.connections.removeIf(c -> c.x == x && c.y == y && c.z == z);
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public void setConnectionTo(int x, int y, int z, Object connectionType, Object modelState) {
        // 本家 TileEntityElectricalWiring.setConnectionTo の移植。
        jp.ngt.rtm.electric.Connection.ConnectionType type =
            connectionType instanceof jp.ngt.rtm.electric.Connection.ConnectionType ct
                ? ct : jp.ngt.rtm.electric.Connection.ConnectionType.WIRE;
        net.minecraft.core.BlockPos targetPos = new net.minecraft.core.BlockPos(x, y, z);
        if (type == jp.ngt.rtm.electric.Connection.ConnectionType.NONE) {
            // 切断 (本家と同じ): 自分と相手の両方から外す
            this.connections.removeIf(c -> c.x == x && c.y == y && c.z == z);
            if (level != null && level.getBlockEntity(targetPos)
                    instanceof InstalledObjectBlockEntity target) {
                target.connections.removeIf(c -> c.x == this.worldPosition.getX()
                    && c.y == this.worldPosition.getY() && c.z == this.worldPosition.getZ());
                target.setChanged();
                if (!level.isClientSide) {
                    level.sendBlockUpdated(target.worldPosition, target.getBlockState(),
                        target.getBlockState(), 3);
                }
            }
            setChanged();
            if (level != null && !level.isClientSide) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
                jp.ngt.rtm.electric.WireManager.unregister(level, this.worldPosition, targetPos);
            }
            return;
        }
        String wireModelId = null;
        if (modelState instanceof com.portofino.realtrainmodunofficial.item.WireItem.WireModelState wms) {
            wireModelId = wms.modelId;
        } else if (modelState instanceof String s) {
            wireModelId = s;
        }
        // 本家: 自分に root の接続を足し、相手には root でない接続を足す (setConnec…
        this.connections.removeIf(c -> c.x == x && c.y == y && c.z == z);
        this.connections.add(new jp.ngt.rtm.electric.Connection(
            true, x, y, z, type, wireModelId == null ? "" : wireModelId));
        if (level != null && level.getBlockEntity(targetPos)
                instanceof InstalledObjectBlockEntity target) {
            target.setConnectionFrom(this.worldPosition.getX(), this.worldPosition.getY(),
                this.worldPosition.getZ(), type, wireModelId == null ? "" : wireModelId);
        }
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            // 電気グラフ (WebCTC 連動等)。再読込時は onLoad が接続から再登録する。
            jp.ngt.rtm.electric.WireManager.register(level, this.worldPosition, targetPos);
        }
    }

    /** ロード済み設置物 (WebCTC の信号地図 API が使用。 */
    private static final java.util.Set<InstalledObjectBlockEntity> LOADED_OBJECTS =
            java.util.Collections.synchronizedSet(
                    java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>()));

    public static java.util.List<InstalledObjectBlockEntity> getLoadedObjects(net.minecraft.world.level.Level level) {
        synchronized (LOADED_OBJECTS) {
            return LOADED_OBJECTS.stream()
                    .filter(o -> o.getLevel() == level && !o.isRemoved())
                    .collect(java.util.stream.Collectors.toList());
        }
    }

    // ★@Override を付けないこと: これは NeoForge が足したメソッドで、バニラには無い
    public void onLoad() {
        // super.onLoad; — バニラの BlockEntity には onLoad が無い
        LOADED_OBJECTS.add(this);
        if (level instanceof ServerLevel serverLevel && isSignal()) {
            SignalNetworkSavedData.get(serverLevel).syncLoadedSignal(serverLevel, this);
        }
        // ワイヤーは配線網 (本家 WireManager) へ登録
        if (level != null && getCategory() == InstalledObjectCategory.WIRE
                && wireStart != null && wireEnd != null) {
            if (!level.isClientSide) {
                jp.ngt.rtm.electric.WireManager.register(level, wireStart, wireEnd);
            }
            jp.ngt.rtm.electric.WireManager.INSTANCE.addWire(level, wireStart, wireEnd);
        }
        // 接続式 (本家構造) の配線も同様に登録
        if (level != null) {
            for (jp.ngt.rtm.electric.Connection c : this.connections) {
                if (!c.isRoot || c.type != jp.ngt.rtm.electric.Connection.ConnectionType.WIRE) {
                    continue;
                }
                net.minecraft.core.BlockPos target = new net.minecraft.core.BlockPos(c.x, c.y, c.z);
                if (!level.isClientSide) {
                    jp.ngt.rtm.electric.WireManager.register(level, worldPosition, target);
                }
                jp.ngt.rtm.electric.WireManager.INSTANCE.addWire(level, worldPosition, target);
            }
        }
    }

    @Override
    public void setRemoved() {
        if (level != null && getCategory() == InstalledObjectCategory.WIRE
                && wireStart != null && wireEnd != null) {
            if (!level.isClientSide) {
                jp.ngt.rtm.electric.WireManager.unregister(level, wireStart, wireEnd);
            }
            jp.ngt.rtm.electric.WireManager.INSTANCE.removeWire(level, wireStart, wireEnd);
        }
        // 本家 EntityATC.attackEntityFrom: ATC を撤去したらレールの信号を…
        if (level != null && !level.isClientSide && getCategory() == InstalledObjectCategory.ATC) {
            setRailSignal(level, worldPosition, 0);
        }
        // 接続式 (本家構造) の配線も解除
        if (level != null) {
            for (jp.ngt.rtm.electric.Connection c : this.connections) {
                if (!c.isRoot || c.type != jp.ngt.rtm.electric.Connection.ConnectionType.WIRE) {
                    continue;
                }
                net.minecraft.core.BlockPos target = new net.minecraft.core.BlockPos(c.x, c.y, c.z);
                if (!level.isClientSide) {
                    jp.ngt.rtm.electric.WireManager.unregister(level, worldPosition, target);
                }
                jp.ngt.rtm.electric.WireManager.INSTANCE.removeWire(level, worldPosition, target);
            }
        }
        super.setRemoved();
    }

    /** ATC 地上子: 下のレールの signal に値を書き込む。 */
    private void emitAtcSignal(Level level, BlockPos pos) {
        int value = Math.max(0, Math.min(15, this.electricity));
        setRailSignal(level, pos, value);
    }

    /** 本家でエンティティ実装になっているカテゴリか (ATC / 列車検知器 / 車止め)。 */
    private static boolean isLegacyEntityCategory(InstalledObjectCategory category) {
        return category == InstalledObjectCategory.ATC
            || category == InstalledObjectCategory.TRAIN_DETECTOR
            || category == InstalledObjectCategory.BUMPING_POST;
    }

    /** 旧ワールドの該当設置物ブロックを、本家と同じエンティティへ変換する (移行処理)。 */
    private void convertToInstalledEntity(Level level, BlockPos pos) {
        jp.ngt.rtm.entity.EntityInstalledObject entity = switch (getCategory()) {
            case ATC -> new jp.ngt.rtm.entity.EntityATC(
                com.portofino.realtrainmodunofficial.registry.RealTrainModUnofficialEntities.ATC.get(), level);
            case TRAIN_DETECTOR -> new jp.ngt.rtm.entity.EntityTrainDetector(
                com.portofino.realtrainmodunofficial.registry.RealTrainModUnofficialEntities
                    .TRAIN_DETECTOR.get(), level);
            case BUMPING_POST -> new jp.ngt.rtm.entity.EntityBumpingPost(
                com.portofino.realtrainmodunofficial.registry.RealTrainModUnofficialEntities
                    .BUMPING_POST_ENTITY.get(), level);
            default -> null;
        };
        if (entity == null) {
            return;
        }
        entity.setModelName(this.getDefinitionId());
        // 移行も本家の位置 (レールブロックの Y) に合わせる。
        net.minecraft.world.phys.Vec3 off = this.getRenderOffset();
        entity.moveTo(pos.getX() + 0.5D + off.x, pos.getY() - 1.0D + off.y, pos.getZ() + 0.5D + off.z,
            this.getYaw(), 0.0F);
        level.addFreshEntity(entity);
        level.removeBlock(pos, false);
    }

    /** レールの signal へ書き込む (本家 EntityATC.setSignalToRail 相… */
    private void setRailSignal(Level level, BlockPos pos, int value) {
        jp.ngt.rtm.rail.TileEntityLargeRailBase rail =
                jp.ngt.rtm.rail.TileEntityLargeRailBase.getRailFromCoordinates(
                        level, pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        if (rail != null) {
            jp.ngt.rtm.rail.TileEntityLargeRailCore core = rail.getRailCore();
            if (core != null) {
                core.setSignal(value);
            }
        }
    }

    /** 本家 TileEntityMachineBase.isGettingPower: スクリプトがフィ… */
    public boolean isGettingPower;

    public static void tick(Level level, BlockPos pos, BlockState state, InstalledObjectBlockEntity be) {
        // 本家 TileEntityMachineBase.isGettingPower を毎 tick 更…
        be.isGettingPower = be.isPowered();
        // ATS 連動: 信号機は現示 (legacy 値) を signalLevel フィールドに毎 t…
        if (be.getCategory() == InstalledObjectCategory.SIGNAL) {
            be.signalLevel = be.getLegacySignalState();
        }
        // 蛍光灯は明滅以外にやることが無い。明るさはクライアント/サーバー両方で使うので サイド分岐より前に…
        if (be.getCategory() == InstalledObjectCategory.FLUORESCENT) {
            tickFluorescentFlicker(level, pos, be);
            return;
        }
        if (level.isClientSide) {
            // 看板: 本家 TileEntitySignBoard.update — フレームアニメを進める。
            if (be.getCategory() == InstalledObjectCategory.SIGNBOARD) {
                InstalledObjectDefinition signDef = be.getDefinition();
                int period = signDef == null ? 1 : Math.max(1, signDef.getSignFrame() * signDef.getAnimationCycle());
                be.signCounter = (be.signCounter + 1) % period;
            }
            // sound_Running を持つ設置オブジェクト(スピーカー/サイレン/踏切など)は種別を問わず…
            InstalledObjectDefinition definition = be.getDefinition();
            String running = definition == null ? null : definition.getRunningSound();
            if (running != null && !running.isBlank()) {
                ClientHooks.tickCrossingGateSound(be);
            }
            // 軽量化: 走行音を持たない設置物 (標識/照明/架線柱/信号機など大多数) は「鳴らす音が無い＝…
            return;
        }
        // 本家 EntityInstalledObject.onUpdate: サーバー側では毎 tick、…
        if (isLegacyEntityCategory(be.getCategory())) {
            be.convertToInstalledEntity(level, pos);
            return;
        }
        if (be.getCategory() == InstalledObjectCategory.ATC
                && (be.getDefinition() == null || !be.getDefinition().hasServerScript())) {
            be.emitAtcSignal(level, pos);
            return;
        }
        if (be.getDefinition() != null && be.getDefinition().hasServerScript()) {
            if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                be.refreshScriptFields();
                be.getScriptExecuter(serverLevel).execScript(be);
            }
            // ★ここで return してよいのは検知器だけ。
            if (be.getCategory() == InstalledObjectCategory.TRAIN_DETECTOR) {
                return;
            }
        }
        // 看板: 本家 TileEntitySignBoard.update — レッドストーン状態とランダ…
        if (be.isSpeaker()) {
            boolean sig = be.redstoneOn(level, pos);
            if (sig && !be.powered) {
                be.playSpeakerSound(1);   // 立ち上がりでスロット1の音を再生
            }
            be.powered = sig;
            return;
        }
        if (be.getCategory() == InstalledObjectCategory.SIGNBOARD) {
            InstalledObjectDefinition signDef = be.getDefinition();
            int lightValue = signDef == null ? 0 : signDef.getLightValue();
            boolean redstone = be.redstoneOn(level, pos);
            if (be.powered != redstone) {
                be.powered = redstone;
                be.setChanged();
            }
            if (lightValue == -16) {
                // 本家: ランダム点滅 (0,3,6,9,12,15)
                be.signFlicker = SIGN_RANDOM.nextInt(6) * 3;
            }
            int light = be.getSignboardLightEmission();
            if (light != be.signLastLight) {
                be.signLastLight = light;
                level.getLightEngine().checkBlock(pos);
            }
            return;
        }
        // 列車検知器: 本家 EntityTrainDetector — 真下のレールの在線を見る。
        if (be.getCategory() == InstalledObjectCategory.TRAIN_DETECTOR) {
            if ((level.getGameTime() + pos.asLong()) % DETECTOR_INTERVAL != 0L) {
                return;
            }
            boolean onRail = detectTrainOnRailBelow(level, pos);
            if (be.detectorTrainOnRail != onRail) {
                be.detectorTrainOnRail = onRail;
                be.setChanged();
                level.sendBlockUpdated(pos, state, state, 3);
            }
            // ★本家 EntityTrainDetector.getElectricity(): 検知=STOP…
            int detectLevel = onRail ? 0 : 5;
            if (be.electricity != detectLevel) {
                be.setElectricity(detectLevel);
                be.setChanged();
                level.sendBlockUpdated(pos, state, state, 3);
            }
            // 毎回「あるべき状態」に合わせる (壊された/チャンクを読み直した場合も復旧する)
            be.applyDetectorOutput(level, onRail);
            return;
        }
        // ★本家 TileEntityConnector.checkSignalOutput: 出力コネクタ…
        if (be.getCategory() == InstalledObjectCategory.CONNECTOR_OUTPUT) {
            int sig = be.readAttachedElectricity();
            if (sig != be.prevOutputSignal) {
                be.prevOutputSignal = sig;
                be.setElectricity(sig);
                jp.ngt.rtm.electric.WireManager.propagate(level, pos, sig);
            }
            return;
        }
        if (be.getCategory() == InstalledObjectCategory.CONNECTOR_INPUT) {
            return;
        }
        if (be.getCategory() == InstalledObjectCategory.TICKET_GATE) {
            boolean changed = false;
            // 本家 BlockTurnstile は切符で開くだけ (RS 開扉は本家に無い独自拡張だったため撤…
            if (be.powered) {
                if (be.barMoveCount < 90) {
                    be.barMoveCount = Math.min(90, be.barMoveCount + Math.max(1, 90 / TICKET_GATE_MOVE_TICKS));
                    changed = true;
                }
                be.tickCountOnActive++;
                if (be.tickCountOnActive >= TICKET_GATE_OPEN_TICKS) {
                    be.powered = false;
                    be.tickCountOnActive = 0;
                    changed = true;
                }
            } else {
                if (be.barMoveCount > 0) {
                    be.barMoveCount = Math.max(0, be.barMoveCount - Math.max(1, 90 / TICKET_GATE_MOVE_TICKS));
                    changed = true;
                }
            }
            if (changed) {
                be.setChanged();
                level.sendBlockUpdated(pos, state, state, 3);
            }
            return;
        }
        // 照明 (ミラーボール・赤色灯・サーチライト等): レッドストーンで点灯/回転する。
        if (be.getCategory() == InstalledObjectCategory.LIGHT) {
            boolean redstone = be.redstoneOn(level, pos);
            if (be.powered != redstone) {
                be.powered = redstone;
                be.setChanged();
                level.sendBlockUpdated(pos, state, state, 3);
                // 光源レベルが powered で変わる (InstalledObjectBlock.getLigh…
                level.getLightEngine().checkBlock(pos);
            }
            return;
        }
        // サーバー側: 遮断桿アニメ等は踏切ロジック対象のみ
        if (!be.shouldHandleCrossingLogic()) {
            return;
        }
        // 踏切のレッドストーン受信を毎tick再評価する。
        boolean redstone = be.redstoneOn(level, pos);
        boolean changed = false;
        if (be.powered != redstone) {
            be.powered = redstone;
            changed = true;
        }
        if (be.powered) {
            if (be.barMoveCount < 90) {
                be.barMoveCount++;
                changed = true;
            }
            be.tickCountOnActive = (be.tickCountOnActive + 1) % 360;
            int previousLight = be.lightCount;
            if (be.lightCount < 0) {
                be.lightCount = 0;
            } else if (be.tickCountOnActive % 10 == 0) {
                be.lightCount = (be.lightCount + 1) % 2;
            }
            changed |= previousLight != be.lightCount;
        } else {
            if (be.barMoveCount > 0) {
                be.barMoveCount--;
                changed = true;
            }
            if (be.tickCountOnActive != 0 || be.lightCount != -1) {
                be.tickCountOnActive = 0;
                be.lightCount = -1;
                changed = true;
            }
        }
        if (changed) {
            be.setChanged();
            level.sendBlockUpdated(pos, state, state, 3);
        }
    }

    public static final class ResourceStateCompat {
        private final InstalledObjectBlockEntity blockEntity;

        public ResourceStateCompat(InstalledObjectBlockEntity blockEntity) {
            this.blockEntity = blockEntity;
        }

        public jp.ngt.rtm.modelpack.modelset.ModelSetBase<?> getResourceSet() {
            return blockEntity == null ? null : blockEntity.getModelSet();
        }

        public DataMapCompat getDataMap() {
            return new DataMapCompat(blockEntity);
        }

        public String getResourceName() {
            return blockEntity == null ? "" : blockEntity.getModelName();
        }
    }

    public static final class DataMapCompat {
        private final InstalledObjectBlockEntity blockEntity;
        private final Map<String, Object> values = new HashMap<>();

        public DataMapCompat(InstalledObjectBlockEntity blockEntity) {
            this.blockEntity = blockEntity;
            refresh();
        }

        public boolean contains(String key) {
            refresh();
            return values.containsKey(key) || blockEntity != null && blockEntity.scriptData.containsKey(key);
        }

        public Object get(String key) {
            refresh();
            Object value = values.get(key);
            if (value != null) {
                return value;
            }
            return blockEntity == null ? null : blockEntity.scriptData.get(key);
        }

        public int getInt(String key) {
            Object value = get(key);
            if (value instanceof Number number) {
                return number.intValue();
            }
            if (value instanceof Boolean bool) {
                return bool ? 1 : 0;
            }
            if (value instanceof String string) {
                try {
                    return Integer.decode(string);
                } catch (NumberFormatException ignored) {
                    try {
                        return (int) Math.round(Double.parseDouble(string));
                    } catch (NumberFormatException ignoredAgain) {
                    }
                }
            }
            return 0;
        }

        public int getHex(String key) {
            return getInt(key);
        }

        public double getDouble(String key) {
            Object value = get(key);
            if (value instanceof Number number) {
                return number.doubleValue();
            }
            if (value instanceof Boolean bool) {
                return bool ? 1.0D : 0.0D;
            }
            if (value instanceof String string) {
                try {
                    return Double.parseDouble(string);
                } catch (NumberFormatException ignored) {
                }
            }
            return 0.0D;
        }

        public jp.ngt.ngtlib.math.Vec3 getVec(String key) {
            Object value = this.get(key);
            if (value instanceof jp.ngt.ngtlib.math.Vec3 vec) {
                return vec;
            }
            String text = value == null ? "" : String.valueOf(value);
            Object parsed = parseVecLike(text);
            return parsed instanceof jp.ngt.ngtlib.math.Vec3 vec ? vec : jp.ngt.ngtlib.math.Vec3.ZERO;
        }

        public void setVec(String key, jp.ngt.ngtlib.math.Vec3 value, int syncType) {
            jp.ngt.ngtlib.math.Vec3 vec = value == null ? jp.ngt.ngtlib.math.Vec3.ZERO : value;
            String text = vec.getX() + " " + vec.getY() + " " + vec.getZ();
            values.put(key, vec);
            if (blockEntity != null) {
                blockEntity.scriptData.put(key, text);
                blockEntity.setChanged();
            }
        }

        public boolean getBoolean(String key) {
            Object value = get(key);
            if (value instanceof Boolean bool) {
                return bool;
            }
            if (value instanceof Number number) {
                return number.intValue() != 0;
            }
            if (value instanceof String string) {
                return Boolean.parseBoolean(string) || "1".equals(string);
            }
            return false;
        }

        public String getString(String key) {
            Object value = get(key);
            return value == null ? "" : String.valueOf(value);
        }

        public void setBoolean(String key, boolean value, int syncType) {
            values.put(key, value);
            if (blockEntity != null) {
                blockEntity.scriptData.put(key, Boolean.toString(value));
                blockEntity.setChanged();
            }
        }

        public void setInt(String key, int value, int syncType) {
            values.put(key, value);
            if (blockEntity != null) {
                blockEntity.scriptData.put(key, Integer.toString(value));
                blockEntity.setChanged();
            }
        }

        public void setDouble(String key, double value, int syncType) {
            values.put(key, value);
            if (blockEntity != null) {
                blockEntity.scriptData.put(key, Double.toString(value));
                blockEntity.setChanged();
            }
        }

        public void setString(String key, String value, int syncType) {
            String safeValue = value == null ? "" : value;
            values.put(key, safeValue);
            if (blockEntity != null) {
                blockEntity.scriptData.put(key, safeValue);
                blockEntity.setChanged();
            }
        }

        /** 本家 (1.12 系) DataMap: 配列。 */
        public Object[] getArray(String key) {
            Object value = get(key);
            if (value instanceof Object[] arr) {
                return arr;
            }
            if (value instanceof java.util.Collection<?> col) {
                return col.toArray();
            }
            if (value instanceof String s && !s.isEmpty()) {
                String[] parts = s.split("\\|");
                Object[] out = new Object[parts.length];
                for (int i = 0; i < parts.length; i++) {
                    out[i] = parseVecLike(parts[i]);
                }
                return out;
            }
            return new Object[0];
        }

        public void setArray(String key, Object value, int syncType) {
            Object stored = value;
            if (value instanceof java.util.Collection<?> col) {
                stored = col.toArray();
            }
            values.put(key, stored);
            if (blockEntity != null) {
                StringBuilder sb = new StringBuilder();
                if (stored instanceof Object[] arr) {
                    for (Object o : arr) {
                        if (sb.length() > 0) {
                            sb.append('|');
                        }
                        sb.append(o == null ? "" : o);
                    }
                } else if (stored != null) {
                    sb.append(stored);
                }
                blockEntity.scriptData.put(key, sb.toString());
                blockEntity.setChanged();
            }
        }

        /** "x y z" を Vec3 に、それ以外は文字列のまま返す。 */
        private static Object parseVecLike(String s) {
            String[] n = s == null ? new String[0] : s.trim().split("\\s+");
            if (n.length >= 3) {
                try {
                    return new jp.ngt.ngtlib.math.Vec3(
                        Double.parseDouble(n[0]), Double.parseDouble(n[1]), Double.parseDouble(n[2]));
                } catch (NumberFormatException ignored) {
                    // 数値でなければ文字列のまま
                }
            }
            return s;
        }

        private void refresh() {
            if (blockEntity == null) {
                return;
            }
            values.put("powered", blockEntity.isPowered() ? 1 : 0);
            values.put("isPowered", blockEntity.isPowered());
            // 本家 TileEntityMachineBase.isGettingPower。
            values.put("isGettingPower", blockEntity.isPowered());
            values.put("barMoveCount", blockEntity.getBarMoveCount());
            values.put("lightCount", blockEntity.getLightCount());
            values.put("signal", blockEntity.getSignal());
            values.put("signalAspect", blockEntity.getLegacySignalState());
            values.put("yaw", blockEntity.getYaw());
            blockEntity.scriptData.forEach(values::putIfAbsent);
        }
    }

    private boolean shouldHandleCrossingLogic() {
        // 定義から不変。definitionId が同じ間はキャッシュを返す (毎servertick の…
        if (definitionId.equals(crossingLogicKey)) {
            return crossingLogicVal;
        }
        if (getCategory() == InstalledObjectCategory.CROSSING) {
            crossingLogicKey = definitionId;
            crossingLogicVal = true;
            return true;
        }
        InstalledObjectDefinition definition = getDefinition();
        if (definition == null) {
            return false;  // 定義未解決: まだキャッシュしない (登録が読めたら次tickで再計算)
        }
        String id = definition.getId() == null ? "" : definition.getId().toLowerCase(java.util.Locale.ROOT);
        String name = definition.getDisplayName() == null ? "" : definition.getDisplayName().toLowerCase(java.util.Locale.ROOT);
        String model = definition.getModelFile() == null ? "" : definition.getModelFile().toLowerCase(java.util.Locale.ROOT);
        String sound = definition.getRunningSound() == null ? "" : definition.getRunningSound().toLowerCase(java.util.Locale.ROOT);
        boolean result = id.contains("crossing")
            || id.contains("fumikiri")
            || name.contains("crossing")
            || name.contains("fumikiri")
            || model.contains("crossing")
            || model.contains("fumikiri")
            || sound.contains("crossing")
            || sound.contains("fumikiri")
            || sound.contains("toryanse");
        crossingLogicKey = definitionId;
        crossingLogicVal = result;
        return result;
    }
}
