package net.neoforged.neoforge.client.extensions.common;

import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;

/**
 * シム: アイテムのクライアント側拡張。
 *
 * <p>RTMU が使うのは {@code getCustomRenderer()} (ミニチュアの独自アイテム描画) だけ。
 * Fabric では {@code BuiltinItemRendererRegistry} が対応物なので、
 * エントリポイント側でこの実装を拾って登録する。
 */
public interface IClientItemExtensions {

    IClientItemExtensions DEFAULT = new IClientItemExtensions() {
    };

    /** 独自のアイテム描画。null ならバニラのモデル描画。 */
    default BlockEntityWithoutLevelRenderer getCustomRenderer() {
        return null;
    }
}
