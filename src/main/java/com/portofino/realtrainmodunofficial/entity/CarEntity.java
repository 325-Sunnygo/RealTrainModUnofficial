package com.portofino.realtrainmodunofficial.entity;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import com.portofino.realtrainmodunofficial.RealTrainModUnofficialItems;
import com.portofino.realtrainmodunofficial.item.CrowbarItem;
import com.portofino.realtrainmodunofficial.vehicle.VehicleDefinition;
import com.portofino.realtrainmodunofficial.vehicle.VehicleRegistry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import javax.script.ScriptEngine;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import static com.portofino.realtrainmodunofficial.util.RealTrainModUnofficialConstants.SECONDS_IN_TICK;
import static com.portofino.realtrainmodunofficial.util.RealTrainModUnofficialConstants.TICK_PER_SECOND;
import static com.portofino.realtrainmodunofficial.util.UnitConverter.*;

// / 自動車Entityクラス
public final class CarEntity extends Entity {
    /**
     * 本家 EntityVehicleBase.getBrightnessForRender 相当。
     * 1.7.10 のバニラ Entity.getBrightnessForRender は
     * int j = MathHelper.floor_double(this.posY + (double)(this.height / 2.0F));
     * と車体の中心の高さで明るさを取る。
     */
    @Override
    public net.minecraft.world.phys.Vec3 getLightProbePosition(float partialTicks) {
        return new net.minecraft.world.phys.Vec3(
            this.getX(), this.getY() + this.getBbHeight() * 0.5D, this.getZ());
    }

    private static final EntityDataAccessor<String> DATA_VEHICLE_ID =
        SynchedEntityData.defineId(CarEntity.class, EntityDataSerializers.STRING);

    private com.portofino.realtrainmodunofficial.script.CarServerScripts.Entry serverScript;
    private boolean attemptedServerScriptLoad;
    private final java.util.Map<String, String> scriptData = new java.util.HashMap<>();
    /** サーバーが flag!=0 で書いた値。毎tick まとめてクライアントへ流す。 */
    private boolean scriptDataDirty;
    /** 乗客同期を送った相手 (プレイヤー本人には通常届かないため自前で送る)。 */
    private int lastPassengerSyncVehicleId = -1;

    // === RTM 1.7.10/1.12 互換フィールド (SRB3 等のスクリプトが直接読み書きする) ===
    /** RTM の yaw 名 (entity.field_70177_z) */
    public float field_70177_z;
    /** RTM の pitch 名 (entity.field_70125_A) */
    public float field_70125_A;
    /** RTM の tick counter 名 (entity.field_70173_aa) */
    public int field_70173_aa;
    /** RTM の world 参照 (entity.field_70170_p)。WorldCompat 経由でアクセス。 */
    public jp.ngt.mccompat.WorldCompat field_70170_p;
    /** RTM の motionX/Y/Z 名 (SRB3 の doFollowing が 0 を書いて漂流を止める)。 */
    public double field_70159_w;
    public double field_70181_x;
    public double field_70179_y;
    /** サーバスクリプトが motion を書いた = 移動はスクリプト任せ。RTMU独自の車物理を止める。 */
    private boolean scriptDrivesMotion;
    /** 1.7.10 riddenByEntity (この車に乗っているプレイヤーのラッパー) */
    public jp.ngt.mccompat.PlayerCompat field_70153_n;
    /** 1.7.10 ridingEntity (この車が乗っている対象=ホストプレイヤーのラッパー) */
    public jp.ngt.mccompat.PlayerCompat field_70154_o;
    /** 1.7.10 posX/posY/posZ */
    public double field_70165_t;
    public double field_70163_u;
    public double field_70161_v;

    // / 車輪のX座標オフセット
    public static final float WHEEL_X_COORD = cm2m(72.47766876220703f);
    // private static final EntityDataAccessor<Float> DATA_SPEED =
    // SynchedEntityData.defineId(CarEntity.class, EntityDataSerializers.FLOAT);
    // 自動車の情報

