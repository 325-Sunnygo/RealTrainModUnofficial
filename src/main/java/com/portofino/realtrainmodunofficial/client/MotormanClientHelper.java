package com.portofino.realtrainmodunofficial.client;

import com.portofino.realtrainmodunofficial.client.screen.MotormanScreen;
import net.minecraft.client.Minecraft;

/** 運転士 (EntityMotorman) のクライアント処理入口。 */
public final class MotormanClientHelper {

    private MotormanClientHelper() {
    }

    /** マクロ選択画面を開く。 */
    public static void openMacroScreen(int entityId) {
        Minecraft.getInstance().setScreen(new MotormanScreen(entityId));
    }
}
