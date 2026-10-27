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
import net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;

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
public class BallastModel implements BakedModel, FabricBakedModel {

    /** チャンクを組み立てるスレッドへ形を渡すための入れ物。実体は {@link BallastGeometry#SHAPE}。 */
    public static final ModelProperty<BallastGeometry.Shape> SHAPE = BallastGeometry.SHAPE;

    private final BakedModel fallback;

    public BallastModel(BakedModel fallback) {
        this.fallback = fallback;
    }

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


        for (BallastGeometry.Face face : BallastGeometry.faces(shape)) {
            TextureAtlasSprite sprite = spriteFor(sourceModel, shape.source(), face.dir(), random);
            if (sprite == null) {
                continue;
            }
            int[] vertices = new int[32];
            for (int i=0;i<4;i++) {
                int offset=i*8;
                vertices[offset]=Float.floatToRawIntBits(face.xs()[i]);
                vertices[offset+1]=Float.floatToRawIntBits(face.ys()[i]);
                vertices[offset+2]=Float.floatToRawIntBits(face.zs()[i]);
                vertices[offset+3]=-1;
                vertices[offset+4]=Float.floatToRawIntBits(sprite.getU(face.us()[i]));
                vertices[offset+5]=Float.floatToRawIntBits(sprite.getV(face.vs()[i]));
                vertices[offset+7]=(face.dir().getStepX()*127&255)|((face.dir().getStepY()*127&255)<<8)|((face.dir().getStepZ()*127&255)<<16);
            }
            out.add(new BakedQuad(vertices,-1,face.dir(),sprite,true));
        }
        return out;
    }

    @Override public List<BakedQuad> getQuads(BlockState state,Direction side,RandomSource random) { return Collections.emptyList(); }
    @Override public boolean isVanillaAdapter(){return false;}
    @Override public void emitBlockQuads(net.minecraft.world.level.BlockAndTintGetter view,BlockState state,net.minecraft.core.BlockPos pos,java.util.function.Supplier<RandomSource> random,net.fabricmc.fabric.api.renderer.v1.render.RenderContext context) {
        var shape=BallastGeometry.shapeAt(view,pos);
        if(shape==null)return;
        var data=ModelData.builder().with(SHAPE,shape).build();
        var emitter=context.getEmitter();
        for(var quad:getQuads(state,null,random.get(),data,null)) {
            emitter.fromVanilla(quad,net.fabricmc.fabric.api.renderer.v1.RendererAccess.INSTANCE.getRenderer().materialFinder().ambientOcclusion(net.fabricmc.fabric.api.util.TriState.FALSE).find(),null);
            emitter.emit();
        }
    }
    @Override public net.minecraft.client.renderer.block.model.ItemTransforms getTransforms(){return fallback.getTransforms();}

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
        // Original RenderBlockLargeRail submits fixed face shades, without AO.
        return false;
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
