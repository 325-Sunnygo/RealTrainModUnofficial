package jp.ngt.rtm.entity.vehicle;

import jp.ngt.rtm.modelpack.cfg.TrainConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * 本家 jp.ngt.rtm.entity.vehicle.EntityVehicleBase の最小移植 (Phase 2)。
 * onUpdate 骨格: onVehicleUpdate → (server) updateMovement → applyPhysicalEffect。
 * @param <T> 本家は VehicleBaseConfig; 当面 TrainConfig のみ。
 */
public abstract class EntityVehicleBase<T extends TrainConfig> extends Entity {
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

    public static final int MAX_SEAT_ROTATION = 45;
    public static final int MAX_DOOR_MOVE = 60;
    public static final int MAX_PANTOGRAPH_MOVE = 40;
    public static final float TO_ANGULAR_VELOCITY = (float) (360.0D / Math.PI);

    public float rotationRoll;
    public float prevRotationRoll;

    /**
     * 転換クロスシートの回転量 (-#MAX_SEAT_ROTATION 〜 #MAX_SEAT_ROTATION)。
     * updateAnimation が進行方向へ毎 tick 1 ずつ動かすので、転換は滑らかに進む。
     * ★ Nashorn (Dynalink の BeansLinker) はプロパティ解決でフィールドより getter を優先する。
     */
    public int seatRotation;
    public int doorMoveL;
    public int doorMoveR;
    /** 方向幕スクロールアニメ (幕回し) の現在位置 (1/16 単位)。本家 EntityVehicleBase.rollsignAnimation。 */
    public int rollsignAnimation;
    /** 方向幕スクロールの目標位置 (= 行先 index * 16)。本家 EntityVehicleBase.rollsignV。 */
    public int rollsignV;

    /**
     * RTMU 追加: 転換クロスシートの向き 等の train スクリプトが読む)。
     * -1 = 未確定 (誰も乗っていない → スクリプトは進行方向にフォールバック)、
     * 0 = 前向き / 1 = 後ろ向き。
     */
    private int seatBoardDirection = -1;

    public int getSeatDirection() {
        return this.seatBoardDirection;
    }

