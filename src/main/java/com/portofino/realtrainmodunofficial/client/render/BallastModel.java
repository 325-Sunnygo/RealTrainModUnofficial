package com.portofino.realtrainmodunofficial.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.IDynamicBakedModel;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import net.neoforged.neoforge.client.model.pipeline.QuadBakingVertexConsumer;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 道床 (レールの下の砂利) を<b>チャンクのメッシュへ焼き込む</b>モデル。
 *
 * <p>本家 1.7.10 は {@code ISimpleBlockRenderingHandler} でチャンクに焼いていた。
 * ブロックエンティティ描画にすると、レール 1 本の脇に幅 3 で敷き詰めた道床が
 * 全部毎フレーム CPU を通ることになるので、同じ「焼く」側に合わせてある。
 * 形が変わるのはレールを敷いた/壊したときだけなので、焼き直しもそのときだけで足りる。
 *
 * <p>形は {@link BallastGeometry} が出す。テクスチャは
 * レールに設定されたブロック (砂利など) のモデルから借りる。
 */
public class BallastModel implements IDynamicBakedModel {

    /** チャンクを組み立てるスレッドへ形を渡すための入れ物。実体は {@link BallastGeometry#SHAPE}。 */
    public static final ModelProperty<BallastGeometry.Shape> SHAPE = BallastGeometry.SHAPE;

    private final BakedModel fallback;

    public BallastModel(BakedModel fallback) {
        this.fallback = fallback;
    }

    @Override
    public @NotNull List<BakedQuad> getQuads(BlockState state, Direction side, @NotNull RandomSource random,
                                             @NotNull ModelData data, RenderType renderType) {
        // side != null は「隣のブロックで隠せる面」の問い合わせ。
        // 道床は高さがまちまちで隣に隠してもらえないので、面引きは自分で済ませてある。
        if (side != null) {
            return Collections.emptyList();
        }
        BallastGeometry.Shape shape = data.get(SHAPE);
        if (shape == null) {
            // まだ形が出せない (レールコアが読み込まれていない等)。
            // ここで元モデルを出すと、当たり判定ブロックが板や立方体として見えてしまう。
            // 敷設途中の一瞬なので、何も描かないのが正しい
            return Collections.emptyList();
        }

        BakedModel sourceModel = Minecraft.getInstance().getBlockRenderer().getBlockModel(shape.source());
        List<BakedQuad> out = new ArrayList<>(6);
        QuadBakingVertexConsumer baker = new QuadBakingVertexConsumer();

        for (BallastGeometry.Face face : BallastGeometry.faces(shape)) {
            TextureAtlasSprite sprite = spriteFor(sourceModel, shape.source(), face.dir(), random);
            if (sprite == null) {
                continue;
            }
            baker.setSprite(sprite);
            baker.setDirection(face.dir());
            // shade=true にすると、バニラが面の向きで 0.5/0.6/0.8/1.0 を掛ける。
            // 本家が手で書いていた係数とちょうど同じ値になる
            baker.setShade(true);
            baker.setTintIndex(-1);
            baker.setHasAmbientOcclusion(true);
            for (int i = 0; i < 4; i++) {
                emit(baker, face, i, sprite);
            }
            //頂点 4 つを入れてから焼く
            out.add(baker.bakeQuad());
        }
        return out;
    }

    private static void emit(VertexConsumer baker, BallastGeometry.Face face, int i,
                             TextureAtlasSprite sprite) {
        Direction dir = face.dir();
        baker.addVertex(face.xs()[i], face.ys()[i], face.zs()[i])
            .setColor(255, 255, 255, 255)
            .setUv(sprite.getU(face.us()[i]), sprite.getV(face.vs()[i]))
            .setUv1(0, 0)
            .setUv2(0, 0)
            .setNormal(dir.getStepX(), dir.getStepY(), dir.getStepZ());
    }

    /** その面に貼るテクスチャ。元ブロックのモデルから借りる。 */
    private static TextureAtlasSprite spriteFor(BakedModel sourceModel, BlockState sourceState,
                                                Direction dir, RandomSource random) {
        try {
            List<BakedQuad> quads = sourceModel.getQuads(sourceState, dir, random);
            if (!quads.isEmpty()) {
                return quads.get(0).getSprite();
            }
            quads = sourceModel.getQuads(sourceState, null, random);
            for (BakedQuad q : quads) {
                if (q.getDirection() == dir) {
                    return q.getSprite();
                }
            }
            return sourceModel.getParticleIcon();
        } catch (Throwable t) {
            return null;
        }
    }

    // --- 以下は元モデルへそのまま流す ---

    @Override
    public boolean useAmbientOcclusion() {
        return true;
    }

    @Override
    public boolean isGui3d() {
        return this.fallback.isGui3d();
    }

    @Override
    public boolean usesBlockLight() {
        return this.fallback.usesBlockLight();
    }

    @Override
    public boolean isCustomRenderer() {
        return false;
    }

    @Override
    public @NotNull TextureAtlasSprite getParticleIcon() {
        return this.fallback.getParticleIcon();
    }

    @Override
    public @NotNull ItemOverrides getOverrides() {
        return ItemOverrides.EMPTY;
    }
}
