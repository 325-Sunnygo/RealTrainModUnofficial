package jp.ngt.rtm.item;

import jp.ngt.rtm.item.ItemGun.GunType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * レーザー銃の弾。本家 {@code jp.ngt.rtm.item.RazerBullet} の移植。
 *
 * <p>本家は TickProcessQueue に積んで「1 tick で全処理」する。RTMU に同じ待ち行列が無いので
 * 撃った側から直接 {@link #process} を呼ぶ (本家も結局 1 tick で終わるので挙動は同じ)。
 */
public class RazerBullet {
    protected static final int RANGE = 4;
    protected static final float SPEED = 2.0F;
    protected static final int MAX_AGE = (int) (256.0F / SPEED);

    protected final LivingEntity shooter;
    protected double posX;
    protected double posY;
    protected double posZ;
    protected double motionX;
    protected double motionY;
    protected double motionZ;
    protected int age;

    public RazerBullet(Player shooter) {
        this.shooter = shooter;
        this.posX = shooter.getX();
        this.posY = shooter.getY() + shooter.getEyeHeight();
        this.posZ = shooter.getZ();
        float yawRad = (float) Math.toRadians(shooter.getYRot());
        float pitchRad = (float) Math.toRadians(shooter.getXRot());
        this.motionX = -Mth.sin(yawRad) * Mth.cos(pitchRad) * SPEED;
        this.motionZ = Mth.cos(yawRad) * Mth.cos(pitchRad) * SPEED;
        this.motionY = -Mth.sin(pitchRad) * SPEED;

        double recoilCoe = 0.01D;
        recoilCoe *= GunType.razer_gun.speed / SPEED;
        this.shooter.setDeltaMovement(this.shooter.getDeltaMovement()
            .subtract(this.motionX * recoilCoe, this.motionY * recoilCoe, this.motionZ * recoilCoe));
        this.shooter.hurtMarked = true;
    }

    /** 本家と同じ「1tickで全処理する方式」。 */
    public boolean process(Level level) {
        for (int i = 0; i < MAX_AGE; ++i) {
            this.posX += this.motionX;
            this.posY += this.motionY;
            this.posZ += this.motionZ;
            this.age++;
            this.deleteBlocks(level);
            this.deleteEntities(level);
            if (this.posY < level.getMinBuildHeight() || this.posY > level.getMaxBuildHeight()) {
                return true;
            }
        }
        return true;
    }

    protected void deleteBlocks(Level level) {
        int blockX = Mth.floor(this.posX);
        int blockY = Mth.floor(this.posY);
        int blockZ = Mth.floor(this.posZ);
        for (int i = -RANGE; i < RANGE; ++i) {
            for (int j = -RANGE; j < RANGE; ++j) {
                for (int k = -RANGE; k < RANGE; ++k) {
                    int len2 = i * i + j * j + k * k;
                    int rng2 = RANGE * RANGE;
                    if (len2 <= rng2) {
                        BlockPos pos = new BlockPos(blockX + i, blockY + j, blockZ + k);
                        BlockState state = level.getBlockState(pos);
                        if (!state.isAir() && !state.is(Blocks.BEDROCK)) {
                            if (len2 >= rng2 - 6) {
                                level.setBlock(pos, Blocks.MAGMA_BLOCK.defaultBlockState(), 3);
                            } else {
                                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                            }
                        }
                    }
                }
            }
        }
    }

    protected void deleteEntities(Level level) {
        List<Entity> list = level.getEntities(this.shooter,
            new AABB(this.posX - RANGE, this.posY - RANGE, this.posZ - RANGE,
                this.posX + RANGE, this.posY + RANGE, this.posZ + RANGE));
        double rng2 = RANGE * RANGE;
        Vec3 center = new Vec3(this.posX, this.posY, this.posZ);
        for (Entity entity : list) {
            if (entity.distanceToSqr(center) <= rng2) {
                entity.hurt(level.damageSources().magic(), 10000.0F);
            }
        }
    }
}
