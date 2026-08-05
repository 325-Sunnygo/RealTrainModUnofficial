package jp.ngt.rtm.entity.npc;

import com.portofino.realtrainmodunofficial.npc.NpcDefinition;
import com.portofino.realtrainmodunofficial.npc.NpcRegistry;
import jp.ngt.rtm.entity.EntityBullet;
import jp.ngt.rtm.entity.RTMEntities;
import jp.ngt.rtm.item.ItemGun;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.level.Level;

/**
 * NPC。本家 {@code jp.ngt.rtm.entity.npc.EntityNPC} の移植。
 *
 * <p>パック ({@code ModelNPC_*.json}) の {@code role} で振る舞いが決まる:
 * <ul>
 *   <li>mannequin … 何もしない人形。設置者かクリエイティブしか壊せない</li>
 *   <li>passenger / attendant … うろつき、プレイヤーを見る</li>
 *   <li>guard … 銃を持たせると撃つ。素手なら殴る。飼い主 (設置者) を守る</li>
 *   <li>salesperson / buyer … その場でプレイヤーを見る</li>
 * </ul>
 * 手持ち・防具は右クリックの装備画面で入れる。食べ物を持たせると体力が減ったとき食べる (本家 healNPC)。
 *
 * <p>★本家の乗車 AI (EntityAITravelByTrain = 駅から列車に乗って移動する) は未移植。
 */
public class EntityNPC extends TamableAnimal implements RangedAttackMob {
    private static final EntityDataAccessor<String> MODEL_ID =
        SynchedEntityData.defineId(EntityNPC.class, EntityDataSerializers.STRING);
    /** 本家 menu: 商売メニュー (NBT 文字列 {list:[{item,price}...]})。 */
    private static final EntityDataAccessor<String> MENU =
        SynchedEntityData.defineId(EntityNPC.class, EntityDataSerializers.STRING);

    /** 本家 EntityNPC の定数。 */
    public static final float MAX_HEALTH = 40.0F;
    public static final float FOLLOWING_RANGE = 64.0F;
    public static final float SPEED = 0.45F;
    public static final float ATTACK_POWER = 1.0F;

    private Role role = Role.MANNEQUIN;
    private boolean roleDirty = true;
    /** 銃使用の経過 tick (本家 useItemCount)。 */
    private int useItemCount;

    public EntityNPC(EntityType<? extends EntityNPC> type, Level level) {
        super(type, level);
    }

