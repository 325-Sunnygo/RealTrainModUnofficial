package net.neoforged.fml.common;

import net.neoforged.api.distmarker.Dist;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * シム: Fabric には注釈スキャンが無いため、エントリポイント側の明示リストで
 * ShimEventBus.register(Class) を呼ぶ (RtmuFabricInit が担当)。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface EventBusSubscriber {
    String modid() default "";

    Dist[] value() default {Dist.CLIENT, Dist.DEDICATED_SERVER};

    Bus bus() default Bus.GAME;

    enum Bus {
        GAME,
        MOD
    }
}
