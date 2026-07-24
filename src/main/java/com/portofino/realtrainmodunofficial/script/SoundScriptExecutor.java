package com.portofino.realtrainmodunofficial.script;

import com.portofino.realtrainmodunofficial.client.sound.LegacyScriptSoundManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;

/**
 * サウンドスクリプト (sound_*.js) に渡される {@code su} オブジェクト。
 * 本家 {@code jp.ngt.rtm.sound.SoundUpdaterVehicle / SoundUpdaterTrain} 相当。
 *
 * <p>本家のサウンドスクリプトは {@code onUpdate(su)} 一本で、su に対して使うのは
 * 以下だけ (本家同梱スクリプト全数を調査した結果):
 * <pre>
 *   su.playSound(domain, name, vol, pitch)   su.stopSound(domain, name)
 *   su.getSpeed()   su.getNotch()   su.inTunnel()
 *   su.isComplessorActive()   su.complessorCount()   su.getEntity()
 * </pre>
 *
 * <p>従来はこの su に {@code LegacyScriptExecutor} を渡していたが、あれは旧
 * {@code TrainEntity} 専用で、<b>列車アイテムが実際に出す本家系エンティティ
 * ({@link jp.ngt.rtm.entity.train.EntityTrainBase}) では null になっていた</b>。
 * その結果スクリプトは {@code su = 列車エンティティ} で呼ばれ、{@code su.playSound} が
 * 存在せず例外 → スクリプトごと無効化 → 走行音が一切鳴らない状態だった。
 *
 * <p><b>単位に注意</b>: エンティティの {@code getSpeed()} は「ブロック/tick」だが、
 * スクリプトが見る {@code su.getSpeed()} は <b>km/h</b> (本家も {@code * 72.0F} している)。
 * ここを合わせないと、スクリプトから見た速度が常に 0〜0.3 程度になり、
 * 走行音の閾値 (223 系なら 8/12/20 km/h) に一生届かず無音になる。
 */
public final class SoundScriptExecutor {

    /** ブロック/tick → km/h (1 block = 1m, 20tick = 1s → 3.6 * 20 = 72)。本家と同じ係数。 */
    private static final float SPEED_TO_KMH = 72.0F;

    private final Entity train;

    public SoundScriptExecutor(Entity train) {
        this.train = train;
    }

    public Entity getEntity() {
        return this.train;
    }

    /**
     * スクリプトが見る速度 (km/h)。<b>常に絶対値 (0 以上)</b>。
     * <p>
     * 列車の内部速度 {@code getSpeed()} は進行方向 (リバーサ) で符号が付き、後進時は<b>負</b>になる。
     * 一方サウンドスクリプトは {@code if (speed < 0.1) 停車} / {@code if (speed < 14) 低速}
     * のように<b>非負の閾値</b>としか比較しない。符号付きのまま渡すと、後ろ向きに走らせた瞬間
     * speed が負 → 「speed < 0.1 = 停車」と誤判定され、走行音が止まって停車音に切り替わっていた。
     * 本家 SoundUpdater も絶対速度を渡すため、ここで abs して合わせる (JSON 走行音経路は
     * すでに {@code Math.abs(speedOf(...))} 済み)。
     */
    public float getSpeed() {
        return Math.abs(this.rawSpeed()) * SPEED_TO_KMH;
    }

    /** エンティティ内部の速度 (ブロック/tick)。 */
    private float rawSpeed() {
        if (this.train instanceof jp.ngt.rtm.entity.train.EntityTrainBase t) {
            return t.getSpeed();
        }
        if (this.train instanceof com.portofino.realtrainmodunofficial.entity.TrainEntity t) {
            return t.getSpeed();
        }
        return 0.0F;
    }

    /**
     * ノッチ。本家は編成 (Formation) のノッチを見る (先頭車のマスコンが編成全体に効くため)。
     */
    public int getNotch() {
        if (this.train instanceof jp.ngt.rtm.entity.train.EntityTrainBase t) {
            jp.ngt.rtm.entity.train.util.Formation formation = t.getFormation();
            return formation != null ? formation.getNotch() : t.getNotch();
        }
        if (this.train instanceof com.portofino.realtrainmodunofficial.entity.TrainEntity t) {
            return t.getNotch();
        }
        return 0;
    }

    /** 車両の状態値 (ドア/パンタ等)。本家 SoundUpdaterTrain.getState(id) 相当。 */
    public byte getState(int id) {
        if (this.train instanceof jp.ngt.rtm.entity.train.EntityTrainBase t) {
            return (byte) t.getTrainStateData(id);
        }
        return 0;
    }

    /**
     * 本家 SoundUpdaterVehicle.getData(id): dataMap の "SU"+id (double)。
     * スクリプトが tick をまたいで状態を覚えるのに使う (sound_jnr185_mi.js 等)。
     */
    public Object getData(int id) {
        if (this.train instanceof jp.ngt.rtm.entity.train.EntityTrainBase t) {
            return t.getResourceState().getDataMap().getDouble("SU" + id);
        }
        if (this.train instanceof com.portofino.realtrainmodunofficial.entity.TrainEntity t) {
            return t.getResourceState().getDataMap().getDouble("SU" + id);
        }
        return 0.0D;
    }

