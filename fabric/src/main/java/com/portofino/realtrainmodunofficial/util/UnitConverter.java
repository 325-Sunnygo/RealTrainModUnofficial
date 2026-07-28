package com.portofino.realtrainmodunofficial.util;

import static com.portofino.realtrainmodunofficial.util.RealTrainModUnofficialConstants.TICK_PER_SECOND;

/// 単位変換メソッド
public final class UnitConverter {
    private UnitConverter() {
    }

    /// 速度の単位キロメートル毎時をブロック毎ティックに変換する
    public static float kph2bpt(float kilometrePerHour) {
        return kilometrePerHour / 3.6f / TICK_PER_SECOND;
    }

    /// 距離の単位センチメートルをメートルに変換する
    public static float cm2m(float centimetre) {
        return centimetre / 100;
    }

    /// 時間の単位秒をティックに変換する
    public static float s2t(float second) {
        return second * TICK_PER_SECOND;
    }

    /// 加速度の単位メートル毎秒毎秒をブロック毎ティック毎ティックに変換する
    public static float mpss2bpts(float meterPerSecondSquared) {
        return meterPerSecondSquared / (TICK_PER_SECOND * TICK_PER_SECOND);
    }

}