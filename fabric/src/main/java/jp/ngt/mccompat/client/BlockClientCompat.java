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

/**
 * 1.7.10 {@code Block.func_149691_a(side, meta)} (= getIcon) のクライアント互換。
 *
 * <p>1.21 に IIcon は無いが、ブロックモデルの<b>面ごとの BakedQuad</b> が
 * 実テクスチャ (TextureAtlasSprite) を持っているため、それを IIcon 相当の UV として返せる。
 * 1.7.10 の side 番号 (0=下/1=上/2=北/3=南/4=西/5=東) は 1.21 の
 * {@link Direction} の 3D data value とそのまま一致するので、side→Direction は 1 対 1。
 *
 * <p>指定面に quad が無いモデル (草花のクロスモデル等) では
 * direction=null の一般 quad、それも無ければ代表スプライト (particle icon) へ落とす。
 *
 * <p>クライアント専用クラス。render スクリプト経路でのみ到達するため、
 * 専用サーバーでは読み込まれない (PackScriptSource がこの FQN へ書き換える)。
 */
public final class BlockClientCompat {
    private BlockClientCompat() {
    }

    /** 42 は BakedModel のランダム選択を固定するための種 (どの quad でも sprite は同じ)。 */
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
            // 1.7.10 の (block, meta) → 1.21 の BlockState (色メタは色別ブロックへ)
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