    // / 乗車定員
    private static final int RIDING_CAPACITY = 5;

    // モデル情報
    // / 前輪のZ座標
    public static final float WHEEL_F_COORD = cm2m(158.62274169921875f);
    // / 後輪のZ座標
    public static final float WHEEL_R_COORD = cm2m(-164.98480224609375f);
    // / 車輪のY座標
    public static final float WHEEL_Y_COORD = cm2m(37.28034973144531f);
    // / 車輪の半径
    public static final float WHEEL_RADIUS = WHEEL_Y_COORD;
    // / ホイールベースの距離
    private static final float WHEELBASE = WHEEL_F_COORD - WHEEL_R_COORD;

    // 性能
    // / 加速度（ブロック毎ティック毎ティック）
    private static final float ACCELERATION = mpss2bpts(4.15f); // ゼロヒャク6.7秒から計算 約0.01f
    // / 減速度 正の値（ブロック毎ティック毎ティック）
    private static final float DECELERATION = ACCELERATION * 1.2f; // 加速度より少し強め
    // / 惰性の減速度 正の値（ブロック毎ティック毎ティック）
    private static final float SLOWDOWN_DECELERATION = 0.001f;
    // / 前進の最高速度 120km/h -> 33.33…m/s -> 1.666…block/tick
    private static final float MAX_SPEED = kph2bpt(120.0f);

    // / 車両が停止しているとみなす速度の閾値
    private static final float SPEED_STOP_THRESHOLD = 0.01f;
    // / ステアリングレシオ
    public static final float STEERING_RATIO = 1 / 12.0f; // ステアリング角度は、ハンドルの回転角度の12分の1

    // / 左右入力中の1tick当たりのハンドル回転角度（度毎ティック）
    private static final float STEERING_WHEEL_ANGULAR_VELOCITY_MANIPULATED = 10.0f;
    // / セルフセンタリングによる1tick当たりのハンドル回転係数（単位無し 1ブロック移動するごとに変化させる割合を決める）
    private static final float STEERING_WHEEL_SELF_CENTERING_PARAMETER = 2.0f;
    // / ハンドルの最大回転角度 左右に1.75回転ずつ（度）
    private static final float STEERING_WHEEL_MAX_ANGLE = 630.0f;
    // / ハンドルの回転角度
    public float currentSteeringWheelAngle = 0.0f; // 単位: 度
    // / 前回tickでのハンドルの回転角度
    public float prevSteeringWheelAngle = 0.0f;

    // / 車輪の回転角度 クライアントのみ
    public float wheelRotation = 0.0f;
    // / 前tickでの車輪の回転角度 クライアントのみ
    public float prevWheelRotation = 0.0f;

    // / 踏んでいる間のアクセル開度の変化量
    private static final float ACCELERATOR_STROKE_CHANGE_RATE = 1.0f / TICK_PER_SECOND / 3.0f; // 3秒でベタ踏み
    // / アクセル開度 0~1
    private float acceleratorStroke = 0.0f;
    // / 踏んでいる間のブレーキストロークの変化量
    private static final float BRAKE_STROKE_CHANGE_RATE = 1.0f / TICK_PER_SECOND; // 1秒でベタ踏み
    // / ブレーキのストローク量 0~1
    private float brakeStroke = 0.0f;
    // / ギアをリバースに入れているか
    private boolean isReversing = false;
    // / 現在ブレーキ中か
    private boolean isBraking = false;
    // / 前tickでのwSの値
    private float prevWs = 0.0f;
    // / ブレーキ中に停止してもキーを押し続けた際に、方向転換をロックする
    private boolean isReversalLocked = false;
    // / 速度 前進方向が正、後進方向が負
    public float speed = 0.0f;
    // / 現在のtickでのヨーの変化量（度）
    private float deltaYaw = 0.0f;


