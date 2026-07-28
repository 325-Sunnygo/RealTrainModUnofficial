package com.portofino.realtrainmodunofficial.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Fabric のクライアントエントリポイント。
 *
 * <p>NeoForge の {@code @EventBusSubscriber(value = Dist.CLIENT)} で拾っていたものを、
 * ここから Fabric のコールバックへ配線する。
 *
 * <p>★クライアント専用クラスをここより上 (共通側) から参照しないこと。
 * 専用サーバーで起動時クラッシュになる。NeoForge 版で同じ踏み方をしている。
 */
public class RtmuFabricClientInit implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        //@EventBusSubscriber(Dist.CLIENT) 相当
        RtmuSubscribers.registerClient();

        //--- MOD バス: 起動時に 1 回 ---
        //レンダラー登録・キーマッピング・クライアント初期化。順番は NeoForge と同じにする。
        RtmuFabricInit.MOD_BUS.post(new EntityRenderersEvent.RegisterRenderers());
        registerKeyMappings();
        RtmuFabricInit.MOD_BUS.post(new FMLClientSetupEvent());

        //ブロック/アイテムの色 (マーカーの赤・青など)。シムが直接 Fabric へ登録するので post だけ。
        RtmuFabricInit.MOD_BUS.post(
            new net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.Block());
        RtmuFabricInit.MOD_BUS.post(
            new net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.Item());

        registerCutoutBlocks();
        registerBakedModelHook();
        registerCustomItemRenderers();

        //★同梱パック・生成サウンドパック・外部建材パックの差し込み。
        //  これが無いとモデルもテクスチャも 1 つも出ない。
        //  NeoForge は RepositorySource を受け取る形、Fabric は Pack を直接足す形なので、
        //  集めた source から Pack を取り出して登録する。
        var packEvent = new net.neoforged.neoforge.event.AddPackFindersEvent(
            net.minecraft.server.packs.PackType.CLIENT_RESOURCES);
        RtmuFabricInit.MOD_BUS.post(packEvent);
        RtmuPackBridge.install(packEvent);

        //コアシェーダー。1.21.1 のバニラには mod から足す仕組みが無く、
        //NeoForge は RegisterShadersEvent、Fabric はこのコールバックで受ける。
        //★NeoForge は「自分で ShaderInstance を作って渡す」形だが、Fabric は
        //  「名前と頂点フォーマットを預けて作ってもらう」形で、リソース提供者は表に出ない。
        //  そこでシムのイベントは<b>作らずに宣言だけ</b>集める形へ寄せ、ここで Fabric に作らせる。
        CoreShaderRegistrationCallback.EVENT.register(context -> {
            RegisterShadersEvent event = new RegisterShadersEvent();
            RtmuFabricInit.MOD_BUS.post(event);
            for (RegisterShadersEvent.Declaration d : event.getDeclarations()) {
                try {
                    context.register(d.id(), d.format(), d.sink()::accept);
                } catch (java.io.IOException e) {
                    com.portofino.realtrainmodunofficial.RealTrainModUnofficial.LOGGER.error(
                        "[RTMU/Fabric] コアシェーダーの登録に失敗: {}", d.id(), e);
                }
            }
        });

        //--- GAME バス ---
        ClientTickEvents.END_CLIENT_TICK.register(mc ->
            NeoForge.EVENT_BUS.post(new ClientTickEvent.Post()));

        //ワールド描画の各段階。NeoForge の Stage と Fabric のイベントを対応付ける。
        //
        //★<b>名前が似ているだけで飛ぶ位置が違う</b>。Fabric 側の実際の注入位置は
        //  fabric-rendering-v1 の WorldRendererMixin で確認すること。
        //
        //  BEFORE_ENTITIES    … renderLayer 3 本 (solid/cutout_mipped/cutout) の直後
        //  AFTER_ENTITIES     … プロファイラが "blockentities" に切り替わる所
        //                       = エンティティの<b>後</b>・ブロックエンティティの<b>前</b>
        //  BEFORE_DEBUG_RENDER… ブロックエンティティ・破壊進捗・当たり判定枠の後、半透明地形の前
        //  AFTER_TRANSLUCENT  … 半透明地形の後 (雲の直前)
        //
        //★以前 AFTER_ENTITIES を AFTER_BLOCK_ENTITIES に当てていて、
        //  <b>レールを溜める前に描画キューを吐いていた</b>。結果レールは常に
        //  1 フレーム前の行列で描かれ、<b>視点を動かすとレールがずれて見えた</b>
        //  (止まっていると正しく見えるので気付きにくい)。
        WorldRenderEvents.BEFORE_ENTITIES.register(ctx ->
            postStage(RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS, ctx));
        WorldRenderEvents.AFTER_ENTITIES.register(ctx ->
            postStage(RenderLevelStageEvent.Stage.AFTER_ENTITIES, ctx));
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(ctx ->
            postStage(RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES, ctx));
        WorldRenderEvents.AFTER_TRANSLUCENT.register(ctx ->
            postStage(RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS, ctx));
        WorldRenderEvents.LAST.register(ctx ->
            postStage(RenderLevelStageEvent.Stage.AFTER_LEVEL, ctx));

        //画面。ポーズメニューへのボタン差し込み等はここで実際に足す。
        ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
            ScreenEvent.Init.Post event = new ScreenEvent.Init.Post(screen);
            NeoForge.EVENT_BUS.post(event);
            for (var listener : event.getAddedListeners()) {
                //NeoForge は event.addListener で画面へ直接足す。Fabric は
                //Screens.getButtons(screen) が窓口なので、Button だけそちらへ回す。
                if (listener instanceof net.minecraft.client.gui.components.AbstractWidget widget) {
                    net.fabricmc.fabric.api.client.screen.v1.Screens.getButtons(screen).add(widget);
                }
            }
        });

        //画面の上への追加描画 (モデル選択のプレビュー等)。
        ScreenEvents.AFTER_INIT.register((mc, screen, w, h) ->
            ScreenEvents.afterRender(screen).register((sc, graphics, mouseX, mouseY, delta) ->
                NeoForge.EVENT_BUS.post(new ScreenEvent.Render.Post(sc, graphics, mouseX, mouseY, delta))));

        HudRenderCallback.EVENT.register((graphics, tickCounter) ->
            NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.client.event.RenderGuiEvent.Post(
                graphics, tickCounter.getGameTimeDeltaPartialTick(false))));

        //共通側と同じ理由でクライアントにも要る (描画側の一覧に自分を入れている)。
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientBlockEntityEvents.BLOCK_ENTITY_LOAD
            .register((be, level) -> {
                if (be instanceof jp.ngt.mccompat.LoadAwareBlockEntity aware) {
                    aware.onLoad();
                }
            });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) ->
            NeoForge.EVENT_BUS.post(new ClientPlayerNetworkEvent.LoggingIn(mc.player)));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) ->
            NeoForge.EVENT_BUS.post(new ClientPlayerNetworkEvent.LoggingOut(mc.player)));

        com.portofino.realtrainmodunofficial.RealTrainModUnofficial.LOGGER.info(
            "[RTMU/Fabric] クライアント初期化が完了しました");
    }

    /**
     * <b>切り抜き (cutout) で描くブロック。</b>
     *
     * <p>直した症状: <b>マーカーの見た目がおかしい</b> (薄い板のはずが真っ黒な塊になる)。
     *
     * <p>モデル JSON の {@code "render_type": "cutout"} は<b>NeoForge が足した項目</b>で、
     * Fabric は読まない。指定が無いと不透明として描かれ、
     * <b>抜けているはずの画素が黒く塗り潰される</b>。
     *
     * <p>Fabric ではモデルではなく<b>ブロック単位で</b>描画層を指定する。
     * モデル側の記述はそのまま残してある (NeoForge 版と資源を共有しているため)。
     *
     * <p>★{@code render_type} を使うモデルを増やしたらここへも足すこと。
     * 現状は marker_base だけ (marker / marker_switch / legacy_marker / legacy_marker_switch が使う)。
     */
    private static void registerCutoutBlocks() {
        var cutout = net.minecraft.client.renderer.RenderType.cutout();
        net.minecraft.world.level.block.Block[] blocks = {
            jp.ngt.rtm.rail.RTMRailBlocks.MARKER.get(),
            jp.ngt.rtm.rail.RTMRailBlocks.MARKER_SWITCH.get(),
            com.portofino.realtrainmodunofficial.RealTrainModUnofficialBlocks.MARKER.get(),
            com.portofino.realtrainmodunofficial.RealTrainModUnofficialBlocks.MARKER_SWITCH.get(),
        };
        for (net.minecraft.world.level.block.Block block : blocks) {
            net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap.INSTANCE
                .putBlock(block, cutout);
        }
    }

    /**
     * <b>焼き上がったモデルへの介入</b> ({@code customIconTexture})。
     *
     * <p>NeoForge は「全モデルの表」を一度に渡すが、Fabric は 1 個ずつしか渡さない。
     * そこで<b>1 個だけ入った表</b>を作って共有処理へ流す。共有側は表を回して条件に合う物を
     * 差し替えるだけなので、この形でそのまま動く (シム側の説明も参照)。
     */
    private static void registerBakedModelHook() {
        net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin.register(pluginContext ->
            pluginContext.modifyModelAfterBake().register((model, context) -> {
                net.minecraft.client.resources.model.ModelResourceLocation id = context.topLevelId();
                if (id == null || model == null) {
                    return model;
                }
                java.util.Map<net.minecraft.client.resources.model.ModelResourceLocation,
                    net.minecraft.client.resources.model.BakedModel> one = new java.util.HashMap<>();
                one.put(id, model);
                RtmuFabricInit.MOD_BUS.post(
                    new net.neoforged.neoforge.client.event.ModelEvent.ModifyBakingResult(one));
                return one.get(id);
            }));
    }

    /**
     * <b>アイテムの独自描画</b> (ミニチュアの中身・{@code customIconTexture} のアイコン)。
     *
     * <p>NeoForge はアイテム側の {@code initializeClient} を勝手に呼んで
     * {@code IClientItemExtensions#getCustomRenderer} を拾う。Fabric にその仕組みは無いので、
     * ここで<b>自分で呼んで</b> {@code BuiltinItemRendererRegistry} へ繋ぐ。
     *
     * <p>アイテム側の定義は NeoForge 版と<b>同じファイルを共有している</b>ので、
     * 取り出しはリフレクションで行う。共有ファイルに Fabric 用の細工を入れると
     * 次に本家側を直したときの取り込みで衝突する。
     *
     * <p>★描画器の生成は<b>実際に描くときまで遅らせる</b>。ここは Minecraft の
     * コンストラクタの途中で、{@code BlockEntityRenderDispatcher} 等がまだ用意されていない。
     * 初期化時に作ると NullPointerException で起動しなくなる。
     */
    private static void registerCustomItemRenderers() {
        int registered = 0;
        for (net.minecraft.world.item.Item item : net.minecraft.core.registries.BuiltInRegistries.ITEM) {
            net.minecraft.resources.ResourceLocation id =
                net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item);
            if (id == null || !com.portofino.realtrainmodunofficial.RealTrainModUnofficial.MODID
                    .equals(id.getNamespace())) {
                continue;
            }
            java.lang.reflect.Method init;
            try {
                init = item.getClass().getMethod("initializeClient", java.util.function.Consumer.class);
            } catch (NoSuchMethodException e) {
                continue;
            }
            try {
                net.neoforged.neoforge.client.extensions.common.IClientItemExtensions[] holder =
                    new net.neoforged.neoforge.client.extensions.common.IClientItemExtensions[1];
                init.invoke(item, (java.util.function.Consumer<
                    net.neoforged.neoforge.client.extensions.common.IClientItemExtensions>)
                    ext -> holder[0] = ext);
                if (holder[0] == null) {
                    continue;
                }
                var extensions = holder[0];
                net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry.INSTANCE
                    .register(item, (stack, mode, pose, buffers, light, overlay) -> {
                        var renderer = extensions.getCustomRenderer();
                        if (renderer != null) {
                            renderer.renderByItem(stack, mode, pose, buffers, light, overlay);
                        }
                    });
                registered++;
            } catch (Throwable t) {
                com.portofino.realtrainmodunofficial.RealTrainModUnofficial.LOGGER.error(
                    "[RTMU/Fabric] アイテムの独自描画を登録できません: {}", id, t);
            }
        }
        com.portofino.realtrainmodunofficial.RealTrainModUnofficial.LOGGER.info(
            "[RTMU/Fabric] アイテムの独自描画を {} 件登録しました", registered);
    }

    /**
     * RTMU のキー割り当てを Fabric へ登録する。
     *
     * <p>★集めるだけで<b>登録し忘れていた</b>ことがある。登録しないと
     * 「操作設定」の一覧に出てこず、割り当てを変えることも保存することもできない。
     * それでいて {@code KeyMapping} を作った時点でバニラの静的な表には載るので、
     * <b>バニラのキーを奪うのに設定画面からは直せない</b>という最悪の状態になる
     * (前後に歩けなくなった原因。表の側は {@code KeyMappingMixin} で直してある)。
     */
    private static void registerKeyMappings() {
        RegisterKeyMappingsEvent event = new RegisterKeyMappingsEvent();
        RtmuFabricInit.MOD_BUS.post(event);
        for (net.minecraft.client.KeyMapping mapping : event.getMappings()) {
            try {
                net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
                    .registerKeyBinding(mapping);
            } catch (Throwable t) {
                //1 つ失敗しても残りは登録する。黙って消えると原因が分からない
                com.portofino.realtrainmodunofficial.RealTrainModUnofficial.LOGGER.error(
                    "[RTMU/Fabric] キー割り当ての登録に失敗: {}", mapping.getName(), t);
            }
        }
        com.portofino.realtrainmodunofficial.RealTrainModUnofficial.LOGGER.info(
            "[RTMU/Fabric] キー割り当てを {} 件登録しました", event.getMappings().size());
    }

    /** ワールド描画イベント 1 段ぶんを流す。 */
    private static void postStage(RenderLevelStageEvent.Stage stage,
                                  net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext ctx) {
        NeoForge.EVENT_BUS.post(new RenderLevelStageEvent(stage, ctx.matrixStack(),
            ctx.projectionMatrix(), ctx.camera(), ctx.tickCounter().getGameTimeDeltaPartialTick(false)));
    }
}
