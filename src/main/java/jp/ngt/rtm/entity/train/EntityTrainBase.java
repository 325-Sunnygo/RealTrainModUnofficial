package jp.ngt.rtm.entity.train;

import jp.ngt.ngtlib.math.NGTMath;
import jp.ngt.ngtlib.math.Vec3;
import jp.ngt.rtm.entity.train.util.BogieController;
import jp.ngt.rtm.entity.train.util.Formation;
import jp.ngt.rtm.entity.train.util.FormationEntry;
import jp.ngt.rtm.entity.train.util.FormationManager;
import jp.ngt.rtm.entity.train.util.TrainSpeedManager;
import jp.ngt.rtm.entity.train.util.TrainState;
import jp.ngt.rtm.entity.train.util.TrainState.TrainStateType;
import jp.ngt.rtm.entity.vehicle.EntityVehicleBase;
import jp.ngt.rtm.modelpack.cfg.TrainConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.Base64;

/**
 * 本家 jp.ngt.rtm.entity.train.EntityTrainBase (KaizPatchX) の忠実移植 (物理コア)。
 * updateSpeed / updateRoll / applyPhysicalEffect / TrainState 16byte 同期 / Formation 連携は本家のまま。
 * TODO(段階移植): ChunkLoader, ATS, 煙, パンタ/ドアのクライアントアニメ, PermissionManager, 実 ModelSet (Phase 4)。
 */
