package jp.ngt.rtm.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 本家 {@code jp.ngt.rtm.entity.EntityInstalledObject} の 1.21 移植。
 *
 * <p>レール上に置く設置物 (ATC / 列車検知器 / 車止め) の基底。本家と同じく<b>エンティティ</b>
 * として存在し、ブロックではない (RTMU は以前設置物ブロックで代用していた)。
 *
 * <p>移動しない (本家 {@code moveEntity} は空実装)、殴られるとドロップして消える、
 * サーバーで毎 tick {@code onUpdate(entity, scriptExecuter)} を回す、という本家の挙動を踏襲する。
 */
public abstract class EntityInstalledObject extends Entity {
    private static final EntityDataAccessor<String> DATA_MODEL_NAME =
        SynchedEntityData.defineId(EntityInstalledObject.class, EntityDataSerializers.STRING);

    private final jp.ngt.rtm.modelpack.state.ResourceState state;
    private final jp.ngt.rtm.modelpack.ScriptExecuter executer = new jp.ngt.rtm.modelpack.ScriptExecuter();

    /** 本家 rotationRoll (描画で使う)。 */
    public float rotationRoll;

    // === スクリプトが SRG 名で読むフィールド (設置物ブロック版と同じ面を出す) ===
    public double field_70165_t;
    public double field_70163_u;
    public double field_70161_v;
    public float field_70177_z;
    public jp.ngt.mccompat.WorldCompat field_70170_p;
    public jp.ngt.mccompat.PlayerCompat field_70153_n;

    protected EntityInstalledObject(EntityType<?> type, Level level) {
        super(type, level);
        this.state = new jp.ngt.rtm.modelpack.state.ResourceState(this::getModelName);
        this.noCulling = true;
        // 本家は moveEntity が空 = 重力も移動も無い (レール上に固定)。
        this.setNoGravity(true);
    }

    /** 既定モデル名 (本家 getDefaultName)。 */
    protected abstract String getDefaultName();

    /** 壊したときにドロップするもの (本家 dropItems)。 */
    protected abstract void dropItems();

    public jp.ngt.rtm.modelpack.ScriptExecuter getScriptExecuter() {
        return this.executer;
    }

    public jp.ngt.rtm.modelpack.state.ResourceState getResourceState() {
        return this.state;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_MODEL_NAME, "");
    }

    public String getModelName() {
        return this.entityData.get(DATA_MODEL_NAME);
    }

    public void setModelName(String name) {
        this.entityData.set(DATA_MODEL_NAME, name == null ? "" : name);
    }

    /** スクリプトが読む SRG フィールドを最新値にする。 */
    public void refreshScriptFields() {
        this.field_70165_t = this.getX();
        this.field_70163_u = this.getY();
        this.field_70161_v = this.getZ();
        this.field_70177_z = this.getYRot();
        if (this.field_70170_p == null || this.field_70170_p.getLevel() != this.level()) {
            this.field_70170_p = new jp.ngt.mccompat.WorldCompat(this.level());
        }
        if (this.getFirstPassenger() instanceof Player player) {
            this.field_70153_n = jp.ngt.mccompat.PlayerCompat.of(player);
            this.field_70153_n.refresh();
        } else {
            this.field_70153_n = null;
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            return;
        }
        // 本家 EntityInstalledObject.onUpdate: サーバーで onUpdate(entity, executer)。
        com.portofino.realtrainmodunofficial.installedobject.InstalledObjectDefinition def =
            com.portofino.realtrainmodunofficial.installedobject.InstalledObjectRegistry
                .getById(this.getModelName());
        com.portofino.realtrainmodunofficial.script.InstalledObjectServerScripts.tickEntity(this, def);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        String name = tag.getString("ModelName");
        if (name.isEmpty()) {
            name = this.getDefaultName();
        }
        this.setModelName(name);
        this.rotationRoll = tag.getFloat("RotationRoll");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putString("ModelName", this.getModelName());
        tag.putFloat("RotationRoll", this.rotationRoll);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return !this.isRemoved();
    }

    @Override
    public boolean canBeCollidedWith() {
        return !this.isRemoved();
    }

    @Override
    public void push(Entity entity) {
        // 本家 canBePushed=false / addVelocity 空。押されても動かない。
    }

    @Override
    public void move(MoverType type, Vec3 pos) {
        // 本家 moveEntity 空実装: レール上から動かない。
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.isInvulnerable() || this.isRemoved()) {
            return false;
        }
        if (source.getEntity() instanceof Player player) {
            if (!this.level().isClientSide()) {
                if (!player.getAbilities().instabuild) {
                    this.dropItems();
                }
                this.discard();
            }
            return true;
        }
        return false;
    }
}
