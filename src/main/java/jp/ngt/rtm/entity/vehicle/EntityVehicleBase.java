package jp.ngt.rtm.entity.vehicle;

import jp.ngt.rtm.modelpack.cfg.TrainConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/** 本家 jp.ngt.rtm.entity.vehicle.EntityVehicleBase の最… */
public abstract class EntityVehicleBase<T extends TrainConfig> extends Entity
        implements jp.ngt.rtm.modelpack.IModelSelector {
    /** 本家 EntityVehicleBase.getBrightnessForRender 相当。 */
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

    /** 転換クロスシートの回転量 (-#MAX_SEAT_ROTATION 〜 #MAX_SEAT_ROT… */
    public int seatRotation;
    public int doorMoveL;
    public int doorMoveR;
    /** 方向幕スクロールアニメ (幕回し) の現在位置 (1/16 単位)。 */
    public int rollsignAnimation;
    /** 方向幕スクロールの目標位置 (= 行先 index * 16)。 */
    public int rollsignV;

    /** RTMU 追加: 転換クロスシートの向き 等の train スクリプトが読む)。 */
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

    // パックスクリプト互換 (1.7.10 SRG 名を直接参照するスクリプトのため tick 毎に更新…
    public jp.ngt.mccompat.WorldCompat field_70170_p;
    public int field_70173_aa;
    public float field_70177_z;
    public float field_70125_A;
    // prevRotationYaw / prevRotationPitch。
    public float field_70126_B;
    public float field_70127_C;
    public Entity field_70153_n;
    // posX / posY / posZ
    public double field_70165_t;
    public double field_70163_u;
    public double field_70161_v;
    // lastTickPosX / lastTickPosY / lastTickPosZ (列車検知器…
    public double field_70169_q;
    public double field_70167_r;
    public double field_70166_s;
    // lastTickPosX / lastTickPosY / lastTickPosZ (1.7.1…
    public double field_70142_S;
    public double field_70137_T;
    public double field_70136_U;
    // boundingBox
    public jp.ngt.mccompat.AxisAlignedBB field_70121_D;

    /** 本家 EntityVehicleBase:43 と同じく、車両 1 体につき 1 個の Scrip… */
    public final jp.ngt.rtm.modelpack.ScriptExecuter scriptExecuter = new jp.ngt.rtm.modelpack.ScriptExecuter();

    private final jp.ngt.rtm.modelpack.state.ResourceState resourceState =
            new jp.ngt.rtm.modelpack.state.ResourceState(this::getResourceName, this::getResourceSetForScript);

    // 本家 vehicleFloors (slotPos 座席)
    protected final java.util.List<jp.ngt.rtm.entity.train.parts.EntityFloor> vehicleFloors = new java.util.ArrayList<>();
    protected boolean floorLoaded;

    public float prevRotationYawVehicle;
    public float prevRotationPitchVehicle;

    // 本家 setPositionAndRotation2/updatePosAndRotationCl…
    /** ★滑らかさのための遅延バッファ。 漸近方式 (1/inc ずつ寄せる) は速度が一定にならず、常に… */
    protected final jp.ngt.rtm.entity.ClientMotionBuffer motionBuffer =
        new jp.ngt.rtm.entity.ClientMotionBuffer();
    protected int vehiclePosRotationInc;
    protected double vehicleX, vehicleY, vehicleZ;
    protected float vehicleYaw, vehiclePitch, vehicleRoll;
    private boolean clientRotInit;

    public EntityVehicleBase(EntityType<?> type, Level level) {
        super(type, level);
        // 本家 EntityVehicleBase:85 this.ignoreFrustumCheck =…
        this.noCulling = true;
        this.noPhysics = true;
        // スクリプトは初回 tick 前 (スポーン直後の描画) にも参照する
        this.field_70170_p = new jp.ngt.mccompat.WorldCompat(level);
    }

    /** 本家 getModelSet.getConfig 相当。暫定: サブクラスが供給。 */
    public abstract T getConfig();

    /** スクリプトの entity.getResourceState.getResourceSet 用。 */
    protected jp.ngt.rtm.modelpack.modelset.ModelSetBase<?> getResourceSetForScript() {
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

        // 床/座席パーツを「車体が動き終わったこの時点」の位置へ揃える。 ★これが無いと走行中に座れなくなる。
        this.updateFloorPositions();

        // 視点追従 (本家 KaizPatchX EntityTrainBase.updateRiderPo…
        this.rotateRiders();

        // パックスクリプト互換 SRG フィールドの更新
        if (this.field_70170_p == null || this.field_70170_p.level != this.level()) {
            this.field_70170_p = new jp.ngt.mccompat.WorldCompat(this.level());
        }
        this.field_70173_aa = this.tickCount;
        // 前 tick の角度は「今の値で上書きする前」に退避する (スクリプトが partialTick…
        this.field_70126_B = this.yRotO;
        this.field_70127_C = this.xRotO;
        this.field_70177_z = this.getYRot();
        this.field_70125_A = this.getXRot();
        this.field_70153_n = this.getPassengers().isEmpty() ? null : this.getPassengers().get(0);
        // 前 tick の位置は「今の値で上書きする前」に退避する (進行方向の算出に使われる)
        this.field_70169_q = this.xOld;
        this.field_70167_r = this.yOld;
        this.field_70166_s = this.zOld;
        this.field_70142_S = this.xOld;
        this.field_70137_T = this.yOld;
        this.field_70136_U = this.zOld;
        this.field_70165_t = this.getX();
        this.field_70163_u = this.getY();
        this.field_70161_v = this.getZ();
        this.field_70121_D = new jp.ngt.mccompat.AxisAlignedBB(this.getBoundingBox());
    }

    /** 車体が向きを変えたぶんだけ、乗客の視点も一緒に回す (カーブでの視点追従)。 */
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
        // ローカルプレイヤーを乗せているなら、この tick の回転量を毎フレームのカメラ補正へ渡す (Ri…
        if (this.getPassengers().contains(net.minecraft.client.Minecraft.getInstance().player)) {
            com.portofino.realtrainmodunofficial.client.RiderViewSmoother.record(dYaw, dPitch);
        }
        if (dYaw == 0.0F && dPitch == 0.0F) {
            return;
        }
        for (Entity rider : this.getPassengers()) {
            // yRotO / xRotO も一緒に動かす。
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

    /** 本家 setupFloors (Server Only): config の slotPos ごと… */
    protected void setupFloors() {
        this.vehicleFloors.stream().filter(java.util.Objects::nonNull).forEach(Entity::discard);
        this.vehicleFloors.clear();
        // ★実行時の一覧だけでは足りない。 空なのに、ワールドには前の座席が生きたまま残っていることがある。
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
                this.floorLoaded = false;// 1つでもスポーン失敗したら、やり直し
                break;
            }
        }
    }

    /** 車体が消えたら座席も消える。 理由を問わず片付ける。 */
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

    /** 本家 getSpeed: 車両速度 (ブロック/tick)。列車側がオーバーライドする。 */
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
    public jp.ngt.rtm.modelpack.modelset.ModelSetBase<?> getModelSet() {
        return this.getResourceSetForScript();
    }

    /** 本家 getModelType: モデル種別 ("ModelTrain" 等)。 */
    public String getModelType() {
        return jp.ngt.rtm.modelpack.cfg.TrainConfig.TYPE;
    }

    // ---- 本家 EntityVehicle (1.12 系) の戦車/車両スクリプト API --…

    /** 砲塔の旋回 (度)。スクリプトの entity.getBarrelYaw() が読む。 */
    protected float barrelYaw;
    protected float barrelPitch;
    /** 発砲後のリコイル 0..1。 */
    protected float recoil;
    /** 前後/横の加速度。サウンドスクリプト (sound_crusader 等) が読む。 */
    protected double accelForward;
    protected double accelStrafe;

    public float getBarrelYaw() {
        return this.barrelYaw;
    }

    public float getBarrelPitch() {
        return this.barrelPitch;
    }

    public float getRecoil() {
        return this.recoil;
    }

    public float getAccelerationForward() {
        return (float) this.accelForward;
    }

    public float getAccelerationStrafe() {
        return (float) this.accelStrafe;
    }

    public void setBarrelYaw(float value) {
        this.barrelYaw = value;
    }

    public void setBarrelPitch(float value) {
        this.barrelPitch = value;
    }

    public void setRecoil(float value) {
        this.recoil = value;
    }

    /** 本家 Entity.onGround をメソッド名で読むスクリプト用 (RenderNGT-18… */
    public boolean isOnGround() {
        return this.onGround();
    }


    /** 本家 useInteriorLight: 室内灯を点けるか (設定で常時オフに出来る)。 */
    public boolean useInteriorLight() {
        return true;
    }

    /** 本家 getBoundingBox。 */
    public net.minecraft.world.phys.AABB getBoundingBox2() {
        return this.getBoundingBox();
    }

    /** 本家 getCollisionBox: 自分に属する台車/パーツとは当たり判定を持たない。 */
    public net.minecraft.world.phys.AABB getCollisionBox(net.minecraft.world.entity.Entity other) {
        if (other instanceof jp.ngt.rtm.entity.train.parts.EntityVehiclePart part && part.getVehicle() == this) {
            return null;
        }
        if (other instanceof jp.ngt.rtm.entity.train.EntityBogie bogie && bogie.getTrain() == this) {
            return null;
        }
        return other == null ? null : other.getBoundingBox();
    }

    /** この車体に紐づく座席がワールドに残っていれば片付ける。 判定はエンティティ IDで行う。 */
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

    /** 本家 onModelChanged: モデルが差し替わった時の後始末。 */
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

    /** 一覧に載っていない自分の座席を周囲から拾い直す。 */
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
        net.minecraft.world.phys.AABB area = this.getBoundingBox().inflate(64.0D, 32.0D, 64.0D);
        for (jp.ngt.rtm.entity.train.parts.EntityFloor floor
                : this.level().getEntitiesOfClass(jp.ngt.rtm.entity.train.parts.EntityFloor.class, area)) {
            this.setFloor(floor);
        }
    }

    /** 床/座席パーツを今の車体位置へ揃える。 パーツ自身の tick でも追従しているが、tick 順に… */
    protected void updateFloorPositions() {
        // ★空でも return しない。空こそが「追従が完全に止まっている」状態で、 ここで拾い直さないと…
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

    /** 本家 fixRiderPos: 降車時に車体の内側へ埋まらないよう、 車体 AABB の外の足場へ… */
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

    private jp.ngt.rtm.modelpack.modelset.ModelSetBase<?> dataFormatterSet;

    public jp.ngt.rtm.modelpack.state.ResourceState getResourceState() {
        jp.ngt.rtm.modelpack.modelset.ModelSetBase<?> set = this.getResourceSetForScript();
        if (set != null && set != this.dataFormatterSet) {
            this.dataFormatterSet = set;
            set.dataFormatter.initDataMap(this.resourceState.getDataMap());
        }
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

    // ---- 本家スクリプト互換 API ---- RTM 標準のレンダースクリプト (Render.…

    /** #seatRotation の生の値 (-45〜45)。 */
    public int getSeatRotationRaw() {
        return this.seatRotation;
    }

    /** 本家 EntityTrainBase.getRollsignAnimation: 方向幕のスクロー… */
    public float getRollsignAnimation() {
        return (float) this.rollsignAnimation / 16.0F;
    }

    /** 本家 EntityTrainBase.setRollsignAnimation: 方向幕スクロール… */
    public void setRollsignAnimation(int destination) {
        this.rollsignV = destination * 16;
    }

    /** ResourceState.getResourceName 用 (モデル名)。 */
    protected String getResourceName() {
        return "";
    }

    // ---- パックスクリプト互換 (1.7.10/1.12 SRG メソッド) ----

    /** 本家 setDead。 */
    public void setDead() {
        this.discard();
    }

    /** 本家 onUpdate。tick 系は各自で処理済み。 */
    public void onUpdate() {
    }

    /** 本家 canBePushed。 */
    public boolean canBePushed() {
        return false;
    }

    /** 本家 shouldRenderInPass: light/alphaBlend モデルは全パスで描… */
    public boolean shouldRenderInPass(int pass) {
        if (pass == 0) {
            return true;
        }
        Object set = null;
        try {
            set = this.getModelSet();
        } catch (Throwable ignored) {
        }
        jp.ngt.rtm.render.ModelObject model = set instanceof jp.ngt.rtm.modelpack.modelset.ModelSetVehicleBaseClient<?> client
                ? client.vehicleModel : null;
        return model != null && (model.light || model.alphaBlend) && pass >= 0;
    }

    /** 本家 getBrightnessForRender(float)。 */
    public int getBrightnessForRender(float partialTick) {
        return this.func_70070_b();
    }

    /** 本家 moveEntity: 位置を delta だけずらす。 */
    public void moveEntity(double x, double y, double z) {
        this.setPos(this.getX() + x, this.getY() + y, this.getZ() + z);
    }

    /** 本家 addVelocity。 */
    public void addVelocity(double x, double y, double z) {
        this.setDeltaMovement(this.getDeltaMovement().add(x, y, z));
    }

    /** 本家 setVelocity。 */
    public void setVelocity(double x, double y, double z) {
        this.setDeltaMovement(x, y, z);
    }

    /** 本家 Entity.setPositionAndRotation。 */
    public void setPositionAndRotation(double x, double y, double z, float yaw, float pitch) {
        this.setPos(x, y, z);
        this.setRot(yaw, pitch);
    }

    /** getBrightnessForRender (packed lightmap; */
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

    /** 本家 EntityVehicleBase.setPositionAndRotation2 相当 (… */
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

    /** 本家 updatePosAndRotationClient の忠実移植 (クライアント補間)。 */
    /** クライアント側で初期姿勢を入れ終えたか。 */
    protected boolean isClientRotInit() {
        return this.clientRotInit;
    }

    /** クライアントで姿勢を補間せず即座に合わせる。 */
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
            // 回転も位置と同じ 1/inc で補間。ヨーはラップ跨ぎ (179→-179 等) で逆回りしないよ…
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