    public CarEntity(EntityType<? extends CarEntity> entityType, Level level) {
        super(entityType, level);
        // 本家 EntityVehicleBase:85 の ignoreFrustumCheck = true 相当。
        // SRB / NGTO Builder の描画スクリプトはワールド座標にマーカーや補助線を描くが、
        // 描画されるのは「車が視錐台に入っているとき」だけ。
        this.noCulling = true;
    }

    @Override
    protected void readAdditionalSaveData(@NotNull CompoundTag tag) {
        if (tag.contains("VehicleId")) {
            setVehicleId(tag.getString("VehicleId"));
        }
        if (tag.contains("ScriptData")) {
            CompoundTag sd = tag.getCompound("ScriptData");
            for (String key : sd.getAllKeys()) {
                scriptData.put(key, sd.getString(key));
            }
        }
    }

    @Override
    protected void addAdditionalSaveData(@NotNull CompoundTag tag) {
        tag.putString("VehicleId", getVehicleId());
        if (!scriptData.isEmpty()) {
            CompoundTag sd = new CompoundTag();
            scriptData.forEach(sd::putString);
            tag.put("ScriptData", sd);
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.@NotNull Builder builder) {
        builder.define(DATA_VEHICLE_ID, "");
    }

    public String getVehicleId() {
        return this.entityData.get(DATA_VEHICLE_ID);
    }

    public void setVehicleId(String id) {
        this.entityData.set(DATA_VEHICLE_ID, id == null ? "" : id);
    }

    public String getScriptDataValue(String key) {
        return scriptData.getOrDefault(key, "");
    }

    public void setScriptDataValue(String key, String value) {
        if (key == null || key.isBlank()) return;
        String v = value == null ? "" : value;
        String prev = scriptData.put(key, v);
        if (!v.equals(prev)) {
            scriptDataDirty = true;
        }
    }

    /** サーバ→クライアント同期で受け取った scriptData を適用する(クライアント側)。 */
    public void applyScriptDataSync(java.util.Map<String, String> data) {
        if (data == null) return;
        scriptData.putAll(data);
    }

    public java.util.Map<String, String> scriptDataMap() {
        return scriptData;
    }

    private void ensureServerScriptLoaded() {
        if (attemptedServerScriptLoad) return;
        String id = getVehicleId();
        if (id == null || id.isBlank()) return;
        attemptedServerScriptLoad = true;
        VehicleDefinition def = VehicleRegistry.getById(id);
        if (def == null || !def.hasServerScript()) {
            return;
        }
        try {
            // 本家と同じ Nashorn (jp.ngt 実クラス) でサーバースクリプトを実行
            serverScript = com.portofino.realtrainmodunofficial.script.CarServerScripts.get(def);
        } catch (Throwable t) {
            RealTrainModUnofficial.LOGGER.warn("Failed to load server script for {}: {}", id, t.toString());
        }
    }

    /** RTM 互換: スクリプトから entity.getResourceState で呼ばれる。 */
    public ResourceStateCompat getResourceState() {
        return new ResourceStateCompat(this);
    }

    public static final class ResourceStateCompat {
        private final CarEntity car;
        public ResourceStateCompat(CarEntity car) { this.car = car; }
        public DataMapCompat getDataMap() { return new DataMapCompat(car); }
    }

    /** RTM 互換: scriptData への読み書きを media する。 */
    public static final class DataMapCompat {
        private final CarEntity car;
        public DataMapCompat(CarEntity car) { this.car = car; }
        public String getString(String key) { return car == null ? "" : car.getScriptDataValue(key); }
        public boolean getBoolean(String key) {
            String v = getString(key);
            return "true".equalsIgnoreCase(v) || "1".equals(v);
        }
        public int getInt(String key) {
            try { return Integer.parseInt(getString(key)); } catch (Exception e) { return 0; }
        }
        public double getDouble(String key) {
            try { return Double.parseDouble(getString(key)); } catch (Exception e) { return 0.0; }
        }
        public void setString(String key, String value, int syncType) {
            apply(key, value == null ? "" : value, syncType);
        }
        public void setBoolean(String key, boolean value, int syncType) {
            apply(key, Boolean.toString(value), syncType);
        }
        public void setInt(String key, int value, int syncType) {
            apply(key, Integer.toString(value), syncType);
        }
        public void setDouble(String key, double value, int syncType) {
            apply(key, Double.toString(value), syncType);
        }
        /**
         * ローカルへ書き込みつつ、クライアント側で syncType!=0 の値はサーバへ送る。
         * render(クライアント)スクリプトが書いた設置点/ビルドフラグをサーバ onUpdate へ届け、
         * 実際の敷設をサーバで行えるようにする。
         */
        private void apply(String key, String value, int syncType) {
            if (car == null) {
                return;
            }
            // サーバーが書いた値のクライアントへの配布は tick 側の CarScriptDataSyncPayload が行う。
            car.setScriptDataValue(key, value);
            if (syncType != 0 && car.level().isClientSide()) {
                try {
                    net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                        new com.portofino.realtrainmodunofficial.network.CarScriptDataPayload(car.getId(), key, value));
                } catch (Throwable ignored) {
                    // サーバ未接続/送信失敗時は無視(ローカルには書けている)。
                }
            }
        }
    }

