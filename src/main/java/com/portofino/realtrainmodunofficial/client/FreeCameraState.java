package com.portofino.realtrainmodunofficial.client;

/** フリーカメラの有効フラグだけを持つ軽量ホルダー。 */
public final class FreeCameraState {

    /** フリーカメラ中は true。クライアントの FreeCameraController だけが書き換… */
    public static volatile boolean active;

    private FreeCameraState() {
    }
}
