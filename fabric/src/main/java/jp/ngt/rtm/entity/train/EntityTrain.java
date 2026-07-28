package jp.ngt.rtm.entity.train;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * 本家 jp.ngt.rtm.entity.train.EntityTrain 相当の具象クラス。
 */
public class EntityTrain extends EntityTrainBase {

    public EntityTrain(EntityType<?> type, Level level) {
        super(type, level);
    }
}
