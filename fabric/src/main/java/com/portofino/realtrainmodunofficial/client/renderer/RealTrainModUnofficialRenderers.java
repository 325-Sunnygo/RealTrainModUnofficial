package com.portofino.realtrainmodunofficial.client.renderer;

import com.portofino.realtrainmodunofficial.registry.RealTrainModUnofficialEntities;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

public class RealTrainModUnofficialRenderers {
    public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(RealTrainModUnofficialEntities.CAR.get(), CarRenderer::new);
    }
}
