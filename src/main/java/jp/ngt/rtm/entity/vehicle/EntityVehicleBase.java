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
 * モデル/リソース状態/GUI 系は段階移植 (TODO Phase 4)。
 *
 * @param <T> 本家は VehicleBaseConfig; 当面 TrainConfig のみ。
 */
public abstract class EntityVehicleBase<T extends TrainConfig> extends Entity {
    public static final int MAX_SEAT_ROTATION = 45;
    public static final int MAX_DOOR_MOVE = 60;
    public static final int MAX_PANTOGRAPH_MOVE = 40;
    public static final float TO_ANGULAR_VELOCITY = (float) (360.0D / Math.PI);

    public float rotationRoll;
    public float prevRotationRoll;

    /**
     * 転換クロスシートの回転量 (-{@link #MAX_SEAT_ROTATION} 〜 {@link #MAX_SEAT_ROTATION})。
     * {@code updateAnimation} が進行方向へ毎 tick 1 ずつ動かすので、転換は滑らかに進む。
     * <p>
     * ★ Nashorn (Dynalink の BeansLinker) はプロパティ解決で<b>フィールドより getter を優先する</b>。
     * つまり {@code getSeatRotation()} が存在すると、スクリプト中の {@code entity.seatRotation} は
     * フィールドではなく getter を返す。本家 getSeatRotation() は {@code seatRotation / 45.0F} を
     * 返すので、{@code entity.seatRotation / 45} と書いているパック (小田急 30000 形など) では
     * 45 で二重に割られて座席が動かなくなる。
     * <p>
     * しかし本家 RTM の標準スクリプト (Render223.js 等) は {@code entity.getSeatRotation()} を呼ぶので、
     * getter を消すと今度はそちらが落ちる。両立させるため:
     * <ul>
     *   <li>{@link #getSeatRotation()} は本家どおり {@code seatRotation / 45.0F} を返す</li>
     *   <li>{@link #getSeatRotationRaw()} が生の値を返す</li>
     *   <li>{@code PackScriptSource} がスクリプト中の {@code .seatRotation} を
     *       {@code .getSeatRotationRaw()} に書き換えるので、パック側は今までどおり生の値を読む</li>
     * </ul>
     */
    public int seatRotation;
    public int doorMoveL;
    public int doorMoveR;
    /** 方向幕スクロールアニメ (幕回し) の現在位置 (1/16 単位)。本家 EntityVehicleBase.rollsignAnimation。 */
    public int rollsignAnimation;
    /** 方向幕スクロールの目標位置 (= 行先 index * 16)。本家 EntityVehicleBase.rollsignV。 */
    public int rollsignV;

    /**
     * RTMU 追加: 転換クロスシートの向き (E257 等の train スクリプトが読む)。
     * -1 = 未確定 (誰も乗っていない → スクリプトは進行方向にフォールバック)、
     * 0 = 前向き / 1 = 後ろ向き。<b>最初のプレイヤーが乗った瞬間の向き</b>で確定し、
     * 着席後に振り向いても変わらない。全員降りたら未確定に戻る。
     */
    private int seatBoardDirection = -1;

    public int getSeatDirection() {
        return this.seatBoardDirection;
    }