    /** 本家 SoundUpdaterVehicle.setData(id, value): dataMap の "SU"+id へ double を書く。 */
    public void setData(int id, Object value) {
        double v = value instanceof Number n ? n.doubleValue() : 0.0D;
        if (this.train instanceof jp.ngt.rtm.entity.train.EntityTrainBase t) {
            t.getResourceState().getDataMap().setDouble("SU" + id, v, 0);
        } else if (this.train instanceof com.portofino.realtrainmodunofficial.entity.TrainEntity t) {
            t.getResourceState().getDataMap().setDouble("SU" + id, v, 0);
        }
    }

    /**
     * トンネル内か。本家は車体の四隅 (x±1, z±1) がいずれも空を見上げられないときに true
     * (橋の下や木の下で誤爆しないよう 4 点見る)。223 系はこれで走行音を切り替える。
     */
    public boolean inTunnel() {
        if (this.train == null) {
            return false;
        }
        int x = Mth_floor(this.train.getX());
        int y = Mth_floor(this.train.getY());
        int z = Mth_floor(this.train.getZ());
        return !canSeeSky(x + 1, y, z + 1)
            && !canSeeSky(x - 1, y, z + 1)
            && !canSeeSky(x + 1, y, z - 1)
            && !canSeeSky(x - 1, y, z - 1);
    }

    private boolean canSeeSky(int x, int y, int z) {
        return this.train.level().canSeeSky(new BlockPos(x, y, z));
    }

    private static int Mth_floor(double value) {
        return net.minecraft.util.Mth.floor(value);
    }

    /** 圧縮機 (コンプレッサー) が回っているか。本家はエンティティ側が状態を持つ。 */
    public boolean isComplessorActive() {
        if (this.train instanceof jp.ngt.rtm.entity.train.EntityTrainBase t) {
            return t.complessorActive;
        }
        if (this.train instanceof com.portofino.realtrainmodunofficial.entity.TrainEntity t) {
            //旧エンティティは圧縮機の状態を持たないので、停車中に周期で回す近似
            return Math.abs(t.getSpeed()) <= 0.03F && t.getNotch() <= 0
                && Math.floorMod(t.tickCount, 240) < 55;
        }
        return false;
    }

    /** 圧縮機の進行カウント。本家は brakeAirCount - MIN_AIR_COUNT。 */
    public int complessorCount() {
        if (this.train instanceof jp.ngt.rtm.entity.train.EntityTrainBase t) {
            return t.brakeAirCount - jp.ngt.rtm.entity.train.EntityTrainBase.MIN_AIR_COUNT;
        }
        if (this.train != null && this.isComplessorActive()) {
            return Math.floorMod(this.train.tickCount, 240);
        }
        return 0;
    }

    //綴り違い (compressor) でも通るように
    public boolean isCompressorActive() {
        return this.isComplessorActive();
    }

    public int compressorCount() {
        return this.complessorCount();
    }

    public void playSound(String domain, String name, double volume, double pitch) {
        this.playSound(domain, name, volume, pitch, true);
    }

    public void playSound(String domain, String name, double volume, double pitch, boolean looping) {
        if (this.train == null || !this.train.level().isClientSide()) {
            return;
        }
        LegacyScriptSoundManager.play(this.train, domain, name, (float) volume, (float) pitch, looping);
    }

    public void stopSound(String domain, String name) {
        if (this.train == null || !this.train.level().isClientSide()) {
            return;
        }
        LegacyScriptSoundManager.stop(this.train, domain, name);
    }

    public void stopAllSounds() {
        if (this.train == null) {
            return;
        }
        LegacyScriptSoundManager.stopAll(this.train);
    }

    /** 本家 getMaxSpeed: 最高速度 (km/h)。走行音の段階判定に使う。 */
    public float getMaxSpeed() {
        if (this.train instanceof jp.ngt.rtm.entity.train.EntityTrainBase t) {
            jp.ngt.rtm.modelpack.cfg.TrainConfig cfg = t.getConfig();
            if (cfg != null && cfg.maxSpeed != null && cfg.maxSpeed.length > 0) {
                return cfg.maxSpeed[cfg.maxSpeed.length - 1] * SPEED_TO_KMH;
            }
        }
        return 0.0F;
    }

    /** 本家 changePitch: 最高速域では速度でピッチを変える。 */
    public boolean changePitch() {
        float max = this.getMaxSpeed();
        return max > 0.0F && this.getSpeed() >= max;
    }

    /** 本家 onModelChanged: モデルが変わったら鳴っている音を全部止める。 */
    public void onModelChanged() {
        this.stopAllSounds();
    }

}
