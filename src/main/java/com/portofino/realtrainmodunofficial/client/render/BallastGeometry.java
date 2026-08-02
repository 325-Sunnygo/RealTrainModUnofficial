package com.portofino.realtrainmodunofficial.client.render;

import jp.ngt.rtm.rail.TileEntityLargeRailBase;
import jp.ngt.rtm.rail.util.RailProperty;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 道床 (レールの下の砂利) の形。
 *
 * <p><b>本家 1.7.10 と同じ「4 隅の高さが違う箱」を作る。</b>
 * 本家 RenderBlockLargeRail は、レール 1 本の脇に敷き詰めたブロックを
 * {@code getBlockHeights} が返す 4 隅の高さで描いている。だから勾配やカントに沿って
 * 道床が斜めになり、平らな板を並べたようにはならない。
 *
 * <p><b>描画方式もそろえる。</b> 本家は {@code ISimpleBlockRenderingHandler} で
 * <b>チャンクのメッシュへ焼き込む</b>ので、毎フレームの負担はゼロ。
 * 1.21 でも同じにするため、ここは「形を出すだけ」の共通部品にしてある。
 * 実際に頂点を出すのは各ローダー側 (NeoForge = 動的ベイクドモデル、
 * Fabric = FRAPI) で、どちらもチャンクに焼かれる経路に載せる。
 * ブロックエンティティ描画にすると、レール 1 本につき幅 3 で敷き詰めた
 * 道床が全部毎フレーム CPU を通ることになるので選ばない。
 */
public final class BallastGeometry {

    /** 高さが出せないときの既定 (1/16 ブロック)。本家と同じ。 */
    public static final float DEFAULT_HEIGHT = 0.0625F;

    /**
     * チャンクを組み立てるスレッドへ形を渡すための入れ物 (NeoForge)。
     *
     * <p>★ここに置くのは意図的。持ち主を {@link BallastModel} にすると、
     * レールの BlockEntity (専用サーバーでも動く) が client 専用の親型を持つクラスを
     * 参照することになる。[[rtmu-dedicated-server-client-class-crash]] と同じ踏み方。
     */
    public static final net.neoforged.neoforge.client.model.data.ModelProperty<Shape> SHAPE =
        new net.neoforged.neoforge.client.model.data.ModelProperty<>();

    private BallastGeometry() {
    }

    /**
     * その道床ブロックの 4 隅の高さと、貼るべきブロックの見た目。
     *
     * @param heights {xNzP, xPzP, xPzN, xNzN}
     * @param source  テクスチャを借りるブロック (砂利など)。空気なら描かない
     */
    public record Shape(float[] heights, BlockState source, boolean[] sides, boolean bottom) {

        /** 全部同じ高さか (斜めでないか)。 */
        public boolean isFlat() {
            return heights[0] == heights[1] && heights[1] == heights[2] && heights[2] == heights[3];
        }
    }

    /**
     * 面 1 枚。頂点は 4 つで、順番は<b>本家 RenderBlockLargeRail のまま</b>。
     *
     * <p>ここを勝手に並べ替えると裏表が反転して面が消える。
     * uv は 0〜1 の割合で持ち、実際のアトラス座標は各ローダー側でテクスチャに掛ける。
     */
    public record Face(Direction dir, float[] xs, float[] ys, float[] zs, float[] us, float[] vs) {
    }

    /** 側面の並び。{@link Shape#sides} の添字と対応する。 */
    private static final Direction[] SIDE_ORDER = {
        Direction.SOUTH, Direction.EAST, Direction.NORTH, Direction.WEST
    };

    /**
     * 描く面を組み立てる。本家と同じ 6 面 (側面は隣が塞がっていなければ描く)。
     *
     * <p>陰影は付けない。バニラは面の向きから 0.5 / 0.8 / 0.6 / 1.0 を掛けるので、
     * 本家が手で書いていた係数とちょうど同じになる。
     */
    public static java.util.List<Face> faces(Shape shape) {
        float[] h = shape.heights();
        float y0 = h[0];  //(x0, z1)
        float y1 = h[1];  //(x1, z1)
        float y2 = h[2];  //(x1, z0)
        float y3 = h[3];  //(x0, z0)

        java.util.List<Face> out = new java.util.ArrayList<>(6);

        //--- 側面。v は高さに合わせて縮める (本家 getV) ---
        if (shape.sides()[0]) {   //SOUTH (z+1)
            out.add(new Face(Direction.SOUTH,
                new float[]{0, 0, 1, 1}, new float[]{y0, 0, 0, y1}, new float[]{1, 1, 1, 1},
                new float[]{0, 0, 1, 1}, new float[]{1 - v(y0), 1, 1, 1 - v(y1)}));
        }
        if (shape.sides()[1]) {   //EAST (x+1)
            out.add(new Face(Direction.EAST,
                new float[]{1, 1, 1, 1}, new float[]{y1, 0, 0, y2}, new float[]{1, 1, 0, 0},
                new float[]{0, 0, 1, 1}, new float[]{1 - v(y1), 1, 1, 1 - v(y2)}));
        }
        if (shape.sides()[2]) {   //NORTH (z-1)
            out.add(new Face(Direction.NORTH,
                new float[]{1, 1, 0, 0}, new float[]{y2, 0, 0, y3}, new float[]{0, 0, 0, 0},
                new float[]{0, 0, 1, 1}, new float[]{1 - v(y2), 1, 1, 1 - v(y3)}));
        }
        if (shape.sides()[3]) {   //WEST (x-1)
            out.add(new Face(Direction.WEST,
                new float[]{0, 0, 0, 0}, new float[]{y3, 0, 0, y0}, new float[]{0, 0, 1, 1},
                new float[]{0, 0, 1, 1}, new float[]{1 - v(y3), 1, 1, 1 - v(y0)}));
        }

        //--- 上面。ここが斜めになる ---
        out.add(new Face(Direction.UP,
            new float[]{0, 1, 1, 0}, new float[]{y0, y1, y2, y3}, new float[]{1, 1, 0, 0},
            new float[]{0, 0, 1, 1}, new float[]{0, 1, 1, 0}));

        //--- 底面 ---
        if (shape.bottom()) {
            out.add(new Face(Direction.DOWN,
                new float[]{0, 1, 1, 0}, new float[]{0, 0, 0, 0}, new float[]{0, 0, 1, 1},
                new float[]{0, 0, 1, 1}, new float[]{0, 1, 1, 0}));
        }
        return out;
    }

