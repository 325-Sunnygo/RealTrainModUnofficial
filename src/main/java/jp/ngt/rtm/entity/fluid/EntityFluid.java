package jp.ngt.rtm.entity.fluid;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficialItems;
import jp.ngt.rtm.entity.RTMEntities;
import jp.ngt.rtm.item.ItemBucketLiquid;
import jp.ngt.rtm.item.ItemPaddle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * 溶けた金属 / コークスの粒。本家 {@code jp.ngt.rtm.entity.fluid.EntityFluid} の移植。
 *
 * <p>粒 1 つ 1 つがエンティティで、近い粒どうしで熱をやり取りし、
 * 描画では近い粒へ向かって膨らんで繋がって見える。
 */
public class EntityFluid extends Entity {
    private static final EntityDataAccessor<String> TYPE =
        SynchedEntityData.defineId(EntityFluid.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Float> TEMP =
        SynchedEntityData.defineId(EntityFluid.class, EntityDataSerializers.FLOAT);

    public static final float R = 0.125F;
    public static final float SIZE = R * 2.0F;
    public static final float METABALL_RANGE = SIZE * 4.0F;
    private static final float METABALL_RANGE_SQ = METABALL_RANGE * METABALL_RANGE;

    /** メタボール計算用 */
    public final List<EntityFluid> nearFluids = new ArrayList<>();

    /** クライアント: 色グラデーション用、最下層の fluid との位置差分 */
    public float posDif;

    private int counter;
    private int airCount;

    public FluidVertexHolder fluidVtx;

    public EntityFluid(EntityType<? extends EntityFluid> entityType, Level level) {
        super(entityType, level);
        if (level.isClientSide()) {
            this.fluidVtx = new FluidVertexHolder();
        }
    }

    public EntityFluid(Level level) {
        this(RTMEntities.FLUID.get(), level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(TYPE, "");
        builder.define(TEMP, 0.0F);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return this.isAlive();   //右クリックの対象にする
    }

    @Override
    public boolean isPickable() {
        return this.isAlive();
    }

    @Override
    public boolean isAttackable() {
        return true;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag nbt) {
        this.setFluidType(nbt.getString("type"));
        this.setTemperture(nbt.getFloat("temperture"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag nbt) {
        nbt.putString("type", this.getFluidType().toString());
        nbt.putFloat("temperture", this.getTemperature());
    }

    @Override
    public void tick() {
        super.tick();

        this.xo = this.getX();
        this.yo = this.getY();
        this.zo = this.getZ();

        this.setDeltaMovement(this.getDeltaMovement().add(0.0D, -0.03D, 0.0D));   //重力

        this.calcFluidMovement();
        this.fixPosByBlockCollision();
        this.move(MoverType.SELF, this.getDeltaMovement());

        float f = 0.98F;
        if (this.onGround()) {
            //摩擦処理
            BlockPos underPos = BlockPos.containing(this.getX(), this.getBoundingBox().minY - 1.0D, this.getZ());
            f *= this.level().getBlockState(underPos).getBlock().getFriction();
        }
        Vec3 m = this.getDeltaMovement();
        this.setDeltaMovement(m.x * f, m.y * 0.98D * (this.onGround() ? -0.9D : 1.0D), m.z * f);

        this.updateFluidState();
    }

    protected void updateFluidState() {
        float temp = this.getTemperature();
        if (temp >= 500.0F) {
            ++this.counter;
        }

        if (this.level().isClientSide()) {
            float f0 = 1.0F - this.getNormalizedTemperture();
            if (this.getFluidType() == FluidType.COKE) {
                int rand = 100 + (int) (100 * f0);
                if (temp > 200.0F && this.random.nextInt(rand) == 0) {
                    this.level().addParticle(ParticleTypes.SMOKE,
                        this.getX(), this.getY() + SIZE, this.getZ(), 0.0D, 0.125D, 0.0D);
                }
            } else if (this.getFluidType().type == FluidType.Type.LIQUID) {
                int rand = 250 + (int) (250 * f0);
                if (temp > 300.0F && this.random.nextInt(rand) == 0) {
                    this.level().addParticle(ParticleTypes.EXPLOSION,
                        this.getX(), this.getY() + SIZE, this.getZ(), 0.0D, 0.125D, 0.0D);
                }
            }
            this.fluidVtx.update(this);
        } else {
            if (temp > 0.0F) {
                if (this.level().getFluidState(this.blockPosition()).is(Fluids.WATER)
                    || this.level().getFluidState(this.blockPosition()).is(Fluids.FLOWING_WATER)) {
                    temp -= 20.0F;
                } else if (this.getFluidType() != FluidType.COKE && this.random.nextInt(10) == 0) {
                    temp -= 1.0F;
                }
                if (temp != this.getTemperature()) {
                    this.setTemperture(temp);
                }
            }

            int max = this.getFluidType().life;
            if (max > 0 && this.counter >= max) {
                this.discard();
            }
        }
    }

    private void calcFluidMovement() {
        this.nearFluids.clear();
        this.posDif = 0.0F;

        final double repRange = SIZE * 1.2D;
        final double repulsion = this.getFluidType().viscosity * this.getNormalizedTemperture();   //斥力
        double px = this.getX();
        double py = this.getY();
        double pz = this.getZ();
        double maxDif = 0.0D;
        double accumDif = 0.0D;

        List<Entity> list = this.level().getEntities(this,
            this.getBoundingBox().inflate(METABALL_RANGE));
        for (Entity entity2 : list) {   //反発処理
            if (!entity2.isAlive()) {
                continue;
            }

            if (entity2 instanceof EntityFluid entity) {
                if (entity == this) {
                    continue;
                }

                double difX = px - entity.getX();
                double difY = py - entity.getY();
                double difZ = pz - entity.getZ();
                double distanceSq = this.distanceToSqr(entity);

                if (distanceSq < repRange * repRange) {
                    double distance = Math.sqrt(distanceSq);
                    double d0 = (repRange - distance) * repulsion;
                    px += (difX / distance) * d0;
                    py += (difY / distance) * d0;
                    pz += (difZ / distance) * d0;
                }

                if (this.level().isClientSide()) {
                    if (distanceSq <= METABALL_RANGE_SQ && this.getFluidType().type != FluidType.Type.SOLID) {
                        this.nearFluids.add(entity);
                        if (!this.onGround() && entity.getY() < this.getY()) {
                            double dif = this.getY() - entity.getY();
                            if (dif > maxDif) {
                                maxDif = dif;
                                accumDif = entity.posDif;
                            }
                        }
                    }
                } else {
                    if (distanceSq <= METABALL_RANGE_SQ) {
                        float temp1 = this.getTemperature();
                        float temp2 = entity.getTemperature();
                        if (temp1 > temp2) {
                            float f0 = (temp1 - temp2) * this.getFluidType().thermalConductivity;
                            temp1 -= f0;
                            temp2 += f0;
                        } else if (temp2 > temp1) {
                            float f0 = (temp2 - temp1) * entity.getFluidType().thermalConductivity;
                            temp2 -= f0;
                            temp1 += f0;
                        }
                        this.setTemperture(temp1);
                        entity.setTemperture(temp2);
                    }
                }
                this.onFluidCollide(entity);
            } else {
                this.onEntityCollideFluid(entity2);
            }
        }

        if (this.level().isClientSide()) {
            this.posDif = (float) (accumDif + maxDif);
        }

        Vec3 m = this.getDeltaMovement();
        this.setDeltaMovement(m.x + (px - this.getX()), m.y, m.z + (pz - this.getZ()));
    }

    protected void onFluidCollide(EntityFluid fluid) {
        if (this.level().isClientSide()) {
            return;
        }
        if (this.distanceToSqr(fluid) <= METABALL_RANGE_SQ) {
            if (fluid.getFluidType() == FluidType.IRON_ORE
                && fluid.getTemperature() >= FluidType.PIG_IRON.meltingPoint) {
                int countInc = this.random.nextInt(2) + 2;
                ItemBucketLiquid.setFluid(this.level(), fluid.getX(), fluid.getY(), fluid.getZ(),
                    FluidType.PIG_IRON, countInc, this.getTemperature());
                fluid.setFluidType(FluidType.SLAG);
                fluid.setTemperture(this.getTemperature());
                this.counter += 2000;
            }
        }
    }

    protected void onEntityCollideFluid(Entity entity) {
        if (this.level().isClientSide()) {
            return;
        }
        if (this.distanceToSqr(entity) <= SIZE * SIZE) {
            boolean burnFlag = this.getTemperature() > 300.0F;
            if (entity instanceof ItemEntity itemEntity) {
                burnFlag = !this.addItem(itemEntity);
            }

            if (burnFlag && this.random.nextInt(5) == 0) {
                entity.hurt(this.damageSources().lava(), 2.0F * this.getNormalizedTemperture());
                entity.setRemainingFireTicks(5 * 20);
            }
        }
    }

    private boolean addItem(ItemEntity entity) {
        ItemStack stack = entity.getItem();

        if (stack.getItem() == RealTrainModUnofficialItems.COKE_ITEM.get()) {
            ItemBucketLiquid.setFluid(this.level(), entity.getX(), entity.getY(), entity.getZ(),
                FluidType.COKE, stack.getCount(), 0.0F);
            entity.discard();
            return true;
        } else if (stack.getItem() == Blocks.IRON_ORE.asItem()
            || stack.getItem() == Blocks.DEEPSLATE_IRON_ORE.asItem()) {
            ItemBucketLiquid.setFluid(this.level(), entity.getX(), entity.getY(), entity.getZ(),
                FluidType.IRON_ORE, stack.getCount(), 0.0F);
            entity.discard();
            return true;
        }

        return false;
    }

    /** 本家 {@code fixPosByBlockCollision}: 接地面からはみ出していれば横へずらして落とす。 */
    protected void fixPosByBlockCollision() {
        this.airCount = 0;
        Vec3 m = this.getDeltaMovement();
        double newX = this.getX() + m.x;
        double newY = this.getY() + (this.onGround() ? 0.0D : m.y);
        double newZ = this.getZ() + m.z;
        int bx = Mth.floor(newX);
        int by = Mth.floor(newY);
        int bz = Mth.floor(newZ);
        double addX = 0.0D;
        double addZ = 0.0D;
        for (Direction face : Direction.values()) {
            int x = bx + face.getStepX();
            int y = by + face.getStepY();
            int z = bz + face.getStepZ();
            boolean isAir = this.level().getBlockState(new BlockPos(x, y, z)).isAir();
            this.airCount += isAir ? 1 : 0;
            if (isAir && face == Direction.DOWN) {
                AABB aabb = this.getBoundingBox();
                int minX = Mth.floor(aabb.minX);
                int maxX = Mth.floor(aabb.maxX);
                int minZ = Mth.floor(aabb.minZ);
                int maxZ = Mth.floor(aabb.maxZ);
                double d0 = 0.25D;
                if (minX < bx) {
                    addX += (bx - aabb.minX) * d0;
                } else if (maxX > bx) {
                    addX -= (aabb.maxX - (bx + 1)) * d0;
                }
                if (minZ < bz) {
                    addZ += (bz - aabb.minZ) * d0;
                } else if (maxZ > bz) {
                    addZ -= (aabb.maxZ - (bz + 1)) * d0;
                }
            }
        }
        if (addX != 0.0D || addZ != 0.0D) {
            this.setDeltaMovement(m.x + addX, m.y, m.z + addZ);
        }
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        float temp = this.getTemperature();
        ItemStack held = player.getItemInHand(hand);

        if (held.is(Items.BUCKET) || held.is(RealTrainModUnofficialItems.BUCKET_LIQUID_ITEM.get())) {
            if (temp > this.getFluidType().meltingPoint) {
                return ItemBucketLiquid.pickupFluid(player, this)
                    ? InteractionResult.sidedSuccess(this.level().isClientSide())
                    : InteractionResult.PASS;
            }
        } else if (held.is(RealTrainModUnofficialItems.BELLOWS_ITEM.get())) {
            if (!this.level().isClientSide()) {
                this.setTemperture(temp - 10.0F);
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide());
        } else if (held.is(RealTrainModUnofficialItems.PADDLE_ITEM.get())) {
            if (!this.level().isClientSide()) {
                ItemPaddle.pushPull(player, this, -1.0F);
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide());
        } else if (held.is(Items.FLINT_AND_STEEL)) {
            if (!this.level().isClientSide() && this.getFluidType() == FluidType.COKE) {
                if (temp < 500.0F) {
                    this.setTemperture(500.0F);
                }
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide());
        }
        return InteractionResult.PASS;
    }

    /** 本家 {@code attackEntityFrom}: 金ノコで冷えた塊を切り出す / 柄杓で押す。 */
    @Override
    public boolean hurt(DamageSource source, float strength) {
        if (this.level().isClientSide() || !(source.getEntity() instanceof Player player)) {
            return true;
        }
        Item item = null;
        ItemStack held = player.getMainHandItem();
        if (held.is(RealTrainModUnofficialItems.IRON_HACKSAW_ITEM.get())) {
            if (this.getTemperature() <= 100.0F) {
                if (this.getFluidType() == FluidType.STEEL) {
                    item = RealTrainModUnofficialItems.INGOT_STEEL_ITEM.get();
                } else if (this.getFluidType() == FluidType.PIG_IRON) {
                    item = Items.IRON_INGOT;
                } else if (this.getFluidType() == FluidType.SLAG) {
                    item = Blocks.COBBLESTONE.asItem();
                } else if (this.getFluidType() == FluidType.IRON_ORE) {
                    item = Blocks.IRON_ORE.asItem();
                }
            } else {
                player.displayClientMessage(Component.literal(
                    String.format("Temperture is too hot ! (%5.1f)", this.getTemperature())), false);
                return true;
            }
        } else if (held.is(RealTrainModUnofficialItems.PADDLE_ITEM.get())) {
            ItemPaddle.pushPull(player, this, 1.0F);
            return true;
        }

        if (item == null && this.getFluidType() == FluidType.COKE) {
            item = RealTrainModUnofficialItems.COKE_ITEM.get();
        }

        if (item != null) {
            held.hurtAndBreak(1, player, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
            player.spawnAtLocation(new ItemStack(item, 1));
            this.discard();
        }
        return true;
    }

    public float getNormalizedLife() {
        int max = this.getFluidType().life;
        return max > 0 ? ((float) (max - this.counter) / (float) max) : 1.0F;
    }

    public FluidType getFluidType() {
        String s = this.entityData.get(TYPE);
        if (s == null || s.isEmpty()) {
            return FluidType.STEEL;
        }
        try {
            return FluidType.valueOf(s);
        } catch (IllegalArgumentException e) {
            return FluidType.STEEL;
        }
    }

    private void setFluidType(String type) {
        if (type == null || type.isEmpty()) {
            type = FluidType.STEEL.toString();
        }
        this.entityData.set(TYPE, type);
    }

    public void setFluidType(FluidType type) {
        this.setFluidType(type.toString());
        this.counter = 0;
    }

    public float getTemperature() {
        return this.entityData.get(TEMP);
    }

    public float getNormalizedTemperture() {
        float temp = this.getTemperature();
        if (temp > this.getFluidType().meltingPoint) {
            return 1.0F;
        }
        return temp / this.getFluidType().meltingPoint;
    }

    public void setTemperture(float f) {
        this.entityData.set(TEMP, Math.max(f, 0.0F));
    }

    public int countAir() {
        return this.airCount;
    }

    /** 本家は {@code setSize(SIZE, SIZE)}。判定箱は 1.21 では EntityType 側で決まる。 */
    public static BlockState airState() {
        return Blocks.AIR.defaultBlockState();
    }
}
