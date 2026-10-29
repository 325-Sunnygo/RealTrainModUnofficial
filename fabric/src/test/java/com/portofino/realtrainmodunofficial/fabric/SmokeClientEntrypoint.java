package com.portofino.realtrainmodunofficial.fabric;

/** Developer-only entry point; test sources/resources never enter the release jar. */
public final class SmokeClientEntrypoint implements net.fabricmc.api.ClientModInitializer {
    @Override public void onInitializeClient() {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(com.portofino.realtrainmodunofficial.client.render.MirrorClientSmoke.class);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(com.portofino.realtrainmodunofficial.client.render.MarkerPanelClientSmoke.class);
    }
}
