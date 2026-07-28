package jp.ngt.rtm.entity;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import jp.ngt.rtm.entity.npc.EntityMotorman;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

/**
 * 生物系エンティティ (運転士 NPC) の属性登録。
 */
@EventBusSubscriber(modid = RealTrainModUnofficial.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class RTMEntityAttributes {

    private RTMEntityAttributes() {
    }

    @SubscribeEvent
    public static void onAttributeCreation(EntityAttributeCreationEvent event) {
        event.put(RTMEntities.MOTORMAN.get(), EntityMotorman.createAttributes().build());
    }
}
