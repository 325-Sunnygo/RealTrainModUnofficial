package jp.ngt.mccompat;

/**
 * 「ワールドに読み込まれた」通知を受けたいブロックエンティティの目印。
 * NeoForge は BlockEntity#onLoad を用意していて、レールや設置物はそこで
 * 自分を静的な一覧へ登録している。
 */
public interface LoadAwareBlockEntity {
    void onLoad();
}
