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

//★jp.ngt.mccompat.LoadAwareBlockEntity を必ず付けること。
//  バニラの BlockEntity に onLoad は無く、Fabric 側はこの印が付いた物だけへ
//  BLOCK_ENTITY_LOAD から onLoad() を呼んでいる。外すと例外もログも出ないまま
//  「マーカーが自分を登録できずレールが引けない」「信号が反応しない」になる。
public class InstalledObjectBlockEntity extends BlockEntity
        implements jp.ngt.rtm.electric.TileEntityInsulator, jp.ngt.ngtlib.block.TileEntityPlaceable,
        jp.ngt.mccompat.LoadAwareBlockEntity {
    private static final int TICKET_GATE_OPEN_TICKS = 60;
    private static final int TICKET_GATE_MOVE_TICKS = 12;
    private String definitionId = "";
    private String category = InstalledObjectCategory.LIGHT.name();
    //ATS 地上子ビーコン (server_ATS_Beacon.js) が、連動信号機の現示を
    //  NGTUtil.getField(TileEntitySignal.class, tile, ["signalLevel"])
    //で読むためのフィールド。本家 TileEntitySignal.signalLevel 相当 (STOP=1, PROCEED=5 ... の legacy 値)。
    //SIGNAL カテゴリのとき tick() で毎回 getLegacySignalState() を書き込む。
    @SuppressWarnings("unused")
    private int signalLevel;
    //--- 派生値キャッシュ (definitionId から一意に決まる不変値を毎tick/毎frame 再計算しない) ---
    //軽量化: shouldHandleCrossingLogic()=toLowerCase×4+contains×9、isBrokenFluorescent()=substring
    //+toLowerCase+contains を、設置物 1 個ずつ毎servertick/毎frame 走らせると駅 (設置物多数) で
    //無視できない CPU/GC になる。定義が変わらない限り結果は不変なので definitionId をキーに保持する。
    //見た目・挙動は一切変えない (NBT には保存しない = ロード後は最初のアクセスで再計算)。
    private transient String crossingLogicKey;
    private transient boolean crossingLogicVal;
    private transient String brokenFluorescentKey;
    private transient boolean brokenFluorescentVal;
    private float yaw;
    // 壁(横面)挿し時に碍子を横倒しにするためのピッチ(度)。0=通常(縦置き)。
    // 列車検知器ではレールの勾配に使う (レンダラでは yaw の後 = モデル局所のX回転)。
    private float mountPitch;
    // yaw の後に掛けるロール(度)。列車検知器をレールのカント(横傾き)に合わせるために使う。
    // 0 が既定なので、これを使わない設置物の見た目は変わらない。
    private float mountRoll;
    // 本家 meta 相当のクリック面 (0-5)。-1 = 旧方式
    private int mountFace = -1;
    // 本家 ResourceState.color 相当 (電線の頂点色)。既定 0xFFFFFF = 白。0 だと電線が黒くなる。
    private int wireColor = 0xFFFFFF;
    // 本家 TileEntityPlaceable の微調整 (GuiChangeOffset): 追加回転とスケール。
    // オフセットは renderOffset (offsetX/Y/Z) を共用する。
    private float adjustRoll;
    private float adjustPitch;
    private float adjustYaw;
    private float adjustScale = 1.0F;
    private BlockPos wireStart;
    private BlockPos wireEnd;
    private boolean powered;
    /** リモコン (RemoteItem) のペアリングで無線給電されているか。保存せず毎 tick 外部が設定する。 */
    private transient boolean remotePowered;
    private int barMoveCount;
    private int lightCount = -1;
    private int tickCountOnActive;
    private double offsetX;
    private double offsetY;
    private double offsetZ;
    private int signalChannel = -1;
    private int signalAspect = SignalAspect.STOP.getId();
    //本家 electric: コネクタの信号レベル (配線網)
    private int electricity;
    private int prevConnectorInput = -1;
    // スピーカー: 音が聞こえる範囲(ブロック)。GUIで可変。
    private int speakerRange = 32;
    private final Map<String, String> scriptData = new HashMap<>();

    // ---- 蛍光灯 (本家 TileEntityFluorescent) ----
    // 本家 dirF: 設置面とプレイヤーの向きから決まる 0..7 の取付方向。
    // RenderFluorescent.js が entity.getDir() で読み、平行移動と Y90度回転を自分で行う。
    //   0=天井(Z向き) 1=北壁 2=床(Z向き) 3=南壁 4=天井(X向き) 5=西壁 6=床(X向き) 7=東壁
    private byte fluorescentDir;
    // 本家 BlockFluorescent.getLightValue: meta==2 (壊れた蛍光灯) はランダムに明滅する。
    private int fluorescentFlickerTick;
    private int fluorescentLight = 15;

    // ---- 転轍機 (本家 TileEntityPoint) ----
    // activated: レッドストーン出力 (ON=15)。右クリックで切り替わる。
    private boolean pointActivated;
    // move: 本家既定 24.0。符号がレバー/本体の向き(左右どちら側に付くか)を決める。
    // MachinePartsRenderer.getLodState が move>0 ? 1 : -1 を返し、RenderPoint01.js が
    // state<0 のとき本体を +2.75 ずらす。バールの右クリックで符号を反転する。
    private float pointMove = 24.0F;

    // ---- 看板 (本家 TileEntitySignBoard / ResourceStateSignboard) ----
    /**
     * 板に貼り付けた文字。看板エディタ (SignboardScreen) で編集する。
     */
    private final List<SignboardText> signTexts = new ArrayList<>();
    /**
     * 時刻表の参照設定。本家形式 "tt=<file>,station=<駅名>,track=<番線>"。
     */
    private String signTtSetting = DEFAULT_TT_SETTING;
    /**
     * 本家 direction (0-3)。設置時のプレイヤー向きで、板の面が向く方角を決める。
     */
    private byte signDirection;
    /**
     * クライアント: フレームアニメ用カウンタ (frame * animationCycle で1周)。
     */
    private int signCounter;
    /**
     * サーバー: lightValue == -16 (ランダム点滅) 用の現在の明るさ。保存も同期もしない
     * (明るさ自体はライトエンジン経由でクライアントへ届く)。
     */
    private int signFlicker;
    /**
     * サーバー: 最後にライトエンジンへ渡した明るさ。保存しないので、BE が作り直された
     * 最初の tick では必ず -1 になり、設置直後やチャンク再読込でも必ず一度は再計算される。
     */
    private int signLastLight = -1;

    // ---- 列車検知器 (本家 EntityTrainDetector) ----
    /**
     * 検知したときにレッドストーンブロックを置く/消す座標。未設定なら null (何もしない)。
     */
    private BlockPos detectorTarget;
    /**
     * true  = 列車を検知したら「置く」 (居なくなったら消す)
     * false = 列車を検知したら「消す」 (居なくなったら置く)
     */
    private boolean detectorPlaceOnDetect = true;
    /**
     * 真下のレールに列車が乗っているか。クライアントにも同期して GUI に出す。
     */
    private boolean detectorTrainOnRail;

    private static final String DEFAULT_TT_SETTING = "tt=tt_sample.csv,station=西京,track=-1";
    private static final RandomSource SIGN_RANDOM = RandomSource.create();
    /**
     * 本家 EntityTrainDetector: 自分の位置から真下に最大 8 ブロックまでレールを探す。
     */
    private static final int DETECTOR_RAIL_SEARCH_DEPTH = 8;
    /**
     * 検知の間隔(tick)。毎tickでなくても列車の検知には十分。
     */
    private static final int DETECTOR_INTERVAL = 5;

    public InstalledObjectBlockEntity(BlockPos pos, BlockState blockState) {
        super(RealTrainModUnofficialBlockEntities.INSTALLED_OBJECT.get(), pos, blockState);
        //本家スクリプト互換 (1.7.10 の TileEntity 座標フィールド)。
        //架線柱パックの描画スクリプトは周囲の碍子を探すのにこれを読む。
        this.field_145851_c = pos.getX();
        this.field_145848_d = pos.getY();
        this.field_145849_e = pos.getZ();
    }

    // ---- 本家スクリプト互換 (SRG 名) ----
    //
    // 架線柱パックのスクリプトは RTMCore.VERSION が "1.7.10" を含むため 1.7.10 の
    // API 名で書かれた分岐に入る。そこで読まれる名前だけをここで提供する。

    /** 1.7.10 TileEntity.xCoord */
    public int field_145851_c;
    /** 1.7.10 TileEntity.yCoord */
    public int field_145848_d;
    /** 1.7.10 TileEntity.zCoord */
    public int field_145849_e;

    private jp.ngt.mccompat.WorldCompat worldCompat;

    /** func_145831_w = getWorldObj()。スクリプトは戻り値に func_147438_o(x,y,z) を呼ぶ。 */
    public jp.ngt.mccompat.WorldCompat func_145831_w() {
        if (this.level == null) {
            return null;
        }
        if (this.worldCompat == null || this.worldCompat.level != this.level) {
            this.worldCompat = new jp.ngt.mccompat.WorldCompat(this.level);
        }
        return this.worldCompat;
    }

    // ---- 本家 EntityInstalledObject 互換 (サーバースクリプト用) ----
    //
    // 本家の設置物は「エンティティ」なので、サーバースクリプトは設置物を Entity として扱い、
    // 座標・向き・当たり判定・World を SRG 名で読む。RTMU ではブロックエンティティだが、
    // 同じ名前で読めるようにしておく。
    //
    // ★ここは必ず public フィールドにすること。スクリプトは entity.field_70170_p と
    //   「プロパティとして」読む。Nashorn は同名メソッドを自動では呼ばないので、
    //   メソッドで用意すると値ではなく関数オブジェクトが返り、その後の
    //   world.func_147438_o(...) が "not a function" で落ちる。

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
        //ATC 地上子はレール吸着で<b>レールの 1 つ上のブロック</b>に置かれる。ところが本家の
        //server_ATS_Beacon.js は「自分の真下 (y-1) のコマンドブロックを設定として読む」「自分の高さの
        //AABB で列車を検知する」— これは<b>地上子がレール高さのエンティティだった前提</b>。
        //ブロック化した RTMU では 1 ブロック高いので、y-1 がレール自身になりコマブロを置けず、
        //検知もレールの 1 つ上にズレていた (ユーザー指摘)。スクリプトから見た自分の Y を 1 下げ、
        //本家エンティティと同じ「レール高さ」に合わせる → コマブロ=レールの下 / 検知=レール高さ。
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

    /**
     * 本家 TileEntityInsulator.wirePos (電線の取付点)。碍子 (コネクタ) 以外は null。
     * スクリプトは {@code tile.wirePos} と書き、Nashorn がこの getter に解決する。
     */
    @Override
    public jp.ngt.ngtlib.math.Vec3 getWirePos() {
        //碍子 (INSULATOR) も対象に含める。NGTO Builder / Baru's Pole の架線スクリプトは
        //碍子の wirePos (JSON の wirePos=[x,y,z]) を金具・接続点の計算に使い、
        //null だと render_Wire2 の getAroundInsulatorPosData が wirePos.getX() で落ちる。
        //碍子/コネクタ以外 (照明・看板等) は従来どおり null (=スクリプト側の `=== null` ガードで弾く)。
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
        //★ここは<b>モデル座標のままの生の取付点</b>を返す。
        //本家 TileEntityConnectorBase.updateWirePos に相当する変換
        //(取付面/pitch/yaw/roll の回転 + オフセット加算) は
        //InstalledObjectBlockEntityRenderer.wireEndpoint 側が既に忠実に行っている。
        //ここでも変換すると<b>二重に掛かって</b>電線が明後日を向く。
        return wp == null ? null : new jp.ngt.ngtlib.math.Vec3(wp.x, wp.y, wp.z);
    }

    /**
     * NGTO Builder の Wire ツール互換: スクリプトの setModelName
     * ({@code NGTUtil.setValueToField(TileEntityConnectorBase.class, tile, name, "modelName")}) を
     * {@link jp.ngt.ngtlib.util.NGTUtil} がここへブリッジする。bare name から碍子定義を解決して適用する。
     */
    public void applyScriptModelName(String bareName) {
        InstalledObjectDefinition def = InstalledObjectRegistry.getByBareName(bareName, InstalledObjectCategory.INSULATOR);
        if (def == null) {
            return;
        }
        this.definitionId = def.getId();
        this.category = def.getCategory().name();
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("DefinitionId", definitionId);
        tag.putString("Category", category);
        tag.putFloat("Yaw", yaw);
        tag.putFloat("MountPitch", mountPitch);
        tag.putFloat("MountRoll", mountRoll);
        tag.putInt("MountFace", mountFace);
        //本家 ResourceState と同じキー名 (本家ワールドのデータをそのまま読める)。
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
        //本家 TileEntityPlaceable:33 と同じキー名・型 (小文字 offsetX / float)。
        //スクリプトが設置時に NBT へ書くのもこの名前:
        //  NGTOBuilder2!server_wire_beam.js  nbt.func_74776_a("offsetX", pos[4]);  // setFloat
        //RTMU は "OffsetX"(double) だったため、スクリプトが指定したオフセットが
        //一切読まれず、ビームが本来の位置に乗らず傾いて見えていた。
        tag.putFloat("offsetX", (float) offsetX);
        tag.putFloat("offsetY", (float) offsetY);
        tag.putFloat("offsetZ", (float) offsetZ);
        tag.putInt("SignalChannel", signalChannel);
        tag.putInt("SignalAspect", signalAspect);
        tag.putInt("SpeakerRange", speakerRange);
        //蛍光灯 / 転轍機 (本家 TileEntityFluorescent.dir, TileEntityPoint.Activated/Move)
        tag.putByte("FluorescentDir", fluorescentDir);
        tag.putBoolean("PointActivated", pointActivated);
        tag.putFloat("PointMove", pointMove);
        if (!scriptData.isEmpty()) {
            CompoundTag scriptDataTag = new CompoundTag();
            scriptData.forEach(scriptDataTag::putString);
            tag.put("ScriptData", scriptDataTag);
        }
        //看板: 本家 ResourceStateSignboard と同じキー名なので、本家ワールドのデータも読める。
        if (!signTexts.isEmpty()) {
            ListTag list = new ListTag();
            for (SignboardText text : signTexts) {
                list.add(text.save());
            }
            tag.put("Texts", list);
        }
        tag.putString("TimeTableSetting", signTtSetting);
        tag.putByte("SignDir", signDirection);
        //列車検知器
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
        category = tag.contains("Category") ? tag.getString("Category") : InstalledObjectCategory.LIGHT.name();
        yaw = tag.getFloat("Yaw");
        mountPitch = tag.getFloat("MountPitch");
        mountRoll = tag.getFloat("MountRoll");
        mountFace = tag.contains("MountFace") ? tag.getInt("MountFace") : -1;
        //未設定なら本家 ResourceState.color の既定 (0xFFFFFF = 白)。0 にすると電線が黒くなる。
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
        //本家キー (小文字 float) を優先し、旧 RTMU セーブ ("OffsetX" double) も読めるようにする。
        offsetX = tag.contains("offsetX") ? tag.getFloat("offsetX") : tag.getDouble("OffsetX");
        offsetY = tag.contains("offsetY") ? tag.getFloat("offsetY") : tag.getDouble("OffsetY");
        offsetZ = tag.contains("offsetZ") ? tag.getFloat("offsetZ") : tag.getDouble("OffsetZ");
        signalChannel = tag.contains("SignalChannel") ? tag.getInt("SignalChannel") : -1;
        signalAspect = tag.contains("SignalAspect") ? tag.getInt("SignalAspect") : SignalAspect.STOP.getId();
        //旧セーブ互換: 信号機は現示 (SignalAspect) と electricity を単一状態にミラーする。
        //electricity 未保存 (0) の場合は現示側を正とする。
        if (getCategory() == InstalledObjectCategory.SIGNAL && electricity == 0) {
            electricity = SignalAspect.byId(signalAspect).getLegacyValue();
        }
        speakerRange = tag.contains("SpeakerRange") ? tag.getInt("SpeakerRange") : 32;
        fluorescentDir = tag.getByte("FluorescentDir");
        pointActivated = tag.getBoolean("PointActivated");
        //本家 TileEntityPoint の既定は 24.0。0 だと転轍機の本体が反対側にずれてしまうので、
        //キーが無い旧データは既定値に戻す。
        pointMove = tag.contains("PointMove") ? tag.getFloat("PointMove") : 24.0F;
        scriptData.clear();
        if (tag.contains("ScriptData")) {
            CompoundTag scriptDataTag = tag.getCompound("ScriptData");
            for (String key : scriptDataTag.getAllKeys()) {
                scriptData.put(key, scriptDataTag.getString(key));
            }
        }
        //看板
        signTexts.clear();
        if (tag.contains("Texts")) {
            ListTag list = tag.getList("Texts", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                signTexts.add(SignboardText.load(list.getCompound(i)));
            }
        }
        signTtSetting = tag.contains("TimeTableSetting") ? tag.getString("TimeTableSetting") : DEFAULT_TT_SETTING;
        signDirection = tag.getByte("SignDir");
        //列車検知器
        detectorTarget = tag.contains("DetectorTargetX")
                ? new BlockPos(tag.getInt("DetectorTargetX"), tag.getInt("DetectorTargetY"), tag.getInt("DetectorTargetZ"))
                : null;
        detectorPlaceOnDetect = !tag.contains("DetectorPlaceOnDetect") || tag.getBoolean("DetectorPlaceOnDetect");
        detectorTrainOnRail = tag.getBoolean("DetectorTrainOnRail");
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
        setChanged();
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

    /**
     * yaw の後に掛けるロール(度)。列車検知器のカント用。
     */
    public float getMountRoll() {
        return mountRoll;
    }

    public void setMountRoll(float mountRoll) {
        this.mountRoll = mountRoll;
        setChanged();
    }

    /**
     * 本家 ItemInstalledObject の meta (クリック面 0-5)。-1 = 旧方式 (持ち上げハック)。
     * 碍子等は本家 RenderElectricalWiring と同じブロック中心ピボット+面回転で描画する。
     */
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

    public void setWireEndpoints(BlockPos start, BlockPos end) {
        this.wireStart = start;
        this.wireEnd = end;
        setChanged();
        //電気配線網に登録 (本家 WireManager)
        if (level != null && !level.isClientSide && start != null && end != null) {
            jp.ngt.rtm.electric.WireManager.register(level, start, end);
        }
        //架線ジオメトリはパンタの上昇停止位置に使う。描画はクライアント側なので両サイドで登録する
        if (level != null && start != null && end != null) {
            jp.ngt.rtm.electric.WireManager.INSTANCE.addWire(level, start, end);
        }
    }

    /**
     * 本家 electric: コネクタの信号レベル
     */
    public int getElectricity() {
        return electricity;
    }

    public void setElectricity(int levelValue) {
        //信号機は「電気値は同じだが現示 (aspect) がズレている」ことがある
        //(ミラー導入前の設置物や手動変更との競合) — その場合も書き込む
        boolean signalDesynced = getCategory() == InstalledObjectCategory.SIGNAL
                && getLegacySignalState() != levelValue;
        if (this.electricity != levelValue || signalDesynced) {
            this.electricity = levelValue;
            //本家 TileEntitySpeaker.setElectricity: レベル 1-64 = そのスロットの音を再生
            if (isSpeaker() && levelValue >= 1 && levelValue <= 64) {
                playSpeakerSound(levelValue);
            }
            //本家 TileEntitySignal.setElectricity 同様、信号機は電気レベル=現示。
            //現示 (SignalAspect) と electricity の二重管理が「UI で変えた現示と
            //配線/変換器/SignalController で変えた現示がバラバラ」の原因だったため、
            //信号カテゴリでは常に両方向ミラーして単一状態にする。
            if (getCategory() == InstalledObjectCategory.SIGNAL) {
                this.signalAspect = com.portofino.realtrainmodunofficial.signal.SignalAspect
                        .byLegacyValue(levelValue).getId();
            }
            setChanged();
            if (level != null && !level.isClientSide) {
                if (getCategory() == InstalledObjectCategory.CONNECTOR_OUTPUT) {
                    //レッドストーン出力の更新
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

    /** リモコンの無線給電を設定 (RemoteRedstoneHandler が毎 tick 呼ぶ)。 */
    public void setRemotePowered(boolean v) {
        this.remotePowered = v;
    }

    public boolean isRemotePowered() {
        return this.remotePowered;
    }

    /** 隣接レッドストーン入力があるか、またはリモコンで無線給電されているか。 */
    private boolean redstoneOn(Level level, BlockPos pos) {
        return level.getBestNeighborSignal(pos) > 0 || this.remotePowered;
    }

    public boolean isPowered() {
        return powered;
    }

    // ---- 看板 ----

    /**
     * 板に貼られている文字。描画側から触るので変更不可で返す。
     */
    public List<SignboardText> getSignTexts() {
        return Collections.unmodifiableList(signTexts);
    }

    public String getSignTtSetting() {
        return signTtSetting;
    }

    /**
     * 看板エディタ (SignboardScreen) の保存。サーバー側で呼ばれる。
     */
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

    /**
     * 本家 direction (0-3)。板が向く方角。
     */
    public byte getSignDirection() {
        return signDirection;
    }

    public void setSignDirection(byte direction) {
        this.signDirection = (byte) (direction & 3);
        setChanged();
    }

    /**
     * クライアント: フレームアニメの現在位置 (0 .. frame*animationCycle-1)。
     */
    public int getSignCounter() {
        return signCounter;
    }

    /**
     * 描画結果を左右する状態のシグネチャ (スクリプト描画キャッシュ用)。
     * これが変わらない間はスクリプト (Nashorn) を再実行せず記録を再生する。
     * 静的な信号機/看板 (現示・向き・スクリプトデータが不変) はほぼ毎フレーム再生で済み軽くなる。
     * 点滅・アニメ物 (signCounter が毎フレーム変わる) は従来どおり再実行される。
     */
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

    /**
     * 本家 BlockSignBoard.getLightValue の移植。
     * <ul>
     *   <li>lightValue &gt;= 0 … 常にその明るさ</li>
     *   <li>lightValue == -16 … ランダム点滅 (0,3,6,9,12,15)</li>
     *   <li>lightValue &lt; 0 … レッドストーン通電時に -lightValue</li>
     * </ul>
     */
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

    /**
     * 検知時にレッドストーンブロックを操作する座標。未設定なら null。
     */
    @javax.annotation.Nullable
    public BlockPos getDetectorTarget() {
        return detectorTarget;
    }

    /**
     * true = 検知したら「置く」 / false = 検知したら「消す」。
     */
    public boolean isDetectorPlaceOnDetect() {
        return detectorPlaceOnDetect;
    }

    /**
     * 真下のレールに列車が乗っているか (GUI 表示用)。
     */
    public boolean isDetectorTrainOnRail() {
        return detectorTrainOnRail;
    }

    /**
     * 検知器の設定 (GUI から)。サーバー側で呼ばれる。
     */
    public void configureDetector(@javax.annotation.Nullable BlockPos target, boolean placeOnDetect) {
        this.detectorTarget = target == null ? null : target.immutable();
        this.detectorPlaceOnDetect = placeOnDetect;
        setChanged();
        if (level != null && !level.isClientSide) {
            //設定を変えた瞬間に対象ブロックを今の在線状態に合わせる。
            applyDetectorOutput(level, detectorTrainOnRail);
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /**
     * 本家 EntityTrainDetector 準拠: 自分の真下 8 ブロック以内で最初に見つかったレールの
     * 在線状態を返す。レールが無ければ false。
     */
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

    /**
     * 検知結果を対象座標のレッドストーンブロックに反映する。
     * <p>
     * <b>建築物を壊さないため、置くのは対象が空気のときだけ・消すのは対象がレッドストーン
     * ブロックのときだけ</b>にしてある。それ以外のブロックには一切触れない。
     * <p>
     * 毎回「あるべき状態」と突き合わせるので、チャンクを読み直した後やプレイヤーが手で
     * 壊した後でも自動で復旧する。
     */
    private void applyDetectorOutput(Level level, boolean detected) {
        BlockPos target = detectorTarget;
        if (target == null || level.isClientSide || !level.isLoaded(target)) {
            return;
        }
        //置くモード: 検知中 = レッドストーンブロックあり / 消すモード: 検知中 = なし
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
    /**
     * 本家 TileEntitySpeaker.setSound/getSound 相当 (スピーカーごとの音登録)。
     * 未登録スロットはグローバル設定 (SpeakerSoundConfig) にフォールバック (旧ワールド互換)。
     */
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

    /**
     * 本家 TileEntitySpeaker.setElectricity 相当の発音 (index = 電気レベル 1-64)。
     * 音量は可聴範囲 (speakerRange ブロック) から算出し、離れるほど減衰する
     * (MC LINEAR 減衰: 可聴距離 ≒ volume × 16 ブロック)。
     */
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
        float volume = Math.max(0.05F, range / 16.0F);
        var payload = new com.portofino.realtrainmodunofficial.network.SpeakerPlayPayload(cx, cy, cz, sound, volume, 1.0F);
        double rangeSq = (double) range * (double) range;
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            for (net.minecraft.server.level.ServerPlayer p : serverLevel.players()) {
                if (p.distanceToSqr(cx, cy, cz) <= rangeSq * 1.44D) {
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

    /**
     * Legacy RTM signal scripts read the current aspect through entity.getSignal().
     */
    public int getSignal() {
        return Math.max(0, getLegacySignalState());
    }

    /**
     * Legacy RTM signal scripts query a resource-state wrapper to read config values.
     */
    public ResourceStateCompat getResourceState() {
        return new ResourceStateCompat(this);
    }

    /**
     * Legacy 1.7-style scripts sometimes access modelSet directly.
     */
    public ModelSetCompat getModelSet() {
        return new ModelSetCompat(this);
    }

    /**
     * The installed-object renderer already applies block yaw before the script runs,
     * so returning zero here avoids rotating scripted signals twice.
     */
    @Override
    public float getRotation() {
        //本家 TileEntityPlaceable.getRotation。NGTO Builder は
        //  rotation = tile.getRotation() + yaw;  tile.setRotation(rotation, true);
        //と<b>現在値に加算</b>する。0 固定だと設置ごとに向きが失われる。
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

    /**
     * Slanted placement is not implemented for installed objects yet, so the block
     * direction is exposed as zero for script compatibility.
     */
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

    /**
     * RTM signal scripts use sparse numeric states rather than the compact UI ids.
     */
    public int getLegacySignalState() {
        return getSignalAspect().getLegacyValue();
    }

    public void setSignalAspect(SignalAspect aspect, boolean updateClient) {
        SignalAspect a = aspect == null ? SignalAspect.STOP : aspect;
        this.signalAspect = a.getId();
        //electricity と双方向ミラー (setElectricity 側のコメント参照)
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

    // ---- Legacy RTM script position/direction accessors ----

    public int getX() { return worldPosition.getX(); }
    public int getY() { return worldPosition.getY(); }
    public int getZ() { return worldPosition.getZ(); }

    /**
     * 0–3 facing direction derived from yaw (0=south,1=west,2=north,3=east).
     * <p>
     * ただし蛍光灯だけは本家 TileEntityFluorescent.getDir() と同じく
     * <b>取付方向 0..7</b> を返す (RenderFluorescent.js がこれで平行移動と回転を決める)。
     */
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

    /** 壊れた蛍光灯 (本家 meta==2 / モデル名 *Broken) か。定義から不変なので definitionId でキャッシュ。 */
    public boolean isBrokenFluorescent() {
        if (!definitionId.equals(brokenFluorescentKey)) {
            brokenFluorescentKey = definitionId;
            brokenFluorescentVal = getCategory() == InstalledObjectCategory.FLUORESCENT
                && getModelName().toLowerCase(java.util.Locale.ROOT).contains("broken");
        }
        return brokenFluorescentVal;
    }

    /**
     * 本家 BlockFluorescent.getLightValue: 壊れた蛍光灯は 0/4/8/12 をランダムに返して明滅する。
     * 通常の蛍光灯は常に 15。
     * <p>
     * 本家は getLightValue が呼ばれるたびに乱数を振っていたが、getLightEmission は
     * 光源伝播中に何度も呼ばれるので値がぶれる。ここでは 3tick ごと (本家
     * TileEntityFluorescent.update の count==3 と同じ間隔) に振り直した値を保持して返す。
     */
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
            //本家 world.checkLight(pos) 相当。これを呼ばないと明るさが変わっても再描画されない。
            level.getLightEngine().checkBlock(pos);
        }
    }

    // ---- 転轍機 (本家 TileEntityPoint) ----

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

    /** バールでの右クリックで符号が反転し、転轍機の本体が線路の反対側に移る (本家 BlockPoint)。 */
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

    /**
     * 指定方向 (RTM side 0-5) の隣に接続相手がいるか。
     * RTM の side 番号 (0=下,1=上,2=北,3=南,4=西,5=東) は MC の {@link net.minecraft.core.Direction#from3DDataValue}
     * と一致する。パイプは隣接パイプ同士で接続する (RenderConnectablePipe.js が partXP..partZN の腕を出す)。
     */
    public boolean isConnected(int side) {
        if (level == null || side < 0 || side > 5 || getCategory() != InstalledObjectCategory.PIPE) {
            return false;
        }
        net.minecraft.core.BlockPos neighbor = worldPosition.relative(net.minecraft.core.Direction.from3DDataValue(side));
        return level.getBlockEntity(neighbor) instanceof InstalledObjectBlockEntity io
            && io.getCategory() == InstalledObjectCategory.PIPE;
    }

    /**
     * このブロックが貼り付いている面 (0=下,1=上,2-5=NSWE)。
     * パイプ: 設置時のクリック面 (mountFace)。RenderPipe.js はこの軸に真っ直ぐ描き
     * (side 4/5=X, 0/1=Y, 2/3=Z、該当なしだと<b>何も描かない</b>)、RenderConnectablePipe.js は
     * この面へ向かう腕を出す。未設定(-1)は縦置き(1)にフォールバック。それ以外の設置物は従来通り 1。
     */
    public int getAttachedSide() {
        if (getCategory() == InstalledObjectCategory.PIPE) {
            return mountFace >= 0 ? mountFace : 1;
        }
        return 1;
    }

    /** Random decorative scale factor (used by RenderPalm etc.). */
    public float getRandomScale() { return 1.0F; }

    public InstalledObjectDefinition getDefinition() {
        return InstalledObjectRegistry.getById(definitionId);
    }

    /**
     * 本家 {@code ResourceState.color} 相当。電線スクリプトが
     * {@code renderer.getColor(tileEntity)} で読み、頂点色にそのまま使う。
     * <p>
     * 既定は本家と同じ {@code 0xFFFFFF} (白)。0 にすると電線が真っ黒になる。
     */
    public int getWireColor() {
        return wireColor;
    }

    public void setWireColor(int color) {
        this.wireColor = color;
        setChanged();
    }

    // --- NGTO Builder の Wire ツール互換 ---

    /** Wire ツールが碍子の微調整オフセットを設定する (本家 TileEntityConnectorBase.setOffset)。 */
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

    /**
     * Wire ツールが「この碍子から (x,y,z) の碍子へワイヤーを張る」ときに呼ぶ (本家 setConnectionTo)。
     * RTMU の配線描画は wireStart/wireEnd を持つ設置物に対して行われるので、両端を設定して描画対象にする。
     * connectionType/modelState は本家 API 互換で受け取るだけ (現状 WIRE のケーブルを描くのみ)。
     */
    public void setConnectionTo(int x, int y, int z, Object connectionType, Object modelState) {
        this.wireStart = this.worldPosition;
        this.wireEnd = new net.minecraft.core.BlockPos(x, y, z);
        // RTMU の配線描画は「WIRE カテゴリの設置物が、その定義(モデル)のケーブルを wireStart→wireEnd に描く」。
        // なので接続された碍子を WIRE オブジェクト化する: 定義=選択ワイヤーモデル、category=WIRE。
        // modelState は WireItem.getModelState() が返す WireModelState (modelId を持つ)。
        // 選択ワイヤーモデル ID を取り出す。isOldVer=false 経路は WireModelState、
        // isOldVer=true 経路 (RTMU は VERSION に "1.7.10" を含むのでこちら) は getModelName の String。
        String wireModelId = null;
        if (modelState instanceof com.portofino.realtrainmodunofficial.item.WireItem.WireModelState wms) {
            wireModelId = wms.modelId;
        } else if (modelState instanceof String s) {
            wireModelId = s;
        }
        if (wireModelId != null && !wireModelId.isEmpty()) {
            this.definitionId = wireModelId;
            this.category = InstalledObjectCategory.WIRE.name();
        }
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            //WIRE の電気グラフにも登録 (再読込時は onLoad が category==WIRE で再登録する)。
            if (getCategory() == InstalledObjectCategory.WIRE) {
                jp.ngt.rtm.electric.WireManager.register(level, this.wireStart, this.wireEnd);
            }
        }
    }

    /** ロード済み設置物 (WebCTC の信号地図 API が使用。レールコアの LOADED_CORES と同じ方式)。 */
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

    //★@Override を付けないこと: これは NeoForge が足したメソッドで、バニラには無い
    public void onLoad() {
        //super.onLoad(); — バニラの BlockEntity には onLoad が無い
        LOADED_OBJECTS.add(this);
        if (level instanceof ServerLevel serverLevel && isSignal()) {
            SignalNetworkSavedData.get(serverLevel).syncLoadedSignal(serverLevel, this);
        }
        //ワイヤーは配線網 (本家 WireManager) へ登録
        if (level != null && getCategory() == InstalledObjectCategory.WIRE
                && wireStart != null && wireEnd != null) {
            if (!level.isClientSide) {
                jp.ngt.rtm.electric.WireManager.register(level, wireStart, wireEnd);
            }
            jp.ngt.rtm.electric.WireManager.INSTANCE.addWire(level, wireStart, wireEnd);
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
        super.setRemoved();
    }

    /**
     * ATS 基礎 (Phase 1): ATC 地上子。真下のレール ({@link jp.ngt.rtm.rail.TileEntityLargeRailCore})
     * の signal に、隣接レッドストーンの最大強度 (0-15) を書き込む。通過した列車が
     * {@code EntityTrainBase.updateRailSignalPickup} で拾い、パックの ATS/ATS-P スクリプトが利用する。
     * 無給電 (強度 0) のときは書き込まない = 意図しない「信号 0=停止」の散布を避ける。
     */
    private void emitAtcSignal(Level level, BlockPos pos) {
        int redstone = level.getBestNeighborSignal(pos);
        if (redstone <= 0) {
            return;
        }
        jp.ngt.rtm.rail.TileEntityLargeRailBase rail =
                jp.ngt.rtm.rail.TileEntityLargeRailBase.getRailFromCoordinates(
                        level, pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        if (rail != null) {
            jp.ngt.rtm.rail.TileEntityLargeRailCore core = rail.getRailCore();
            if (core != null) {
                core.setSignal(redstone);
            }
        }
    }

    public static void tick(Level level, BlockPos pos, BlockState state, InstalledObjectBlockEntity be) {
        //ATS 連動: 信号機は現示 (legacy 値) を signalLevel フィールドに毎 tick 反映しておく。
        //ATS-Ps 地上子ビーコンが NGTUtil.getField(..., "signalLevel") でこれを読む (GAP C)。
        //hasServerScript の早期 return より前に置くこと (ブロック検知式信号機は script を持つため)。
        if (be.getCategory() == InstalledObjectCategory.SIGNAL) {
            be.signalLevel = be.getLegacySignalState();
        }
        //蛍光灯は明滅以外にやることが無い。明るさはクライアント/サーバー両方で使うので
        //サイド分岐より前に処理する (壊れていない蛍光灯なら何もせず抜ける)。
        if (be.getCategory() == InstalledObjectCategory.FLUORESCENT) {
            tickFluorescentFlicker(level, pos, be);
            return;
        }
        if (level.isClientSide) {
            //看板: 本家 TileEntitySignBoard.update — フレームアニメを進める。
            if (be.getCategory() == InstalledObjectCategory.SIGNBOARD) {
                InstalledObjectDefinition signDef = be.getDefinition();
                int period = signDef == null ? 1 : Math.max(1, signDef.getSignFrame() * signDef.getAnimationCycle());
                be.signCounter = (be.signCounter + 1) % period;
            }
            // sound_Running を持つ設置オブジェクト(スピーカー/サイレン/踏切など)は種別を問わず、
            // powered の間ループ再生する。実際の再生可否(powered・音名)は CrossingGateSoundManager 側で判定。
            InstalledObjectDefinition definition = be.getDefinition();
            String running = definition == null ? null : definition.getRunningSound();
            if (running != null && !running.isBlank()) {
                ClientHooks.tickCrossingGateSound(be);
            }
            //軽量化: 走行音を持たない設置物 (標識/照明/架線柱/信号機など大多数) は「鳴らす音が無い＝
            //止める音も無い」。以前はここで毎 client tick stopCrossingGateSound を呼んでいたが、これは
            //リフレクション + 文字列キー生成の空振り。鳴っている踏切音は LoopingCrossingSound.tick() 自身が
            //powered off/範囲外/定義変更/撤去を検知して停止するので、毎tick の明示停止は不要 (見た目・音とも不変)。
            return;
        }
        //本家 EntityInstalledObject.onUpdate: サーバー側では毎 tick、パックの
        //serverScriptPath を onUpdate(entity, executer) として呼ぶ。
        //
        //列車検知器パック (hi03TrainDetector 等) は全処理をこのスクリプトに書くので、
        //スクリプトを持つ設置物は<b>パック側の実装に任せて</b> RTMU 内蔵の検知器処理は動かさない
        //(両方が出力を書くと奪い合いになる)。
        //ATS 基礎 (Phase 1): 内蔵の簡易 ATC 地上子は真下のレールに signal を送出する。
        //送出レベル = 隣接レッドストーンの最大強度 (0-15)。0 (無給電) のときは何も上書きしない。
        //※ ただし hi03 ATS-Ps 地上子のように<b>独自の serverScriptPath (server_ATS_Beacon.js) を持つ</b>
        //   ATC は、その script が列車検知〜ATSDataReceive 送信まで全部やるので、ここでは手を出さず
        //   下の hasServerScript 分岐に任せる (両方が動くと信号の奪い合いになる)。
        if (be.getCategory() == InstalledObjectCategory.ATC
                && (be.getDefinition() == null || !be.getDefinition().hasServerScript())) {
            be.emitAtcSignal(level, pos);
            return;
        }
        if (be.getDefinition() != null && be.getDefinition().hasServerScript()) {
            if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                com.portofino.realtrainmodunofficial.script.InstalledObjectServerScripts.tick(serverLevel, be);
            }
            return;
        }
        //看板: 本家 TileEntitySignBoard.update — レッドストーン状態とランダム点滅を更新し、
        //明るさが変わったらライトエンジンに再計算させる。
        //
        //設置直後は setBlock の時点でまだ BE が空 (definitionId 未設定) なので、
        //そのままだと明るさ 0 がチャンクに焼き付いてしまう。ここで「最後に出した明るさ」と
        //比較しておくと、設置直後・チャンク再読込・テクスチャ変更のいずれでも拾える
        //(signLastLight は保存しないので、BE が作られた最初の tick では必ず不一致になる)。
        //スピーカー: レッドストーン入力 or リモコン無線給電の<b>立ち上がり</b>で音を鳴らす。
        //(通常は電気配線 setElectricity 経由で鳴るが、素のレッドストーン/リモコンでも鳴らせるように)
        if (be.isSpeaker()) {
            boolean sig = be.redstoneOn(level, pos);
            if (sig && !be.powered) {
                be.playSpeakerSound(1);   //立ち上がりでスロット1の音を再生
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
                //本家: ランダム点滅 (0,3,6,9,12,15)
                be.signFlicker = SIGN_RANDOM.nextInt(6) * 3;
            }
            int light = be.getSignboardLightEmission();
            if (light != be.signLastLight) {
                be.signLastLight = light;
                level.getLightEngine().checkBlock(pos);
            }
            return;
        }
        //列車検知器: 本家 EntityTrainDetector — 真下のレールの在線を見る。
        //本家は配線網へ STOP/PROCEED を流していたが、RTMU の配線は信号機に届かないので、
        //代わりに「指定座標のレッドストーンブロックを置く/消す」で出力する
        //(座標と動作は右クリックの GUI で設定)。
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
            //毎回「あるべき状態」に合わせる (壊された/チャンクを読み直した場合も復旧する)
            be.applyDetectorOutput(level, onRail);
            return;
        }
        //本家 electric: 入力コネクタ = レッドストーンを監視して配線網へ伝播
        if (be.getCategory() == InstalledObjectCategory.CONNECTOR_INPUT) {
            int sig = level.getBestNeighborSignal(pos);
            if (sig != be.prevConnectorInput) {
                be.prevConnectorInput = sig;
                be.setElectricity(sig);
                jp.ngt.rtm.electric.WireManager.propagate(level, pos, sig);
            }
            return;
        }
        if (be.getCategory() == InstalledObjectCategory.TICKET_GATE) {
            boolean changed = false;
            //レッドストーン入力でも開く (本家 Turnstile は切符で openGate だが、
            //自動化/検知ブロック連携用に RS 開扉を追加)
            if (!be.powered && be.redstoneOn(level, pos)) {
                be.powered = true;
                be.tickCountOnActive = 0;
                changed = true;
            }
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
        // サーバー側: 遮断桿アニメ等は踏切ロジック対象のみ
        if (!be.shouldHandleCrossingLogic()) {
            return;
        }
        // 踏切のレッドストーン受信を毎tick再評価する。neighborChanged の取りこぼしや
        // ワールド再読込・ワイヤ隣接(hasNeighborSignal が拾いにくいケース)に強くするため、
        // getBestNeighborSignal(>0)で判定して powered を常に信号と同期させる。
        // リモコン無線給電 (remotePowered) も同じ扱い。
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

        public ModelSetCompat getResourceSet() {
            return new ModelSetCompat(blockEntity);
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

        private void refresh() {
            if (blockEntity == null) {
                return;
            }
            values.put("powered", blockEntity.isPowered() ? 1 : 0);
            values.put("isPowered", blockEntity.isPowered());
            values.put("barMoveCount", blockEntity.getBarMoveCount());
            values.put("lightCount", blockEntity.getLightCount());
            values.put("signal", blockEntity.getSignal());
            values.put("signalAspect", blockEntity.getLegacySignalState());
            values.put("yaw", blockEntity.getYaw());
            blockEntity.scriptData.forEach(values::putIfAbsent);
        }
    }

    public static final class ModelSetCompat {
        private final InstalledObjectBlockEntity blockEntity;

        public ModelSetCompat(InstalledObjectBlockEntity blockEntity) {
            this.blockEntity = blockEntity;
        }

        public String getName() {
            return blockEntity == null ? "" : blockEntity.getModelName();
        }

        public String getModelName() {
            return getName();
        }

        public String getTextureName() {
            return getName();
        }

        public ConfigCompat getConfig() {
            return new ConfigCompat(blockEntity);
        }
    }

    public static final class ConfigCompat {
        public final float[] offset;
        public final ModelPartsBodyCompat modelPartsBody;

        public ConfigCompat(InstalledObjectBlockEntity blockEntity) {
            InstalledObjectDefinition definition = blockEntity == null ? null : blockEntity.getDefinition();
            Vec3 modelOffset = definition == null ? Vec3.ZERO : definition.getModelOffset();
            Vec3 scriptBodyPos = definition == null ? Vec3.ZERO : definition.getScriptBodyPos();
            this.offset = new float[] {(float) modelOffset.x, (float) modelOffset.y, (float) modelOffset.z};
            this.modelPartsBody = new ModelPartsBodyCompat(scriptBodyPos);
        }
    }

    public static final class ModelPartsBodyCompat {
        public final float[] pos;

        public ModelPartsBodyCompat(Vec3 pos) {
            this.pos = new float[] {(float) pos.x, (float) pos.y, (float) pos.z};
        }
    }

    private boolean shouldHandleCrossingLogic() {
        //定義から不変。definitionId が同じ間はキャッシュを返す (毎servertick の toLowerCase×4+contains×9 を排除)。
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
            return false;  //定義未解決: まだキャッシュしない (登録が読めたら次tickで再計算)
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