    /** 側面テクスチャの縦の使う割合 (本家 getV)。 */
    private static float v(float height) {
        return height < 0.0F ? 0.0F : (height > 1.0F ? 1.0F : height);
    }

    /**
     * その位置の道床の形を出す。道床でなければ null。
     *
     * <p>対象は<b>実際に敷設で置かれるブロック</b>、つまり
     * {@link jp.ngt.rtm.rail.BlockLargeRailBase} (とそのコア/分岐/坂の派生)。
     * これは {@code RailMap.setRail} が置く本家と同じブロックで、
     * 本家 {@code RenderBlockLargeRail} もこのブロックの描画として道床を出していた。
     *
     * <p>高さ・テクスチャ・面引きはすべて本家 RenderBlockLargeRail のとおり:
     * <ul>
     *   <li>高さ = {@code tile.getBlockHeights(x, y, z, prop.blockHeight, false)} の 4 隅</li>
     *   <li>テクスチャ = {@code prop.block} (空気なら描かない)</li>
     *   <li>側面 = 隣が不透明キューブでなく、かつ隣がレールでもないときだけ描く</li>
     * </ul>
     */
    public static Shape shapeAt(BlockGetter level, BlockPos pos) {
        if (level == null || pos == null) {
            return null;
        }
        if (!(level.getBlockEntity(pos) instanceof TileEntityLargeRailBase rail)) {
            return null;
        }
        // 本家: core == null / prop == null なら岩盤を描く。ここでは「まだ読めていない」
        // だけなので何も描かない (コアが届いた時点で描き直す)。
        jp.ngt.rtm.rail.TileEntityLargeRailCore core = rail.getRailCore();
        if (core == null) {
            return null;
        }
        RailProperty prop = core.getProperty();
        if (prop == null || prop.block == null || prop.block == Blocks.AIR) {
            return null;   //本家: prop.block == Blocks.air なら道床無し
        }
        BlockState source = prop.block.defaultBlockState();

        // useCache=false は本家 RenderBlockLargeRail と同じ。描画は常に今の線形で引く。
        float[] heights = rail.getBlockHeights(pos.getX(), pos.getY(), pos.getZ(), prop.blockHeight, false);
        if (heights == null || heights.length < 4) {
            heights = new float[]{prop.blockHeight, prop.blockHeight, prop.blockHeight, prop.blockHeight};
        } else {
            heights = heights.clone();   //キャッシュ配列を書き換えない
        }
        // 床下へ潜らせない / ブロックから飛び出させない。
        // 本家は 1.7.10 の即時描画なので素通しだったが、1.21 はチャンクメッシュに焼くため
        // 単位立方体の外へ出た面は隣セクションで切られて欠けて見える。
        // 当たり判定 (TileEntityLargeRailBase.getRailCollisionShape) も同じ範囲で切っている。
        for (int i = 0; i < heights.length; i++) {
            if (!Float.isFinite(heights[i]) || heights[i] < 0.0F) {
                heights[i] = 0.0F;
            } else if (heights[i] > 1.0F) {
                heights[i] = 1.0F;
            }
        }

        //本家と同じ面引き: 隣が塞がっているか、隣もレールなら側面を描かない
        boolean[] sides = new boolean[SIDE_ORDER.length];
        for (int i = 0; i < SIDE_ORDER.length; i++) {
            sides[i] = drawSide(level, pos.relative(SIDE_ORDER[i]));
        }
        //本家の底面だけは「不透明キューブでなければ描く」= 下がレールでも描く
        BlockPos below = pos.below();
        boolean bottom = !level.getBlockState(below).isSolidRender(level, below);
        return new Shape(heights, source, sides, bottom);
    }

    /** その隣に向けて面を描くか。本家 {@code !isOpaqueCube() && !(instanceof BlockLargeRailBase)}。 */
    private static boolean drawSide(BlockGetter level, BlockPos neighbor) {
        BlockState state = level.getBlockState(neighbor);
        if (state.isSolidRender(level, neighbor)) {
            return false;
        }
        return !(state.getBlock() instanceof jp.ngt.rtm.rail.BlockLargeRailBase);
    }
}
