package jp.ngt.rtm.entity.train.parts;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficialItems;
import com.portofino.realtrainmodunofficial.cargo.CargoDefinition;
import jp.ngt.rtm.entity.EntityBullet;
import jp.ngt.rtm.entity.RTMEntities;
import jp.ngt.rtm.item.ItemAmmunition;
import jp.ngt.rtm.item.ItemAmmunition.BulletType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 火砲。本家 {@code jp.ngt.rtm.entity.train.parts.EntityArtillery} の移植。
 *
 * <p>右クリックで乗り、乗ると<b>視線で砲を向ける</b> (パックの yaw/pitch の範囲内)。
 * 弾薬アイテムを持って右クリックで装填、パドルで発射。本家 {@code setSize(3.0F, 2.5F)}。
 *
 * <p>★本家の「発射キー」(onFireKeyDown) は移植していない。装填→パドルで撃つ経路のみ。
 */
public class EntityArtillery extends EntityCargoWithModel {
    private static final EntityDataAccessor<Float> BARREL_YAW =
        SynchedEntityData.defineId(EntityArtillery.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> BARREL_PITCH =
        SynchedEntityData.defineId(EntityArtillery.class, EntityDataSerializers.FLOAT);
    /** 装填中の弾種。-1 = 空。本家の hasAmmo() と同じ意味。 */
    private static final EntityDataAccessor<Integer> AMMO_TYPE =
        SynchedEntityData.defineId(EntityArtillery.class, EntityDataSerializers.INT);

    /** 装填数 (本家は負で持つが、ここは素直に正で持つ)。 */
    private int ammoCount;

    public EntityArtillery(EntityType<? extends EntityArtillery> type, Level level) {
        super(type, level);
    }

    public EntityArtillery(Level level) {
        this(RTMEntities.ARTILLERY.get(), level);
    }

    @Override
    protected CargoDefinition.Kind getKind() {
        return CargoDefinition.Kind.FIREARM;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(BARREL_YAW, 0.0F);
        builder.define(BARREL_PITCH, 0.0F);
        builder.define(AMMO_TYPE, -1);
    }

    public float getBarrelYaw() {
        return this.entityData.get(BARREL_YAW);
    }

    public float getBarrelPitch() {
        return this.entityData.get(BARREL_PITCH);
    }

    public int getAmmoType() {
        return this.entityData.get(AMMO_TYPE);
    }

    public int getAmmoCount() {
        return this.ammoCount;
    }

    @Override
    protected void readCargoFromNBT(CompoundTag nbt) {
        super.readCargoFromNBT(nbt);
        this.entityData.set(AMMO_TYPE, nbt.contains("ammoType") ? nbt.getInt("ammoType") : -1);
        this.ammoCount = nbt.getInt("ammoCount");
    }

    @Override
    protected void writeCargoToNBT(CompoundTag nbt) {
        super.writeCargoToNBT(nbt);
        nbt.putInt("ammoType", this.getAmmoType());
        nbt.putInt("ammoCount", this.ammoCount);
    }

    /** 本家: 乗っている人の視線で砲を回す。範囲はパックの yaw/pitch (Max, Min の順)。 */
    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            return;
        }
        Entity rider = this.getFirstPassenger();
        if (rider == null) {
            return;
        }
        CargoDefinition def = this.getDefinition();
        float[] yawRange = def == null ? new float[]{0.0F, 0.0F} : def.getYawRange();
        float[] pitchRange = def == null ? new float[]{60.0F, -5.0F} : def.getPitchRange();

