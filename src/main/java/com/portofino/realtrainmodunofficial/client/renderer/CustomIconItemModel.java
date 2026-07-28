package com.portofino.realtrainmodunofficial.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransform;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * customIconTexture が設定されているときだけ、アイテムの描画を
 * CustomIconItemRenderer へ回すためのモデル。
 * 仕組み: 1.21 のアイテム描画は「焼き上がったモデルが isCustomRenderer を
 * 返したら IClientItemExtensions の描画器を使う」という作りになっている。
 */
public class CustomIconItemModel implements BakedModel {

    private final BakedModel original;

    private CustomIconItemModel(BakedModel original) {
        this.original = original;
    }

    /** 元のモデルを包んで、customIconTexture 付きのときだけ差し替わるようにする。 */
    public static BakedModel wrap(BakedModel original) {
        if (original == null || original instanceof Wrapper) {
            return original;
        }
        return new Wrapper(original, new CustomIconItemModel(original));
    }

    @Override
    public boolean isCustomRenderer() {
        return true;
    }

    @Override
    public List<net.minecraft.client.renderer.block.model.BakedQuad> getQuads(
            BlockState state, Direction side, RandomSource random) {
        return this.original.getQuads(state, side, random);
    }

    @Override
    public boolean useAmbientOcclusion() {
        return this.original.useAmbientOcclusion();
    }

    @Override
    public boolean isGui3d() {
        return this.original.isGui3d();
    }

    @Override
    public boolean usesBlockLight() {
        return this.original.usesBlockLight();
    }

    @Override
    public TextureAtlasSprite getParticleIcon() {
        return this.original.getParticleIcon();
    }

    @Override
    public ItemOverrides getOverrides() {
        return ItemOverrides.EMPTY;
    }

    @Override
    public ItemTransforms getTransforms() {
        return this.original.getTransforms();
    }

    @Override
    public BakedModel applyTransform(ItemDisplayContext context, PoseStack poseStack,
                                     boolean applyLeftHandTransform) {
        // 位置と大きさはバニラのアイテムと同じにしたいので元のモデルの変換を使う。
        // ただし戻すのは自分 (元を返すと isCustomRenderer が false になり自前描画に来ない)。
        this.original.applyTransform(context, poseStack, applyLeftHandTransform);
        return this;
    }

    /**
     * 元のモデルの皮。ItemOverrides だけ差し替える。
     * これがレジストリに載るモデルになる。
     */
    private static final class Wrapper implements BakedModel {
        private final BakedModel original;
        private final CustomIconItemModel custom;
        private final ItemOverrides overrides;

        Wrapper(BakedModel original, CustomIconItemModel custom) {
            this.original = original;
            this.custom = custom;
            this.overrides = new ItemOverrides() {
                @Override
                public BakedModel resolve(BakedModel model, ItemStack stack, ClientLevel level,
                                          LivingEntity entity, int seed) {
                    // まず元の override (壊れ具合など) を通してから判定する
                    BakedModel resolved = original.getOverrides()
                        .resolve(original, stack, level, entity, seed);
                    if (resolved == null) {
                        resolved = original;
                    }
                    return CustomIconItemRenderer.iconPathOf(stack).isEmpty() ? resolved : custom;
                }
            };
        }

        @Override
        public List<net.minecraft.client.renderer.block.model.BakedQuad> getQuads(
                BlockState state, Direction side, RandomSource random) {
            return this.original.getQuads(state, side, random);
        }

        @Override
        public boolean useAmbientOcclusion() {
            return this.original.useAmbientOcclusion();
        }

        @Override
        public boolean isGui3d() {
            return this.original.isGui3d();
        }

        @Override
        public boolean usesBlockLight() {
            return this.original.usesBlockLight();
        }

        @Override
        public boolean isCustomRenderer() {
            return false;   //既定はバニラ描画。差し替えは overrides が決める
        }

        @Override
        public TextureAtlasSprite getParticleIcon() {
            return this.original.getParticleIcon();
        }

        @Override
        public ItemOverrides getOverrides() {
            return this.overrides;
        }

        @Override
        public ItemTransforms getTransforms() {
            return this.original.getTransforms();
        }

        @Override
        public BakedModel applyTransform(ItemDisplayContext context, PoseStack poseStack,
                                         boolean applyLeftHandTransform) {
            return this.original.applyTransform(context, poseStack, applyLeftHandTransform);
        }

        /** 使わないが interface の既定実装が要求する。 */
        @SuppressWarnings("unused")
        ItemTransform transform(ItemDisplayContext context) {
            return this.original.getTransforms().getTransform(context);
        }
    }
}
