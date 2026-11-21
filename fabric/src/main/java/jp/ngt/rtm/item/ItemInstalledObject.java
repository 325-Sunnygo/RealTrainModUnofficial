package jp.ngt.rtm.item;

import net.minecraft.world.item.Item;

/**
 * 本家 jp.ngt.rtm.item.ItemInstalledObject のスクリプト互換シム。
 * NGTO Builder の Wire ツールが importPackage(Packages.jp.ngt.rtm.item) 経由で参照し、
 * item.func_77973_b instanceof ItemInstalledObject(＋getItemType==="Relay") で
 * 手持ちが設置物アイテム (碍子/リレー) かを判定してワイヤーの端点モデルに使う。
 */
public class ItemInstalledObject extends Item {
    public ItemInstalledObject(Properties properties) {
        super(properties);
    }
}