    @Override
    protected void addPassenger(Entity passenger) {
        super.addPassenger(passenger);
        if (this.seatBoardDirection < 0 && passenger instanceof net.minecraft.world.entity.player.Player) {
            //乗車時のプレイヤー向きと車両向きの差で前/後ろ向きを決める
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

    //パックスクリプト互換 (1.7.10 SRG 名を直接参照するスクリプトのため tick 毎に更新)
    //field_70170_p は World の SRG メソッド (func_72929_e 等) を委譲する WorldCompat
    public jp.ngt.mccompat.WorldCompat field_70170_p;
    public int field_70173_aa;
    public float field_70177_z;
    public float field_70125_A;
    //prevRotationYaw / prevRotationPitch。
    //スクリプトは pitch = field_70127_C + (field_70125_A - field_70127_C) * partialTick で補間する。
    //欠けていると undefined→NaN になり glRotatef(NaN) で行列が壊れ、車体が丸ごと消える
    //(MultiFunctionCarsPack の車が透明になっていた原因)。
    public float field_70126_B;
    public float field_70127_C;
    public Entity field_70153_n;
    //posX / posY / posZ
    public double field_70165_t;
    public double field_70163_u;
    public double field_70161_v;
    //lastTickPosX / lastTickPosY / lastTickPosZ
    //(列車検知器のスクリプトは (lastTickPos - pos) で進行方向を出し、
    // 検知器の向きと突き合わせて「どちら向きに通過したか」を判定する)
    public double field_70169_q;
    public double field_70167_r;
    public double field_70166_s;
    //boundingBox
    public jp.ngt.mccompat.AxisAlignedBB field_70121_D;

    private final jp.ngt.rtm.modelpack.state.ResourceState resourceState =
            new jp.ngt.rtm.modelpack.state.ResourceState(this::getResourceName, this::getResourceSetForScript);

    //本家 vehicleFloors (slotPos 座席)
    protected final java.util.List<jp.ngt.rtm.entity.train.parts.EntityFloor> vehicleFloors = new java.util.ArrayList<>();
    protected boolean floorLoaded;

    public float prevRotationYawVehicle;
    public float prevRotationPitchVehicle;

    //本家 setPositionAndRotation2/updatePosAndRotationClient のクライアント補間
    protected int vehiclePosRotationInc;
    protected double vehicleX, vehicleY, vehicleZ;
    protected float vehicleYaw, vehiclePitch, vehicleRoll;
    private boolean clientRotInit;

    public EntityVehicleBase(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        //スクリプトは初回 tick 前 (スポーン直後の描画) にも参照する
        this.field_70170_p = new jp.ngt.mccompat.WorldCompat(level);
    }

    /**
     * 本家 getModelSet().getConfig() 相当。暫定: サブクラスが供給。
     */
    public abstract T getConfig();

    /**
     * スクリプトの {@code entity.getResourceState().getResourceSet()} 用。
     * 本家 ResourceState.getResourceSet() (= getModelSet() 相当) を返す。
     * 既定は null (供給元の無い車種)。列車は {@code EntityTrainBase} が getModelSet() を返す。
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

        //床/座席パーツを「車体が動き終わったこの時点」の位置へ揃える。
        //
        //★これが無いと走行中に座れなくなる。パーツ側も自分の tick で追従するが、
        //エンティティの tick 順は登録順で決まり、パーツが車体より先に回ると
        //<b>1 tick 前の車体位置</b>を使ってしまう。最高速では 1 tick で 1.8 ブロック進むため、
        //当たり判定だけが車両の後方に取り残され、クリックしても座席に届かない。
        this.updateFloorPositions();

        //視点追従 (本家 KaizPatchX EntityTrainBase.updateRiderPosition)
        this.rotateRiders();

        //パックスクリプト互換 SRG フィールドの更新
        if (this.field_70170_p == null || this.field_70170_p.level != this.level()) {
            this.field_70170_p = new jp.ngt.mccompat.WorldCompat(this.level());
        }
        this.field_70173_aa = this.tickCount;
        //前 tick の角度は「今の値で上書きする前」に退避する (スクリプトが partialTick 補間に使う)
        this.field_70126_B = this.yRotO;
        this.field_70127_C = this.xRotO;
        this.field_70177_z = this.getYRot();
        this.field_70125_A = this.getXRot();
        this.field_70153_n = this.getPassengers().isEmpty() ? null : this.getPassengers().get(0);
        //前 tick の位置は「今の値で上書きする前」に退避する (進行方向の算出に使われる)
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
     * <p>
     * 本家 KaizPatchX {@code EntityTrainBase.updateRiderPosition}:
     * <pre>
     *   //運転手のYaw調整, PlayerのYawは他のEntityとは逆向き
     *   riddenByEntity.rotationYaw   -= wrapAngleTo180(rotationYaw   - prevRotationYaw);
     *   riddenByEntity.rotationPitch -= wrapAngleTo180(rotationPitch - prevRotationPitch);
     * </pre>
     * <p>
     * 符号が引き算なのは、<b>車体の yaw (RTM 系: 90° = +X) と Minecraft のプレイヤー yaw
     * (90° = −X) で X の符号が逆</b>だから。車体が RTM 系で +θ 回れば、同じ向きを向くために
     * プレイヤーの yaw は −θ 動かす必要がある。
     * <p>
     * クライアントだけで行う。プレイヤーの視点はクライアントが持ち主で、毎tickサーバーへ
     * 送られるため、サーバー側で回しても上書きされて意味がない (他プレイヤーの視点は
     * そのプレイヤー自身のクライアントが回す)。
     */
    private void rotateRiders() {
        if (!this.level().isClientSide || this.getPassengers().isEmpty()) {
            return;
        }
        //フリーカメラ中は視点追従オフ (カーブで乗員の視点を回さない)
        if (com.portofino.realtrainmodunofficial.client.FreeCameraController.isActive()) {
            return;
        }
        float dYaw = net.minecraft.util.Mth.wrapDegrees(this.getYRot() - this.prevRotationYawVehicle);
        float dPitch = net.minecraft.util.Mth.wrapDegrees(this.getXRot() - this.prevRotationPitchVehicle);
        //ローカルプレイヤーを乗せているなら、この tick の回転量を毎フレームのカメラ補正へ渡す
        //(RiderViewSmoother がサブtick補間して視点追従を滑らかにする)。直進 (0) でも記録して
        //前カーブの値を残さない。yRot/yRotO への瞬時適用は従来どおりで、カメラ描画だけ滑らかに。
        if (this.getPassengers().contains(net.minecraft.client.Minecraft.getInstance().player)) {
            com.portofino.realtrainmodunofficial.client.RiderViewSmoother.record(dYaw, dPitch);
        }
        if (dYaw == 0.0F && dPitch == 0.0F) {
            return;
        }
        for (Entity rider : this.getPassengers()) {
            //yRotO / xRotO も一緒に動かす。動かさないと補間が 1 tick ぶん引っ張られて
            //カーブのたびに視点がガクつく。
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

    /**
     * 本家 updateAnimation (Client): 車輪回転。ドア/パンタ/座席はサブクラス。
     */
    protected void updateAnimation() {
        float speed = this.getVehicleSpeed();
        float f0 = speed * TO_ANGULAR_VELOCITY * this.getConfig().wheelRotationSpeed * this.getMoveDir();
        this.wheelRotationR = (this.wheelRotationR + f0) % 360.0F;
        this.wheelRotationL = (this.wheelRotationL + f0) % 360.0F;
    }

    protected float getMoveDir() {
        return 1.0F;
    }

    /**
     * 本家 setupFloors (Server Only): config の slotPos ごとに EntityFloor をスポーン。
     */
    protected void setupFloors() {
        this.vehicleFloors.stream().filter(java.util.Objects::nonNull).forEach(Entity::discard);
        this.vehicleFloors.clear();

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

    @Override
    public void remove(RemovalReason reason) {
        super.remove(reason);
        if (!this.level().isClientSide && reason.shouldDestroy()) {
            this.vehicleFloors.stream().filter(java.util.Objects::nonNull).forEach(Entity::discard);
            this.vehicleFloors.clear();
        }
    }

    /**
     * 本家 getSpeed 相当 (アニメ用)。
     */
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
     * 本家 onModelChanged: モデルが差し替わった時の後始末。
     * <p>
     * サーバー側は座席 (slotPos) がモデル依存なので床を作り直させる
     * ({@code floorLoaded=false} で次 tick に setupFloors が走る)。
     * クライアント側は鳴っている走行音を止める (モデルが変われば音源も変わる)。
     */
    public void onModelChanged() {
        if (!this.level().isClientSide()) {
            //古い座席を消してから作り直す (新モデルの slotPos で並べ直すため)
            this.vehicleFloors.stream().filter(java.util.Objects::nonNull).forEach(Entity::discard);
            this.vehicleFloors.clear();
            this.floorLoaded = false;
        } else {
            com.portofino.realtrainmodunofficial.client.sound.LegacyScriptSoundManager.stopAll(this);
        }
    }

    /** 本家 setFloor: EntityFloor から自分を登録してもらう。 */
    public void setFloor(jp.ngt.rtm.entity.train.parts.EntityFloor floor) {
        if (floor != null && !this.vehicleFloors.contains(floor)) {
            this.vehicleFloors.add(floor);
        }
    }

    /**
     * 床/座席パーツを今の車体位置へ揃える。
     * <p>
     * パーツ自身の tick でも追従しているが、tick 順によっては車体より先に回って
     * 1 tick 前の位置を使う。ここで車体の移動後に押し出しておけば、順序に関係なく
     * 当たり判定が車体と一致する。
     */
    protected void updateFloorPositions() {
        if (this.vehicleFloors.isEmpty()) {
            return;
        }
        //消えた床は取り除く (クライアントでは床が個別に消えるので溜まる)
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
        //車体 AABB のすぐ外側 4 方向のうち、立てる所へ寄せる
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
    //
    // RTM 標準のレンダースクリプト (Render223.js 等) が entity に対して直接呼ぶメソッド群。
    // 1 つでも欠けると Nashorn が TypeError を投げ、その車両の描画が丸ごと止まる。

    /**
     * 本家 EntityVehicleBase.getSeatRotation: 転換クロスシートの回転量を -1.0〜1.0 に正規化して返す。
     * <p>
     * スクリプト側 (Render223.js) は {@code entity.getSeatRotation() * 15.0} のように使う。
     * 生の値が要るときは {@link #getSeatRotationRaw()} (パックスクリプトはこちらに書き換えられる)。
     */
    public float getSeatRotation() {
        return (float) this.seatRotation / (float) MAX_SEAT_ROTATION;
    }

    /**
     * {@link #seatRotation} の生の値 (-45〜45)。
     * パックの {@code entity.seatRotation} は PackScriptSource でこちらへ振り替えられる。
     */
    public int getSeatRotationRaw() {
        return this.seatRotation;
    }

    /**
     * 本家 EntityTrainBase.getRollsignAnimation: 方向幕のスクロール位置 (連続値・行先index基準)。
     * {@code rollsignAnimation} は毎 tick 目標 {@code rollsignV} (=行先*16) へ ±1 で寄るので、
     * 16 で割ると「今どの行先フレームを表示中か」を小数で表す (幕回しアニメ)。列車以外は 0。
     */
    public float getRollsignAnimation() {
        return (float) this.rollsignAnimation / 16.0F;
    }

    /**
     * 本家 EntityTrainBase.setRollsignAnimation: 方向幕スクロールの目標フレームを設定する。
     * 実際の {@code rollsignAnimation} はこの目標へ毎 tick 1 ずつ寄り、滑らかに幕が回る。
     */
    public void setRollsignAnimation(int destination) {
        this.rollsignV = destination * 16;
    }

    /**
     * ResourceState.getResourceName 用 (モデル名)。
     */
    protected String getResourceName() {
        return "";
    }

    //---- パックスクリプト互換 (1.7.10/1.12 SRG メソッド) ----

    /**
     * getBrightnessForRender (packed lightmap; 1.21 と同レイアウト sky<<20|block<<4)
     */
    public int func_70070_b() {
        BlockPos pos = BlockPos.containing(this.getX(), this.getY() + 0.5D, this.getZ());
        return net.minecraft.client.renderer.LevelRenderer.getLightColor(this.level(), pos);
    }

    public int func_70070_b(float partialTick) {
        return this.func_70070_b();
    }

    /**
     * getEntityId
     */
    public int func_145782_y() {
        return this.getId();
    }

    /**
     * isBeingRidden
     */
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
            //スポーン直後の初期姿勢のみパケット値を採用 (以降は float 同期)
            this.vehicleYaw = yaw;
            this.vehiclePitch = pitch;
        }
    }

    /**
     * 本家 updatePosAndRotationClient の忠実移植 (クライアント補間)。
     * <p>
     * 位置・回転 (ヨー/ピッチ/ロール) を<b>どちらも同じ 1/inc で漸近補間</b>する。
     * inc (= {@link #vehiclePosRotationInc}) は移動パケットの steps (バニラ既定 3)。
     * 走行中は毎 tick リセットされるので実質 EMA (α=1/3, 約 2tick 遅れ) として働き、
     * 停車 (パケットが止まる) と inc が 3→2→1 と減って 1/inc→1/1 でターゲットへ
     * 残差なく収束するため、止まった車体が僅かに傾いたまま残ることもない。
     * <p>
     * <b>なぜ回転も補間するか (このバグの修正点)。</b>
     * 以前は回転だけ「同期 float 値へ毎 tick 直接代入」していた。位置は約 2tick 遅れの
     * 補間・回転は遅れ 0、という<b>非対称</b>が次を招いていた:
     * <ul>
     *   <li>車体: 補間で遅れた位置に最新の向きが載るため、カーブで車体が線路に対して
     *       ねじれて見えた (位置と向きの基準時刻がずれる)。</li>
     *   <li>統合サーバでもサーバ/クライアントは別スレッドで、移動パケットは 1tick あたり
     *       0/1/2 個とばらつく。直接代入だとその揺らぎが車体ヨーにそのまま乗り、描画補間は
     *       1tick ぶんしか均さないので周期的にガクついた。</li>
     *   <li>{@link #rotateRiders()} は車体の毎 tick ヨー/ピッチ差分を乗客カメラへ渡すため、
     *       その揺らぎが視点の揺れ (カーブでの視界揺れ) になっていた。</li>
     * </ul>
     * 位置と同じ補間へ戻すと、車体の位置と向きの遅れが揃って線路上で一貫し、パケットの
     * 揺らぎは位置と同様に均される。rotateRiders も補間後の滑らかな差分を読むのでカメラも
     * 滑らかになる ({@code EntityBogie.updatePosAndRotationClient} と同一方式)。
     * <p>
     * 「回転がワンテンポ遅れる」件は<b>回転だけが位置と無関係に遅れた</b>場合の話。ここでは
     * 位置・回転・台車がいずれも同じ ~2tick 遅れになるため、車体基準では遅れは知覚されない。
     */
    protected void updatePosAndRotationClient() {
        if (!this.clientRotInit) {
            this.clientRotInit = true;
            this.setRot(this.vehicleYaw, this.vehiclePitch);
            this.yRotO = this.vehicleYaw;
            this.xRotO = this.vehiclePitch;
            this.rotationRoll = this.vehicleRoll;
            this.prevRotationRoll = this.vehicleRoll;
        }
        if (this.vehiclePosRotationInc > 0) {
            float d0 = 1.0F / (float) this.vehiclePosRotationInc;
            double x = this.getX() + (this.vehicleX - this.getX()) * d0;
            double y = this.getY() + (this.vehicleY - this.getY()) * d0;
            double z = this.getZ() + (this.vehicleZ - this.getZ()) * d0;
            //回転も位置と同じ 1/inc で補間。ヨーはラップ跨ぎ (179→-179 等) で逆回りしないよう
            //wrapDegrees で現在値の近傍へ展開してから寄せる。
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
