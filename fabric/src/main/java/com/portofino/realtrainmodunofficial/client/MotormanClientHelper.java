package com.portofino.realtrainmodunofficial.client;

import com.portofino.realtrainmodunofficial.client.screen.MotormanScreen;
import net.minecraft.client.Minecraft;

/**
 * 運転士 (EntityMotorman) のクライアント処理入口。
 * 共通コード (mobInteract) から isClientSide ガード付きで呼ばれる
 * (クライアント専用クラスを共通クラスから直接参照しないための分離)。
 */
public final class MotormanClientHelper {

    private MotormanClientHelper() {
    }

    /** マクロ選択画面を開く。 */
    public static void openMacroScreen(int entityId) {
        Minecraft.getInstance().setScreen(new MotormanScreen(entityId));
    }
}
