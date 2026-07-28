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
}
