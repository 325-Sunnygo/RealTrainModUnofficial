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
 * {@code customIconTexture} が設定されているときだけ、アイテムの描画を
 * {@link CustomIconItemRenderer} へ回すためのモデル。
 *
 * <p><b>仕組み</b>: 1.21 のアイテム描画は「焼き上がったモデルが {@code isCustomRenderer()} を
 * 返したら {@code IClientItemExtensions} の描画器を使う」という作りになっている。
 * ところがこの判定にはアイテムスタックが渡らないので、<b>スタックを見られる
 * {@link ItemOverrides#resolve} で差し替える</b>。
 *
 * <pre>
 *   元のモデル ── overrides.resolve(stack) ─┬─ customIconTexture 無し → 元のモデル (バニラ描画)
 *                                          └─ 有り → このモデル (isCustomRenderer=true)
 *                                                     → CustomIconItemRenderer
 * </pre>
 *
 * <p>この形にしているので、<b>設定していないアイテムの見た目は 1 ピクセルも変わらない</b>。
 * 逆に「全部のアイテムを自前描画に切り替えて、必要なときだけ元へ戻す」という作りにすると、
 * バニラのアイテム描画 (厚みのある板・エンチャント光沢・GUI の配置) を全部作り直すことになる。
 *
 * <p>変換 ({@link ItemTransforms}) は<b>元のモデルのものをそのまま使う</b>。
 * 持ち物欄・手元・地面での大きさと角度がバニラのアイテムと揃う。
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

    //★applyTransform は NeoForge が BakedModel に足したメソッドで、バニラには無い。
    //  バニラの ItemRenderer は代わりに getTransforms() を読むので、
    //  上の getTransforms() が元のモデルへ委譲していれば位置と大きさは同じになる。

    /**
     * 元のモデルの皮。{@link ItemOverrides} だけ差し替える。
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
                    //まず元の override (壊れ具合など) を通してから判定する
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

        //applyTransform は NeoForge 拡張。理由は上の同名メソッドの注記を参照。

        /** 使わないが interface の既定実装が要求する。 */
        @SuppressWarnings("unused")
        ItemTransform transform(ItemDisplayContext context) {
            return this.original.getTransforms().getTransform(context);
        }
    }
}
