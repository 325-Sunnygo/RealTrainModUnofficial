package jp.ngt.rtm.item;

import net.minecraft.world.item.Item;

/**
 * 本家 jp.ngt.rtm.item.ItemInstalledObject のスクリプト互換シム。
 *
 * <p>NGTO Builder の Wire ツールが {@code importPackage(Packages.jp.ngt.rtm.item)} 経由で参照し、
 * {@code item.func_77973_b() instanceof ItemInstalledObject}(＋{@code getItemType()==="Relay"}) で
 * 手持ちが設置物アイテム (碍子/リレー) かを判定してワイヤーの端点モデルに使う。これが無いと
 * Wire スクリプトが {@code ReferenceError: "ItemInstalledObject" is not defined} で止まる。
 *
 * <p>RTMU の実設置物アイテム {@code com.portofino...item.InstalledObjectItem} がこれを継承するので
 * {@code instanceof} が正しく真になり、リレー/碍子の判定も機能する。
 */
public class ItemInstalledObject extends Item {
    public ItemInstalledObject(Properties properties) {
        super(properties);
    }
}
