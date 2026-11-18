package jp.ngt.mccompat.client;

import jp.ngt.mccompat.IconCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/** 1.7.10 Block.func_149691_a(side, meta) (= getIcon… */
public final class BlockClientCompat {
    private BlockClientCompat() {
    }

    /** 42 は BakedModel のランダム選択を固定するための種 (どの quad でも spri… */
    private static final long QUAD_RANDOM_SEED = 42L;

    @SuppressWarnings("deprecation")
    public static IconCompat func_149691_a(Object block, int side, int meta) {
        try {
            if (!(block instanceof Block b)) {
                return fallback();
            }
            Minecraft mc = Minecraft.getInstance();
            if (mc == null) {
                return fallback();
            }
            // 1.7.10 の (block, meta) → 1.21 の BlockState (色メタは色…
            BlockState state = jp.ngt.mccompat.init.Blocks.stateFor(b, meta);
            BlockModelShaper shaper = mc.getBlockRenderer().getBlockModelShaper();
            BakedModel model = shaper.getBlockModel(state);
            RandomSource random = RandomSource.create(QUAD_RANDOM_SEED);

            TextureAtlasSprite sprite = null;
            if (side >= 0 && side < 6) {
                Direction dir = Direction.from3DDataValue(side);
                sprite = firstSprite(model.getQuads(state, dir, random));
            }
            if (sprite == null) {
                sprite = firstSprite(model.getQuads(state, null, random));
            }
            if (sprite == null) {
                sprite = shaper.getParticleIcon(state);
            }
            if (sprite == null) {
                return fallback();
            }
            return new IconCompat(sprite.getU0(), sprite.getV0(), sprite.getU1(), sprite.getV1());
        } catch (Throwable t) {
            return fallback();
        }
    }

    private static TextureAtlasSprite firstSprite(List<BakedQuad> quads) {
        if (quads == null) {
            return null;
        }
        for (BakedQuad quad : quads) {
            TextureAtlasSprite s = quad.getSprite();
            if (s != null) {
                return s;
            }
        }
        return null;
    }

    private static IconCompat fallback() {
        return new IconCompat(0.0F, 0.0F, 1.0F, 1.0F);
    }
}