    // ===== RTM 1.12.2 MCP 名の互換メソッド (SRB3 等のサーバスクリプトが直接呼ぶ) =====
    /** func_184188_bt = getPassengers */
    public java.util.List<Entity> func_184188_bt() {
        return this.getPassengers();
    }
    /** func_184187_bx = getVehicle (乗っている対象) */
    public Entity func_184187_bx() {
        return this.getVehicle();
    }
    /** func_184210_p = stopRiding (降車/乗り物から降りる) */
    public void func_184210_p() {
        this.stopRiding();
    }
    /** func_145782_y = getId (エンティティID) */
    public int func_145782_y() {
        return this.getId();
    }
    /** func_70106_y = discard (エンティティ除去) */
    public void func_70106_y() {
        this.discard();
    }
    /** func_70107_b = setPos(x,y,z) */
    public void func_70107_b(double x, double y, double z) {
        this.setPos(x, y, z);
    }
    /**
     * func_70078_a = mountEntity (1.7.10)。この車が target に乗る (target=null で降りる)。
     * SRB3 / NGTO Builder のサーバースクリプトは
     * 「プレイヤーを降ろす → 車をプレイヤーに乗せる」で追従を実現しており、
     * 本家 1.7.10 の RTMApiCompat.doFollowing が空実装なのはそのため。
     */
    public void func_70078_a(Object target) {
        if (target == null) {
            this.stopRiding();
            return;
        }
        Entity e = jp.ngt.mccompat.EntityCompatUtil.unwrapEntity(target);
        if (e == null) {
            return;
        }
        // ★必ず自分と同じレベルの実体へ乗る。
        // スクリプトが持つラッパー (PlayerCompat) は、スクリプトエンジンが定義ごとに
        // 共有されている関係で反対サイドのプレイヤーを指していることがある。
        if (e.level() != this.level()) {
            Entity sameSide = this.level().getEntity(e.getId());
            if (sameSide == null) {
                RealTrainModUnofficial.LOGGER.warn(
                    "[RTMU] 反対サイドのエンティティへの騎乗要求を無視しました: target={} targetLevel={} selfLevel={}",
                    e.getClass().getSimpleName(), e.level().getClass().getSimpleName(),
                    this.level().getClass().getSimpleName());
                return;
            }
            e = sameSide;
        }
        // 本家 1.7.10 では「車がプレイヤーに乗る」= プレイヤーは車の乗客ではあり得ない。
        // スクリプトは dismountPlayer → startRiding の順で呼ぶが、降車が何らかの理由で
        // 効かないと相互に乗った状態になり、rider が毎tick残ってサーバースクリプトが
        // 「編集終了 (isEndEdit)」の枝から出られなくなる (= 敷設も終了も効かない)。
        this.ejectPassengers();
        boolean ok = this.startRiding(e, true);
        // ★乗り物がプレイヤー本人の場合、そのプレイヤーには乗客同期が届かない。
        // バニラは乗客の変化を ServerEntity:89 の broadcast で「その乗り物を追跡している
        // 他のプレイヤー」にだけ送るため、自分に何かが乗ったことを本人は知らない。
        if (ok && e instanceof net.minecraft.server.level.ServerPlayer sp) {
            sp.connection.send(new net.minecraft.network.protocol.game.ClientboundSetPassengersPacket(sp));
        }
    }