        float yaw = Mth.wrapDegrees(rider.getYRot() - this.getYRot());
        float pitch = -rider.getXRot();
        //本家の配列は [Max, Min]
        this.entityData.set(BARREL_YAW, clampRange(yaw, yawRange));
        this.entityData.set(BARREL_PITCH, clampRange(pitch, pitchRange));
    }

    private static float clampRange(float value, float[] range) {
        if (range == null || range.length < 2) {
            return value;
        }
        float max = Math.max(range[0], range[1]);
        float min = Math.min(range[0], range[1]);
        return Mth.clamp(value, min, max);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        InteractionResult parent = super.interact(player, hand);
        if (parent != InteractionResult.PASS) {
            return parent;
        }
        if (!this.getPassengers().isEmpty()) {
            return InteractionResult.sidedSuccess(this.level().isClientSide());
        }

        ItemStack held = player.getItemInHand(hand);
        CargoDefinition def = this.getDefinition();
        int magazine = def == null ? 1 : def.getMagazineSize();

        //装填: 弾薬アイテム (弾種 * 4 + 1 = 弾) を入れる
        if (held.getItem() instanceof ItemAmmunition) {
            int type = ItemAmmunition.getVariant(held) / 4;
            if (!this.level().isClientSide()) {
                if ((this.getAmmoType() < 0 || this.getAmmoType() == type) && this.ammoCount < magazine) {
                    this.entityData.set(AMMO_TYPE, type);
                    this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                        SoundEvents.GRASS_PLACE, SoundSource.BLOCKS, 1.0F, 0.8F);
                    held.shrink(1);
                    ++this.ammoCount;
                }
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide());
        }

        //発射: パドル
        if (held.is(RealTrainModUnofficialItems.PADDLE_ITEM.get())) {
            if (!this.level().isClientSide() && this.getAmmoType() >= 0 && this.ammoCount > 0) {
                BulletType type = BulletType.getBulletType(this.getAmmoType());
                for (int i = 0; i < this.ammoCount; ++i) {
                    this.fireOnServer(type);
                }
                this.ammoCount = 0;
                this.entityData.set(AMMO_TYPE, -1);
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide());
        }

        if (!this.level().isClientSide()) {
            player.startRiding(this);
        }
        return InteractionResult.sidedSuccess(this.level().isClientSide());
    }

    /**
     * 本家 fireOnServer: 砲口の位置と向きを、パーツの位置差を順に回して出す。
     * {@code 砲身 → X回転 → Y回転 → 車体の向き} の順。
     */
    protected void fireOnServer(BulletType type) {
        CargoDefinition cfg = this.getDefinition();
        if (cfg == null) {
            return;
        }
        Vec3 muzzle = transformPart(cfg, cfg.getMuzzlePos());
        Vec3 bolt = transformPart(cfg, cfg.getPartsPosBarrel());
        Vec3 dir = muzzle.subtract(bolt);
        if (dir.lengthSqr() < 1.0E-6D) {
            dir = new Vec3(0.0D, 0.0D, 1.0D);
        }

        EntityBullet bullet = new EntityBullet(this.level());
        bullet.setOwner(this);
        bullet.setBulletType(type);
        bullet.setPos(muzzle.x, muzzle.y, muzzle.z);
        //本家 EntityArtillery 用のコンストラクタ: ロケットだけ初速 1.25、他は 10.0
        float speed = (type == BulletType.rocket) ? 1.25F : 10.0F;
        bullet.shootFrom(dir.x, dir.y, dir.z, speed);
        this.level().addFreshEntity(bullet);

        this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
            SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 4.0F, 0.6F);
    }

    /** 本家の変換の連鎖をそのまま。 */
    private Vec3 transformPart(CargoDefinition cfg, Vec3 point) {
        Vec3 v = point.subtract(cfg.getPartsPosX());
        v = rotateAroundX(v, -this.getBarrelPitch());
        v = v.add(cfg.getPartsPosX().subtract(cfg.getPartsPosY()));
        v = rotateAroundY(v, this.getBarrelYaw());
        v = v.add(cfg.getPartsPosY().subtract(cfg.getPartsPosN()));
        v = rotateAroundY(v, this.getYRot());
        return v.add(this.getX(), this.getY(), this.getZ());
    }

    private static Vec3 rotateAroundX(Vec3 v, float deg) {
        float rad = (float) Math.toRadians(deg);
        float cos = Mth.cos(rad);
        float sin = Mth.sin(rad);
        return new Vec3(v.x, v.y * cos - v.z * sin, v.z * cos + v.y * sin);
    }

    private static Vec3 rotateAroundY(Vec3 v, float deg) {
        float rad = (float) Math.toRadians(deg);
        float cos = Mth.cos(rad);
        float sin = Mth.sin(rad);
        return new Vec3(v.x * cos + v.z * sin, v.y, v.z * cos - v.x * sin);
    }

    /** 本家 playerPos: 砲手はここに座る。 */
    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction callback) {
        CargoDefinition cfg = this.getDefinition();
        Vec3 seat = cfg == null ? new Vec3(0.0D, 1.5D, -3.5D) : cfg.getPlayerPos();
        Vec3 v = rotateAroundY(seat, this.getYRot());
        callback.accept(passenger, this.getX() + v.x, this.getY() + v.y, this.getZ() + v.z);
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return this.getPassengers().isEmpty();
    }
}
