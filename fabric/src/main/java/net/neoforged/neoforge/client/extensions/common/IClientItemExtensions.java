package net.neoforged.neoforge.client.extensions.common;

import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;

/**
 * シム: アイテムのクライアント側拡張。
 * RTMU が使うのは getCustomRenderer (ミニチュアの独自アイテム描画) だけ。
 */
public interface IClientItemExtensions {

    IClientItemExtensions DEFAULT = new IClientItemExtensions() {
    };

    /** 独自のアイテム描画。null ならバニラのモデル描画。 */
    default BlockEntityWithoutLevelRenderer getCustomRenderer() {
        return null;
    }
}
