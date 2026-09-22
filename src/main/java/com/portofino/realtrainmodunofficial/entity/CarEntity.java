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
import net.minecraft.server.level.ServerEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

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
    /** 1.7.10 riddenByEntity (この車に乗っているプレイヤーのラッパー) */
    public jp.ngt.mccompat.PlayerCompat field_70153_n;
    /** 1.7.10 ridingEntity (この車が乗っている対象=ホストプレイヤーのラッパー) */
    public jp.ngt.mccompat.PlayerCompat field_70154_o;
    /** 1.7.10 posX/posY/posZ */
    public double field_70165_t;
    public double field_70163_u;
    public double field_70161_v;
    /** 1.7.10 lastTickPosX/Y/Z (描画補間用。NGTO Builder2 の getInterpolatedPos が読む) */
    public double field_70142_S;
    public double field_70137_T;
    public double field_70136_U;
    /** 1.7.10 prevPosX/Y/Z */
    public double field_70169_q;
    public double field_70167_r;
    public double field_70166_s;

    // / 車輪のX座標オフセット
    public static final float WHEEL_X_COORD = cm2m(72.47766876220703f);

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

    // 性能: 本家 VehicleConfig の既定値をそのまま使う (KaizPatchX VehicleConfig)
    // / 滑りやすさ
    protected static final float FRICTION = 0.9F;
    // / 加速度
    protected static final float ACCELERATION = 0.0125F;
    // / 最大速度
    protected static final float MAX_SPEED = 0.8F;
    // / 最大Y軸回転
    protected static final float MAX_YAW = 15.0F;
    // / 係数
    protected static final float YAW_COEFFICIENT = 4.5F;

    // / 速度 前進方向が正、後進方向が負 (本家 EntityVehicle.speed)
    public float speed = 0.0f;
    // / 車輪の回転角度 クライアントのみ
    public float wheelRotation = 0.0f;
    // / 前tickでの車輪の回転角度 クライアントのみ
    public float prevWheelRotation = 0.0f;
    // / ロール (本家 rotationRoll)
    public float rotationRoll = 0.0f;
    public float prevRotationRoll = 0.0f;


    public CarEntity(EntityType<? extends CarEntity> entityType, Level level) {
        super(entityType, level);
        // 本家 EntityVehicleBase:85 の ignoreFrustumCheck = true 相当。
        // SRB / NGTO Builder の描画スクリプトはワールド座標にマーカーや補助線を描くが、
        // 描画されるのは「車が視錐台に入っているとき」だけ。
        this.noCulling = true;
    }

    /** 本家 EntityCar: this.stepHeight = 2.0F (1ブロックの段差を乗り越える)。 */
    @Override
    public float maxUpStep() {
        return 2.0F;
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
        builder.define(DATA_SPEED, 0.0F);
    }

    /** 本家 EntityVehicleBase.speed の同期値。サーバーが書き、クライアントのスクリプトが読む。 */
    private static final EntityDataAccessor<Float> DATA_SPEED =
        SynchedEntityData.defineId(CarEntity.class, EntityDataSerializers.FLOAT);

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

    /**
     * 本家 API: スクリプトから {@code entity.getResourceState()} で呼ばれる。
     *
     * <p>★以前は RTMU 独自の薄いラッパ (getDataMap だけ) を返していたため、
     * 本家スクリプトが使う {@code getResourceName()} が無く
     * (Tank.js: {@code entity.getResourceState().getResourceName is not a function}) で
     * サーバースクリプトが落ちていた。本家と同じ {@code ResourceState} を返す。
     *
     * <p>DataMap は既存の保存先 (scriptData) とサーバー同期へブリッジしたもので、
     * 本家と同じく {@code state.dataMap.setXxx(key, value, syncFlag)} がそのまま使える。
     */
    private final jp.ngt.rtm.modelpack.state.ResourceState resourceState =
        new jp.ngt.rtm.modelpack.state.ResourceState(this::getScriptResourceName, null, new ScriptDataMap(this));

    public jp.ngt.rtm.modelpack.state.ResourceState getResourceState() {
        return this.resourceState;
    }

    /**
     * 本家 ResourceState.getResourceName が返すモデル名 (例 "Crusader")。
     * RTM 公式スクリプトは {@code modelName == "Crusader"} のように比較するため、
     * id (vehicle:pack:Crusader) ではなく表示名を返す。
     */
    private String getScriptResourceName() {
        VehicleDefinition def = VehicleRegistry.getById(this.getVehicleId());
        if (def == null) {
            return "";
        }
        String name = def.getDisplayName();
        return name == null ? "" : name;
    }

    /** 送信失敗を 1 キーにつき 1 回だけ知らせる。 */
    private static final java.util.Set<String> WARNED_SEND_FAILURE =
        java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * 本家 DataMap を CarEntity の scriptData へブリッジする。
     * 読み書きは scriptData に対して行い、クライアントで syncFlag!=0 の書き込みは
     * サーバーへ送る (render スクリプトが書いた砲撃フラグ等を server onUpdate へ届ける)。
     */
    public static final class ScriptDataMap extends jp.ngt.rtm.modelpack.state.DataMap {
        private final CarEntity car;

        public ScriptDataMap(CarEntity car) {
            this.car = car;
        }

        @Override
        public String getString(String key) {
            return car == null ? "" : car.getScriptDataValue(key);
        }

        @Override
        public boolean getBoolean(String key) {
            String v = getString(key);
            return "true".equalsIgnoreCase(v) || "1".equals(v);
        }

        @Override
        public int getInt(String key) {
            try { return Integer.parseInt(getString(key)); } catch (Exception e) { return 0; }
        }

        @Override
        public double getDouble(String key) {
            try { return Double.parseDouble(getString(key)); } catch (Exception e) { return 0.0; }
        }

        @Override
        public void setString(String key, String value, int flag) {
            apply(key, value == null ? "" : value, flag);
        }

        @Override
        public void setBoolean(String key, boolean value, int flag) {
            apply(key, Boolean.toString(value), flag);
        }

        @Override
        public void setInt(String key, int value, int flag) {
            apply(key, Integer.toString(value), flag);
        }

        @Override
        public void setDouble(String key, double value, int flag) {
            apply(key, Double.toString(value), flag);
        }

        /**
         * ローカルへ書き込みつつ、クライアント側で syncFlag!=0 の値はサーバへ送る。
         * render(クライアント)スクリプトが書いた設置点/ビルドフラグをサーバ onUpdate へ届け、
         * 実際の敷設/砲撃をサーバで行えるようにする。
         */
        private void apply(String key, String value, int flag) {
            if (car == null) {
                return;
            }
            car.setScriptDataValue(key, value);
            if ((flag & jp.ngt.rtm.modelpack.state.DataMap.SYNC_FLAG) != 0 && car.level().isClientSide()) {
                try {
                    net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                        new com.portofino.realtrainmodunofficial.network.CarScriptDataPayload(car.getId(), key, value));
                } catch (Throwable e) {
                    // ★握りつぶさない。ここが失敗すると「クライアントでは組めているのに
                    //   サーバーでは何も起きない」になり、原因が一切分からなくなる。
                    if (WARNED_SEND_FAILURE.add(key)) {
                        com.portofino.realtrainmodunofficial.RealTrainModUnofficial.LOGGER.warn(
                            "[RTMU] スクリプトデータをサーバーへ送れませんでした key={} 長さ={}: {}",
                            key, value == null ? 0 : value.length(), e.toString());
                    }
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

    /**
     * 本家 EntityVehicle.applyEntityCollision: 高速で走っている車は乗員以外の生物をはねる
     * (速度が最大速度の半分を超えたときだけダメージ)。
     */
    @Override
    public void push(@NotNull Entity entity) {
        super.push(entity);
        if (this.level().isClientSide() || entity == this.getControllingPassenger()) {
            return;
        }
        if (entity instanceof LivingEntity) {
            Vec3 m = this.getDeltaMovement();
            double dxz = m.x * m.x + m.z * m.z;
            if (dxz > 0.0D) {
                float strength = (float) (dxz / MAX_SPEED);
                if (strength > 0.5F) {
                    entity.hurt(this.damageSources().thorns(this), strength);
                }
            }
        }
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
        // ★サーバーは位置同期だけで this.speed を更新しない (移動は運転クライアント主導)。
        //   さらに super.tick() (= baseTick) が xOld を今tickの位置で上書きするため、
        //   スクリプト実行時点では getX()-xOld が常に 0 になり、速度が取れなかった。
        //   上書きされる前に「前tickからの実移動量 (前進が正)」を退避しておく。
        super.tick();

        // RTM 互換フィールドを最新値に同期 (SRB3 等のレガシースクリプトが直接読む)
        this.field_70177_z = getYRot();
        this.field_70125_A = getXRot();
        this.field_70173_aa = this.tickCount;
        // 前tickの位置は「今の値で上書きする前」に退避する (描画補間・進行方向算出用)。
        // field_70165_t/u/v (現在位置) は tick 末尾の移動後に更新する。
        this.field_70142_S = this.xOld;
        this.field_70137_T = this.yOld;
        this.field_70136_U = this.zOld;
        this.field_70169_q = this.xOld;
        this.field_70167_r = this.yOld;
        this.field_70166_s = this.zOld;
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

        this.prevRotationRoll = this.rotationRoll;

        // ★本家 EntityVehicleBase と同じく<b>サーバー権威</b>で物理を回す。
        //   乗員の入力は 1.21 では ServerPlayer.setPlayerInput が xxa/zza に入れるので
        //   サーバーでもそのまま読める。クライアントは何もせず、バニラの補間で位置が来る。
        //   (クライアント主導だとサーバーの getSpeed が 0 になり、サーバースクリプトが
        //    動かない等、本家と挙動が食い違って面倒になるためサーバー権威に統一。)
        if (!level.isClientSide) {
            if (this.shouldUpdateMotion() && this.getControllingPassenger() instanceof LivingEntity living) {
                this.updateMotion(living, living.xxa, living.zza);
            }
            this.applyPhysicalEffect(); // 本家: 非接地時のみ空気抵抗
            this.updateFallState();     // 本家: 非接地なら落下
            this.updateRotation();      // 本家: 坂でピッチ/ロール
            this.move(MoverType.SELF, this.getDeltaMovement());
            // 移動後の現在位置 (サーバースクリプトは次tickの頭でこれを読む)
            this.field_70165_t = getX();
            this.field_70163_u = getY();
            this.field_70161_v = getZ();
            // 物理で求めた速度を同期 (クライアントのスクリプト getSpeed 用)
            this.publishSpeed();
        } else {
            updateWheelRotationInClient();
        }
    }

    // ===== 本家 EntityVehicle (KaizPatchX) の運転物理 =====

    /** 本家 shouldUpdateMotion: 車は接地時のみ操作できる。 */
    protected boolean shouldUpdateMotion() {
        return this.onGround();
    }

    /** 本家 updateMotion: 加速・旋回・滑り。 */
    protected void updateMotion(LivingEntity entity, float moveStrafe, float moveForward) {
        this.speed += moveForward * ACCELERATION;
        float f0 = -moveStrafe * YAW_COEFFICIENT;
        f0 *= this.speed / MAX_SPEED; // changeYawOnStopping=false (既定)
        f0 = Mth.clamp(f0, -MAX_YAW, MAX_YAW);
        this.setYRot(this.getYRot() + f0);

        this.speed = Mth.clamp(this.speed, -MAX_SPEED, MAX_SPEED);

        Vec3 vec = this.getMotionVec();
        this.setDeltaMovement(vec.x, this.getDeltaMovement().y, vec.z);
        if (moveForward == 0.0F) {
            this.speed *= FRICTION;
        }
        if (Math.abs(this.speed) < 0.001D) {
            this.speed = 0.0F;
            this.setDeltaMovement(0.0D, this.getDeltaMovement().y, 0.0D);
        }
    }

    /** 本家 getMotionVec: 速度が上がるほどヨーの追従が遅れる = 滑り。 */
    protected Vec3 getMotionVec() {
        float f0 = 1.0F - (this.speed / MAX_SPEED);
        float f1 = this.yRotO + (Mth.wrapDegrees(this.getYRot() - this.yRotO) * f0);
        float yaw2 = (this.onGround() || this.isInWater()) ? f1 : this.getYRot();
        float rad = (float) Math.toRadians(yaw2);
        return new Vec3(-Mth.sin(rad) * this.speed, 0.0D, Mth.cos(rad) * this.speed);
    }

    /** 本家 applyPhysicalEffect: 非接地時の空気抵抗。 */
    protected void applyPhysicalEffect() {
        if (!this.shouldUpdateMotion()) {
            this.speed *= 0.9999D;
        }
    }

    /** 本家 updateFallState: 接地していなければ落ちる。 */
    protected void updateFallState() {
        if (this.onGround()) {
            this.setDeltaMovement(this.getDeltaMovement().x, 0.0D, this.getDeltaMovement().z);
        } else {
            this.setDeltaMovement(this.getDeltaMovement().add(0.0D, -0.05D, 0.0D));
        }
    }

    /** 本家 updateRotation: 前後左右のブロック高さからピッチとロールを出す。 */
    protected void updateRotation() {
        float prevPitch = this.getXRot();
        float prevRoll = this.rotationRoll;
        float pitch = prevPitch;
        float roll = prevRoll;

        if (this.onGround() && (this.getDeltaMovement().x != 0.0D || this.getDeltaMovement().z != 0.0D)) {
            double hFront = this.getBlockHeight(this.getYRot());
            double hBack = this.getBlockHeight(this.getYRot() + 180.0F);
            double hLeft = this.getBlockHeight(this.getYRot() + 90.0F);
            double hRight = this.getBlockHeight(this.getYRot() - 90.0F);
            pitch = (float) Math.toDegrees(Math.atan2(hFront - hBack, this.getBbWidth()));
            roll = (float) Math.toDegrees(Math.atan2(hLeft - hRight, this.getBbWidth()));
        } else {
            pitch *= 0.75F;
            roll *= 0.75F;
        }

        if (Math.abs(pitch) < 0.01F) pitch = 0.0F;
        if (Math.abs(roll) < 0.01F) roll = 0.0F;

        this.setXRot(pitch);
        this.rotationRoll = roll;
    }

    /** 本家 getBlockHeight: その方角のブロック上面の高さ。 */
    protected double getBlockHeight(float yaw) {
        float rad = (float) Math.toRadians(yaw);
        double r = this.getBbWidth() * 0.5D;
        int blockX = Mth.floor(this.getX() - Mth.sin(rad) * r);
        int blockZ = Mth.floor(this.getZ() + Mth.cos(rad) * r);
        int blockY = Mth.floor(this.getY()) + 1;
        net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(blockX, blockY, blockZ);
        for (; blockY > this.level().getMinBuildHeight(); --blockY) {
            net.minecraft.world.phys.shapes.VoxelShape shape =
                this.level().getBlockState(pos).getCollisionShape(this.level(), pos);
            if (!shape.isEmpty()) {
                return shape.bounds().maxY + blockY;
            }
            pos = pos.below();
        }
        return this.getY();
    }

    /**
     * 本家 EntityVehicle.getSpeed (スクリプト互換)。
     * サーバーが物理で求めた速度を同期値から読む (本家と同じく両サイドで同じ値)。
     */
    public float getSpeed() {
        return this.entityData.get(DATA_SPEED);
    }

    /**
     * ★サーバー権威にするため false を返す。
     *
     * <p>1.21 の {@code LocalPlayer.tick()} は「操作中の乗り物」に対して
     * {@code ServerboundMoveVehiclePacket} を送る。true のままだとクライアントの
     * 位置でサーバーの権威位置が上書きされ、サーバー側の getSpeed やスクリプトが
     * 本家と食い違う。物理はサーバーだけが回し、クライアントは補間で受ける。
     */
    @Override
    public boolean isControlledByLocalInstance() {
        return false;
    }

    /** サーバー側: 物理で求めた速度を同期値へ書き出す。 */
    private void publishSpeed() {
        this.entityData.set(DATA_SPEED, this.speed);
    }

    // / 車輪の回転角度を更新する クライアントのみ (実移動量から求める)。
    private void updateWheelRotationInClient() {
        this.prevWheelRotation = this.wheelRotation;
        Vec3 m = this.getDeltaMovement();
        double horizontal = Math.sqrt(m.x * m.x + m.z * m.z);
        // 進行方向 (車体前方) との内積で前進/後退の符号を決める
        Vec3 forward = Vec3.directionFromRotation(0.0F, this.getYRot());
        double signed = (m.x * forward.x + m.z * forward.z) < 0 ? -horizontal : horizontal;
        if (WHEEL_RADIUS > 1.0E-5F) {
            this.wheelRotation += (float) (signed / WHEEL_RADIUS);
        }
    }
}
