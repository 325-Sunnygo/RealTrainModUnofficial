package com.portofino.realtrainmodunofficial.fabric;

import net.neoforged.neoforge.common.NeoForge;

/**
 * {@code @EventBusSubscriber} が付いたクラスの一覧。
 *
 * <p>NeoForge はアノテーションをロード時に走査して勝手に登録するが、Fabric にその仕組みは無い。
 * かといって実行時にクラスパスを舐めるのは遅く壊れやすいので、<b>ここに明示的に並べる</b>。
 *
 * <p>クラスを増やしたらここへ 1 行足すこと。足し忘れると「コンパイルは通るのにイベントが
 * 一切来ない」という追いにくい壊れ方をするので、名前は {@link #register} の失敗ログで分かるようにしてある。
 *
 * <p>★クライアント専用のものを共通側で触らないこと。専用サーバーで
 * NoClassDefFoundError になる (NeoForge 版でも同じ踏み方をしている)。
 */
public final class RtmuSubscribers {

    /** 共通 (サーバーでも読む)。 */
    private static final String[] COMMON = {
        "com.portofino.realtrainmodunofficial.TrainCommands",
        "com.portofino.realtrainmodunofficial.convert.ConvertEvents$Game",
        "com.portofino.realtrainmodunofficial.entity.SeatCleanup",
        "com.portofino.realtrainmodunofficial.remote.RemoteRedstoneHandler",
        "jp.ngt.rtm.entity.RTMEntityAttributes",
    };

    /** クライアント専用。 */
    private static final String[] CLIENT = {
        "com.portofino.realtrainmodunofficial.RealTrainModUnofficialClient",
        "com.portofino.realtrainmodunofficial.RealTrainModUnofficialClientModEvents",
        "com.portofino.realtrainmodunofficial.client.ClientRenderProfiler",
        "com.portofino.realtrainmodunofficial.client.EditorKeys",
        "com.portofino.realtrainmodunofficial.client.EditorKeys$Registration",
        "com.portofino.realtrainmodunofficial.client.FreeCameraController",
        "com.portofino.realtrainmodunofficial.client.RailPreviewRenderer",
        "com.portofino.realtrainmodunofficial.client.RideCameraEvents",
        "com.portofino.realtrainmodunofficial.client.RiderViewSmoother",
        "com.portofino.realtrainmodunofficial.client.RtmuPauseMenuButton",
        "com.portofino.realtrainmodunofficial.client.SelectScreenHudHider",
        "com.portofino.realtrainmodunofficial.client.SelectionHud",
        "com.portofino.realtrainmodunofficial.client.TitleScreenWarningOverlay",
        "com.portofino.realtrainmodunofficial.client.TrainControlKeyHandler",
        "com.portofino.realtrainmodunofficial.client.TrainHudOverlay",
        "com.portofino.realtrainmodunofficial.client.camera.CameraClientEvents",
        "com.portofino.realtrainmodunofficial.client.render.RailDrawQueue",
        "com.portofino.realtrainmodunofficial.client.render.SelectionRenderer",
        "com.portofino.realtrainmodunofficial.convert.ConvertEvents$Setup",
        "com.portofino.realtrainmodunofficial.online.OnlineClientHooks",
        "com.portofino.rtmupassenger.client.PassengerClientEvents",
    };

    private RtmuSubscribers() {
    }

    public static void registerCommon() {
        register(COMMON);
    }

    public static void registerClient() {
        register(CLIENT);
    }

    /**
     * ★<b>どちらのバスに載せるかは注釈から読む。</b>
     *
     * <p>NeoForge にはバスが 2 本ある。
     *
     * <pre>
     *   MOD  … 起動時に 1 回だけ流れるもの (レンダラー登録・キー割り当て・シェーダー・属性)
     *   GAME … 遊んでいる間ずっと流れるもの (tick・描画・入力・コマンド)
     * </pre>
     *
     * <p>前は<b>全部 GAME バスへ登録していた</b>ため、{@code bus = MOD} が付いたクラスには
     * イベントが一切届いていなかった。例外は出ず、ログにも何も出ないまま
     * <b>車両のレンダラーもキー割り当ても登録されない</b>という壊れ方をする。
     *
     * <p>注釈は実行時まで残るので、ここで読んで振り分ける。こうしておけば
     * クラスを足すときにバスを間違えようがない。
     */
    private static void register(String[] names) {
        for (String name : names) {
            try {
                Class<?> clazz = Class.forName(name);
                net.neoforged.fml.common.EventBusSubscriber annotation =
                    clazz.getAnnotation(net.neoforged.fml.common.EventBusSubscriber.class);
                boolean modBus = annotation != null
                    && annotation.bus() == net.neoforged.fml.common.EventBusSubscriber.Bus.MOD;
                if (modBus) {
                    RtmuFabricInit.MOD_BUS.register(clazz);
                } else {
                    NeoForge.EVENT_BUS.register(clazz);
                }
            } catch (Throwable t) {
                //1 つ落ちても他は生かす。黙って消えると原因が分からないので必ず出す。
                com.portofino.realtrainmodunofficial.RealTrainModUnofficial.LOGGER.error(
                    "[RTMU/Fabric] イベント購読の登録に失敗: {}", name, t);
            }
        }
    }
}
