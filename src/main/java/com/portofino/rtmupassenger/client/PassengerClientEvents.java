package com.portofino.rtmupassenger.client;

import com.portofino.rtmupassenger.PassengerMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** クライアント登録 (乗客レンダラ)。 */
@EventBusSubscriber(modid = PassengerMod.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class PassengerClientEvents {

    private PassengerClientEvents() {
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(PassengerMod.PASSENGER.get(), PassengerRenderer::new);
    }
}
