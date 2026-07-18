package com.myname.legacyloader;

import com.myname.legacyloader.bridge.client.registry.LegacyRenderingRegistry;
import com.myname.legacyloader.bridge.client.registry.LegacySimpleBlockRenderingHandler;
import com.myname.legacyloader.bridge.client.renderer.LegacyISBRHBakedModel;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;

/**
 * LegacyLoaderMod の<b>クライアント専用</b>イベントハンドラ (1.7.10 ISBRH/TESR ブロックの
 * モデル差し替え・TESR ディスパッチャ登録・BEWLR 登録)。
 * <p>
 * これらを本体 {@link LegacyLoaderMod} から分離した理由: メソッドの引数/本体が
 * {@code net.minecraft.client.*} / {@code neoforge.client.*} (例 BlockEntityRenderer) を参照するため、
 * それらを本体クラスに置くと<b>専用サーバー起動時にクラス解決で
 * {@code NoClassDefFoundError: .../BlockEntityRenderer} が出て落ちる</b> (RuntimeDistCleaner が
 * DEDICATED_SERVER でのクライアントクラス読込を拒否する)。クライアント専用ハンドラを別クラスにし、
 * {@code Dist.CLIENT} のときだけ {@link #register} を呼ぶことで、サーバーではこのクラスを一切
 * ロードせず本体が正常に構築される。
 */
public final class LegacyLoaderClient {

    private LegacyLoaderClient() {
    }

    /** クライアント専用イベントを mod バスへ登録する (Dist.CLIENT のときのみ本体から呼ばれる)。 */
    public static void register(IEventBus modBus) {
        modBus.addListener(LegacyLoaderClient::onModifyBakingResult);
        modBus.addListener(LegacyLoaderClient::onRegisterBlockEntityRenderers);
        modBus.addListener(LegacyLoaderClient::onRegisterClientExtensions);
    }

    /**
     * 共有 LegacyTileEntity 型に、1.7.10 TESR を毎フレーム呼ぶディスパッチャを登録する。
     * これで AsphaltMod のカラーコーン/道路灯/電光掲示板等 (OBJ を TESR で描く 3D ブロック) が
     * 現行 1.21 でも立体表示される。
     */
    private static void onRegisterBlockEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
                com.myname.legacyloader.bridge.tileentity.LegacyTileEntity.LEGACY_TYPE,
                ctx -> new com.myname.legacyloader.bridge.client.renderer.tileentity.LegacyBlockEntityDispatcher());
    }

    /**
     * TESR 専用ブロック (getRenderType()==-1) の BlockItem に BEWLR
     * ({@link com.myname.legacyloader.bridge.client.renderer.tileentity.LegacyTesrItemRenderer}) を登録。
     * インベントリモデルは {@link #onModifyBakingResult} で isCustomRenderer=true に差し替えるので、
     * ItemRenderer がこの BEWLR を呼んで OBJ を立体表示する。
     */
    private static void onRegisterClientExtensions(
            net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent event) {
        java.util.List<Item> tesrItems = new java.util.ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (item instanceof net.minecraft.world.item.BlockItem blockItem
                    && LegacyRenderingRegistry.getRenderType(blockItem.getBlock()) == -1) {
                tesrItems.add(item);
            }
        }
        if (tesrItems.isEmpty()) return;
        var extensions = new net.neoforged.neoforge.client.extensions.common.IClientItemExtensions() {
            private com.myname.legacyloader.bridge.client.renderer.tileentity.LegacyTesrItemRenderer renderer;

            @Override
            public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) {
                    renderer = new com.myname.legacyloader.bridge.client.renderer.tileentity.LegacyTesrItemRenderer();
                }
                return renderer;
            }
        };
        event.registerItem(extensions, tesrItems.toArray(new Item[0]));
        LegacyLoaderMod.LOGGER.info("LegacyLoader: Registered TESR item renderer for {} items", tesrItems.size());
    }

    private static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        int wrapped = 0;
        int tesrItemModels = 0;
        net.minecraft.client.renderer.block.model.ItemTransforms blockTransforms = standardBlockTransforms(event);
        for (Block block : BuiltInRegistries.BLOCK) {
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
            if (id == null) continue;
            int renderId = LegacyRenderingRegistry.getRenderType(block);
            if (renderId == 0) continue;
            if (renderId == -1) {
                // TESR 専用ブロック: ブロックモデルは空 (OBJ は TESR が描く)。アイテムは
                // isCustomRenderer=true のモデルに差し替え、BEWLR (LegacyTesrItemRenderer) に描かせる。
                ModelResourceLocation invId = new ModelResourceLocation(id, "inventory");
                BakedModel original = event.getModels().get(invId);
                if (original != null
                        && !(original instanceof com.myname.legacyloader.bridge.client.renderer.LegacyTesrItemBakedModel)) {
                    event.getModels().put(invId,
                            new com.myname.legacyloader.bridge.client.renderer.LegacyTesrItemBakedModel(
                                    original, blockTransforms));
                    tesrItemModels++;
                }
                continue;
            }
            LegacySimpleBlockRenderingHandler handler = LegacyRenderingRegistry.getBlockHandler(renderId);
            if (handler == null) continue;
            for (int meta = 0; meta < 16; meta++) {
                if (wrapLegacyModel(event, id, block, renderId, handler, "metadata=" + meta)) wrapped++;
            }
            if (wrapLegacyModel(event, id, block, renderId, handler, "")) wrapped++;
            if (wrapLegacyModel(event, id, block, renderId, handler, "inventory")) wrapped++;
        }
        LegacyLoaderMod.LOGGER.info("LegacyLoader: Wrapped {} legacy ISBRH baked block models, {} TESR item models",
                wrapped, tesrItemModels);
    }

    /** 標準ブロック (stone) の表示変換を借りる。GUI での 30/225 度回転・0.625 縮小など。 */
    private static net.minecraft.client.renderer.block.model.ItemTransforms standardBlockTransforms(
            ModelEvent.ModifyBakingResult event) {
        BakedModel stone = event.getModels().get(new ModelResourceLocation(
                ResourceLocation.fromNamespaceAndPath("minecraft", "stone"), "inventory"));
        return stone != null ? stone.getTransforms()
                : net.minecraft.client.renderer.block.model.ItemTransforms.NO_TRANSFORMS;
    }

    private static boolean wrapLegacyModel(ModelEvent.ModifyBakingResult event, ResourceLocation id, Block block,
                                           int renderId, LegacySimpleBlockRenderingHandler handler, String variant) {
        ModelResourceLocation modelId = new ModelResourceLocation(id, variant);
        BakedModel original = event.getModels().get(modelId);
        if (original == null || original instanceof LegacyISBRHBakedModel) return false;
        event.getModels().put(modelId, new LegacyISBRHBakedModel(original, id, block, renderId, handler, event.getTextureGetter()));
        return true;
    }
}
