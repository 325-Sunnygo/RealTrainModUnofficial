package com.portofino.realtrainmodunofficial.script;

import net.minecraft.world.entity.Entity;

/** The runtime sound context retains the original SoundUpdaterTrain type and API. */
public final class SoundScriptExecutor extends jp.ngt.rtm.sound.SoundUpdaterTrain {
    public SoundScriptExecutor(Entity train) {
        super(train);
    }
}
