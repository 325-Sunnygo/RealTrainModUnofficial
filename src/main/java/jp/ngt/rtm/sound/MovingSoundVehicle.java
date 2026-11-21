package jp.ngt.rtm.sound;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

/** 本家 jp.ngt.rtm.sound.MovingSoundVehicle の移植。 */
public class MovingSoundVehicle extends MovingSoundEntity {
    protected boolean changePitch;

    public MovingSoundVehicle(Entity vehicle, ResourceLocation sound, boolean repeat, boolean changePitch) {
        this(vehicle, sound, repeat, changePitch, 16.0F);
    }

    public MovingSoundVehicle(Entity vehicle, ResourceLocation sound, boolean repeat,
                              boolean changePitch, float range) {
        super(vehicle, sound, repeat, range);
        this.changePitch = changePitch;
    }

    @Override
    public void func_73660_a() {
        super.func_73660_a();
    }
}
