package jp.ngt.ngtlib.world;

/**
 * 本家 jp.ngt.ngtlib.world.NGTWorld のプレースホルダ。
 *
 * <p>NGTO Builder の Prop ツールの<b>プレビュー描画</b> (render_Prop.js の renderNGTO) が
 * {@code new NGTWorld(NGTUtil.getClientWorld(), ngto)} を生成し {@code NGTObjectRenderer.INSTANCE
 * .renderNGTObject(world, ...)} に渡す。これが無いと {@code ReferenceError: "NGTWorld" is not defined}
 * で描画スクリプトが止まる。
 *
 * <p>RTMU の {@link jp.ngt.ngtlib.renderer.NGTObjectRenderer#renderNGTObject} は現状 no-op
 * (ミニチュアのゴーストプレビュー未実装) なので、この world は生成できれば十分でメソッドは呼ばれない。
 * プレビュー描画自体を実装する際はここに getBlockSet 等を足す。設置 (サーバー) はこのクラスを使わない。
 */
@SuppressWarnings("unused")
public class NGTWorld {
    public final Object baseWorld;
    public final Object ngtObject;

    public NGTWorld(Object baseWorld, Object ngtObject) {
        this.baseWorld = baseWorld;
        this.ngtObject = ngtObject;
    }

    public NGTWorld(Object baseWorld, Object ngtObject, int x, int y, int z) {
        this(baseWorld, ngtObject);
    }

    /** ミニチュア内のブロックを返す (NGTObject に委譲。プレビュー実装時に使う)。 */
    public Object getBlockSet(int x, int y, int z) {
        if (this.ngtObject instanceof jp.ngt.ngtlib.block.NGTObject ngto) {
            return ngto.getBlockSet(x, y, z);
        }
        return null;
    }
}
