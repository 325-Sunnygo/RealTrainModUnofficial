package com.portofino.realtrainmodunofficial.entity;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import com.portofino.realtrainmodunofficial.RealTrainModUnofficialItems;
import com.portofino.realtrainmodunofficial.RealTrainModUnofficialEntities;
import com.portofino.realtrainmodunofficial.block.LargeRailCoreBlock;
import com.portofino.realtrainmodunofficial.block.RailCollisionBlock;
import com.portofino.realtrainmodunofficial.blockentity.LargeRailCoreBlockEntity;
import com.portofino.realtrainmodunofficial.blockentity.RailCollisionBlockEntity;
import jp.ngt.rtm.rail.util.RailMap;
import jp.ngt.rtm.rail.util.RailPosition;
import com.portofino.realtrainmodunofficial.script.TrainScriptSystem;
import com.portofino.realtrainmodunofficial.vehicle.VehicleDefinition;
import com.portofino.realtrainmodunofficial.vehicle.VehicleRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import javax.script.ScriptEngine;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

public class TrainEntity extends Entity {
    /** 本家 EntityVehicleBase:43 相当。車両ごとに永続する ScriptExecuter (count を進める)。 */
    public final jp.ngt.rtm.modelpack.ScriptExecuter scriptExecuter = new jp.ngt.rtm.modelpack.ScriptExecuter();


