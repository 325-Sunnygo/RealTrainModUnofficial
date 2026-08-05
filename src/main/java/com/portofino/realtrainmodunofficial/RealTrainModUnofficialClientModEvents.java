package com.portofino.realtrainmodunofficial;

import com.portofino.realtrainmodunofficial.client.renderer.RailCoreBlockEntityRenderer;
import com.portofino.realtrainmodunofficial.client.renderer.RtmBogieRenderer;
import com.portofino.realtrainmodunofficial.client.renderer.RtmTrainRenderer;
import com.portofino.realtrainmodunofficial.client.renderer.TrainBogieEntityRenderer;
import com.portofino.realtrainmodunofficial.client.renderer.TrainEntityRenderer;
import com.portofino.realtrainmodunofficial.client.renderer.TrainSeatEntityRenderer;
import com.portofino.realtrainmodunofficial.client.TrainControlKeyMappings;
import com.portofino.realtrainmodunofficial.client.renderer.CarRenderer;
import com.portofino.realtrainmodunofficial.client.renderer.InstalledObjectBlockEntityRenderer;
import com.portofino.realtrainmodunofficial.client.sound.ExternalSoundPackBridge;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.event.AddPackFindersEvent;

@EventBusSubscriber(modid = RealTrainModUnofficial.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class RealTrainModUnofficialClientModEvents {
    private RealTrainModUnofficialClientModEvents() {
    }

    /**
     * 本家 KaizPatchX の customIconTexture を効かせる。
     * 設置物アイテムの焼き上がったモデルを包み、スタックを見られる ItemOverrides で
     * 差し替え判定をする。
     */
    @SubscribeEvent
    public static void wrapCustomIconModels(net.neoforged.neoforge.client.event.ModelEvent.ModifyBakingResult event) {
        int wrapped = 0;
        for (var entry : event.getModels().entrySet()) {
            net.minecraft.client.resources.model.ModelResourceLocation key = entry.getKey();
            if (!RealTrainModUnofficial.MODID.equals(key.id().getNamespace())) {
                continue;
            }
            // アイテムのモデルだけ (ブロックの状態モデルは対象外)
            if (!"inventory".equals(key.getVariant())) {
                continue;
            }
            entry.setValue(
                com.portofino.realtrainmodunofficial.client.renderer.CustomIconItemModel
                    .wrap(entry.getValue()));
            wrapped++;
        }
        com.portofino.realtrainmodunofficial.client.renderer.CustomIconItemRenderer.clear();
        RealTrainModUnofficial.LOGGER.debug(
            "[RTMU] customIconTexture 用にアイテムモデルを {} 個包みました", wrapped);
    }

    /**
     * 弾薬 / 紙幣の絵を variant で切り替える。
     * 本家はアイテムのメタでモデルを分けていたが 1.21 にメタが無いので、
     * モデル json の {@code overrides} が読む述語をここで用意する。
     */
    /** 作業台の画面を紐づける。 */
    @SubscribeEvent
    public static void registerScreens(net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event) {
        event.register(RealTrainModUnofficialMenus.WORK_BENCH.get(),
            com.portofino.realtrainmodunofficial.client.screen.WorkBenchScreen::new);
        event.register(RealTrainModUnofficialMenus.NPC.get(),
            com.portofino.realtrainmodunofficial.client.screen.NpcScreen::new);
    }

    @SubscribeEvent
    public static void registerItemProperties(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            net.minecraft.resources.ResourceLocation key =
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                    RealTrainModUnofficial.MODID, "variant");
            //★ClampedItemPropertyFunction にすると値が 0〜1 に丸められ、variant 2 以上が
            //  全部 1 に潰れる (枕木のアイコンが砲弾になる)。素の ItemPropertyFunction を使う。
            net.minecraft.client.renderer.item.ItemPropertyFunction fn =
                (stack, level, entity, seed) -> {
                    Integer v = stack.get(
                        com.portofino.realtrainmodunofficial.RealTrainModUnofficialComponents
                            .ITEM_VARIANT.get());
                    return v == null ? 0.0F : v;
                };
            net.minecraft.client.renderer.item.ItemProperties.register(
                RealTrainModUnofficialItems.BULLET_ITEM.get(), key, fn);
            net.minecraft.client.renderer.item.ItemProperties.register(
                RealTrainModUnofficialItems.ITEM_CARGO.get(), key, fn);
        });
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        // ブロック製の乗り物 (乗り物生成機)
        event.registerEntityRenderer(jp.ngt.rtm.entity.RTMEntities.NGTO_CAR.get(),
            com.portofino.realtrainmodunofficial.client.renderer.NgtoVehicleRenderer::new);
        event.registerEntityRenderer(jp.ngt.rtm.entity.RTMEntities.NGTO_SHIP.get(),
            com.portofino.realtrainmodunofficial.client.renderer.NgtoVehicleRenderer::new);
        event.registerEntityRenderer(jp.ngt.rtm.entity.RTMEntities.NGTO_PLANE.get(),
            com.portofino.realtrainmodunofficial.client.renderer.NgtoVehicleRenderer::new);

        // NPC (本家 RenderNPC)
        event.registerEntityRenderer(
            jp.ngt.rtm.entity.RTMEntities.NPC.get(),
            com.portofino.realtrainmodunofficial.client.renderer.NpcRenderer::new
        );

        // 貨物 (本家 RenderContainer / RenderArtillery)
        event.registerEntityRenderer(
            jp.ngt.rtm.entity.RTMEntities.CONTAINER.get(),
            com.portofino.realtrainmodunofficial.client.renderer.CargoRenderer::new
        );
        event.registerEntityRenderer(
            jp.ngt.rtm.entity.RTMEntities.ARTILLERY.get(),
            com.portofino.realtrainmodunofficial.client.renderer.CargoRenderer::new
        );
        // 貨物用枕木 (本家 RenderTie: 2.5x0.125x2.5 の板)
        event.registerEntityRenderer(
            jp.ngt.rtm.entity.RTMEntities.TIE.get(),
            com.portofino.realtrainmodunofficial.client.renderer.TieRenderer::new
        );

        // 溶けた金属 / コークスの粒 (本家 RenderFluid)
        event.registerEntityRenderer(
            jp.ngt.rtm.entity.RTMEntities.FLUID.get(),
            com.portofino.realtrainmodunofficial.client.renderer.FluidRenderer::new
        );

        // 銃の弾 (本家 RenderBullet)
        event.registerEntityRenderer(
            jp.ngt.rtm.entity.RTMEntities.BULLET.get(),
            com.portofino.realtrainmodunofficial.client.renderer.BulletRenderer::new
        );

        // エディタの選択範囲 (neo mcte)
        event.registerEntityRenderer(
            com.portofino.realtrainmodunofficial.RealTrainModUnofficialEntities.EDITOR.get(),
            com.portofino.realtrainmodunofficial.client.renderer.EditorEntityRenderer::new
        );

        // 装飾ブロック (本家 RenderDecoration)
        event.registerBlockEntityRenderer(
            com.portofino.realtrainmodunofficial.RealTrainModUnofficialBlockEntities.DECORATION.get(),
            com.portofino.realtrainmodunofficial.client.renderer.DecorationRenderer::new
        );

        // 設置済みミニチュア (neo mcte)
        event.registerBlockEntityRenderer(
            com.portofino.realtrainmodunofficial.RealTrainModUnofficialBlockEntities.MINIATURE.get(),
            com.portofino.realtrainmodunofficial.client.renderer.MiniatureBlockEntityRenderer::new
        );

        // レールコアのブロックエンティティレンダラーを登録（MQOモデル描画）
        // jp.ngt.rtm.rail の各コア BE に登録 (Phase 1 フリップ)
        event.registerBlockEntityRenderer(
            jp.ngt.rtm.rail.RTMRailBlockEntities.LARGE_RAIL_NORMAL_CORE.get(),
            RailCoreBlockEntityRenderer::new
        );
        event.registerBlockEntityRenderer(
            jp.ngt.rtm.rail.RTMRailBlockEntities.LARGE_RAIL_SWITCH_CORE.get(),
            RailCoreBlockEntityRenderer::new
        );
        event.registerBlockEntityRenderer(
            jp.ngt.rtm.rail.RTMRailBlockEntities.LARGE_RAIL_SLOPE_CORE.get(),
            RailCoreBlockEntityRenderer::new
        );
        event.registerBlockEntityRenderer(
            jp.ngt.rtm.rail.RTMRailBlockEntities.TURNTABLE_CORE.get(),
            RailCoreBlockEntityRenderer::new
        );
        // マーカーの距離 (メートル) 表示
        event.registerBlockEntityRenderer(
            jp.ngt.rtm.rail.RTMRailBlockEntities.MARKER.get(),
            com.portofino.realtrainmodunofficial.client.renderer.MarkerBlockEntityRenderer::new
        );
        event.registerBlockEntityRenderer(
            RealTrainModUnofficialBlockEntities.INSTALLED_OBJECT.get(),
            InstalledObjectBlockEntityRenderer::new
        );
        // 背景パネル (模型の背景に写真を立てる)
        event.registerBlockEntityRenderer(
            RealTrainModUnofficialBlockEntities.BACKGROUND_PANEL.get(),
            com.portofino.realtrainmodunofficial.client.renderer.BackgroundPanelBlockEntityRenderer::new
        );
        // jp.ngt.rtm.entity: 本家忠実移植の列車/台車 (Phase 2 フリップ)
        event.registerEntityRenderer(
            jp.ngt.rtm.entity.RTMEntities.TRAIN.get(),
            RtmTrainRenderer::new
        );
        event.registerEntityRenderer(
            jp.ngt.rtm.entity.RTMEntities.BOGIE.get(),
            RtmBogieRenderer::new
        );
        event.registerEntityRenderer(
            jp.ngt.rtm.entity.RTMEntities.FLOOR.get(),
            com.portofino.realtrainmodunofficial.client.renderer.RtmFloorRenderer::new
        );
        if (RealTrainModUnofficialEntities.TRAIN.isBound()) {
            event.registerEntityRenderer(
                RealTrainModUnofficialEntities.TRAIN.get(),
                TrainEntityRenderer::new
            );
        }
        if (RealTrainModUnofficialEntities.TRAIN_BOGIE.isBound()) {
            event.registerEntityRenderer(
                RealTrainModUnofficialEntities.TRAIN_BOGIE.get(),
                TrainBogieEntityRenderer::new
            );
        }
        if (RealTrainModUnofficialEntities.TRAIN_SEAT.isBound()) {
            event.registerEntityRenderer(
                RealTrainModUnofficialEntities.TRAIN_SEAT.get(),
                TrainSeatEntityRenderer::new
            );
        }
        event.registerEntityRenderer(
            com.portofino.realtrainmodunofficial.registry.RealTrainModUnofficialEntities.CAR.get(),
            CarRenderer::new
        );
        // 本家 EntityMotorman (運転士): プレイヤーモデル + 同梱スキン
        event.registerEntityRenderer(
            jp.ngt.rtm.entity.RTMEntities.MOTORMAN.get(),
            com.portofino.realtrainmodunofficial.client.renderer.MotormanRenderer::new
        );
    }

    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        TrainControlKeyMappings.register(event);
        // カメラのキーは固定 (キーコンフィグに出さない)。CameraKeyMappings 参照。
    }

    /** カメラの被写界深度 / 流し撮り用コアシェーダー */
    @SubscribeEvent
    public static void registerShaders(net.neoforged.neoforge.client.event.RegisterShadersEvent event)
            throws java.io.IOException {
        com.portofino.realtrainmodunofficial.client.camera.CameraPostProcessor.registerShaders(event);
    }

    @SubscribeEvent
    public static void registerPackFinders(AddPackFindersEvent event) {
        ExternalSoundPackBridge.register(event);
        // mods フォルダの 1.7.10 建材ブロックぶんの blockstate/model/texture/lang を生成注入。
        com.portofino.realtrainmodunofficial.client.building.ExternalBuildingPackBridge.register(event);
    }

    // 本家RTM同様、テクスチャ(白の marker_0 等)は変えず tint 色だけ変える。
    // 普通マーカー=赤、分岐マーカー=青。
    private static final int MARKER_COLOR = 0xFF3B30;        // 赤
    private static final int MARKER_SWITCH_COLOR = 0x0028C8; // 濃い青(本家寄り)

    @SubscribeEvent
    public static void registerBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register(
            (state, tintGetter, pos, tintIndex) -> MARKER_COLOR,
            jp.ngt.rtm.rail.RTMRailBlocks.MARKER.get()
        );
        event.register(
            (state, tintGetter, pos, tintIndex) -> MARKER_SWITCH_COLOR,
            jp.ngt.rtm.rail.RTMRailBlocks.MARKER_SWITCH.get()
        );
    }

    @SubscribeEvent
    public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        event.register(
            (stack, tintIndex) -> MARKER_COLOR,
            RealTrainModUnofficialItems.MARKER_ITEM.get()
        );
        event.register(
            (stack, tintIndex) -> MARKER_SWITCH_COLOR,
            RealTrainModUnofficialItems.MARKER_SWITCH_ITEM.get()
        );
    }



}
