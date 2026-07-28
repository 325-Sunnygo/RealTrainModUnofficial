package jp.ngt.rtm.entity.npc;

import jp.ngt.rtm.entity.ai.DriveWithMacroGoal;
import jp.ngt.rtm.entity.ai.DrivingWithDiagramGoal;
import jp.ngt.rtm.entity.ai.DrivingWithSignalGoal;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * 本家 jp.ngt.rtm.entity.npc.EntityMotorman の移植 (運転士)。
 * 運転士アイテムで列車を右クリックすると運転台に乗り、自動運転する:
 */
public class EntityMotorman extends PathfinderMob {

    private static final EntityDataAccessor<ItemStack> DIAGRAM =
            SynchedEntityData.defineId(EntityMotorman.class, EntityDataSerializers.ITEM_STACK);
    /** スキン名 ("" = 既定/季節、"santa"/"shishi" = 同梱、その他 = npc_skins/ のファイル名)。 */
    private static final EntityDataAccessor<String> SKIN =
            SynchedEntityData.defineId(EntityMotorman.class, EntityDataSerializers.STRING);

    // ★registerGoals はスーパーコンストラクタから呼ばれるため、ここで生成する
    // (コンストラクタのフィールド初期化より前。final にして ctor で入れると null で登録され NPE)。
    private DriveWithMacroGoal macroGoal;

    public EntityMotorman(EntityType<? extends EntityMotorman> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
    }

    /** 本家 applyEntityAttributes: HP40 / 速度0.45 / 追跡64 / 攻撃1 */
    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 40.0D)
                .add(Attributes.FOLLOW_RANGE, 64.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.45D)
                .add(Attributes.ATTACK_DAMAGE, 1.0D);
    }

    @Override
    protected void registerGoals() {
        // 本家の task 順: 泳ぐ > マクロ > ダイヤ > 信号 > うろつき > プレイヤー注視 > 見回し
        // (このメソッドはスーパーコンストラクタから呼ばれるので macroGoal はここで生成する)
        this.macroGoal = new DriveWithMacroGoal(this);
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(2, this.macroGoal);
        this.goalSelector.addGoal(3, new DrivingWithDiagramGoal(this));
        this.goalSelector.addGoal(4, new DrivingWithSignalGoal(this));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.45D));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DIAGRAM, ItemStack.EMPTY);
        builder.define(SKIN, "");
    }

    // ------------------------------------------------------------ スキン

    /** スキン名 ("" = 既定/季節)。レンダラが参照する。 */
    public String getSkin() {
        return this.entityData.get(SKIN);
    }

    public void setSkin(String skin) {
        this.entityData.set(SKIN, skin == null ? "" : skin);
    }

    // ------------------------------------------------------------ ダイヤ (本と羽根ペン)

    public boolean hasDiagram() {
        ItemStack stack = this.getDiagram();
        return !stack.isEmpty() && stack.is(Items.WRITABLE_BOOK);
    }

    public ItemStack getDiagram() {
        return this.entityData.get(DIAGRAM);
    }

    public void setDiagram(ItemStack stack) {
        this.entityData.set(DIAGRAM, stack == null ? ItemStack.EMPTY : stack);
    }

    /** マクロ運転の設定 (GUI からのペイロードで呼ばれる)。 */
    public void setMacro(String[] args) {
        if (this.macroGoal != null) {
            this.macroGoal.setMacro(args);
        }
    }

    // ------------------------------------------------------------ NBT

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        ItemStack diagram = this.getDiagram();
        if (!diagram.isEmpty()) {
            nbt.put("DiagramRTM", diagram.save(this.registryAccess()));
        }
        nbt.putString("SkinRTM", this.getSkin());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        if (nbt.contains("DiagramRTM")) {
            this.setDiagram(ItemStack.parse(this.registryAccess(), nbt.getCompound("DiagramRTM"))
                    .orElse(ItemStack.EMPTY));
        }
        if (nbt.contains("SkinRTM")) {
            this.setSkin(nbt.getString("SkinRTM"));
        }
    }

    // ------------------------------------------------------------ インタラクト

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        // 本家: 「本と羽根ペン」を渡すとダイヤとしてセット
        if (held.is(Items.WRITABLE_BOOK) && held.has(DataComponents.WRITABLE_BOOK_CONTENT)) {
            if (!this.level().isClientSide) {
                this.setDiagram(held.copyWithCount(1));
            }
            held.shrink(1);
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }
        // 本家: 素手で右クリック → マクロ選択 GUI (クライアント)
        if (hand == InteractionHand.MAIN_HAND && held.isEmpty()) {
            if (this.level().isClientSide) {
                com.portofino.realtrainmodunofficial.client.MotormanClientHelper.openMacroScreen(this.getId());
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }
        return super.mobInteract(player, hand);
    }

    // ------------------------------------------------------------ ダメージ/ドロップ

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // 本家: プレイヤーの攻撃は一撃 (回収しやすく)。
        if (source.getEntity() instanceof Player) {
            amount = 10000.0F;
        }
        return super.hurt(source, amount);
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!this.level().isClientSide) {
            // ダイヤの本は必ず返す
            if (this.hasDiagram()) {
                this.spawnAtLocation(this.getDiagram(), 1.0F);
            }
            // サバイバルのプレイヤーに倒されたらアイテムとして返す (本家 dropEntity)
            if (source.getEntity() instanceof Player player && !player.getAbilities().instabuild) {
                this.spawnAtLocation(new ItemStack(
                        com.portofino.realtrainmodunofficial.RealTrainModUnofficialItems.MOTORMAN_ITEM.get()), 0.5F);
            }
        }
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(com.portofino.realtrainmodunofficial.RealTrainModUnofficialItems.MOTORMAN_ITEM.get());
    }

    // ------------------------------------------------------------ その他

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false; //本家 canDespawn=false
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    // 経験値は落とさない (本家 getExperiencePoints=0。Mob の既定 xpReward も 0)

    /** 本家 isMotorman: 運転士 NPC か。 */
    public boolean isMotorman() {
        return true;
    }

}
