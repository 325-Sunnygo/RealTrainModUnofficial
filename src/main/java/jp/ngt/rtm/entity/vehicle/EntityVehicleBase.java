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
            new jp.ngt.rtm.modelpack.state.ResourceState(this::getResourceName);

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

        //視点追従 (本家 KaizPatchX EntityTrainBase.updateRiderPosition)
        this.rotateRiders();

        //パックスクリプト互換 SRG フィールドの更新
        if (this.field_70170_p == null || this.field_70170_p.level != this.level()) {
            this.field_70170_p = new jp.ngt.mccompat.WorldCompat(this.level());
        }
        this.field_70173_aa = this.tickCount;
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

    public jp.ngt.rtm.modelpack.state.ResourceState getResourceState() {
        return this.resourceState;
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
     * 本家 EntityVehicleBase.getRollsignAnimation: 方向幕のスクロール量 (0.0〜1.0)。
     * RTMU は方向幕をアニメーションさせないので常に 0 (= 表示が切り替わるだけ)。
     */
    public float getRollsignAnimation() {
        return 0.0F;
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