    @Override
    protected void addPassenger(Entity passenger) {
        super.addPassenger(passenger);
        if (this.seatBoardDirection < 0 && passenger instanceof net.minecraft.world.entity.player.Player) {
            // 乗車時のプレイヤー向きと車両向きの差で前/後ろ向きを決める
            float rel = Mth.wrapDegrees(passenger.getYRot() - this.getYRot());
            this.seatBoardDirection = Math.abs(rel) <= 90.0F ? 0 : 1;
        }
    }

    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        if (this.getPassengers().stream().noneMatch(p -> p instanceof net.minecraft.world.entity.player.Player)) {
            this.seatBoardDirection = -1;
        }
    }
    public int pantograph_F;
    public int pantograph_B;
    public float wheelRotationR;
    public float wheelRotationL;

    // パックスクリプト互換 (1.7.10 SRG 名を直接参照するスクリプトのため tick 毎に更新)
    // field_70170_p は World の SRG メソッド (func_72929_e 等) を委譲する WorldCompat
    public jp.ngt.mccompat.WorldCompat field_70170_p;
    public int field_70173_aa;
    public float field_70177_z;
    public float field_70125_A;
    // prevRotationYaw / prevRotationPitch。
    // スクリプトは pitch = field_70127_C + (field_70125_A - field_70127_C) * partialTick で補間する。
    public float field_70126_B;
    public float field_70127_C;
    public Entity field_70153_n;
    // posX / posY / posZ
    public double field_70165_t;
    public double field_70163_u;
    public double field_70161_v;
    // lastTickPosX / lastTickPosY / lastTickPosZ
    // (列車検知器のスクリプトは (lastTickPos - pos) で進行方向を出し、
    // 検知器の向きと突き合わせて「どちら向きに通過したか」を判定する)
    public double field_70169_q;
    public double field_70167_r;
    public double field_70166_s;
    // boundingBox
    public jp.ngt.mccompat.AxisAlignedBB field_70121_D;

    /**
     * 本家 EntityVehicleBase:43 と同じく、車両 1 体につき 1 個の ScriptExecuter を永続保持する。
     * 本家は execScript(this) が onUpdate(entity, executer) を呼び、毎 tick count を進める。
     */
    public final jp.ngt.rtm.modelpack.ScriptExecuter scriptExecuter = new jp.ngt.rtm.modelpack.ScriptExecuter();

    private final jp.ngt.rtm.modelpack.state.ResourceState resourceState =
            new jp.ngt.rtm.modelpack.state.ResourceState(this::getResourceName, this::getResourceSetForScript);

    // 本家 vehicleFloors (slotPos 座席)
    protected final java.util.List<jp.ngt.rtm.entity.train.parts.EntityFloor> vehicleFloors = new java.util.ArrayList<>();
    protected boolean floorLoaded;

    public float prevRotationYawVehicle;
    public float prevRotationPitchVehicle;

    // 本家 setPositionAndRotation2/updatePosAndRotationClient のクライアント補間
    /**
     * ★滑らかさのための遅延バッファ。
     * 漸近方式 (1/inc ずつ寄せる) は速度が一定にならず、常に約 3 tick 遅れる。
     * 届いた位置を時刻つきで貯めて「今 − 遅延」を再生すると、速度が一定になり遅れも減る。
     */
    protected final jp.ngt.rtm.entity.ClientMotionBuffer motionBuffer =
        new jp.ngt.rtm.entity.ClientMotionBuffer();
    protected int vehiclePosRotationInc;
    protected double vehicleX, vehicleY, vehicleZ;
    protected float vehicleYaw, vehiclePitch, vehicleRoll;
    private boolean clientRotInit;

    public EntityVehicleBase(EntityType<?> type, Level level) {
        super(type, level);
        // 本家 EntityVehicleBase:85  this.ignoreFrustumCheck = true;
        // 車両は自分の当たり判定より遥かに大きく描かれる (連結・台車・スクリプト描画) ため、
        // 視錐台カリングに任せると端から消える。
        this.noCulling = true;
        this.noPhysics = true;
        // スクリプトは初回 tick 前 (スポーン直後の描画) にも参照する
        this.field_70170_p = new jp.ngt.mccompat.WorldCompat(level);
    }

    /** 本家 getModelSet.getConfig 相当。暫定: サブクラスが供給。 */
    public abstract T getConfig();

    /**
     * スクリプトの entity.getResourceState.getResourceSet 用。
     * 本家 ResourceState.getResourceSet (= getModelSet 相当) を返す。
     */
    protected jp.ngt.rtm.modelpack.modelset.ModelSetCompat getResourceSetForScript() {
        return null;
    }

    @Override
    public void tick() {
        this.prevRotationRoll = this.rotationRoll;
        this.prevRotationYawVehicle = this.getYRot();
        this.prevRotationPitchVehicle = this.getXRot();

        this.baseTick();

        if (this.level().isClientSide) {
            this.updatePosAndRotationClient();
            this.updateAnimation();
        }

        this.onVehicleUpdate();

        if (!this.level().isClientSide) {
            if (!this.floorLoaded) {
                this.setupFloors();
            }
            this.updateMovement();
        }

        this.applyPhysicalEffect();

        // 床/座席パーツを「車体が動き終わったこの時点」の位置へ揃える。
        // ★これが無いと走行中に座れなくなる。
        this.updateFloorPositions();

        // 視点追従 (本家 KaizPatchX EntityTrainBase.updateRiderPosition)
        this.rotateRiders();

        // パックスクリプト互換 SRG フィールドの更新
        if (this.field_70170_p == null || this.field_70170_p.level != this.level()) {
            this.field_70170_p = new jp.ngt.mccompat.WorldCompat(this.level());
        }
        this.field_70173_aa = this.tickCount;
        // 前 tick の角度は「今の値で上書きする前」に退避する (スクリプトが partialTick 補間に使う)
        this.field_70126_B = this.yRotO;
        this.field_70127_C = this.xRotO;
        this.field_70177_z = this.getYRot();
        this.field_70125_A = this.getXRot();
        this.field_70153_n = this.getPassengers().isEmpty() ? null : this.getPassengers().get(0);
        // 前 tick の位置は「今の値で上書きする前」に退避する (進行方向の算出に使われる)
        this.field_70169_q = this.xOld;
        this.field_70167_r = this.yOld;
        this.field_70166_s = this.zOld;
        this.field_70165_t = this.getX();
        this.field_70163_u = this.getY();
        this.field_70161_v = this.getZ();
        this.field_70121_D = new jp.ngt.mccompat.AxisAlignedBB(this.getBoundingBox());
    }

    /**
     * 車体が向きを変えたぶんだけ、乗客の視点も一緒に回す (カーブでの視点追従)。
     * 本家 KaizPatchX EntityTrainBase.updateRiderPosition:
     */
    private void rotateRiders() {
        if (!this.level().isClientSide || this.getPassengers().isEmpty()) {
            return;
        }
        // フリーカメラ中は視点追従オフ (カーブで乗員の視点を回さない)
        if (com.portofino.realtrainmodunofficial.client.FreeCameraController.isActive()) {
            return;
        }
        float dYaw = net.minecraft.util.Mth.wrapDegrees(this.getYRot() - this.prevRotationYawVehicle);
        float dPitch = net.minecraft.util.Mth.wrapDegrees(this.getXRot() - this.prevRotationPitchVehicle);
        // ローカルプレイヤーを乗せているなら、この tick の回転量を毎フレームのカメラ補正へ渡す
        // (RiderViewSmoother がサブtick補間して視点追従を滑らかにする)。直進 (0) でも記録して
        // 前カーブの値を残さない。yRot/yRotO への瞬時適用は従来どおりで、カメラ描画だけ滑らかに。
        if (this.getPassengers().contains(net.minecraft.client.Minecraft.getInstance().player)) {
            com.portofino.realtrainmodunofficial.client.RiderViewSmoother.record(dYaw, dPitch);
        }
        if (dYaw == 0.0F && dPitch == 0.0F) {
            return;
        }
        for (Entity rider : this.getPassengers()) {
            // yRotO / xRotO も一緒に動かす。動かさないと補間が 1 tick ぶん引っ張られて
            // カーブのたびに視点がガクつく。
            rider.setYRot(rider.getYRot() - dYaw);
            rider.yRotO -= dYaw;
            rider.setYHeadRot(rider.getYHeadRot() - dYaw);
            rider.setXRot(rider.getXRot() - dPitch);
            rider.xRotO -= dPitch;
            if (rider instanceof net.minecraft.world.entity.LivingEntity living) {
                living.yBodyRot -= dYaw;
                living.yBodyRotO -= dYaw;
            }
        }
    }

    /** 本家 updateAnimation (Client): 車輪回転。ドア/パンタ/座席はサブクラス。 */
    protected void updateAnimation() {
        float speed = this.getVehicleSpeed();
        float f0 = speed * TO_ANGULAR_VELOCITY * this.getConfig().wheelRotationSpeed * this.getMoveDir();
        this.wheelRotationR = (this.wheelRotationR + f0) % 360.0F;
        this.wheelRotationL = (this.wheelRotationL + f0) % 360.0F;
    }

    protected float getMoveDir() {
        return 1.0F;
    }

    /** 本家 setupFloors (Server Only): config の slotPos ごとに EntityFloor をスポーン。 */
    protected void setupFloors() {
        this.vehicleFloors.stream().filter(java.util.Objects::nonNull).forEach(Entity::discard);
        this.vehicleFloors.clear();
        // ★実行時の一覧だけでは足りない。
        // 空なのに、ワールドには前の座席が生きたまま残っていることがある。
        this.discardStrayFloors();

        this.floorLoaded = true;
        float[][] slots = this.getConfig().getSlotPos();
        if (slots == null) {
            return;
        }
        for (float[] fa : slots) {
            if (fa == null || fa.length < 3) {
                continue;
            }
            byte type = fa.length >= 4 ? (byte) fa[3] : (byte) 2;
            jp.ngt.rtm.entity.train.parts.EntityFloor floor = new jp.ngt.rtm.entity.train.parts.EntityFloor(
                    jp.ngt.rtm.entity.RTMEntities.FLOOR.get(), this.level(), this,
                    new float[]{fa[0], fa[1], fa[2]}, type);
            if (this.level().addFreshEntity(floor)) {
                this.vehicleFloors.add(floor);
            } else {
                this.floorLoaded = false;//1つでもスポーン失敗したら、やり直し
                break;
            }
        }
    }

    /**
     * 車体が消えたら座席も消える。
     * 理由を問わず片付ける。
     */
    @Override
    public void remove(RemovalReason reason) {
        super.remove(reason);
        if (!this.level().isClientSide) {
            this.vehicleFloors.stream().filter(java.util.Objects::nonNull).forEach(Entity::discard);
            this.vehicleFloors.clear();
            this.discardStrayFloors();
        }
    }

    /** 本家 getSpeed 相当 (アニメ用)。 */
    public float getVehicleSpeed() {
        return 0.0F;
    }

    /**
     * 本家 getSpeed: 車両速度 (ブロック/tick)。列車側がオーバーライドする。
     * 本家は abstract だが、RTMU では車以外も同じ基底を使うため既定を返す。
     */
    public float getSpeed() {
        return this.getVehicleSpeed();
    }

    /** 本家 getModelName: 選択中のモデル名。 */
    public String getModelName() {
        return this.getResourceName();
    }

    /** 本家 setModelName: 既定は何もしない (列車/車が実装)。 */
    public void setModelName(String name) {
    }

    /** 本家 getDefaultName: モデル未指定時のフォールバック名。 */
    public String getDefaultName() {
        return "";
    }

    /** 本家 getModelSet: このモデル名に対応する ModelSet。 */
    public jp.ngt.rtm.modelpack.modelset.ModelSetCompat getModelSet() {
        return this.getResourceSetForScript();
    }

    /** 本家 getModelType: モデル種別 ("ModelTrain" 等)。 */
    public String getModelType() {
        return jp.ngt.rtm.modelpack.cfg.TrainConfig.TYPE;
    }

    /**
     * 本家 useInteriorLight: 室内灯を点けるか (設定で常時オフに出来る)。
     * shouldUseInteriorLight は「これ AND 周囲が暗い」で判定する。
     */
    public boolean useInteriorLight() {
        return true;
    }

    /** 本家 getBoundingBox。 */
    public net.minecraft.world.phys.AABB getBoundingBox2() {
        return this.getBoundingBox();
    }

    /**
     * 本家 getCollisionBox: 自分に属する台車/パーツとは当たり判定を持たない。
     * 持たせると自車のパーツに押し返されて車両が跳ねる。
     */
    public net.minecraft.world.phys.AABB getCollisionBox(net.minecraft.world.entity.Entity other) {
        if (other instanceof jp.ngt.rtm.entity.train.parts.EntityVehiclePart part && part.getVehicle() == this) {
            return null;
        }
        if (other instanceof jp.ngt.rtm.entity.train.EntityBogie bogie && bogie.getTrain() == this) {
            return null;
        }
        return other == null ? null : other.getBoundingBox();
    }

    /**
     * この車体に紐づく座席がワールドに残っていれば片付ける。
     * 判定はエンティティ IDで行う。
     */
    private void discardStrayFloors() {
        if (this.level().isClientSide()) {
            return;
        }
        net.minecraft.world.phys.AABB area = this.getBoundingBox().inflate(48.0D, 16.0D, 48.0D);
        for (jp.ngt.rtm.entity.train.parts.EntityFloor floor
                : this.level().getEntitiesOfClass(jp.ngt.rtm.entity.train.parts.EntityFloor.class, area)) {
            if (floor.getVehicleId() == this.getId()) {
                floor.discard();
            }
        }
    }

    /**
     * 本家 onModelChanged: モデルが差し替わった時の後始末。
     * サーバー側は座席 (slotPos) がモデル依存なので床を作り直させる
     * (floorLoaded=false で次 tick に setupFloors が走る)。
     */
    public void onModelChanged() {
        if (!this.level().isClientSide()) {
            // 古い座席を消してから作り直す (新モデルの slotPos で並べ直すため)
            this.vehicleFloors.stream().filter(java.util.Objects::nonNull).forEach(Entity::discard);
            this.vehicleFloors.clear();
            this.floorLoaded = false;
        } else {
            com.portofino.realtrainmodunofficial.client.sound.LegacyScriptSoundManager.stopAll(this);
        }
    }

    /** 本家 setFloor: EntityFloor から自分を登録してもらう。 */
    public void setFloor(jp.ngt.rtm.entity.train.parts.EntityFloor floor) {
        if (floor != null && floor.getVehicleId() == this.getId() && !this.vehicleFloors.contains(floor)) {
            this.vehicleFloors.add(floor);
        }
    }

    /** 次に座席の取りこぼしを探しに行くまでの残り tick。 */
    private int floorScanCooldown;

    /** config 上あるべき座席の数。 */
    private int expectedFloorCount() {
        T cfg = this.getConfig();
        if (cfg == null) {
            return 0;
        }
        float[][] slots = cfg.getSlotPos();
        return slots == null ? 0 : slots.length;
    }

    /**
     * 一覧に載っていない自分の座席を周囲から拾い直す。
     * 座席の登録は座席側から行う (onAddedToLevel / 座席の tick) が、これは
     * 取りこぼしうる。
     */
    private void rescanFloors() {
        if (this.floorScanCooldown > 0) {
            --this.floorScanCooldown;
            return;
        }
        this.floorScanCooldown = 20;
        if (this.vehicleFloors.size() >= this.expectedFloorCount()) {
            return;
        }
        // 置いていかれた座席は車体から離れている可能性があるので広めに探す。
        // ID が一致するものだけ拾うので、他車両の座席を取り込む心配は無い。
        net.minecraft.world.phys.AABB area = this.getBoundingBox().inflate(64.0D, 32.0D, 64.0D);
        for (jp.ngt.rtm.entity.train.parts.EntityFloor floor
                : this.level().getEntitiesOfClass(jp.ngt.rtm.entity.train.parts.EntityFloor.class, area)) {
            this.setFloor(floor);
        }
    }

    /**
     * 床/座席パーツを今の車体位置へ揃える。
     * パーツ自身の tick でも追従しているが、tick 順によっては車体より先に回って
     * 1 tick 前の位置を使う。
     * ★座席が自分では tick していない場合 (チャンクの状態等) でも、車体は動いて
     * いるのでここだけは必ず回る。
     */
    protected void updateFloorPositions() {
        // ★空でも return しない。空こそが「追従が完全に止まっている」状態で、
        // ここで拾い直さないと永久に復帰しない。
        this.rescanFloors();
        if (this.vehicleFloors.isEmpty()) {
            return;
        }
        // 消えた床は取り除く (クライアントでは床が個別に消えるので溜まる)
        this.vehicleFloors.removeIf(f -> f == null || f.isRemoved());
        for (jp.ngt.rtm.entity.train.parts.EntityFloor floor : this.vehicleFloors) {
            floor.updatePartPos(this);
        }
    }

    /**
     * 本家 fixRiderPos: 降車時に車体の内側へ埋まらないよう、
     * 車体 AABB の外の足場へ押し出す。
     */
    public static void fixRiderPos(net.minecraft.world.entity.LivingEntity entity,
                                   net.minecraft.world.entity.Entity vehicle) {
        if (entity == null || vehicle == null) {
            return;
        }
        net.minecraft.world.phys.AABB aabb = vehicle.getBoundingBox();
        if (entity.getX() < aabb.minX || entity.getX() >= aabb.maxX
                || entity.getZ() < aabb.minZ || entity.getZ() >= aabb.maxZ) {
            return;
        }
        double range = 0.5D;
        int y = net.minecraft.util.Mth.floor(aabb.minY);
        // 車体 AABB のすぐ外側 4 方向のうち、立てる所へ寄せる
        double[][] candidates = {
                {aabb.minX - range, entity.getZ()},
                {aabb.maxX + range, entity.getZ()},
                {entity.getX(), aabb.minZ - range},
                {entity.getX(), aabb.maxZ + range}};
        for (double[] c : candidates) {
            net.minecraft.core.BlockPos pos = net.minecraft.core.BlockPos.containing(c[0], y, c[1]);
            if (entity.level().getBlockState(pos).isAir()
                    && !entity.level().getBlockState(pos.below()).isAir()) {
                entity.teleportTo(c[0], y, c[1]);
                return;
            }
        }
    }

    public jp.ngt.rtm.modelpack.state.ResourceState getResourceState() {
        return this.resourceState;
    }

    /** 本家getRoll: 車体ロール角。 */
    public float getRoll() {
        return this.rotationRoll;
    }

    /** 本家setSpeed: 既定は何もしない(列車側がオーバーライド)。 */
    public void setSpeed(float par1) {
    }

    /** 本家setRollAndSpeed。 */
    public void setRollAndSpeed(float speed, float roll) {
        this.setSpeed(speed);
        this.vehicleRoll = roll;
    }

    /** 本家closeGui。 */
    public boolean closeGui(String par1, Object par2) {
        return true;
    }

    /** 本家getPos: {entityId, -1, 0}。 */
    public int[] getPos() {
        return new int[]{this.getId(), -1, 0};
    }

    /** 本家shouldUseInteriorLight: 周囲光が暗ければ室内灯を使う。 */
    public boolean shouldUseInteriorLight() {
        int x = net.minecraft.util.Mth.floor(this.getX());
        int y = net.minecraft.util.Mth.floor(this.getY() + 0.5D);
        int z = net.minecraft.util.Mth.floor(this.getZ());
        return jp.ngt.ngtlib.util.NGTUtil.getLightValue(this.level(), x, y, z) < 7;
    }

    // ---- 本家スクリプト互換 API ----
    // RTM 標準のレンダースクリプト (Render.js 等) が entity に対して直接呼ぶメソッド群。

    /**
     * 本家 EntityVehicleBase.getSeatRotation: 転換クロスシートの回転量を -1.0〜1.0 に正規化して返す。
     * スクリプト側 (Render.js) は entity.getSeatRotation * 15.0 のように使う。
     */
    public float getSeatRotation() {
        return (float) this.seatRotation / (float) MAX_SEAT_ROTATION;
    }

    /**
     * #seatRotation の生の値 (-45〜45)。
     * パックの entity.seatRotation は PackScriptSource でこちらへ振り替えられる。
     */
    public int getSeatRotationRaw() {
        return this.seatRotation;
    }

    /**
     * 本家 EntityTrainBase.getRollsignAnimation: 方向幕のスクロール位置 (連続値・行先index基準)。
     * rollsignAnimation は毎 tick 目標 rollsignV (=行先*16) へ ±1 で寄るので、
     * 16 で割ると「今どの行先フレームを表示中か」を小数で表す (幕回しアニメ)。列車以外は 0。
     */
    public float getRollsignAnimation() {
        return (float) this.rollsignAnimation / 16.0F;
    }

    /**
     * 本家 EntityTrainBase.setRollsignAnimation: 方向幕スクロールの目標フレームを設定する。
     * 実際の rollsignAnimation はこの目標へ毎 tick 1 ずつ寄り、滑らかに幕が回る。
     */
    public void setRollsignAnimation(int destination) {
        this.rollsignV = destination * 16;
    }

    /** ResourceState.getResourceName 用 (モデル名)。 */
    protected String getResourceName() {
        return "";
    }

    // ---- パックスクリプト互換 (1.7.10/1.12 SRG メソッド) ----

    /** getBrightnessForRender (packed lightmap; 1.21 と同レイアウト sky<<20|block<<4) */
    public int func_70070_b() {
        BlockPos pos = BlockPos.containing(this.getX(), this.getY() + 0.5D, this.getZ());
        return net.minecraft.client.renderer.LevelRenderer.getLightColor(this.level(), pos);
    }

    public int func_70070_b(float partialTick) {
        return this.func_70070_b();
    }

    /** getEntityId */
    public int func_145782_y() {
        return this.getId();
    }

    /** isBeingRidden */
    public boolean func_184207_aI() {
        return !this.getPassengers().isEmpty();
    }

    /**
     * 本家 EntityVehicleBase.setPositionAndRotation2 相当 (エンティティトラッカーからの位置同期)。
     * 回転はバニラパケットだとバイト量子化 (約1.4°刻み) で段階的になるため受け取らず、
     * float 同期 (EntityTrainBase の DATA_YAW/DATA_PITCH → vehicleYaw/vehiclePitch) を使う。
     */
    @Override
    public void lerpTo(double x, double y, double z, float yaw, float pitch, int steps) {
        this.vehiclePosRotationInc = steps;
        this.vehicleX = x;
        this.vehicleY = y;
        this.vehicleZ = z;
        if (!this.clientRotInit) {
            // スポーン直後の初期姿勢のみパケット値を採用 (以降は float 同期)
            this.vehicleYaw = yaw;
            this.vehiclePitch = pitch;
        }
        // 遅延バッファへ到着時刻つきで積む。姿勢は float 同期側の値を使う
        this.motionBuffer.push(x, y, z, this.vehicleYaw, this.vehiclePitch, this.vehicleRoll);
    }

    /**
     * 本家 updatePosAndRotationClient の忠実移植 (クライアント補間)。
     * 位置・回転 (ヨー/ピッチ/ロール) をどちらも同じ 1/inc で漸近補間する。
     */
    /** クライアント側で初期姿勢を入れ終えたか。 */
    protected boolean isClientRotInit() {
        return this.clientRotInit;
    }

    /**
     * クライアントで姿勢を<b>補間せず即座に</b>合わせる。
     *
     * <p>スポーンパケットの yaw は byte 1 個ぶんの粗い値 (車両は 0 のことが多い) なので、
     * 最初の tick で {@link #updatePosAndRotationClient} が直すまでの<b>1 フレームだけ
     * 横を向いた車両が描かれる</b> (チャンク外から近付いたときに一瞬横向きに出るのがこれ)。
     * 実姿勢が同期された時点でここを呼び、tick を待たずに向きを確定させる。
     */
    protected void snapClientRotation(float yaw, float pitch) {
        this.vehicleYaw = yaw;
        this.vehiclePitch = pitch;
        this.setRot(yaw, pitch);
        this.yRotO = yaw;
        this.xRotO = pitch;
        this.prevRotationYawVehicle = yaw;
        this.prevRotationPitchVehicle = pitch;
        this.clientRotInit = true;
    }

    protected void updatePosAndRotationClient() {
        if (!this.clientRotInit) {
            this.clientRotInit = true;
            this.setRot(this.vehicleYaw, this.vehiclePitch);
            this.yRotO = this.vehicleYaw;
            this.xRotO = this.vehiclePitch;
            this.rotationRoll = this.vehicleRoll;
            this.prevRotationRoll = this.vehicleRoll;
        }
        // ★まず遅延バッファ。届いた位置を時刻で再生するので速度が一定になる。
        // 標本が足りない間 (湧いた直後・テレポート直後) だけ従来の漸近方式へ落とす。
        if (this.motionBuffer.sample()) {
            this.setPos(this.motionBuffer.outX, this.motionBuffer.outY, this.motionBuffer.outZ);
            this.setRot(this.motionBuffer.outYaw, this.motionBuffer.outPitch);
            this.rotationRoll = this.motionBuffer.outRoll;
            this.vehiclePosRotationInc = 0;
            return;
        }
        if (this.vehiclePosRotationInc > 0) {
            float d0 = 1.0F / (float) this.vehiclePosRotationInc;
            double x = this.getX() + (this.vehicleX - this.getX()) * d0;
            double y = this.getY() + (this.vehicleY - this.getY()) * d0;
            double z = this.getZ() + (this.vehicleZ - this.getZ()) * d0;
            // 回転も位置と同じ 1/inc で補間。ヨーはラップ跨ぎ (179→-179 等) で逆回りしないよう
            // wrapDegrees で現在値の近傍へ展開してから寄せる。
            float yaw = this.getYRot() + Mth.wrapDegrees(this.vehicleYaw - this.getYRot()) * d0;
            float pitch = this.getXRot() + (this.vehiclePitch - this.getXRot()) * d0;
            this.rotationRoll += (this.vehicleRoll - this.rotationRoll) * d0;
            --this.vehiclePosRotationInc;
            this.setPos(x, y, z);
            this.setRot(yaw, pitch);
        }
    }

    protected void onVehicleUpdate() {
    }

    protected void updateMovement() {
    }

    protected void applyPhysicalEffect() {
    }

    public void setPositionAndRotationDirect(double x, double y, double z, float yaw, float pitch) {
        this.setPos(x, y, z);
        this.setRotationWrapped(yaw, pitch);
    }

    protected void setRotationWrapped(float yaw, float pitch) {
        this.setYRot(Mth.wrapDegrees(yaw));
        this.setXRot(Mth.wrapDegrees(pitch));
    }

    @Override
    public boolean isPickable() {
        return !this.isRemoved();
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }
}
