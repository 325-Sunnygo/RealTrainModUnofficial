package com.portofino.rtmuautodrive;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** クライアント側の登録 (画面の割り当て)。 */
@EventBusSubscriber(modid = AutoDriveMod.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class AutoDriveClient {

    private AutoDriveClient() {
    }

    @SubscribeEvent
    public static void onRegisterScreens(RegisterMenuScreensEvent event) {
        event.register(AutoDriveMod.DISPATCHER_MENU.get(), DispatcherScreen::new);
    }
}
