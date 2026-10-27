package com.portofino.realtrainmodunofficial.registry;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import com.portofino.realtrainmodunofficial.entity.CarEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class RealTrainModUnofficialEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES
        = DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE, RealTrainModUnofficial.MODID);

    public static final Supplier<EntityType<CarEntity>> CAR = ENTITY_TYPES.register(
        "car",
        () -> EntityType.Builder.of(CarEntity::new, MobCategory.MISC)
            .sized(2.0f, 2.0f)
            .clientTrackingRange(10)
            .updateInterval(1)
            .build("car")
    );

    // === 本家 EntityInstalledObject 系 (レール上に置く設置物。ブロックではなくエンティティ) ===
    // 本家 EntityElectricalWiring の setSize(1.0F, 0.0625F) に合わせる。

    public static final Supplier<EntityType<jp.ngt.rtm.entity.EntityATC>> ATC = ENTITY_TYPES.register(
        "atc",
        () -> EntityType.Builder.of(jp.ngt.rtm.entity.EntityATC::new, MobCategory.MISC)
            .sized(1.0f, 0.0625f)
            .clientTrackingRange(10)
            .updateInterval(1)
            .build("atc")
    );

    public static final Supplier<EntityType<jp.ngt.rtm.entity.EntityTrainDetector>> TRAIN_DETECTOR =
        ENTITY_TYPES.register(
            "train_detector_entity",
            () -> EntityType.Builder.of(jp.ngt.rtm.entity.EntityTrainDetector::new, MobCategory.MISC)
                .sized(1.0f, 0.0625f)
                .clientTrackingRange(10)
                .updateInterval(1)
                .build("train_detector_entity")
        );

    public static final Supplier<EntityType<jp.ngt.rtm.entity.EntityBumpingPost>> BUMPING_POST_ENTITY =
        ENTITY_TYPES.register(
            "bumping_post_entity",
            () -> EntityType.Builder.of(jp.ngt.rtm.entity.EntityBumpingPost::new, MobCategory.MISC)
                .sized(1.0f, 0.0625f)
                .clientTrackingRange(10)
                .updateInterval(1)
                .build("bumping_post_entity")
        );
}
