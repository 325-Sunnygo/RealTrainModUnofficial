package jp.ngt.rtm.entity;

import jp.ngt.rtm.item.ItemAmmunition.BulletType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * 弾。本家 {@code jp.ngt.rtm.entity.EntityBullet} の移植。
 *
 * <p>本家は {@code EntityArrow} を継承しているが、{@code onUpdate} を丸ごと差し替えて
 * 着地判定・寿命・回転まで自前で持っているので、1.21 では素の {@link Projectile} に載せている
 * (矢の当たり判定や回収処理は本家でも一切使っていない)。
 *
 * <p>参考 : 初速 拳銃:340m/s, 徹甲弾:1800m/s ･･･ここでの処理とは関係ない
 */
public class EntityBullet extends Projectile {
    private static final EntityDataAccessor<Byte> BREAKABLE =
        SynchedEntityData.defineId(EntityBullet.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> AMMO_TYPE =
        SynchedEntityData.defineId(EntityBullet.class, EntityDataSerializers.BYTE);

    private static final int LIFE_ON_GROUND = 400;
    private static final int LIFE_IN_AIR = 1200;

    private BulletType type;

    private int tileX = -1;
    private int tileY = -1;
    private int tileZ = -1;
    private Block landingBlock;
    private boolean inGround;
    private int ticksInGround;
    private int ticksInAir;

    public EntityBullet(EntityType<? extends EntityBullet> entityType, Level level) {
        super(entityType, level);
    }

    public EntityBullet(Level level) {
        this(RTMEntities.BULLET.get(), level);
    }

    /** 本家 ItemGun 用のコンストラクタ。 */
    public EntityBullet(Level level, LivingEntity shooter, float speed, BulletType type) {
        this(level);
        this.setOwner(shooter);
        this.setBulletType(type);

        this.moveTo(shooter.getX(), shooter.getY() + shooter.getEyeHeight(), shooter.getZ(),
            shooter.getYRot(), shooter.getXRot());
        float yawRad = (float) Math.toRadians(this.getYRot());
        float pitchRad = (float) Math.toRadians(this.getXRot());
        double mx = -Mth.sin(yawRad) * Mth.cos(pitchRad);
        double mz = Mth.cos(yawRad) * Mth.cos(pitchRad);
        double my = -Mth.sin(pitchRad);
        this.shootBullet(mx, my, mz, speed);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(BREAKABLE, (byte) 0);
        builder.define(AMMO_TYPE, (byte) -1);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        nbt.putShort("xTile", (short) this.tileX);
        nbt.putShort("yTile", (short) this.tileY);
        nbt.putShort("zTile", (short) this.tileZ);
        nbt.putShort("life", (short) this.ticksInGround);
        nbt.putByte("inGround", (byte) (this.inGround ? 1 : 0));
        nbt.putByte("canBreak", (byte) (this.getCanBreakBlock() ? 1 : 0));
        nbt.putByte("bulletType", this.getBulletType().id);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        this.tileX = nbt.getShort("xTile");
        this.tileY = nbt.getShort("yTile");
        this.tileZ = nbt.getShort("zTile");
        this.ticksInGround = nbt.getShort("life");
        this.inGround = nbt.getByte("inGround") == 1;
        this.setCanBreakBlock(nbt.getByte("canBreak") == 1);
        this.setBulletType(BulletType.getBulletType(nbt.getByte("bulletType")));
    }

    /** 本家 EntityArtillery 用: 向きと初速を与えて撃つ。 */
    public void shootFrom(double dx, double dy, double dz, float speed) {
        this.shootBullet(dx, dy, dz, speed);
    }

    /**
     * 本家 {@code shoot}: 向きへ speed 倍の速度を与え、撃った人に反動を返す。
     */
    private void shootBullet(double par1, double par3, double par5, float par7) {
        double f2 = Math.sqrt(par1 * par1 + par3 * par3 + par5 * par5);
        double d0 = par7 / f2;
        par1 *= d0;
        par3 *= d0;
        par5 *= d0;
        this.setDeltaMovement(par1, par3, par5);
        float f3 = (float) Math.sqrt(par1 * par1 + par5 * par5);
        this.setYRot((float) Math.toDegrees(Math.atan2(par1, par5)));
        this.setXRot((float) Math.toDegrees(Math.atan2(par3, f3)));
        this.yRotO = this.getYRot();
        this.xRotO = this.getXRot();
        this.ticksInGround = 0;

        Entity shooter = this.getOwner();
        if (shooter != null) {
            double recoilCoe = 0.01D;
            shooter.setDeltaMovement(shooter.getDeltaMovement()
                .subtract(par1 * recoilCoe, par3 * recoilCoe, par5 * recoilCoe));
            shooter.hurtMarked = true;
        }
    }

    @Override
    public void tick() {
        this.baseTick();

        if (this.level().isClientSide()) {
            this.updateClient();
        }

        if (this.xRotO == 0.0F && this.yRotO == 0.0F) {
            Vec3 m = this.getDeltaMovement();
            float f = (float) Math.sqrt(m.x * m.x + m.z * m.z);
            this.setYRot((float) Math.toDegrees(Math.atan2(m.x, m.z)));
            this.setXRot((float) Math.toDegrees(Math.atan2(m.y, f)));
            this.yRotO = this.getYRot();
            this.xRotO = this.getXRot();
        }

        BlockPos pos = new BlockPos(this.tileX, this.tileY, this.tileZ);
        BlockState state = this.level().getBlockState(pos);
        if (!state.isAir()) {
            var shape = state.getCollisionShape(this.level(), pos);
            if (!shape.isEmpty()
                && shape.bounds().move(pos).contains(this.getX(), this.getY(), this.getZ())) {
                this.inGround = true;
            }
        }

        if (this.inGround) {
            if (state.getBlock() == this.landingBlock) {
                ++this.ticksInGround;
                if (!this.level().isClientSide() && this.ticksInGround >= LIFE_ON_GROUND) {
                    this.discard();
                }
                this.onLanding(this.tileX, this.tileY, this.tileZ);
            } else {
                this.inGround = false;
                this.ticksInGround = 0;
                this.ticksInAir = 0;
            }
            return;
        }

        ++this.ticksInAir;
        if (!this.level().isClientSide() && this.ticksInAir >= LIFE_IN_AIR) {
            this.discard();
        }

        Vec3 motion = this.getDeltaMovement();
        Vec3 vecPos = this.position();
        Vec3 vec3 = vecPos.add(motion);
        //ブロックをすり抜けないように
        BlockHitResult blockHit = this.level().clip(new ClipContext(
            vecPos, vec3, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (blockHit.getType() != HitResult.Type.MISS) {
            vec3 = blockHit.getLocation();
        }

        Entity hitEntity = null;
        List<Entity> list = this.level().getEntities(this,
            this.getBoundingBox().expandTowards(motion).inflate(1.0D));
        if (!list.isEmpty()) {
            double d0 = 0.0D;
            for (Entity entity : list) {
                //撃った人には当たらないように
                if (entity.equals(this.getOwner())) {
                    continue;
                }
                if (entity.canBeCollidedWith()) {
                    AABB aabb = entity.getBoundingBox().inflate(0.5D);
                    var clip = aabb.clip(vecPos, vec3);
                    if (clip.isPresent()) {
                        double d1 = vecPos.distanceTo(clip.get());
                        if (d1 < d0 || d0 == 0.0D) {
                            hitEntity = entity;
                            d0 = d1;
                        }
                    }
                }
            }
        }

        boolean hitBlock = false;
        if (hitEntity != null) {
            this.onHitEntity(new EntityHitResult(hitEntity));
        } else if (blockHit.getType() != HitResult.Type.MISS) {
            hitBlock = true;
            this.tileX = blockHit.getBlockPos().getX();
            this.tileY = blockHit.getBlockPos().getY();
            this.tileZ = blockHit.getBlockPos().getZ();
            this.landingBlock = this.level().getBlockState(blockHit.getBlockPos()).getBlock();
            this.inGround = true;
        }

        if (hitBlock) {
            this.setPos(vec3.x, vec3.y, vec3.z);
        } else {
            this.setPos(this.getX() + motion.x, this.getY() + motion.y, this.getZ() + motion.z);
        }

        float f2 = (float) Math.sqrt(motion.x * motion.x + motion.z * motion.z);
        float targetYaw = (float) Math.toDegrees(Math.atan2(motion.x, motion.z));
        float targetPitch = (float) Math.toDegrees(Math.atan2(motion.y, f2));
        this.yRotO = Mth.wrapDegrees(this.yRotO);
        this.xRotO = Mth.wrapDegrees(this.xRotO);
        this.setYRot(this.yRotO + Mth.wrapDegrees(targetYaw - this.yRotO) * 0.2F);
        this.setXRot(this.xRotO + Mth.wrapDegrees(targetPitch - this.xRotO) * 0.2F);

        double d3 = 0.999D;
        if (this.isInWater()) {
            for (int l = 0; l < 4; ++l) {
                double d4 = 0.25D;
                this.level().addParticle(ParticleTypes.BUBBLE,
                    this.getX() - motion.x * d4, this.getY() - motion.y * d4, this.getZ() - motion.z * d4,
                    motion.x, motion.y, motion.z);
            }
            d3 = 0.9D;
        }

        BulletType bullet = this.getBulletType();
        if (bullet == BulletType.cannon_40cm || bullet == BulletType.cannon_Atomic) {
            this.setDeltaMovement(motion.x * d3, motion.y - 0.025D, motion.z * d3);
        } else if (bullet == BulletType.rocket) {
            //X,Zは加速,Yはゆるく落下
            double len = Math.sqrt(motion.x * motion.x + motion.z * motion.z);
            double accXZ = 0.125D * (1.0D - (len / 10.0D));
            double accY = -0.01D;
            double nx = motion.x + accXZ * (motion.x >= 0.0D ? 1.0D : -1.0D) * (Math.abs(motion.x) / len);
            double nz = motion.z + accXZ * (motion.z >= 0.0D ? 1.0D : -1.0D) * (Math.abs(motion.z) / len);
            this.setDeltaMovement(nx, motion.y + accY, nz);
        } else {
            this.setDeltaMovement(motion.x * d3, motion.y * d3 - 0.001D, motion.z * d3);
        }
    }

    private void updateClient() {
        if (this.getBulletType() != BulletType.rocket) {
            return;
        }
        Vec3 motion = this.getDeltaMovement();
        int count = 15;
        var random = this.level().random;
        for (int j = 0; j < count; ++j) {
            double d0 = this.getX() + (random.nextDouble() * -motion.x);
            double d1 = this.getY() + (random.nextDouble() * -motion.y);
            double d2 = this.getZ() + (random.nextDouble() * -motion.z);
            double dece = 0.5D;
            double vari = 0.01D;
            double vx = -motion.x * ((random.nextDouble() * 0.5D + 0.5D) * dece) + random.nextGaussian() * vari;
            double vy = -motion.y * ((random.nextDouble() * 0.5D + 0.5D) * dece) + random.nextGaussian() * vari;
            double vz = -motion.z * ((random.nextDouble() * 0.5D + 0.5D) * dece) + random.nextGaussian() * vari;
            this.level().addParticle(ParticleTypes.EXPLOSION, d0, d1, d2, vx, vy, vz);
        }
    }

    public BulletType getBulletType() {
        if (this.type == null) {
            byte id = this.entityData.get(AMMO_TYPE);
            if (id < 0) {
                return BulletType.handgun_9mm;
            }
            this.type = BulletType.getBulletType(id);
        }
        return this.type;
    }

    public void setBulletType(BulletType par1) {
        this.type = par1;
        this.entityData.set(AMMO_TYPE, par1.id);
    }

    public void setCanBreakBlock(boolean par1) {
        byte b0 = this.entityData.get(BREAKABLE);
        byte b1 = (byte) (par1 ? (b0 & -2) : (b0 | 1));
        this.entityData.set(BREAKABLE, b1);
    }

    public boolean getCanBreakBlock() {
        return (this.entityData.get(BREAKABLE) & 1) == 0;
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        Entity entity = result.getEntity();
        if (entity.equals(this.getOwner())) {
            return;
        }

        float damage = this.getBulletType().damage;
        entity.hurt(this.damageSources().thrown(this, this.getOwner()), damage);

        if (!this.level().isClientSide()) {
            BulletType bullet = this.getBulletType();
            if (bullet == BulletType.cannon_40cm || bullet == BulletType.rocket) {
                boolean doMobGriefing = this.level().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
                this.level().explode(this, entity.getX(), entity.getY(), entity.getZ(), 12.0F, doMobGriefing,
                    doMobGriefing ? Level.ExplosionInteraction.MOB : Level.ExplosionInteraction.NONE);
            }
            this.discard();
        }
    }

    /** 着弾時のアクション。本家 {@code onLanding}。 */
    protected void onLanding(int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        BlockState state = this.level().getBlockState(pos);
        Block block = state.getBlock();

        boolean doMobGriefing = this.level().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
        BulletType bullet = this.getBulletType();

        if (bullet == BulletType.cannon_40cm || bullet == BulletType.rocket
            || bullet == BulletType.cannon_Atomic) {
            if (!this.level().isClientSide()) {
                float hardness = state.getDestroySpeed(this.level(), pos);
                if (!state.isAir() && doMobGriefing) {
                    if (hardness >= 0.0F && hardness < 500.0F) {
                        this.level().removeBlock(pos, false);
                    }
                    this.level().explode(this, this.getX(), this.getY(), this.getZ(), 12.0F, doMobGriefing,
                        Level.ExplosionInteraction.MOB);
                }
                if (bullet == BulletType.cannon_Atomic) {
                    this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                        SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 10.0F, 1.0F);
                }
                this.discard();
            }
        } else if (this.landingBlock != null && this.getCanBreakBlock()) {
            if (this.isBreakableBlock(bullet, state, pos, doMobGriefing)) {
                if (!this.level().isClientSide()) {
                    this.level().destroyBlock(pos, false);
                    this.discard();
                }
            } else if (this.landingBlock == Blocks.TNT) {
                if (!this.level().isClientSide()) {
                    TntBlock.explode(this.level(), pos);
                    this.level().removeBlock(pos, false);
                    this.discard();
                }
            } else {
                this.level().playSound(null, pos, state.getSoundType().getBreakSound(),
                    SoundSource.BLOCKS, 1.0F, 1.0F);
            }
        }

        this.setCanBreakBlock(false);
    }

    /**
     * 本家 {@code isBreakableBlock}: 弾種ごとに壊せる材質が違う。
     * 1.21 に {@code Material} が無いので、本家の材質一覧と同じ顔ぶれになるよう
     * ブロックの性質 (硬さ・爆破耐性) と代表的なタグで判定する。
     */
    protected boolean isBreakableBlock(BulletType bullet, BlockState state, BlockPos pos, boolean doMobGriefing) {
        if (!doMobGriefing) {
            return false;
        }
        float hardness = state.getDestroySpeed(this.level(), pos);
        if (hardness < 0.0F) {
            return false;
        }
        if (bullet == BulletType.rifle_12_7mm) {
            //本家: 硬さ 5 未満かつ爆破耐性 6 以下
            return hardness < 5.0F && state.getBlock().getExplosionResistance() <= 6.0F;
        }
        boolean fragile = state.is(net.minecraft.tags.BlockTags.LEAVES)
            || state.is(net.minecraft.tags.BlockTags.REPLACEABLE)
            || state.is(net.minecraft.tags.BlockTags.WOOL_CARPETS)
            || state.is(net.minecraft.tags.BlockTags.FLOWERS)
            || state.is(net.minecraft.tags.BlockTags.SAPLINGS)
            || state.is(net.minecraft.tags.BlockTags.IMPERMEABLE)   //ガラス
            || state.is(net.minecraft.tags.BlockTags.CORAL_BLOCKS)
            || state.is(net.minecraft.tags.BlockTags.CANDLES)
            || state.is(Blocks.REDSTONE_LAMP) || state.is(Blocks.CACTUS) || state.is(Blocks.CAKE)
            || state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.VINE);
        if (bullet == BulletType.handgun_9mm) {
            //拳銃弾は木を壊せない
            return fragile && !state.is(net.minecraft.tags.BlockTags.LOGS)
                && !state.is(net.minecraft.tags.BlockTags.PLANKS);
        }
        return fragile
            || state.is(net.minecraft.tags.BlockTags.LOGS)
            || state.is(net.minecraft.tags.BlockTags.PLANKS)
            || state.is(net.minecraft.tags.BlockTags.ICE);
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return super.canHitEntity(entity) && !entity.equals(this.getOwner());
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    protected double getDefaultGravity() {
        //落下は tick 側で本家と同じ式で入れているのでバニラの重力は掛けない
        return 0.0D;
    }

    @Override
    public boolean isInWater() {
        return this.level().getFluidState(this.blockPosition()).is(Fluids.WATER)
            || this.level().getFluidState(this.blockPosition()).is(Fluids.FLOWING_WATER);
    }
}
