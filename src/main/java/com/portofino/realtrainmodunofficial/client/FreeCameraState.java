package com.portofino.realtrainmodunofficial.client;

/**
 * フリーカメラの有効フラグだけを持つ軽量ホルダー。
 * FreeCameraController はクライアント専用クラス (Minecraft 等を import) なので、
 * 共通クラス Player へ当てる mixin から直接参照すると専用サーバーで
 * クラスロードに失敗する。
 */
public final class FreeCameraState {

    /** フリーカメラ中は true。クライアントの FreeCameraController だけが書き換える。 */
    public static volatile boolean active;

    private FreeCameraState() {
    }
}