    public EntityNPC(Level level) {
        this(RTMEntities.NPC.get(), level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
            .add(Attributes.MAX_HEALTH, MAX_HEALTH)
            .add(Attributes.FOLLOW_RANGE, FOLLOWING_RANGE)
            .add(Attributes.MOVEMENT_SPEED, SPEED)
            .add(Attributes.ATTACK_DAMAGE, ATTACK_POWER);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(MODEL_ID, "");
        builder.define(MENU, "");
    }

    public String getMenu() {
        return this.entityData.get(MENU);
    }

    public void setMenu(String menu) {
        this.entityData.set(MENU, menu == null ? "" : menu);
    }

    // ───── モデルと役割 ─────

    public String getModelId() {
        return this.entityData.get(MODEL_ID);
    }

    public void setModelId(String id) {
        this.entityData.set(MODEL_ID, id == null ? "" : id);
        this.roleDirty = true;
    }

    public NpcDefinition getDefinition() {
        NpcDefinition def = NpcRegistry.getById(this.getModelId());
        return def != null ? def : NpcRegistry.getDefault();
    }

    public Role getRole() {
        return this.role;
    }

    /** 本家 Role.getRole(config.role) → Role.init(this)。 */
    public void applyRole() {
        NpcDefinition def = this.getDefinition();
        this.role = Role.byName(def == null ? "" : def.getRole());
        this.goalSelector.removeAllGoals(goal -> true);
        this.targetSelector.removeAllGoals(goal -> true);
        this.getNavigation().stop();
        this.setTarget(null);
        this.setNoGravity(false);

        switch (this.role) {
            case PASSENGER -> {
                this.goalSelector.addGoal(1, new FloatGoal(this));
                //本家 RolePassenger: EntityAITravelByTrain(entity, 0.45F)
                this.goalSelector.addGoal(2, new jp.ngt.rtm.entity.ai.EntityAITravelByTrain(this, 1.0D));
                this.goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(this, SPEED));
                this.goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 4.0F));
                this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));
            }
            case ATTENDANT -> {
                this.goalSelector.addGoal(1, new FloatGoal(this));
                this.goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(this, SPEED));
                this.goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 4.0F));
                this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));
            }
            case GUARD -> {
                this.goalSelector.addGoal(1, new FloatGoal(this));
                ItemStack held = this.getMainHandItem();
                if (held.getItem() instanceof ItemGun) {
                    //本家 EntityAIRangedAttackWithItem(entity, SPEED*1.5, 20, 30, 20.0F)
                    this.goalSelector.addGoal(3, new RangedAttackGoal(this, SPEED * 1.5D, 25, 20.0F));
                } else {
                    this.goalSelector.addGoal(2, new LeapAtTargetGoal(this, SPEED));
                    this.goalSelector.addGoal(3, new MeleeAttackGoal(this, SPEED * 1.5D, true));
                }
                this.goalSelector.addGoal(4, new WaterAvoidingRandomStrollGoal(this, SPEED));
                this.goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 4.0F));
                this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));
                this.targetSelector.addGoal(1, new OwnerHurtByTargetGoal(this));
                this.targetSelector.addGoal(2, new OwnerHurtTargetGoal(this));
                this.targetSelector.addGoal(3, new HurtByTargetGoal(this));
                this.targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, Monster.class, true));
            }
            case SALESPERSON, BUYER -> {
                this.goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 4.0F));
                this.goalSelector.addGoal(3, new RandomLookAroundGoal(this));
            }
            case MANNEQUIN, MOTORMAN -> {
                //人形: AI 無し。本家は onLivingUpdate を丸ごと飛ばす (= その場で固まる)
                this.setNoGravity(false);
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.roleDirty) {
            this.roleDirty = false;
            this.applyRole();
        }
        if (!this.level().isClientSide()) {
            this.healNPC();
        }
    }

    /** 本家 healNPC: 体力が減っていたら持ち物の食べ物を食べる。 */
    private void healNPC() {
        if (this.tickCount % 3 != 0 || this.getHealth() >= this.getMaxHealth()) {
            return;
        }
        ItemStack held = this.getMainHandItem();
        FoodProperties food = held.get(net.minecraft.core.component.DataComponents.FOOD);
        if (food != null) {
            this.heal(food.nutrition());
            held.shrink(1);
        }
    }

    /** 人形はうろつかない (本家: MANNEQUIN は onLivingUpdate を飛ばす)。 */
    @Override
    public void aiStep() {
        if (this.role == Role.MANNEQUIN || this.role == Role.MOTORMAN) {
            this.setDeltaMovement(0.0D, Math.min(0.0D, this.getDeltaMovement().y), 0.0D);
        }
        super.aiStep();
    }

    // ───── 戦闘 ─────

    /** 本家 attackEntityFrom: 設置者は一撃、人形は設置者/クリエイティブ以外壊せない。 */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.getEntity() instanceof Player player) {
            if (player.getUUID().equals(this.getOwnerUUID())) {
                amount = 10000.0F;
            } else if (!player.getAbilities().instabuild && this.role == Role.MANNEQUIN) {
                return false;
            }
        }
        return super.hurt(source, amount);
    }

    /** 本家 attackEntityWithRangedAttack: 銃を構えて撃つ。 */
    @Override
    public void performRangedAttack(LivingEntity target, float power) {
        ItemStack held = this.getMainHandItem();
        if (!(held.getItem() instanceof ItemGun gun)) {
            return;
        }
        this.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
        //本家 getBullet: 目標が居れば目標へ向けて撃つ
        EntityBullet bullet = new EntityBullet(this.level(), this, gun.gunType.speed, gun.gunType.bulletType);
        this.level().addFreshEntity(bullet);
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
            com.portofino.realtrainmodunofficial.RealTrainModUnofficialSounds.GUN.get(),
            SoundSource.NEUTRAL, ItemGun.GUN_SOUND_VOLUME, 1.0F);
    }

    // ───── やり取り ─────

    /** スニーク右クリック = モデル選択 / 通常右クリック = 装備画面 (本家 guiIdNPC)。 */
    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (player.isShiftKeyDown()) {
            if (this.level().isClientSide()) {
                com.portofino.realtrainmodunofficial.ClientHooks.openNpcModelScreen(this.getId());
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide());
        }
        //本家: salesperson/buyer は取引 GUI (GuiSalesperson)
        if (this.role == Role.SALESPERSON || this.role == Role.BUYER) {
            if (this.level().isClientSide()) {
                com.portofino.realtrainmodunofficial.ClientHooks.openNpcTradeScreen(this.getId());
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide());
        }
        if (!this.level().isClientSide()) {
            player.openMenu(new net.minecraft.world.SimpleMenuProvider(
                (id, inv, p) -> new com.portofino.realtrainmodunofficial.menu.NpcMenu(id, inv, this),
                this.getName()));
        }
        return InteractionResult.sidedSuccess(this.level().isClientSide());
    }

    /** 装備が変わったら役割の AI を組み直す (本家 onInventoryChanged: 銃の有無で警備の型が変わる)。 */
    public void onInventoryChanged() {
        this.roleDirty = true;
    }

    // ───── 保存・ドロップ ─────

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        nbt.putString("ModelId", this.getModelId());
        nbt.putString("Menu", this.getMenu());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        this.setModelId(nbt.getString("ModelId"));
        this.setMenu(nbt.getString("Menu"));
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean hitByPlayer) {
        super.dropCustomDeathLoot(level, source, hitByPlayer);
        //本家 onDeath: 持ち物を全部落とし、プレイヤーに壊されたら NPC アイテムも落とす
        for (var slot : net.minecraft.world.entity.EquipmentSlot.values()) {
            ItemStack stack = this.getItemBySlot(slot);
            if (!stack.isEmpty()) {
                this.spawnAtLocation(stack);
                this.setItemSlot(slot, ItemStack.EMPTY);
            }
        }
        if (source.getEntity() instanceof Player player && !player.getAbilities().instabuild) {
            ItemStack drop = new ItemStack(
                com.portofino.realtrainmodunofficial.RealTrainModUnofficialItems.NPC_ITEM.get());
            com.portofino.realtrainmodunofficial.item.NpcItem.setModelId(drop, this.getModelId());
            this.spawnAtLocation(drop);
        }
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;   //本家 canDespawn=false
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return false;
    }

    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob other) {
        return null;
    }

    @Override
    protected int getBaseExperienceReward() {
        return 0;   //本家 getExperiencePoints=0
    }

    /** 本家 Role。パックの role 文字列に対応。 */
    public enum Role {
        PASSENGER, ATTENDANT, MANNEQUIN, GUARD, MOTORMAN, SALESPERSON, BUYER;

        public static Role byName(String name) {
            if (name == null) {
                return MANNEQUIN;
            }
            for (Role role : values()) {
                if (role.name().equalsIgnoreCase(name)) {
                    return role;
                }
            }
            return MANNEQUIN;
        }
    }
}
