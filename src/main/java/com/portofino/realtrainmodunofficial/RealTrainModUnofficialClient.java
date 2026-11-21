package com.portofino.realtrainmodunofficial;

import com.portofino.realtrainmodunofficial.client.PackRequirementWarnings;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

// このクラスは専用サーバーではロードされません。
@Mod(value = RealTrainModUnofficial.MODID, dist = Dist.CLIENT)
// EventBusSubscriber を使用すると、@SubscribeEvent でアノテーショ…
@EventBusSubscriber(modid = RealTrainModUnofficial.MODID, value = Dist.CLIENT)
public class RealTrainModUnofficialClient {
    public RealTrainModUnofficialClient(ModContainer container) {
        // RTM.cfg のゲーム内編集画面は登録しない (ファイルを直接編集する)。
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        // クライアントのセットアップ・コード
        com.portofino.realtrainmodunofficial.script.TrainScriptSystem.getInstance().initialize();
        com.portofino.realtrainmodunofficial.client.SodiumCompat.report();
        // KaizPatchX 処理系 (ModelPackManager) によるモデルパック読込へ移行済み
        jp.ngt.rtm.modelpack.ModelPackManager.INSTANCE.clearModelSetCache();
        com.portofino.realtrainmodunofficial.vehicle.VehiclePackLoader.reload();
        PackRequirementWarnings.refresh();
        com.portofino.realtrainmodunofficial.client.VehicleVisualSettings.load();
        // オンライン連携 (GitHub アップデート確認)。バックグラウンドで実行。
        com.portofino.realtrainmodunofficial.online.RtmuOnlineServices.init();
        // RTMU 設定 (自動カント/自動高さ) をファイルから読み込む。
    }

    /** ワールド入室時に RTMU 設定をサーバーへ同期 (敷設時・乗客上限でサーバーが参照するため)。 */
    @SubscribeEvent
    static void onLoggingIn(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingIn event) {
        com.portofino.realtrainmodunofficial.client.ServerPackClient.begin();
    }

    /** ワールドを抜けたら看板まわりのキャッシュを捨てる。 */
    @SubscribeEvent
    static void onLoggingOut(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        com.portofino.realtrainmodunofficial.client.ServerPackClient.end();
        com.portofino.realtrainmodunofficial.client.mirror.MirrorRenderer.clear();
        com.portofino.realtrainmodunofficial.client.signboard.FontImage.clearCache();
        com.portofino.realtrainmodunofficial.client.signboard.tt.TimeTableManager.INSTANCE.invalidate();
    }
}
