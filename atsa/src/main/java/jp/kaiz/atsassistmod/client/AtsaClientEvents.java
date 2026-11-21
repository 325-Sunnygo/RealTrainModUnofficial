package jp.kaiz.atsassistmod.client;

import jp.kaiz.atsassistmod.ATSAssistCore;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** クライアント登録 (BER など)。 */
@EventBusSubscriber(modid = ATSAssistCore.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class AtsaClientEvents {

    private AtsaClientEvents() {
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        //本家 TileEntityBeamRenderer: バール所持で地上子 / IFTTT に光の柱
        event.registerBlockEntityRenderer(ATSAssistCore.GROUND_UNIT_BE.get(), ctx -> new BeamRenderer<>());
        event.registerBlockEntityRenderer(ATSAssistCore.IFTTT_BE.get(), ctx -> new BeamRenderer<>());
    }
}
