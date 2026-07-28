package com.portofino.realtrainmodunofficial.util;


public final class RealTrainModUnofficialConstants {
    private RealTrainModUnofficialConstants() {
    }

    /// 1秒あたりのティック数
    public static final float TICK_PER_SECOND = 20.0f; // あるいは、LevelがあればTickRateManager#tirkrate？
    /// 1ティックの秒数
    public static final float SECONDS_IN_TICK = 1.0f / TICK_PER_SECOND;
}