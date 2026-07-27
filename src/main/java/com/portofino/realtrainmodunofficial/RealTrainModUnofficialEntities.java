package com.portofino.realtrainmodunofficial;

import com.portofino.realtrainmodunofficial.entity.TrainBogieEntity;
import com.portofino.realtrainmodunofficial.entity.TrainEntity;
import com.portofino.realtrainmodunofficial.entity.TrainSeatEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class RealTrainModUnofficialEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, RealTrainModUnofficial.MODID);

    /** エディタの選択範囲 (neo mcte)。不可視・当たり判定なし。 */
    public static final DeferredHolder<EntityType<?>, EntityType<com.portofino.realtrainmodunofficial.entity.EditorEntity>> EDITOR =
        ENTITIES.register("editor",
            () -> EntityType.Builder.<com.portofino.realtrainmodunofficial.entity.EditorEntity>of(
                    com.portofino.realtrainmodunofficial.entity.EditorEntity::new, MobCategory.MISC)
                .sized(0.1F, 0.1F)
                .noSummon()
                .fireImmune()
                .clientTrackingRange(32)
                .updateInterval(1)
                .build("editor"));

    public static final DeferredHolder<EntityType<?>, EntityType<TrainEntity>> TRAIN =
        ENTITIES.register("train",
            () -> EntityType.Builder.<TrainEntity>of(TrainEntity::new, MobCategory.MISC)
                .sized(2.0F, 2.0F)
                .fireImmune()
                .clientTrackingRange(10)
                .build("train"));

    public static final DeferredHolder<EntityType<?>, EntityType<TrainBogieEntity>> TRAIN_BOGIE =
        ENTITIES.register("train_bogie",
            () -> EntityType.Builder.<TrainBogieEntity>of(TrainBogieEntity::new, MobCategory.MISC)
                .sized(1.4F, 1.6F)
                .fireImmune()
                .clientTrackingRange(10)
                .updateInterval(1)
                .build("train_bogie"));

    public static final DeferredHolder<EntityType<?>, EntityType<TrainSeatEntity>> TRAIN_SEAT =
        ENTITIES.register("train_seat",
            () -> EntityType.Builder.<TrainSeatEntity>of(TrainSeatEntity::new, MobCategory.MISC)
                .sized(0.9F, 0.25F)
                .fireImmune()
                .clientTrackingRange(10)
                .updateInterval(1)
                .build("train_seat"));

    private RealTrainModUnofficialEntities() {
    }
}
