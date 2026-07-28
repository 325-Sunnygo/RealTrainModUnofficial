package jp.ngt.rtm.entity;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import jp.ngt.rtm.entity.train.EntityBogie;
import jp.ngt.rtm.entity.train.EntityTrain;
import jp.ngt.rtm.entity.train.EntityTrainBase;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * jp.ngt.rtm エンティティの登録 (Phase 2)。
 * メインクラスから REGISTER.register(modBus) される。
 */
public final class RTMEntities {
    public static final DeferredRegister<EntityType<?>> REGISTER =
            DeferredRegister.create(Registries.ENTITY_TYPE, RealTrainModUnofficial.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<EntityTrain>> TRAIN =
            REGISTER.register("rtm_train", () -> EntityType.Builder.<EntityTrain>of(EntityTrain::new, MobCategory.MISC)
                    .sized(EntityTrainBase.TRAIN_WIDTH, EntityTrainBase.TRAIN_HEIGHT)
                    .clientTrackingRange(10)
                    .updateInterval(3)
                    .build("rtm_train"));

    public static final DeferredHolder<EntityType<?>, EntityType<EntityBogie>> BOGIE =
            REGISTER.register("rtm_bogie", () -> EntityType.Builder.<EntityBogie>of(EntityBogie::new, MobCategory.MISC)
                    .sized(EntityTrainBase.TRAIN_WIDTH, EntityTrainBase.TRAIN_HEIGHT)
                    .clientTrackingRange(10)
                    .updateInterval(3)
                    .build("rtm_bogie"));

    public static final DeferredHolder<EntityType<?>, EntityType<jp.ngt.rtm.entity.train.parts.EntityFloor>> FLOOR =
            REGISTER.register("rtm_floor", () -> EntityType.Builder.<jp.ngt.rtm.entity.train.parts.EntityFloor>of(
                            jp.ngt.rtm.entity.train.parts.EntityFloor::new, MobCategory.MISC)
                    //本家 setSize(1.25F, 0.0625F) — 高さは掴みやすいよう少し確保
                    .sized(1.25F, 0.35F)
                    .clientTrackingRange(8)
                    .updateInterval(3)
                    .build("rtm_floor"));

    //本家 EntityMotorman (運転士)。setSize(0.6F, 1.8F)
    public static final DeferredHolder<EntityType<?>, EntityType<jp.ngt.rtm.entity.npc.EntityMotorman>> MOTORMAN =
            REGISTER.register("rtm_motorman", () -> EntityType.Builder.of(
                            jp.ngt.rtm.entity.npc.EntityMotorman::new, MobCategory.MISC)
                    .sized(0.6F, 1.8F)
                    .clientTrackingRange(10)
                    .build("rtm_motorman"));

    private RTMEntities() {
    }
}
