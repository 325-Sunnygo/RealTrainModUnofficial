package com.myname.legacyloader.bridge.client.renderer;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * TESR 専用ブロック (getRenderType()==-1、AsphaltMod の OBJ 3D ブロック等) のアイテムモデル。
 * ブロックモデルは空 (OBJ は TESR が描く) のため、そのままではアイテムが透明になる。
 * {@code isCustomRenderer()=true} を返して ItemRenderer に BEWLR
 * ({@link com.myname.legacyloader.bridge.client.renderer.tileentity.LegacyTesrItemRenderer})
 * を呼ばせ、インベントリ/手持ちでも OBJ を立体表示する。
 * 表示変換 (GUI での回転/縮小等) は標準ブロックモデルから借りたものを返す。
 */
public class LegacyTesrItemBakedModel implements BakedModel {

    private final BakedModel fallback;
    private final ItemTransforms transforms;

    public LegacyTesrItemBakedModel(BakedModel fallback, ItemTransforms transforms) {
        this.fallback = fallback;
        this.transforms = transforms != null ? transforms : fallback.getTransforms();
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand) {
        return List.of();
    }

    @Override public boolean useAmbientOcclusion() { return true; }
    @Override public boolean isGui3d() { return true; }
    @Override public boolean usesBlockLight() { return true; }
    @Override public boolean isCustomRenderer() { return true; } // BEWLR 経由で描画させる
    @Override public TextureAtlasSprite getParticleIcon() { return fallback.getParticleIcon(); }
    @Override public ItemTransforms getTransforms() { return transforms; }
    @Override public ItemOverrides getOverrides() { return fallback.getOverrides(); }
}