    private static final EntityDataAccessor<String> VEHICLE_ID =
        SynchedEntityData.defineId(TrainEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Float> SPEED =
        SynchedEntityData.defineId(TrainEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> TRAIN_DISTANCE =
        SynchedEntityData.defineId(TrainEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> NOTCH =
        SynchedEntityData.defineId(TrainEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> HEADLIGHT_ON =
        SynchedEntityData.defineId(TrainEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DOOR_OPEN =
        SynchedEntityData.defineId(TrainEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DOOR_LEFT_OPEN =
        SynchedEntityData.defineId(TrainEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DOOR_RIGHT_OPEN =
        SynchedEntityData.defineId(TrainEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> LIGHT_MODE =
        SynchedEntityData.defineId(TrainEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> PANTOGRAPH_UP =
        SynchedEntityData.defineId(TrainEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> REVERSE =
        SynchedEntityData.defineId(TrainEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> REVERSER =
        SynchedEntityData.defineId(TrainEntity.class, EntityDataSerializers.INT);
    /** 転換クロスシートの向き。0=前向き/1=後ろ向き/-1=未設定。 */
    private static final EntityDataAccessor<Integer> SEAT_DIRECTION =
        SynchedEntityData.defineId(TrainEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DESTINATION_INDEX =
        SynchedEntityData.defineId(TrainEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SOUND_INDEX =
        SynchedEntityData.defineId(TrainEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> BODY_ROLL =
        SynchedEntityData.defineId(TrainEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> CUSTOM_BUTTON_BITS =
        SynchedEntityData.defineId(TrainEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> RAIL_PROGRESS =
        SynchedEntityData.defineId(TrainEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<String> SEAT_ASSIGNMENTS =
        SynchedEntityData.defineId(TrainEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Optional<UUID>> COUPLED_FOLLOWER =
        SynchedEntityData.defineId(TrainEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Optional<UUID>> COUPLED_LEADER =
        SynchedEntityData.defineId(TrainEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Boolean> INTERIOR_LIGHT_ON =
        SynchedEntityData.defineId(TrainEntity.class, EntityDataSerializers.BOOLEAN);
    // 前後(端)台車のワールド位置を「エンティティ位置からのオフセット」でサーバー→クライアント同期する。
    // クライアントは movement(travelAlongRail)を走らせずレールマップも持たないため、これが無いと
    // 台車を剛体(弦上)で描画してカーブでレールからズレる。サーバーの実台車位置を同期して正確に描く。
    private static final EntityDataAccessor<Float> FRONT_BOGIE_DX =
        SynchedEntityData.defineId(TrainEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> FRONT_BOGIE_DY =
        SynchedEntityData.defineId(TrainEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> FRONT_BOGIE_DZ =
        SynchedEntityData.defineId(TrainEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> REAR_BOGIE_DX =
        SynchedEntityData.defineId(TrainEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> REAR_BOGIE_DY =
        SynchedEntityData.defineId(TrainEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> REAR_BOGIE_DZ =
        SynchedEntityData.defineId(TrainEntity.class, EntityDataSerializers.FLOAT);
    /** 端台車のワールド位置同期が有効か(サーバーがレール上に乗っている間のみ true)。 */
    private static final EntityDataAccessor<Boolean> BOGIE_SYNC_VALID =
        SynchedEntityData.defineId(TrainEntity.class, EntityDataSerializers.BOOLEAN);
    // 端台車のレール接線ヨー(度)もサーバーから同期する。クライアントで毎フレーム探索計算すると
    // 別レールを拾う/180°反転で「一瞬明日の方向」になるため、サーバーのアンカー接線を正とする(RTM同様)。
    private static final EntityDataAccessor<Float> FRONT_BOGIE_YAW =
        SynchedEntityData.defineId(TrainEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> REAR_BOGIE_YAW =
        SynchedEntityData.defineId(TrainEntity.class, EntityDataSerializers.FLOAT);

    private static final float ACCEL = 0.012f;
    private static final float BRAKE = 0.013f; // 減速度
    private static final float MAX_SPEED = 2.0f;
    // 慣性の法則: 力行/制動を解いたあとも勢いが長く続くよう、転がり抵抗と
    // 空気抵抗を実車に近い小さい値に抑える。FRICTION は等比減衰、DRAG_* は
    // notch<=0 の時にだけ働く線形抵抗。
    private static final float FRICTION = 0.9997f;
    private static final float DRAG_BASE = 0.00012f;
    private static final float DRAG_SPEED_FACTOR = 0.00018f;
    // 本家RTM(EnumNotch)準拠: 力行 P1-P5(5段)、ブレーキ B1-B7 + 非常EB(-8) = 8段。
    private static final int MAX_POWER_NOTCH = 5;
    private static final int MAX_BRAKE_NOTCH = 8;
    // 本家EnumNotch: 力行ノッチごとの最高速 (P_n = 0.36*n)。ブレーキ各段の減速度。
    private static final float RTM_POWER_SPEED_PER_NOTCH = 0.36F;
    private static final float MOVEMENT_SMOOTHING = 0.22f;
    private static final double RAIL_SEARCH_RADIUS = 12.0D;
    private static final double BOGIE_CLICK_RADIUS_SQ = 1.96D;
    private static final double BOGIE_INTERACT_HALF_WIDTH = 0.72D;
    private static final double BOGIE_INTERACT_HALF_HEIGHT = 1.1D;
    private static final double BOGIE_INTERACT_HALF_LENGTH = 1.18D;
    private static final double RTM_VEHICLE_Y_OFFSET = 1.1875D;
    /** BogieRenderer 描画台車(MQO台車モデル車両)の高さ微調整。MSE 等で台車がレールに少し埋まるのを補正。 */
    private static final double BOGIE_RENDER_LIFT = 0.18D;
    private static final double DEFAULT_HALF_WIDTH = 1.35D;
    private static final double DEFAULT_HALF_HEIGHT = 2.2D;
    private static final double TRAIN_BODY_MARGIN = 1.2D;
    private static final double COUPLED_CLEARANCE = 0.55D;
    private static final double BOGIE_SPAN_TOLERANCE = 1.75D;
    private static final double RAIL_CONNECTION_MAX_DISTANCE_SQ = 0.25D;
    private static final float RAIL_CONNECTION_MAX_YAW_DIFF = 20.0F;
    // 1tickの本体中心移動の許容上限(=移動距離 + この余裕)。
    // 逆向き継ぎ目等でのレール選択不安定によるワープとみなし棄却する。
    private static final double RAIL_TELEPORT_TOLERANCE = 3.0D;
    // 0.74 → +0.1 = 0.84。
    private static final double EXTRA_TRAIN_BODY_LIFT = 0.84D;
    private static final double EXTRA_BOGIE_LIFT = 0.02D;
    // モデル原点より低い床下パーツがレールと干渉しない高さ。
    private static final double RAIL_HEIGHT_OFFSET = 1.09D;
    // RTM 本家はレール基準で車体中心を 1.1875 上げて描画する。
    // この実装では sampleRail 側で RAIL_HEIGHT_OFFSET を先に足しているため、
    // ここでは残り分だけを加算して車体と台車の相対位置を本家に合わせる。
    private static final double TRAIN_BODY_HEIGHT_OFFSET = (RTM_VEHICLE_Y_OFFSET - RAIL_HEIGHT_OFFSET) + EXTRA_TRAIN_BODY_LIFT;
    private static final double BOGIE_HITBOX_HEIGHT_OFFSET = 0.25D + EXTRA_BOGIE_LIFT;
    public static final double BOGIE_VISUAL_LIFT = 0.39D;
    private static final float BODY_HITBOX_SIZE = 0.1F;
    private static final int BOGIE_SPLITS_PER_METER = 48;

    private final Map<UUID, Integer> seatAssignments = new HashMap<>();
    private final Map<String, String> scriptData = new HashMap<>();
    private boolean scriptDataDirty = false;
    private UUID coupledFollowerUuid;
    private UUID coupledLeaderUuid;
    private int coupledFollowerThisSide = -1;
    private int coupledFollowerOtherSide = 1;
    private com.portofino.realtrainmodunofficial.entity.legacyformation.Formation formation;
    private int travelStallTicks = 0;
    private long lastTravelStallLogTick = Long.MIN_VALUE;
    private int railGuidanceFailureTicks = 0;
    private int centerGuidanceFallbackTicks = 0;
    private int[] customButtonValues;
    private ScriptEngine scriptEngine;
    private ScriptEngine soundScriptEngine;
    private boolean attemptedSoundScriptLoad;
    private ScriptEngine serverScriptEngine;
    private boolean attemptedServerScriptLoad;
    public final WorldCompat field_70170_p = new WorldCompat(this);
    public int field_70173_aa;
    public float field_70177_z;
    private RailMap activeRailMap;
    private int activeRailSplit;
    private int activeRailIndex = -1;
    private int activeRailDirection = 1;
    private int activeRailBodyDirection = 1;
    private double activeRailPosition = -1.0D;
    private RailAnchor frontRailAnchor;
    private RailAnchor rearRailAnchor;
    private final RailMap[] bogiePrevMaps = new RailMap[2];
    private final int[] bogiePrevSplits = new int[2];
    private final int[] bogiePrevSampleIndex = new int[]{-1, -1};
    private final float[] bogieYawMemory = new float[2];
    private final float[] bogiePitchMemory = new float[2];
    // クライアント描画で台車をレール追従させる際の RailMap キャッシュ(全探索を避ける)。
    // bogieIndex を 0/1 の extreme side に正規化したインデックスで保持。
    private final RailMap[] clientBogieRailMap = new RailMap[2];
    // 台車レール追従の水平オフセット(本体ローカル)を平滑化して保持。探索が一瞬失敗しても
    // 直前値を維持し、台車が一瞬剛体位置へ戻って「外れて見える」のを防ぐ。
    private UUID activeDriverUuid;
    private int activeDriverTicks;
    private int clientLerpSteps;
    private double clientLerpX;
    private double clientLerpY;
    private double clientLerpZ;
    private double clientLerpYRot;
    private double clientLerpXRot;
    // クライアント: 同期された端台車オフセット(エンティティ相対)の前tick/現tick値。
    // tick段付きを無くすため、描画時に partialTicks で補間する(本家RTMの台車補間に相当)。
    private Vec3 clientRearBogieOffPrev = Vec3.ZERO;
    private Vec3 clientRearBogieOffCurr = Vec3.ZERO;
    private Vec3 clientFrontBogieOffPrev = Vec3.ZERO;
    private Vec3 clientFrontBogieOffCurr = Vec3.ZERO;
    private boolean clientBogieOffInit;
    // クライアント: 端台車(0=後/1=前)のレール接線ヨーの前tick/現tick値。
    // 毎フレーム生計算すると微振動(ガクガク)、減衰平滑すると遅延する。
    private final float[] clientBogieYawPrev = {Float.NaN, Float.NaN};
    private final float[] clientBogieYawCurr = {Float.NaN, Float.NaN};
    private final int[] clientBogieYawRejectCount = {0, 0};
    // 位置(同期オフセット)のレール継ぎ目グリッチ除去用。0=後/1=前。
    private final int[] clientBogieOffRejectCount = {0, 0};
    private int interactionHitboxRefreshCooldown;
    private float rotationRoll;
    private float prevRotationRoll;
    public float doorMoveL;
    public float doorMoveR;
    // クライアント側ドア開閉音 (sound_DoorOpen/sound_DoorClose) の状態変化検出用。
    // 多くのパック (209/125 系等) はサウンドスクリプトでドア音を鳴らさず、この設定に頼る。
    private boolean prevDoorLeftForSound;
    private boolean prevDoorRightForSound;
    private boolean doorSoundStateInitialized;
    public float pantograph_F = 40.0F;
    public float pantograph_B = 40.0F;
    public float seatRotation;
    private static final Map<UUID, CouplingSelection> COUPLING_MODE = new HashMap<>();
    private final Map<Integer, UUID> bogieHitboxUuids = new HashMap<>();
    private final Map<Integer, UUID> seatHitboxUuids = new HashMap<>();

    public TrainEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        // noCulling は false。
        // 広い AABB でフラスタム判定するので、画面外の車両は描画(JS実行含む)がスキップされる。
        this.noCulling = false;
    }

    public static TrainEntity create(Level level, String vehicleId, double x, double y, double z, float yRot, float trainDistance) {
        TrainEntity e = RealTrainModUnofficialEntities.TRAIN.get().create(level);
        if (e == null) return null;
        e.setVehicleId(vehicleId);
        e.setTrainDistance(trainDistance);
        e.setLightMode(0);
        // スポーン位置をレール基準で少し上げる（埋まり防止）
        e.moveTo(x, y + RAIL_HEIGHT_OFFSET, z, yRot, 0f);
        // 前tick位置(xo/yo/zo)を現在位置に揃える。これをしないとスポーン直後の
        // 描画補間が原点(0,0,0)から行われ、台車のワールド位置計算が大きくズレる
        // (=「列車を出した瞬間だけ台車がズレる/動かすと直る」の原因)。
        e.setOldPosAndRot();
        e.refreshDimensions();

        // スクリプトはMqoModelLoaderでロードされるため、ここではロードしない
        return e;
    }

    /** The rail this train currently sits on (used by placement to allow side-by-side trains on separate rails). */
    public RailMap getActiveRailMap() {
        return activeRailMap;
    }

    public void initializeOnRail(RailMap map, int split, int index) {
        if (map == null || split <= 0) {
            return;
        }
        activeRailMap = map;
        activeRailSplit = getMovementSplitForMap(map);
        double normalized = Mth.clamp((double) index / (double) split, 0.0D, 1.0D);
        activeRailIndex = Mth.clamp((int) Math.round(normalized * activeRailSplit), 0, activeRailSplit);
        activeRailPosition = normalized * activeRailSplit;
        activeRailBodyDirection = getBodyDirectionOnRail(map, activeRailSplit, activeRailIndex, getYRot());
        activeRailDirection = activeRailBodyDirection;
        setRailProgress(activeRailIndex / (float) activeRailSplit);

        RailSample requestedCenter = sampleRail(map, activeRailSplit, activeRailIndex);
        RailAnchorPair pair = findBestAnchorPairForCenter(map, activeRailSplit, activeRailPosition, requestedCenter, activeRailBodyDirection);
        frontRailAnchor = pair.front();
        rearRailAnchor = pair.rear();
        RailSample front = pair.frontSample();
        RailSample rear = pair.rearSample();
        float yaw = getRailYawForBody(map, activeRailSplit, activeRailIndex, activeRailBodyDirection, getYRot());
        float pitch = getRailPitchForBody(map, activeRailSplit, activeRailIndex, activeRailBodyDirection);
        applyPoseFromBogieSamples(front, rear, yaw, pitch, true);
        syncBogieOrientationMemory(front, rear, yaw, pitch);
        updateStoredBogieState();
        setDeltaMovement(Vec3.ZERO);
        setSpeed(0.0F);
        setNotch(0);
        // レール整列後の本体位置を前tick位置にも反映し、初回フレームの補間ズレ
        // (台車が一瞬ズレて見える)を防ぐ。
        setOldPosAndRot();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(VEHICLE_ID, "");
        builder.define(SPEED, 0.0f);
        builder.define(TRAIN_DISTANCE, 4.5f);
        builder.define(NOTCH, 0);
        builder.define(HEADLIGHT_ON, false);
        builder.define(DOOR_OPEN, false);
        builder.define(DOOR_LEFT_OPEN, false);
        builder.define(DOOR_RIGHT_OPEN, false);
        builder.define(LIGHT_MODE, 0);
        builder.define(PANTOGRAPH_UP, true);
        builder.define(REVERSE, false);
        builder.define(REVERSER, 1);
        // -1 = まだ誰も座っていない (進行方向に従う)
        builder.define(SEAT_DIRECTION, -1);
        builder.define(DESTINATION_INDEX, 0);
        builder.define(SOUND_INDEX, 0);
        builder.define(BODY_ROLL, 0.0F);
        builder.define(CUSTOM_BUTTON_BITS, 0);
        builder.define(RAIL_PROGRESS, 0.0F);
        builder.define(SEAT_ASSIGNMENTS, "");
        builder.define(COUPLED_FOLLOWER, Optional.empty());
        builder.define(COUPLED_LEADER, Optional.empty());
        builder.define(INTERIOR_LIGHT_ON, false);
        builder.define(FRONT_BOGIE_DX, 0.0F);
        builder.define(FRONT_BOGIE_DY, 0.0F);
        builder.define(FRONT_BOGIE_DZ, 0.0F);
        builder.define(REAR_BOGIE_DX, 0.0F);
        builder.define(REAR_BOGIE_DY, 0.0F);
        builder.define(REAR_BOGIE_DZ, 0.0F);
        builder.define(BOGIE_SYNC_VALID, false);
        builder.define(FRONT_BOGIE_YAW, 0.0F);
        builder.define(REAR_BOGIE_YAW, 0.0F);
    }

    public String getVehicleId() { return entityData.get(VEHICLE_ID); }
    public void setVehicleId(String id) { entityData.set(VEHICLE_ID, id != null ? id : ""); }
    public float getSpeed() { return entityData.get(SPEED); }
    public void setSpeed(float speed) { entityData.set(SPEED, speed); }
    /** 動輪/ロッドの累積回転角(度, 0-360)。毎tickの移動距離で加算。スクリプトの getWheelRotationR が参照。 */
    private float wheelRotationDegrees = 0.0F;
    public float getWheelRotationDegrees() { return wheelRotationDegrees; }
    public float getTrainDistance() { return entityData.get(TRAIN_DISTANCE); }
    public void setTrainDistance(float distance) { entityData.set(TRAIN_DISTANCE, Math.max(2.5f, distance)); }
    public int getNotch() { return entityData.get(NOTCH); }
    public void setNotch(int notch) { entityData.set(NOTCH, Mth.clamp(notch, -getMaxBrakeNotch(), getMaxPowerNotch())); }
    public int getMaxPowerNotch() { return getMaxPowerNotch(VehicleRegistry.getById(getVehicleId())); }
    public int getMaxBrakeNotch() { return MAX_BRAKE_NOTCH; }
    public boolean isHeadlightOn() { return entityData.get(HEADLIGHT_ON); }
    public void setHeadlightOn(boolean value) { setLightMode(value ? 1 : 0); }
    public int getLightMode() { return entityData.get(LIGHT_MODE); }
    public void setLightMode(int value) {
        int mode = Mth.clamp(value, 0, 3);
        entityData.set(LIGHT_MODE, mode);
        entityData.set(HEADLIGHT_ON, mode == 1 || mode == 3);
    }
    public void setLightModeForFormation(int value) {
        if (level().isClientSide()) {
            setLightMode(value);
            return;
        }
        forEachFormationTrain(train -> train.setLightMode(value));
    }
    public boolean isInteriorLightOn() { return entityData.get(INTERIOR_LIGHT_ON); }
    public void setInteriorLightOn(boolean value) { entityData.set(INTERIOR_LIGHT_ON, value); }
    public void setInteriorLightOnForFormation(boolean value) {
        if (level().isClientSide()) {
            setInteriorLightOn(value);
            return;
        }
        forEachFormationTrain(train -> train.setInteriorLightOn(value));
    }
    public boolean isDoorOpen() { return entityData.get(DOOR_OPEN); }
    public boolean isDoorLeftOpen() { return entityData.get(DOOR_LEFT_OPEN); }
    public boolean isDoorRightOpen() { return entityData.get(DOOR_RIGHT_OPEN); }
    public void setDoorOpen(boolean value) {
        entityData.set(DOOR_OPEN, value);
        entityData.set(DOOR_LEFT_OPEN, value);
        entityData.set(DOOR_RIGHT_OPEN, value);
    }
    public void setDoorLeftOpen(boolean value) {
        entityData.set(DOOR_LEFT_OPEN, value);
        entityData.set(DOOR_OPEN, value || isDoorRightOpen());
    }
    public void setDoorRightOpen(boolean value) {
        entityData.set(DOOR_RIGHT_OPEN, value);
        entityData.set(DOOR_OPEN, value || isDoorLeftOpen());
    }
    public void toggleDoorForFormation() { setDoorOpenForFormation(!isDoorOpen()); }
    public void setDoorOpenForFormation(boolean value) {
        if (level().isClientSide()) {
            setDoorOpen(value);
            return;
        }
        forEachFormationTrain(train -> train.setDoorOpen(value));
    }
    public void toggleDoorSideForFormation(boolean left) {
        boolean next = left ? !isDoorLeftOpen() : !isDoorRightOpen();
        if (level().isClientSide()) {
            if (left) setDoorLeftOpen(next);
            else setDoorRightOpen(next);
            return;
        }
        forEachFormationTrain(train -> {
            if (left) train.setDoorLeftOpen(next);
            else train.setDoorRightOpen(next);
        });
    }
    public boolean isPantographUp() { return entityData.get(PANTOGRAPH_UP); }
    public void setPantographUp(boolean value) { entityData.set(PANTOGRAPH_UP, value); }
    public void setPantographUpForFormation(boolean value) {
        if (level().isClientSide()) {
            setPantographUp(value);
            return;
        }
        forEachFormationTrain(train -> train.setPantographUp(value));
    }
    public int getReverser() { return entityData.get(REVERSER); }
    public void setReverser(int value) {
        int clamped = Mth.clamp(value, -1, 1);
        entityData.set(REVERSER, clamped);
        entityData.set(REVERSE, clamped < 0);
    }
    /** 転換クロスシートの向き。乗り込んだ向きで自動転換。未乗車は進行方向に従う。 */
    public float getSeatDirection() {
        int dir = entityData.get(SEAT_DIRECTION);
        if (dir < 0) {
            // 未設定: 進行方向に合わせる (従来の挙動)
            return getReverser() >= 0 ? 0.0F : 1.0F;
        }
        return dir;
    }

    /** 乗り込んだ向きで編成全体の座席を転換する。 */
    public void updateSeatDirectionFor(Player player) {
        if (player == null || level().isClientSide) {
            return;
        }
        float relative = Mth.wrapDegrees(player.getYRot() - getYRot());
        int dir = Math.abs(relative) > 90.0F ? 1 : 0;
        for (TrainEntity car : getFormationTrainsForDisplay()) {
            car.entityData.set(SEAT_DIRECTION, dir);
        }
    }

    public boolean isReverse() { return getReverser() < 0; }
    public void setReverse(boolean value) { setReverser(value ? -1 : 1); }
    public int getDestinationIndex() { return entityData.get(DESTINATION_INDEX); }
    public void setDestinationIndex(int value) { entityData.set(DESTINATION_INDEX, Math.max(0, value)); }
    public void setDestinationIndexForFormation(int value) {
        int index = Math.max(0, value);
        if (level().isClientSide()) {
            setDestinationIndex(index);
            return;
        }
        forEachFormationTrain(train -> train.setDestinationIndex(index));
    }
    public int getSoundIndex() { return entityData.get(SOUND_INDEX); }
    public void setSoundIndex(int value) { entityData.set(SOUND_INDEX, Math.max(0, value)); }
    public float getBodyRoll() { return entityData.get(BODY_ROLL); }
    public void setBodyRoll(float value) {
        entityData.set(BODY_ROLL, value);
        this.rotationRoll = value;
    }
    public float getVisualRoll(float partialTicks) {
        return Mth.lerp(partialTicks, prevRotationRoll, rotationRoll);
    }
    public int getCustomButtonBits() { return entityData.get(CUSTOM_BUTTON_BITS); }
    public void setCustomButtonBits(int bits) { entityData.set(CUSTOM_BUTTON_BITS, bits); }
    public float getRailProgress() { return entityData.get(RAIL_PROGRESS); }
    public void setRailProgress(float progress) { entityData.set(RAIL_PROGRESS, Mth.clamp(progress, 0.0F, 1.0F)); }
    private String getSeatAssignmentsData() { return entityData.get(SEAT_ASSIGNMENTS); }
    private void setSeatAssignmentsData(String data) { entityData.set(SEAT_ASSIGNMENTS, data == null ? "" : data); }

    private void setCoupledFollowerUuid(UUID uuid) {
        coupledFollowerUuid = uuid;
        entityData.set(COUPLED_FOLLOWER, Optional.ofNullable(uuid));
    }

    private void setCoupledLeaderUuid(UUID uuid) {
        coupledLeaderUuid = uuid;
        entityData.set(COUPLED_LEADER, Optional.ofNullable(uuid));
    }

    public boolean isCustomButtonOn(int index) {
        if (index < 0 || index >= 31) return false;
        return (getCustomButtonBits() & (1 << index)) != 0;
    }

    public void setCustomButton(int index, boolean on) {
        if (index < 0 || index >= 31) return;
        int bits = getCustomButtonBits();
        int mask = 1 << index;
        setCustomButtonBits(on ? (bits | mask) : (bits & ~mask));
    }

    public void toggleCustomButton(int index) {
        if (index < 0 || index >= 31) return;
        setCustomButton(index, !isCustomButtonOn(index));
    }

    public String getScriptDataValue(String key) {
        if (key == null || key.isBlank()) {
            return "";
        }
        return scriptData.getOrDefault(key, "");
    }

    public void setScriptDataValue(String key, String value) {
        if (key == null || key.isBlank()) {
            return;
        }
        scriptData.put(key, value == null ? "" : value);
        scriptDataDirty = true;
    }

    public void applyScriptDataSync(java.util.Map<String, String> data) {
        if (data == null || data.isEmpty()) return;
        scriptData.putAll(data);
        for (Map.Entry<String, String> entry : data.entrySet()) {
            String key = entry.getKey();
            if (key == null || !key.startsWith("Button")) continue;
            try {
                int index = Integer.parseInt(key.substring("Button".length()));
                if (index >= 0 && index < 16) {
                    if (customButtonValues == null) customButtonValues = new int[16];
                    customButtonValues[index] = Integer.parseInt(entry.getValue());
                }
            } catch (Exception ignored) {
            }
        }
    }

    public ScriptEngine getScriptEngine() { return this.scriptEngine; }
    public ScriptEngine getSoundScriptEngine() { return this.soundScriptEngine; }
    public void setScriptEngine(ScriptEngine scriptEngine) { this.scriptEngine = scriptEngine; }
    public void setSoundScriptEngine(ScriptEngine soundScriptEngine) {
        this.soundScriptEngine = soundScriptEngine;
        this.attemptedSoundScriptLoad = true;
    }

    private void ensureServerScriptLoaded() {
        if (attemptedServerScriptLoad) return;
        String id = getVehicleId();
        if (id == null || id.isBlank()) return;
        VehicleDefinition def = VehicleRegistry.getById(id);
        if (def == null || !def.hasServerScript()) {
            attemptedServerScriptLoad = true;
            return;
        }
        attemptedServerScriptLoad = true;
        try {
            serverScriptEngine = com.portofino.realtrainmodunofficial.client.model.MqoModelLoader
                .loadServerScriptForVehicle(def);
        } catch (Throwable t) {
            RealTrainModUnofficial.LOGGER.warn("Failed to load train server script for {}: {}", id, t.toString());
        }
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public float getPickRadius() {
        return 0.1F;
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return EntityDimensions.scalable(BODY_HITBOX_SIZE, BODY_HITBOX_SIZE);
    }

    @Override
    protected AABB makeBoundingBox() {
        double x = getX();
        double y = getY();
        double z = getZ();
        double half = BODY_HITBOX_SIZE * 0.5D;
        return new AABB(x - half, y - half, z - half, x + half, y + half, z + half);
    }

    public double getBodyHalfLengthForPlacement() {
        return getTrainHalfLength();
    }

    public double getBodyHalfWidthForPlacement() {
        return getTrainHalfWidth();
    }

    private double getTrainHalfLength() {
        VehicleDefinition def = VehicleRegistry.getById(getVehicleId());
        // trainDistance stores center-to-end distance (half-length), not total length.
        double maxZ = Math.max(1.75D, getTrainDistance());
        if (def != null) {
            for (VehicleDefinition.BogieDefinition bogie : def.getBogies()) {
                maxZ = Math.max(maxZ, Math.abs(bogie.position().z) + 0.95D);
            }
            for (Vec3 seat : def.getAllSeatPositions()) {
                maxZ = Math.max(maxZ, Math.abs(seat.z) + 0.95D);
            }
        }
        return maxZ;
    }

    private double getTrainHalfWidth() {
        VehicleDefinition def = VehicleRegistry.getById(getVehicleId());
        double maxX = DEFAULT_HALF_WIDTH;
        if (def != null) {
            for (Vec3 seat : def.getAllSeatPositions()) {
                maxX = Math.max(maxX, Math.abs(seat.x) + 0.55D);
            }
            for (VehicleDefinition.BogieDefinition bogie : def.getBogies()) {
                maxX = Math.max(maxX, Math.abs(bogie.position().x) + 1.0D);
            }
        }
        return maxX;
    }

    private double getTrainHalfHeight() {
        VehicleDefinition def = VehicleRegistry.getById(getVehicleId());
        double maxY = DEFAULT_HALF_HEIGHT;
        if (def != null) {
            for (Vec3 seat : def.getAllSeatPositions()) {
                maxY = Math.max(maxY, seat.y + 1.8D);
            }
        }
        return maxY;
    }

    @Override
    public void tick() {
        super.tick();
        field_70173_aa = tickCount;
        field_70177_z = getYRot();
        field_70170_p.field_72995_K = level().isClientSide();
        prevRotationRoll = rotationRoll;
        rotationRoll = entityData.get(BODY_ROLL);

        // 動輪/ロッド回転角は毎tickの移動距離で累積する
        {
            float distThisTick = Math.abs(getSpeed());
            final float wheelCircumference = (float) (2.0 * Math.PI * 0.43);
            wheelRotationDegrees = (wheelRotationDegrees + (distThisTick / wheelCircumference) * 360.0F) % 360.0F;
        }

        // スクリプトのtick関数を呼び出す
        updateTrainAnimationState();
        if (level().isClientSide() && soundScriptEngine == null && !attemptedSoundScriptLoad) {
            attemptedSoundScriptLoad = true;
            VehicleDefinition soundVehicle = VehicleRegistry.getById(getVehicleId());
            if (soundVehicle != null && soundVehicle.hasSoundScript()) {
                setSoundScriptEngine(com.portofino.realtrainmodunofficial.client.model.MqoModelLoader.loadSoundScriptForVehicle(soundVehicle));
            }
        }
        boolean runScriptTick = !level().isClientSide() || shouldRunClientVisualScriptThisTick();
        if (level().isClientSide() && soundScriptEngine != null) {
            com.portofino.realtrainmodunofficial.client.sound.LegacyScriptSoundManager.stopAutoRunningSound(this);
            // 本家 SoundUpdaterVehicle.update:45 と同じ onUpdate(su) 1 本。
            // 以前ここだけ「生エンティティを tick/update へ渡す」独自呼出になっていて、
            // EntityTrainBase 側 (本家系) と全く違うフックを同じサウンドスクリプトに投げていた。
            TrainScriptSystem.invokeSoundScript(soundScriptEngine, this);
        } else {
            if (level().isClientSide()) {
                com.portofino.realtrainmodunofficial.client.sound.LegacyScriptSoundManager.tickJsonRunningSound(this);
            }
            // 音は毎tick更新するが、重い描画スクリプトは既存の間引き設定を尊重する。
            if (runScriptTick && scriptEngine != null) {
                TrainScriptSystem.invokeScriptTick(scriptEngine, this);
            }
        }

        if (level().isClientSide()) {
            if (clientLerpSteps > 0) {
                lerpPositionAndRotationStep(clientLerpSteps, clientLerpX, clientLerpY, clientLerpZ, clientLerpYRot, clientLerpXRot);
                clientLerpSteps--;
            } else {
                reapplyPosition();
                setRot(getYRot(), getXRot());
            }
            updateClientBogieOffsetInterpolation();
            return;
        }

        // SD8200 等の serverScriptPath（Server_sd8200_1.js など）を毎tick実行。
        // 方向幕(pck_maku) や buildData 等の DataMap を書き換える処理がここで走る。
        ensureServerScriptLoaded();
        if (serverScriptEngine != null) {
            com.portofino.realtrainmodunofficial.script.TrainScriptSystem
                .invokeServerScriptOnUpdate(serverScriptEngine, this, this.scriptExecuter);
        }

        if (scriptDataDirty && !scriptData.isEmpty()) {
            scriptDataDirty = false;
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                this, new com.portofino.realtrainmodunofficial.network.TrainScriptDataPayload(
                    this.getId(), new java.util.HashMap<>(scriptData)
                )
            );
        }

        if (interactionHitboxRefreshCooldown-- <= 0) {
            ensureBogieHitboxes();
            ensureSeatHitboxes();
            interactionHitboxRefreshCooldown = Math.abs(getSpeed()) > 0.02F ? 10 : 20;
        }

        if (!level().isClientSide()) {
            pruneSeatAssignments();
            for (Entity passenger : List.copyOf(getPassengers())) {
                if (passenger instanceof net.minecraft.world.entity.player.Player player && player.isShiftKeyDown()) {
                    forceDismountPassenger(player);
                }
            }
            if (activeDriverTicks > 0) {
                activeDriverTicks--;
            } else {
                activeDriverUuid = null;
            }
            // Lazy formation init for head/solo cars
            if (!level().isClientSide() && formation == null && coupledLeaderUuid == null) {
                rebuildFormationFromUuidChain();
            }
            boolean isFormationFollower = (formation != null && !formation.isFrontCar(this))
                || (formation == null && coupledLeaderUuid != null);
            if (isFormationFollower) {
                setDeltaMovement(Vec3.ZERO);
                this.hurtMarked = true;
                this.hasImpulse = true;
                // 後続車もここで早期returnするため、台車位置の同期を必ず行う。
                // (アンカーは編成側 formation.updateTrainMovement が設定済み。
                syncBogieRenderOffsets();
                return;
            }

            float speed = getSpeed();
            int notch = getNotch();
            // ATSA(別mod)は NeoForge の EntityTickEvent 経由で notch を制御するため、
            // ここでの直接呼び出しは行わない。ATSA未導入時は何も起きない。

            FormationDriver formationDriver = getFormationDriver();
            Entity controller = formationDriver != null ? formationDriver.controller() : null;
            TrainEntity cabTrain = formationDriver != null ? formationDriver.cabTrain() : this;

            if (getReverser() == 0 && notch > 0) {
                notch = 0;
            }

            speed = applyNotchPhysics(speed, notch);

            setNotch(notch);

            if (notch <= 0) {
                float drag = DRAG_BASE + Math.abs(speed) * DRAG_SPEED_FACTOR;
                speed = approachZero(speed, drag);
            }
            speed *= FRICTION;
            if (Math.abs(speed) < 0.001f && notch <= 0) speed = 0.0f;

            setSpeed(speed);

            // 先に支え(レール)の有無を判定する。レールが壊れて支えが無い時に travelAlongRail を
            // 走らせると、キャッシュ済みアンカー(壊れたレール位置)へスナップし続けて空中に浮くため、
            // 支え無しならレール追従をスキップして重力に任せる。
            boolean unsupported = isUnsupportedInAir();
            boolean onRail = !unsupported && travelAlongRail(speed, controller, cabTrain);
            if (!onRail || unsupported) {
                setSpeed(0.0F);
                setNotch(0);
                setNoGravity(false);
                Vec3 dm = getDeltaMovement();
                double vy = dm.y;
                vy -= 0.08D;   // 重力加速度
                vy *= 0.98D;   // 空気抵抗
                double vx = dm.x * 0.98D;
                double vz = dm.z * 0.98D;
                setDeltaMovement(vx, vy, vz);
                move(net.minecraft.world.entity.MoverType.SELF, getDeltaMovement());
                if (onGround() || verticalCollision) {
                    setDeltaMovement(getDeltaMovement().multiply(0.6D, 0.0D, 0.6D));
                }
                this.hurtMarked = true;
                this.hasImpulse = true;
            } else {
                if (!isNoGravity()) {
                    setNoGravity(true);
                }
            }

            if (formation != null && formation.size() > 1) {
                formation.updateTrainMovement();
            }
            tryCompletePendingCoupling();

            // 端台車のワールド位置をクライアントへ同期(カーブで台車をレール上に正確に描くため)。
            syncBogieRenderOffsets();

            hasImpulse = Math.abs(speed) > 0.001F;
        }
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        clientLerpX = x;
        clientLerpY = y;
        clientLerpZ = z;
        clientLerpYRot = yRot;
        clientLerpXRot = xRot;
        // 位置更新は毎tick来るのでsteps=1で即スナップし台車位置と揃える
        clientLerpSteps = 1;
        setDeltaMovement(Vec3.ZERO);
    }

    private boolean isLocalPlayerOnThisTrain() {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null) return false;
        Entity vehicle = mc.player.getVehicle();
        if (vehicle instanceof TrainEntity t) {
            return t == this || (t.coupledLeaderUuid != null || t.coupledFollowerUuid != null)
                && t.getFormationHead() == getFormationHead();
        }
        if (vehicle instanceof TrainSeatEntity seat) {
            TrainEntity t = seat.getTrain();
            return t != null && (t == this || t.getFormationHead() == getFormationHead());
        }
        return false;
    }

    @Override
    public double lerpTargetX() {
        return clientLerpSteps > 0 ? clientLerpX : getX();
    }

    @Override
    public double lerpTargetY() {
        return clientLerpSteps > 0 ? clientLerpY : getY();
    }

    @Override
    public double lerpTargetZ() {
        return clientLerpSteps > 0 ? clientLerpZ : getZ();
    }

    @Override
    public float lerpTargetXRot() {
        return clientLerpSteps > 0 ? (float) clientLerpXRot : getXRot();
    }

    @Override
    public float lerpTargetYRot() {
        return clientLerpSteps > 0 ? (float) clientLerpYRot : getYRot();
    }

    private float applyNotchPhysics(float speed, int notch) {
        VehicleDefinition def = VehicleRegistry.getById(getVehicleId());
        float accelBase = getConfiguredAcceleration(def);

        if (notch > 0) {
            if (getReverser() == 0) {
                return speed;
            }
            float maxSpeed = getConfiguredMaxSpeed(def, notch);
            float absSpeed = Math.abs(speed);
            float speedRatio = Mth.clamp(absSpeed / maxSpeed, 0.0F, 1.0F);
            if (absSpeed >= maxSpeed) {
                return speed;
            }
            // 力行: 実車らしく「発車はゆっくり → 徐々にぐんぐん速くなる → 最高速で頭打ち」。
            float notchFactor = notch / (float) getMaxPowerNotch(def);
            // 発進ランプ: 最高速の約15%まででフル牽引に立ち上げる(カックン発進を防ぐ程度に短め)。
            float launchRamp = Mth.clamp(speedRatio / 0.15F, 0.0F, 1.0F);
            launchRamp = launchRamp * launchRamp * (3.0F - 2.0F * launchRamp);
            // 高速減衰は緩やか(speedRatio^3)。中速までは加速度を保ち「どんどん速くなる」感を出し、
            // 最高速付近でだけ頭打ちにする(実車の定出力域→特性域に近い)。
            float tractionCurve = (0.30F + launchRamp * 0.70F) * (1.0F - (float) Math.pow(speedRatio, 3.0));
            float accelCurve = accelBase * (0.55F + notchFactor * 0.70F) * tractionCurve;
            float next = speed + Math.max(0.00008F, accelCurve);
            return Math.abs(next) > maxSpeed ? Math.copySign(maxSpeed, next) : next;
        }

        if (notch < 0) {
            // 本家RTM(EnumNotch)準拠のブレーキ。B1=-0.0005 ... B7=-0.0035、非常EB(-8)=-0.01。
            int b = -notch; // 1..8
            float decel = (b >= MAX_BRAKE_NOTCH) ? 0.01F : 0.0005F * b;
            return approachZero(speed, decel);
        }

        return speed;
    }

    private float getConfiguredMaxSpeed(VehicleDefinition def, int notch) {
        if (def != null && notch > 0 && !def.getNotchMaxSpeeds().isEmpty()) {
            int index = Mth.clamp(notch - 1, 0, def.getNotchMaxSpeeds().size() - 1);
            float configured = def.getNotchMaxSpeeds().get(index);
            if (configured > 0.0F) {
                return configured;
            }
        }
        // 本家RTM(EnumNotch): 力行ノッチごとに最高速を制限する(P_n = 0.36*n)。
        // これで低ノッチは低速で頭打ち、高ノッチほど伸びる本家挙動になる。
        if (notch > 0) {
            return RTM_POWER_SPEED_PER_NOTCH * Math.min(notch, MAX_POWER_NOTCH);
        }
        return MAX_SPEED;
    }

    private float getConfiguredAcceleration(VehicleDefinition def) {
        if (def != null && def.getAcceleration() > 0.0F) {
            // 実車に近い穏やかな加速にするため倍率と上限を下げる
            // (旧: *1.75 / 上限0.0062 は急加速ぎみだった)。
            return Mth.clamp(def.getAcceleration() * 1.30F, 0.0006F, 0.0042F);
        }
        return 0.0022F;
    }

    private boolean shouldRunClientVisualScriptThisTick() {
        if (!level().isClientSide()) {
            return true;
        }
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player != null && mc.player.getVehicle() == this) {
            return true;
        }
        Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();
        double distanceSq = cameraPos.distanceToSqr(getX(), getY() + 1.5D, getZ());
        if (distanceSq > 140.0D * 140.0D) {
            return (tickCount & 7) == 0;
        }
        if (distanceSq > 80.0D * 80.0D) {
            return (tickCount & 3) == 0;
        }
        if (distanceSq > 40.0D * 40.0D) {
            return (tickCount & 1) == 0;
        }
        return true;
    }

    private void updateTrainAnimationState() {
        doorMoveL = approach(doorMoveL, isDoorLeftOpen() ? 60.0F : 0.0F, 1.0F);
        doorMoveR = approach(doorMoveR, isDoorRightOpen() ? 60.0F : 0.0F, 1.0F);
        if (level().isClientSide()) {
            updateDoorSound();
        }
        // 本家RTM準拠: pantograph movement(=pantograph_F/40) は「下降量」。DOWN で 40(=1.0)、UP で 0。
        // (RTMU は従来 UP で 40 にしており、パンタ上で下降表示・下で上昇表示と逆になっていた。)
        pantograph_F = approach(pantograph_F, isPantographUp() ? 0.0F : 40.0F, 1.0F);
        pantograph_B = approach(pantograph_B, isPantographUp() ? 0.0F : 40.0F, 1.0F);
        int reverserDir = getReverser();
        // 座席 script 側は -45〜45 度前提なので、進行方向へゆっくり寄せていく。
        if (reverserDir < 0 && seatRotation > -45.0F) {
            seatRotation -= 1.0F;
        } else if (reverserDir > 0 && seatRotation < 45.0F) {
            seatRotation += 1.0F;
        }
        seatRotation = Mth.clamp(seatRotation, -45.0F, 45.0F);
    }

    /** ドア開閉変化を検出してsound_DoorOpen/Closeを鳴らす。初回tickは記録のみ。 */
    private void updateDoorSound() {
        boolean left = isDoorLeftOpen();
        boolean right = isDoorRightOpen();
        if (!doorSoundStateInitialized) {
            prevDoorLeftForSound = left;
            prevDoorRightForSound = right;
            doorSoundStateInitialized = true;
            return;
        }
        if (left == prevDoorLeftForSound && right == prevDoorRightForSound) {
            return;
        }
        // 開いた側があれば開音、閉じた側があれば閉音 (左右同時操作は各 1 回に集約)。
        boolean opened = (left && !prevDoorLeftForSound) || (right && !prevDoorRightForSound);
        boolean closed = (!left && prevDoorLeftForSound) || (!right && prevDoorRightForSound);
        prevDoorLeftForSound = left;
        prevDoorRightForSound = right;
        VehicleDefinition def = VehicleRegistry.getById(getVehicleId());
        if (def == null) {
            return;
        }
        if (opened) {
            playDoorSound(def.getSoundDoorOpen());
        }
        if (closed) {
            playDoorSound(def.getSoundDoorClose());
        }
    }

    private void playDoorSound(String soundId) {
        if (soundId == null || soundId.isBlank()) {
            return;
        }
        com.portofino.realtrainmodunofficial.client.sound.LegacyScriptSoundManager
            .playLegacyId(this, soundId, 1.0F, 1.0F, false);
    }

    private boolean canTravelOnRail(Vec3 worldPos) {
        BlockPos base = BlockPos.containing(worldPos.x, worldPos.y - 0.2, worldPos.z);
        for (int dy = -1; dy <= 1; dy++) {
            BlockPos pos = base.offset(0, dy, 0);
            var block = level().getBlockState(pos).getBlock();
            if (block instanceof RailCollisionBlock || block instanceof LargeRailCoreBlock) {
                return true;
            }
        }
        return false;
    }

    private void syncCoupledFollower() {
        if (coupledFollowerUuid == null || level().isClientSide()) {
            return;
        }
        Entity followerRaw = ((net.minecraft.server.level.ServerLevel) level()).getEntity(coupledFollowerUuid);
        if (!(followerRaw instanceof TrainEntity follower) || !follower.isAlive()) {
            setCoupledFollowerUuid(null);
            coupledFollowerThisSide = -1;
            coupledFollowerOtherSide = 1;
            return;
        }

        follower.setCoupledLeaderUuid(this.getUUID());
        follower.setNotch(this.getNotch());
        follower.setSpeed(this.getSpeed());
        follower.setReverser(this.getReverser());

        double gap = getCoupledGap(this, follower);
        if (!placeCoupledFollowerOnRail(follower, coupledFollowerThisSide, coupledFollowerOtherSide)) {
            placeCoupledFollowerFallback(follower, coupledFollowerThisSide, coupledFollowerOtherSide, gap);
        }
        follower.hurtMarked = true;
        follower.hasImpulse = true;
    }

    private void placeCoupledFollowerFallback(TrainEntity follower, int thisSide, int followerSide, double gap) {
        if (follower == null) {
            return;
        }
        int currentSide = normalizeCouplerSide(thisSide);
        int otherSide = normalizeCouplerSide(followerSide);
        Vec3 forward = localToWorld(new Vec3(0.0D, 0.0D, 1.0D)).subtract(position()).normalize();
        if (forward.lengthSqr() < 1.0E-6D) {
            double yawRad = Math.toRadians(-getYRot());
            forward = new Vec3(-Math.sin(yawRad), 0.0D, Math.cos(yawRad));
        }
        // 連結間隔は当たり判定用の膨張長ではなく実車体端(連結面)で配置する(短車両の間隔過大対策)。
        double thisHalf = getCouplingHalfLength();
        double followerHalf = follower.getCouplingHalfLength();
        int followerDirection = -currentSide * otherSide;
        Vec3 coupler = position().add(forward.scale(currentSide * (thisHalf + COUPLED_CLEARANCE)));
        Vec3 center = coupler.subtract(forward.scale(followerDirection * otherSide * followerHalf));
        float yaw = followerDirection < 0 ? Mth.wrapDegrees(getYRot() + 180.0F) : getYRot();
        follower.moveTo(center.x, getY(), center.z, yaw, follower.getXRot());
    }

    private void syncCoupledChain() {
        if (!(level() instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            return;
        }

        TrainEntity current = this;
        int guard = 0;
        while (current.coupledFollowerUuid != null && guard++ < 16) {
            Entity nextRaw = serverLevel.getEntity(current.coupledFollowerUuid);
            if (!(nextRaw instanceof TrainEntity next) || !next.isAlive()) {
                current.setCoupledFollowerUuid(null);
                current.coupledFollowerThisSide = -1;
                current.coupledFollowerOtherSide = 1;
                break;
            }

            next.setCoupledLeaderUuid(current.getUUID());
            next.setNotch(current.getNotch());
            next.setSpeed(current.getSpeed());
            next.setReverser(current.getReverser());
            next.setDeltaMovement(Vec3.ZERO);

            double gap = getCoupledGap(current, next);
            if (!current.placeCoupledFollowerOnRail(next, current.coupledFollowerThisSide, current.coupledFollowerOtherSide)) {
                current.placeCoupledFollowerFallback(next, current.coupledFollowerThisSide, current.coupledFollowerOtherSide, gap);
            }
            next.hurtMarked = true;
            next.hasImpulse = true;

            current = next;
        }
    }

    private void syncCoupledFormationFromHead() {
        if (!(level() instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            syncCoupledChain();
            return;
        }

        TrainEntity head = this;
        int guard = 0;
        while (head.coupledLeaderUuid != null && guard++ < 16) {
            Entity leaderRaw = serverLevel.getEntity(head.coupledLeaderUuid);
            if (leaderRaw instanceof TrainEntity leader && leader.isAlive()) {
                head = leader;
            } else {
                head.setCoupledLeaderUuid(null);
                break;
            }
        }
        head.syncCoupledChain();
    }

    private boolean placeCoupledFollowerOnRail(TrainEntity follower, int thisSide, int followerSide) {
        if (follower == null || activeRailMap == null || activeRailSplit <= 0 || activeRailPosition < 0.0D) {
            return false;
        }
        int currentSide = normalizeCouplerSide(thisSide);
        int otherSide = normalizeCouplerSide(followerSide);
        int bodyDirection = activeRailBodyDirection == 0 ? 1 : activeRailBodyDirection;
        double gap = getCoupledGap(this, follower);

        // アンカー基準の走査で継ぎ目を正しく越える。offsetの符号で方向を反転
        RailAnchor leaderCenter = new RailAnchor(
            activeRailMap, activeRailSplit,
            Mth.clamp(activeRailPosition, 0.0D, activeRailSplit),
            bodyDirection
        );
        RailAnchor followerCenter = advanceAnchorAlongPath(leaderCenter, (double) currentSide * gap);
        if (!isRailAnchorUsable(followerCenter)) {
            return false;
        }

        int desiredFollowerBodyDirection = bodyDirection * (-currentSide * otherSide);
        RailAnchorPair pair = follower.createAnchorPairFromCenter(
            followerCenter.map(),
            followerCenter.split(),
            followerCenter.index(),
            desiredFollowerBodyDirection
        );
        if (!isRailAnchorUsable(pair.front()) || !isRailAnchorUsable(pair.rear())) {
            return false;
        }
        follower.frontRailAnchor = pair.front();
        follower.rearRailAnchor = pair.rear();
        follower.activeRailMap = followerCenter.map();
        follower.activeRailSplit = followerCenter.split();
        follower.activeRailPosition = Mth.clamp(followerCenter.index(), 0.0D, followerCenter.split());
        follower.activeRailIndex = Mth.clamp((int) Math.round(follower.activeRailPosition), 0, follower.activeRailSplit);
        follower.activeRailBodyDirection = desiredFollowerBodyDirection;
        follower.activeRailDirection = activeRailDirection * (-currentSide * otherSide);
        follower.setRailProgress(follower.activeRailIndex / (float) follower.activeRailSplit);

        float yaw = follower.getRailYawForBody(
            follower.activeRailMap,
            follower.activeRailSplit,
            follower.activeRailPosition,
            follower.activeRailBodyDirection,
            follower.getYRot()
        );
        float pitch = follower.getRailPitchForBody(
            follower.activeRailMap,
            follower.activeRailSplit,
            follower.activeRailPosition,
            follower.activeRailBodyDirection
        );
        follower.applyPoseFromBogieSamples(pair.frontSample(), pair.rearSample(), yaw, pitch, false);
        follower.setDeltaMovement(Vec3.ZERO);
        return true;
    }

    /** 連結間隔に使う「中心→車体端」距離。 */
    private double getCouplingHalfLength() {
        return Math.max(1.0D, getTrainDistance());
    }

    private static double getCoupledGap(TrainEntity front, TrainEntity rear) {
        double frontHalf = front == null ? 4.5D : front.getCouplingHalfLength();
        double rearHalf = rear == null ? frontHalf : rear.getCouplingHalfLength();
        // 車体端どうし + 余裕。固定の最小値(旧:4.0)で底上げしないので短い車両は詰まる。
        return Math.max(2.0D, frontHalf + rearHalf + COUPLED_CLEARANCE);
    }

    private static int normalizeCouplerSide(int side) {
        return side >= 0 ? 1 : -1;
    }

    private void forEachFormationTrain(Consumer<TrainEntity> action) {
        if (action == null) {
            return;
        }
        if (formation != null) {
            formation.trainStream().forEach(action);
            return;
        }
        if (!(level() instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            action.accept(this);
            return;
        }

        TrainEntity head = this;
        int guard = 0;
        while (head.coupledLeaderUuid != null && guard++ < 16) {
            Entity leaderRaw = serverLevel.getEntity(head.coupledLeaderUuid);
            if (leaderRaw instanceof TrainEntity leader && leader.isAlive()) {
                head = leader;
            } else {
                head.setCoupledLeaderUuid(null);
                break;
            }
        }

        TrainEntity current = head;
        guard = 0;
        while (current != null && guard++ < 16) {
            action.accept(current);
            if (current.coupledFollowerUuid == null) {
                break;
            }
            Entity followerRaw = serverLevel.getEntity(current.coupledFollowerUuid);
            if (followerRaw instanceof TrainEntity follower && follower.isAlive()) {
                current = follower;
            } else {
                current.setCoupledFollowerUuid(null);
                break;
            }
        }
    }

    public TrainEntity getFormationHead() {
        if (formation != null) {
            com.portofino.realtrainmodunofficial.entity.legacyformation.FormationEntry front = formation.getFrontEntry();
            if (front != null && front.train != null && front.train.isAlive()) {
                return front.train;
            }
        }
        if (!(level() instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            return this;
        }

        TrainEntity head = this;
        int guard = 0;
        while (head.coupledLeaderUuid != null && guard++ < 16) {
            Entity leaderRaw = serverLevel.getEntity(head.coupledLeaderUuid);
            if (leaderRaw instanceof TrainEntity leader && leader.isAlive()) {
                head = leader;
            } else {
                head.setCoupledLeaderUuid(null);
                break;
            }
        }
        return head;
    }

    private TrainEntity getFormationTail() {
        if (!(level() instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            return this;
        }

        TrainEntity tail = getFormationHead();
        int guard = 0;
        while (tail.coupledFollowerUuid != null && guard++ < 16) {
            Entity followerRaw = serverLevel.getEntity(tail.coupledFollowerUuid);
            if (followerRaw instanceof TrainEntity follower && follower.isAlive()) {
                tail = follower;
            } else {
                tail.setCoupledFollowerUuid(null);
                break;
            }
        }
        return tail;
    }

    private FormationDriver getFormationDriver() {
        if (level().isClientSide()) {
            Entity driver = getDriverPassenger();
            if (driver == null) {
                driver = getFirstAssignedPassenger();
            }
            return driver == null ? null : new FormationDriver(driver, this);
        }

        final FormationDriver[] result = new FormationDriver[1];
        forEachFormationTrain(train -> {
            if (result[0] != null || train.activeDriverUuid == null) {
                return;
            }
            Entity driver = train.findAssignedPassenger(train.activeDriverUuid);
            if (driver != null) {
                result[0] = new FormationDriver(driver, train);
            }
        });
        if (result[0] != null) {
            return result[0];
        }
        forEachFormationTrain(train -> {
            if (result[0] != null) {
                return;
            }
            Entity driver = train.getDriverPassenger();
            if (driver != null) {
                result[0] = new FormationDriver(driver, train);
            }
        });
        if (result[0] != null) {
            return result[0];
        }
        forEachFormationTrain(train -> {
            if (result[0] != null) {
                return;
            }
            Entity passenger = train.getFirstAssignedPassenger();
            if (passenger instanceof Player) {
                result[0] = new FormationDriver(passenger, train);
            }
        });
        return result[0];
    }

    private record RailFollowContext(RailMap map, int split, int nearestIndex, double distanceSq) {}
    private record FormationDriver(Entity controller, TrainEntity cabTrain) {}
    private record RailResolvedSample(RailSample sample, RailMap map, int split, double index, int bodyDirection) {}
    private record RailAnchor(RailMap map, int split, double index, int travelDirection) {}
    private record RailAnchorPair(RailAnchor front, RailAnchor rear, RailSample frontSample, RailSample rearSample, double distanceSq) {}
    private record RailConnection(RailMap map, int split, double index, int travelDirection, double score) {}
    private record RailSample(double x, double y, double z) {}

    /** 列車真下に支えのレールがあるか。無ければ落下対象。 */
    private boolean isUnsupportedInAir() {
        if (level() == null) return false;
        // 支え判定はレール系ブロックのみ。車体中心に加え前後台車位置も見る
        if (hasRailSupportNear(blockPosition())) {
            return false;
        }
        if (isRailAnchorUsable(frontRailAnchor)) {
            RailSample s = sampleBogieRail(frontRailAnchor.map(), frontRailAnchor.split(), frontRailAnchor.index());
            if (s != null && hasRailSupportNear(net.minecraft.core.BlockPos.containing(s.x(), s.y(), s.z()))) {
                return false;
            }
        }
        if (isRailAnchorUsable(rearRailAnchor)) {
            RailSample s = sampleBogieRail(rearRailAnchor.map(), rearRailAnchor.split(), rearRailAnchor.index());
            if (s != null && hasRailSupportNear(net.minecraft.core.BlockPos.containing(s.x(), s.y(), s.z()))) {
                return false;
            }
        }
        return true;
    }

    private boolean hasRailSupportNear(net.minecraft.core.BlockPos base) {
        for (int dy = -2; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    net.minecraft.world.level.block.Block b =
                        level().getBlockState(base.offset(dx, dy, dz)).getBlock();
                    if (b instanceof com.portofino.realtrainmodunofficial.block.RailCollisionBlock
                        || b instanceof com.portofino.realtrainmodunofficial.block.LargeRailCoreBlock
                        || b instanceof com.portofino.realtrainmodunofficial.block.BallastBlock) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean travelAlongRail(float speed, Entity controller, TrainEntity cabTrain) {
        // Direction of travel is driven purely by the reverser.
        // Cab direction (seat Z position) is a UI concept only — reading it here caused
        // the train to violently reverse the moment a player mounted a rear seat.
        int controllerDirection = Integer.compare(getReverser(), 0);
        if (controllerDirection == 0 && Math.abs(speed) > 0.0F) {
            controllerDirection = activeRailDirection != 0 ? activeRailDirection : (speed >= 0.0F ? 1 : -1);
        }
        double distance = Math.abs(speed);
        if (distance > 0.0D && controllerDirection == 0) {
            setSpeed(0.0F);
            distance = 0.0D;
        }

        if (!ensureBogieAnchors()) {
            setSpeed(0.0F);
            setDeltaMovement(Vec3.ZERO);
            return false;
        }
        if (activeRailBodyDirection == 0) {
            activeRailBodyDirection = controllerDirection == 0 ? 1 : controllerDirection;
        }
        syncActiveRailStateFromAnchors(activeRailBodyDirection);
        if (activeRailMap == null || activeRailSplit <= 0) {
            RailFollowContext context = getActiveRailContext();
            if (context == null) {
                return false;
            }
            activeRailMap = context.map();
            activeRailSplit = context.split();
            if (activeRailPosition < 0.0D) {
                activeRailPosition = context.nearestIndex();
            }
            activeRailIndex = Mth.clamp((int) Math.round(activeRailPosition), 0, activeRailSplit);
            if (activeRailBodyDirection == 0) {
                activeRailBodyDirection = getBodyDirectionOnRail(activeRailMap, activeRailSplit, activeRailIndex, getYRot());
            }
        }

        if (distance > 0.0D) {
            // ワープ検出用に、前進前のアンカー・方向・本体位置を保存しておく。
            RailAnchor preFrontAnchor = frontRailAnchor;
            RailAnchor preRearAnchor = rearRailAnchor;
            int preBodyDirection = activeRailBodyDirection;
            double preX = getX();
            double preY = getY();
            double preZ = getZ();
            if (advanceBogiePairAlongPath(distance, controllerDirection)) {
                RailSample front = sampleBogieRail(frontRailAnchor.map(), frontRailAnchor.split(), frontRailAnchor.index());
                RailSample rear = sampleBogieRail(rearRailAnchor.map(), rearRailAnchor.split(), rearRailAnchor.index());
                if (front == null || rear == null) {
                    if (!restabilizeBogieAnchors(controllerDirection)) {
                        setSpeed(0.0F);
                        setDeltaMovement(Vec3.ZERO);
                        return false;
                    }
                    front = sampleBogieRail(frontRailAnchor.map(), frontRailAnchor.split(), frontRailAnchor.index());
                    rear = sampleBogieRail(rearRailAnchor.map(), rearRailAnchor.split(), rearRailAnchor.index());
                }
                // ワープ・ガード: 物理的にあり得ない1tickジャンプは棄却して直前状態を維持
                RailSample candidateCenter = resolveBodyCenterSample(front, rear);
                double jumpDx = candidateCenter.x() - preX;
                double jumpDz = candidateCenter.z() - preZ;
                double jumpSq = jumpDx * jumpDx + jumpDz * jumpDz;
                double allowed = distance + RAIL_TELEPORT_TOLERANCE;
                if (jumpSq > allowed * allowed && (preX != 0.0D || preZ != 0.0D)) {
                    // 復帰試行: スパン逆算が膨らみレール等で遠点を拾った場合でも永久停止しないよう、
                    // 前台車は素直に前進、後台車は「前回後台車位置の近傍」で前台車からスパン直線距離の
                    // 点に補正する(連続性優先=膨らみの遠点を拾わない)。結果がしきい値内なら採用して進む。
                    boolean recovered = false;
                    {
                        // 前台車は advanceBogiePairAlongPath が選んだ進行先(分岐切替時は分岐先)をそのまま維持し、
                        // 後台車だけ「前回後台車位置の近傍」で前台車から台車間隔の点に補正する。これで
                        // 分岐ルートを維持したまま、後台車がレールの膨らみの遠点を拾うのを防ぐ。
                        double[] bz = getBogieRailOffsets();
                        double span = Math.abs(bz[1] - bz[0]);
                        RailAnchor recRear = refineAnchorByStraightDistance(preRearAnchor, front, span);
                        if (isRailAnchorUsable(recRear)) {
                            RailSample rr = sampleBogieRail(recRear.map(), recRear.split(), recRear.index());
                            if (rr != null) {
                                RailSample rc = resolveBodyCenterSample(front, rr);
                                double rjx = rc.x() - preX;
                                double rjz = rc.z() - preZ;
                                if (rjx * rjx + rjz * rjz <= allowed * allowed) {
                                    rearRailAnchor = recRear;
                                    rear = rr;
                                    recovered = true;
                                }
                            }
                        }
                    }
                    if (!recovered) {
                    // ワープを棄却: 元のアンカー/方向に戻し、本体は動かさない(速度は維持)。
                    frontRailAnchor = preFrontAnchor;
                    rearRailAnchor = preRearAnchor;
                    activeRailBodyDirection = preBodyDirection;
                    syncActiveRailStateFromAnchors(preBodyDirection == 0 ? 1 : preBodyDirection);
                    setDeltaMovement(Vec3.ZERO);
                    return true;
                    }
                }
                float appliedYaw = applyPoseFromBogieSamples(front, rear, getYRot(), getXRot(), true);
                syncBogieOrientationMemory(front, rear, appliedYaw, getXRot());
                activeRailBodyDirection = chooseStableBodyDirection(
                    activeRailMap,
                    activeRailSplit,
                    activeRailIndex,
                    appliedYaw,
                    activeRailBodyDirection == 0 ? 1 : activeRailBodyDirection
                );
            } else {
                setSpeed(0.0F);
                setDeltaMovement(Vec3.ZERO);
                if (!restabilizeBogieAnchors(controllerDirection)) {
                    return false;
                }
            }
        }

        if (!isRailAnchorUsable(frontRailAnchor) || !isRailAnchorUsable(rearRailAnchor)) {
            if (!restabilizeBogieAnchors(controllerDirection)) {
                setSpeed(0.0F);
                setDeltaMovement(Vec3.ZERO);
                return false;
            }
        }

        RailSample front = sampleBogieRail(frontRailAnchor.map(), frontRailAnchor.split(), frontRailAnchor.index());
        RailSample rear = sampleBogieRail(rearRailAnchor.map(), rearRailAnchor.split(), rearRailAnchor.index());
        if (front == null || rear == null) {
            if (!restabilizeBogieAnchors(controllerDirection)) {
                setSpeed(0.0F);
                setDeltaMovement(Vec3.ZERO);
                return false;
            }
            front = sampleBogieRail(frontRailAnchor.map(), frontRailAnchor.split(), frontRailAnchor.index());
            rear = sampleBogieRail(rearRailAnchor.map(), rearRailAnchor.split(), rearRailAnchor.index());
        }
        float appliedYaw = applyPoseFromBogieSamples(front, rear, getYRot(), getXRot(), false);
        syncBogieOrientationMemory(front, rear, appliedYaw, getXRot());
        activeRailBodyDirection = chooseStableBodyDirection(
            activeRailMap,
            activeRailSplit,
            activeRailIndex,
            appliedYaw,
            activeRailBodyDirection == 0 ? 1 : activeRailBodyDirection
        );
        setDeltaMovement(Vec3.ZERO);
        activeRailIndex = Mth.clamp((int) Math.round(activeRailPosition), 0, activeRailSplit);
        activeRailDirection = controllerDirection == 0 ? activeRailDirection : controllerDirection * (activeRailBodyDirection == 0 ? 1 : activeRailBodyDirection);
        setRailProgress(activeRailIndex / (float) activeRailSplit);
        return true;
    }

    /** refSampleから弦距離targetDistに最も近いレール点を探す。 */
    /** 後続台車は前回位置近傍を優先して弦距離で決め、無理ならアーク逆算で補正。 */
    private RailAnchor deriveTrailingAnchor(RailAnchor prevTrailing, RailSample leadingSample, double span, RailAnchor leading) {
        RailAnchor cont = refineAnchorByStraightDistance(prevTrailing, leadingSample, span);
        if (chordWithinTolerance(cont, leadingSample, span)) {
            return cont;
        }
        RailAnchor arc = advanceAnchorAlongPath(leading, -Math.abs(span));
        if (isRailAnchorUsable(arc)) {
            RailAnchor refined = refineAnchorByStraightDistance(arc, leadingSample, span);
            if (isRailAnchorUsable(refined)) {
                return refined;
            }
        }
        return cont;
    }

    /** baseAnchor の位置が leadingSample から span 弦距離(±1m)になっているか。 */
    private boolean chordWithinTolerance(RailAnchor a, RailSample leadingSample, double span) {
        if (!isRailAnchorUsable(a) || leadingSample == null) {
            return false;
        }
        RailSample s = sampleBogieRail(a.map(), a.split(), a.index());
        if (s == null) {
            return false;
        }
        double dx = s.x() - leadingSample.x();
        double dz = s.z() - leadingSample.z();
        double d = Math.sqrt(dx * dx + dz * dz);
        return Math.abs(d - Math.abs(span)) <= 1.0D;
    }

    private RailAnchor refineAnchorByStraightDistance(RailAnchor baseAnchor, RailSample refSample, double targetDist) {
        if (!isRailAnchorUsable(baseAnchor) || refSample == null) {
            return baseAnchor;
        }
        RailMap map = baseAnchor.map();
        int split = baseAnchor.split();
        // 探索窓は弦-弧差(数十cm〜数m)を吸収できる小さめに固定する。台車間隔(targetDist)でスケール
        // させると窓が非常に広くなり、端点を大きくはみ出す不正レールで遠い張り出し点を拾ってしまい、
        // 本体中心が1tickで瞬間移動→ワープ棄却で列車が停止する(ユーザー報告: 分岐で止まる)。
        int searchInc = Math.max(8, (int) (3.0D * (double) BOGIE_SPLITS_PER_METER));
        int center = Mth.clamp((int) Math.round(baseAnchor.index()), 0, split);
        int min = Math.max(0, center - searchInc);
        int max = Math.min(split, center + searchInc);
        double targetSq = targetDist * targetDist;
        int bestIndex = -1;
        double bestDiff = Double.MAX_VALUE;
        for (int i = min; i <= max; i++) {
            double[] p = map.getRailPos(split, i); // p[1]=x, p[0]=z
            double ddx = p[1] - refSample.x;
            double ddz = p[0] - refSample.z;
            double dsq = ddx * ddx + ddz * ddz;
            double diff = Math.abs(dsq - targetSq);
            if (diff < bestDiff) {
                bestDiff = diff;
                bestIndex = i;
            }
        }
        if (bestIndex < 0) {
            return baseAnchor;
        }
        return new RailAnchor(map, split, bestIndex, baseAnchor.travelDirection());
    }

    private boolean advanceBogiePairAlongPath(double distanceMeters, int controllerDirection) {
        if (!isRailAnchorUsable(frontRailAnchor) || !isRailAnchorUsable(rearRailAnchor) || controllerDirection == 0) {
            return false;
        }
        double[] bogieZ = getBogieRailOffsets();
        double span = bogieZ[1] - bogieZ[0];
        if (Math.abs(span) < 1.0E-4D) {
            return false;
        }
        int bodyDirection = activeRailBodyDirection == 0 ? 1 : activeRailBodyDirection;
        // アンカーごとの進行方向を使う(継ぎ目跨ぎでレール向きが逆でも正しく処理)
        int frontPathDirection = frontRailAnchor.travelDirection() != 0
            ? frontRailAnchor.travelDirection()
            : bodyDirection;
        int rearPathDirection = rearRailAnchor.travelDirection() != 0
            ? rearRailAnchor.travelDirection()
            : -bodyDirection;
        if (controllerDirection > 0) {
            RailAnchor movedFront = advanceBogieAnchor(frontRailAnchor, distanceMeters, frontPathDirection);
            if (!isRailAnchorUsable(movedFront)) {
                return false;
            }
            RailSample movedFrontSample = sampleBogieRail(movedFront.map(), movedFront.split(), movedFront.index());
            // 本家RTM式: 後台車は「前回位置の近傍」で前台車から台車間隔の弦距離になる点を探す(連続性優先・
            // 遠点回避)。前回位置近傍に無い(レール端越え等)場合だけアーク逆算にフォールバックする。
            RailAnchor bestRear = deriveTrailingAnchor(rearRailAnchor, movedFrontSample, Math.abs(span), movedFront);
            if (!isRailAnchorUsable(bestRear)) {
                return false;
            }
            frontRailAnchor = movedFront;
            rearRailAnchor = bestRear;
            syncActiveRailStateFromAnchors(bodyDirection);
            updateStoredBogieState();
            return true;
        }

        RailAnchor movedRear = advanceBogieAnchor(rearRailAnchor, distanceMeters, rearPathDirection);
        if (!isRailAnchorUsable(movedRear)) {
            return false;
        }
        RailSample movedRearSample = sampleBogieRail(movedRear.map(), movedRear.split(), movedRear.index());
        // 本家RTM式: 前台車は「前回位置の近傍」で後台車から台車間隔の弦距離になる点を探す(連続性優先)。
        RailAnchor bestFront = deriveTrailingAnchor(frontRailAnchor, movedRearSample, Math.abs(span), movedRear);
        if (!isRailAnchorUsable(bestFront)) {
            return false;
        }
        rearRailAnchor = movedRear;
        frontRailAnchor = bestFront;
        syncActiveRailStateFromAnchors(bodyDirection);
        updateStoredBogieState();
        return true;
    }

    private boolean restabilizeBogieAnchors(int controllerDirection) {
        RailFollowContext center = getActiveRailContext();
        if (center == null) {
            center = findRailContextNearAny(position(), null);
        }
        if (center == null) {
            return false;
        }
        int bodyDirection = activeRailBodyDirection == 0 ? getBodyDirectionOnRail(center.map(), center.split(), center.nearestIndex(), getYRot()) : activeRailBodyDirection;
        RailAnchorPair pair = createAnchorPairFromCenter(center.map(), center.split(), center.nearestIndex(), bodyDirection);
        if (!isRailAnchorUsable(pair.front()) || !isRailAnchorUsable(pair.rear())) {
            return false;
        }
        frontRailAnchor = pair.front();
        rearRailAnchor = pair.rear();
        updateStoredBogieState();
        activeRailMap = center.map();
        activeRailSplit = center.split();
        activeRailPosition = center.nearestIndex();
        activeRailIndex = center.nearestIndex();
        activeRailBodyDirection = bodyDirection;
        return true;
    }

    private RailAnchorPair createAnchorPairFromCenter(RailMap map, int split, double centerIndex, int bodyDirection) {
        double[] bogieZ = getBogieRailOffsets();
        int dir = bodyDirection == 0 ? 1 : bodyDirection;
        RailAnchor centerAnchor = new RailAnchor(map, split, Mth.clamp(centerIndex, 0.0D, split), dir);
        RailAnchor frontAnchor = advanceAnchorAlongPath(centerAnchor, bogieZ[1]);
        RailAnchor rearAnchor = advanceAnchorAlongPath(centerAnchor, bogieZ[0]);
        if (!isRailAnchorUsable(frontAnchor) || !isRailAnchorUsable(rearAnchor)) {
            RailSample requestedCenter = sampleRail(map, split, centerIndex);
            return findBestAnchorPairForCenter(map, split, centerIndex, requestedCenter, dir);
        }
        // 後台車を前台車からの直線距離=台車間隔に補正(本家RTM準拠・弦-弧ズレ解消)。
        RailSample frontSample = sampleBogieRail(frontAnchor.map(), frontAnchor.split(), frontAnchor.index());
        rearAnchor = refineAnchorByStraightDistance(rearAnchor, frontSample, Math.abs(bogieZ[1] - bogieZ[0]));
        RailSample rearSample = sampleBogieRail(rearAnchor.map(), rearAnchor.split(), rearAnchor.index());
        return new RailAnchorPair(
            frontAnchor,
            rearAnchor,
            frontSample,
            rearSample,
            0.0D
        );
    }

    private void syncActiveRailStateFromAnchors(int fallbackBodyDirection) {
        if (!isRailAnchorUsable(frontRailAnchor) || !isRailAnchorUsable(rearRailAnchor)) {
            return;
        }

        double[] bogieZ = getBogieRailOffsets();
        int bodyDirection = fallbackBodyDirection == 0 ? 1 : fallbackBodyDirection;
        // Go backward from front bogie by bogieZ[1] to reach center (bogieZ[1] > 0, so negate).
        // Go forward from rear bogie by |bogieZ[0]| to reach center; bogieZ[0] < 0 so use it
        // directly — the negative offset flips rearAnchor's backward travelDirection to forward.
        RailAnchor centerFromFront = advanceAnchorAlongPath(frontRailAnchor, -bogieZ[1]);
        RailAnchor centerFromRear = advanceAnchorAlongPath(rearRailAnchor, bogieZ[0]);
        RailAnchor centerAnchor = chooseCenterAnchor(centerFromFront, centerFromRear, bodyDirection);
        if (!isRailAnchorUsable(centerAnchor)) {
            return;
        }
        activeRailMap = centerAnchor.map();
        activeRailSplit = centerAnchor.split();
        activeRailPosition = Mth.clamp(centerAnchor.index(), 0.0D, activeRailSplit);
        activeRailIndex = Mth.clamp((int) Math.round(activeRailPosition), 0, activeRailSplit);
        activeRailBodyDirection = chooseStableBodyDirection(
            activeRailMap,
            activeRailSplit,
            activeRailIndex,
            getYRot(),
            bodyDirection
        );
    }

    private RailAnchor chooseCenterAnchor(RailAnchor centerFromFront, RailAnchor centerFromRear, int bodyDirection) {
        if (isRailAnchorUsable(centerFromFront) && isRailAnchorUsable(centerFromRear)) {
            if (centerFromFront.map() == centerFromRear.map() && centerFromFront.split() == centerFromRear.split()) {
                return new RailAnchor(
                    centerFromFront.map(),
                    centerFromFront.split(),
                    (centerFromFront.index() + centerFromRear.index()) * 0.5D,
                    bodyDirection
                );
            }
            RailSample frontSample = sampleRail(centerFromFront.map(), centerFromFront.split(), centerFromFront.index());
            RailSample rearSample = sampleRail(centerFromRear.map(), centerFromRear.split(), centerFromRear.index());
            Vec3 midpoint = position();
            double frontDist = new Vec3(frontSample.x, frontSample.y, frontSample.z).distanceToSqr(midpoint);
            double rearDist = new Vec3(rearSample.x, rearSample.y, rearSample.z).distanceToSqr(midpoint);
            return frontDist <= rearDist ? centerFromFront : centerFromRear;
        }
        if (isRailAnchorUsable(centerFromFront)) {
            return centerFromFront;
        }
        return centerFromRear;
    }

    private float applyPoseFromBogieSamples(RailSample front, RailSample rear, float fallbackYaw, float fallbackPitch, boolean move) {
        double dx = front.x - rear.x;
        double dy = front.y - rear.y;
        double dz = front.z - rear.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = horizontal > 1.0E-4D
            ? (float) Math.toDegrees(Math.atan2(dx, dz))
            : fallbackYaw;
        // 前後台車の微小なY差(分岐マップの縦ベジェのわずかな膨らみ等)で小さなピッチが付き、本体が
        // 跳ねて見える。Y差が小さいうち(0.15ブロック未満)はピッチに反映しない(デッドゾーン)。実際の
        // 勾配はこれより大きなY差になるので従来通り正確に追従する。
        double pitchDy = Math.abs(dy) < 0.15D ? 0.0D : dy;
        float pitch = horizontal > 1.0E-4D
            ? (float) Math.toDegrees(Math.atan2(pitchDy, horizontal))
            : fallbackPitch;
        // 分岐境界などで前後台車のY差が急変したとき、本体ピッチが瞬間的に振れて跳ねるのを抑える。
        // 1tickのピッチ変化量を制限し急なジョルトだけ平滑化する(通常走行・カーブ・坂は無影響)。
        if (move) {
            float prevPitch = getXRot();
            float maxPitchDelta = 6.0F;
            pitch = Mth.clamp(pitch, prevPitch - maxPitchDelta, prevPitch + maxPitchDelta);
        }
        yaw = keepNearestYaw(yaw, getYRot());
        RailSample centerSample = resolveBodyCenterSample(front, rear);
        Vec3 center = new Vec3(centerSample.x, centerSample.y + TRAIN_BODY_HEIGHT_OFFSET, centerSample.z);
        if (move) {
            moveTo(center.x, center.y, center.z, yaw, pitch);
        } else {
            setPos(center.x, center.y, center.z);
        }
        setYRot(yaw);
        setXRot(pitch);
        yRotO = yaw;
        xRotO = pitch;
        setYHeadRot(yaw);
        setYBodyRot(yaw);
        return yaw;
    }

    private float computeBodyRoll(RailSample front, RailSample rear) {
        if (isRailAnchorUsable(frontRailAnchor) && isRailAnchorUsable(rearRailAnchor)) {
            float frontRoll = sampleRailRoll(frontRailAnchor.map(), frontRailAnchor.split(), frontRailAnchor.index());
            float rearRoll = sampleRailRoll(rearRailAnchor.map(), rearRailAnchor.split(), rearRailAnchor.index());
            return (frontRoll + rearRoll) * 0.5F;
        }
        if (activeRailMap != null && activeRailSplit > 0) {
            return sampleRailRoll(activeRailMap, activeRailSplit, activeRailPosition);
        }
        return 0.0F;
    }

    private Vec3 applyBodyCenterOffset(RailSample centerSample, float yaw, float roll) {
        double x = centerSample.x;
        double y = centerSample.y;
        double z = centerSample.z;
        double rollRad = Math.toRadians(-roll);
        double offsetX = Math.sin(rollRad) * RTM_VEHICLE_Y_OFFSET;
        double offsetY = Math.cos(rollRad) * RTM_VEHICLE_Y_OFFSET - RTM_VEHICLE_Y_OFFSET;
        double yawRad = Math.toRadians(yaw);
        double rotatedX = Math.sin(yawRad) * offsetX;
        double rotatedZ = Math.cos(yawRad) * offsetX;
        return new Vec3(
            x + rotatedX,
            y + offsetY,
            z + rotatedZ
        );
    }

    private RailSample resolveBodyCenterSample(RailSample front, RailSample rear) {
        double[] bogieZ = getBogieRailOffsets();
        double frontZ = bogieZ[1];
        double rearZ = bogieZ[0];
        double span = Math.abs(frontZ - rearZ);
        if (span > 1.0E-4D) {
            double frontWeight = Math.abs(rearZ) / span;
            double rearWeight = Math.abs(frontZ) / span;
            return new RailSample(
                front.x * frontWeight + rear.x * rearWeight,
                (front.y + rear.y) * 0.5D,
                front.z * frontWeight + rear.z * rearWeight
            );
        }
        return new RailSample(
            (front.x + rear.x) * 0.5D,
            (front.y + rear.y) * 0.5D,
            (front.z + rear.z) * 0.5D
        );
    }

    private float getRailYawForBody(RailMap map, int split, double index, int bodyDirection, float baseYaw) {
        if (map == null || split <= 0) {
            return baseYaw;
        }
        float yaw = sampleRailYaw(map, split, index);
        if (bodyDirection < 0) {
            yaw = Mth.wrapDegrees(yaw + 180.0F);
        }
        return keepNearestYaw(yaw, baseYaw);
    }

    private float getRailPitchForBody(RailMap map, int split, double index, int bodyDirection) {
        if (map == null || split <= 0) {
            return getXRot();
        }
        float pitch = sampleRailPitch(map, split, index);
        return bodyDirection < 0 ? -pitch : pitch;
    }

    private RailAnchorPair findBestAnchorPairForCenter(RailMap map, int split, double centerIndex, RailSample requestedCenter, int bodyDirection) {
        double[] bogieZ = getBogieRailOffsets();
        double trackLen = Math.max(0.001D, map.getLength());
        double sampleStep = trackLen / split;
        double frontOffset = bogieZ[1] / sampleStep;
        double rearOffset = bogieZ[0] / sampleStep;
        double minCenter = Math.max(0.0D, -Math.min(frontOffset, rearOffset));
        double maxCenter = Math.min(split, split - Math.max(frontOffset, rearOffset));
        boolean hasFullBogieRange = minCenter <= maxCenter;
        double spanSamples = Math.abs(bogieZ[1] - bogieZ[0]) / sampleStep;
        int searchRadius = Mth.clamp((int) Math.ceil(spanSamples * 0.35D), 4, 48);
        RailAnchorPair best = null;

        for (int offset = -searchRadius; offset <= searchRadius; offset++) {
            double candidateCenter = hasFullBogieRange
                ? Mth.clamp(centerIndex + offset, minCenter, maxCenter)
                : Mth.clamp(centerIndex + offset, 0.0D, split);
            RailAnchor centerAnchor = new RailAnchor(map, split, candidateCenter, bodyDirection);
            RailAnchor frontAnchor = advanceAnchorAlongPath(centerAnchor, bogieZ[1]);
            RailAnchor rearAnchor = advanceAnchorAlongPath(centerAnchor, bogieZ[0]);
            if (!isRailAnchorUsable(frontAnchor) || !isRailAnchorUsable(rearAnchor)) {
                continue;
            }
            RailSample front = sampleBogieRail(frontAnchor.map(), frontAnchor.split(), frontAnchor.index());
            // 後台車を前台車からの直線距離=台車間隔に補正(本家RTM準拠・弦-弧ズレ解消)。
            rearAnchor = refineAnchorByStraightDistance(rearAnchor, front, Math.abs(bogieZ[1] - bogieZ[0]));
            RailSample rear = sampleBogieRail(rearAnchor.map(), rearAnchor.split(), rearAnchor.index());
            double centerX = (front.x + rear.x) * 0.5D;
            double centerY = (front.y + rear.y) * 0.5D;
            double centerZ = (front.z + rear.z) * 0.5D;
            double dx = centerX - requestedCenter.x;
            double dz = centerZ - requestedCenter.z;
            double distanceSq = dx * dx + dz * dz;
            if (best == null || distanceSq < best.distanceSq()) {
                best = new RailAnchorPair(
                    frontAnchor,
                    rearAnchor,
                    front,
                    rear,
                    distanceSq
                );
            }
        }

        if (best != null) {
            return best;
        }
        double fallbackCenter = hasFullBogieRange ? Mth.clamp(centerIndex, minCenter, maxCenter) : Mth.clamp(centerIndex, 0.0D, split);
        RailAnchor fallbackAnchor = new RailAnchor(map, split, fallbackCenter, bodyDirection);
        RailAnchor frontAnchor = advanceAnchorAlongPath(fallbackAnchor, bogieZ[1]);
        RailAnchor rearAnchor = advanceAnchorAlongPath(fallbackAnchor, bogieZ[0]);
        return new RailAnchorPair(
            frontAnchor,
            rearAnchor,
            sampleBogieRail(frontAnchor.map(), frontAnchor.split(), frontAnchor.index()),
            sampleBogieRail(rearAnchor.map(), rearAnchor.split(), rearAnchor.index()),
            0.0D
        );
    }

    private RailAnchor normalizeAnchorOrientation(RailAnchor anchor, int bodyDirection) {
        if (anchor == null || anchor.map() == null || anchor.split() <= 0) {
            return anchor;
        }
        int normalized = bodyDirection == 0 ? 1 : bodyDirection;
        return new RailAnchor(anchor.map(), anchor.split(), anchor.index(), normalized);
    }


    private RailAnchor advanceAnchorAlongPath(RailAnchor anchor, double offsetMeters) {
        if (anchor == null || anchor.map() == null || anchor.split() <= 0) {
            return null;
        }
        RailMap map = anchor.map();
        int split = anchor.split();
        double index = Mth.clamp(anchor.index(), 0.0D, split);
        int travelDirection = anchor.travelDirection() == 0 ? 1 : anchor.travelDirection();
        double remaining = Math.abs(offsetMeters);
        if (offsetMeters < 0.0D) {
            travelDirection *= -1;
        }

        int guard = 0;
        while (remaining > 1.0E-5D && guard++ < 8) {
            double step = Math.max(0.001D, map.getLength()) / split;
            double samplesToBoundary = travelDirection > 0 ? split - index : index;
            double metersToBoundary = samplesToBoundary * step;
            if (remaining <= metersToBoundary) {
                index += travelDirection * (remaining / step);
                remaining = 0.0D;
                break;
            }

            remaining -= Math.max(0.0D, metersToBoundary);
            int boundaryIndex = travelDirection > 0 ? split : 0;
            RailConnection next = findConnectedRailContext(map, split, boundaryIndex, travelDirection);
            if (next == null) {
                RailFollowContext snapped = findRailContextBeyondBoundary(map, split, boundaryIndex, travelDirection);
                if (snapped == null) {
                    RailSample bSample = sampleRail(map, split, boundaryIndex);
                    index = boundaryIndex;
                    break;
                }
                map = snapped.map();
                split = snapped.split();
                index = snapped.nearestIndex();
                travelDirection = getBodyDirectionOnRail(map, split, (int) Math.round(index), getYRot()) * (travelDirection > 0 ? 1 : -1);
                continue;
            }
            map = next.map();
            split = next.split();
            index = next.index();
            travelDirection = next.travelDirection() == 0 ? travelDirection : next.travelDirection();
        }

        return new RailAnchor(map, split, Mth.clamp(index, 0.0D, split), travelDirection);
    }

    private RailAnchor findBestFollowerAnchor(
        RailAnchor predictedAnchor,
        RailSample leadingSample,
        double targetDistanceSq,
        int bodyDirection,
        float referenceYaw,
        double distanceMeters,
        int followerSide
    ) {
        if (!isRailAnchorUsable(predictedAnchor) || leadingSample == null || predictedAnchor.split() <= 0) {
            return null;
        }

        RailMap map = predictedAnchor.map();
        int split = predictedAnchor.split();
        double predictedIndex = Mth.clamp(predictedAnchor.index(), 0.0D, split);
        double searchCenter = predictedIndex;
        if (followerSide >= 0 && followerSide < 2
            && bogiePrevMaps[followerSide] == map
            && bogiePrevSplits[followerSide] == split
            && bogiePrevSampleIndex[followerSide] >= 0) {
            searchCenter = Mth.clamp((double) bogiePrevSampleIndex[followerSide], 0.0D, split);
        }
        double targetDistanceMeters = Math.sqrt(Math.max(targetDistanceSq, 0.0D));
        int indexInc = Math.max(
            48,
            (int) (Math.max(Math.abs(distanceMeters) + 0.25D, targetDistanceMeters * 0.50D) * (double) BOGIE_SPLITS_PER_METER)
        );
        indexInc = Math.min(indexInc, Math.max(16, split));
        int indexMin = Math.max((int) Math.floor(searchCenter) - indexInc, 0);
        int indexMax = Math.min((int) Math.ceil(searchCenter) + indexInc, split);
        RailAnchor best = null;
        double bestScore = Double.MAX_VALUE;

        for (int i = indexMin; i <= indexMax; i++) {
            RailResolvedSample resolved = resolveRailSample(map, split, i, bodyDirection);
            RailSample sample = resolved.sample();
            double dx = leadingSample.x - sample.x;
            double dy = leadingSample.y - sample.y;
            double dz = leadingSample.z - sample.z;
            double distSq = dx * dx + dy * dy + dz * dz;
            double score = Math.abs(distSq - targetDistanceSq)
                + Math.abs(i - searchCenter) * 0.001D
                + (resolved.map() == map ? 0.0D : 0.08D);
            if (best == null || score < bestScore) {
                bestScore = score;
                best = new RailAnchor(resolved.map(), resolved.split(), resolved.index(), resolved.bodyDirection());
            }
        }

        return best;
    }

    private double distanceToRailMeters(RailSample a, RailAnchor b) {
        if (a == null || b == null || !isRailAnchorUsable(b)) {
            return 0.0D;
        }
        RailSample sample = sampleRail(b.map(), b.split(), b.index());
        double dx = a.x - sample.x;
        double dy = a.y - sample.y;
        double dz = a.z - sample.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private int getMovementSplitForMap(RailMap map) {
        if (map == null) {
            return 0;
        }
        int split = (int) Math.ceil(map.getLength() * (double) BOGIE_SPLITS_PER_METER);
        split = Math.max(split, 2);
        return Math.min(split, 32768);
    }

    private boolean ensureBogieAnchors() {
        double[] bogieZ = getBogieRailOffsets();
        if (isRailAnchorUsable(frontRailAnchor) && isRailAnchorUsable(rearRailAnchor)) {
            return true;
        }

        RailFollowContext center = getActiveRailContext();
        if (center == null) {
            RailFollowContext front = findRailContextNearAny(localToWorld(new Vec3(0.0D, 0.0D, bogieZ[1])), null);
            RailFollowContext rear = findRailContextNearAny(localToWorld(new Vec3(0.0D, 0.0D, bogieZ[0])), null);
            if (front == null && rear == null) {
                return false;
            }
            RailFollowContext seed = front != null ? front : rear;
            activeRailMap = seed.map();
            activeRailSplit = getMovementSplitForMap(seed.map());
            activeRailIndex = seed.nearestIndex();
            activeRailPosition = seed.nearestIndex();
            center = seed;
        }
        int bodyDirection = getBodyDirectionOnRail(center.map(), center.split(), center.nearestIndex(), getYRot());
        RailAnchorPair pair = createAnchorPairFromCenter(center.map(), center.split(), center.nearestIndex(), bodyDirection);
        if (!isRailAnchorUsable(pair.front()) || !isRailAnchorUsable(pair.rear())) {
            return false;
        }
        frontRailAnchor = pair.front();
        rearRailAnchor = pair.rear();
        updateStoredBogieState();
        activeRailMap = center.map();
        activeRailSplit = getMovementSplitForMap(center.map());
        activeRailIndex = center.nearestIndex();
        activeRailPosition = center.nearestIndex();
        activeRailBodyDirection = bodyDirection;
        return true;
    }

    private boolean isRailAnchorUsable(RailAnchor anchor) {
        return anchor != null && anchor.map() != null && anchor.split() > 0;
    }

    private RailAnchor advanceBogieAnchor(RailAnchor anchor, double distanceMeters, int pathDirection) {
        if (anchor == null || anchor.map() == null || anchor.split() <= 0) {
            return null;
        }
        if (distanceMeters <= 0.0D) {
            return anchor;
        }

        RailMap map = anchor.map();
        int split = anchor.split();
        double index = Mth.clamp(anchor.index(), 0.0D, split);
        int travelDirection = pathDirection != 0
            ? pathDirection
            : (anchor.travelDirection() == 0 ? 1 : anchor.travelDirection());
        double remaining = distanceMeters;
        int guard = 0;

        while (remaining > 1.0E-5D && guard++ < 8) {
            double step = Math.max(0.001D, map.getLength()) / split;
            double samplesToBoundary = travelDirection > 0 ? split - index : index;
            double metersToBoundary = samplesToBoundary * step;
            if (remaining <= metersToBoundary) {
                index += travelDirection * (remaining / step);
                remaining = 0.0D;
                break;
            }

            remaining -= Math.max(0.0D, metersToBoundary);
            int boundaryIndex = travelDirection > 0 ? split : 0;
            RailConnection next = findConnectedRailContext(map, split, boundaryIndex, travelDirection);
            if (next == null) {
                RailFollowContext snapped = findRailContextBeyondBoundary(map, split, boundaryIndex, travelDirection);
                if (snapped == null) {
                    return null;
                }
                map = snapped.map();
                split = snapped.split();
                index = snapped.nearestIndex();
                travelDirection = pathDirection != 0 ? pathDirection : travelDirection;
                continue;
            }

            map = next.map();
            split = next.split();
            index = next.index();
            travelDirection = next.travelDirection();
        }

        if (index < -0.001D || index > split + 0.001D) {
            return null;
        }
        return new RailAnchor(map, split, Mth.clamp(index, 0.0D, split), travelDirection);
    }

    private float keepNearestYaw(float targetYaw, float currentYaw) {
        float diff = Mth.wrapDegrees(targetYaw - currentYaw);
        if (Math.abs(diff) > 120.0F) {
            targetYaw = Mth.wrapDegrees(targetYaw + 180.0F);
        }
        return targetYaw;
    }

    private RailResolvedSample resolveRailSample(RailMap map, int split, double index, int bodyDirection) {
        if (index >= 0.0D && index <= split) {
            return new RailResolvedSample(sampleRail(map, split, index), map, split, index, bodyDirection);
        }

        int direction = index > split ? 1 : -1;
        int boundaryIndex = direction > 0 ? split : 0;
        RailConnection next = findConnectedRailContext(map, split, boundaryIndex, direction);
        if (next == null) {
            double clamped = Mth.clamp(index, 0.0D, split);
            return new RailResolvedSample(sampleRail(map, split, clamped), map, split, clamped, bodyDirection);
        }

        int nextBodyDirection = chooseStableBodyDirection(next.map(), next.split(), (int) Math.round(next.index()), getYRot(), bodyDirection);
        double overflow = Math.abs(index < 0.0D ? index : index - split);
        double nextIndex = next.index() + next.travelDirection() * overflow;
        nextIndex = Mth.clamp(nextIndex, 0.0D, next.split());
        return new RailResolvedSample(sampleRail(next.map(), next.split(), nextIndex), next.map(), next.split(), nextIndex, nextBodyDirection);
    }

    private RailConnection findConnectedRailContext(RailMap currentMap, int currentSplit, int boundaryIndex, int travelDirection) {
        if (currentMap == null || currentSplit <= 0) {
            return null;
        }

        RailSample boundary = sampleRail(currentMap, currentSplit, boundaryIndex);
        Vec3 boundaryPos = new Vec3(boundary.x, boundary.y, boundary.z);
        float outgoingYaw = currentMap.getRailYaw(currentSplit, Mth.clamp(boundaryIndex, 0, currentSplit));
        if (travelDirection < 0) {
            outgoingYaw = Mth.wrapDegrees(outgoingYaw + 180.0F);
        }

        jp.ngt.rtm.rail.util.RailPosition endpoint =
            boundaryIndex <= 0 ? currentMap.getStartRP() : currentMap.getEndRP();

        BlockPos center = BlockPos.containing(boundary.x, boundary.y, boundary.z);
        int radius = 24;
        // 1st passはアクティブ分岐のみ、無ければ全分岐へフォールバック
        RailConnection best = null;
        for (int pass = 0; pass < 2 && best == null; pass++) {
            railLookupIncludeAllSegments = pass == 1;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dy = -4; dy <= 4; dy++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        RailMap[] maps = getConnectionCandidateMapsAt(center.offset(dx, dy, dz), currentMap);
                        if (maps.length == 0) continue;

                        for (RailMap map : maps) {
                            if (map == null || map == currentMap || map.equals(currentMap)) {
                                continue;
                            }
                            int split = getMovementSplitForMap(map);
                            best = betterConnection(best, evaluateRailEndpointConnection(map, split, 0, endpoint, boundaryPos, outgoingYaw));
                            best = betterConnection(best, evaluateRailEndpointConnection(map, split, split, endpoint, boundaryPos, outgoingYaw));
                        }
                    }
                }
            }
        }
        railLookupIncludeAllSegments = false;
        return best;
    }

    private RailConnection evaluateRailEndpointConnection(
        RailMap map,
        int split,
        int endpointIndex,
        jp.ngt.rtm.rail.util.RailPosition currentEndpoint,
        Vec3 boundaryPos,
        float outgoingYaw
    ) {
        jp.ngt.rtm.rail.util.RailPosition candidateEndpoint =
            endpointIndex <= 0 ? map.getStartRP() : map.getEndRP();
        boolean sameEndpoint = candidateEndpoint != null && sameRailEndpoint(candidateEndpoint, currentEndpoint);
        RailSample sample = sampleRail(map, split, endpointIndex);
        double distSq = new Vec3(sample.x, sample.y, sample.z).distanceToSqr(boundaryPos);
        if (!sameEndpoint && distSq > RAIL_CONNECTION_MAX_DISTANCE_SQ) {
            return null;
        }

        int nextTravelDirection = endpointIndex <= 0 ? 1 : -1;
        float candidateYaw = map.getRailYaw(split, endpointIndex);
        if (nextTravelDirection < 0) {
            candidateYaw = Mth.wrapDegrees(candidateYaw + 180.0F);
        }
        float yawDiff = Math.abs(Mth.wrapDegrees(outgoingYaw - candidateYaw));
        // 端点共有でも逆走(yaw差>90°)の接続は拒否する
        boolean reversal = yawDiff > 90.0F;
        if ((!sameEndpoint && yawDiff > RAIL_CONNECTION_MAX_YAW_DIFF) || reversal) {
            return null;
        }
        double score = distSq + yawDiff * 0.02D + (sameEndpoint ? -200.0D : 0.0D);
        return new RailConnection(map, split, endpointIndex, nextTravelDirection, score);
    }

    private RailConnection betterConnection(RailConnection current, RailConnection candidate) {
        if (candidate == null) {
            return current;
        }
        if (current == null || candidate.score() < current.score()) {
            return candidate;
        }
        return current;
    }

    /** true の間はスイッチの全分岐をレール探索候補にする(アクティブ分岐で見つからない時のフォールバック)。 */
    private boolean railLookupIncludeAllSegments = false;

    private RailMap[] switchCandidateMaps(LargeRailCoreBlockEntity core, RailMap currentMap) {
        if (railLookupIncludeAllSegments || shouldInspectAllSegments(core, currentMap)) {
            return core.getAllRailMaps();
        }
        return core.getActiveRailMaps();
    }

    private RailMap[] getConnectionCandidateMapsAt(BlockPos pos, RailMap currentMap) {
        BlockEntity blockEntity = level().getBlockEntity(pos);
        if (blockEntity instanceof LargeRailCoreBlockEntity core && core.isLoaded()) {
            return switchCandidateMaps(core, currentMap);
        }
        if (blockEntity instanceof RailCollisionBlockEntity collision) {
            BlockPos corePos = collision.getCorePos();
            if (corePos != null && level().getBlockEntity(corePos) instanceof LargeRailCoreBlockEntity core && core.isLoaded()) {
                return switchCandidateMaps(core, currentMap);
            }
        }
        // jp.ngt.rtm.rail (Phase 1 本家忠実システム): ベース/コアどちらでもコア経由で全 RailMap を返す
        if (blockEntity instanceof jp.ngt.rtm.rail.TileEntityLargeRailBase railBase) {
            jp.ngt.rtm.rail.TileEntityLargeRailCore core = railBase.getRailCore();
            if (core != null && core.isLoaded()) {
                RailMap[] maps = core.getAllRailMaps();
                if (maps != null) {
                    return maps;
                }
            }
        }
        return new RailMap[0];
    }

    private boolean shouldInspectAllSegments(LargeRailCoreBlockEntity core, RailMap currentMap) {
        if (core == null || currentMap == null) {
            return false;
        }
        for (RailMap map : core.getAllRailMaps()) {
            if (sameRailShape(map, currentMap)) {
                return true;
            }
        }
        return false;
    }

    private boolean sameRailShape(RailMap a, RailMap b) {
        if (a == null || b == null) {
            return false;
        }
        return sameRailEndpoint(a.getStartRP(), b.getStartRP()) && sameRailEndpoint(a.getEndRP(), b.getEndRP())
            || sameRailEndpoint(a.getStartRP(), b.getEndRP()) && sameRailEndpoint(a.getEndRP(), b.getStartRP());
    }

    private boolean sameRailEndpoint(RailPosition a, RailPosition b) {
        if (a == null || b == null) {
            return false;
        }
        return a.blockX == b.blockX && a.blockY == b.blockY && a.blockZ == b.blockZ;
    }

    private boolean transitionToConnectedRail(RailMap currentMap, int currentSplit, int direction) {
        int boundaryIndex = direction > 0 ? currentSplit : 0;
        RailSample boundary = sampleRail(currentMap, currentSplit, boundaryIndex);
        RailFollowContext next = findRailContextNear(new Vec3(boundary.x, boundary.y, boundary.z), currentMap);
        if (next == null) {
            return false;
        }

        activeRailMap = next.map();
        activeRailSplit = next.split();
        activeRailIndex = next.nearestIndex();
        activeRailPosition = activeRailIndex;
        activeRailBodyDirection = chooseStableBodyDirection(next.map(), next.split(), next.nearestIndex(), getYRot(), activeRailBodyDirection);
        activeRailDirection = next.nearestIndex() <= Math.max(4, next.split() / 8) ? 1 : -1;
        setRailProgress(activeRailIndex / (float) activeRailSplit);
        return true;
    }

    private int getBodyDirectionOnRail(RailMap map, int split, int index, float bodyYaw) {
        if (map == null || split <= 0) {
            return activeRailDirection == 0 ? 1 : activeRailDirection;
        }
        int clamped = Mth.clamp(index, 0, split);
        float yawAtRail = map.getRailYaw(split, clamped);
        return Math.abs(Mth.wrapDegrees(bodyYaw - yawAtRail)) <= 90.0F ? 1 : -1;
    }

    private int chooseStableBodyDirection(RailMap map, int split, int index, float bodyYaw, int fallback) {
        if (map == null || split <= 0) {
            return fallback == 0 ? 1 : fallback;
        }
        int byYaw = getBodyDirectionOnRail(map, split, index, bodyYaw);
        if (fallback == 0) {
            return byYaw;
        }
        float railYaw = map.getRailYaw(split, Mth.clamp(index, 0, split));
        if (fallback < 0) {
            railYaw = Mth.wrapDegrees(railYaw + 180.0F);
        }
        float diff = Math.abs(Mth.wrapDegrees(bodyYaw - railYaw));
        return diff <= 120.0F ? fallback : byYaw;
    }

    private RailSample sampleRail(RailMap map, int split, int index) {
        return sampleRail(map, split, (double) index);
    }

    private RailSample sampleRail(RailMap map, int split, double index) {
        double clamped = Mth.clamp(index, 0.0D, split);
        int low = Mth.clamp((int) Math.floor(clamped), 0, split);
        int high = Mth.clamp((int) Math.ceil(clamped), 0, split);
        if (low == high) {
            double[] pos = map.getRailPos(split, low);
            return new RailSample(pos[1], map.getRailHeight(split, low) + RAIL_HEIGHT_OFFSET, pos[0]);
        }
        double t = clamped - low;
        double[] a = map.getRailPos(split, low);
        double[] b = map.getRailPos(split, high);
        double yA = map.getRailHeight(split, low) + RAIL_HEIGHT_OFFSET;
        double yB = map.getRailHeight(split, high) + RAIL_HEIGHT_OFFSET;
        return new RailSample(
            Mth.lerp(t, a[1], b[1]),
            Mth.lerp(t, yA, yB),
            Mth.lerp(t, a[0], b[0])
        );
    }

    private RailSample sampleBogieRail(RailMap map, int split, double index) {
        double clamped = Mth.clamp(index, 0.0D, split);
        int low = Mth.clamp((int) Math.floor(clamped), 0, split);
        int high = Mth.clamp((int) Math.ceil(clamped), 0, split);
        if (low == high) {
            double[] pos = map.getRailPos(split, low);
            return new RailSample(pos[1], map.getRailHeight(split, low) + BOGIE_HITBOX_HEIGHT_OFFSET, pos[0]);
        }
        double t = clamped - low;
        double[] a = map.getRailPos(split, low);
        double[] b = map.getRailPos(split, high);
        double yA = map.getRailHeight(split, low) + BOGIE_HITBOX_HEIGHT_OFFSET;
        double yB = map.getRailHeight(split, high) + BOGIE_HITBOX_HEIGHT_OFFSET;
        return new RailSample(
            Mth.lerp(t, a[1], b[1]),
            Mth.lerp(t, yA, yB),
            Mth.lerp(t, a[0], b[0])
        );
    }

    private RailSample sampleRailIndex(RailMap map, int split, int index) {
        int clamped = Mth.clamp(index, 0, split);
        double[] pos = map.getRailPos(split, clamped);
        return new RailSample(pos[1], map.getRailHeight(split, clamped) + RAIL_HEIGHT_OFFSET, pos[0]);
    }

    private float sampleRailYaw(RailMap map, int split, double index) {
        if (map == null || split <= 0) {
            return getYRot();
        }
        double clamped = Mth.clamp(index, 0.0D, split);
        int low = Mth.clamp((int) Math.floor(clamped), 0, split);
        int high = Mth.clamp((int) Math.ceil(clamped), 0, split);
        if (low == high) {
            return map.getRailYaw(split, low);
        }
        float yawLow = map.getRailYaw(split, low);
        float yawHigh = keepNearestYaw(map.getRailYaw(split, high), yawLow);
        return Mth.lerp((float) (clamped - low), yawLow, yawHigh);
    }

    private float sampleRailPitch(RailMap map, int split, double index) {
        if (map == null || split <= 0) {
            return getXRot();
        }
        double clamped = Mth.clamp(index, 0.0D, split);
        int low = Mth.clamp((int) Math.floor(clamped), 0, split);
        int high = Mth.clamp((int) Math.ceil(clamped), 0, split);
        if (low == high) {
            return map.getRailPitch(split, low);
        }
        float pitchLow = map.getRailPitch(split, low);
        float pitchHigh = map.getRailPitch(split, high);
        return Mth.lerp((float) (clamped - low), pitchLow, pitchHigh);
    }

    private float sampleRailRoll(RailMap map, int split, double index) {
        if (map == null || split <= 0) {
            return 0.0F;
        }
        double clamped = Mth.clamp(index, 0.0D, split);
        int low = Mth.clamp((int) Math.floor(clamped), 0, split);
        int high = Mth.clamp((int) Math.ceil(clamped), 0, split);
        if (low == high) {
            return map.getRailRoll(split, low);
        }
        float rollLow = map.getRailRoll(split, low);
        float rollHigh = map.getRailRoll(split, high);
        return Mth.lerp((float) (clamped - low), rollLow, rollHigh);
    }

    private double[] getBogieRailOffsets() {
        VehicleDefinition def = VehicleRegistry.getById(getVehicleId());
        if (def == null || def.getBogies().isEmpty()) {
            double distance = Math.max(2.0D, getTrainDistance() * 0.7D);
            return new double[]{-distance, distance};
        }
        double rear = Double.POSITIVE_INFINITY;
        double front = Double.NEGATIVE_INFINITY;
        for (VehicleDefinition.BogieDefinition bogie : def.getBogies()) {
            rear = Math.min(rear, bogie.position().z);
            front = Math.max(front, bogie.position().z);
        }
        if (!Double.isFinite(rear) || !Double.isFinite(front) || Math.abs(front - rear) < 0.5D) {
            double distance = Math.max(2.0D, getTrainDistance() * 0.7D);
            return new double[]{-distance, distance};
        }
        double midpoint = (front + rear) * 0.5D;
        double halfSpan = Math.abs(front - rear) * 0.5D;
        if (Math.abs(midpoint) > Math.max(0.75D, halfSpan * 0.35D) && shouldCenterAsymmetricBogieAnchors(def)) {
            return new double[]{-halfSpan, halfSpan};
        }
        return new double[]{rear, front};
    }

    private static boolean shouldCenterAsymmetricBogieAnchors(VehicleDefinition def) {
        if (def == null) {
            return false;
        }
        String id = def.getId() == null ? "" : def.getId().toLowerCase(java.util.Locale.ROOT);
        String model = def.getModelFile() == null ? "" : def.getModelFile().toLowerCase(java.util.Locale.ROOT);
        boolean classBogie = false;
        for (VehicleDefinition.BogieDefinition bogie : def.getBogies()) {
            String bogieModel = bogie.modelFile() == null ? "" : bogie.modelFile().toLowerCase(java.util.Locale.ROOT);
            if (bogieModel.endsWith(".class")) {
                classBogie = true;
                break;
            }
        }
        return classBogie && (id.contains("tkmtp") || id.contains("c56") || model.contains("tkmtp") || model.contains("c56"));
    }

    private int[] getExtremeBogieIndices(VehicleDefinition def) {
        if (def == null || def.getBogies().isEmpty()) {
            return new int[]{0, 1};
        }
        int rearIndex = 0;
        int frontIndex = 0;
        double rearZ = Double.POSITIVE_INFINITY;
        double frontZ = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < def.getBogies().size(); i++) {
            double z = def.getBogies().get(i).position().z;
            if (z < rearZ) {
                rearZ = z;
                rearIndex = i;
            }
            if (z > frontZ) {
                frontZ = z;
                frontIndex = i;
            }
        }
        return new int[]{rearIndex, frontIndex};
    }

    public float getBogieYawOffset(VehicleDefinition.BogieDefinition bogie) {
        return getBogieYawOffset(bogie, getYRot());
    }

    public float getBogieYawOffset(int bogieIndex, VehicleDefinition.BogieDefinition bogie, float baseYaw) {
        return getBogieYawOffset(bogieIndex, bogie, baseYaw, 1.0F);
    }

    public float getBogieYawOffset(int bogieIndex, VehicleDefinition.BogieDefinition bogie, float baseYaw, float partialTicks) {
        RailAnchor anchor = resolveRenderAnchorForBogie(bogieIndex);
        if (isRailAnchorUsable(anchor)) {
            return relativeBogieYaw(getAnchorRailYaw(anchor, baseYaw), baseYaw);
        }
        // クライアント: tick記録した端台車ヨーを partialTicks で補間(滑らか＋遅延なし=RTM同等)。
        if (level().isClientSide()) {
            int side = resolveExtremeSideForBogieIndex(bogieIndex);
            if (side >= 0 && side < 2 && !Float.isNaN(clientBogieYawCurr[side])) {
                float prev = Float.isNaN(clientBogieYawPrev[side]) ? clientBogieYawCurr[side] : clientBogieYawPrev[side];
                float interp = Mth.rotLerp(Mth.clamp(partialTicks, 0.0F, 1.0F), prev, clientBogieYawCurr[side]);
                return relativeBogieYaw(interp, baseYaw);
            }
        }
        // フォールバック: 台車位置のレール接線を直接計算(ラグ無し)。スクリプト車両と同じ経路。
        float railYaw = computeClientBogieRailYaw(bogieIndex);
        if (!Float.isNaN(railYaw)) {
            return relativeBogieYaw(railYaw, baseYaw);
        }
        return getBogieYawOffset(bogie, baseYaw);
    }

    public float getBogieYawOffset(VehicleDefinition.BogieDefinition bogie, float baseYaw) {
        if (bogie == null) {
            return 0.0F;
        }
        RailAnchor anchor = getNearestAnchorForBogie(bogie);
        if (isRailAnchorUsable(anchor)) {
            return relativeBogieYaw(getAnchorRailYaw(anchor, baseYaw), baseYaw);
        }
        if (activeRailMap != null && activeRailIndex >= 0 && activeRailSplit > 0) {
            double trackLen = Math.max(0.001D, activeRailMap.getLength());
            double sampleStep = trackLen / activeRailSplit;
            int bodyDirection = activeRailBodyDirection == 0 ? 1 : activeRailBodyDirection;
            double bogieIndex = activeRailPosition + bodyDirection * (bogie.position().z / sampleStep);
            RailResolvedSample resolved = resolveRailSample(activeRailMap, activeRailSplit, bogieIndex, bodyDirection);
            int clampedIndex = Mth.clamp((int) Math.round(resolved.index()), 0, resolved.split());
            float railYaw = resolved.map().getRailYaw(resolved.split(), clampedIndex);
            if (resolved.bodyDirection() < 0) {
                railYaw = Mth.wrapDegrees(railYaw + 180.0F);
            }
            return relativeBogieYaw(railYaw, baseYaw);
        }

        RailFollowContext context = findRailContextNearAny(localToWorld(bogie.position()), null);
        if (context == null) {
            return 0.0F;
        }
        int bodyDirection = getBodyDirectionOnRail(context.map(), context.split(), context.nearestIndex(), baseYaw);
        float railYaw = context.map().getRailYaw(context.split(), context.nearestIndex());
        if (bodyDirection < 0) {
            railYaw = Mth.wrapDegrees(railYaw + 180.0F);
        }
        return relativeBogieYaw(railYaw, baseYaw);
    }

    public Vec3 getBogieRenderOffset(int bogieIndex, VehicleDefinition.BogieDefinition bogie, float baseYaw, float partialTicks) {
        if (bogie == null) {
            return Vec3.ZERO;
        }
        VehicleDefinition def = VehicleRegistry.getById(getVehicleId());
        if (def == null) {
            return bogie.position();
        }
        // クライアントはサーバー同期の台車ワールド位置を partialTicks 補間で使う(本家RTMの台車補間相当)。
        boolean clientSynced = level().isClientSide() && entityData.get(BOGIE_SYNC_VALID);
        Vec3 railWorldY = clientSynced ? clientSyncedBogieWorld(bogieIndex, partialTicks) : getBogieWorldPosition(bogieIndex);
        if (railWorldY == null) {
            railWorldY = getBogieWorldPosition(bogieIndex);
        }
        // 台車高さは車体と同一基準+bogiePos.y。X/Zはレール位置へ追従
        RailAnchor anchor = getAnchorForRenderedBogie(bogieIndex);
        if (clientSynced || isRailAnchorUsable(anchor)) {
            double bodyRefY = railWorldY.y + (RAIL_HEIGHT_OFFSET - BOGIE_HITBOX_HEIGHT_OFFSET) + bogie.position().y + BOGIE_RENDER_LIFT;
            // 本体ポーズ(yaw/pitch/bank/modelOffset/scale)を厳密に逆変換して、カーブのバンクでも
            // 台車がレール上の正しい位置に描画されるようにする。
            Vec3 railLocal = worldToBogieLocalForRender(new Vec3(railWorldY.x, bodyRefY, railWorldY.z), partialTicks);
            return new Vec3(railLocal.x, railLocal.y, railLocal.z);
        }
        // レール非追従時(浮いている等)は本家 updatePosAndRotationClient と同様、
        // bogiePos をそのまま車体相対オフセットとして使う。
        return bogie.position();
    }

    public Vec3 getBogieRenderOffset(VehicleDefinition.BogieDefinition bogie, float baseYaw) {
        if (bogie == null) {
            return Vec3.ZERO;
        }
        VehicleDefinition def = VehicleRegistry.getById(getVehicleId());
        if (def == null) {
            return bogie.position();
        }
        int index = def.getBogies().indexOf(bogie);
        if (index < 0) {
            return bogie.position();
        }
        return getBogieRenderOffset(index, bogie, baseYaw, 1.0F);
    }

    public float getBogiePitch(int bogieIndex) {
        int side = resolveExtremeSideForBogieIndex(bogieIndex);
        RailAnchor anchor = side == 0 ? rearRailAnchor : frontRailAnchor;
        if (isRailAnchorUsable(anchor)) {
            return bogiePitchMemory[side];
        }
        return bogiePitchMemory[side];
    }

    public float getBogieRoll(int bogieIndex) {
        RailAnchor anchor = getAnchorForRenderedBogie(bogieIndex);
        if (isRailAnchorUsable(anchor)) {
            return sampleRailRoll(anchor.map(), anchor.split(), anchor.index());
        }
        return getBodyRoll();
    }

    private RailAnchor resolveRenderAnchorForBogie(int bogieIndex) {
        int side = resolveExtremeSideForBogieIndex(bogieIndex);
        return side == 0 ? rearRailAnchor : frontRailAnchor;
    }

    private int resolveExtremeSideForBogieIndex(int bogieIndex) {
        VehicleDefinition def = VehicleRegistry.getById(getVehicleId());
        if (def != null && !def.getBogies().isEmpty()) {
            int[] extremes = getExtremeBogieIndices(def);
            if (bogieIndex >= 0 && bogieIndex < def.getBogies().size()) {
                if (bogieIndex == extremes[0]) {
                    return 0;
                }
                if (bogieIndex == extremes[1]) {
                    return 1;
                }
            }
        }
        return Mth.clamp(bogieIndex, 0, 1);
    }

    public float getBogieWorldYaw(int bogieIndex) {
        int side = resolveExtremeSideForBogieIndex(bogieIndex);
        RailAnchor anchor = side == 0 ? rearRailAnchor : frontRailAnchor;
        if (isRailAnchorUsable(anchor)) {
            // サーバー: レールアンカーの接線。
            float reference = bogieYawMemory[side] == 0.0F ? getYRot() : bogieYawMemory[side];
            return sampleAnchorTangentYaw(anchor, reference);
        }
        // クライアント: アンカー/bogieYawMemory が無いので、台車位置のレール接線を直接求める。
        // これを返さないと getYRot(車体ヨー)になり、スクリプトの台車相対角が 0 → 台車が
        // 車体と一緒に回ってしまう(独立しない)。本家RTMは台車エンティティの接線ヨーを返す。
        float railYaw = computeClientBogieRailYaw(bogieIndex);
        if (!Float.isNaN(railYaw)) {
            return railYaw;
        }
        return bogieYawMemory[side] == 0.0F ? getYRot() : bogieYawMemory[side];
    }

    /** 台車のレール接線ワールドヨーを求める。無ければNaN。 */
    private float computeClientBogieRailYaw(int bogieIndex) {
        // 注意: 同期された台車エンティティの向き(clientBogieEntityYaw)はラグがあり、
        // 走行中に台車が一瞬古い向きを指して横ずれして見える。そこでクライアントでは
        // 台車取付位置のレール接線を毎フレーム直接計算する(ラグ無し・描画位置と一致)。
        int side = resolveExtremeSideForBogieIndex(bogieIndex);
        VehicleDefinition def = VehicleRegistry.getById(getVehicleId());
        if (def == null) {
            return Float.NaN;
        }
        // 同期された実台車ワールド位置でレール接線を求める(剛体マウントだと弦上=レールずれ位置で
        // 接線を取ってしまうため)。同期が無い時は従来どおり剛体マウント位置にフォールバック。
        Vec3 mount = getBogieWorldPosition(bogieIndex);
        RailMap map = clientBogieRailMap[side];
        // キャッシュ済みレールが取付位置から遠ければ(別レールへ移った)再探索。
        if (map == null || farFromRail(map, mount)) {
            RailFollowContext ctx = findRailContextNearAny(mount, null);
            if (ctx == null || ctx.map() == null) {
                return Float.NaN;
            }
            map = ctx.map();
            clientBogieRailMap[side] = map;
        }
        int split = getMovementSplitForMap(map);
        if (split <= 0) {
            return Float.NaN;
        }
        // サーバー(sampleAnchorTangentYaw)と同じ規約: 最近点の前後を少しずらしてサンプルし、
        // atan2(dx,dz) で接線ヨーを求める。getRailYaw より滑らかで規約も一致する。
        int nearest = Mth.clamp(map.getNearlestPoint(split, mount.x, mount.z), 0, split);
        double delta = Math.max(1.0D, split * 0.0060D);
        double beforeIndex = Mth.clamp(nearest - delta, 0.0D, split);
        double afterIndex = Mth.clamp(nearest + delta, 0.0D, split);
        RailSample before = sampleRail(map, split, beforeIndex);
        RailSample after = sampleRail(map, split, afterIndex);
        double dx = after.x - before.x;
        double dz = after.z - before.z;
        float tangentYaw = (dx * dx + dz * dz < 1.0E-6D)
            ? sampleRailYaw(map, split, nearest)
            : (float) Math.toDegrees(Math.atan2(dx, dz));
        // 本家RTM EntityBogie.fixBogieYaw と同じ: 車体向きと 90°以上離れていれば 180°反転。
        return fixBogieYaw(getYRot(), tangentYaw);
    }

    /** 本家RTM EntityBogie.fixBogieYaw 準拠: yaw2 を reference と同じ向き(90°以内)に整える。 */
    private static float fixBogieYaw(float reference, float yaw2) {
        float diff = Math.abs(Mth.wrapDegrees(reference - yaw2));
        return Mth.wrapDegrees(diff > 90.0F ? yaw2 + 180.0F : yaw2);
    }

    /** map 上で worldPos の最近点が 4 ブロック超離れているか(=別レールへ移った)。 */
    private boolean farFromRail(RailMap map, Vec3 worldPos) {
        int split = getMovementSplitForMap(map);
        if (split <= 0) {
            return true;
        }
        int nearest = Mth.clamp(map.getNearlestPoint(split, worldPos.x, worldPos.z), 0, split);
        double[] p = map.getRailPos(split, nearest);
        double dx = p[1] - worldPos.x;
        double dz = p[0] - worldPos.z;
        return dx * dx + dz * dz > 4.0D * 4.0D;
    }

    public Vec3 getBogieWorldPosition(int bogieIndex) {
        // クライアントは movement を走らせずレールも持たないため、サーバーが同期した端台車の
        // ワールド位置を使う(カーブで台車をレール上に正確に描く)。中間台車は前後で補間。
        if (level().isClientSide() && entityData.get(BOGIE_SYNC_VALID)) {
            // partialTicks 無しの呼び出し(ヨー計算等)は現tick値(pt=1)を使う。描画位置の補間は
            // getBogieRenderOffset 側が partialTicks 付きで clientSyncedBogieWorld を呼ぶ。
            Vec3 synced = clientSyncedBogieWorld(bogieIndex, 1.0F);
            if (synced != null) {
                return synced;
            }
        }
        RailAnchor anchor = getAnchorForRenderedBogie(bogieIndex);
        if (isRailAnchorUsable(anchor)) {
            RailSample sample = sampleBogieRail(anchor.map(), anchor.split(), anchor.index());
            return new Vec3(sample.x, sample.y, sample.z);
        }
        VehicleDefinition def = VehicleRegistry.getById(getVehicleId());
        Vec3 local = getBogieLocalPosition(bogieIndex, def);
        return localToWorld(local);
    }

    /** クライアント: 毎tick、同期された端台車オフセットを prev/curr へ取り込む(描画補間用)。 */
    private void updateClientBogieOffsetInterpolation() {
        Vec3 rear = new Vec3(entityData.get(REAR_BOGIE_DX), entityData.get(REAR_BOGIE_DY), entityData.get(REAR_BOGIE_DZ));
        Vec3 front = new Vec3(entityData.get(FRONT_BOGIE_DX), entityData.get(FRONT_BOGIE_DY), entityData.get(FRONT_BOGIE_DZ));
        if (!clientBogieOffInit) {
            clientRearBogieOffPrev = clientRearBogieOffCurr = rear;
            clientFrontBogieOffPrev = clientFrontBogieOffCurr = front;
            clientBogieOffInit = true;
            return;
        }
        clientRearBogieOffPrev = clientRearBogieOffCurr;
        clientFrontBogieOffPrev = clientFrontBogieOffCurr;
        clientRearBogieOffCurr = filterOffsetJump(0, clientRearBogieOffCurr, rear);
        clientFrontBogieOffCurr = filterOffsetJump(1, clientFrontBogieOffCurr, front);
        // 端台車のレール接線ヨーを tick 単位で記録(描画時に partialTicks 補間=滑らか＋遅延なし)。
        // ヨーはサーバーのアンカー接線を同期した値を使う(クライアント探索の誤方向/180°反転を回避)。
        VehicleDefinition def = VehicleRegistry.getById(getVehicleId());
        if (def != null && !def.getBogies().isEmpty()) {
            if (entityData.get(BOGIE_SYNC_VALID)) {
                updateTickBogieYaw(0, entityData.get(REAR_BOGIE_YAW));
                updateTickBogieYaw(1, entityData.get(FRONT_BOGIE_YAW));
            } else {
                int[] ext = getExtremeBogieIndices(def);
                updateTickBogieYaw(0, computeClientBogieRailYaw(ext[0]));
                updateTickBogieYaw(1, computeClientBogieRailYaw(ext[1]));
            }
        }
    }

    /** 継ぎ目での同期台車位置の大ジャンプ(>6ブロック/tick)だけ弾く。連続したら採用。 */
    private Vec3 filterOffsetJump(int side, Vec3 cur, Vec3 target) {
        if (cur == null || target == null) {
            return target;
        }
        if (target.distanceToSqr(cur) > 6.0D * 6.0D && clientBogieOffRejectCount[side] < 2) {
            clientBogieOffRejectCount[side]++;
            return cur;
        }
        clientBogieOffRejectCount[side] = 0;
        return target;
    }

    /** side(0=後/1=前)のレール接線ヨーを prev/curr へ記録(NaNは前回値維持、初回は即セット)。 */
    private void updateTickBogieYaw(int side, float target) {
        if (Float.isNaN(target)) {
            return;
        }
        if (Float.isNaN(clientBogieYawCurr[side])) {
            clientBogieYawPrev[side] = clientBogieYawCurr[side] = target;
            clientBogieYawRejectCount[side] = 0;
            return;
        }
        // 明らかな誤値(約180°反転等)だけ弾く。しきい値100°。連続したら採用
        if (Math.abs(Mth.wrapDegrees(target - clientBogieYawCurr[side])) > 100.0F
            && clientBogieYawRejectCount[side] < 2) {
            clientBogieYawRejectCount[side]++;
            return;
        }
        clientBogieYawRejectCount[side] = 0;
        clientBogieYawPrev[side] = clientBogieYawCurr[side];
        clientBogieYawCurr[side] = target;
    }

    /** 同期された端台車位置から指定台車のワールド位置を返す(partialTicks補間)。 */
    private Vec3 clientSyncedBogieWorld(int bogieIndex, float partialTicks) {
        VehicleDefinition def = VehicleRegistry.getById(getVehicleId());
        if (def == null || def.getBogies().isEmpty()) {
            return null;
        }
        float pt = Mth.clamp(partialTicks, 0.0F, 1.0F);
        double ex = Mth.lerp(pt, this.xo, getX());
        double ey = Mth.lerp(pt, this.yo, getY());
        double ez = Mth.lerp(pt, this.zo, getZ());
        Vec3 rearOff = clientRearBogieOffPrev.lerp(clientRearBogieOffCurr, pt);
        Vec3 frontOff = clientFrontBogieOffPrev.lerp(clientFrontBogieOffCurr, pt);
        Vec3 rearWorld = new Vec3(ex + rearOff.x, ey + rearOff.y, ez + rearOff.z);
        Vec3 frontWorld = new Vec3(ex + frontOff.x, ey + frontOff.y, ez + frontOff.z);
        int[] ext = getExtremeBogieIndices(def);
        if (bogieIndex == ext[0]) {
            return rearWorld;
        }
        if (bogieIndex == ext[1]) {
            return frontWorld;
        }
        // 中間台車: z位置の比率で前後台車間を補間。
        double rearZ = def.getBogies().get(Mth.clamp(ext[0], 0, def.getBogies().size() - 1)).position().z;
        double frontZ = def.getBogies().get(Mth.clamp(ext[1], 0, def.getBogies().size() - 1)).position().z;
        double z = def.getBogies().get(Mth.clamp(bogieIndex, 0, def.getBogies().size() - 1)).position().z;
        double denom = frontZ - rearZ;
        double t = Math.abs(denom) < 1.0E-6D ? 0.5D : Mth.clamp((z - rearZ) / denom, 0.0D, 1.0D);
        return new Vec3(
            Mth.lerp(t, rearWorld.x, frontWorld.x),
            Mth.lerp(t, rearWorld.y, frontWorld.y),
            Mth.lerp(t, rearWorld.z, frontWorld.z));
    }

    /** サーバー専用: 端台車のワールド位置をエンティティ相対オフセットでクライアントへ同期する。 */
    private void syncBogieRenderOffsets() {
        if (level().isClientSide()) {
            return;
        }
        VehicleDefinition def = VehicleRegistry.getById(getVehicleId());
        if (def == null || def.getBogies().isEmpty()
            || !isRailAnchorUsable(frontRailAnchor) || !isRailAnchorUsable(rearRailAnchor)) {
            if (entityData.get(BOGIE_SYNC_VALID)) {
                entityData.set(BOGIE_SYNC_VALID, false);
            }
            return;
        }
        int[] ext = getExtremeBogieIndices(def);
        Vec3 frontWorld = getBogieWorldPosition(ext[1]);
        Vec3 rearWorld = getBogieWorldPosition(ext[0]);
        entityData.set(FRONT_BOGIE_DX, (float) (frontWorld.x - getX()));
        entityData.set(FRONT_BOGIE_DY, (float) (frontWorld.y - getY()));
        entityData.set(FRONT_BOGIE_DZ, (float) (frontWorld.z - getZ()));
        entityData.set(REAR_BOGIE_DX, (float) (rearWorld.x - getX()));
        entityData.set(REAR_BOGIE_DY, (float) (rearWorld.y - getY()));
        entityData.set(REAR_BOGIE_DZ, (float) (rearWorld.z - getZ()));
        // 台車のレール接線ヨーもサーバーのアンカーから求めて同期(クライアントの探索計算より安定=誤方向なし)。
        entityData.set(FRONT_BOGIE_YAW, getBogieWorldYaw(ext[1]));
        entityData.set(REAR_BOGIE_YAW, getBogieWorldYaw(ext[0]));
        entityData.set(BOGIE_SYNC_VALID, true);
    }

    public Vec3 getBogieEntityWorldPosition(int bogieIndex) {
        return getBogieWorldPosition(bogieIndex);
    }

    public Vec3 getBogieVisualWorldPosition(int bogieIndex, float partialTicks) {
        VehicleDefinition def = VehicleRegistry.getById(getVehicleId());
        Vec3 local = getBogieLocalPosition(bogieIndex, def);
        return projectLocalBogiePositionToWorld(local, partialTicks);
    }

    private Vec3 projectLocalBogiePositionToWorld(Vec3 local, float partialTicks) {
        VehicleDefinition def = VehicleRegistry.getById(getVehicleId());
        Vec3 modelOffset = def != null ? def.getModelOffset() : Vec3.ZERO;
        double scale = def != null ? def.getModelScale() : 1.0D;

        double px = modelOffset.x + local.x * scale;
        double py = modelOffset.y + local.y * scale;
        double pz = modelOffset.z + local.z * scale;

        // スポーン直後の未初期化な前tick位置は補間せず現在値を使う
        double dxo = this.getX() - this.xo;
        double dyo = this.getY() - this.yo;
        double dzo = this.getZ() - this.zo;
        boolean staleOldPos = (dxo * dxo + dyo * dyo + dzo * dzo) > 64.0D;

        float trainPitch = staleOldPos ? this.getXRot() : Mth.lerp(partialTicks, this.xRotO, this.getXRot());
        double pitchRad = Math.toRadians(-trainPitch);
        double pitchedY = Math.cos(pitchRad) * py - Math.sin(pitchRad) * pz;
        double pitchedZ = Math.sin(pitchRad) * py + Math.cos(pitchRad) * pz;

        float trainYaw = staleOldPos ? this.getYRot() : Mth.rotLerp(partialTicks, this.yRotO, this.getYRot());
        double yawRad = Math.toRadians(-trainYaw);
        double rotatedX = Math.cos(yawRad) * px - Math.sin(yawRad) * pitchedZ;
        double rotatedZ = Math.sin(yawRad) * px + Math.cos(yawRad) * pitchedZ;

        double trainX = staleOldPos ? this.getX() : Mth.lerp(partialTicks, this.xo, this.getX());
        double trainY = staleOldPos ? this.getY() : Mth.lerp(partialTicks, this.yo, this.getY());
        double trainZ = staleOldPos ? this.getZ() : Mth.lerp(partialTicks, this.zo, this.getZ());
        return new Vec3(trainX + rotatedX, trainY + pitchedY, trainZ + rotatedZ);
    }

    public float getScriptBogieWorldYaw(int bogieIndex) {
        return getBogieWorldYaw(bogieIndex);
    }

    private RailAnchor getAnchorForBogieIndex(VehicleDefinition def, int bogieIndex) {
        if (def == null || def.getBogies().isEmpty()) {
            return null;
        }
        int clamped = Mth.clamp(bogieIndex, 0, def.getBogies().size() - 1);
        int[] extremes = getExtremeBogieIndices(def);
        if (clamped == extremes[0]) {
            return rearRailAnchor;
        }
        if (clamped == extremes[1]) {
            return frontRailAnchor;
        }
        double z = def.getBogies().get(clamped).position().z;
        double rearZ = def.getBogies().get(extremes[0]).position().z;
        double frontZ = def.getBogies().get(extremes[1]).position().z;
        if (Math.abs(z - frontZ) <= Math.abs(z - rearZ)) {
            return frontRailAnchor;
        }
        return rearRailAnchor;
    }

    private float getAnchorRailYaw(RailAnchor anchor, float baseYaw) {
        if (!isRailAnchorUsable(anchor)) {
            return baseYaw;
        }
        return sampleAnchorTangentYaw(anchor, baseYaw);
    }

    private void syncBogieOrientationMemory(RailSample front, RailSample rear, float fallbackYaw, float fallbackPitch) {
        RailAnchor[] anchors = new RailAnchor[]{rearRailAnchor, frontRailAnchor};
        RailSample[] samples = new RailSample[]{rear, front};
        for (int side = 0; side < anchors.length; side++) {
            RailAnchor anchor = anchors[side];
            RailSample sample = samples[side];
            if (isRailAnchorUsable(anchor) && sample != null) {
                float referenceYaw = bogieYawMemory[side] == 0.0F ? fallbackYaw : bogieYawMemory[side];
                float bogieYaw = sampleAnchorTangentYaw(anchor, referenceYaw);
                float railPitch = sampleRailPitch(anchor.map(), anchor.split(), anchor.index());
                bogieYawMemory[side] = bogieYaw;
                bogiePitchMemory[side] = anchor.travelDirection() < 0 ? -railPitch : railPitch;
            } else {
                bogieYawMemory[side] = fallbackYaw;
                bogiePitchMemory[side] = fallbackPitch;
            }
        }
    }

    private float getBogieReferenceYaw(int bogieIndex, float baseYaw) {
        return baseYaw;
    }

    private RailAnchor getNearestAnchorForBogie(VehicleDefinition.BogieDefinition bogie) {
        if (bogie == null) {
            return null;
        }
        Vec3 bogieWorld = localToWorld(bogie.position());
        RailAnchor best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        if (isRailAnchorUsable(frontRailAnchor)) {
            RailSample sample = sampleRail(frontRailAnchor.map(), frontRailAnchor.split(), frontRailAnchor.index());
            bestDistance = new Vec3(sample.x, sample.y, sample.z).distanceToSqr(bogieWorld);
            best = frontRailAnchor;
        }
        if (isRailAnchorUsable(rearRailAnchor)) {
            RailSample sample = sampleRail(rearRailAnchor.map(), rearRailAnchor.split(), rearRailAnchor.index());
            double distance = new Vec3(sample.x, sample.y, sample.z).distanceToSqr(bogieWorld);
            if (distance < bestDistance) {
                best = rearRailAnchor;
            }
        }
        return best;
    }

    private float relativeBogieYaw(float railYaw, float baseYaw) {
        float directDiff = Mth.wrapDegrees(railYaw - baseYaw);
        float flippedDiff = Mth.wrapDegrees(directDiff + 180.0F);
        float diff = Math.abs(directDiff) <= Math.abs(flippedDiff) ? directDiff : flippedDiff;

        return Mth.clamp(diff, -85.0F, 85.0F);
    }

    private void updateStoredBogieState() {
        RailAnchor[] anchors = new RailAnchor[]{rearRailAnchor, frontRailAnchor};
        for (int side = 0; side < anchors.length; side++) {
            RailAnchor anchor = anchors[side];
            if (anchor == null) {
                bogiePrevMaps[side] = null;
                bogiePrevSplits[side] = 0;
                bogiePrevSampleIndex[side] = -1;
                continue;
            }
            bogiePrevMaps[side] = anchor.map();
            bogiePrevSplits[side] = anchor.split();
            bogiePrevSampleIndex[side] = Mth.clamp((int) Math.round(anchor.index()), 0, Math.max(0, anchor.split()));
        }
    }

    private float sampleAnchorTangentYaw(RailAnchor anchor, float fallbackYaw) {
        if (anchor == null || anchor.map() == null || anchor.split() <= 0) {
            return fallbackYaw;
        }
        double delta = Math.max(1.0D, anchor.split() * 0.0060D);
        double beforeIndex = Mth.clamp(anchor.index() - delta, 0.0D, anchor.split());
        double afterIndex = Mth.clamp(anchor.index() + delta, 0.0D, anchor.split());
        RailSample before = sampleRail(anchor.map(), anchor.split(), beforeIndex);
        RailSample after = sampleRail(anchor.map(), anchor.split(), afterIndex);
        double dx = after.x - before.x;
        double dz = after.z - before.z;
        if (dx * dx + dz * dz < 1.0E-6D) {
            float yaw = sampleRailYaw(anchor.map(), anchor.split(), anchor.index());
            return anchor.travelDirection() < 0 ? Mth.wrapDegrees(yaw + 180.0F) : yaw;
        }
        float tangentYaw = (float) Math.toDegrees(Math.atan2(dx, dz));
        if (anchor.travelDirection() < 0) {
            tangentYaw = Mth.wrapDegrees(tangentYaw + 180.0F);
        }
        return keepNearestYaw(tangentYaw, fallbackYaw);
    }

    private RailFollowContext getActiveRailContext() {
        if (activeRailMap != null && activeRailIndex >= 0 && activeRailSplit > 0) {
            int index = Mth.clamp(activeRailIndex, 0, activeRailSplit);
            double[] p = activeRailMap.getRailPos(activeRailSplit, index);
            double py = activeRailMap.getRailHeight(activeRailSplit, index) + RAIL_HEIGHT_OFFSET;
            double distSq = distanceToSqr(p[1], py, p[0]);
            // 交差部では別レールとの距離がかなり近くなるので、少し離れても
            // 現在のレールを優先して掴み続ける。
            if (distSq < 400.0D) {
                return new RailFollowContext(activeRailMap, activeRailSplit, index, distSq);
            }
        }

        RailFollowContext nearest = findNearestRailContext();
        if (nearest != null) {
            activeRailMap = nearest.map();
            activeRailSplit = getMovementSplitForMap(nearest.map());
            activeRailIndex = nearest.nearestIndex();
            activeRailPosition = activeRailIndex;
            float yawAtRail = activeRailMap.getRailYaw(activeRailSplit, activeRailIndex);
            float yawDiff = Math.abs(Mth.wrapDegrees(getYRot() - yawAtRail));
            int mapForwardSign = yawDiff <= 90.0F ? 1 : -1;
            activeRailBodyDirection = mapForwardSign;
            FormationDriver formationDriver = getFormationDriver();
            Entity controller = formationDriver != null ? formationDriver.controller() : null;
            TrainEntity cabTrain = formationDriver != null ? formationDriver.cabTrain() : this;
            float cabDirection = getCabDirectionSign(controller, cabTrain);
            activeRailDirection = Math.abs(cabDirection) < 0.5F
                ? mapForwardSign
                : mapForwardSign * (cabDirection > 0.0F ? 1 : -1);
        }
        return nearest;
    }

    private RailFollowContext findRailContextNear(Vec3 worldPos, RailMap exclude) {
        return findRailContextNear(worldPos, exclude, true);
    }

    private RailFollowContext findRailContextNearAny(Vec3 worldPos, RailMap exclude) {
        return findRailContextNear(worldPos, exclude, false);
    }

    private RailFollowContext findRailContextNear(Vec3 worldPos, RailMap exclude, boolean endpointOnly) {
        BlockPos center = BlockPos.containing(worldPos.x, worldPos.y, worldPos.z);
        RailFollowContext best = null;
        int radius = 20;
        float referenceYaw = getYRot();

        // 1st pass: アクティブ分岐のみ。見つからなければ 2nd pass で全分岐へフォールバック
        // (非アクティブ分岐の終端突入やスイッチ切替で現在分岐が非アクティブ化した際に弾かれないように)。
        for (int pass = 0; pass < 2 && best == null; pass++) {
            railLookupIncludeAllSegments = pass == 1;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dy = -4; dy <= 4; dy++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        BlockPos pos = center.offset(dx, dy, dz);
                        RailMap[] maps = getRailMapsAt(pos);
                        if (maps.length == 0) continue;

                        for (RailMap map : maps) {
                            if (map == null || map == exclude) continue;
                            int split = getMovementSplitForMap(map);
                            // RailMap#getNearlestPoint expects world X/Z in that order.
                            int nearest = map.getNearlestPoint(split, worldPos.x, worldPos.z);
                            nearest = Mth.clamp(nearest, 0, split);
                            double[] p = map.getRailPos(split, nearest);
                            double py = map.getRailHeight(split, nearest) + RAIL_HEIGHT_OFFSET;
                            double distSq = new Vec3(p[1], py, p[0]).distanceToSqr(worldPos);
                            boolean nearEndpoint = nearest <= Math.max(6, split / 6) || nearest >= split - Math.max(6, split / 6);
                            double maxDistSq = endpointOnly ? 81.0D : 144.0D;
                            if ((endpointOnly && !nearEndpoint) || distSq > maxDistSq) {
                                continue;
                            }
                            distSq += railYawPenalty(map, split, nearest, referenceYaw);
                            if (activeRailMap != null && map == activeRailMap) {
                                distSq -= endpointOnly ? 2.0D : 6.0D;
                            }
                            if (best == null || distSq < best.distanceSq()) {
                                best = new RailFollowContext(map, split, nearest, distSq);
                            }
                        }
                    }
                }
            }
        }
        railLookupIncludeAllSegments = false;

        return best;
    }

    private RailFollowContext findNearestRailContext() {
        BlockPos center = blockPosition();
        RailFollowContext best = null;
        int radius = (int) RAIL_SEARCH_RADIUS;
        float referenceYaw = getYRot();

        // 1st pass: アクティブ分岐のみ。見つからなければ 2nd pass で全分岐へフォールバック
        // (分岐器の非アクティブ側に触れて再取得が走った時に弾かれないように)。
        for (int pass = 0; pass < 2 && best == null; pass++) {
            railLookupIncludeAllSegments = pass == 1;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dy = -2; dy <= 2; dy++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        BlockPos pos = center.offset(dx, dy, dz);
                        RailMap[] maps = getRailMapsAt(pos);
                        if (maps.length == 0) continue;

                        for (RailMap map : maps) {
                            if (map == null) continue;
                            int split = getMovementSplitForMap(map);
                            int nearest = map.getNearlestPoint(split, getX(), getZ());
                            nearest = Mth.clamp(nearest, 0, split);
                            double[] p = map.getRailPos(split, nearest);
                            double py = map.getRailHeight(split, nearest) + RAIL_HEIGHT_OFFSET;
                            double distSq = distanceToSqr(p[1], py, p[0]);

                            distSq += railYawPenalty(map, split, nearest, referenceYaw);
                            if (activeRailMap != null && map == activeRailMap) {
                                distSq -= 6.0D;
                            }
                            if (best == null || distSq < best.distanceSq()) {
                                best = new RailFollowContext(map, split, nearest, distSq);
                            }
                        }
                    }
                }
            }
        }
        railLookupIncludeAllSegments = false;

        return best;
    }

    private double railYawPenalty(RailMap map, int split, int index, float referenceYaw) {
        float railYaw = map.getRailYaw(split, index);
        float diffForward = Math.abs(Mth.wrapDegrees(referenceYaw - railYaw));
        float diffReverse = Math.abs(Mth.wrapDegrees(referenceYaw - (railYaw + 180.0F)));
        float diff = Math.min(diffForward, diffReverse);
        return diff * 0.06D;
    }

    private RailFollowContext findRailContextBeyondBoundary(RailMap currentMap, int currentSplit, int boundaryIndex, int travelDirection) {
        if (currentMap == null || currentSplit <= 0) {
            return null;
        }
        RailSample boundary = sampleRail(currentMap, currentSplit, boundaryIndex);
        float boundaryYaw = currentMap.getRailYaw(currentSplit, boundaryIndex);
        if (travelDirection < 0) {
            boundaryYaw = Mth.wrapDegrees(boundaryYaw + 180.0F);
        }
        double yawRad = Math.toRadians(-boundaryYaw);
        Vec3 forward = new Vec3(-Math.sin(yawRad), 0.0D, Math.cos(yawRad));
        Vec3 boundaryPos = new Vec3(boundary.x, boundary.y, boundary.z);
        RailPosition endpoint = boundaryIndex <= 0 ? currentMap.getStartRP() : currentMap.getEndRP();
        BlockPos center = BlockPos.containing(boundary.x, boundary.y, boundary.z);
        RailFollowContext best = null;
        int radius = 20;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -4; dy <= 4; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    RailMap[] maps = getConnectionCandidateMapsAt(center.offset(dx, dy, dz), currentMap);
                    if (maps.length == 0) {
                        continue;
                    }
                    for (RailMap map : maps) {
                        if (map == null || map == currentMap || map.equals(currentMap)) {
                            continue;
                        }
                        int split = getMovementSplitForMap(map);
                        best = betterFollowContext(best, evaluateBoundaryFallback(map, split, 0, endpoint, boundaryPos, boundaryYaw));
                        best = betterFollowContext(best, evaluateBoundaryFallback(map, split, split, endpoint, boundaryPos, boundaryYaw));
                    }
                }
            }
        }
        return best;
    }

    private RailFollowContext evaluateBoundaryFallback(
        RailMap map,
        int split,
        int endpointIndex,
        RailPosition currentEndpoint,
        Vec3 boundaryPos,
        float outgoingYaw
    ) {
        RailPosition candidateEndpoint = endpointIndex <= 0 ? map.getStartRP() : map.getEndRP();
        boolean sameEndpoint = candidateEndpoint != null && sameRailEndpoint(candidateEndpoint, currentEndpoint);
        RailSample sample = sampleRail(map, split, endpointIndex);
        double distSq = new Vec3(sample.x, sample.y, sample.z).distanceToSqr(boundaryPos);
        if (!sameEndpoint && distSq > RAIL_CONNECTION_MAX_DISTANCE_SQ) {
            return null;
        }

        int nextTravelDirection = endpointIndex <= 0 ? 1 : -1;
        float candidateYaw = map.getRailYaw(split, endpointIndex);
        if (nextTravelDirection < 0) {
            candidateYaw = Mth.wrapDegrees(candidateYaw + 180.0F);
        }
        float yawDiff = Math.abs(Mth.wrapDegrees(outgoingYaw - candidateYaw));
        // 端点共有でも逆走(yaw差>90°)接続は拒否(分岐器トランクでの兄弟分岐へのU字乗り移り防止)。
        boolean reversal = yawDiff > 90.0F;
        if ((!sameEndpoint && yawDiff > RAIL_CONNECTION_MAX_YAW_DIFF) || reversal) {
            return null;
        }
        double score = distSq + yawDiff * 0.02D + (sameEndpoint ? -576.0D : 0.0D);
        return new RailFollowContext(map, split, endpointIndex, score);
    }

    private RailFollowContext betterFollowContext(RailFollowContext current, RailFollowContext candidate) {
        if (candidate == null) {
            return current;
        }
        if (current == null || candidate.distanceSq() < current.distanceSq()) {
            return candidate;
        }
        return current;
    }

    private RailMap[] getRailMapsAt(BlockPos pos) {
        if (level().getBlockEntity(pos) instanceof LargeRailCoreBlockEntity core && core.isLoaded()) {
            return railLookupIncludeAllSegments ? core.getAllRailMaps() : core.getActiveRailMaps();
        }
        if (level().getBlockEntity(pos) instanceof RailCollisionBlockEntity collision) {
            BlockPos corePos = collision.getCorePos();
            if (corePos != null && level().getBlockEntity(corePos) instanceof LargeRailCoreBlockEntity core && core.isLoaded()) {
                return railLookupIncludeAllSegments ? core.getAllRailMaps() : core.getActiveRailMaps();
            }
        }
        // jp.ngt.rtm.rail (Phase 1 本家忠実システム)
        if (level().getBlockEntity(pos) instanceof jp.ngt.rtm.rail.TileEntityLargeRailBase railBase) {
            jp.ngt.rtm.rail.TileEntityLargeRailCore core = railBase.getRailCore();
            if (core != null && core.isLoaded()) {
                if (core instanceof jp.ngt.rtm.rail.TileEntityLargeRailSwitchCore switchCore && !railLookupIncludeAllSegments) {
                    jp.ngt.rtm.rail.util.SwitchType st = switchCore.getSwitch();
                    if (st != null) {
                        java.util.List<RailMap> open = st.getOpenRailMaps();
                        return open.toArray(new RailMap[0]);
                    }
                }
                RailMap[] maps = core.getAllRailMaps();
                if (maps != null) {
                    return maps;
                }
            }
        }
        return new RailMap[0];
    }


    public void coupleNearest() {
        if (level().isClientSide()) {
            return;
        }
        TrainEntity nearest = null;
        double best = 9.0D;
        for (TrainEntity other : level().getEntitiesOfClass(TrainEntity.class, getBoundingBox().inflate(6.0D))) {
            if (other == this || !other.isAlive()) continue;
            double d = other.position().distanceToSqr(this.position());
            if (d < best) {
                best = d;
                nearest = other;
            }
        }
        if (nearest != null) {
            coupleWith(nearest);
        }
    }

    public void coupleWith(TrainEntity other) {
        if (other == null || other == this || level().isClientSide()) {
            return;
        }
        TrainEntity tail = getFormationTail();
        TrainEntity otherHead = other.getFormationHead();
        if (tail == null || otherHead == null || tail == otherHead) {
            return;
        }
        if (otherHead.coupledLeaderUuid != null || tail.coupledFollowerUuid != null || otherHead.hasIndirectPassenger(tail) || tail.isConnectedTo(otherHead)) {
            return;
        }
        tail.linkCouplingByPosition(otherHead);
        // stabilizeCoupledFormations handles: velocity zeroing, settings sync, position snapping,
        // formation rebuild, and immediate updateTrainMovement snap for all cars.
        stabilizeCoupledFormations(tail, otherHead);
        clearCouplingModeInvolving(this, other);
        tail.hurtMarked = true;
        otherHead.hurtMarked = true;
    }

    private void linkCouplingByPosition(TrainEntity other) {
        if (other == null || other == this || isConnectedTo(other)) {
            return;
        }
        CouplerPair pair = findNearestCouplerPair(other);
        setCoupledFollowerUuid(other.getUUID());
        coupledFollowerThisSide = pair.thisSide();
        coupledFollowerOtherSide = pair.otherSide();
        other.setCoupledLeaderUuid(getUUID());
    }

    private CouplerPair findNearestCouplerPair(TrainEntity other) {
        CouplerPair best = new CouplerPair(-1, 1, Double.MAX_VALUE);
        if (other == null) {
            return best;
        }
        for (int thisSide : new int[] {1, -1}) {
            Vec3 thisPoint = getCouplerPoint(thisSide > 0);
            for (int otherSide : new int[] {1, -1}) {
                double distance = thisPoint.distanceToSqr(other.getCouplerPoint(otherSide > 0));
                if (distance < best.distanceSqr()) {
                    best = new CouplerPair(thisSide, otherSide, distance);
                }
            }
        }
        return best;
    }

    private Vec3 getCouplerPoint(boolean front) {
        double z = front ? getTrainHalfLength() : -getTrainHalfLength();
        return localToWorld(new Vec3(0.0D, 0.0D, z));
    }

    private double getNearestCouplerDistanceSqr(TrainEntity other) {
        return findNearestCouplerPair(other).distanceSqr();
    }

    private boolean canCompleteCouplingWith(TrainEntity other) {
        if (other == null || other == this || isConnectedTo(other)) {
            return false;
        }
        if (getNearestCouplerDistanceSqr(other) <= 16.0D) {
            return true;
        }
        return getBoundingBox().inflate(4.0D).intersects(other.getBoundingBox().inflate(4.0D));
    }

    private void enterCouplingMode(Player player) {
        if (level().isClientSide()) {
            return;
        }
        UUID playerId = player.getUUID();
        CouplingSelection selection = COUPLING_MODE.get(playerId);
        if (selection == null || selection.isComplete()) {
            COUPLING_MODE.put(playerId, new CouplingSelection(getUUID(), -1, null, -1, level().getGameTime()));
            player.displayClientMessage(Component.literal("連結モード: もう片方の列車の台車を選択してください"), true);
            return;
        }
        if (selection.first().equals(getUUID())) {
            player.displayClientMessage(Component.literal("連結モード: もう片方の列車を選択してください"), true);
            return;
        }
        COUPLING_MODE.put(playerId, new CouplingSelection(selection.first(), selection.firstBogieIndex(), getUUID(), -1, level().getGameTime()));
        player.displayClientMessage(Component.literal("連結モード: 2両をゆっくり接触させてください"), true);
    }

    private void tryCompletePendingCoupling() {
        if (!(level() instanceof net.minecraft.server.level.ServerLevel serverLevel) || COUPLING_MODE.isEmpty()) {
            return;
        }
        Map<UUID, CouplingSelection> pending = new HashMap<>(COUPLING_MODE);
        for (Map.Entry<UUID, CouplingSelection> entry : pending.entrySet()) {
            CouplingSelection selection = entry.getValue();
            if (selection == null || !selection.isComplete()) {
                continue;
            }
            if (level().getGameTime() - selection.armedAt() < 6L) {
                continue;
            }
            Entity sourceRaw = serverLevel.getEntity(selection.first());
            Entity targetRaw = serverLevel.getEntity(selection.second());
            if (!(sourceRaw instanceof TrainEntity source) || !source.isAlive()) {
                COUPLING_MODE.remove(entry.getKey());
                continue;
            }
            if (!(targetRaw instanceof TrainEntity target) || !target.isAlive()) {
                COUPLING_MODE.remove(entry.getKey());
                continue;
            }
            if (source == target || source.isConnectedTo(target)) {
                // 既に連結済み（別経路で接触連結された等）なら、残った連結モードを破棄する。
                COUPLING_MODE.remove(entry.getKey());
                continue;
            }
            if (source.canCompleteCouplingWith(target)) {
                source.coupleWith(target);
                COUPLING_MODE.remove(entry.getKey());
                Player player = serverLevel.getPlayerByUUID(entry.getKey());
                if (player != null) {
                    player.displayClientMessage(Component.literal("連結しました"), true);
                }
            }
        }
    }

    public boolean isConnectedTo(TrainEntity other) {
        if (other == null) {
            return false;
        }
        return other.getUUID().equals(coupledFollowerUuid)
            || other.getUUID().equals(coupledLeaderUuid)
            || this.getUUID().equals(other.coupledFollowerUuid)
            || this.getUUID().equals(other.coupledLeaderUuid);
    }

    public void decouple() {
        if (level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            // Invalidate the shared Formation so all members rebuild independently on next tick
            if (formation != null) {
                formation.trainStream().forEach(t -> { if (t != null) t.formation = null; });
            }
            formation = null;
            if (coupledFollowerUuid != null) {
                Entity followerRaw = serverLevel.getEntity(coupledFollowerUuid);
                if (followerRaw instanceof TrainEntity follower) {
                    follower.setCoupledLeaderUuid(null);
                }
            }
            if (coupledLeaderUuid != null) {
                Entity leaderRaw = serverLevel.getEntity(coupledLeaderUuid);
                if (leaderRaw instanceof TrainEntity leader && this.getUUID().equals(leader.coupledFollowerUuid)) {
                    leader.setCoupledFollowerUuid(null);
                    leader.coupledFollowerThisSide = -1;
                    leader.coupledFollowerOtherSide = 1;
                }
            }
        }
        setCoupledFollowerUuid(null);
        setCoupledLeaderUuid(null);
        coupledFollowerThisSide = -1;
        coupledFollowerOtherSide = 1;
    }

    public boolean isConnected() {
        return coupledFollowerUuid != null || coupledLeaderUuid != null;
    }

    public TrainEntity getConnectedTrain() {
        if (!(level() instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            return null;
        }
        if (coupledFollowerUuid != null) {
            Entity entity = serverLevel.getEntity(coupledFollowerUuid);
            if (entity instanceof TrainEntity train) return train;
        }
        if (coupledLeaderUuid != null) {
            Entity entity = serverLevel.getEntity(coupledLeaderUuid);
            if (entity instanceof TrainEntity train) return train;
        }
        return null;
    }

    private RailAnchor getAnchorForRenderedBogie(int bogieIndex) {
        VehicleDefinition def = VehicleRegistry.getById(getVehicleId());
        if (def == null || def.getBogies().isEmpty()) {
            return null;
        }
        return getAnchorForBogieIndex(def, bogieIndex);
    }

    // ======== Coupling system (RTM-faithful port) ========

    private boolean coupleFormationsRtMLike(TrainEntity sourceTrain, int sourceSide, TrainEntity targetTrain, int targetSide) {
        if (sourceTrain == null || targetTrain == null || sourceTrain == targetTrain) return false;
        if (!(level() instanceof net.minecraft.server.level.ServerLevel)) return false;
        if (sourceTrain.isConnectedTo(targetTrain) || targetTrain.hasIndirectPassenger(sourceTrain)) return false;
        CouplingEndpoint source = prepareCouplingEndpoint(sourceTrain, normalizeCouplerSide(sourceSide), true);
        CouplingEndpoint target = prepareCouplingEndpoint(targetTrain, normalizeCouplerSide(targetSide), false);
        if (source == null || target == null) return false;
        if (source.train() == null || target.train() == null || source.train() == target.train()) return false;
        if (source.train().coupledFollowerUuid != null || target.train().coupledLeaderUuid != null) return false;
        if (!source.train().canCompleteCouplingWith(target.train(), source.side(), target.side())) return false;
        source.train().linkCouplingBySelection(target.train(), source.side(), target.side());
        stabilizeCoupledFormations(source.train(), target.train());
        return true;
    }

    private void linkCouplingBySelection(TrainEntity other, int thisSide, int otherSide) {
        if (other == null || other == this || isConnectedTo(other)) return;
        setCoupledFollowerUuid(other.getUUID());
        coupledFollowerThisSide = thisSide;
        coupledFollowerOtherSide = otherSide;
        other.setCoupledLeaderUuid(getUUID());
    }

    private CouplingEndpoint prepareCouplingEndpoint(TrainEntity selectedTrain, int selectedSide, boolean sourceRole) {
        if (selectedTrain == null) return null;
        List<TrainEntity> chain = selectedTrain.getFormationTrainsInOrder();
        if (chain.isEmpty()) return null;
        TrainEntity head = chain.get(0);
        TrainEntity tail = chain.get(chain.size() - 1);
        if (chain.size() == 1) return new CouplingEndpoint(selectedTrain, selectedSide);
        int headSide = getExposedHeadSide(head);
        int tailSide = getExposedTailSide(tail);
        if (selectedTrain == head && headSide == selectedSide) return new CouplingEndpoint(head, selectedSide);
        if (selectedTrain == tail && tailSide == selectedSide) return new CouplingEndpoint(tail, selectedSide);
        return null;
    }

    private void stabilizeCoupledFormations(TrainEntity sourceTail, TrainEntity targetHead) {
        TrainEntity formationHead = sourceTail.getFormationHead();
        formationHead.forEachFormationTrain(t -> {
            t.setSpeed(0.0F);
            t.setNotch(0);
            t.setDeltaMovement(Vec3.ZERO);
        });
        targetHead.forEachFormationTrain(t -> {
            t.setSpeed(0.0F);
            t.setNotch(0);
            t.setDeltaMovement(Vec3.ZERO);
        });
        formationHead.setNotchForFormation(0);
        formationHead.setReverserForFormation(sourceTail.getReverser());
        formationHead.setLightModeForFormation(sourceTail.getLightMode());
        formationHead.setDestinationIndexForFormation(sourceTail.getDestinationIndex());
        FormationDriver formationDriver = formationHead.getFormationDriver();
        if (formationDriver != null) {
            formationHead.markDriverControl(formationDriver.controller());
            formationHead.ensureDriverReadyForFormation(formationDriver.controller());
        }
        // 連結時に本体をテレポートさせない。編成結合とブレーキのみ
        sourceTail.stopFormationMotionForResync(8L);
        targetHead.stopFormationMotionForResync(8L);
        // Rebuild Formation for the newly combined UUID chain
        sourceTail.getFormationHead().rebuildFormationFromUuidChain();
        TrainEntity newHead = sourceTail.getFormationHead();
        if (newHead != null) {
            newHead.setNotchForFormation(0);
        }
        // 連結直後は新規連結車だけを静かに寄せる。大ジャンプは棄却
        if (sourceTail.coupledFollowerUuid != null && targetHead != null) {
            double pX = targetHead.getX();
            double pY = targetHead.getY();
            double pZ = targetHead.getZ();
            boolean ok = sourceTail.placeCoupledFollowerOnRail(targetHead,
                    sourceTail.coupledFollowerThisSide, sourceTail.coupledFollowerOtherSide);
            double jx = targetHead.getX() - pX;
            double jz = targetHead.getZ() - pZ;
            double maxJump = 6.0D; // 接触連結は約3m以内なので、隙間詰めは小移動。これを超えたらテレポート扱い。
            if (!ok || jx * jx + jz * jz > maxJump * maxJump) {
                targetHead.moveTo(pX, pY, pZ, targetHead.getYRot(), targetHead.getXRot());
            } else {
                targetHead.settleCoupledRailPose();
            }
        }
    }

    private double getCoupledFollowerDistanceErrorMeters(TrainEntity follower, int thisSide, int followerSide) {
        if (follower == null) return Double.MAX_VALUE;
        double expected = getDefaultDistanceToConnectedTrain(follower);
        double actual = Math.sqrt(getCouplerDistanceSqr(follower, thisSide, followerSide));
        return Math.abs(actual - expected);
    }

    private boolean followCoupledTrainRtMLike(TrainEntity leader, TrainEntity follower) {
        if (leader == null || follower == null) return false;
        boolean snapped = leader.placeCoupledFollowerOnRail(follower, leader.coupledFollowerThisSide, leader.coupledFollowerOtherSide)
                || leader.trySoftPlaceCoupledFollowerOnRail(follower, leader.coupledFollowerThisSide, leader.coupledFollowerOtherSide, true);
        follower.setSpeed(leader.getSpeed());
        follower.setNotch(leader.getNotch());
        follower.setReverser(leader.getReverser());
        if (snapped) {
            follower.centerGuidanceFallbackTicks = 0;
            follower.railGuidanceFailureTicks = 0;
            follower.travelStallTicks = 0;
            follower.settleCoupledRailPose();
        } else {
            leader.placeCoupledFollowerFallback(follower, leader.coupledFollowerThisSide, leader.coupledFollowerOtherSide, leader.getDefaultDistanceToConnectedTrain(follower));
            follower.centerGuidanceFallbackTicks = Math.max(follower.centerGuidanceFallbackTicks, 16);
            follower.railGuidanceFailureTicks = 0;
            follower.travelStallTicks = 0;
            follower.clearRailGuidance();
            follower.setDeltaMovement(Vec3.ZERO);
            follower.settleCoupledRailPose();
        }
        return true;
    }

    private boolean trySoftPlaceCoupledFollowerOnRail(TrainEntity follower, int thisSide, int followerSide, boolean curveSensitive) {
        if (follower == null) return false;
        RailMap prevRailMap = follower.activeRailMap;
        int prevRailSplit = follower.activeRailSplit;
        int prevRailIndex = follower.activeRailIndex;
        double prevRailPosition = follower.activeRailPosition;
        int prevRailDirection = follower.activeRailDirection;
        int prevBodyDirection = follower.activeRailBodyDirection;
        RailAnchor prevFrontAnchor = follower.frontRailAnchor;
        RailAnchor prevRearAnchor = follower.rearRailAnchor;
        Vec3 prevPos = follower.position();
        if (!placeCoupledFollowerOnRail(follower, thisSide, followerSide)) return false;
        double maxJump = curveSensitive
                ? Math.max(1.4, Math.abs(getSpeed()) * 0.42 + 0.8)
                : Math.max(0.95, Math.abs(getSpeed()) * 0.34 + 0.5);
        double jumpSq = follower.position().distanceToSqr(prevPos);
        double maxDistanceError = curveSensitive ? 5.2 : 3.75;
        if (jumpSq <= maxJump * maxJump && isCoupledFollowerGeometryStable(follower, thisSide, followerSide, maxDistanceError)) {
            return true;
        }
        follower.restoreRailState(prevRailMap, prevRailSplit, prevRailIndex, prevRailPosition, prevRailDirection, prevBodyDirection, prevFrontAnchor, prevRearAnchor);
        if (isRailAnchorUsable(prevFrontAnchor) && isRailAnchorUsable(prevRearAnchor)) {
            follower.settleCoupledRailPose();
        } else {
            follower.moveTo(prevPos.x, prevPos.y, prevPos.z, follower.getYRot(), follower.getXRot());
        }
        follower.setDeltaMovement(Vec3.ZERO);
        return false;
    }

    private boolean isCoupledFollowerGeometryStable(TrainEntity follower, int thisSide, int followerSide, double maxDistanceError) {
        if (follower == null) return false;
        int leaderSide = normalizeCouplerSide(thisSide);
        int otherSide = normalizeCouplerSide(followerSide);
        double anchorGap = getCoupledAnchorGapMeters(leaderSide, follower, otherSide);
        double distance = Math.sqrt(getCouplerDistanceSqr(follower, thisSide, followerSide));
        return distance <= anchorGap + maxDistanceError;
    }

    private double getCoupledAnchorGapMeters(int thisSide, TrainEntity follower, int followerSide) {
        if (follower == null) return Math.max(4.0, getConfiguredTrainDistance() * 2.0);
        double dist = getDefaultDistanceToConnectedTrain(follower);
        double leaderOffset = Math.abs(getRailOffsetForSide(thisSide));
        double followerOffset = Math.abs(follower.getRailOffsetForSide(followerSide));
        return Math.max(0.5, dist - leaderOffset - followerOffset);
    }

    private double getRailOffsetForSide(int side) {
        double[] bogieZ = getBogieRailOffsets();
        return side > 0 ? bogieZ[1] : bogieZ[0];
    }

    private double getCouplerDistanceSqr(TrainEntity other, int thisSide, int otherSide) {
        return other == null ? Double.MAX_VALUE
                : getCouplerPoint(thisSide > 0).distanceToSqr(other.getCouplerPoint(otherSide > 0));
    }

    private boolean forceCoupledFormationResync(TrainEntity follower, boolean curveSensitive) {
        if (follower == null) return false;
        stopFormationMotionForResync(curveSensitive ? 10L : 6L);
        follower.stopFormationMotionForResync(curveSensitive ? 10L : 6L);
        boolean resynced = trackCoupledFollowerFromLeaderRtMLike(follower, coupledFollowerThisSide, coupledFollowerOtherSide)
                || trySoftPlaceCoupledFollowerOnRail(follower, coupledFollowerThisSide, coupledFollowerOtherSide, true)
                || placeCoupledFollowerOnRail(follower, coupledFollowerThisSide, coupledFollowerOtherSide);
        TrainEntity head = getFormationHead();
        head.settleConnectedFormationToRail();
        head.rememberConnectedFormationStableRailState();
        if (resynced) {
            follower.centerGuidanceFallbackTicks = 0;
            follower.railGuidanceFailureTicks = 0;
            follower.travelStallTicks = 0;
            return true;
        } else if (follower.restoreLastStableRailState()) {
            follower.settleCoupledRailPose();
            head.settleConnectedFormationToRail();
            head.rememberConnectedFormationStableRailState();
            follower.centerGuidanceFallbackTicks = Math.max(follower.centerGuidanceFallbackTicks, 12);
            follower.railGuidanceFailureTicks = 0;
            follower.travelStallTicks = 0;
            return true;
        } else {
            follower.centerGuidanceFallbackTicks = Math.max(follower.centerGuidanceFallbackTicks, 8);
            return false;
        }
    }

    private boolean trackCoupledFollowerFromLeaderRtMLike(TrainEntity follower, int thisSide, int followerSide) {
        if (follower == null) return false;
        if (!isRailAnchorUsable(frontRailAnchor) || !isRailAnchorUsable(rearRailAnchor)) return false;
        boolean stationary = areCoupledTrainsEffectivelyStationary(this, follower);
        boolean settleWindow = isWithinCouplingSettleWindow() || follower.isWithinCouplingSettleWindow();
        if (!stationary && !settleWindow && follower.isRailGuided()) return false;
        return placeCoupledFollowerOnRail(follower, thisSide, followerSide);
    }

    private static boolean areCoupledTrainsEffectivelyStationary(TrainEntity first, TrainEntity second) {
        if (first == null || second == null) return false;
        return Math.abs(first.getSpeed()) < 0.01F && Math.abs(second.getSpeed()) < 0.01F
                && first.getDeltaMovement().horizontalDistanceSqr() < 1e-4
                && second.getDeltaMovement().horizontalDistanceSqr() < 1e-4;
    }

    private void stopFormationMotionForResync(long settleTicks) {
        forEachFormationTrain(train -> {
            train.setDeltaMovement(Vec3.ZERO);
            train.markCouplingSettleWindow(settleTicks);
            train.hurtMarked = true;
            train.hasImpulse = true;
        });
    }

    private void applyImmediateContactBrake(long holdTicks) {
        forEachFormationTrain(train -> {
            train.setSpeed(0.0F);
            train.setDeltaMovement(Vec3.ZERO);
            train.markUncoupledContactStopWindow(holdTicks);
            train.hurtMarked = true;
            train.hasImpulse = true;
        });
    }

    public boolean tryCoupleFromBogieContact(int thisBogieIndex, TrainEntity otherTrain, int otherBogieIndex) {
        if (otherTrain == null || otherTrain == this || level().isClientSide()) return false;
        int thisCouplingIndex = getPreferredCouplingBogieIndex(thisBogieIndex);
        int otherCouplingIndex = otherTrain.getPreferredCouplingBogieIndex(otherBogieIndex);
        if (!canResolveBogieContactWith(otherTrain, thisCouplingIndex, otherCouplingIndex)) return false;
        // RTMスタイル: アクティベーション不要、接触したら自動連結
        int thisSide = getCouplerSideForBogieIndex(thisCouplingIndex);
        int otherSide = otherTrain.getCouplerSideForBogieIndex(otherCouplingIndex);
        if (isConnectionPresentOnSide(thisSide) || otherTrain.isConnectionPresentOnSide(otherSide)) return false;
        // カーブなどで編成先頭車の前端に別の車両が誤接触するのを防ぐ。
        // 先頭車（後方に連結済み）の前端へのボギー接触連結は禁止し、プレイヤー操作連結のみ許可する。
        if (isHeadFrontSideOfMulticarFormation(thisSide) || otherTrain.isHeadFrontSideOfMulticarFormation(otherSide)) return false;
        boolean coupled = coupleFormationsRtMLike(this, thisSide, otherTrain, otherSide)
                || otherTrain.coupleFormationsRtMLike(otherTrain, otherSide, this, thisSide);
        if (coupled) {
            clearCouplingModeInvolving(this, otherTrain);
            notifyCouplingChat(Component.literal("連結しました"), otherTrain, null);
        }
        return coupled;
    }

    private boolean isHeadFrontSideOfMulticarFormation(int side) {
        if (coupledFollowerUuid == null || coupledLeaderUuid != null) return false;
        // この車両は多両編成の先頭車。sideが後方連結側でなければ「前端」と判定する。
        return normalizeCouplerSide(side) != normalizeCouplerSide(coupledFollowerThisSide);
    }

    public void handleBogieContactWithoutCoupling(int thisBogieIndex, TrainEntity otherTrain, int otherBogieIndex) {
        if (otherTrain == null || otherTrain == this || level().isClientSide()) return;
        // 連結モード中(プレイヤーがこの2編成を選択して連結しようとしている)は、接触ブレーキを
        // 抑止して接近・連結させる。ブレーキすると速度0/1の振動で連結完了距離まで近づけず、
        // 3両目以降が連結できない不具合になっていた(tryCompletePendingCoupling が完了させる)。
        if (isCouplingModeActiveBetween(this, otherTrain)) {
            return;
        }
        // 連結できない（既連結など）場合にめり込み防止のためブレーキ
        if (!isWithinUncoupledContactStopWindow() && !otherTrain.isWithinUncoupledContactStopWindow()) {
            applyImmediateContactBrake(12L);
            otherTrain.applyImmediateContactBrake(12L);
        }
    }

    /** 2編成間で連結モード(プレイヤー選択)がアクティブか。接触ブレーキ抑止の判定に使う。 */
    private static boolean isCouplingModeActiveBetween(TrainEntity a, TrainEntity b) {
        if (COUPLING_MODE.isEmpty() || a == null || b == null) {
            return false;
        }
        java.util.Set<UUID> aSet = new java.util.HashSet<>();
        a.forEachFormationTrain(t -> { if (t != null) aSet.add(t.getUUID()); });
        java.util.Set<UUID> bSet = new java.util.HashSet<>();
        b.forEachFormationTrain(t -> { if (t != null) bSet.add(t.getUUID()); });
        for (CouplingSelection s : COUPLING_MODE.values()) {
            if (s == null) {
                continue;
            }
            boolean firstInA = s.first() != null && aSet.contains(s.first());
            boolean firstInB = s.first() != null && bSet.contains(s.first());
            boolean secondInA = s.second() != null && aSet.contains(s.second());
            boolean secondInB = s.second() != null && bSet.contains(s.second());
            if ((firstInA && secondInB) || (firstInB && secondInA)) {
                return true;
            }
        }
        return false;
    }

    public boolean canResolveBogieContactWith(TrainEntity otherTrain, int thisBogieIndex, int otherBogieIndex) {
        if (otherTrain == null || otherTrain == this) return false;
        RailAnchor thisAnchor = getAnchorForRenderedBogie(thisBogieIndex);
        RailAnchor otherAnchor = otherTrain.getAnchorForRenderedBogie(otherBogieIndex);
        if (isRailAnchorUsable(thisAnchor) && otherTrain.isRailAnchorUsable(otherAnchor)) {
            RailMap thisMap = thisAnchor.map();
            RailMap otherMap = otherAnchor.map();
            if (thisMap == otherMap || sameRailShape(thisMap, otherMap) || railsShareEndpoint(thisMap, otherMap)) {
                return true;
            }
        }
        int thisSide = getCouplerSideForBogieIndex(thisBogieIndex);
        int otherSide = otherTrain.getCouplerSideForBogieIndex(otherBogieIndex);
        double distance = getDefaultDistanceToConnectedTrain(otherTrain);
        double allowed = Math.max(distance + 1.5, distance * 1.12);
        return getCouplerDistanceSqr(otherTrain, thisSide, otherSide) <= allowed * allowed;
    }

    public boolean isCouplingApproachCloseEnough(TrainEntity other, int thisBogieIndex, int otherBogieIndex) {
        if (other == null || other == this) return false;
        int thisCouplingIndex = getPreferredCouplingBogieIndex(thisBogieIndex);
        int otherCouplingIndex = other.getPreferredCouplingBogieIndex(otherBogieIndex);
        int thisSide = getCouplerSideForBogieIndex(thisCouplingIndex);
        int otherSide = other.getCouplerSideForBogieIndex(otherCouplingIndex);
        // 連結器端点間が3m以内のときのみ接近と判定（ブレーキ用途）
        return getCouplerDistanceSqr(other, thisSide, otherSide) <= 3.0D * 3.0D;
    }

    private void decoupleAtSide(int side) {
        int normalized = normalizeCouplerSide(side);
        if (!(level() instanceof net.minecraft.server.level.ServerLevel serverLevel)) return;
        if (coupledFollowerUuid != null && normalizeCouplerSide(coupledFollowerThisSide) == normalized) {
            decouple();
        } else if (getCouplerSideConnectedToLeader() == normalized && coupledLeaderUuid != null) {
            Entity leaderRaw = serverLevel.getEntity(coupledLeaderUuid);
            if (leaderRaw instanceof TrainEntity leader) {
                leader.decouple();
            }
        }
    }

    private boolean isConnectionPresentOnSide(int side) {
        int normalized = normalizeCouplerSide(side);
        if (coupledFollowerUuid != null && normalizeCouplerSide(coupledFollowerThisSide) == normalized) return true;
        return getCouplerSideConnectedToLeader() == normalized;
    }

    private int getCouplerSideConnectedToLeader() {
        if (coupledLeaderUuid == null) return 0;
        TrainEntity leader = resolveCoupledTrain(coupledLeaderUuid);
        return leader != null && getUUID().equals(leader.coupledFollowerUuid)
                ? normalizeCouplerSide(leader.coupledFollowerOtherSide) : 0;
    }

    private int getCouplerSideForBogieIndex(int bogieIndex) {
        Vec3 bogiePos = getBogieWorldPosition(bogieIndex);
        Vec3 forward = localToWorld(new Vec3(0.0D, 0.0D, 1.0D)).subtract(position());
        if (bogiePos != null && forward.lengthSqr() > 1e-6) {
            Vec3 relative = bogiePos.subtract(position());
            double projection = relative.dot(forward.normalize());
            if (Math.abs(projection) > 0.1) return projection > 0.0 ? 1 : -1;
        }
        VehicleDefinition def = VehicleRegistry.getById(getVehicleId());
        if (def != null && bogieIndex >= 0 && bogieIndex < def.getBogies().size()) {
            return def.getBogies().get(bogieIndex).position().z >= 0.0 ? 1 : -1;
        }
        return resolveExtremeSideForBogieIndex(bogieIndex) == 1 ? 1 : -1;
    }

    private TrainBogieEntity getBogieHitbox(int bogieIndex) {
        return resolveBogieHitbox(Mth.clamp(bogieIndex, 0, 1));
    }

    public void setBogieActivated(int bogieIndex, boolean activated) {
        TrainBogieEntity bogie = getBogieHitbox(bogieIndex);
        if (bogie != null) bogie.setActivated(activated);
    }

    public boolean isBogieActivated(int bogieIndex) {
        TrainBogieEntity bogie = getBogieHitbox(bogieIndex);
        return bogie != null && bogie.isActivated();
    }

    private boolean hasAnyBogieActivated() {
        return isBogieActivated(0) || isBogieActivated(1);
    }

    private int getActivatedBogieIndex() {
        if (isBogieActivated(0)) return 0;
        return isBogieActivated(1) ? 1 : -1;
    }

    private int getPreferredCouplingBogieIndex(int contactBogieIndex) {
        int activated = getActivatedBogieIndex();
        return activated >= 0 ? activated : Mth.clamp(contactBogieIndex, 0, 1);
    }

    private void clearBogieActivation() {
        for (int i = 0; i < 2; i++) setBogieActivated(i, false);
    }

    private Vec3 getSelectedBogieWorldPosition(int bogieIndex) {
        TrainBogieEntity bogie = resolveBogieHitbox(Mth.clamp(bogieIndex, 0, 1));
        return bogie != null ? bogie.position() : getBogieWorldPosition(bogieIndex);
    }

    private int getCouplerSideForSelectedBogieAgainst(int bogieIndex, TrainEntity other, int otherBogieIndex) {
        Vec3 selected = getSelectedBogieWorldPosition(bogieIndex);
        if (selected != null) {
            double frontDist = selected.distanceToSqr(getCouplerPoint(true));
            double rearDist  = selected.distanceToSqr(getCouplerPoint(false));
            if (Math.abs(frontDist - rearDist) > 0.01) return frontDist <= rearDist ? 1 : -1;
        }
        if (other != null) {
            Vec3 otherSelected = other.getSelectedBogieWorldPosition(otherBogieIndex);
            if (selected != null && otherSelected != null) {
                double frontDist = getCouplerPoint(true).distanceToSqr(otherSelected);
                double rearDist  = getCouplerPoint(false).distanceToSqr(otherSelected);
                if (Math.abs(frontDist - rearDist) > 0.01) return frontDist <= rearDist ? 1 : -1;
            }
        }
        return getCouplerSideForBogieIndex(bogieIndex);
    }

    private boolean areSelectedBogiesTouching(TrainEntity other, int thisBogieIndex, int otherBogieIndex) {
        if (other == null) return false;
        TrainBogieEntity thisBogie  = resolveBogieHitbox(Mth.clamp(thisBogieIndex, 0, 1));
        TrainBogieEntity otherBogie = other.resolveBogieHitbox(Mth.clamp(otherBogieIndex, 0, 1));
        if (thisBogie != null && otherBogie != null) {
            if (thisBogie.getBoundingBox().inflate(0.45, 0.2, 0.45).intersects(otherBogie.getBoundingBox().inflate(0.45, 0.2, 0.45))) return true;
            if (thisBogie.position().distanceToSqr(otherBogie.position()) <= 6.25) return true;
        }
        int thisSide  = getCouplerSideForBogieIndex(thisBogieIndex);
        int otherSide = other.getCouplerSideForBogieIndex(otherBogieIndex);
        return getCouplerDistanceSqr(other, thisSide, otherSide) <= 6.25;
    }

    private boolean canCompleteCouplingWith(TrainEntity other, int thisSide, int otherSide) {
        if (other == null || other == this || isConnectedTo(other)) return false;
        double distance = getDefaultDistanceToConnectedTrain(other);
        return getCouplerDistanceSqr(other, thisSide, otherSide) <= distance * distance
                || getBoundingBox().inflate(distance * 0.5).intersects(other.getBoundingBox().inflate(distance * 0.5));
    }

    private int getExposedHeadSide(TrainEntity headTrain) {
        if (headTrain == null) return 0;
        return headTrain.coupledFollowerUuid == null ? 0 : -normalizeCouplerSide(headTrain.coupledFollowerThisSide);
    }

    private int getExposedTailSide(TrainEntity tailTrain) {
        if (tailTrain == null) return 0;
        int leaderSide = tailTrain.getCouplerSideConnectedToLeader();
        return leaderSide == 0 ? 0 : -leaderSide;
    }

    private CouplingRequest findNearestExposedCouplerPair(TrainEntity other) {
        if (other == null) return null;
        List<CouplerCandidate> first  = getExposedCouplerCandidates();
        List<CouplerCandidate> second = other.getExposedCouplerCandidates();
        CouplingRequest best = null;
        for (CouplerCandidate a : first) {
            Vec3 aPoint = a.train().getCouplerPoint(a.side() > 0);
            for (CouplerCandidate b : second) {
                double d = aPoint.distanceToSqr(b.train().getCouplerPoint(b.side() > 0));
                if (best == null || d < best.distanceSqr()) {
                    best = new CouplingRequest(a.train(), a.side(), b.train(), b.side(), d);
                }
            }
        }
        return best;
    }

    private List<CouplerCandidate> getExposedCouplerCandidates() {
        List<TrainEntity> chain = getFormationTrainsInOrder();
        if (chain.isEmpty()) return List.of();
        if (chain.size() == 1) {
            return List.of(new CouplerCandidate(chain.get(0), -1), new CouplerCandidate(chain.get(0), 1));
        }
        TrainEntity head = chain.get(0);
        TrainEntity tail = chain.get(chain.size() - 1);
        List<CouplerCandidate> result = new ArrayList<>(2);
        int headSide = getExposedHeadSide(head);
        int tailSide = getExposedTailSide(tail);
        if (headSide != 0) result.add(new CouplerCandidate(head, headSide));
        if (tailSide != 0) result.add(new CouplerCandidate(tail, tailSide));
        return result;
    }

    private boolean tryImmediateActivatedBogieCoupling(Player player, int bogieIndex) {
        if (level() == null || level().isClientSide()) return false;
        AABB searchBox = getBoundingBox().inflate(Math.max(6.0, getDefaultDistanceToConnectedTrain(null)));
        for (TrainEntity other : level().getEntitiesOfClass(TrainEntity.class, searchBox)) {
            if (other == this || !other.isAlive()) continue;
            int otherBogieIndex = other.getActivatedBogieIndex();
            if (otherBogieIndex < 0) continue;
            int thisSide  = getCouplerSideForSelectedBogieAgainst(bogieIndex, other, otherBogieIndex);
            int otherSide = other.getCouplerSideForSelectedBogieAgainst(otherBogieIndex, this, bogieIndex);
            double allowed = Math.max(3.25, getDefaultDistanceToConnectedTrain(other) + 0.75);
            if (getCouplerDistanceSqr(other, thisSide, otherSide) > allowed * allowed) continue;
            if (coupleFormationsRtMLike(this, thisSide, other, otherSide) || coupleFormationsRtMLike(other, otherSide, this, thisSide)) {
                clearBogieActivation();
                other.clearBogieActivation();
                notifyCouplingChat(Component.literal("連結しました"), other, player);
                return true;
            }
        }
        return false;
    }

    private boolean tryImmediateSelectedBogieCoupling(Player player, UUID firstTrainUuid, int firstBogieIndex, TrainEntity secondTrain, int secondBogieIndex) {
        if (!(level() instanceof net.minecraft.server.level.ServerLevel serverLevel)) return false;
        if (firstTrainUuid == null || secondTrain == null) return false;
        Entity sourceRaw = serverLevel.getEntity(firstTrainUuid);
        if (!(sourceRaw instanceof TrainEntity source) || !source.isAlive() || source == secondTrain || source.isConnectedTo(secondTrain)) return false;
        int sourceSide = source.getCouplerSideForSelectedBogieAgainst(firstBogieIndex, secondTrain, secondBogieIndex);
        int targetSide = secondTrain.getCouplerSideForSelectedBogieAgainst(secondBogieIndex, source, firstBogieIndex);
        if (!source.coupleFormationsRtMLike(source, sourceSide, secondTrain, targetSide)
                && !secondTrain.coupleFormationsRtMLike(secondTrain, targetSide, source, sourceSide)) return false;
        source.clearBogieActivation();
        secondTrain.clearBogieActivation();
        source.notifyCouplingChat(Component.literal("連結しました"), secondTrain, player);
        return true;
    }

    private void notifyCouplingChat(Component message, TrainEntity otherTrain, Player directPlayer) {
        if (level().isClientSide() || message == null) return;
        java.util.Set<UUID> delivered = new java.util.HashSet<>();
        if (directPlayer instanceof ServerPlayer sp) {
            sp.sendSystemMessage(message);
            delivered.add(sp.getUUID());
        }
        notifyCouplingChatToTrain(message, this, delivered);
        notifyCouplingChatToTrain(message, otherTrain, delivered);
    }

    private void notifyCouplingChatToTrain(Component message, TrainEntity train, java.util.Set<UUID> delivered) {
        if (train == null) return;
        for (Entity passenger : train.getPassengers()) {
            if (passenger instanceof ServerPlayer sp && delivered.add(sp.getUUID())) {
                sp.sendSystemMessage(message);
            }
        }
    }

    public void setNotchForFormation(int notch) {
        if (level().isClientSide()) {
            setNotch(notch);
        } else {
            int clamped = Mth.clamp(notch, -getMaxBrakeNotch(), getMaxPowerNotch());
            forEachFormationTrain(train -> train.setNotch(clamped));
        }
    }

    // ======== End coupling system ========

    public List<TrainEntity> getFormationTrainsForDisplay() {
        List<TrainEntity> result = new ArrayList<>();
        TrainEntity head = this;
        int guard = 0;
        while (guard++ < 16) {
            TrainEntity leader = resolveTrainByUuid(head.getDisplayLeaderUuid());
            if (leader == null || leader == head) {
                break;
            }
            head = leader;
        }

        TrainEntity current = head;
        guard = 0;
        while (current != null && guard++ < 16) {
            result.add(current);
            TrainEntity follower = resolveTrainByUuid(current.getDisplayFollowerUuid());
            if (follower == null || follower == current) {
                break;
            }
            current = follower;
        }
        if (result.isEmpty()) {
            result.add(this);
        }
        return result;
    }

    private UUID getDisplayFollowerUuid() {
        return coupledFollowerUuid != null ? coupledFollowerUuid : entityData.get(COUPLED_FOLLOWER).orElse(null);
    }

    private UUID getDisplayLeaderUuid() {
        return coupledLeaderUuid != null ? coupledLeaderUuid : entityData.get(COUPLED_LEADER).orElse(null);
    }

    private TrainEntity resolveTrainByUuid(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        if (level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            Entity entity = serverLevel.getEntity(uuid);
            return entity instanceof TrainEntity train ? train : null;
        }
        for (TrainEntity train : level().getEntitiesOfClass(TrainEntity.class, getBoundingBox().inflate(256.0D))) {
            if (uuid.equals(train.getUUID())) {
                return train;
            }
        }
        return null;
    }

    public static boolean tryEnterCouplingModeFromPlayerView(ServerPlayer player) {
        if (player == null || player.getVehicle() != null || player.isSecondaryUseActive()) {
            return false;
        }
        BogieViewHit hit = findBogieHitFromPlayerView(player);
        if (hit == null || hit.train() == null) {
            return false;
        }
        hit.train().enterCouplingMode(player);
        return true;
    }

    public static boolean tryRideFromPlayerView(ServerPlayer player) {
        if (player == null || player.getVehicle() != null || player.isSecondaryUseActive()) {
            return false;
        }
        BogieViewHit hit = findBogieHitFromPlayerView(player);
        if (hit == null || hit.train() == null) {
            return false;
        }
        VehicleDefinition def = VehicleRegistry.getById(hit.train().getVehicleId());
        if (def == null) {
            return false;
        }
        int seatIndex = hit.train().resolveSeatIndexForBogieClick(def, hit.bogieIndex());
        if (seatIndex < 0) {
            seatIndex = hit.train().findNearestSeatToLocalClick(def, hit.localHit());
        }
        return hit.train().tryRideWithSeat(player, seatIndex).consumesAction();
    }

    private static BogieViewHit findBogieHitFromPlayerView(ServerPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        double reach = 12.0D;
        AABB searchBox = player.getBoundingBox().expandTowards(look.scale(reach)).inflate(8.0D);
        TrainEntity bestTrain = null;
        Vec3 bestLocalHit = null;
        int bestBogieIndex = -1;
        double bestT = Double.MAX_VALUE;

        for (TrainEntity train : player.level().getEntitiesOfClass(TrainEntity.class, searchBox)) {
            if (!train.isAlive()) {
                continue;
            }
            VehicleDefinition def = VehicleRegistry.getById(train.getVehicleId());
            Vec3 localStart = train.worldToLocal(eye);
            Vec3 localEnd = train.worldToLocal(eye.add(look.scale(reach)));
            List<Vec3> bogies = train.getInteractionBogieCenters(def);
            for (int bogieIndex = 0; bogieIndex < bogies.size(); bogieIndex++) {
                Vec3 bogie = bogies.get(bogieIndex);
                Double t = intersectSegmentAabb(
                    localStart,
                    localEnd,
                    bogie.x - BOGIE_INTERACT_HALF_WIDTH,
                    bogie.y - 0.6D,
                    bogie.z - BOGIE_INTERACT_HALF_LENGTH,
                    bogie.x + BOGIE_INTERACT_HALF_WIDTH,
                    bogie.y + BOGIE_INTERACT_HALF_HEIGHT,
                    bogie.z + BOGIE_INTERACT_HALF_LENGTH
                );
                if (t != null && t < bestT) {
                    bestT = t;
                    bestTrain = train;
                    bestLocalHit = localStart.add(localEnd.subtract(localStart).scale(t));
                    bestBogieIndex = bogieIndex;
                }
            }
        }

        if (bestTrain == null || bestLocalHit == null) {
            return null;
        }
        return new BogieViewHit(bestTrain, bestBogieIndex, bestLocalHit);
    }

    private static Double intersectSegmentAabb(Vec3 start, Vec3 end, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        double tMin = 0.0D;
        double tMax = 1.0D;
        double[] s = {start.x, start.y, start.z};
        double[] d = {end.x - start.x, end.y - start.y, end.z - start.z};
        double[] min = {minX, minY, minZ};
        double[] max = {maxX, maxY, maxZ};
        for (int i = 0; i < 3; i++) {
            if (Math.abs(d[i]) < 1.0E-8D) {
                if (s[i] < min[i] || s[i] > max[i]) {
                    return null;
                }
                continue;
            }
            double inv = 1.0D / d[i];
            double t1 = (min[i] - s[i]) * inv;
            double t2 = (max[i] - s[i]) * inv;
            if (t1 > t2) {
                double tmp = t1;
                t1 = t2;
                t2 = tmp;
            }
            tMin = Math.max(tMin, t1);
            tMax = Math.min(tMax, t2);
            if (tMin > tMax) {
                return null;
            }
        }
        return tMin;
    }

    private Vec3 getBogieLocalPosition(int bogieIndex, VehicleDefinition def) {
        if (def != null && !def.getBogies().isEmpty()) {
            int clamped = Mth.clamp(bogieIndex, 0, def.getBogies().size() - 1);
            return def.getBogies().get(clamped).position();
        }
        double[] bogieZ = getBogieRailOffsets();
        int clamped = Mth.clamp(bogieIndex, 0, 1);
        return new Vec3(0.0D, 0.0D, bogieZ[clamped]);
    }

    private List<Vec3> getInteractionBogieCenters(VehicleDefinition def) {
        if (def != null && !def.getBogies().isEmpty()) {
            List<Vec3> centers = new ArrayList<>(def.getBogies().size());
            for (VehicleDefinition.BogieDefinition bogie : def.getBogies()) {
                centers.add(bogie.position());
            }
            return centers;
        }
        double[] bogieZ = getBogieRailOffsets();
        List<Vec3> fallback = new ArrayList<>(2);
        fallback.add(new Vec3(0.0D, 0.0D, bogieZ[0]));
        fallback.add(new Vec3(0.0D, 0.0D, bogieZ[1]));
        return fallback;
    }

    private Entity getDriverPassenger() {
        syncSeatAssignmentsFromEntityData();
        for (Map.Entry<UUID, Integer> entry : seatAssignments.entrySet()) {
            if (isDriverSeatIndex(entry.getValue())) {
                Entity passenger = findAssignedPassenger(entry.getKey());
                if (passenger != null) {
                    return passenger;
                }
            }
        }
        return null;
    }

    private float getCabDirectionSign(Entity controller) {
        return getCabDirectionSign(controller, this);
    }

    private float getCabDirectionSign(Entity controller, TrainEntity cabTrain) {
        int reverser = getReverser();
        if (reverser == 0) {
            return 0.0F;
        }
        TrainEntity source = cabTrain == null ? this : cabTrain;
        return source.getDriverCabDirection(controller) * reverser;
    }

    private int getDriverCabDirection(Entity controller) {
        VehicleDefinition def = VehicleRegistry.getById(getVehicleId());
        if (controller != null && def != null) {
            Vec3 seatOffset = getAssignedSeatOffset(controller);
            if (Math.abs(seatOffset.z) > 1.0E-4D) {
                return seatOffset.z < 0.0D ? -1 : 1;
            }
            int assignedSeat = getAssignedSeatIndex(controller);
            if (assignedSeat == resolveRearSeatIndex(def)) {
                return -1;
            }
            if (assignedSeat == resolveFrontSeatIndex(def)) {
                return 1;
            }
            List<Vec3> seats = getSelectableSeats(def);
            if (assignedSeat >= 0 && assignedSeat < seats.size()) {
                return seats.get(assignedSeat).z < 0.0D ? -1 : 1;
            }
        }
        return 1;
    }

    public boolean isDriverSeatIndex(int seatIndex) {
        VehicleDefinition def = VehicleRegistry.getById(getVehicleId());
        if (def == null || seatIndex < 0) {
            return false;
        }
        if (def.isDriverSeatIndex(seatIndex)) {
            return true;
        }
        return seatIndex == resolveFrontSeatIndex(def) || seatIndex == resolveRearSeatIndex(def);
    }

    public boolean isDriverPassenger(Entity passenger) {
        if (passenger == null) {
            return false;
        }
        int assignedSeat = getAssignedSeatIndex(passenger);
        return isDriverSeatIndex(assignedSeat);
    }

    public boolean isLikelyDriverPassenger(Entity passenger) {
        if (passenger == null) {
            return false;
        }
        int assignedSeat = getAssignedSeatIndex(passenger);
        if (isDriverSeatIndex(assignedSeat)) {
            return true;
        }
        if (level().isClientSide()) {
            return isDriverSeatIndex(findNearestSeatIndex(passenger));
        }
        return false;
    }

    public void markDriverControl(Entity passenger) {
        if (passenger == null) {
            return;
        }
        this.activeDriverUuid = passenger.getUUID();
        this.activeDriverTicks = 40;
    }

    public void ensureDriverReady(Entity passenger) {
        if (passenger == null) {
            return;
        }
        if (getReverser() != 0) {
            return;
        }
        VehicleDefinition def = VehicleRegistry.getById(getVehicleId());
        int seatIndex = getAssignedSeatIndex(passenger);
        if (seatIndex >= 0) {
            setReverser(getDefaultReverserForSeat(def, seatIndex));
            return;
        }
        setReverser(1);
    }

    public void applyThrottle(float throttle) {
        float speed = Mth.clamp(getSpeed() + throttle * ACCEL, -MAX_SPEED, MAX_SPEED);
        setSpeed(speed);
    }

    public void stepMascon(int delta) {
        if (delta > 0) {
            setNotch(Math.min(getMaxPowerNotch(), getNotch() + delta));
        } else if (delta < 0) {
            setNotch(Math.max(-getMaxBrakeNotch(), getNotch() + delta));
        }
    }

    private static int getMaxPowerNotch(VehicleDefinition def) {
        if (def != null && !def.getNotchMaxSpeeds().isEmpty()) {
            return Mth.clamp(def.getNotchMaxSpeeds().size(), 1, 12);
        }
        return MAX_POWER_NOTCH;
    }

    public void forceDiscardTrain() {
        if (level().isClientSide()) {
            return;
        }
        if (level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            AABB cleanupBox = getBoundingBox().inflate(8.0D, 6.0D, 8.0D);
            for (TrainEntity train : serverLevel.getEntitiesOfClass(TrainEntity.class, cleanupBox, other ->
                other != null && (other == this || other.getUUID().equals(this.getUUID())))) {
                train.ejectPassengers();
                train.discardBogieHitboxes();
                train.discardSeatHitboxes();
                train.seatAssignments.clear();
                train.syncSeatAssignmentsToEntityData();
                train.setSpeed(0.0F);
                train.setNotch(0);
                train.activeRailMap = null;
                train.frontRailAnchor = null;
                train.rearRailAnchor = null;
                train.activeRailIndex = -1;
                train.activeRailPosition = -1.0D;
                train.discard();
            }
            purgeDanglingTrainResidue(serverLevel, cleanupBox);
        }
        ejectPassengers();
        decouple();
        discardBogieHitboxes();
        discardSeatHitboxes();
        seatAssignments.clear();
        syncSeatAssignmentsToEntityData();
        setSpeed(0.0F);
        setNotch(0);
        remove(RemovalReason.DISCARDED);
    }

    public static void purgeDanglingTrainResidue(net.minecraft.server.level.ServerLevel serverLevel, AABB bounds) {
        if (serverLevel == null || bounds == null) {
            return;
        }
        for (TrainBogieEntity bogie : serverLevel.getEntitiesOfClass(TrainBogieEntity.class, bounds.inflate(2.0D))) {
            TrainEntity train = bogie.getTrain();
            if (train == null || !train.isAlive() || train.isRemoved()) {
                bogie.discard();
            }
        }
        for (TrainSeatEntity seat : serverLevel.getEntitiesOfClass(TrainSeatEntity.class, bounds.inflate(2.0D))) {
            TrainEntity train = seat.getTrain();
            if (train == null || !train.isAlive() || train.isRemoved()) {
                seat.ejectPassengers();
                seat.discard();
            }
        }
    }

    public static void clearCouplingModes() {
        COUPLING_MODE.clear();
    }

    /** 連結成立時に両編成の連結モードを解除する。 */
    private static void clearCouplingModeInvolving(TrainEntity a, TrainEntity b) {
        if (COUPLING_MODE.isEmpty()) {
            return;
        }
        java.util.Set<UUID> involved = new java.util.HashSet<>();
        for (TrainEntity root : new TrainEntity[]{a, b}) {
            if (root == null) {
                continue;
            }
            root.forEachFormationTrain(t -> {
                if (t != null) {
                    involved.add(t.getUUID());
                }
            });
            involved.add(root.getUUID());
        }
        COUPLING_MODE.entrySet().removeIf(e -> {
            CouplingSelection s = e.getValue();
            return s != null && (involved.contains(s.first()) || involved.contains(s.second()));
        });
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        Entity attacker = source.getEntity();
        if (attacker instanceof Player player) {
            boolean hasCrowbar = player.getMainHandItem().is(RealTrainModUnofficialItems.CROWBAR_ITEM.get())
                || player.getOffhandItem().is(RealTrainModUnofficialItems.CROWBAR_ITEM.get());
            if (hasCrowbar) {
                forceDiscardTrain();
                return true;
            }
        }
        return false;
    }

    @Override
    public Vec3 getPassengerRidingPosition(Entity passenger) {
        Vec3 seat = getAssignedSeatOffset(passenger);
        return localToWorld(seat);
    }

    public Vec3 getSeatWorldPosition(int seatIndex) {
        return localToWorld(getSeatOffset(seatIndex));
    }

    public float getSeatWorldYaw(int seatIndex) {
        VehicleDefinition def = VehicleRegistry.getById(getVehicleId());
        if (isDriverSeatIndex(seatIndex)) {
            int cabDirection = getDriverCabDirectionBySeatIndex(seatIndex, def);
            return cabDirection >= 0 ? getYRot() : getYRot() + 180.0F;
        }
        return getYRot();
    }

    @Override
    public Vec3 getVehicleAttachmentPoint(Entity passenger) {
        return localToWorld(getAssignedSeatOffset(passenger));
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        pruneSeatAssignments();
        VehicleDefinition def = VehicleRegistry.getById(getVehicleId());
        int seatCount = Math.max(1, getSeatCount(def));
        return seatAssignments.size() < seatCount;
    }

    // ---- 既存スクリプト向けAPI ----
    public float getTrainStateData(int stateType) {
        return getVehicleState(stateType);
    }

    public void setTrainStateData(int stateType, float value) {
        syncVehicleState(stateType, value);
    }

    public float getVehicleState(int stateType) {
        return switch (stateType) {
            case 0 -> getReverser() >= 0 ? 0.0F : 1.0F;
            case 1 -> getNotch();
            case 2 -> getRailProgress();
            case 3 -> 0.0F;
            case 4 -> (isDoorRightOpen() ? 1.0F : 0.0F) + (isDoorLeftOpen() ? 2.0F : 0.0F);
            case 5 -> toLegacyLightMode(getLightMode());
            case 6 -> isPantographUp() ? 1.0F : 0.0F;
            case 7 -> Math.abs(getSpeed()) * 72.0F;  // speed in km/h (RTM compat)
            case 8 -> getDestinationIndex();
            case 9 -> getSoundIndex();
            case 10 -> 1.0F - getReverser();
            case 11 -> getInteriorLightMode();
            default -> 0.0F;
        };
    }

    private float toLegacyLightMode(int mode) {
        return switch (mode) {
            case 1 -> 1.0F;
            case 2 -> 2.0F;
            case 3 -> 3.0F;
            default -> 0.0F;
        };
    }

    private float getInteriorLightMode() {
        return isInteriorLightOn() ? 1.0F : 0.0F;
    }

    /** 前照灯/尾灯の出し分け。0=進行方向の先頭/1=それ以外。 */
    public float getTrainDirection() {
        return isLeadingCar() ? 0.0F : 1.0F;
    }

    /** 進行方向側に連結相手がいない=編成の先頭か。 */
    public boolean isLeadingCar() {
        updateFormationPositionCache();
        return leadingCarCache;
    }

    /** 進行方向と逆側に連結相手がいない=編成の最後尾か。 */
    public boolean isTrailingCar() {
        updateFormationPositionCache();
        return trailingCarCache;
    }

    /** 描画は毎フレーム走るので、編成の走査は 1 tick に 1 回で足りる。 */
    private int formationPositionCacheTick = -1;
    private boolean leadingCarCache = true;
    private boolean trailingCarCache = true;

    private void updateFormationPositionCache() {
        if (tickCount == formationPositionCacheTick) {
            return;
        }
        formationPositionCacheTick = tickCount;
        List<TrainEntity> formation = getFormationTrainsForDisplay();
        if (formation.size() <= 1) {
            // 単行は先頭かつ最後尾
            leadingCarCache = true;
            trailingCarCache = true;
            return;
        }
        // 連結リストの先頭 (index 0) が前進時の先頭。後進時は末尾が先頭になる。
        boolean forward = getReverser() >= 0;
        TrainEntity leading = forward ? formation.get(0) : formation.get(formation.size() - 1);
        TrainEntity trailing = forward ? formation.get(formation.size() - 1) : formation.get(0);
        leadingCarCache = this == leading;
        trailingCarCache = this == trailing;
    }

    // 旧 RTM の Render スクリプトは entity.getRotation で車体 yaw を取得する。
    public float getRotation() {
        return getYRot();
    }

    public float getDir() {
        return getTrainDirection();
    }

    /** 進行方向そのもの(0=前進/1=後進)。レバーサー基準。 */
    public float getMoveDir() {
        return getReverser() >= 0 ? 0.0F : 1.0F;
    }

    public TrainEntity getConnectedTrain(int dir) {
        return dir == 0 ? getCoupledLeader() : getCoupledFollower();
    }

    public float getCouplerYaw(int index) {
        return 0.0F;
    }

    public int getRollsignAnimation() {
        return getDestinationIndex();
    }

    public void syncNotch(int notch) {
        setNotch(notch);
    }

    public void syncVehicleState(int stateType, float value) {
        switch (stateType) {
            case 0 -> setReverser(value < 0.5F ? 1 : -1);
            case 1 -> setNotch(Math.round(value));
            case 2 -> setRailProgress(value);
            case 4 -> {
                int door = Math.round(value);
                setDoorRightOpen((door & 1) != 0);
                setDoorLeftOpen((door & 2) != 0);
            }
            case 5 -> setLightMode(Math.round(value));
            case 6 -> setPantographUp(value > 0.5F);
            case 8 -> setDestinationIndex(Math.max(0, Math.round(value)));
            case 9 -> setSoundIndex(Math.max(0, Math.round(value)));
            case 11 -> setInteriorLightOn(value > 0.0F);
            default -> {
            }
        }
    }

    public float getSeatRotation() {
        return Mth.clamp(seatRotation / 45.0F, -1.0F, 1.0F);
    }

    /** seatRotationの生値(-45〜45)。 */
    public float getSeatRotationRaw() {
        return seatRotation;
    }

    public FormationCompat getFormation() {
        return new FormationCompat(this);
    }

    public int func_145782_y() {
        return getId();
    }

    public int func_70070_b() {
        if (level() == null) {
            return 0;
        }
        try {
            BlockPos bodyPos = BlockPos.containing(getX(), getY() + 1.5D, getZ());
            int block = level().getBrightness(LightLayer.BLOCK, bodyPos);
            int sky = level().getBrightness(LightLayer.SKY, bodyPos);
            return (block << 4) | (sky << 20);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    public int func_70070_b(int ignored) {
        return func_70070_b();
    }

    // ---- 追加互換API（等で使用） ----
    public Entity func_184207_aI() {
        Entity driver = getDriverPassenger();
        if (driver != null) {
            return driver;
        }
        return getPassengers().isEmpty() ? null : getPassengers().get(0);
    }

    public int getSignal() {
        return 0;
    }

    public boolean isControlCar() {
        return true;
    }

    public Object getModelSet() {
        return new ModelSetCompat(getVehicleId());
    }

    public ResourceStateCompat getResourceState() {
        return new ResourceStateCompat(this);
    }

    public BogieCompat getBogie(int index) {
        return new BogieCompat(this, scriptBogieIndexToDefinitionIndex(index));
    }

    /** 台車モデルが.classの車両か。台車はレール追従描画し車体側のbogie描画を抑制。 */
    public boolean usesReplacementBogies() {
        VehicleDefinition def = VehicleRegistry.getById(getVehicleId());
        if (def == null) {
            return false;
        }
        for (VehicleDefinition.BogieDefinition b : def.getBogies()) {
            String m = b.modelFile();
            if (m != null && m.toLowerCase(java.util.Locale.ROOT).endsWith(".class")) {
                return true;
            }
        }
        return false;
    }

    public int scriptBogieIndexToDefinitionIndex(int scriptIndex) {
        VehicleDefinition def = VehicleRegistry.getById(getVehicleId());
        if (def == null || def.getBogies().isEmpty()) {
            return Mth.clamp(scriptIndex, 0, 1);
        }
        int[] extremes = getExtremeBogieIndices(def);
        return scriptIndex == 0 ? extremes[1] : extremes[0];
    }

    public static final class ResourceStateCompat {
        private final TrainEntity train;

        public ResourceStateCompat(TrainEntity train) {
            this.train = train;
        }

        /** Returns a typed map view used by old render scripts. */
        public DataMapCompat getDataMap() {
            return new DataMapCompat(train);
        }

        /** Returns the vehicle id for scripts that compare resource names. */
        public String getResourceName() {
            return train.getVehicleId();
        }

        // 旧 RTM スクリプトは getName で列車名(カスタムネーム)を取得し、substring(0,4) 等で
        // 列車番号を切り出す。少なくとも String が返れば pck_customNames は通る。
        public String getName() {
            String id = train.getVehicleId();
            return id == null ? "" : id;
        }

        /** Returns a config holder compatible with old render scripts. */
        public ModelSetCompat getResourceSet() {
            return new ModelSetCompat(train.getVehicleId());
        }

        public void addExclusionParts(Object... parts) {}
        public void removeExclusionParts(Object... parts) {}
    }

    public static final class DataMapCompat {
        private final TrainEntity train;
        private final Map<String, Object> values = new HashMap<>();

        /** Creates a script-visible data map for a train. */
        public DataMapCompat(TrainEntity train) {
            this.train = train;
            refresh();
        }

        /** Returns a raw value by key. */
        public Object get(String key) {
            refresh();
            Object value = values.get(key);
            if (value != null) {
                return value;
            }
            return train == null ? null : train.scriptData.get(key);
        }

        public boolean contains(String key) {
            refresh();
            return values.containsKey(key) || train != null && train.scriptData.containsKey(key);
        }

        /** Returns an integer value by key. */
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
                    return Integer.parseInt(string);
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

        /** Returns a boolean value by key. */
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

        /** Stores a boolean value for the current script frame. */
        public void setBoolean(String key, boolean value, int syncType) {
            values.put(key, value);
            if (train != null) {
                train.scriptData.put(key, Boolean.toString(value));
                if (syncType != 0) train.scriptDataDirty = true;
            }
        }

        /** Stores an integer value for the current script frame. */
        public void setInt(String key, int value, int syncType) {
            values.put(key, value);
            if (train != null) {
                train.scriptData.put(key, Integer.toString(value));
                if (syncType != 0) train.scriptDataDirty = true;
            }
        }

        public void setString(String key, String value, int syncType) {
            String safeValue = value == null ? "" : value;
            values.put(key, safeValue);
            if (train != null) {
                train.scriptData.put(key, safeValue);
                if (syncType != 0) train.scriptDataDirty = true;
            }
        }

        public void setDouble(String key, double value, int syncType) {
            values.put(key, value);
            if (train != null) {
                train.scriptData.put(key, Double.toString(value));
                if (syncType != 0) train.scriptDataDirty = true;
            }
        }

        private void refresh() {
            if (train == null) {
                return;
            }
            values.put("headLight", train.isHeadlightOn() ? 1 : 0);
            values.put("door", train.isDoorOpen() ? 1 : 0);
            values.put("doorLeft", train.isDoorLeftOpen() ? 1 : 0);
            values.put("doorRight", train.isDoorRightOpen() ? 1 : 0);
            values.put("lightMode", train.getLightMode());
            values.put("pantograph", train.isPantographUp() ? 1 : 0);
            values.put("destination", train.getDestinationIndex());
            values.put("sound", train.getSoundIndex());
            values.put("reverse", train.getReverser() < 0 ? 1 : 0);
            values.put("reverser", train.getReverser());
            values.put("customButtons", train.getCustomButtonBits());
            values.put("railProgress", train.getRailProgress());
            values.put("connected", train.isConnected() ? 1 : 0);
            values.put("carNumber", defaultCarNumber(train));
            values.put("prevFormationSize", defaultFormationSize(train));
            values.put("isFormationA", false);
            values.put("isFormationB", false);
            values.put("isFormationError", false);
            values.putIfAbsent("prevRollsignId", train.getDestinationIndex());
            for (int i = 0; i < 16; i++) {
                int state = train.getCustomButtonValue(i);
                values.put("Button" + i, state);
                values.put("button" + i, state);
                values.put("CustomButton" + i, state);
                values.put("customButton" + i, state);
            }
            train.scriptData.forEach(values::put);
        }

        private static int defaultCarNumber(TrainEntity train) {
            if (train == null) {
                return 1;
            }
            try {
                List<TrainEntity> formationTrains = train.getFormationTrainsForDisplay();
                int index = formationTrains.indexOf(train);
                if (index >= 0) {
                    return index + 1;
                }
            } catch (RuntimeException ignored) {
            }
            return 1;
        }

        private static int defaultFormationSize(TrainEntity train) {
            if (train == null) {
                return 1;
            }
            try {
                return Math.max(1, train.getFormationTrainsForDisplay().size());
            } catch (RuntimeException ignored) {
                return 1;
            }
        }
    }

    private record CouplingSelection(UUID first, int firstBogieIndex, UUID second, int secondBogieIndex, long armedAt) {
        private boolean isComplete() {
            return first != null && second != null && firstBogieIndex >= 0 && secondBogieIndex >= 0;
        }
    }

    private record CouplerPair(int thisSide, int otherSide, double distanceSqr) {
    }

    private record CouplerCandidate(TrainEntity train, int side) {}
    private record CouplingEndpoint(TrainEntity train, int side) {}
    private record CouplingLink(TrainEntity leader, TrainEntity follower, int leaderSide, int followerSide) {}
    private record CouplingRequest(TrainEntity sourceTrain, int sourceSide, TrainEntity targetTrain, int targetSide, double distanceSqr) {}

    public static final class FormationCompat {
        private final TrainEntity train;

        /** Creates a script-visible formation view. */
        public FormationCompat(TrainEntity train) {
            this.train = train;
        }

        /** Returns the number of cars visible to this script. */
        public int size() {
            return scriptFormationSize();
        }

        /** Returns a formation entry by index. */
        public FormationEntryCompat get(int index) {
            List<TrainEntity> trains = train.getFormationTrainsForDisplay();
            if (index < 0 || index >= trains.size()) {
                return null;
            }
            TrainEntity entryTrain = trains.get(index);
            int dir = 0;
            if (train.formation != null) {
                com.portofino.realtrainmodunofficial.entity.legacyformation.FormationEntry entry = train.formation.getEntry(entryTrain);
                if (entry != null) {
                    dir = entry.dir;
                }
            }
            return new FormationEntryCompat(index, entryTrain, dir);
        }

        /** 編成エントリを返す。引数はObject受け(JSラッパー対応)。 */
        public FormationEntryCompat getEntry(Object entity) {
            TrainEntity resolved = train;
            if (entity instanceof TrainEntity t) {
                resolved = t;
            } else if (entity != null) {
                try {
                    Object r = entity.getClass().getMethod("getTrain").invoke(entity);
                    if (r instanceof TrainEntity t) {
                        resolved = t;
                    }
                } catch (Exception ignored) {
                }
            }
            // 号車位置は表示チェーン順を唯一のソースにする(isMiddleCarと整合)
            List<TrainEntity> trains = train.getFormationTrainsForDisplay();
            int index = trains.indexOf(resolved);
            int dir = 0;
            if (train.formation != null) {
                com.portofino.realtrainmodunofficial.entity.legacyformation.FormationEntry entry = train.formation.getEntry(resolved);
                if (entry != null) {
                    dir = entry.dir;
                }
            }
            return new FormationEntryCompat(index >= 0 ? index : 0, resolved, dir);
        }

        public int getNotch() {
            TrainEntity front = frontTrain();
            return front != null ? front.getNotch() : train.getNotch();
        }

        public float getSpeed() {
            TrainEntity front = frontTrain();
            return front != null ? front.getSpeed() : train.getSpeed();
        }

        public byte getDirection() {
            return train.formation != null ? train.formation.getDirection() : 0;
        }

        private TrainEntity frontTrain() {
            if (train.formation != null) {
                com.portofino.realtrainmodunofficial.entity.legacyformation.FormationEntry front = train.formation.getFrontEntry();
                if (front != null && front.train != null) {
                    return front.train;
                }
            }
            List<TrainEntity> trains = train.getFormationTrainsForDisplay();
            return trains.isEmpty() ? train : trains.get(0);
        }

        private int scriptFormationSize() {
            // 連結中の実両数(表示チェーン順)をそのまま返す。getEntry.entryId と同じソース。
            return train.getFormationTrainsForDisplay().size();
        }

        /** Placeholder for old packet refresh calls. */
        public void sendPacket() {
        }
    }

    public static final class FormationEntryCompat {
        public final int entryId;
        public final TrainEntity train;
        public final int dir;

        /** Creates a script-visible formation entry. */
        public FormationEntryCompat(int entryId, TrainEntity train) {
            this(entryId, train, 0);
        }

        public FormationEntryCompat(int entryId, TrainEntity train, int dir) {
            this.entryId = entryId;
            this.train = train;
            this.dir = dir;
        }
    }

    public static final class BogieCompat {
        private final TrainEntity train;
        private final int index;
        public final float field_70177_z;

        public BogieCompat() {
            this(null, 0);
        }

        public BogieCompat(TrainEntity train, int index) {
            this.train = train;
            this.index = index;
            this.field_70177_z = train != null ? train.getScriptBogieWorldYaw(index) : 0.0F;
        }

        public float getRotation() {
            if (train == null) {
                return 0.0F;
            }
            return train.getScriptBogieWorldYaw(index);
        }

        public float getYaw() {
            return getRotation();
        }

        public float getPitch() {
            return train != null ? train.getXRot() : 0.0F;
        }
    }

    public static final class ModelSetCompat {
        private final String id;
        private final VehicleDefinition definition;

        public ModelSetCompat(String id) {
            this.id = id;
            this.definition = VehicleRegistry.getById(id);
        }

        public String getName() {
            return id;
        }

        public String getModelName() {
            return id;
        }

        public String getTextureName() {
            return id;
        }

        /** Returns a minimal config object used by old render scripts. */
        public ConfigCompat getConfig() {
            return new ConfigCompat(definition);
        }
    }

    public static final class ConfigCompat {
        private static final String[] DEFAULT_ROLLSIGN_NAMES = {
            "None",
            "Out of service",
            "Test run",
            "Party",
            "Extra",
            "Local",
            "Rapid",
            "Express"
        };
        // Legacy cab scripts inspect the array length to know the brake notch count.
        public final float[] deccelerations = {
            0.0F, 0.1F, 0.2F, 0.35F, 0.5F, 0.7F, 0.9F, 1.1F, 1.3F
        };
        // maxSpeedはノッチ別上限の配列として読まれる。非nullで1要素以上必須
        public final float[] maxSpeed = {
            0.0F, 0.22F, 0.44F, 0.66F, 0.88F, 1.1F
        };
        public final String[] rollsignNames;
        // Legacy cab/server scripts read sound_Announcement as a 2D array [[name, soundPath], ...].
        public final String[][] sound_Announcement;
        public final boolean isSingleTrain;
        private final String name;

        public ConfigCompat() {
            this(null, null);
        }

        public ConfigCompat(String name) {
            this(name, null);
        }

        public ConfigCompat(VehicleDefinition definition) {
            this(definition != null ? definition.getId() : null, definition);
        }

        private ConfigCompat(String name, VehicleDefinition definition) {
            this.name = name != null ? name : "";
            if (definition != null && !definition.getRollsignNames().isEmpty()) {
                this.rollsignNames = definition.getRollsignNames().toArray(String[]::new);
            } else {
                this.rollsignNames = DEFAULT_ROLLSIGN_NAMES;
            }
            this.isSingleTrain = definition != null && definition.isSingleTrain();
            java.util.List<String> sounds = definition != null ? definition.getAnnouncementSounds() : java.util.List.of();
            if (sounds == null || sounds.isEmpty()) {
                this.sound_Announcement = new String[0][];
            } else {
                String[][] arr = new String[sounds.size()][];
                for (int i = 0; i < sounds.size(); i++) {
                    String s = sounds.get(i);
                    arr[i] = new String[] { s, s };
                }
                this.sound_Announcement = arr;
            }
        }

        public String getName() { return name; }
        public String getId() { return name; }
        public String getTrainName() { return name; }
    }

    public static final class WorldCompat {
        private final TrainEntity train;
        public boolean field_72995_K;

        /** Creates a script-visible world view. */
        public WorldCompat(TrainEntity train) {
            this.train = train;
        }

        /** Returns whether the current level is client-side. */
        public boolean isClientSide() {
            field_72995_K = train != null && train.level().isClientSide();
            return field_72995_K;
        }

        /** 1.7.10互換: World.loadedEntityList。現在の全エンティティを返す。 */
        public java.util.List<net.minecraft.world.entity.Entity> field_72996_f() {
            java.util.ArrayList<net.minecraft.world.entity.Entity> out = new java.util.ArrayList<>();
            if (train == null) return out;
            net.minecraft.world.level.Level level = train.level();
            if (level instanceof net.minecraft.server.level.ServerLevel server) {
                for (net.minecraft.world.entity.Entity e : server.getAllEntities()) {
                    out.add(e);
                }
            } else if (level instanceof net.minecraft.client.multiplayer.ClientLevel client) {
                for (net.minecraft.world.entity.Entity e : client.entitiesForRendering()) {
                    out.add(e);
                }
            }
            return out;
        }

        /** field 風アクセス対応のためのキャッシュ getter (JS の `world.field_72996_f` でも動くように)。 */
        public java.util.List<net.minecraft.world.entity.Entity> getField_72996_f() {
            return field_72996_f();
        }
    }

    @Override
    public boolean hasIndirectPassenger(Entity passenger) {
        if (super.hasIndirectPassenger(passenger)) {
            return true;
        }
        if (passenger == this) {
            return true;
        }
        return coupledFollowerUuid != null && passenger.getUUID().equals(coupledFollowerUuid);
    }

    @Override
    public void onPassengerTurned(Entity passenger) {
        super.onPassengerTurned(passenger);
        if (!hasPassenger(passenger)) {
            seatAssignments.remove(passenger.getUUID());
            syncSeatAssignmentsToEntityData();
        }
    }

    public double getPassengersRidingOffset() {
        return 0.0;
    }

    private Vec3 getSeatOffset(int index) {
        VehicleDefinition def = VehicleRegistry.getById(getVehicleId());
        if (def == null) return new Vec3(0.0, 1.2, 0.0);

        List<Vec3> seats = getSelectableSeats(def);
        if (!seats.isEmpty()) {
            if (index >= 0 && index < seats.size()) {
                return seats.get(index);
            }
            return seats.get(0);
        }

        if (def.hasSeatOffset()) {
            return def.getSeatOffset();
        }

        return new Vec3(0.0, 1.2, 0.0);
    }

    private Vec3 getAssignedSeatOffset(Entity passenger) {
        int index = getAssignedSeatIndex(passenger);
        return getSeatOffset(index);
    }

    private int getAssignedSeatIndex(Entity passenger) {
        syncSeatAssignmentsFromEntityData();
        UUID id = passenger.getUUID();
        VehicleDefinition def = VehicleRegistry.getById(getVehicleId());
        int seatCount = getSeatCount(def);
        Integer assignedIndex = seatAssignments.get(id);
        if (assignedIndex != null && assignedIndex >= 0 && assignedIndex < seatCount) {
            return assignedIndex;
        }
        if (level().isClientSide()) {
            return findNearestSeatIndex(passenger);
        }
        return assignSeatIndex(passenger, findNearestSeatIndex(passenger));
    }

    private int getSeatCount(VehicleDefinition def) {
        if (def == null) return 0;
        int count = getSelectableSeats(def).size();
        if (count == 0 && def.hasSeatOffset()) {
            count = 1;
        }
        return count;
    }

    private int assignSeatIndex(Entity passenger, int desiredIndex) {
        pruneSeatAssignments();
        VehicleDefinition def = VehicleRegistry.getById(getVehicleId());
        if (def == null) return 0;

        int seatCount = getSeatCount(def);
        if (seatCount <= 0) {
            seatAssignments.put(passenger.getUUID(), 0);
            syncSeatAssignmentsToEntityData();
            return 0;
        }

        if (desiredIndex >= 0 && desiredIndex < seatCount && !isSeatTakenByOther(passenger, desiredIndex)) {
            seatAssignments.put(passenger.getUUID(), desiredIndex);
            syncSeatAssignmentsToEntityData();
            return desiredIndex;
        }

        for (int i = 0; i < seatCount; i++) {
            if (!isSeatTakenByOther(passenger, i)) {
                seatAssignments.put(passenger.getUUID(), i);
                syncSeatAssignmentsToEntityData();
                return i;
            }
        }

        int fallback = Math.max(0, Math.min(desiredIndex, seatCount - 1));
        seatAssignments.put(passenger.getUUID(), fallback);
        syncSeatAssignmentsToEntityData();
        return fallback;
    }

    public boolean hasAssignedSeat(UUID passengerId) {
        if (passengerId == null) {
            return false;
        }
        syncSeatAssignmentsFromEntityData();
        return seatAssignments.containsKey(passengerId);
    }

    public boolean formationHasAssignedSeat(UUID passengerId) {
        if (passengerId == null) {
            return false;
        }
        final boolean[] found = {false};
        forEachFormationTrain(train -> {
            if (!found[0] && train.hasAssignedSeat(passengerId)) {
                found[0] = true;
            }
        });
        return found[0];
    }

    public Entity findAssignedPassenger(UUID passengerId) {
        if (passengerId == null) {
            return null;
        }
        for (Entity passenger : getPassengers()) {
            if (passengerId.equals(passenger.getUUID())) {
                return passenger;
            }
        }
        if (level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            Entity player = serverLevel.getEntity(passengerId);
            if (player != null && hasAssignedSeat(passengerId)) {
                return player;
            }
        }
        return null;
    }

    private Entity getFirstAssignedPassenger() {
        syncSeatAssignmentsFromEntityData();
        for (UUID passengerId : seatAssignments.keySet()) {
            Entity passenger = findAssignedPassenger(passengerId);
            if (passenger != null) {
                return passenger;
            }
        }
        return null;
    }

    public void clearSeatAssignment(UUID passengerId) {
        if (passengerId == null) {
            return;
        }
        syncSeatAssignmentsFromEntityData();
        if (seatAssignments.remove(passengerId) != null) {
            syncSeatAssignmentsToEntityData();
        }
        if (passengerId.equals(activeDriverUuid)) {
            activeDriverUuid = null;
            activeDriverTicks = 0;
        }
    }

    private void syncSeatAssignmentsToEntityData() {
        if (level().isClientSide()) {
            return;
        }
        StringBuilder data = new StringBuilder();
        seatAssignments.forEach((uuid, seatIndex) -> {
            if (!data.isEmpty()) {
                data.append(';');
            }
            data.append(uuid).append('=').append(seatIndex);
        });
        setSeatAssignmentsData(data.toString());
    }

    private void syncSeatAssignmentsFromEntityData() {
        String data = getSeatAssignmentsData();
        if (data == null || data.isBlank()) {
            return;
        }
        String[] entries = data.split(";");
        for (String entry : entries) {
            int sep = entry.indexOf('=');
            if (sep <= 0 || sep >= entry.length() - 1) {
                continue;
            }
            try {
                UUID uuid = UUID.fromString(entry.substring(0, sep));
                int seatIndex = Integer.parseInt(entry.substring(sep + 1));
                seatAssignments.put(uuid, seatIndex);
            } catch (Exception ignored) {
            }
        }
    }

    private boolean isSeatTakenByOther(Entity passenger, int seatIndex) {
        pruneSeatAssignments();
        UUID passengerId = passenger.getUUID();
        for (Map.Entry<UUID, Integer> entry : seatAssignments.entrySet()) {
            if (entry.getValue() == seatIndex && !entry.getKey().equals(passengerId)) {
                return true;
            }
        }
        return false;
    }

    private void pruneSeatAssignments() {
        if (seatAssignments.isEmpty()) {
            return;
        }
        seatAssignments.keySet().removeIf(uuid -> findAssignedPassenger(uuid) == null);
    }

    private int findNearestSeatIndex(Entity passenger) {
        VehicleDefinition def = VehicleRegistry.getById(getVehicleId());
        if (def == null) return -1;

        var seats = getSelectableSeats(def);
        if (seats.isEmpty()) {
            return def.hasSeatOffset() ? 0 : -1;
        }

        // プレイヤーの位置から最も近い座席を返す
        Vec3 target = passenger.position();
        int bestIndex = -1;
        double bestDistance = Double.MAX_VALUE;
        for (int i = 0; i < seats.size(); i++) {
            Vec3 seatPoint = localToWorld(seats.get(i));
            double distance = seatPoint.distanceToSqr(target);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    private List<VehicleDefinition.SeatMarker> getSelectableSeatMarkers(VehicleDefinition def) {
        if (def == null) {
            return List.of();
        }
        return def.getRideableSeatMarkers();
    }

    private List<Vec3> getSelectableSeats(VehicleDefinition def) {
        if (def == null) {
            return List.of();
        }
        return def.getRideableSeatPositions();
    }

    private int resolveFrontSeatIndex(VehicleDefinition def) {
        List<VehicleDefinition.SeatMarker> seatMarkers = getSelectableSeatMarkers(def);
        if (seatMarkers.isEmpty()) {
            return def != null && def.hasSeatOffset() ? 0 : -1;
        }

        int configured = def != null ? def.getFrontDriverSeatIndex() : -1;
        if (configured >= 0 && configured < seatMarkers.size()) {
            return configured;
        }

        int fallbackDriver = def != null ? def.getDriverSeatIndex() : -1;
        if (fallbackDriver >= 0 && fallbackDriver < seatMarkers.size()) {
            return fallbackDriver;
        }

        int driverSeat = findExtremeDriverSeatIndexByZ(seatMarkers, true);
        if (driverSeat >= 0) {
            return driverSeat;
        }

        return findExtremeSeatIndexByZ(getSelectableSeats(def), true);
    }

    private int resolveRearSeatIndex(VehicleDefinition def) {
        List<VehicleDefinition.SeatMarker> seatMarkers = getSelectableSeatMarkers(def);
        if (seatMarkers.isEmpty()) {
            return def != null && def.hasSeatOffset() ? 0 : -1;
        }

        int configured = def != null ? def.getRearDriverSeatIndex() : -1;
        if (configured >= 0 && configured < seatMarkers.size()) {
            return configured;
        }

        int driverSeat = findExtremeDriverSeatIndexByZ(seatMarkers, false);
        if (driverSeat >= 0) {
            return driverSeat;
        }

        return findExtremeSeatIndexByZ(getSelectableSeats(def), false);
    }

    private int findExtremeSeatIndexByZ(List<Vec3> seats, boolean front) {
        if (seats == null || seats.isEmpty()) {
            return -1;
        }

        int bestIndex = 0;
        double bestZ = seats.get(0).z;
        for (int i = 1; i < seats.size(); i++) {
            double z = seats.get(i).z;
            if (front ? z > bestZ : z < bestZ) {
                bestZ = z;
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    private int findExtremeDriverSeatIndexByZ(List<VehicleDefinition.SeatMarker> seats, boolean front) {
        if (seats == null || seats.isEmpty()) {
            return -1;
        }

        int bestIndex = -1;
        double bestZ = 0.0D;
        for (int i = 0; i < seats.size(); i++) {
            VehicleDefinition.SeatMarker seat = seats.get(i);
            if (!seat.driverCab()) {
                continue;
            }
            double z = seat.position().z;
            if (bestIndex < 0 || (front ? z > bestZ : z < bestZ)) {
                bestZ = z;
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    private int findSeatByClickPosition(Player player, Vec3 clickOffsetWorld) {
        VehicleDefinition def = VehicleRegistry.getById(getVehicleId());
        if (def == null) {
            return findNearestSeatIndex(player);
        }

        int byBogie = findSeatByClickedBogie(def, clickOffsetWorld);
        if (byBogie >= 0) {
            return byBogie;
        }

        int fallback = findNearestSeatToLocalClick(def, worldToLocal(position().add(clickOffsetWorld)));
        return fallback;
    }

    private int findNearestSeatToLocalClick(VehicleDefinition def, Vec3 localClick) {
        List<Vec3> seats = getSelectableSeats(def);
        if (seats.isEmpty()) {
            return def != null && def.hasSeatOffset() ? 0 : -1;
        }

        int bestIndex = 0;
        double bestScore = Double.MAX_VALUE;
        for (int i = 0; i < seats.size(); i++) {
            Vec3 seat = seats.get(i);
            double dx = Math.abs(seat.x - localClick.x);
            double dz = Math.abs(seat.z - localClick.z);
            double sameSideBonus = Math.abs(localClick.x) > 0.25D && Math.signum(seat.x) == Math.signum(localClick.x) ? -0.25D : 0.0D;
            double score = dz * 3.0D + dx + sameSideBonus;
            if (score < bestScore) {
                bestScore = score;
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    private int findSeatByClickedBogie(VehicleDefinition def, Vec3 clickOffsetWorld) {
        var bogies = def.getBogies();
        if (bogies.isEmpty()) {
            return -1;
        }

        Vec3 clickedWorld = position().add(clickOffsetWorld);
        VehicleDefinition.BogieDefinition nearest = null;
        int nearestIndex = -1;
        double bestDistance = Double.MAX_VALUE;
        for (int i = 0; i < bogies.size(); i++) {
            VehicleDefinition.BogieDefinition bogie = bogies.get(i);
            Vec3 bogieWorld = localToWorld(bogie.position());
            double distance = bogieWorld.distanceToSqr(clickedWorld);
            if (distance < bestDistance) {
                bestDistance = distance;
                nearest = bogie;
                nearestIndex = i;
            }
        }
        if (nearest == null || bestDistance > 4.0D) {
            return -1;
        }
        double yawRad = Math.toRadians(getYRot());
        Vec3 forward = new Vec3(-Math.sin(yawRad), 0.0D, Math.cos(yawRad));
        Vec3 nearestOffset = localToWorld(nearest.position()).subtract(position());
        boolean frontBogie = nearestOffset.dot(forward) >= 0.0D;
        int seatIndex = frontBogie ? resolveFrontSeatIndex(def) : resolveRearSeatIndex(def);
        return seatIndex;
    }

    private int resolveSeatIndexForBogieClick(VehicleDefinition def, int bogieIndex) {
        if (def == null || bogieIndex < 0 || bogieIndex >= def.getBogies().size()) {
            return -1;
        }
        int[] extremes = getExtremeBogieIndices(def);
        boolean frontBogie = bogieIndex == extremes[1];
        int seatIndex = frontBogie ? resolveFrontSeatIndex(def) : resolveRearSeatIndex(def);
        return isDriverSeatIndex(seatIndex) ? seatIndex : -1;
    }

    public InteractionResult interactWithBogie(Player player, int bogieIndex, InteractionHand hand, boolean holdingCrowbar) {
        if (player == null) {
            return InteractionResult.PASS;
        }
        if (holdingCrowbar) {
            if (!level().isClientSide()) {
                enterCouplingMode(player);
            }
            return InteractionResult.sidedSuccess(level().isClientSide());
        }
        if (player.isSecondaryUseActive()) {
            return InteractionResult.PASS;
        }
        if (player.getVehicle() != null) {
            return InteractionResult.PASS;
        }
        VehicleDefinition def = VehicleRegistry.getById(getVehicleId());
        if (def == null) {
            return InteractionResult.PASS;
        }
        int clampedBogie = Mth.clamp(bogieIndex, 0, Math.max(0, getInteractionBogieCenters(def).size() - 1));
        Vec3 clickOffsetWorld = localToWorld(getBogieLocalPosition(clampedBogie, def)).subtract(position());
        int seatIndex = resolveSeatIndexForBogieClick(def, clampedBogie);
        if (seatIndex < 0) {
            seatIndex = findNearestSeatToLocalClick(def, worldToLocal(position().add(clickOffsetWorld)));
        }
        return tryRideWithSeat(player, seatIndex);
    }

    public InteractionResult rideSeat(Player player, int seatIndex) {
        return tryRideWithSeat(player, seatIndex);
    }

    private InteractionResult tryRideWithSeat(Player player, int seatIndex) {
        if (seatIndex < 0) {
            return InteractionResult.PASS;
        }
        if (player.getVehicle() != null) {
            return InteractionResult.PASS;
        }

        TrainSeatEntity seatEntity = getOrCreateSeatHitbox(seatIndex);
        if (seatEntity == null) {
            RealTrainModUnofficial.LOGGER.warn("Ride denied: seat entity missing for vehicle {} seat {}", getVehicleId(), seatIndex);
            return InteractionResult.PASS;
        }
        if (!seatEntity.getPassengers().isEmpty() && !seatEntity.hasPassenger(player)) {
            return InteractionResult.PASS;
        }

        assignSeatIndex(player, seatIndex);
        VehicleDefinition def = VehicleRegistry.getById(getVehicleId());
        if (isDriverSeatIndex(seatIndex)) {
            setReverser(getDefaultReverserForSeat(def, seatIndex));
        }
        if (player.startRiding(seatEntity, true)) {
            // 転換クロスシート: 座った向きに合わせて編成全体の座席を転換する
            updateSeatDirectionFor(player);
            return InteractionResult.SUCCESS;
        }

        RealTrainModUnofficial.LOGGER.warn(
            "Player '{}' failed to mount vehicle '{}' at seat {}",
            player.getName().getString(),
            getVehicleId(),
            seatIndex
        );
        seatAssignments.remove(player.getUUID());
        return InteractionResult.PASS;
    }

    private int getDefaultReverserForSeat(VehicleDefinition def, int seatIndex) {
        return 1;
    }

    private int getDriverCabDirectionBySeatIndex(int seatIndex, VehicleDefinition def) {
        if (seatIndex < 0 || def == null) {
            return 1;
        }
        if (seatIndex == resolveRearSeatIndex(def)) {
            return -1;
        }
        if (seatIndex == resolveFrontSeatIndex(def)) {
            return 1;
        }
        List<Vec3> seats = getSelectableSeats(def);
        if (seatIndex >= 0 && seatIndex < seats.size()) {
            return seats.get(seatIndex).z < 0.0D ? -1 : 1;
        }
        return 1;
    }

    @Override
    public InteractionResult interactAt(Player player, Vec3 vec, InteractionHand hand) {
        if (isHoldingTrainPlacementItem(player)) {
            return InteractionResult.PASS;
        }
        boolean holdingCrowbar = player.getMainHandItem().is(RealTrainModUnofficialItems.CROWBAR_ITEM.get())
            || player.getOffhandItem().is(RealTrainModUnofficialItems.CROWBAR_ITEM.get());
        if (holdingCrowbar) {
            if (!level().isClientSide()) {
                enterCouplingMode(player);
            }
            return InteractionResult.CONSUME;
        }
        if (player.isSecondaryUseActive()) {
            return InteractionResult.PASS;
        }
        if (player.getVehicle() != null) {
            return InteractionResult.PASS;
        }
        Vec3 clickOffsetWorld = vec != null ? vec : player.position().subtract(position());
        int seatIndex = findSeatByClickPosition(player, clickOffsetWorld);
        return tryRideWithSeat(player, seatIndex);
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        Vec3 seat = passenger != null ? getAssignedSeatOffset(passenger) : Vec3.ZERO;
        double preferredSide = seat.x >= 0.0D ? 1.0D : -1.0D;
        List<Vec3> localCandidates = List.of(
            new Vec3(preferredSide * 3.2D, 0.0D, seat.z),
            new Vec3(preferredSide * 4.0D, 0.0D, seat.z),
            new Vec3(-preferredSide * 3.2D, 0.0D, seat.z),
            new Vec3(preferredSide * 3.0D, 0.0D, seat.z + 2.0D),
            new Vec3(preferredSide * 3.0D, 0.0D, seat.z - 2.0D),
            new Vec3(-preferredSide * 3.0D, 0.0D, seat.z + 2.0D),
            new Vec3(-preferredSide * 3.0D, 0.0D, seat.z - 2.0D)
        );
        double[] yOffsets = {0.5D, 1.0D, 0.0D, 1.5D};
        for (Vec3 local : localCandidates) {
            Vec3 base = localToWorld(local);
            for (double yOffset : yOffsets) {
                Vec3 candidate = new Vec3(base.x, base.y + yOffset, base.z);
                if (isSafeDismountLocation(passenger, candidate)) {
                    return candidate;
                }
            }
        }
        Vec3 fallback = localToWorld(new Vec3(preferredSide * 3.6D, 0.0D, seat.z));
        return new Vec3(fallback.x, fallback.y + 0.5D, fallback.z);
    }

    public void forceDismountPassenger(Player player) {
        if (player == null) {
            return;
        }
        Vec3 dismountPos = getDismountLocationForPassenger(player);
        player.stopRiding();
        clearSeatAssignment(player.getUUID());
        player.teleportTo(dismountPos.x, dismountPos.y, dismountPos.z);
        player.fallDistance = 0.0F;
    }

    private boolean isSafeDismountLocation(LivingEntity passenger, Vec3 pos) {
        if (passenger == null) {
            return false;
        }
        AABB box = passenger.getDimensions(passenger.getPose()).makeBoundingBox(pos);
        if (!level().getWorldBorder().isWithinBounds(BlockPos.containing(pos))) {
            return false;
        }
        if (!level().noCollision(passenger, box)) {
            return false;
        }
        BlockPos feetPos = BlockPos.containing(pos.x, pos.y - 0.1D, pos.z);
        BlockPos belowPos = feetPos.below();
        return !level().getBlockState(belowPos).isAir() || !level().getFluidState(feetPos).isEmpty();
    }

    private Vec3 localToWorld(Vec3 local) {
        VehicleDefinition def = VehicleRegistry.getById(getVehicleId());
        if (def == null) {
            def = VehicleRegistry.getSelected();
        }

        Vec3 offset = def != null ? def.getModelOffset() : Vec3.ZERO;
        float scale = def != null ? def.getModelScale() : 1.0F;
        // モデルのZ+を前方としてYRotで回転させる。
        double yawRad = Math.toRadians(-this.getYRot());

        double localX = local.x;
        double localY = local.y;
        double localZ = local.z;
        // Z+を前方として回転
        double rotatedX = Math.cos(yawRad) * localX - Math.sin(yawRad) * localZ;
        double rotatedZ = Math.sin(yawRad) * localX + Math.cos(yawRad) * localZ;
        double offsetX = Math.cos(yawRad) * offset.x - Math.sin(yawRad) * offset.z;
        double offsetZ = Math.sin(yawRad) * offset.x + Math.cos(yawRad) * offset.z;

        return new Vec3(
            this.getX() + offsetX + rotatedX * scale,
            this.getY() + offset.y + localY * scale,
            this.getZ() + offsetZ + rotatedZ * scale
        );
    }

    private Vec3 worldToLocal(Vec3 world) {
        VehicleDefinition def = VehicleRegistry.getById(getVehicleId());
        if (def == null) {
            def = VehicleRegistry.getSelected();
        }

        Vec3 offset = def != null ? def.getModelOffset() : Vec3.ZERO;
        float scale = def != null ? def.getModelScale() : 1.0F;
        double yawRad = Math.toRadians(-this.getYRot());
        double offsetX = Math.cos(yawRad) * offset.x - Math.sin(yawRad) * offset.z;
        double offsetZ = Math.sin(yawRad) * offset.x + Math.cos(yawRad) * offset.z;
        double dx = world.x - this.getX() - offsetX;
        double dy = world.y - this.getY() - offset.y;
        double dz = world.z - this.getZ() - offsetZ;

        double localX = Math.cos(yawRad) * dx + Math.sin(yawRad) * dz;
        double localZ = -Math.sin(yawRad) * dx + Math.cos(yawRad) * dz;
        return new Vec3(localX / scale, dy / scale, localZ / scale);
    }

    /** 台車描画用のworld→body-local変換。本体ポーズ全段を厳密に逆変換する。 */
    private Vec3 worldToBogieLocalForRender(Vec3 world, float partialTicks) {
        VehicleDefinition def = VehicleRegistry.getById(getVehicleId());
        if (def == null) {
            def = VehicleRegistry.getSelected();
        }
        Vec3 offset = def != null ? def.getModelOffset() : Vec3.ZERO;
        float scale = def != null ? def.getModelScale() : 1.0F;
        double dxo = this.getX() - this.xo;
        double dyo = this.getY() - this.yo;
        double dzo = this.getZ() - this.zo;
        boolean staleOldPos = (dxo * dxo + dyo * dyo + dzo * dzo) > 64.0D;
        double renderX = staleOldPos ? this.getX() : Mth.lerp(partialTicks, this.xo, this.getX());
        double renderY = staleOldPos ? this.getY() : Mth.lerp(partialTicks, this.yo, this.getY());
        double renderZ = staleOldPos ? this.getZ() : Mth.lerp(partialTicks, this.zo, this.getZ());
        // TrainEntityRenderer と同じ式で yaw / pitch / bank を求める。
        float renderYaw = staleOldPos ? getYRot() : Mth.rotLerp(partialTicks, this.yRotO, getYRot());
        float renderPitch = Mth.clamp(staleOldPos ? getXRot() : Mth.lerp(partialTicks, this.xRotO, getXRot()), -45.0F, 45.0F);
        float yawDelta = Mth.wrapDegrees(getYRot() - this.yRotO);
        float horizSpeed = (float) getDeltaMovement().horizontalDistance();
        float bankAngle = Mth.clamp(-yawDelta * horizSpeed * 5.0F, -10.0F, 10.0F);
        // レンダラの回転(YP yaw → XP -pitch → ZP bank)を組み、その逆(共役)で world ベクトルを戻す。
        org.joml.Quaternionf q = new org.joml.Quaternionf()
            .rotateY((float) Math.toRadians(renderYaw))
            .rotateX((float) Math.toRadians(-renderPitch))
            .rotateZ((float) Math.toRadians(bankAngle));
        org.joml.Vector3f d = new org.joml.Vector3f(
            (float) (world.x - renderX), (float) (world.y - renderY), (float) (world.z - renderZ));
        q.conjugate().transform(d);
        // modelOffset は回転後の本体フレームで translate されるので、回転を戻した後に引く。
        d.sub((float) offset.x, (float) offset.y, (float) offset.z);
        return new Vec3(d.x / scale, d.y / scale, d.z / scale);
    }

    private Vec3 worldToLocalForRender(Vec3 world, float renderYaw, float partialTicks) {
        VehicleDefinition def = VehicleRegistry.getById(getVehicleId());
        if (def == null) {
            def = VehicleRegistry.getSelected();
        }

        Vec3 offset = def != null ? def.getModelOffset() : Vec3.ZERO;
        float scale = def != null ? def.getModelScale() : 1.0F;
        double dxo = this.getX() - this.xo;
        double dyo = this.getY() - this.yo;
        double dzo = this.getZ() - this.zo;
        boolean staleOldPos = (dxo * dxo + dyo * dyo + dzo * dzo) > 64.0D;

        double renderX = staleOldPos ? this.getX() : Mth.lerp(partialTicks, this.xo, this.getX());
        double renderY = staleOldPos ? this.getY() : Mth.lerp(partialTicks, this.yo, this.getY());
        double renderZ = staleOldPos ? this.getZ() : Mth.lerp(partialTicks, this.zo, this.getZ());
        float effectiveYaw = staleOldPos ? this.getYRot() : renderYaw;
        double yawRad = Math.toRadians(-effectiveYaw);
        double offsetX = Math.cos(yawRad) * offset.x - Math.sin(yawRad) * offset.z;
        double offsetZ = Math.sin(yawRad) * offset.x + Math.cos(yawRad) * offset.z;
        double dx = world.x - renderX - offsetX;
        double dy = world.y - renderY - offset.y;
        double dz = world.z - renderZ - offsetZ;

        double localX = Math.cos(yawRad) * dx + Math.sin(yawRad) * dz;
        double localZ = -Math.sin(yawRad) * dx + Math.cos(yawRad) * dz;
        return new Vec3(localX / scale, dy / scale, localZ / scale);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (isHoldingTrainPlacementItem(player)) {
            return InteractionResult.PASS;
        }
        boolean holdingCrowbar = player.getMainHandItem().is(RealTrainModUnofficialItems.CROWBAR_ITEM.get())
            || player.getOffhandItem().is(RealTrainModUnofficialItems.CROWBAR_ITEM.get());
        if (holdingCrowbar) {
            if (!level().isClientSide()) {
                enterCouplingMode(player);
            }
            return InteractionResult.CONSUME;
        }
        if (player.isSecondaryUseActive()) return InteractionResult.PASS;
        if (player.getVehicle() != null) {
            return InteractionResult.PASS;
        }
        return tryRideWithSeat(player, findNearestSeatIndex(player));
    }

    private static boolean isHoldingTrainPlacementItem(Player player) {
        return player != null && (
            player.getMainHandItem().is(RealTrainModUnofficialItems.TRAIN_ITEM.get())
                || player.getOffhandItem().is(RealTrainModUnofficialItems.TRAIN_ITEM.get())
                || player.getMainHandItem().is(RealTrainModUnofficialItems.TRAIN_VEHICLE_ITEM.get())
                || player.getOffhandItem().is(RealTrainModUnofficialItems.TRAIN_VEHICLE_ITEM.get())
        );
    }

    private void ensureBogieHitboxes() {
        if (level().isClientSide()) {
            return;
        }
        VehicleDefinition def = VehicleRegistry.getById(getVehicleId());
        List<Vec3> bogies = getInteractionBogieCenters(def);
        int count = bogies.size();
        for (int bogieIndex = 0; bogieIndex < count; bogieIndex++) {
            TrainBogieEntity bogieEntity = resolveBogieHitbox(bogieIndex);
            if (bogieEntity == null) {
                bogieEntity = RealTrainModUnofficialEntities.TRAIN_BOGIE.get().create(level());
                if (bogieEntity == null) {
                    continue;
                }
                bogieEntity.attachToTrain(this, bogieIndex);
                level().addFreshEntity(bogieEntity);
                bogieHitboxUuids.put(bogieIndex, bogieEntity.getUUID());
            } else {
                bogieEntity.attachToTrain(this, bogieIndex);
            }
        }
        List<Integer> staleIndices = new ArrayList<>();
        for (Integer bogieIndex : bogieHitboxUuids.keySet()) {
            if (bogieIndex >= count) {
                staleIndices.add(bogieIndex);
            }
        }
        for (int bogieIndex : staleIndices) {
            TrainBogieEntity stale = resolveBogieHitbox(bogieIndex);
            if (stale != null) {
                stale.discard();
            }
            bogieHitboxUuids.remove(bogieIndex);
        }
    }

    private void ensureSeatHitboxes() {
        if (level().isClientSide()) {
            return;
        }
        VehicleDefinition def = VehicleRegistry.getById(getVehicleId());
        int seatCount = getSeatCount(def);
        for (int seatIndex = 0; seatIndex < seatCount; seatIndex++) {
            getOrCreateSeatHitbox(seatIndex);
        }
        seatHitboxUuids.entrySet().removeIf(entry -> {
            if (entry.getKey() < seatCount) {
                return false;
            }
            TrainSeatEntity stale = resolveSeatHitbox(entry.getKey());
            if (stale != null) {
                stale.discard();
            }
            return true;
        });
    }

    private TrainSeatEntity getOrCreateSeatHitbox(int seatIndex) {
        if (level() == null || level().isClientSide() || seatIndex < 0) {
            return null;
        }
        TrainSeatEntity seatEntity = resolveSeatHitbox(seatIndex);
        if (seatEntity != null) {
            seatEntity.attachToTrain(this, seatIndex);
            return seatEntity;
        }
        seatEntity = RealTrainModUnofficialEntities.TRAIN_SEAT.get().create(level());
        if (seatEntity == null) {
            return null;
        }
        seatEntity.attachToTrain(this, seatIndex);
        level().addFreshEntity(seatEntity);
        seatHitboxUuids.put(seatIndex, seatEntity.getUUID());
        return seatEntity;
    }

    private TrainSeatEntity resolveSeatHitbox(int seatIndex) {
        if (level() == null || level().isClientSide()) {
            return null;
        }
        UUID uuid = seatHitboxUuids.get(seatIndex);
        if (uuid == null) {
            return null;
        }
        if (level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            Entity entity = serverLevel.getEntity(uuid);
            if (entity instanceof TrainSeatEntity seat && seat.isAlive()) {
                return seat;
            }
        }
        return null;
    }

    private TrainBogieEntity resolveBogieHitbox(int bogieIndex) {
        if (level() == null || level().isClientSide()) {
            return null;
        }
        UUID uuid = bogieHitboxUuids.get(bogieIndex);
        if (uuid == null) {
            return null;
        }
        if (level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            Entity entity = serverLevel.getEntity(uuid);
            if (entity instanceof TrainBogieEntity bogie && bogie.isAlive()) {
                return bogie;
            }
        }
        return null;
    }

    private void discardBogieHitboxes() {
        for (Integer bogieIndex : List.copyOf(bogieHitboxUuids.keySet())) {
            TrainBogieEntity bogie = resolveBogieHitbox(bogieIndex);
            if (bogie != null) {
                bogie.discard();
            }
            bogieHitboxUuids.remove(bogieIndex);
        }
        if (level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            AABB searchBox = getBoundingBox().inflate(64.0D);
            for (TrainBogieEntity bogie : serverLevel.getEntitiesOfClass(TrainBogieEntity.class, searchBox)) {
                if (bogie.belongsToTrain(this.getId()) || bogie.getTrain() == null) {
                    bogie.discard();
                }
            }
        }
    }

    private void discardSeatHitboxes() {
        if (level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            for (UUID uuid : seatHitboxUuids.values()) {
                Entity entity = serverLevel.getEntity(uuid);
                if (entity instanceof TrainSeatEntity seat) {
                    seat.ejectPassengers();
                    seat.discard();
                }
            }
            AABB searchBox = getBoundingBox().inflate(64.0D);
            for (TrainSeatEntity seat : serverLevel.getEntitiesOfClass(TrainSeatEntity.class, searchBox)) {
                if (seat.belongsToTrain(this.getId()) || seat.getTrain() == null) {
                    seat.ejectPassengers();
                    seat.discard();
                }
            }
        }
        seatHitboxUuids.clear();
    }

    private record BogieViewHit(TrainEntity train, int bogieIndex, Vec3 localHit) {}

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide()) {
            ejectPassengers();
            discardBogieHitboxes();
            discardSeatHitboxes();
            seatAssignments.clear();
            syncSeatAssignmentsToEntityData();
            // Invalidate Formation so remaining cars rebuild without this train on next tick
            if (formation != null) {
                formation.trainStream().forEach(t -> { if (t != null && t != this) t.formation = null; });
                formation = null;
            }
        }
        super.remove(reason);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        setVehicleId(tag.getString("VehicleId"));
        setSpeed(tag.getFloat("Speed"));
        if (tag.contains("TrainDistance")) {
            setTrainDistance(tag.getFloat("TrainDistance"));
            refreshDimensions();
        }
        if (tag.contains("Notch")) {
            setNotch(tag.getInt("Notch"));
        }
        if (tag.contains("LightMode")) setLightMode(tag.getInt("LightMode"));
        else if (tag.contains("HeadlightOn")) setHeadlightOn(tag.getBoolean("HeadlightOn"));
        if (tag.contains("InteriorLightOn")) setInteriorLightOn(tag.getBoolean("InteriorLightOn"));
        if (tag.contains("DoorOpen")) setDoorOpen(tag.getBoolean("DoorOpen"));
        if (tag.contains("DoorLeftOpen")) setDoorLeftOpen(tag.getBoolean("DoorLeftOpen"));
        if (tag.contains("DoorRightOpen")) setDoorRightOpen(tag.getBoolean("DoorRightOpen"));
        if (tag.contains("PantographUp")) setPantographUp(tag.getBoolean("PantographUp"));
        if (tag.contains("Reverser")) {
            setReverser(tag.getInt("Reverser"));
        } else if (tag.contains("Reverse")) {
            setReverse(tag.getBoolean("Reverse"));
        }
        if (tag.contains("DestinationIndex")) setDestinationIndex(tag.getInt("DestinationIndex"));
        if (tag.contains("SoundIndex")) setSoundIndex(tag.getInt("SoundIndex"));
        if (tag.contains("BodyRoll")) setBodyRoll(tag.getFloat("BodyRoll"));
        if (tag.contains("CustomButtonBits")) setCustomButtonBits(tag.getInt("CustomButtonBits"));
        if (tag.contains("RailProgress")) setRailProgress(tag.getFloat("RailProgress"));
        if (tag.contains("CoupledFollower")) {
            try {
                setCoupledFollowerUuid(UUID.fromString(tag.getString("CoupledFollower")));
            } catch (Exception ignored) {
                setCoupledFollowerUuid(null);
            }
        }
        if (tag.contains("CoupledLeader")) {
            try {
                setCoupledLeaderUuid(UUID.fromString(tag.getString("CoupledLeader")));
            } catch (Exception ignored) {
                setCoupledLeaderUuid(null);
            }
        }
        coupledFollowerThisSide = tag.contains("CoupledFollowerThisSide")
            ? normalizeCouplerSide(tag.getInt("CoupledFollowerThisSide"))
            : -1;
        coupledFollowerOtherSide = tag.contains("CoupledFollowerOtherSide")
            ? normalizeCouplerSide(tag.getInt("CoupledFollowerOtherSide"))
            : 1;

        seatAssignments.clear();
        scriptData.clear();
        if (tag.contains("SeatAssignments")) {
            CompoundTag assignments = tag.getCompound("SeatAssignments");
            VehicleDefinition def = VehicleRegistry.getById(getVehicleId());
            int seatCount = getSeatCount(def);
            for (String key : assignments.getAllKeys()) {
                try {
                    UUID uuid = UUID.fromString(key);
                    int seatIndex = assignments.getInt(key);
                    if (seatCount <= 0) {
                        seatAssignments.put(uuid, 0);
                    } else if (seatIndex < 0) {
                        seatAssignments.put(uuid, 0);
                    } else if (seatIndex >= seatCount) {
                        seatAssignments.put(uuid, seatCount - 1);
                    } else {
                        seatAssignments.put(uuid, seatIndex);
                    }
                } catch (IllegalArgumentException e) {
                    // ignore malformed UUIDs
                }
            }
        }
        if (tag.contains("ScriptData")) {
            CompoundTag scriptDataTag = tag.getCompound("ScriptData");
            for (String key : scriptDataTag.getAllKeys()) {
                scriptData.put(key, scriptDataTag.getString(key));
            }
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putString("VehicleId", getVehicleId());
        tag.putFloat("Speed", getSpeed());
        tag.putFloat("TrainDistance", getTrainDistance());
        tag.putInt("Notch", getNotch());
        tag.putBoolean("HeadlightOn", isHeadlightOn());
        tag.putBoolean("DoorOpen", isDoorOpen());
        tag.putBoolean("DoorLeftOpen", isDoorLeftOpen());
        tag.putBoolean("DoorRightOpen", isDoorRightOpen());
        tag.putInt("LightMode", getLightMode());
        tag.putBoolean("InteriorLightOn", isInteriorLightOn());
        tag.putBoolean("PantographUp", isPantographUp());
        tag.putBoolean("Reverse", isReverse());
        tag.putInt("Reverser", getReverser());
        tag.putInt("DestinationIndex", getDestinationIndex());
        tag.putInt("SoundIndex", getSoundIndex());
        tag.putFloat("BodyRoll", getBodyRoll());
        tag.putInt("CustomButtonBits", getCustomButtonBits());
        tag.putFloat("RailProgress", getRailProgress());
        if (coupledFollowerUuid != null) {
            tag.putString("CoupledFollower", coupledFollowerUuid.toString());
            tag.putInt("CoupledFollowerThisSide", coupledFollowerThisSide);
            tag.putInt("CoupledFollowerOtherSide", coupledFollowerOtherSide);
        }
        if (coupledLeaderUuid != null) {
            tag.putString("CoupledLeader", coupledLeaderUuid.toString());
        }

        if (!seatAssignments.isEmpty()) {
            CompoundTag assignments = new CompoundTag();
            seatAssignments.forEach((uuid, seatIndex) -> assignments.putInt(uuid.toString(), seatIndex));
            tag.put("SeatAssignments", assignments);
        }
        if (!scriptData.isEmpty()) {
            CompoundTag scriptDataTag = new CompoundTag();
            scriptData.forEach(scriptDataTag::putString);
            tag.put("ScriptData", scriptDataTag);
        }
    }

    @Override
    protected void positionRider(Entity passenger, MoveFunction moveFunction) {
        if (!this.hasPassenger(passenger)) {
            return;
        }

        Vec3 seatPos = getPassengerRidingPosition(passenger);
        moveFunction.accept(passenger, seatPos.x, seatPos.y, seatPos.z);
        // 視点固定しすぎない（左右確認できるようにする）
        if (passenger instanceof LivingEntity living) {
            living.setYBodyRot(this.getYRot());
        }
    }

    private static float approachZero(float value, float step) {
        if (value > 0.0F) {
            return Math.max(0.0F, value - step);
        }
        if (value < 0.0F) {
            return Math.min(0.0F, value + step);
        }
        return 0.0F;
    }

    private static float approach(float value, float target, float step) {
        if (value < target) {
            return Math.min(target, value + step);
        }
        if (value > target) {
            return Math.max(target, value - step);
        }
        return value;
    }

    // ---- Missing methods restored ----

    public boolean isRailGuided() {
        return activeRailMap != null && activeRailSplit > 0 && activeRailPosition >= 0.0D;
    }

    public void clearRailGuidance() {
        activeRailMap = null;
        activeRailSplit = 0;
        activeRailPosition = -1.0D;
        frontRailAnchor = null;
        rearRailAnchor = null;
    }

    private long couplingSettleWindowEnd = Long.MIN_VALUE;
    private long uncoupledContactStopWindowEnd = Long.MIN_VALUE;

    public boolean isWithinCouplingSettleWindow() {
        return level() != null && level().getGameTime() <= couplingSettleWindowEnd;
    }

    public void markCouplingSettleWindow(long durationTicks) {
        if (level() != null) couplingSettleWindowEnd = level().getGameTime() + durationTicks;
    }

    public boolean isWithinUncoupledContactStopWindow() {
        return level() != null && level().getGameTime() <= uncoupledContactStopWindowEnd;
    }

    public void markUncoupledContactStopWindow(long durationTicks) {
        if (level() != null) uncoupledContactStopWindowEnd = level().getGameTime() + durationTicks;
    }

    public void settleCoupledRailPose() {
        if (isRailGuided() && isRailAnchorUsable(frontRailAnchor) && isRailAnchorUsable(rearRailAnchor)) {
            RailSample front = sampleBogieRail(frontRailAnchor.map(), frontRailAnchor.split(), frontRailAnchor.index());
            RailSample rear  = sampleBogieRail(rearRailAnchor.map(),  rearRailAnchor.split(),  rearRailAnchor.index());
            applyPoseFromBogieSamples(front, rear, getYRot(), getXRot(), false);
        }
    }

    public void settleConnectedFormationToRail() {
        forEachFormationTrain(t -> { if (t.isRailGuided()) t.settleCoupledRailPose(); });
    }

    private RailMap stableRailMap;
    private int stableRailSplit;
    private double stableRailPosition;
    private int stableRailBodyDirection;
    private RailAnchor stableFrontAnchor;
    private RailAnchor stableRearAnchor;

    public void rememberConnectedFormationStableRailState() {
        forEachFormationTrain(t -> {
            if (t.isRailGuided()) {
                t.stableRailMap = t.activeRailMap;
                t.stableRailSplit = t.activeRailSplit;
                t.stableRailPosition = t.activeRailPosition;
                t.stableRailBodyDirection = t.activeRailBodyDirection;
                t.stableFrontAnchor = t.frontRailAnchor;
                t.stableRearAnchor = t.rearRailAnchor;
            }
        });
    }

    public boolean restoreLastStableRailState() {
        if (stableRailMap != null && stableRailSplit > 0) {
            activeRailMap = stableRailMap;
            activeRailSplit = stableRailSplit;
            activeRailPosition = stableRailPosition;
            activeRailBodyDirection = stableRailBodyDirection;
            frontRailAnchor = stableFrontAnchor;
            rearRailAnchor = stableRearAnchor;
            return true;
        }
        return false;
    }

    public void restoreStableRailStateForFormation() {
        forEachFormationTrain(TrainEntity::restoreLastStableRailState);
    }

    public void restoreRailState(RailMap map, int split, int index, double position, int dir, int bodyDir, RailAnchor front, RailAnchor rear) {
        activeRailMap = map;
        activeRailSplit = split;
        activeRailIndex = index;
        activeRailPosition = position;
        activeRailDirection = dir;
        activeRailBodyDirection = bodyDir;
        frontRailAnchor = front;
        rearRailAnchor = rear;
    }

    private boolean railsShareEndpoint(RailMap a, RailMap b) {
        if (a == null || b == null) return false;
        return sameRailEndpoint(a.getStartRP(), b.getStartRP())
            || sameRailEndpoint(a.getStartRP(), b.getEndRP())
            || sameRailEndpoint(a.getEndRP(), b.getStartRP())
            || sameRailEndpoint(a.getEndRP(), b.getEndRP());
    }

    public TrainEntity resolveCoupledTrain(UUID uuid) {
        return resolveTrainByUuid(uuid);
    }

    public TrainEntity getCoupledLeader() {
        return resolveTrainByUuid(getDisplayLeaderUuid());
    }

    public TrainEntity getCoupledFollower() {
        return resolveTrainByUuid(getDisplayFollowerUuid());
    }

    private List<TrainEntity> getFormationTrainsInOrder() {
        List<TrainEntity> result = new ArrayList<>();
        TrainEntity head = this;
        int guard = 0;
        while (head.coupledLeaderUuid != null && guard++ < 16) {
            TrainEntity leader = head.resolveCoupledTrain(head.coupledLeaderUuid);
            if (leader == null) { head.setCoupledLeaderUuid(null); break; }
            head = leader;
        }
        TrainEntity cur = head;
        guard = 0;
        while (cur != null && guard++ < 16) {
            result.add(cur);
            if (cur.coupledFollowerUuid == null) break;
            TrainEntity follower = cur.resolveCoupledTrain(cur.coupledFollowerUuid);
            if (follower == null) { cur.setCoupledFollowerUuid(null); break; }
            cur = follower;
        }
        return result;
    }

    private double getConfiguredTrainDistance() {
        return getTrainHalfLength() * 2.0D;
    }

    public double getDefaultDistanceToConnectedTrain(TrainEntity other) {
        return Math.max((getConfiguredTrainDistance() + (other != null ? other.getConfiguredTrainDistance() : getConfiguredTrainDistance())) + 0.5,
            getTrainHalfLength() + (other != null ? other.getTrainHalfLength() : getTrainHalfLength()) + 1.5);
    }

    private void ensureDriverReadyForFormation(Entity driver) {
        // stub — driver seat handling not needed for rail movement
    }

    public void setReverserForFormation(int value) {
        forEachFormationTrain(t -> t.setReverser(value));
    }

    // ---- Formation field setters/methods ----

    public void setFormation(com.portofino.realtrainmodunofficial.entity.legacyformation.Formation f) {
        this.formation = f;
    }

    /** UUIDチェーンからFormationを構築する。 */
    private void rebuildFormationFromUuidChain() {
        if (level().isClientSide()) return;
        List<TrainEntity> chain = getFormationTrainsInOrder();
        if (chain.isEmpty()) {
            com.portofino.realtrainmodunofficial.entity.legacyformation.FormationManager.getInstance().createNewFormation(this);
            return;
        }
        if (chain.size() == 1) {
            if (formation == null) {
                com.portofino.realtrainmodunofficial.entity.legacyformation.FormationManager.getInstance().createNewFormation(this);
            }
            return;
        }
        long fid = com.portofino.realtrainmodunofficial.entity.legacyformation.FormationManager.getInstance().getNewId();
        com.portofino.realtrainmodunofficial.entity.legacyformation.Formation f =
            new com.portofino.realtrainmodunofficial.entity.legacyformation.Formation(fid, chain.size());
        for (int i = 0; i < chain.size(); i++) {
            TrainEntity t = chain.get(i);
            int leaderSide = -1;
            int followerSide = 1;
            if (i > 0) {
                TrainEntity prev = chain.get(i - 1);
                leaderSide = normalizeCouplerSide(prev.coupledFollowerThisSide);
                followerSide = normalizeCouplerSide(prev.coupledFollowerOtherSide);
            }
            f.entries[i] = new com.portofino.realtrainmodunofficial.entity.legacyformation.FormationEntry(t, i, 0, leaderSide, followerSide);
            t.formation = f;
        }
    }

    public void moveAsFormationFollower(TrainEntity leader, int leaderSide, int followerSide, float speed) {
        if (leader == null || level().isClientSide()) return;
        // テレポート・ガード: フォロワーがレール追従に失敗した時のフォールバック moveTo は、
        // 中間車のレール状態が未確立だと遠方(他車の上)へ飛ぶことがある。連結中の正当な
        // 微調整を超える大ジャンプは棄却し、本体を元位置に留める(本家は台車を保持して飛ばさない)。
        double preX = getX();
        double preY = getY();
        double preZ = getZ();
        boolean placed = leader.placeCoupledFollowerOnRail(this, leaderSide, followerSide);
        if (!placed) {
            double gap = leader.getCoupledGap(leader, this);
            leader.placeCoupledFollowerFallback(this, leaderSide, followerSide, gap);
            double jumpX = getX() - preX;
            double jumpZ = getZ() - preZ;
            double maxJump = Math.max(8.0D, leader.getDefaultDistanceToConnectedTrain(this) + 2.0D);
            if (jumpX * jumpX + jumpZ * jumpZ > maxJump * maxJump) {
                // ありえない大移動 → テレポート扱いで棄却し元の位置を維持。
                moveTo(preX, preY, preZ, getYRot(), getXRot());
            }
            clearRailGuidance();
        } else {
            centerGuidanceFallbackTicks = 0;
            railGuidanceFailureTicks = 0;
            travelStallTicks = 0;
            settleCoupledRailPose();
        }
        setSpeed(speed);
        setNotch(leader.getNotch());
        setReverser(leader.getReverser());
        setDeltaMovement(Vec3.ZERO);
        this.hurtMarked = true;
        this.hasImpulse = true;
    }

    // ---- Custom button multi-value storage ----

    public int getCustomButtonValue(int index) {
        if (index < 0 || index >= 16) return 0;
        if (customButtonValues == null || index >= customButtonValues.length) {
            return isCustomButtonOn(index) ? 1 : 0;
        }
        return customButtonValues[index];
    }

    public void setCustomButtonValue(int index, int value) {
        if (index < 0 || index >= 16) return;
        if (customButtonValues == null) customButtonValues = new int[16];
        customButtonValues[index] = value;
        setCustomButton(index, value != 0);
        scriptData.put("Button" + index, Integer.toString(value));
        scriptData.put("button" + index, Integer.toString(value));
        scriptData.put("CustomButton" + index, Integer.toString(value));
        scriptData.put("customButton" + index, Integer.toString(value));
        scriptDataDirty = true;
    }

    // ---- Previous-frame bogie world position (for bogie entity xo/yo/zo sync) ----

    public Vec3 getBogieEntityWorldPositionPrev(int bogieIndex) {
        VehicleDefinition def = VehicleRegistry.getById(getVehicleId());
        Vec3 local = getBogieLocalPosition(bogieIndex, def);
        return localToWorldPrev(local);
    }

    private Vec3 localToWorldPrev(Vec3 local) {
        VehicleDefinition def = VehicleRegistry.getById(getVehicleId());
        if (def == null) def = VehicleRegistry.getSelected();
        Vec3 offset = def != null ? def.getModelOffset() : Vec3.ZERO;
        double scale = def != null ? def.getModelScale() : 1.0D;
        double yawRad = Math.toRadians(-this.yRotO);
        double rotatedX = Math.cos(yawRad) * local.x - Math.sin(yawRad) * local.z;
        double rotatedZ = Math.sin(yawRad) * local.x + Math.cos(yawRad) * local.z;
        double offsetX = Math.cos(yawRad) * offset.x - Math.sin(yawRad) * offset.z;
        double offsetZ = Math.sin(yawRad) * offset.x + Math.cos(yawRad) * offset.z;
        return new Vec3(
            this.xo + offsetX + rotatedX * scale,
            this.yo + offset.y + local.y * scale,
            this.zo + offsetZ + rotatedZ * scale
        );
    }

}