    // / 右クリックされた時の処理
    // /
    // / @param player 右クリックしたプレイヤー
    // / @param hand   メインハンドまたはオフハンド
    // / @return 処理の完了状態
    @Override
    public @NotNull InteractionResult interact(@NotNull Player player, @NotNull InteractionHand hand) {
        if (this.canAddPassenger(player)) {
            player.startRiding(this);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @Override
    protected boolean canAddPassenger(@NotNull Entity passenger) {
        return this.getPassengers().size() < RIDING_CAPACITY;
    }


    // / 操縦しているLivingEntity
    // /
    // / @return あればそのLivingEntity、なければnull
    @Override
    public LivingEntity getControllingPassenger() {
        final var passengers = this.getPassengers();
        final var controllingEntity = passengers.isEmpty() ? null : passengers.getFirst();
        return controllingEntity instanceof LivingEntity controllingLivingEntity ? controllingLivingEntity : null;
    }

    // / 渡された乗客Entityの着席位置
    // /
    // / @param passenger   乗客Entity
    // / @param dimensions  自動車の情報 寸法、目の高さなど
    // / @param partialTick なぜ？
    // / @return 位置のベクトル
    @Override
    @NotNull
    protected Vec3 getPassengerAttachmentPoint(@NotNull Entity passenger, @NotNull EntityDimensions dimensions, float partialTick) {
        // 友達がいないのでデバッグできません(泣)
        final var index = this.getPassengers().indexOf(passenger);

        final var baseOffset = calcBaseOffset(index, dimensions);

        final var yRot = this.getViewYRot(partialTick);
        final var rotatedHorizontalOffset = baseOffset.yRot((float) -Math.toRadians(yRot));

        return new Vec3(rotatedHorizontalOffset.x, baseOffset.y, rotatedHorizontalOffset.z);
    }

    private Vec3 calcBaseOffset(int index, EntityDimensions dimensions) {
        final var heightBase = dimensions.height() * 0.2;
        return switch (index) {
            case 0 -> new Vec3(-0.42, heightBase, 0.1);
            case 1 -> new Vec3(0.42, heightBase, 0.1);
            case 2 -> new Vec3(0.42, heightBase, -1.0);
            case 3 -> new Vec3(-0.42, heightBase, -1.0);
            case 4 -> new Vec3(0.0, heightBase, -1.0);
            default -> new Vec3(0.0, dimensions.height() * 0.9, 0.0); // nullが返せないので、Mr.ビーンの場所にしとく
        };
    }

    // / 乗客の向きを車両と同期するために使用 詳細不明
    @Override
    protected void positionRider(@NotNull Entity passenger, Entity.@NotNull MoveFunction callback) {
        super.positionRider(passenger, callback);
        if (!(passenger instanceof Player player)) return;
        player.setYRot(player.getYRot() + this.deltaYaw);
    }

    // / 謎
    @Override
    public boolean canCollideWith(@NotNull Entity entity) {
        return true;
    }

    // / 体当たりをして押せるかどうかだと思われる
    // /
    // / @return 常に偽 自動車だし押せなくていいよね
    @Override
    public boolean isPushable() {
        return false;
    }

    // / クリック判定を発生させるかどうかだと思われる
    // /
    // / @return もちろん発生させる じゃないと乗れない
    @Override
    public boolean isPickable() {
        return true;
    }

    // / バール・素手でプレイヤーが攻撃したら車を撤去してアイテムを回収する
    @Override
    public boolean hurt(@NotNull DamageSource source, float amount) {
        if (level().isClientSide) return false;
        if (!(source.getEntity() instanceof Player player)) return false;
        ItemStack held = player.getMainHandItem();
        // バールまたは素手のみ撤去可能
        if (!held.isEmpty() && !(held.getItem() instanceof CrowbarItem)) return false;
        if (!this.getPassengers().isEmpty()) {
            this.ejectPassengers();
        }
        this.spawnAtLocation(new ItemStack(RealTrainModUnofficialItems.CAR_ITEM.get()));
        this.discard();
        return true;
    }

    // / 用途不明
    @Override
    @NotNull
    public Packet<ClientGamePacketListener> getAddEntityPacket(@NotNull ServerEntity entity) {
        return new ClientboundAddEntityPacket(this, entity);
    }

    // / 毎Tick呼び出される
    @Override
    public void tick() {
        super.tick();

        // RTM 互換フィールドを最新値に同期 (SRB3 等のレガシースクリプトが直接読む)
        this.field_70177_z = getYRot();
        this.field_70125_A = getXRot();
        this.field_70173_aa = this.tickCount;
        this.field_70165_t = getX();
        this.field_70163_u = getY();
        this.field_70161_v = getZ();
        if (this.field_70170_p == null || this.field_70170_p.getLevel() != this.level()) {
            this.field_70170_p = new jp.ngt.mccompat.WorldCompat(this.level());
        }
        // rider (1.7.10: field_70153_n) / ridingEntity (field_70154_o) — PlayerCompat で公開
        {
            var passengers = this.getPassengers();
            net.minecraft.world.entity.player.Player rider = null;
            if (!passengers.isEmpty() && passengers.get(0) instanceof net.minecraft.world.entity.player.Player p) {
                rider = p;
            }
            this.field_70153_n = rider != null ? jp.ngt.mccompat.PlayerCompat.of(rider) : null;
            if (this.field_70153_n != null) {
                this.field_70153_n.refresh();
            }
            net.minecraft.world.entity.player.Player riding =
                this.getVehicle() instanceof net.minecraft.world.entity.player.Player rp ? rp : null;
            this.field_70154_o = riding != null ? jp.ngt.mccompat.PlayerCompat.of(riding) : null;
            if (this.field_70154_o != null) {
                this.field_70154_o.refresh();
            }
        }

        // ★乗り物がプレイヤー本人のとき、そのプレイヤーには乗客同期が届かない
        // (ServerEntity:89 の broadcast は「乗り物を追跡している他のプレイヤー」宛)。
        // 騎乗した瞬間に 1 回送るだけだと、クライアントがまだ車を認識していない場合に
        // 取りこぼして車がその場に残る。騎乗している間は定期的に送り直して確実に合わせる。
        if (!this.level().isClientSide()
                && this.getVehicle() instanceof net.minecraft.server.level.ServerPlayer host) {
            if (this.lastPassengerSyncVehicleId != host.getId() || this.tickCount % 20 == 0) {
                this.lastPassengerSyncVehicleId = host.getId();
                host.connection.send(
                    new net.minecraft.network.protocol.game.ClientboundSetPassengersPacket(host));
            }
        } else if (!this.level().isClientSide()) {
            this.lastPassengerSyncVehicleId = -1;
        }

        // ★別レベルの乗り物に乗ってしまっている車を自己修復する。
        // この状態になると、その車は自分のレベルのtickから外れて処理が止まる。
        if (this.getVehicle() != null && this.getVehicle().level() != this.level()) {
            RealTrainModUnofficial.LOGGER.warn("[RTMU] 別レベルの乗り物に騎乗していたため降車させました: self={} vehicle={}",
                this.level().getClass().getSimpleName(), this.getVehicle().level().getClass().getSimpleName());
            this.stopRiding();
        }

        // サーバ側で vehicle 紐付けスクリプト（SRB3 等）を毎tick実行する。
        // クライアントでは何もしない（DataMap 同期は別経路）。
        if (!this.level().isClientSide()) {
            ensureServerScriptLoaded();
            if (serverScript != null) {
                // スクリプトが読む1.7.10シムを実値で埋めてから走らせる。
                // 埋めないと motion が前回値のまま累積し、車が滑って暴れる。
                net.minecraft.world.phys.Vec3 before = this.getDeltaMovement();
                this.field_70159_w = before.x;
                this.field_70181_x = before.y;
                this.field_70179_y = before.z;

                serverScript.onUpdate(this);

                // サーバスクリプトが entity.field_70177_z=0 等で向きを制御する(SRB3はyawを0固定
                // してマーカーをワールド座標で描く)。シムのフィールドを実際の向きへ反映する。
                // 反映しないと車の実yawが残り、render が回転してマーカーが散らばる。
                this.setYRot(this.field_70177_z);
                this.setXRot(this.field_70125_A);
                this.yRotO = this.field_70177_z;
                this.xRotO = this.field_70125_A;

                // スクリプトが書いた motion (field_70159_w/x/y) を実際の移動へ反映する。
                // MFCP の車はここで前進/操舵を表現するので、反映しないと 1mm も動かない。
                double mx = this.field_70159_w;
                double my = this.field_70181_x;
                double mz = this.field_70179_y;
                if (Double.isFinite(mx) && Double.isFinite(my) && Double.isFinite(mz)
                        && (mx != before.x || my != before.y || mz != before.z)) {
                    this.setDeltaMovement(mx, my, mz);
                    this.scriptDrivesMotion = true;
                }
            }
            // ホストプレイヤー追従は本家どおり「車がプレイヤーに騎乗する」で行う
            // (mc1710 の RTMApiCompat.doFollowing は空実装)。位置ミラーはしない。
            // サーバ→クライアント scriptData 同期。
            if (scriptDataDirty && !scriptData.isEmpty()) {
                scriptDataDirty = false;
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                    this, new com.portofino.realtrainmodunofficial.network.CarScriptDataSyncPayload(
                        this.getId(), new java.util.HashMap<>(scriptData)));
            }
        }

        @SuppressWarnings("resource") final var level = this.level();

        this.prevSteeringWheelAngle = this.currentSteeringWheelAngle; // アニメーションのために前回tickの回転角度を保存

        // Entity#isControlledByLocalInstance は、自身が乗っている場合はクライアント、そうでなければサーバーでtrue
        // Entityの移動操作に使うとよいっぽい
        // マルチプレイでどうなるかはわからないが、テストする友達がいません（泣）
        // 降りた後に惰性で動かないので、とりあえずコメントアウトして無効化 要研究
        // if (!this.isControlledByLocalInstance) return;

        // 移動はサーバスクリプト任せ (本家 RTM と同じ)。RTMU 独自の車物理は持たない。
        // スクリプトが書いた motion をそのまま適用する。
        if (level.isClientSide) {
            updateWheelRotationInClient();
        }
        this.move(MoverType.SELF, this.getDeltaMovement());
    }

    // / 車輪の回転角度を更新する クライアントのみ。
    // / 速度はスクリプトが動かす実移動量から取る (独自物理の speed は持たない)。
    private void updateWheelRotationInClient() {
        this.prevWheelRotation = this.wheelRotation;
        Vec3 m = this.getDeltaMovement();
        double horizontal = Math.sqrt(m.x * m.x + m.z * m.z);
        // 進行方向 (車体前方) との内積で前進/後退の符号を決める
        Vec3 forward = Vec3.directionFromRotation(0.0F, this.getYRot());
        double signed = (m.x * forward.x + m.z * forward.z) < 0 ? -horizontal : horizontal;
        this.speed = (float) signed;
        if (WHEEL_RADIUS > 1.0E-5F) {
            this.wheelRotation += (float) (signed / WHEEL_RADIUS);
        }
    }
}