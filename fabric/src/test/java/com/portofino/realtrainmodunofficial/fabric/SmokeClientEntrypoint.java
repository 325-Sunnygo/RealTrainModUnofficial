package com.portofino.realtrainmodunofficial.fabric;

/** Developer-only entry point; test sources/resources never enter the release jar. */
public final class SmokeClientEntrypoint implements net.fabricmc.api.ClientModInitializer {
    @Override public void onInitializeClient() {
        // (開発用スモークフック。現状登録対象なし)
    }
}
