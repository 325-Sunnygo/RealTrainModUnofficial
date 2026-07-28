package jp.ngt.mccompat;

/**
 * 「ワールドに読み込まれた」通知を受けたいブロックエンティティの目印。
 *
 * <p>NeoForge は {@code BlockEntity#onLoad()} を用意していて、レールや設置物はそこで
 * 自分を静的な一覧へ登録している。<b>バニラ (Fabric) にこのフックは無い</b>ので、
 * 実装クラスにこの印を付けて、エントリポイントが Fabric の
 * {@code ServerBlockEntityEvents.BLOCK_ENTITY_LOAD} から呼ぶ。
 *
 * <p>印を付け忘れると「動くはずの信号が反応しない」「レールが列車を拾わない」のように、
 * 例外を出さずに機能だけ死ぬ。新しく onLoad を持たせたらここも実装すること。
 */
public interface LoadAwareBlockEntity {
    void onLoad();
}
