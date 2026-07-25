package jp.ngt.rtm.entity.train;

import jp.ngt.ngtlib.math.NGTMath;
import jp.ngt.rtm.entity.train.util.BogieController;
import jp.ngt.rtm.modelpack.cfg.TrainConfig;
import jp.ngt.rtm.rail.TileEntityLargeRailBase;
import jp.ngt.rtm.rail.TileEntityLargeRailCore;
import jp.ngt.rtm.rail.TileEntityLargeRailSwitchCore;
import jp.ngt.rtm.rail.util.RailMap;
import jp.ngt.ngtlib.math.Vec3;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.UUID;

/**
 * 本家 jp.ngt.rtm.entity.train.EntityBogie (KaizPatchX) の忠実移植。
 * レール追従 (updateBogiePos)・連結衝突・fixBogieYaw/Pitch は本家アルゴリズムのまま。
 * 1.21 適合: DataWatcher→SynchedEntityData, yOffset→TRAIN_HEIGHT 直接加算。
 * TODO: ジョイント音 (jointDelay 機構は保持、再生は後続)、BumpingPost、VehicleTracker。
 */
public class EntityBogie extends Entity {
    /**
     * ベジェ曲線の分割精度(1m当たり)
     */
    private static final int SPLITS_PER_METER = 360;