public abstract class EntityTrainBase extends EntityVehicleBase<TrainConfig> {
    private static final EntityDataAccessor<Integer> DATA_BOGIE0 =
            SynchedEntityData.defineId(EntityTrainBase.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_BOGIE1 =
            SynchedEntityData.defineId(EntityTrainBase.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> DATA_BYTE_ARRAY =
            SynchedEntityData.defineId(EntityTrainBase.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Float> DATA_SPEED =
            SynchedEntityData.defineId(EntityTrainBase.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Byte> DATA_CAB_DIR =
            SynchedEntityData.defineId(EntityTrainBase.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<String> DATA_MODEL_NAME =
            SynchedEntityData.defineId(EntityTrainBase.class, EntityDataSerializers.STRING);
    /**
     * 本家 PacketVehicleMovement (setRollAndSpeed) 代替: 振子ロールの同期
     */
    private static final EntityDataAccessor<Float> DATA_ROLL =
            SynchedEntityData.defineId(EntityTrainBase.class, EntityDataSerializers.FLOAT);
    /**
     * 本家 PacketVehicleMovement 代替: ヨー/ピッチの float 同期。
     * バニラのエンティティ回転パケットはバイト量子化 (約1.4°刻み) のため、
     * カーブで曲がり方が段階的に見える — float で送って滑らかにする。
     */
    private static final EntityDataAccessor<Float> DATA_YAW =
            SynchedEntityData.defineId(EntityTrainBase.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_PITCH =
            SynchedEntityData.defineId(EntityTrainBase.class, EntityDataSerializers.FLOAT);
    /**
     * 客席 (slotPos): 列車に直接乗せて座席オフセットで配置する方式。
     * EntityFloor に乗せる旧方式は視点固定バグの温床だったため作り直し。
     * "uuid|x|y|z;..." 形式で同期し、クライアントの positionRider でも使う。
     */
    private static final EntityDataAccessor<String> DATA_SEATS =
            SynchedEntityData.defineId(EntityTrainBase.class, EntityDataSerializers.STRING);
    /**
     * 編成 (Formation) のクライアント同期。本家 PacketFormation 相当。
     * <p>
     * 本家 Formation.sendPacket() は編成をクライアントへ送るが、移植では no-op のままで、
     * <b>クライアント側の getFormation() が常に null</b> だった。パックの描画スクリプトは
     * これを使って号車番号を出す (E259 等) ため、号車が 0 のままになり、方向幕のコマ番号が
     * 負になってテクスチャが巻き戻り、まったく別の行先 (「普通」等) が表示されていた。
     * <p>
     * 形式: "id|entryPos|entryDir|size" (編成なしは空文字)。
     */
    private static final EntityDataAccessor<String> DATA_FORMATION =
            SynchedEntityData.defineId(EntityTrainBase.class, EntityDataSerializers.STRING);

    public static final short MAX_AIR_COUNT = 2880;
    public static final short MIN_AIR_COUNT = 2480;
    public static final float TRAIN_WIDTH = 2.75F;
    public static final float TRAIN_HEIGHT = 1.25F - 0.0625F;//レールに合わせ高さ修正

    public BogieController bogieController = new BogieController();
    private Formation formation;

    private float trainSpeed;
    /**
     * 実際に車体へ効かせている加減速度 (tick 毎)。目標加速度へ上限ジャークで漸近させ、
     * 力行/制動の突き上げ (急発進・急ブレーキ) を無くして実車のような立ち上がりにする。
     */
    private float appliedAccel;
    /** 制動の加加速度上限 (tick 毎の加速度変化量)。小さいほどブレーキがじんわり効く。 */
    private static final float BRAKE_JERK = 0.00018F;
    /** 力行の加加速度上限。制動よりやや速く立ち上げる。 */
    private static final float POWER_JERK = 0.00026F;
    /**
     * notch x -18
     */
    public int brakeCount = 72;
    public int brakeAirCount = MAX_AIR_COUNT;
    public boolean complessorActive;

    private float wave;

    private TrainConfig configCache;

    public EntityTrainBase(EntityType<?> type, Level level) {
        super(type, level);
    }

    /**
     * TrainConfig の供給。暫定: VehicleDefinition アダプタ (Phase 4 で ModelSet 直結)。
     */
    @Override
    public TrainConfig getConfig() {
        if (this.configCache == null) {
            this.configCache = jp.ngt.rtm.modelpack.cfg.TrainConfigAdapter.get(this.getModelName());
        }
        return this.configCache;
    }

    public String getModelName() {
        return this.entityData.get(DATA_MODEL_NAME);
    }

    @Override
    protected String getResourceName() {
        return this.getModelName();
    }

    /**
     * 本家 getModelSet() 互換 (スクリプトが getConfig() を参照)。
     * TODO(Phase 4): ModelSetTrainClient の本実装。
     */
    public jp.ngt.rtm.modelpack.modelset.ModelSetCompat getModelSet() {
        return new jp.ngt.rtm.modelpack.modelset.ModelSetCompat(this.getConfig());
    }

    @Override
    public float getVehicleSpeed() {
        return this.getSpeed();
    }

    @Override
    protected float getMoveDir() {
        int i = this.getTrainDirection();
        if (this.getBogie(i) != null) {
            boolean b0 = this.getBogie(i).isFront();
            return ((i == 0 && b0) || (i == 1 && !b0)) ? 1.0F : -1.0F;
        }
        return 1.0F;
    }

    //クライアント側ドア開閉音 (sound_DoorOpen/sound_DoorClose) の状態変化検出用。
    //209/125 系等の多くのパックはサウンドスクリプトでドア音を鳴らさず、この JSON 設定に頼る。
    private int prevDoorStateForSound;
    private boolean doorSoundStateInitialized;

    /**
     * 本家 updateAnimation (Client): 座席/ドア/パンタのアニメカウンタ。
     */
    @Override
    protected void updateAnimation() {
        super.updateAnimation();

        if (this.getTrainDirection() == 0 && this.seatRotation > -MAX_SEAT_ROTATION) {
            --this.seatRotation;
        }
        if (this.getTrainDirection() == 1 && this.seatRotation < MAX_SEAT_ROTATION) {
            ++this.seatRotation;
        }

        int doorState = this.getTrainStateData(TrainStateType.State_Door.id);
        if ((doorState & 1) == 1) {
            if (this.doorMoveR < MAX_DOOR_MOVE) {
                ++this.doorMoveR;
            }
        } else if (this.doorMoveR > 0) {
            --this.doorMoveR;
        }
        if ((doorState & 2) == 2) {
            if (this.doorMoveL < MAX_DOOR_MOVE) {
                ++this.doorMoveL;
            }
        } else if (this.doorMoveL > 0) {
            --this.doorMoveL;
        }

        //クライアント側: ドア状態 (State_Door, bit0=右/bit1=左) の変化を検出して開閉音を鳴らす。
        //本家 RTM 同様、サウンドスクリプトを持たない/ドア音を扱わないパック (209/125 系等) でも
        //JSON の sound_DoorOpen/sound_DoorClose が鳴るようにする。初回は状態記録のみ (スポーン時無音)。
        if (this.level().isClientSide()) {
            if (!this.doorSoundStateInitialized) {
                this.prevDoorStateForSound = doorState;
                this.doorSoundStateInitialized = true;
            } else if (doorState != this.prevDoorStateForSound) {
                int prev = this.prevDoorStateForSound;
                this.prevDoorStateForSound = doorState;
                boolean opened = (doorState & ~prev & 3) != 0; //新たに開いた側
                boolean closed = (~doorState & prev & 3) != 0; //新たに閉じた側
                com.portofino.realtrainmodunofficial.vehicle.VehicleDefinition def =
                        com.portofino.realtrainmodunofficial.vehicle.VehicleRegistry.getById(this.getModelName());
                if (def != null) {
                    //ドア開閉は離散イベント音: 登録制ラッチを通すと 2 回目以降が鳴らなくなるため
                    //bypass=true で毎回そのまま鳴らす (状態変化時にしか呼ばれない)
                    if (opened && !def.getSoundDoorOpen().isBlank()) {
                        com.portofino.realtrainmodunofficial.client.sound.LegacyScriptSoundManager
                                .playLegacyId(this, def.getSoundDoorOpen(), 1.0F, 1.0F, false, true);
                    }
                    if (closed && !def.getSoundDoorClose().isBlank()) {
                        com.portofino.realtrainmodunofficial.client.sound.LegacyScriptSoundManager
                                .playLegacyId(this, def.getSoundDoorClose(), 1.0F, 1.0F, false, true);
                    }
                }
            }
        }

        int pantoState = this.getTrainStateData(TrainStateType.State_Pantograph.id);
        if (pantoState == TrainState.Pantograph_Down.data) {
            if (this.pantograph_F < MAX_PANTOGRAPH_MOVE) {
                ++this.pantograph_F;
            }
            if (this.pantograph_B < MAX_PANTOGRAPH_MOVE) {
                ++this.pantograph_B;
            }
        } else {
            //TODO 本家は架線高さで停止位置を決める (getPantographMaxHeight)
            if (this.pantograph_F > 0) {
                --this.pantograph_F;
            }
            if (this.pantograph_B > 0) {
                --this.pantograph_B;
            }
        }
    }

    public void setModelName(String name) {
        this.entityData.set(DATA_MODEL_NAME, name == null ? "" : name);
        this.configCache = null;
    }

    public void spawnTrain(Level world) {
        //DATA_YAW/PITCH の初期値を実姿勢に (デフォルト 0 のまま初期同期されると
        //クライアントが一瞬ヨー 0 にスナップしてから回転して見える)
        if (!world.isClientSide) {
            this.entityData.set(DATA_YAW, this.getYRot());
            this.entityData.set(DATA_PITCH, this.getXRot());
        }
        //spawnの順番は「先に台車」
        this.bogieController.createBogie(world, this);
        this.bogieController.setupBogiePos(this);
        this.bogieController.spawnBogies(world, this);
        world.addFreshEntity(this);
        this.formation = FormationManager.getInstance(world.isClientSide).createNewFormation(this);
        //カーブ上スポーン対策: setupBogiePos は直線配置 (弦) のため、カーブでは
        //台車が線路から浮いて脱線して見える。forceMove でレール吸着を 1 回実行し、
        //台車をレールへ、車体を台車間へ整列させる (走り出しと同じ経路)。
        if (!world.isClientSide) {
            this.bogieController.moveTrainWithBogie(this, null, 0.0F, true);
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_BOGIE0, 0);
        builder.define(DATA_BOGIE1, 0);
        builder.define(DATA_BYTE_ARRAY, Base64.getEncoder().encodeToString(new byte[16]));
        builder.define(DATA_SPEED, 0.0F);
        builder.define(DATA_CAB_DIR, (byte) 0);
        builder.define(DATA_MODEL_NAME, "");
        builder.define(DATA_ROLL, 0.0F);
        builder.define(DATA_YAW, 0.0F);
        builder.define(DATA_PITCH, 0.0F);
        builder.define(DATA_SEATS, "");
        builder.define(DATA_FORMATION, "");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag nbt) {
        CompoundTag entryData = new CompoundTag();
        this.writeFormationData(entryData);
        nbt.put("FormationEntry", entryData);

        nbt.putInt("trainDir", this.getTrainDirection());
        nbt.putString("byteArray", this.entityData.get(DATA_BYTE_ARRAY));
        nbt.putByte("cabDir", this.entityData.get(DATA_CAB_DIR));
        nbt.putString("modelName", this.getModelName());
    }

    private void writeFormationData(CompoundTag nbt) {
        if (this.formation == null) {
            return;
        }
        FormationEntry entry = this.formation.getEntry(this);
        if (entry != null) {
            nbt.putLong("FormationId", this.formation.id);
            nbt.putByte("EntryPos", entry.entryId);
            nbt.putByte("EntryDir", entry.dir);
            nbt.putInt("FormationSize", this.formation.size());
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag nbt) {
        this.readFormationData(nbt.getCompound("FormationEntry"));
        this.setTrainDirection(nbt.getInt("trainDir"));
        this.entityData.set(DATA_BYTE_ARRAY, nbt.getString("byteArray"));
        this.entityData.set(DATA_CAB_DIR, nbt.getByte("cabDir"));
        this.setModelName(nbt.getString("modelName"));
        //セーブからのロードでも初期同期のヨー/ピッチを実姿勢に合わせる
        this.entityData.set(DATA_YAW, this.getYRot());
        this.entityData.set(DATA_PITCH, this.getXRot());
    }

    private void readFormationData(CompoundTag nbt) {
        long id = nbt.getLong("FormationId");
        byte pos = nbt.getByte("EntryPos");
        byte dir = nbt.getByte("EntryDir");
        int size = nbt.getInt("FormationSize");
        FormationManager fm = FormationManager.getInstance(this.level().isClientSide);
        Formation f0 = fm.getFormation(id);
        if (f0 == null) {
            if (nbt.contains("FormationId")) {
                this.formation = fm.createNewFormation(this, id, pos, dir, size);
            } else {
                this.formation = fm.createNewFormation(this);
            }
        } else {
            this.formation = f0;
            f0.setTrain(this, pos, dir);
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        super.remove(reason);
        //チャンクローダーのチケットを解放 (残すと空チャンクを永久ロードする)
        if (!this.level().isClientSide && this.chunkLoaderLastChunk != Long.MIN_VALUE) {
            com.portofino.realtrainmodunofficial.world.TrainChunkLoader.release(this, this.chunkLoaderLastChunk);
            this.chunkLoaderLastChunk = Long.MIN_VALUE;
        }
        if (!this.level().isClientSide && reason.shouldDestroy()) {
            this.bogieController.setDead();
            if (this.formation != null) {
                try {
                    this.formation.onRemovedTrain(this);
                } catch (IndexOutOfBoundsException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    // ---- サウンド (クライアント側) ----
    //
    // 列車アイテムが出すのはこのクラス (本家系) だが、サウンド一式は旧 TrainEntity 側にしか
    // 実装されていなかったため、リリース版では走行音が一切鳴らなくなっていた。
    // 旧 TrainEntity.tick() のサウンド処理と同じものをここに持ってくる。

    /** 車両パックのサウンドスクリプト (sound_*.js)。クライアント専用。 */
    private javax.script.ScriptEngine soundScriptEngine;
    /** スクリプトの読み込みを 1 度だけ試すためのフラグ (無い車両で毎 tick 探しに行かない)。 */
    private boolean attemptedSoundScriptLoad;

    /** 車両パックのサーバースクリプト (serverScriptPath, Server_*.js)。サーバー専用。 */
    private javax.script.ScriptEngine serverScriptEngine;
    private boolean attemptedServerScriptLoad;

    @Override
    public void tick() {
        super.tick();

        if (this.level().isClientSide()) {
            this.tickSound();
        } else {
            //DataMap を書き換えるサーバースクリプトを先に回してから同期する。
            this.tickServerScript();
            this.syncFormationData();
            this.syncDataMap();
        }
    }

    /**
     * 本家 serverScriptPath (Server_*.js) を毎 tick 実行する。これらは onUpdate(entity, executer) 内で
     * {@code entity.getResourceState().getDataMap().setString("trainType", ...)} のように DataMap を書き換え、
     * 方向幕・種別・trainType 等を決める。
     * <p>
     * DataMap の同期 ({@link #syncDataMap}) は実装済みだったが、<b>書き込み側のサーバースクリプト実行が
     * 実際に設置される本家系エンティティ (EntityTrainBase) に無く</b>、旧 TrainEntity にしか無かったため、
     * DataMap を使うパック (例: 105系 hikari の server_105_oka.js が trainType を設定) で種別が既定のままに
     * なっていた。ここで実行すれば server → client (DataMapSyncPayload) まで通り、描画スクリプトが読める。
     */
    private void tickServerScript() {
        this.ensureServerScriptLoaded();
        if (this.serverScriptEngine != null) {
            com.portofino.realtrainmodunofficial.script.TrainScriptSystem
                    .invokeServerScriptOnUpdate(this.serverScriptEngine, this);
        }
    }

    private void ensureServerScriptLoaded() {
        if (this.attemptedServerScriptLoad) {
            return;
        }
        this.attemptedServerScriptLoad = true;
        com.portofino.realtrainmodunofficial.vehicle.VehicleDefinition def =
                com.portofino.realtrainmodunofficial.vehicle.VehicleRegistry.getById(this.getModelName());
        if (def == null || !def.hasServerScript()) {
            return;
        }
        try {
            this.serverScriptEngine =
                    com.portofino.realtrainmodunofficial.client.model.MqoModelLoader.loadServerScriptForVehicle(def);
        } catch (Throwable t) {
            //専用サーバー等で client パッケージのモデルローダが読めなくても列車 tick を巻き込まない。
            com.portofino.realtrainmodunofficial.RealTrainModUnofficial.LOGGER
                    .warn("Failed to load train server script for {}: {}", this.getModelName(), t.toString());
        }
    }

    /**
     * サーバー: DataMap の flag=1 書き込み (ATSA の HUD 情報等) をクライアントへ配信する。
     * 変更があった時だけ・10 tick おきにまとめて送る (軽量)。
     */
    private void syncDataMap() {
        if ((this.tickCount % 10) != 0) {
            return;
        }
        java.util.Map<String, Object> pending = this.getResourceState().getDataMap().drainPendingSync();
        if (pending.isEmpty()) {
            return;
        }
        java.util.Map<String, String> encoded = new java.util.HashMap<>(pending.size());
        pending.forEach((k, v) -> encoded.put(k, jp.ngt.rtm.modelpack.state.DataMap.encodeSyncedValue(v)));
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingEntity(this,
                new com.portofino.realtrainmodunofficial.network.DataMapSyncPayload(this.getId(), encoded));
    }

    /** サーバー: 編成の位置情報をクライアントへ流す (値が変わった時だけ同期が走る)。 */
    private void syncFormationData() {
        String data = "";
        if (this.formation != null) {
            FormationEntry entry = this.formation.getEntry(this);
            if (entry != null) {
                data = this.formation.id + "|" + entry.entryId + "|" + entry.dir + "|" + this.formation.size();
            }
        }
        if (!data.equals(this.entityData.get(DATA_FORMATION))) {
            this.entityData.set(DATA_FORMATION, data);
        }
    }

    /** クライアント: 受け取った編成データから Formation を組み立て直す。 */
    private void applyClientFormationData(String data) {
        if (data == null || data.isEmpty()) {
            this.formation = null;
            return;
        }
        String[] parts = data.split("\\|");
        if (parts.length != 4) {
            return;
        }
        long id;
        byte pos;
        byte dir;
        int size;
        try {
            id = Long.parseLong(parts[0]);
            pos = Byte.parseByte(parts[1]);
            dir = Byte.parseByte(parts[2]);
            size = Integer.parseInt(parts[3]);
        } catch (NumberFormatException e) {
            return;
        }
        if (size <= 0 || pos < 0 || pos >= size) {
            return;
        }
        FormationManager fm = FormationManager.getInstance(true);
        Formation f = fm.getFormation(id);
        if (f == null) {
            //この編成の 1 両目がクライアントに届いた
            this.formation = fm.createNewFormation(this, id, pos, dir, size);
            return;
        }
        //両数が変わった (連結/解放) → 配列を作り直して既存の車両を引き継ぐ
        if (f.size() != size) {
            FormationEntry[] resized = new FormationEntry[size];
            System.arraycopy(f.entries, 0, resized, 0, Math.min(f.entries.length, size));
            f.entries = resized;
        }
        //自分が別の位置に居座っていたら消してから登録し直す (二重登録の防止)
        for (int i = 0; i < f.entries.length; ++i) {
            FormationEntry e = f.entries[i];
            if (e != null && this.equals(e.train) && i != pos) {
                f.entries[i] = null;
            }
        }
        f.setTrain(this, pos, dir);
        this.formation = f;
    }

    /**
     * 走行音を鳴らす。サウンドスクリプトがある車両はそれを回し、無ければ
     * 車両 JSON の running sound 定義 (速度で音量/ピッチを変えるだけの簡易版) で鳴らす。
     */
    private void tickSound() {
        if (!this.attemptedSoundScriptLoad) {
            this.attemptedSoundScriptLoad = true;
            com.portofino.realtrainmodunofficial.vehicle.VehicleDefinition def =
                    com.portofino.realtrainmodunofficial.vehicle.VehicleRegistry.getById(this.getModelName());
            if (def != null && def.hasSoundScript()) {
                this.soundScriptEngine =
                        com.portofino.realtrainmodunofficial.client.model.MqoModelLoader.loadSoundScriptForVehicle(def);
            }
        }

        if (this.soundScriptEngine != null) {
            //スクリプトが鳴らす音と JSON 定義の自動走行音が二重に鳴らないようにする
            com.portofino.realtrainmodunofficial.client.sound.LegacyScriptSoundManager.stopAutoRunningSound(this);
            //サウンド管理は本家 SoundUpdaterVehicle 方式 (登録制)。playSound は
            //「登録済みなら音量/ピッチ更新のみ」なので前後処理は不要。
            com.portofino.realtrainmodunofficial.script.TrainScriptSystem.invokeSoundScript(this.soundScriptEngine, this);
        } else {
            com.portofino.realtrainmodunofficial.client.sound.LegacyScriptSoundManager.tickJsonRunningSound(this);
        }
    }

    @Override
    protected void onVehicleUpdate() {
        this.updateSpeed();

        if (this.existBogies()) {
            this.bogieController.updateBogies();
        }

        //ブレーキ空気圧 (本家はクライアント側アニメ)
        if (this.level().isClientSide) {
            if (this.complessorActive) {
                ++this.brakeAirCount;
                if (this.brakeAirCount >= MAX_AIR_COUNT) {
                    this.complessorActive = false;
                }
            } else if (this.brakeAirCount < MIN_AIR_COUNT) {
                this.complessorActive = true;
            }
        }
    }

    @Override
    protected void updateMovement() {
        if (this.formation != null && this.formation.isFrontCar(this)) {
            this.formation.updateTrainMovement();
        }
    }

    /**
     * チャンクローダーの前回チャンク (ChunkPos.toLong)。Long.MIN_VALUE = 未登録
     */
    private long chunkLoaderLastChunk = Long.MIN_VALUE;

    @Override
    protected void applyPhysicalEffect() {
        //ヨー/ピッチの float 同期 (updateMovement で確定した姿勢を毎tick送る)
        if (!this.level().isClientSide) {
            this.entityData.set(DATA_YAW, this.getYRot());
            this.entityData.set(DATA_PITCH, this.getXRot());
            //本家 ChunkLoader (TrainState State_ChunkLoader): ON なら周囲チャンクを強制ロード。
            //軽量化: 10tick に 1 回だけチェック + 編成先頭車のみ (全車両分のチケットは張らない)。
            //チケットの付替えはチャンクをまたいだ時のみ。
            if (this.tickCount % 10 == 0) {
                boolean loaderOn = this.getTrainStateData(TrainState.TrainStateType.State_ChunkLoader.id) != 0
                        && this.formation != null && this.formation.isFrontCar(this);
                this.chunkLoaderLastChunk = com.portofino.realtrainmodunofficial.world.TrainChunkLoader
                        .update(this, loaderOn, this.chunkLoaderLastChunk);
            }
        }
        this.setDeltaMovement(this.getDeltaMovement().scale(0.99D));

        //本家はピッチを無条件で毎tick水平へ減衰させるが、坂に停車した列車まで
        //水平になって片方が浮いてしまう。レールに乗っている間 (台車が RailMap を
        //保持) は勾配ピッチを維持し、脱線/落下時のみ減衰させる。
        boolean onRail = this.existBogies()
                && ((this.getBogie(0) != null && this.getBogie(0).hasRailMap())
                    || (this.getBogie(1) != null && this.getBogie(1).hasRailMap()));
        float pitch = this.getXRot();
        if (!onRail) {
            float f0 = 0.125F;
            if (pitch > 0.0F) {
                pitch -= f0;
            } else if (pitch < 0.0F) {
                pitch += f0;
            }
            if (Math.abs(pitch) < f0) {
                pitch = 0.0F;
            }
            this.setXRot(pitch);
        }

        if (this.existBogies()) {
            this.getBogie(0).setXRot(pitch);
            this.getBogie(1).setXRot(pitch * -1.0F);
        }
    }

    //BCから呼び出し
    public void updateRoll(float par1) {
        TrainConfig cfg = this.getConfig();
        float f0 = -cfg.rolling;
        float pendulum = Mth.wrapDegrees((this.getYRot() - this.prevRotationYawVehicle) * f0);
        if (this.getTrainDirection() == 1) {
            pendulum *= -1.0F;
        }
        float roll = par1 + pendulum;
        this.wave = (float) ((this.wave + this.trainSpeed * cfg.rollSpeedCoefficient) % (2.0D * Math.PI));
        float sw = (NGTMath.getSin(this.wave) + NGTMath.getSin(this.wave * cfg.rollVariationCoefficient)) * 0.5F;
        this.rotationRoll = roll + sw * cfg.rollWidthCoefficient;
        if (!this.level().isClientSide) {
            //本家 PacketVehicleMovement 代替: クライアントは vehicleRoll へ補間
            this.entityData.set(DATA_ROLL, this.rotationRoll);
        }
    }

    /**
     * 残ブレーキ量の割合 (0.0 = 完全に緩解, 1.0 = 最大ブレーキ)。
     * brakeCount は「ブレーキノッチ × 18」で溜まり、緩解時は 1tick に 2 ずつ抜ける。
     */
    public float getBrakeRatio() {
        int maxBrakeCount = (this.getConfig().deccelerations.length - 1) * 18;
        if (maxBrakeCount <= 0) {
            return this.brakeCount > 0 ? 1.0F : 0.0F;
        }
        float ratio = (float) this.brakeCount / (float) maxBrakeCount;
        return Math.max(0.0F, Math.min(1.0F, ratio));
    }

    protected void updateSpeed() {
        int notch = this.getNotch();

        float speed = this.trainSpeed;
        //停車中に残っているブレーキ (brakeCount) が加速をどれだけ殺すか。1.0=緩解済み。
        //本家は「緩解し切るまで加速を一切計算しない (isBrakeDisabled=false)」ため、
        //B8 から発進すると 144/2 = 72tick (約3.6秒) 完全に無反応 → 抜けた瞬間フル加速、
        //という不自然な挙動になっていた。残ブレーキ量に比例して加速を絞ることで、
        //緩解が進むにつれて加速が立ち上がる (無反応時間も突然の加速も無くなる)。
        float brakeReleaseFactor = 1.0F;

        //ブレーキ処理, 全ての車両で
        if (notch < 0) {
            int max = notch * -18;
            if (this.brakeCount < max) {
                ++this.brakeCount;
                if (this.level().isClientSide) {
                    --this.brakeAirCount;
                }
            } else if (this.brakeCount > max) {
                this.brakeCount -= (this.brakeCount - max > 1) ? 2 : 1;
            }
        } else {
            if (this.brakeCount > 0) {
                if (speed <= 0.0F) {
                    brakeReleaseFactor = 1.0F - this.getBrakeRatio();
                }
                this.brakeCount -= 2;
            } else if (this.brakeCount < 0) {
                this.brakeCount = 0;
            }
        }

        //速度処理, 先頭車のみ
        if (this.isControlCar()) {
            if (!this.level().isClientSide) {
                TrainConfig cfg = this.getConfig();
                float targetAccel = TrainSpeedManager.getAcceleration(this, notch, Math.abs(speed), cfg)
                        * brakeReleaseFactor;
                TrainState dir = this.getTrainState(10);
                if ((dir == TrainState.Direction_Back && speed > 0) || (dir == TrainState.Direction_Front && speed < 0)) {
                    targetAccel = Math.abs(targetAccel);
                }
                if (dir == TrainState.Direction_Back) {
                    targetAccel *= -1;
                }

                //現実的な加減速: 目標加速度へ上限ジャークで漸近させる。ノッチを入れた瞬間に
                //フル制動/フル力行がかかる突き上げを無くし、実車のようにじんわり立ち上げる。
                float maxJerk = notch < 0 ? BRAKE_JERK : POWER_JERK;
                this.appliedAccel += Mth.clamp(targetAccel - this.appliedAccel, -maxJerk, maxJerk);
                float prevSpeed = speed;
                speed += this.appliedAccel;
                //制動で 0 を跨いだら停止させる (ジャーク制限で行き過ぎても逆走しない)
                if (notch < 0 && prevSpeed != 0.0F
                        && Math.signum(speed) != Math.signum(prevSpeed)) {
                    speed = 0.0F;
                    this.appliedAccel = 0.0F;
                }

                if (notch >= 0)//ブレーキ解
                {
                    float deceleration;
                    if (this.getXRot() == 0.0F) {
                        float f1 = -cfg.deccelerations[0];
                        deceleration = speed > 0.0F ? f1 : (speed < 0.0F ? -f1 : 0.0F);
                    } else//坂
                    {
                        float dec = 0.0125F;
                        float f2 = (this.getTrainDirection() == 0) ? dec : -dec;
                        deceleration = NGTMath.sin(this.getXRot()) * f2;
                    }
                    speed -= deceleration;
                }

                this.setSpeed(speed);
            }
        }
    }

    /**
     * 連結時の車両同士の距離
     */
    public double getDefaultDistanceToConnectedTrain(EntityTrainBase par1) {
        double d0 = this.getConfig().trainDistance;
        double d1 = par1.getConfig().trainDistance;
        return d0 + d1;
    }

    /**
     * 車両同士が連結可能な距離内にあるか
     */
    public boolean inConnectableRange(EntityTrainBase par1) {
        double d0 = this.getDefaultDistanceToConnectedTrain(par1);
        return this.distanceToSqr(par1) <= d0 * d0;
    }

    // ===== 客席 (座席オフセット搭乗) =====

    /**
     * 乗客 UUID → 座席オフセット。サーバーが正、DATA_SEATS でクライアントへ同期。
     */
    private final java.util.Map<java.util.UUID, float[]> seatOffsets = new java.util.HashMap<>();

    public boolean hasSeat(Entity rider) {
        return rider != null && this.seatOffsets.containsKey(rider.getUUID());
    }

    public boolean isSeatOccupied(float[] pos) {
        for (float[] p : this.seatOffsets.values()) {
            if (Math.abs(p[0] - pos[0]) < 0.01F && Math.abs(p[1] - pos[1]) < 0.01F && Math.abs(p[2] - pos[2]) < 0.01F) {
                return true;
            }
        }
        return false;
    }

    /**
     * 客席への着席 (Server Only)。座席オフセット (slotPos) で列車本体に直接乗せる。
     */
    public boolean mountToSeat(Player player, float[] partPos) {
        return this.mountEntityToSeat(player, partPos);
    }

    /**
     * 任意のエンティティを客席に着席させる (Server Only)。乗客シミュレーション mod
     * (乗客 NPC) が座席へ座らせるために公開する。座席オフセットを登録するので
     * {@link #canAddPassenger} の「座席なし乗車は 1 人」制限に引っかからず複数乗れる。
     * {@link #positionRider} がこのオフセットに座らせ、破壊/降車時は removePassenger で解除される。
     */
    public boolean mountEntityToSeat(net.minecraft.world.entity.Entity entity, float[] partPos) {
        if (this.level().isClientSide || this.isSeatOccupied(partPos) || this.hasPassenger(entity)) {
            return false;
        }
        this.seatOffsets.put(entity.getUUID(), partPos.clone());
        this.syncSeats();
        boolean ok = entity.startRiding(this, true);
        if (!ok) {
            this.seatOffsets.remove(entity.getUUID());
            this.syncSeats();
        }
        return ok;
    }

    private void syncSeats() {
        if (this.level().isClientSide) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        this.seatOffsets.forEach((id, p) -> {
            if (sb.length() > 0) {
                sb.append(';');
            }
            sb.append(id).append('|').append(p[0]).append('|').append(p[1]).append('|').append(p[2]);
        });
        this.entityData.set(DATA_SEATS, sb.toString());
    }

    private void readSeats(String s) {
        this.seatOffsets.clear();
        if (s == null || s.isEmpty()) {
            return;
        }
        for (String entry : s.split(";")) {
            String[] t = entry.split("\\|");
            if (t.length == 4) {
                try {
                    this.seatOffsets.put(java.util.UUID.fromString(t[0]),
                            new float[]{Float.parseFloat(t[1]), Float.parseFloat(t[2]), Float.parseFloat(t[3])});
                } catch (Exception ignored) {
                }
            }
        }
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        if (this.seatOffsets.containsKey(passenger.getUUID())) {
            return true;
        }
        //運転士 (座席なし乗車) は 1 人のみ
        return this.getPassengers().stream().allMatch(p -> this.seatOffsets.containsKey(p.getUUID()));
    }

    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        if (!this.level().isClientSide && this.seatOffsets.remove(passenger.getUUID()) != null) {
            this.syncSeats();
        }
    }

    @Override
    protected void positionRider(Entity rider, Entity.MoveFunction move) {
        if (this.hasPassenger(rider)) {
            //客席 (座席オフセット): EntityVehiclePart.updatePartPos と同じ回転
            float[] seat = this.seatOffsets.get(rider.getUUID());
            if (seat != null) {
                Vec3 sv = new Vec3(seat[0], seat[1], seat[2]);
                sv = sv.rotateAroundZ(-this.rotationRoll);
                sv = sv.rotateAroundX(this.getXRot());
                sv = sv.rotateAroundY(this.getYRot());
                //旧 EntityFloor 搭乗時の実効高さ (floorY + 0.15) に合わせる
                move.accept(rider,
                        this.getX() + sv.getX(),
                        this.getY() + sv.getY() + 0.15D,
                        this.getZ() + sv.getZ());
                return;
            }
            float[][] pos = this.getConfig().getPlayerPos();
            int dir = this.entityData.get(DATA_CAB_DIR);
            Vec3 v31 = new Vec3(pos[dir][0], pos[dir][1], pos[dir][2]);
            v31 = v31.rotateAroundZ(-this.rotationRoll);
            v31 = v31.rotateAroundX(this.getXRot());
            v31 = v31.rotateAroundY(this.getYRot());
            //座位: playerPos は本家で搭乗者の腰位置。1.21 は渡した Y がほぼ腰になるため
            //追加リフトはせず、モデル床に合うよう少し下げる。
            move.accept(rider,
                    this.getX() + v31.getX(),
                    this.getY() + v31.getY() - 0.35D,
                    this.getZ() + v31.getZ());
        }
    }

    /**
     * 本家 attackEntityFrom 相当: バール/クリエイティブで撤去
     */
    @Override
    public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
        if (this.level().isClientSide || this.isRemoved()) {
            return false;
        }
        if (source.getEntity() instanceof Player player) {
            if (player.getAbilities().instabuild
                    || player.getMainHandItem().getItem() instanceof com.portofino.realtrainmodunofficial.item.CrowbarItem) {
                this.kill();
                return true;
            }
        }
        return false;
    }

    /**
     * EntityBogieで呼ばれる (乗車・連結棒)
     */
    protected InteractionResult interactTrain(EntityBogie bogie, Player player) {
        if (!this.level().isClientSide) {
            int id1 = bogie.getBogieId();
            net.minecraft.world.item.ItemStack itemstack = player.getMainHandItem();
            if (!itemstack.isEmpty() && itemstack.getItem() instanceof com.portofino.realtrainmodunofficial.item.CrowbarItem) {
                if (id1 >= 0) {
                    if (this.getConnectedTrain(id1) == null) {
                        if (bogie.isActivated) {
                            if (this.getNotch() == 0) {
                                this.setTrainDirection(id1);
                                this.setNotch(1);
                                jp.ngt.ngtlib.io.NGTLog.sendChatMessage(player, "message.train.start_auto_concatenation");
                            } else {
                                jp.ngt.ngtlib.io.NGTLog.sendChatMessage(player, "message.train.already_concatenation_mode");
                            }
                        } else {
                            bogie.isActivated = true;
                            jp.ngt.ngtlib.io.NGTLog.sendChatMessage(player, "message.train.concatenation_mode");
                        }
                    } else {
                        this.formation.onDisconnectedTrain(this, id1);
                        jp.ngt.ngtlib.io.NGTLog.sendChatMessage(player, "message.train.deconcatenation");
                    }
                }
            } else if (!itemstack.isEmpty()
                    && itemstack.getItem() instanceof com.portofino.realtrainmodunofficial.item.MotormanItem) {
                //本家: 運転士アイテムで列車を右クリック → 運転士がスポーンして運転台に乗車
                boolean driverSeatFree = this.getPassengers().stream().allMatch(this::hasSeat);
                if (id1 >= 0 && driverSeatFree) {
                    jp.ngt.rtm.entity.npc.EntityMotorman motorman =
                            jp.ngt.rtm.entity.RTMEntities.MOTORMAN.get().create(this.level());
                    if (motorman != null) {
                        motorman.moveTo(this.getX(), this.getY(), this.getZ(), 0.0F, 0.0F);
                        if (this.level().addFreshEntity(motorman)) {
                            this.mountEntityToTrain(motorman, id1);
                            if (!player.getAbilities().instabuild) {
                                itemstack.shrink(1);
                            }
                        }
                    }
                }
            } else if (id1 >= 0) {
                this.mountEntityToTrain(player, id1);
            }
        }
        return InteractionResult.SUCCESS;
    }

    private void mountEntityToTrain(Entity entity, int direction) {
        if (this.isControlCar()) {
            this.setTrainDirection(direction);
            if (this.formation != null && this.formation.size() > 1) {
                byte data = this.getTrainStateData(TrainStateType.State_Direction.id);
                byte newData = this.getCabDirection() == this.getTrainDirection() ? data : (byte) (data ^ 2);
                this.setTrainStateData_NoSync(TrainStateType.State_Direction.id, newData);
            }
        }
        this.entityData.set(DATA_CAB_DIR, (byte) direction);
        entity.startRiding(this);
    }

    /**
     * @param par1 連結される台車
     * @param par2 連結対象の台車
     */
    public void connectTrain(EntityBogie par1, EntityBogie par2) {
        if (!this.level().isClientSide && par2.getTrain() != null) {
            int id1 = par1.getBogieId();
            int id2 = par2.getBogieId();
            if (id1 >= 0 && id2 >= 0 && this.getConnectedTrain(id1) == null && par2.getTrain().getFormation() != null) {
                this.formation.connectTrain(this, par2.getTrain(), id1, id2, par2.getTrain().getFormation());
                this.level().levelEvent(1022, this.blockPosition(), 0);
            }
        }
    }

    public float getSpeed() {
        return this.entityData.get(DATA_SPEED);
    }

    public void setSpeed(float par1) {
        if (this.level().isClientSide) {
            this.trainSpeed = par1;
        } else if (this.isControlCar() && this.formation != null) {
            this.formation.setSpeed(par1);
        }
    }

    public void setSpeed_NoSync(float par1) {
        if (this.trainSpeed != par1) {
            this.trainSpeed = par1;
            this.entityData.set(DATA_SPEED, par1);
        }
    }

    public void stopTrain(boolean changeSpeed) {
        if (this.formation != null) {
            this.setEBNotch();
            if (changeSpeed) {
                this.setSpeed(0.0F);
            }
        }
    }

    public boolean isControlCar() {
        int data = this.getTrainStateData(TrainStateType.State_Direction.id);
        return data == TrainState.Direction_Front.data || data == TrainState.Direction_Back.data;
    }

    public boolean existBogies() {
        return this.getBogie(0) != null && this.getBogie(1) != null;
    }

    public EntityBogie getBogie(int bogieId) {
        if (this.bogieController.getBogie(bogieId) == null) {
            EntityDataAccessor<Integer> id = bogieId == 0 ? DATA_BOGIE0 : DATA_BOGIE1;
            Entity entity = this.level().getEntity(this.entityData.get(id));
            if (entity instanceof EntityBogie bogie) {
                this.bogieController.setBogie(bogieId, bogie);
            }
        }
        return this.bogieController.getBogie(bogieId);
    }

    /**
     * EntityBogieから呼び出し
     */
    public void setBogie(int id, EntityBogie bogie) {
        this.bogieController.setBogie(id, bogie);
        this.entityData.set(id == 0 ? DATA_BOGIE0 : DATA_BOGIE1, bogie.getId());
    }

    /**
     * @param par1 0 or 1
     */
    public EntityTrainBase getConnectedTrain(int par1) {
        if (this.formation != null) {
            FormationEntry entry = this.formation.getEntry(this);
            if (entry == null) {
                return null;
            }
            int pos = entry.entryId;
            int dif = (par1 == 0) ? -1 : 1;
            if (entry.dir == 1) {
                dif *= -1;
            }
            pos += dif;
            if (pos < 0 || pos >= this.formation.size()) {
                return null;
            }
            FormationEntry connected = this.formation.get(pos);
            if (connected != null) {
                return connected.train;
            }
        }
        return null;
    }

    public Formation getFormation() {
        return this.formation;
    }

    public void setFormation(Formation par1) {
        this.formation = par1;
    }

    private byte[] getByteArray() {
        byte[] ba = Base64.getDecoder().decode(this.entityData.get(DATA_BYTE_ARRAY));
        return ba.length < 16 ? new byte[16] : ba;
    }

    private byte clampTrainStateData(int id, byte data) {
        TrainStateType trainStateType = TrainState.getStateType(id);
        if (trainStateType == TrainStateType.State_Notch) {
            TrainConfig cfg = this.getConfig();
            return NGTMath.clamp(data, (byte) -(cfg.deccelerations.length - 1), (byte) cfg.maxSpeed.length);
        } else {
            return NGTMath.clamp(data, trainStateType.min, trainStateType.max);
        }
    }

    private byte getByteFromDataWatcher(int par1) {
        byte[] ba = this.getByteArray();
        return this.clampTrainStateData(par1, ba[par1]);
    }

    private byte setByteToDataWatcher(int par1, byte par2) {
        byte data = this.clampTrainStateData(par1, par2);
        byte[] ba = this.getByteArray();
        ba[par1] = data;
        this.entityData.set(DATA_BYTE_ARRAY, Base64.getEncoder().encodeToString(ba));
        return data;
    }

    public byte getCabDirection() {
        return this.entityData.get(DATA_CAB_DIR);
    }

    /**
     * 0:前, 1:後
     */
    public int getTrainDirection() {
        return this.getByteFromDataWatcher(TrainStateType.State_TrainDir.id);
    }

    public void setTrainDirection(int par1) {
        if (this.formation == null) {
            this.setTrainDirection_NoSync((byte) par1);
        } else {
            this.formation.setTrainDirection((byte) par1, this);
        }
    }

    public void setTrainDirection_NoSync(byte par1) {
        byte data = this.setByteToDataWatcher(TrainStateType.State_TrainDir.id, par1);
        int id2 = 1 - data;
        if (id2 < 2 && this.existBogies()) {
            this.getBogie(data).setFront(true);
            this.getBogie(id2).setFront(false);
        }
    }

    public int getNotch() {
        return this.getByteFromDataWatcher(TrainStateType.State_Notch.id);
    }

    /** 本家 rtm:sounds/train/lever.ogg (マスコン/ブレーキハンドルの操作音)。 */
    private static final String SOUND_LEVER = "rtm:sounds/train/lever.ogg";

    /** 本家 EntityTrainBase.atsCount (ATS 確認カウンタ)。ATSAssistMod (別mod) が使用する。 */
    public int atsCount;

    public boolean addNotch(Entity driver, int par2) {
        if (par2 != 0) {
            int i = this.getNotch();
            if (this.setNotch(i + par2)) {
                //本家 EntityTrainBase.addNotch と同じ: ノッチが動いたらレバー音を鳴らす
                com.portofino.realtrainmodunofficial.network.TrainSoundPayload.broadcast(
                        this, SOUND_LEVER, 1.0F, 1.0F);
                //ブレーキ → 力行に入れた瞬間の緩解音 (ノッチ -1 からなら強い音)
                if (i < 0 && par2 > 0 && !this.level().isClientSide()) {
                    this.playBrakeReleaseSound(i == -1);
                }
                //本家: マクロ録画中ならノッチ増分を記録 (/rtm macro start)
                jp.ngt.rtm.entity.npc.macro.MacroRecorder.recNotch(driver, par2);
                return true;
            }
        }
        return false;
    }

    /** 本家 playBrakeReleaseSound: 車両側 JSON の sound_BrakeRelease / sound_BrakeRelease2。 */
    protected void playBrakeReleaseSound(boolean isStrong) {
        com.portofino.realtrainmodunofficial.vehicle.VehicleDefinition def =
                com.portofino.realtrainmodunofficial.vehicle.VehicleRegistry.getById(this.getModelName());
        if (def == null) {
            return;
        }
        String sound = isStrong ? def.getSoundBrakeRelease() : def.getSoundBrakeRelease2();
        if (sound == null || sound.isBlank()) {
            return;
        }
        com.portofino.realtrainmodunofficial.network.TrainSoundPayload.broadcast(this, sound, 1.0F, 1.0F);
    }

    public boolean setNotch(int par1) {
        if (this.isControlCar()) {
            byte notch = this.clampTrainStateData(TrainStateType.State_Notch.id, (byte) par1);
            int prevNotch = this.getNotch();
            if (prevNotch != notch) {
                this.setByteToDataWatcher(TrainStateType.State_Notch.id, notch);
                return true;
            }
        }
        return false;
    }

    public void setEBNotch() {
        int prevNotch = this.getNotch();
        int ebNotch = -(this.getConfig().deccelerations.length - 1);
        if (prevNotch != ebNotch) {
            this.setByteToDataWatcher(TrainStateType.State_Notch.id, (byte) ebNotch);
        }
    }

    public int getSignal() {
        return this.getByteFromDataWatcher(TrainStateType.State_Signal.id);
    }

    public void setSignal(int par1) {
        int signal = this.getSignal();
        if (par1 > 0 && signal != -1) {
            this.setByteToDataWatcher(TrainStateType.State_Signal.id, (byte) par1);
        }
    }

    /**
     * 0:direction 1:notch 2:signal 4:door 5:light 6:pantograph 7:chunk_loader
     * 8:destination 9:announcement 10:direction 11:interior_light
     */
    public byte getTrainStateData(int id) {
        return this.getByteFromDataWatcher(id);
    }

    public TrainState getTrainState(int id) {
        return TrainState.getState(id, this.getTrainStateData(id));
    }

    public void setTrainStateData(int id, byte data) {
        if (this.formation != null) {
            this.formation.setTrainStateData(id, data, this);
        }
    }

    public void setTrainStateData_NoSync(int id, byte data) {
        this.setByteToDataWatcher(id, data);
    }

    // ---- 本家スクリプト互換 API ----
    //
    // RTM 標準のレンダースクリプトが entity に対して直接呼ぶメソッド群。
    // 1 つでも欠けると Nashorn が TypeError を投げ、その車両の描画が丸ごと止まる
    // (Render223.js は 1 行目で getVehicleState を呼ぶため、223 系の車体が透明になっていた)。

    /**
     * 本家 EntityVehicleBase.getVehicleState。RTMU の状態は id で持っているので id に落として読む。
     */
    public byte getVehicleState(TrainState.TrainStateType type) {
        return type == null ? 0 : this.getTrainStateData(type.id);
    }

    /**
     * 本家 EntityVehicleBase.setVehicleState。
     */
    public void setVehicleState(TrainState.TrainStateType type, byte data) {
        if (type != null) {
            this.setTrainStateData(type.id, data);
        }
    }

    /**
     * 本家 EntityVehicleBase.syncVehicleState: クライアントの操作をサーバーへ送る。
     * <p>
     * 本家はモデル内の運転台スイッチをクリックしたときにスクリプトから呼ばれる。RTMU は
     * 運転操作を専用 GUI ({@code TrainControlScreen}) で行うため、ここではサーバー側でのみ
     * 状態を書き換える (クライアントから勝手に編成の状態を変えないため)。
     * <b>スクリプトが呼んでも落ちないこと</b>が目的。
     */
    public void syncVehicleState(TrainState.TrainStateType type, byte data) {
        if (type != null && !this.level().isClientSide) {
            this.setTrainStateData(type.id, data);
        }
    }

    /**
     * 本家 EntityTrainBase.syncNotch: ノッチを相対値で動かす。
     * syncVehicleState と同じ理由でサーバー側でのみ効かせる。
     */
    public void syncNotch(int notchInc) {
        if (notchInc != 0 && !this.level().isClientSide) {
            this.addNotch(null, notchInc);
        }
    }

    /**
     * 本家 EntityTrainBase.getCouplerYaw: 連結器が隣の車両を向く角度 (度)。
     * 連結相手がいなければ 0。
     */
    public float getCouplerYaw(int dir) {
        EntityTrainBase conTrain = this.getConnectedTrain(dir);
        if (conTrain == null) {
            return 0.0F;
        }
        float dif = NGTMath.getAngleD(this.getZ(), this.getX(), conTrain.getZ(), conTrain.getX());
        float angle = NGTMath.wrapAngle(dif - (this.getYRot() + (dir == 0 ? 0.0F : 180.0F)));
        return angle + (Math.abs(angle) > 90.0F ? 180.0F : 0.0F);
    }

    /**
     * サーバー同期後にクライアントで speed を反映
     */
    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_SPEED.equals(key) && this.level().isClientSide) {
            this.trainSpeed = this.entityData.get(DATA_SPEED);
        }
        if (DATA_ROLL.equals(key) && this.level().isClientSide) {
            this.vehicleRoll = this.entityData.get(DATA_ROLL);
        }
        if (DATA_YAW.equals(key) && this.level().isClientSide) {
            this.vehicleYaw = this.entityData.get(DATA_YAW);
        }
        if (DATA_PITCH.equals(key) && this.level().isClientSide) {
            this.vehiclePitch = this.entityData.get(DATA_PITCH);
        }
        if (DATA_MODEL_NAME.equals(key)) {
            this.configCache = null;
        }
        if (DATA_SEATS.equals(key) && this.level().isClientSide) {
            this.readSeats(this.entityData.get(DATA_SEATS));
        }
        if (DATA_FORMATION.equals(key) && this.level().isClientSide) {
            this.applyClientFormationData(this.entityData.get(DATA_FORMATION));
        }
    }
}
