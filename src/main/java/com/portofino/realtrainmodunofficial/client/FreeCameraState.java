package com.portofino.realtrainmodunofficial.client;

/**
 * フリーカメラの有効フラグだけを持つ軽量ホルダー。
 * <p>
 * {@link FreeCameraController} はクライアント専用クラス (Minecraft 等を import) なので、
 * 共通クラス {@code Player} へ当てる mixin から直接参照すると専用サーバーで
 * クラスロードに失敗する。フラグをこの client 依存の無いクラスに切り出し、
 * mixin はこちらを見る (サーバーでは常に false)。
 */
public final class FreeCameraState {

    /** フリーカメラ中は true。クライアントの {@link FreeCameraController} だけが書き換える。 */
    public static volatile boolean active;

    private FreeCameraState() {
    }
}