    private static final EntityDataAccessor<Integer> DATA_TRAIN_ID =
            SynchedEntityData.defineId(EntityBogie.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> DATA_IS_FRONT =
            SynchedEntityData.defineId(EntityBogie.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> DATA_BOGIE_ID =
            SynchedEntityData.defineId(EntityBogie.class, EntityDataSerializers.BYTE);

    private EntityTrainBase parentTrain;
    /**
     * 連結可能かどうか
     */
    public boolean isActivated = false;
    /**
     * 走る向き（≠ rotationYaw）
     */
    public float movingYaw;
    private UUID unloadedTrain;

    private TileEntityLargeRailCore currentRailObj;
    private RailMap currentRailMap;
    /**
     * {x, y, z}
     */
    private final double[] posBuf = new double[3];
    /**
     * {yaw, pitch, yaw2, cant}
     */
    private final float[] rotationBuf = new float[4];
    private int split = -1;
    private int prevPosIndex;
    private float jointDelay;
    /** 何本目の継ぎ目か (jointDelay 配列のインデックス)。レール変更で 0 に戻る。 */
    private int jointIndex;
    /** 今いるレールが反響音指定か (トンネル/高架下)。 */
    private boolean reverbSound;

    public float rotationRoll;
    public float prevRotationRoll;

    //パックスクリプト互換 (1.7.10 SRG フィールド、tick 毎更新)
    public float field_70177_z;
    public float field_70125_A;
    public int field_70173_aa;

    //クライアント補間
    private int carPosRotationInc;
    private float carYaw, carPitch, carRoll;

    public EntityBogie(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public EntityBogie(EntityType<?> type, Level level, byte id) {
        this(type, level);
        this.setBogieId(id);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_TRAIN_ID, 0);
        builder.define(DATA_IS_FRONT, (byte) 0);
        builder.define(DATA_BOGIE_ID, (byte) 0);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag nbt) {
        nbt.putBoolean("isFront", this.isFront());
        nbt.putByte("bogieId", this.getBogieId());
        if (this.getTrain() != null) {
            UUID uuid = this.getTrain().getUUID();
            nbt.putLong("trainUUID_Most", uuid.getMostSignificantBits());
            nbt.putLong("trainUUID_Least", uuid.getLeastSignificantBits());
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag nbt) {
        this.setFront(nbt.getBoolean("isFront"));
        this.setBogieId(nbt.getByte("bogieId"));
        if (nbt.contains("trainUUID_Most") && nbt.contains("trainUUID_Least")) {
            UUID uuid = new UUID(nbt.getLong("trainUUID_Most"), nbt.getLong("trainUUID_Least"));
            if (!this.loadTrainFromUUID(uuid)) {
                this.unloadedTrain = uuid;
            }
        }
    }

    private boolean loadTrainFromUUID(UUID uuid) {
        if (this.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            Entity entity = serverLevel.getEntity(uuid);
            if (entity instanceof EntityTrainBase train) {
                this.setTrain(train);
                train.setBogie(this.getBogieId(), this);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return !this.isRemoved();
    }

    @Override
    public boolean isPickable() {
        return !this.isRemoved();
    }

    /**
     * 当たり判定をレール面まで下げる。
     *
     * <p>本家の台車は {@code getYOffset() == 0} で<b>レール高さそのもの</b>に立ち、
     * 当たり判定もそこから上へ伸びる。RTMU は台車の座標を車体と同じ基準に揃えるため
     * {@code railHeight + TRAIN_HEIGHT} に置いており (updateBogiePos)、そのぶん
     * <b>当たり判定だけが約 1.19 ブロック浮いて</b>いた (台車を殴る/掴む位置が実際の
     * 台車より上にずれる)。判定を TRAIN_HEIGHT ぶん下げて本家と同じ「レールの上」に戻す。
     */
    @Override
    protected AABB makeBoundingBox() {
        return super.makeBoundingBox().move(0.0D, -EntityTrainBase.TRAIN_HEIGHT, 0.0D);
    }

    /**
     * 分岐で台車が追従すべきレールマップを選ぶ。
     *
     * <p><b>本家と同じライブ参照方式</b>。本家 EntityBogie は
     * {@code getSwitch().getNearestPoint(台車).getActiveRailMap(world)} で、台車に最も近い
     * 分岐点の<b>開通(アクティブ)側</b>を掴む。{@code Point.getActiveRailMap} は根元で
     * {@code RailPosition.checkRSInput} を<b>毎回ライブで参照</b>するため、レバー/レッドストーンの
     * 現在状態を必ず反映する。根元から進入すれば開通方向、枝から背向で合流すれば其の枝、を
     * 「最も近い点」の位置で自然に選び分ける (根元点なら RS で分岐/直進、枝端点なら其の枝)。
     *
     * <p>かつてここを {@code SwitchType.getActiveRailMap()} が返す<b>キャッシュ済み activeRails</b> を
     * 距離で選ぶ方式に作り替えていたが、そのキャッシュはレール<b>コア</b>への {@code neighborChanged}
     * 由来の {@code onBlockChanged} でしか更新されない。レバーがコアに隣接していない等で更新が
     * 届かないと、見た目の分岐 (クライアントはライブ RS でアニメする) は切り替わっているのに台車だけ
     * 直進側に固定される、という不具合になっていた。本家どおりライブ参照へ戻す。
     *
     * <p>本家に無い保険は 2 つだけ残す:
     * ①同じ列車のもう一方の台車が既に或る枝に乗っていれば<b>同じ枝</b>を使う (車体が別々の枝へ
     *   引き裂かれて台車分離するのを防ぐ) 。②開通側が進入元レールと連続しない (=非開通の枝から
     *   背向合流) 場合は、実際に連続する枝へ乗せる (開通側へ TP させない) 。
     */
    private RailMap selectSwitchRailMap(jp.ngt.rtm.rail.util.SwitchType sw, double px, double pz) {
        //★最優先: 同じ列車のもう一方の台車が既にこの分岐の枝に乗っているなら、必ず<b>同じ枝</b>を使う。
        //  分岐の根元付近では2本の枝が近接しているため、台車ごとに独立して選ぶと前後で判定が割れ、
        //  片方が直進側・片方が分岐側になって車体が引き裂かれる (台車が分離して残る) 。
        RailMap sibling = this.siblingBogieSwitchRailMap(sw);
        if (sibling != null) {
            return sibling;
        }
        //★本家と同じ: 台車に最も近い分岐点の「開通(アクティブ)側」をライブ参照で掴む。
        //  Point.getActiveRailMap は根元で checkRSInput (getBestNeighborSignal) を毎回読むため、
        //  レバー/RS の現在状態を必ず反映する。activeRails キャッシュには一切依存しない。
        jp.ngt.rtm.rail.util.Point np = sw.getNearestPoint(this);
        RailMap active = np != null ? np.getActiveRailMap(this.level()) : null;

        //進入元レールと連続していれば其れに従う (通常の進入。根元では両枝が根元端点を共有するので
        //開通側が直進でも分岐でも canConnect が成立し、RS の選択どおりに進む)。
        if (active != null && (this.currentRailMap == null || this.currentRailMap.canConnect(active))) {
            return active;
        }
        //★開通側が進入元と繋がらない = 非開通の枝から背向で合流している。実際に連続する枝へ乗せ、
        //  開通側へ台車だけを TP させない。
        jp.ngt.rtm.rail.util.RailMapSwitch[] allArr = sw.getAllRailMap();
        if (allArr != null && this.currentRailMap != null) {
            for (jp.ngt.rtm.rail.util.RailMapSwitch rm : allArr) {
                if (rm != null && this.currentRailMap.canConnect(rm)) {
                    return rm;
                }
            }
        }
        return active;
    }

    /**
     * 同じ列車のもう一方の台車が、この分岐のいずれかの枝に乗っていればその枝を返す (無ければ null)。
     * 前後の台車を必ず同じ枝に乗せ、車体が別々の枝へ引き裂かれる (台車が分離する) のを防ぐ。
     */
    private RailMap siblingBogieSwitchRailMap(jp.ngt.rtm.rail.util.SwitchType sw) {
        EntityTrainBase train = this.getTrain();
        if (train == null) {
            return null;
        }
        jp.ngt.rtm.rail.util.RailMapSwitch[] all = sw.getAllRailMap();
        if (all == null) {
            return null;
        }
        for (int i = 0; i < 2; i++) {
            EntityBogie other = train.getBogie(i);
            if (other == null || other == this) {
                continue;
            }
            RailMap orm = other.currentRailMap;
            if (orm == null) {
                continue;
            }
            for (jp.ngt.rtm.rail.util.RailMapSwitch rm : all) {
                if (rm == orm) {
                    return rm;
                }
            }
        }
        return null;
    }

    /**
     * レール曲線と点 (px,pz) の最短距離の2乗 (サンプリング近似)。描画で「どちらの枝に描くか」を
     * 選ぶのに使う。<b>サンプル密度はレール長に比例させること</b> (固定分割だと長い分岐で曲線上の
     * 台車を「離れている」と誤測定する)。
     */
    private static double distToRailMapSq(RailMap rm, double px, double pz,
                                          double samplesPerMeter, double minSplit) {
        int split = (int) Math.max(minSplit, rm.getLength() * samplesPerMeter);
        int idx = rm.getNearlestPoint(split, px, pz);
        double[] p = rm.getRailPos(split, idx);//{z, x}
        double dz = p[0] - pz;
        double dx = p[1] - px;
        return dx * dx + dz * dz;
    }

    /**
     * 描画専用: 分岐器の全枝のうち (px,pz) に最も近いものを返す (<b>開通方向は見ない</b>)。
     *
     * <p>この選択は「どちらの枝に描くか」を決めるだけなので、サーバ側の進路選択のような細かい
     * 分解能は要らない。<b>毎フレーム・台車ごとに呼ばれる</b>ため、サンプル密度は枝を区別できる
     * 最低限 (4 サンプル/m) に抑える。分岐の根元では枝同士が数 cm しか離れておらず、そこで取り違えても
     * 描画位置の差は数 cm で見えない。枝が離れるほど判定は明確になるので、実用上これで十分。
     */
    private static RailMap nearestSwitchRailMapForRender(jp.ngt.rtm.rail.util.SwitchType sw,
                                                         double px, double pz) {
        jp.ngt.rtm.rail.util.RailMapSwitch[] all = sw.getAllRailMap();
        if (all == null) {
            return null;
        }
        RailMap best = null;
        double bestSq = Double.MAX_VALUE;
        for (jp.ngt.rtm.rail.util.RailMapSwitch rm : all) {
            if (rm == null) {
                continue;
            }
            double d = distToRailMapSq(rm, px, pz, 4.0D, 32.0D);
            if (d < bestSq) {
                bestSq = d;
                best = rm;
            }
        }
        return best;
    }

    /**
     * レールに乗っているか (currentRailMap 保持)。停車中のピッチ維持判定に使用。
     */
    public boolean hasRailMap() {
        return this.currentRailMap != null;
    }

    /** 現在乗っているレールコア (WebCTC の RailGroup 在線判定などが使う)。 */
    public TileEntityLargeRailCore getCurrentRailObj() {
        return this.currentRailObj;
    }

    /**
     * @return 位置更新が成功したらtrue
     */
    public boolean updateBogiePos(float speed, float trainLength, EntityBogie frontBogie) {
        if (this.updateCollision()) {
            return false;
        }

        this.movingYaw = Mth.wrapDegrees(this.getYRot() + (this.isFront() ? 0.0F : 180.0F));

        double px = this.getX() + (double) NGTMath.sin(this.movingYaw) * (double) speed;
        double py = this.getY();
        double pz = this.getZ() + (double) NGTMath.cos(this.movingYaw) * (double) speed;

        if (!this.resetRailObj(px, py, pz)) {
            if (this.getTrain() != null) {
                this.getTrain().stopTrain(true);
            }
            return false;
        }

        RailMap rm = this.currentRailMap;
        int pIndex = 0;
        if (frontBogie == null || this.prevPosIndex == -1) {
            pIndex = rm.getNearlestPoint(this.split, px, pz);
        } else {
            //移動範囲を制限して、「台車からの距離は同じでも位置は真逆」な点の検出を防ぐ
            int indexInc = (int) ((Math.abs(speed) + 0.25F) * (float) SPLITS_PER_METER);
            int indexMin = Math.max(this.prevPosIndex - indexInc, 0);
            int indexMax = Math.min(this.prevPosIndex + indexInc, this.split);
            double[] fp = frontBogie.getPosBuf();
            double dif = Double.MAX_VALUE;
            double tlSq = trainLength * trainLength;
            for (int i = indexMin; i < indexMax; ++i) {
                double[] pxz = rm.getRailPos(this.split, i);
                double lenTemp = distanceSq(pxz[1], py, pxz[0], fp[0], fp[1], fp[2]);
                double difTemp = Math.abs(lenTemp - tlSq);
                if (difTemp < dif) {
                    dif = difTemp;
                    pIndex = i;//理想の台車間距離と実際の距離の差が最も小さくなる点を求める
                }
            }
        }
        this.prevPosIndex = pIndex;

        double[] posZX = rm.getRailPos(this.split, pIndex);
        py = rm.getRailHeight(this.split, pIndex) + EntityTrainBase.TRAIN_HEIGHT;
        float railYaw = Mth.wrapDegrees(rm.getRailRotation(this.split, pIndex));
        float movYaw = fixBogieYaw(this.movingYaw, railYaw);
        float yaw = fixBogieYaw(this.getYRot(), movYaw);
        float pitch = fixBogiePitch(rm.getRailPitch(this.split, pIndex), railYaw, yaw);
        float cant = rm.getCant(this.split, pIndex);

        if (Math.abs(Mth.wrapDegrees(railYaw - yaw)) > 90.0F) {
            cant *= -1.0F;
        }

        if ((this.getX() == posZX[1]) && (this.getZ() == posZX[0])) {
            return true;//低速時に斜めレールで走行しない問題回避
        }

        this.posBuf[0] = posZX[1];
        this.posBuf[1] = py;
        this.posBuf[2] = posZX[0];
        this.rotationBuf[0] = yaw;
        this.rotationBuf[1] = pitch;
        this.rotationBuf[2] = movYaw;
        this.rotationBuf[3] = cant;

        if (this.jointDelay > 0.0F) {
            this.jointDelay -= Math.abs(speed);
            if (this.jointDelay <= 0.0F) {
                this.playJointSound();
            }
        }

        return true;
    }

    private static double distanceSq(double x0, double y0, double z0, double x1, double y1, double z1) {
        double dx = x1 - x0;
        double dy = y1 - y0;
        double dz = z1 - z0;
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * @return 移動先にレールがあればtrue
     */
    private boolean resetRailObj(double px, double py, double pz) {
        TileEntityLargeRailCore coreObj = this.getRail(px, py, pz);

        if (coreObj == null) {
            return false;
        } else {
            if (this.currentRailObj != coreObj) {
                //新しいレール上に移動
                RailMap railMap;
                if (coreObj instanceof TileEntityLargeRailSwitchCore switchObj) {
                    jp.ngt.rtm.rail.util.SwitchType sw = switchObj.getSwitch();
                    railMap = sw != null ? this.selectSwitchRailMap(sw, px, pz) : null;
                } else {
                    railMap = coreObj.getRailMap(this);
                }
                if (railMap == null) {
                    return this.currentRailMap != null;
                }

                if (this.currentRailMap != null && !this.currentRailMap.canConnect(railMap)) {
                    return true;
                }

                this.currentRailObj = coreObj;
                this.currentRailMap = railMap;
                this.split = (int) (this.currentRailMap.getLength() * (double) SPLITS_PER_METER);
                this.prevPosIndex = -1;
                this.onChangeRail(coreObj);
            }

            return true;
        }
    }

    /**
     * 別レールに移動した際呼び出し
     */
    protected void onChangeRail(TileEntityLargeRailCore newRail) {
        //新しいレールの反響音指定を引き継ぎ、継ぎ目カウントを最初から数え直す
        this.reverbSound = newRail != null && newRail.isReberbSound();
        this.jointIndex = 0;
        this.playJointSound();
        //レール継ぎ目のガタン — 車体サスペンションへ入力 (RTMU オリジナル)
        EntityTrainBase train = this.getTrain();
        if (train != null && train.bogieController != null) {
            train.bogieController.onRailJoint(this, train.getSpeed());
        }
    }

    /** 既定のジョイント音 (パックが sound_Joint を指定しない場合)。 */
    private static final String JOINT_SOUND = "rtm:train/joint";
    private static final String JOINT_SOUND_REVERB = "rtm:train/joint_reverb";

    /**
     * 本家 playJointSound: レール継ぎ目の「ガタン」を鳴らし、次の継ぎ目までの距離を決める。
     * <p>
     * jointDelay は台車ごとの配列で、車体の何本目の継ぎ目かによって間隔が変わる
     * (先頭台車と後方台車で「ガタン…ゴトン」がずれるのはこれ)。
     */
    protected void playJointSound() {
        EntityTrainBase train = this.getTrain();
        if (train == null) {
            return;
        }
        TrainConfig cfg = train.getConfig();
        if (cfg == null || cfg.muteJointSound || cfg.jointDelay == null) {
            return;
        }
        int bogieId = this.getBogieId();
        if (bogieId < 0 || bogieId >= cfg.jointDelay.length || cfg.jointDelay[bogieId] == null) {
            return;
        }

        if (this.level().isClientSide()) {
            float maxSpeed = cfg.maxSpeed != null && cfg.maxSpeed.length > 0
                    ? cfg.maxSpeed[cfg.maxSpeed.length - 1] : 1.8F;
            //速いほど高いピッチ (1.0〜1.5)
            float pitch = maxSpeed > 0.0F
                    ? (Math.abs(train.getSpeed()) / maxSpeed) * 0.5F + 1.0F : 1.0F;
            String sound;
            if (cfg.sound_Joint != null && cfg.sound_JointReverb != null) {
                sound = this.reverbSound ? cfg.sound_JointReverb : cfg.sound_Joint;
            } else {
                sound = this.reverbSound ? JOINT_SOUND_REVERB : JOINT_SOUND;
            }
            //継ぎ目音は離散イベント: 登録制ラッチを通さず毎回鳴らす
            com.portofino.realtrainmodunofficial.client.sound.LegacyScriptSoundManager
                    .playLegacyId(this, sound, 1.0F, pitch, false, true);
        }

        int size = cfg.jointDelay[bogieId].length;
        if (this.jointIndex < size - 1) {
            int index0 = this.reverseJointArray() ? size - this.jointIndex - 1 : this.jointIndex;
            ++this.jointIndex;
            int index1 = this.reverseJointArray() ? size - this.jointIndex - 1 : this.jointIndex;
            this.jointDelay = Math.abs(cfg.jointDelay[bogieId][index1] - cfg.jointDelay[bogieId][index0]);
        }
    }

    /**
     * 本家 reverseJointArray: 進行方向と台車位置の組み合わせで、
     * jointDelay 配列を逆から読むか。後進時に継ぎ目の間隔が反転するのを合わせる。
     */
    protected boolean reverseJointArray() {
        EntityTrainBase train = this.getTrain();
        if (train == null) {
            return false;
        }
        return (train.getTrainDirection() == 0) ^ (this.getBogieId() == 0);
    }

    /** 本家 setBogieSize: 台車の当たり判定サイズ。 */
    public void setBogieSize(float width, float height) {
        this.setBoundingBox(this.getBoundingBox().inflate(0.0D));
        this.refreshDimensions();
    }

    /** 本家 Lockable: 施錠対象は親車両。 */
    public Object getTarget(Object world, int x, int y, int z) {
        return this.getTrain();
    }

    public boolean lock(Object player, String code) {
        return true;
    }

    public boolean unlock(Object player, String code) {
        return true;
    }

    /** 本家 getProhibitedAction: 1 = 破壊禁止。 */
    public int getProhibitedAction() {
        return 1;
    }

    private TileEntityLargeRailCore getRail(double px, double py, double pz) {
        TileEntityLargeRailBase railObj = TileEntityLargeRailBase.getRailFromCoordinates(this.level(), px, py, pz);
        if (railObj == null) {
            return null;
        }
        return railObj.getRailCore();
    }

    public double[] getPosBuf() {
        return this.posBuf;
    }

    public void moveBogie(EntityTrainBase train, double x, double y, double z, BogieController.UpdateFlag flag) {
        this.setPos(x, y, z);
        switch (flag) {
            case ALL -> {
                this.setYRot(this.rotationBuf[0]);
                this.setXRot(this.rotationBuf[1]);
                this.movingYaw = this.rotationBuf[2];
                this.rotationRoll = this.rotationBuf[3];
            }
            case YAW -> {
                float movYaw = fixBogieYaw(this.movingYaw, train.getYRot());
                float yaw = fixBogieYaw(this.getYRot(), movYaw);
                this.setYRot(yaw % 360.0F);
                this.movingYaw = movYaw;
            }
            case NONE -> {
            }
        }
    }

    /**
     * BogieController.updateBogies から毎 tick (本家 updateBogie)。
     */
    public void updateBogie() {
        this.prevRotationRoll = this.rotationRoll;
        this.xo = this.getX();
        this.yo = this.getY();
        this.zo = this.getZ();

        if (this.level().isClientSide) {
            this.updatePosAndRotationClient();
        }
    }

    protected void updatePosAndRotationClient() {
        if (this.carPosRotationInc > 0) {
            float d0 = 1.0F / (float) this.carPosRotationInc;
            this.setYRot(this.getYRot() + Mth.wrapDegrees(this.carYaw - this.getYRot()) * d0);
            this.setXRot(this.getXRot() + (this.carPitch - this.getXRot()) * d0);
            this.rotationRoll += (this.carRoll - this.rotationRoll) * d0;
            --this.carPosRotationInc;
        }
        EntityTrainBase train = this.getTrain();
        if (train != null) {
            TrainConfig cfg = train.getConfig();
            if (cfg != null) {
                //Clientでの台車エンティティ位置は車両位置から (弦 = 車体位置 + bogiePos を直線配置)。
                //実レール(弧)へのスナップは描画側 (RtmBogieRenderer) が毎フレーム行う。ここ(毎tick)で
                //やると高速時に台車が車体の毎フレーム補間へ追従しきれず、次第に遅れて見えるため。
                float[][] pos = cfg.getBogiePos();
                int bogieIndex = this.getBogieId();
                Vec3 v31 = new Vec3(pos[bogieIndex][0], pos[bogieIndex][1], pos[bogieIndex][2]);
                v31 = v31.rotateAroundX(train.getXRot());
                v31 = v31.rotateAroundY(train.getYRot());
                this.setPos(train.getX() + v31.getX(), train.getY() + v31.getY(), train.getZ() + v31.getZ());
            }
        }
    }

    /**
     * クライアント描画専用: 弦上の推定位置 (cx,cz) に最も近いレール曲線 (弧) 上の点へ吸着する。
     * サーバ {@link #updateBogiePos} と同じレール取得・サンプリングを行うが、
     * <b>台車の物理状態フィールド (currentRailObj/currentRailMap/split/prevPosIndex) は
     * 一切変更しない</b>。そのため hasRailMap()・連結判定・onChangeRail(ジョイント音) など
     * クライアント挙動へ副作用を与えず、純粋に描画位置のみを補正する。
     *
     * @return レール弧上の {x, y, z}。レール未検出時は null (呼び出し側で弦へフォールバック)。
     */
    public double[] snapToRailArc(double cx, double cy, double cz) {
        TileEntityLargeRailCore coreObj = this.getRail(cx, cy, cz);
        if (coreObj == null) {
            return null;
        }
        //★分岐器では「開通している側」ではなく<b>車体が実際に居る側の枝</b>へ吸着すること。
        //
        //以前はここで getActiveRailMap() を呼び、分岐器の開通方向へ無条件にスナップしていた。
        //しかし開通方向と列車の実際の進路は一致するとは限らない (対向側から割り出して通過する、
        //通過中に転換される、そもそもサーバの進路選択と食い違う)。その結果<b>台車だけが反対側の
        //線路に描かれ、分岐を抜けてレールが1本に戻ると車体の位置へ戻る</b> = 「分岐で台車が消えて
        //反対側の線路から出てきて、合流したら合体して走る」という報告そのものの症状になっていた。
        //
        //厄介なのは<b>物理(サーバ)は最初から正しく走っている</b>点で、ログには一切異常が出ない。
        //台車間距離もレール距離も正常値のままなので、物理側をいくら調べても原因に辿り着けない。
        //
        //cx/cz は同期済みの車体位置から求めた弦上の点 = 列車が本当に居る場所なので、各枝との
        //距離を測って最も近い枝へ吸着すれば、サーバがどちらを選んでいても描画は自動的に一致する。
        RailMap railMap;
        if (coreObj instanceof TileEntityLargeRailSwitchCore switchObj) {
            railMap = switchObj.getSwitch() != null
                    ? nearestSwitchRailMapForRender(switchObj.getSwitch(), cx, cz)
                    : null;
        } else {
            railMap = coreObj.getRailMap(this);
        }
        if (railMap == null) {
            return null;
        }
        int railSplit = (int) (railMap.getLength() * (double) SPLITS_PER_METER);
        int pIndex = railMap.getNearlestPoint(railSplit, cx, cz);
        double[] posZX = railMap.getRailPos(railSplit, pIndex);//{z, x}
        double py = railMap.getRailHeight(railSplit, pIndex) + EntityTrainBase.TRAIN_HEIGHT;
        return new double[]{posZX[1], py, posZX[0]};
    }

    @Override
    public void tick() {
        this.checkUnloadTrain();

        if (!this.level().isClientSide) {
            if (this.currentRailObj != null) {
                this.currentRailObj.colliding = true;
            } else {
                this.resetRailObj(this.getX(), this.getY(), this.getZ());
            }
        }

        //パックスクリプト互換 SRG フィールドの更新
        this.field_70177_z = this.getYRot();
        this.field_70125_A = this.getXRot();
        this.field_70173_aa = this.tickCount;
    }

    /**
     * getBrightnessForRender 互換 (スクリプト用)
     */
    public int func_70070_b() {
        net.minecraft.core.BlockPos pos = net.minecraft.core.BlockPos.containing(this.getX(), this.getY() + 0.5D, this.getZ());
        return net.minecraft.client.renderer.LevelRenderer.getLightColor(this.level(), pos);
    }

    public int func_70070_b(float partialTick) {
        return this.func_70070_b();
    }

    private void checkUnloadTrain() {
        if (this.unloadedTrain != null && !this.level().isClientSide) {
            if (this.loadTrainFromUUID(this.unloadedTrain)) {
                this.unloadedTrain = null;
            }
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.getTrain() == null || this.getTrain().isRemoved()) {
            if (!this.level().isClientSide) {
                this.discard();
            }
            return true;
        }
        return this.getTrain().hurt(source, amount);
    }

    /**
     * @return 衝突して止まるならtrue
     */
    private boolean updateCollision() {
        double dis = this.getBbWidth() * 2.0F;
        List<Entity> list = this.level().getEntities(this,
                new AABB(this.getX() - dis, this.getY() - EntityTrainBase.TRAIN_HEIGHT, this.getZ() - dis,
                        this.getX() + dis, this.getY() + EntityTrainBase.TRAIN_HEIGHT, this.getZ() + dis));
        this.collideWithBumpingPost();
        return this.collideWithEntities(list);
    }

    /**
     * 本家 EntityBogie.collideWithBumpingPost の移植。
     * 進行方向の先頭台車が車止めの 1.75 ブロック以内に入ったら列車を非常停止させる。
     * <p>
     * 本家の車止めはエンティティ (EntityBumpingPost) だったが、RTMU では他の設置物と同じ
     * ブロックなので、エンティティ一覧ではなく周囲のブロックエンティティを見る。
     */
    private void collideWithBumpingPost() {
        EntityTrainBase train = this.getTrain();
        //本家: 先頭台車のときだけ判定する。動いていない列車は調べる必要が無い。
        if (train == null || train.getTrainDirection() != this.getBogieId() || train.getSpeed() == 0.0F) {
            return;
        }
        final double range = 1.75D;
        final double rangeSq = range * range;
        final int r = Mth.ceil(range);
        net.minecraft.core.BlockPos center = this.blockPosition();
        for (int dx = -r; dx <= r; ++dx) {
            for (int dy = -1; dy <= 2; ++dy) {
                for (int dz = -r; dz <= r; ++dz) {
                    net.minecraft.core.BlockPos pos = center.offset(dx, dy, dz);
                    if (!(this.level().getBlockEntity(pos)
                            instanceof com.portofino.realtrainmodunofficial.blockentity.InstalledObjectBlockEntity be)
                            || be.getCategory()
                                != com.portofino.realtrainmodunofficial.installedobject.InstalledObjectCategory.BUMPING_POST) {
                        continue;
                    }
                    //車止めはレールに吸着して置かれる。実際の位置はブロックの
                    //(x+0.5, y, z+0.5) にレール曲線への寄せ (renderOffset) を足したもの。
                    net.minecraft.world.phys.Vec3 offset = be.getRenderOffset();
                    double px = pos.getX() + 0.5D + offset.x;
                    double py = pos.getY() + offset.y;
                    double pz = pos.getZ() + 0.5D + offset.z;
                    if (this.distanceToSqr(px, py, pz) <= rangeSq) {
                        train.stopTrain(true);
                        return;
                    }
                }
            }
        }
    }

    private boolean collideWithEntities(List<Entity> list) {
        EntityBogie nearestBogie = null;
        double distanceSq = Double.MAX_VALUE;

        for (Entity entity : list) {
            if (entity instanceof EntityBogie bogie) {
                if (this.collideWithBogie(bogie)) {
                    double d0 = this.distanceToSqr(entity);
                    if (d0 < distanceSq) {
                        nearestBogie = bogie;
                        distanceSq = d0;
                    }
                }
            } else if (entity instanceof LivingEntity living) {
                this.collideWithLiving(living);
            }
        }

        if (nearestBogie != null) {
            this.connectBogie(nearestBogie);
            return true;
        }

        return false;
    }

    /**
     * @return 連結可能か
     */
    private boolean collideWithBogie(EntityBogie bogie) {
        EntityTrainBase train = this.getTrain();
        EntityTrainBase train2 = bogie.getTrain();

        if (train != null && train2 != null && !train.equals(train2)) {
            //連結してるのは無視
            if (train.getFormation() != null && train.getFormation().containBogie(bogie)) {
                return false;
            }

            //範囲外
            if (!train.inConnectableRange(train2)) {
                return false;
            }

            //違う線路上なら無視
            RailMap rm0 = TileEntityLargeRailBase.getRailMapFromCoordinates(this.level(), this, this.getX(), this.getY(), this.getZ());
            RailMap rm1 = TileEntityLargeRailBase.getRailMapFromCoordinates(this.level(), bogie, bogie.getX(), bogie.getY(), bogie.getZ());
            if (!(rm0 != null && rm0.canConnect(rm1))) {
                return false;
            }

            //連結可能
            if (this.isActivated || bogie.isActivated) {
                return true;
            }

            //衝突処理 (弾性)
            float speed0 = train.getSpeed();
            float speed1 = train2.getSpeed();
            boolean b0 = train.getTrainDirection() == this.getBogieId();
            boolean b1 = train2.getTrainDirection() == bogie.getBogieId();

            float sp0 = b0 ? speed0 : -speed0;
            float sp1 = b1 ? -speed1 : speed1;
            if (sp0 - sp1 >= 0.0F) {
                train2.setSpeed(b0 ^ b1 ? speed0 : -speed0);
                train.setSpeed(b0 ^ b1 ? speed1 : -speed1);
            }
        }

        return false;
    }

    private void connectBogie(EntityBogie bogie) {
        if (this.getTrain() != null) {
            this.getTrain().connectTrain(this, bogie);
        }
        this.isActivated = false;
        bogie.isActivated = false;
    }

    private void collideWithLiving(LivingEntity entity) {
        EntityTrainBase train = this.getTrain();
        if (train == null) {
            return;
        }
        if (!entity.equals(train.getControllingPassenger()) && !entity.isPassenger()) {
            float speed = train.getSpeed();
            double dis = NGTMath.pow((this.getBbWidth() / 2.0F) + 0.375F, 2);

            if (speed > 0.0F && this.distanceToSqr(entity) < dis) {
                double d2 = entity.getX() - this.getX();
                double d3 = entity.getZ() - this.getZ();
                double d4 = (d2 * d2 + d3 * d3);
                entity.push(d2 / d4 * 10.0D, 0.3D, d3 / d4 * 10.0D);
                float damage = speed * 7.2F;
                entity.hurt(this.damageSources().mobAttack(null), damage);
            }
        }
    }

    @Override
    public void lerpTo(double x, double y, double z, float yaw, float pitch, int steps) {
        this.carPosRotationInc = steps;
        this.carYaw = yaw;
        this.carPitch = pitch;
    }

    public void setRoll(float par1) {
        this.carRoll = par1;
    }

    @Override
    public net.minecraft.world.InteractionResult interact(Player player, net.minecraft.world.InteractionHand hand) {
        if (this.getTrain() != null) {
            return player.isShiftKeyDown()
                    ? this.getTrain().interact(player, hand)
                    : this.getTrain().interactTrain(this, player);
        }
        return net.minecraft.world.InteractionResult.SUCCESS;
    }

    public void setTrain(EntityTrainBase train) {
        this.entityData.set(DATA_TRAIN_ID, train.getId());
        this.parentTrain = train;
    }

    public EntityTrainBase getTrain() {
        if (this.parentTrain == null || this.parentTrain.isRemoved()) {
            Entity entity = this.level().getEntity(this.entityData.get(DATA_TRAIN_ID));
            if (entity instanceof EntityTrainBase train) {
                this.parentTrain = train;
            }
        }
        return this.parentTrain;
    }

    public void setFront(boolean par1) {
        this.entityData.set(DATA_IS_FRONT, par1 ? (byte) 1 : (byte) 0);
    }

    public boolean isFront() {
        return this.entityData.get(DATA_IS_FRONT) == 1;
    }

    public byte getBogieId() {
        return this.entityData.get(DATA_BOGIE_ID);
    }

    public void setBogieId(byte par1) {
        this.entityData.set(DATA_BOGIE_ID, par1);
    }

    /**
     * @param yaw1 : 元の向き
     * @param yaw2 : 新しい向き
     */
    public static float fixBogieYaw(float yaw1, float yaw2) {
        float f0 = Math.abs(yaw1 - yaw2);
        f0 = f0 > 180.0F ? 360.0F - f0 : f0;
        return Mth.wrapDegrees(f0 > 90.0F ? yaw2 + 180.0F : yaw2);
    }

    public static float fixBogiePitch(float railPitch, float railYaw, float bogieYaw) {
        return Mth.wrapDegrees(Math.abs(bogieYaw - railYaw) > 45.0F ? -railPitch : railPitch);
    }
}
