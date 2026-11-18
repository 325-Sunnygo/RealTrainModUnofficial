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

/** 本家 jp.ngt.rtm.entity.EntityInstalledObject の 1.21… */
public abstract class EntityInstalledObject extends Entity
        implements jp.ngt.rtm.modelpack.IModelSelector,
                   net.neoforged.neoforge.entity.IEntityWithComplexSpawn {
    private static final EntityDataAccessor<String> DATA_MODEL_NAME =
        SynchedEntityData.defineId(EntityInstalledObject.class, EntityDataSerializers.STRING);

    private final jp.ngt.rtm.modelpack.state.ResourceState state;
    private final jp.ngt.rtm.modelpack.ScriptExecuter executer = new jp.ngt.rtm.modelpack.ScriptExecuter();
    /** 定義の defaultValues を適用済みの定義 ID。 */
    private String defaultsAppliedFor;

    /** 本家 rotationRoll (描画で使う)。 */
    public float rotationRoll;
    private boolean precisePositionSynced;
    private double precisePosX;
    private double precisePosY;
    private double precisePosZ;
    private boolean preciseRotationSynced;
    private float preciseRotationYaw;
    private float preciseRotationPitch;

    // === スクリプトが SRG 名で読むフィールド (設置物ブロック版と同じ面を出す) ===
    public double field_70165_t;
    public double field_70163_u;
    public double field_70161_v;
    public float field_70177_z;
    public jp.ngt.mccompat.WorldCompat field_70170_p;
    public jp.ngt.mccompat.PlayerCompat field_70153_n;

    protected EntityInstalledObject(EntityType<?> type, Level level) {
        super(type, level);
        this.state = new jp.ngt.rtm.modelpack.state.ResourceState(this::getModelName, this::getModelSet);
        this.noCulling = true;
        // 本家は moveEntity が空 = 重力も移動も無い (レール上に固定)。
        this.setNoGravity(true);
    }

    /** 既定モデル名 (本家 getDefaultName)。 */
    protected abstract String getDefaultName();

    /** 壊したときにドロップするもの (本家 dropItems)。 */
    protected abstract void dropItems();

    /** 本家 getSubType (モデル選択画面/パックのサブタイプ)。 */
    public String getSubType() {
        return "";
    }

    /** 本家 getItem: この設置物に対応するアイテム。 */
    protected net.minecraft.world.item.ItemStack getItem() {
        return net.minecraft.world.item.ItemStack.EMPTY;
    }

    /** 本家 getPickedResult: 中クリックでモデル名/DataMap を保持したアイテムを… */
    @Override
    public net.minecraft.world.item.ItemStack getPickedResult(net.minecraft.world.phys.HitResult target) {
        net.minecraft.world.item.ItemStack stack = this.getItem();
        if (!stack.isEmpty()) {
            com.portofino.realtrainmodunofficial.compat.LegacyItemStackBridge.setSelectedModelData(
                    stack, this.getModelId(), this.getResourceState().getDataMap().getArg());
        }
        return stack;
    }

    public javax.script.ScriptEngine getServerScriptEngine() {
        return this.getModelSet().serverSE;
    }

    /** 本家 EntityInstalledObject.getModelSet。 */
    public jp.ngt.rtm.modelpack.modelset.ModelSetBase<?> getModelSet() {
        com.portofino.realtrainmodunofficial.installedobject.InstalledObjectDefinition def =
                com.portofino.realtrainmodunofficial.installedobject.InstalledObjectRegistry.getById(this.getModelId());
        String key = "machine:" + (def == null ? "" : def.getId());
        this.applyDefinitionDefaults(def);
        float[] offset = null;
        float[] bodyPos = null;
        if (def != null) {
            var mo = def.getModelOffset();
            offset = new float[]{(float) mo.x, (float) mo.y, (float) mo.z};
            var bp = def.getScriptBodyPos();
            bodyPos = new float[]{(float) bp.x, (float) bp.y, (float) bp.z};
        }
        jp.ngt.rtm.modelpack.modelset.ModelSetBase<?> set =
                jp.ngt.rtm.modelpack.ModelPackManager.INSTANCE.getScriptOnlyModelSet(
                        key,
                        def == null ? "" : def.getBareName(),
                        "ModelMachine",
                        def == null ? null : def.getServerScriptPath(),
                        null, null, def == null ? null : def.getButtonTexture(),
                        offset, bodyPos);
        if (def != null && set instanceof jp.ngt.rtm.modelpack.modelset.ModelSetScriptOnly scriptOnly) {
            var config = scriptOnly.getConfig();
            config.machineType = def.getSubType();
            config.followRailAngle = def.isFollowRailAngle();
            config.smoothing = def.isSmoothing();
            config.doCulling = def.isDoCulling();
        }
        return set;
    }

    @Override
    public String getModelType() {
        return "ModelMachine";
    }

    /** 本家 EntityInstalledObject.getPos: {entityId, -1, 0… */
    @Override
    public int[] getPos() {
        return new int[]{this.getId(), -1, 0};
    }

    /** 本家 EntityInstalledObject.closeGui: 選択したモデル名を反映する。 */
    @Override
    public boolean closeGui(String modelName, Object state) {
        if (modelName != null && !modelName.isBlank()) {
            this.setModelName(modelName);
        }
        if (state instanceof jp.ngt.rtm.modelpack.state.ResourceState resourceState) {
            this.getResourceState().setArg(resourceState.getDataMap().getArg(), true);
            this.getResourceState().color = resourceState.color;
        }
        return true;
    }

    /** 本家 ModelSetMachine.dataFormatter.initDataMap 相当:… */
    private void applyDefinitionDefaults(
            com.portofino.realtrainmodunofficial.installedobject.InstalledObjectDefinition def) {
        if (def == null || def.getId().equals(this.defaultsAppliedFor)) {
            return;
        }
        this.defaultsAppliedFor = def.getId();
        var dataMap = this.state.getDataMap();
        for (var value : def.getDefaultValues()) {
            if (value == null || value.key() == null || dataMap.contains(value.key())) {
                continue;
            }
            try {
                switch (value.type() == null ? "" : value.type()) {
                    case "Int" -> dataMap.setInt(value.key(), Integer.parseInt(value.value()), 0);
                    case "Double" -> dataMap.setDouble(value.key(), Double.parseDouble(value.value()), 0);
                    case "Boolean" -> dataMap.setBoolean(value.key(), Boolean.parseBoolean(value.value()), 0);
                    case "Hex" -> dataMap.setHex(value.key(), Integer.decode(value.value()), 0);
                    case "Vec" -> {
                        String[] p = value.value().trim().split("\\s+");
                        if (p.length >= 3) {
                            dataMap.setVec(value.key(), new jp.ngt.ngtlib.math.Vec3(
                                    Double.parseDouble(p[0]), Double.parseDouble(p[1]),
                                    Double.parseDouble(p[2])), 0);
                        }
                    }
                    case "List" -> dataMap.setArray(value.key(), value.values().toArray(), 0);
                    default -> dataMap.setString(value.key(), value.value() == null ? "" : value.value(), 0);
                }
            } catch (RuntimeException ignored) {
            }
        }
    }

    public jp.ngt.rtm.modelpack.ScriptExecuter getScriptExecuter() {
        return this.executer;
    }

    public jp.ngt.rtm.modelpack.state.ResourceState getResourceState() {
        return this.state;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        // 本家 EntityInstalledObject.entityInit: 既定モデル名を最初から持…
        builder.define(DATA_MODEL_NAME, this.getDefaultName());
    }

    /** 内部用: 定義のフル ID ("category:pack:name")。 */
    public String getModelId() {
        String id = this.entityData.get(DATA_MODEL_NAME);
        if (id != null && !id.isEmpty() && id.indexOf(':') < 0) {
            // /summon や既定名 ("ATC_01") はフル定義 ID ではないため、登録定義から解決す…
            for (var candidate : com.portofino.realtrainmodunofficial.installedobject.InstalledObjectRegistry.getAll()) {
                if (candidate.getBareName().equals(id) || candidate.getDisplayName().equals(id)) {
                    return candidate.getId();
                }
            }
        }
        return id;
    }

    /** 本家 EntityInstalledObject.getModelName: スクリプトが見る素の… */
    public String getModelName() {
        String id = this.getModelId();
        int index = id.lastIndexOf(':');
        return index >= 0 ? id.substring(index + 1) : id;
    }

    public void setModelName(String name) {
        String value = name == null ? "" : name;
        if (value.isEmpty()) {
            this.entityData.set(DATA_MODEL_NAME, "");
            return;
        }
        var registry = com.portofino.realtrainmodunofficial.installedobject.InstalledObjectRegistry.class;
        var def = com.portofino.realtrainmodunofficial.installedobject.InstalledObjectRegistry.getById(value);
        if (def == null) {
            for (var candidate : com.portofino.realtrainmodunofficial.installedobject.InstalledObjectRegistry.getAll()) {
                if (candidate.getBareName().equals(value)
                        || candidate.getDisplayName().equals(value)) {
                    def = candidate;
                    break;
                }
            }
        }
        this.entityData.set(DATA_MODEL_NAME, def == null ? value : def.getId());
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
        // 本家 EntityInstalledObject.onUpdate: サーバーで onUpdate…
        this.refreshScriptFields();
        this.executer.execScript(this);
    }

    /** 本家 EntityInstalledObject の spawn data (正確な位置・向き・S… */
    @Override
    public void writeSpawnData(net.minecraft.network.RegistryFriendlyByteBuf buffer) {
        CompoundTag tag = new CompoundTag();
        tag.putString("ModelName", this.getModelId());
        tag.put("State", this.getResourceState().writeToNBT());
        tag.putFloat("RotationRoll", this.rotationRoll);
        tag.putDouble("PositionX", this.getX());
        tag.putDouble("PositionY", this.getY());
        tag.putDouble("PositionZ", this.getZ());
        tag.putFloat("RotationYaw", this.getYRot());
        tag.putFloat("RotationPitch", this.getXRot());
        buffer.writeUtf(tag.toString());
    }

    @Override
    public void readSpawnData(net.minecraft.network.RegistryFriendlyByteBuf buffer) {
        try {
            CompoundTag tag = net.minecraft.nbt.TagParser.parseTag(buffer.readUtf());
            this.readAdditionalSaveData(tag);
            this.setPos(tag.getDouble("PositionX"), tag.getDouble("PositionY"), tag.getDouble("PositionZ"));
            this.setRot(tag.getFloat("RotationYaw"), tag.getFloat("RotationPitch"));
        } catch (Exception ignored) {
        }
    }

    public double getPreciseRenderPosX() {
        return this.precisePositionSynced ? this.precisePosX : this.getX();
    }

    public double getPreciseRenderPosY() {
        return this.precisePositionSynced ? this.precisePosY : this.getY();
    }

    public double getPreciseRenderPosZ() {
        return this.precisePositionSynced ? this.precisePosZ : this.getZ();
    }

    public float getPreciseRenderYaw() {
        return this.preciseRotationSynced ? this.preciseRotationYaw : this.getYRot();
    }

    public float getPreciseRenderPitch() {
        return this.preciseRotationSynced ? this.preciseRotationPitch : this.getXRot();
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        String name = tag.getString("ModelName");
        if (name.isEmpty()) {
            name = this.getDefaultName();
        }
        this.setModelName(name);
        this.getResourceState().readFromNBT(tag.getCompound("State"));
        this.rotationRoll = tag.getFloat("RotationRoll");
        this.precisePositionSynced = tag.contains("PositionX") && tag.contains("PositionY") && tag.contains("PositionZ");
        if (this.precisePositionSynced) {
            this.precisePosX = tag.getDouble("PositionX");
            this.precisePosY = tag.getDouble("PositionY");
            this.precisePosZ = tag.getDouble("PositionZ");
        }
        this.preciseRotationSynced = tag.contains("RotationYaw") && tag.contains("RotationPitch");
        if (this.preciseRotationSynced) {
            this.preciseRotationYaw = tag.getFloat("RotationYaw");
            this.preciseRotationPitch = tag.getFloat("RotationPitch");
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putString("ModelName", this.getModelId());
        tag.put("State", this.getResourceState().writeToNBT());
        tag.putFloat("RotationRoll", this.rotationRoll);
        tag.putDouble("PositionX", this.getX());
        tag.putDouble("PositionY", this.getY());
        tag.putDouble("PositionZ", this.getZ());
        tag.putFloat("RotationYaw", this.getYRot());
        tag.putFloat("RotationPitch", this.getXRot());
    }

    /** 本家 EntityElectricalWiring.interactFirst: スニーク + 素… */
    @Override
    public net.minecraft.world.InteractionResult interact(net.minecraft.world.entity.player.Player player,
                                                          net.minecraft.world.InteractionHand hand) {
        if (player != null && player.isShiftKeyDown()
                && player.getMainHandItem().isEmpty() && player.getOffhandItem().isEmpty()) {
            if (this.level().isClientSide()) {
                com.portofino.realtrainmodunofficial.ClientHooks.openEntityModelSelectScreen(this);
            }
            return net.minecraft.world.InteractionResult.sidedSuccess(this.level().isClientSide());
        }
        return net.minecraft.world.InteractionResult.PASS;
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
                // 本家 EntityInstalledObject.attackEntityFrom: 石の破壊音。
                this.level().playSound(null, this.blockPosition(),
                        net.minecraft.sounds.SoundEvents.STONE_BREAK,
                        net.minecraft.sounds.SoundSource.BLOCKS, 0.5F, 0.8F);
                this.discard();
            }
            return true;
        }
        return false;
    }
}
