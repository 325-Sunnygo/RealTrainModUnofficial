package jp.ngt.rtm.electric;

/**
 * 本家 jp.ngt.rtm.electric.BlockInsulator のプレースホルダ ({@link jp.ngt.ngtlib.block.TileEntityCustom}
 * と同じ「名前解決だけ」方針)。
 *
 * <p>NGTO Builder の Wire ツールが {@code importPackage(Packages.jp.ngt.rtm.electric)} 経由で参照し、
 * {@code block instanceof BlockInsulator} でワイヤーの端点に碍子ブロックが置かれているかを判定する。
 * これが無いと Wire スクリプトが {@code ReferenceError: "BlockInsulator" is not defined} で止まる。
 *
 * <p>RTMU の実ブロックはこれを継承しないため {@code instanceof} は常に false = 碍子端点の特別扱いは
 * スキップされ、ワイヤーは既定の端点で接続される (基本のワイヤー設置は動作する)。碍子端点対応が
 * 必要になったら RTMU の碍子ブロックにこれを継承させる。
 */
public class BlockInsulator {
}
