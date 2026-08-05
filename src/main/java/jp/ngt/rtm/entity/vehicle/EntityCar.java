package jp.ngt.rtm.entity.vehicle;

import jp.ngt.rtm.entity.RTMEntities;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/** ブロック製の車。本家 {@code EntityCar} 相当 (基底の物理をそのまま使う)。 */
public class EntityCar extends EntityVehicle {
    public EntityCar(EntityType<? extends EntityCar> type, Level level) {
        super(type, level);
    }

    public EntityCar(Level level) {
        this(RTMEntities.NGTO_CAR.get(), level);
    }
}
