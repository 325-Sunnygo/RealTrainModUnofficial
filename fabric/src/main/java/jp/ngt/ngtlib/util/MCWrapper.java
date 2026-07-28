package jp.ngt.ngtlib.util;

import jp.ngt.mccompat.EntityCompatUtil;
import net.minecraft.world.entity.Entity;

/**
 * 本家 jp.ngt.ngtlib.util.MCWrapper のスクリプト互換。
 * エンティティ引数は 実 Entity / PlayerCompat のどちらでも受ける。
 */
@SuppressWarnings("unused")
public final class MCWrapper {
    private MCWrapper() {
    }

    public static double getPosX(Object entity) {
        Entity e = EntityCompatUtil.unwrapEntity(entity);
        return e != null ? e.getX() : 0.0D;
    }

    public static double getPosY(Object entity) {
        Entity e = EntityCompatUtil.unwrapEntity(entity);
        return e != null ? e.getY() : 0.0D;
    }

    public static double getPosZ(Object entity) {
        Entity e = EntityCompatUtil.unwrapEntity(entity);
        return e != null ? e.getZ() : 0.0D;
    }

    public static double getYaw(Object entity) {
        Entity e = EntityCompatUtil.unwrapEntity(entity);
        return e != null ? e.getYRot() : 0.0D;
    }

    public static double getPitch(Object entity) {
        Entity e = EntityCompatUtil.unwrapEntity(entity);
        return e != null ? e.getXRot() : 0.0D;
    }

    /** 本家 getEntityId。 */
    public static int getEntityId(Object entity) {
        Entity e = EntityCompatUtil.unwrapEntity(entity);
        return e != null ? e.getId() : -1;
    }

    /** 本家 getEntityById。world は実 Level / WorldCompat のどちらでも受ける。 */
    public static Entity getEntityById(Object world, int id) {
        net.minecraft.world.level.Level level = unwrapLevel(world);
        return level != null ? level.getEntity(id) : null;
    }

    /** 本家 getWorld: エンティティ / ブロックエンティティのワールド。 */
    public static Object getWorld(Object entity) {
        net.minecraft.world.level.Level level = levelOf(entity);
        return level != null ? new jp.ngt.mccompat.WorldCompat(level) : null;
    }

    /** 本家 getRandom。 */
    public static net.minecraft.util.RandomSource getRandom(Object world) {
        net.minecraft.world.level.Level level = unwrapLevel(world);
        return level != null ? level.getRandom() : net.minecraft.util.RandomSource.create();
    }

    /** 本家 getDistanceSq: 2 エンティティ間の距離の 2 乗。 */
    public static double getDistanceSq(Object entity1, Object entity2) {
        Entity a = EntityCompatUtil.unwrapEntity(entity1);
        Entity b = EntityCompatUtil.unwrapEntity(entity2);
        return a != null && b != null ? a.distanceToSqr(b) : 0.0D;
    }

    /** 本家 getEntities: 範囲内の全エンティティ。 */
    public static java.util.List<Entity> getEntities(Object world,
                                                     double x1, double y1, double z1,
                                                     double x2, double y2, double z2) {
        net.minecraft.world.level.Level level = unwrapLevel(world);
        if (level == null) {
            return java.util.List.of();
        }
        return level.getEntities((Entity) null, new net.minecraft.world.phys.AABB(
                Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2),
                Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2)), e -> true);
    }

    /** 本家 setBlockToAir。 */
    public static void setBlockToAir(Object world, double x, double y, double z) {
        net.minecraft.world.level.Level level = unwrapLevel(world);
        if (level == null) {
            return;
        }
        level.removeBlock(net.minecraft.core.BlockPos.containing(x, y, z), false);
    }

    /**
     * 本家 breakBlock: 乗っているプレイヤーの手でブロックを壊す
     * (除雪車/工事車両が線路上の障害物を撤去するのに使う)。
     */
    public static void breakBlock(Object entity, double x, double y, double z) {
        Entity e = EntityCompatUtil.unwrapEntity(entity);
        if (e == null) {
            return;
        }
        Entity rider = e.getVehicle() != null ? e.getVehicle() : e.getFirstPassenger();
        if (!(rider instanceof net.minecraft.world.entity.player.Player player)) {
            return;
        }
        net.minecraft.world.level.Level level = e.level();
        net.minecraft.core.BlockPos pos = net.minecraft.core.BlockPos.containing(x, y, z);
        if (level.getBlockState(pos).isAir()) {
            return;
        }
        level.destroyBlock(pos, true, player);
    }

    /** ブロックエンティティ / エンティティ / WorldCompat から Level を取り出す。 */
    private static net.minecraft.world.level.Level levelOf(Object o) {
        if (o instanceof net.minecraft.world.level.block.entity.BlockEntity be) {
            return be.getLevel();
        }
        Entity e = EntityCompatUtil.unwrapEntity(o);
        if (e != null) {
            return e.level();
        }
        return unwrapLevel(o);
    }

    private static net.minecraft.world.level.Level unwrapLevel(Object world) {
        if (world instanceof net.minecraft.world.level.Level level) {
            return level;
        }
        if (world instanceof jp.ngt.mccompat.WorldCompat compat) {
            return compat.getLevel();
        }
        return null;
    }

    /** 本家 getPosX/Y/Z(TileEntity) 相当。 */
    public static int getPosX(net.minecraft.world.level.block.entity.BlockEntity be) {
        return be == null ? 0 : be.getBlockPos().getX();
    }

    public static int getPosY(net.minecraft.world.level.block.entity.BlockEntity be) {
        return be == null ? 0 : be.getBlockPos().getY();
    }

    public static int getPosZ(net.minecraft.world.level.block.entity.BlockEntity be) {
        return be == null ? 0 : be.getBlockPos().getZ();
    }
}
