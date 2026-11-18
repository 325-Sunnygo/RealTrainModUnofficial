package jp.ngt.rtm.sound;

import net.minecraft.world.entity.Entity;

/** 本家 jp.ngt.rtm.sound.SoundUpdaterVehicle の移植。 */
public class SoundUpdaterVehicle extends SoundUpdaterTrain {
    public SoundUpdaterVehicle(Entity vehicle) {
        super(vehicle);
    }

    public SoundUpdaterVehicle(Object soundHandler, Entity vehicle) {
        super(soundHandler, vehicle);
    }
}
