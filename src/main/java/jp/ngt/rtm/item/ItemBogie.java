package jp.ngt.rtm.item;

import net.minecraft.world.item.Item;

/**
 * 台車アイテム。本家 {@code jp.ngt.rtm.item.ItemBogie} の移植。
 *
 * <p>★本家もレールに置く処理は<b>コメントアウトされていて動かない</b>
 * (クラフトの材料としてだけ存在する)。ここでも素のアイテムのまま。
 * 本家は {@code setHasSubtypes(true)} だがサブアイテムを出す実装が無いのでメタは常に 0。
 */
public class ItemBogie extends Item {
    public ItemBogie() {
        super(new Properties().stacksTo(16));
    }
}
