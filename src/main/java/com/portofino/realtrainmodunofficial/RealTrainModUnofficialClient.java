package com.portofino.realtrainmodunofficial;

import com.portofino.realtrainmodunofficial.client.PackRequirementWarnings;
import com.portofino.realtrainmodunofficial.modelpack.VehicleModelPackManager;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

// このクラスは専用サーバーではロードされません。ここからクライアント側のコードにアクセスしても安全です。
@Mod(value = RealTrainModUnofficial.MODID, dist = Dist.CLIENT)
// EventBusSubscriber を使用すると、@SubscribeEvent でアノテーションされたクラス内のすべての静的メソッドを自動的に登録できます。
@EventBusSubscriber(modid = RealTrainModUnofficial.MODID, value = Dist.CLIENT)
public class RealTrainModUnofficialClient {
    public RealTrainModUnofficialClient(ModContainer container) {
        // NeoForgeがこのMODのコンフィグ画面を作成できるようにします。
        // コンフィグ画面は、Mods画面＞自分のModをクリック＞コンフィグをクリックで表示されます。
        // 設定オプションの翻訳をen_us.jsonファイルに追加することを忘れないでください。
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        // クライアントのセットアップ・コード
        com.portofino.realtrainmodunofficial.script.TrainScriptSystem.getInstance().initialize();
        VehicleModelPackManager.INSTANCE.initialize(Minecraft.getInstance().getResourceManager());
        PackRequirementWarnings.refresh();
        //オンライン連携 (GitHub アップデート確認 + 公式サイトの BAN リスト)。バックグラウンドで実行。
        com.portofino.realtrainmodunofficial.online.RtmuOnlineServices.init();
        //RTMU 設定 (自動カント/自動高さ) をファイルから読み込む。
        RtmuSettings.load();
    }

    /** ワールド入室時に RTMU 設定をサーバーへ同期 (敷設時・乗客上限でサーバーが参照するため)。 */
    @SubscribeEvent
    static void onLoggingIn(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingIn event) {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(
            new com.portofino.realtrainmodunofficial.network.RtmuSettingsPayload(
                RtmuSettings.autoCant, RtmuSettings.autoHeightLevel, RtmuSettings.maxPassengers));
    }

    /**
     * ワールドを抜けたら看板まわりのキャッシュを捨てる。
     * <p>
     * 看板の文字は OS フォントを焼いた GL テクスチャなので、放っておくとワールドを
     * 出入りするたびに溜まっていく。時刻表も破棄して、ユーザーが
     * config/realtrainmodunofficial/timetable/ に置いた tt_*.csv を再入場で拾えるようにする。
     */
    @SubscribeEvent
    static void onLoggingOut(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        com.portofino.realtrainmodunofficial.client.signboard.FontImage.clearCache();
        com.portofino.realtrainmodunofficial.client.signboard.tt.TimeTableManager.INSTANCE.invalidate();
    }
}
